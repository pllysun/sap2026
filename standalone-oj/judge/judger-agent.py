#!/usr/bin/env python3
"""Authenticated supervisor for one isolated go-judge engine. Standard library only."""
import argparse
import hashlib
import hmac
import http.server
import io
import json
import math
import os
from pathlib import Path
import re
import secrets
import signal
import ssl
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import urllib.parse

RUNTIME = 'go-judge-e9d70a0-gcc15.3-jdk27-python3.14.7-rust1.98.1-v1'
MIB = 1024 * 1024
FILE_ID = re.compile(r'[A-Za-z0-9_-]{1,100}')
SOURCE_FILES = {'main.c', 'main.cpp', 'Main.java', 'main.py', 'main.rs'}
ARTIFACT_FILES = {'answer', 'answer.jar'}
PROGRAMS = {'/usr/local/bin/gcc', '/usr/local/bin/g++', '/usr/local/bin/python3',
            '/opt/rust/bin/rustc', '/opt/java27/bin/java', '/opt/java27/bin/javac',
            '/opt/java27/bin/jar', '/bin/sh', '/w/answer'}
ENV_KEYS = {'PATH', 'HOME', 'LANG', 'LC_ALL', 'TMPDIR', 'PYTHONDONTWRITEBYTECODE'}


def bounded_integer(value, maximum):
    # bool is a subclass of int; it is not a resource limit.
    if type(value) is not int or not 0 < value <= maximum:
        raise ValueError('Invalid resource limit')


def unique_keys(pairs):
    # Python and Go can interpret repeated object keys differently. Reject them
    # before forwarding the original JSON to the engine.
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON field')
        result[key] = value
    return result


def validate_run(body, artifacts):
    """Accept the bounded file/command protocol used by the existing backend.

    Code and command arguments still execute only inside go-judge. In particular,
    none of the engine's host-side src, pipe, symlink or extra command features
    can be supplied through the node API.
    """
    request = json.loads(body, object_pairs_hook=unique_keys)
    if not isinstance(request, dict) or set(request) != {'cmd'}:
        raise ValueError('Invalid execution request')
    commands = request['cmd']
    if not isinstance(commands, list) or len(commands) != 1 or not isinstance(commands[0], dict):
        raise ValueError('Exactly one command is required')
    cmd = commands[0]
    allowed = {'args', 'env', 'files', 'cpuLimit', 'clockLimit', 'memoryLimit',
               'stackLimit', 'procLimit', 'strictMemoryLimit', 'copyIn',
               'copyOutCached', 'copyOutMax'}
    if set(cmd) - allowed:
        raise ValueError('Unsupported command fields')
    args = cmd.get('args')
    if (not isinstance(args, list) or not 1 <= len(args) <= 128 or
            any(not isinstance(a, str) or len(a) > 262144 or '\x00' in a for a in args) or
            args[0] not in PROGRAMS):
        raise ValueError('Invalid sandbox command')
    env = cmd.get('env', [])
    if not isinstance(env, list) or len(env) > len(ENV_KEYS):
        raise ValueError('Invalid sandbox environment')
    keys = set()
    for value in env:
        if not isinstance(value, str) or len(value) > 1024 or '\x00' in value or '=' not in value:
            raise ValueError('Invalid environment entry')
        key = value.split('=', 1)[0]
        if key not in ENV_KEYS or key in keys:
            raise ValueError('Unsupported environment entry')
        keys.add(key)
    for key, maximum in [('cpuLimit', 15_000_000_000), ('clockLimit', 45_000_000_000),
                         ('memoryLimit', 512 * MIB), ('stackLimit', 32 * MIB),
                         ('procLimit', 64), ('copyOutMax', 32 * MIB)]:
        bounded_integer(cmd.get(key), maximum)
    if cmd.get('strictMemoryLimit') is not True:
        raise ValueError('Strict memory limit is required')
    files = cmd.get('files')
    if not isinstance(files, list) or len(files) != 3:
        raise ValueError('Invalid standard streams')
    stdin = files[0]
    if (not isinstance(stdin, dict) or set(stdin) != {'content'} or
            not isinstance(stdin['content'], str) or len(stdin['content']) > MIB):
        raise ValueError('Invalid standard input')
    for stream, name, maximum in zip(files[1:], ['stdout', 'stderr'], [262144, 65536]):
        if not isinstance(stream, dict) or set(stream) != {'name', 'max'} or stream['name'] != name:
            raise ValueError('Invalid output collector')
        bounded_integer(stream['max'], maximum)
    inputs = cmd.get('copyIn', {})
    if not isinstance(inputs, dict) or len(inputs) > 2:
        raise ValueError('Invalid input files')
    for name, value in inputs.items():
        if not isinstance(value, dict):
            raise ValueError('Invalid input file')
        if name in SOURCE_FILES and set(value) == {'content'}:
            if not isinstance(value['content'], str) or len(value['content']) > 196608:
                raise ValueError('Source exceeds limit')
        elif name in ARTIFACT_FILES and set(value) == {'fileId'}:
            if not isinstance(value['fileId'], str) or value['fileId'] not in artifacts:
                raise ValueError('Artifact does not belong to lease')
        else:
            raise ValueError('Unsupported input source')
    cached = cmd.get('copyOutCached', [])
    if (not isinstance(cached, list) or len(cached) > 1 or
            any(not isinstance(name, str) or name not in ARTIFACT_FILES for name in cached) or
            (cached and len(artifacts) >= 2)):
        raise ValueError('Invalid cached output')
    return cached


def validate_engine_config(content):
    """Check both v1 and v2 without depending on optional cpuset support."""
    config = json.loads(content)
    runner = config['runnerConfig']
    controllers = set(runner.get('cgroupControllers', []))
    if (runner.get('cgroupType') not in (1, 2) or
            not {'memory', 'pids', 'cpu'} <= controllers or
            (runner['cgroupType'] == 1 and 'cpuacct' not in controllers) or
            runner.get('uid') != 1000 or runner.get('gid') != 1000 or
            runner.get('workDir') != '/w' or config.get('fixSymlinkEscape') is not True):
        raise ValueError('Unsafe engine configuration')
    roots = {'usr', 'opt', 'etc/ld.so.cache', 'etc/alternatives'}
    devices = {'dev/null', 'dev/zero', 'dev/full', 'dev/random', 'dev/urandom'}
    seen = set()
    for mount in runner['mount']:
        target = mount['Target'].lstrip('/')
        if target in seen:
            raise ValueError('Duplicate mount')
        seen.add(target)
        if target in roots:
            if (mount['Source'] != '/opt/judger/rootfs/' + target or
                    mount.get('FsType') or not mount['Flags'] & 1):
                raise ValueError('Toolchain mount must be isolated and read-only')
        elif target in devices:
            if mount['Source'] != '/' + target or mount.get('FsType'):
                raise ValueError('Invalid device mount')
        elif target in {'w', 'tmp'}:
            if (mount.get('FsType') != 'tmpfs' or
                    set(mount.get('Data', '').split(',')) != {'size=64m', 'nr_inodes=8k'}):
                raise ValueError('Workspace must be a bounded tmpfs')
        elif target == 'proc':
            if mount.get('FsType') != 'proc' or not mount['Flags'] & 1:
                raise ValueError('proc must be read-only')
        else:
            raise ValueError('Unexpected sandbox mount')
    if seen != roots | devices | {'w', 'tmp', 'proc'}:
        raise ValueError('Sandbox mounts are incomplete')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def number(path, default=0):
    try:
        return int(Path(path).read_text().strip())
    except (OSError, ValueError):
        return default


class Agent:
    def __init__(self, args):
        self.args, self.lock = args, threading.RLock()
        self.token = Path(args.token_file).read_text().strip()
        if len(self.token) < 32:
            raise ValueError('Node token must contain at least 32 characters')
        self.state_path = Path(args.state_dir) / 'state.json'
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        self.desired = not args.initial_stopped
        if self.state_path.exists():
            self.desired = bool(json.loads(self.state_path.read_text()).get('enabled', True))
        self.process, self.state, self.leases = None, 'STOPPED', {}
        self.cpu_percent, self.last_cpu = 0.0, None
        self.requester = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        self.started_at, self.error = None, ''

    def persist(self):
        tmp = self.state_path.with_suffix('.tmp')
        tmp.write_text(json.dumps({'enabled': self.desired}))
        tmp.chmod(0o600)
        tmp.replace(self.state_path)

    def budget(self):
        a = self.args
        mem = a.memory_mb
        cpu = a.cpu
        base = Path('/sys/fs/cgroup')
        v2 = base / a.cgroup_prefix
        parent_limit = number(base / 'memory' / 'memory.limit_in_bytes', mem * 1048576)
        parent_limit = number(base / 'memory.max', parent_limit)
        limit = min(parent_limit, number(base / 'memory' / a.cgroup_prefix / 'memory.limit_in_bytes', mem * 1048576))
        limit = number(v2 / 'memory.max', limit)
        mem = min(mem, limit // 1048576)
        quota = number(base / 'cpu' / a.cgroup_prefix / 'cpu.cfs_quota_us', -1)
        period = number(base / 'cpu' / a.cgroup_prefix / 'cpu.cfs_period_us', 100000)
        if quota > 0:
            cpu = min(cpu, quota / period)
        parent_quota = number(base / 'cpu' / 'cpu.cfs_quota_us', -1)
        if parent_quota > 0:
            cpu = min(cpu, parent_quota / number(base / 'cpu' / 'cpu.cfs_period_us', 100000))
        try:
            q, p = (v2 / 'cpu.max').read_text().split()
            if q != 'max':
                cpu = min(cpu, int(q) / int(p))
        except (OSError, ValueError):
            pass
        try:
            q, p = (base / 'cpu.max').read_text().split()
            if q != 'max':
                cpu = min(cpu, int(q) / int(p))
        except (OSError, ValueError):
            pass
        return mem, cpu, max(0, min(a.max_concurrency, int(max(1, cpu)), mem // 640))

    def engine(self, method, path, body=None, timeout=55):
        request = urllib.request.Request('http://127.0.0.1:%s%s' % (self.args.engine_port, path),
            data=body, method=method, headers={'Authorization': 'Bearer ' + self.token, 'Content-Type': 'application/json'})
        with self.requester.open(request, timeout=timeout) as response:
            limit = 4 * MIB if path == '/run' else 65536
            content = response.read(limit + 1)
            if len(content) > limit:
                raise ValueError('Engine response exceeds limit')
            return content

    def start(self):
        with self.lock:
            if self.state == 'STOPPING':
                raise ValueError('请等待节点停止后再启动')
            self.desired = True
            self.persist()
            if self.state in ('STARTING', 'RUNNING'):
                return
            self.state, self.error = 'STARTING', ''
            threading.Thread(target=self.launch, daemon=True).start()

    def launch(self):
        try:
            if not Path(self.args.mount_conf).is_file():
                raise RuntimeError('Required sandbox mount configuration is missing')
            _, _, capacity = self.budget()
            if capacity < 1:
                raise RuntimeError('Insufficient memory budget')
            cpu_sets = self.args.cpu_sets.split(',') if self.args.cpu_sets else []
            if cpu_sets and len(cpu_sets) < capacity:
                raise RuntimeError('Reserve one CPU set per task slot')
            # Cached binaries cannot survive an engine restart: leases are gone.
            # Remove those files before the new engine opens its own file store.
            cache = Path(self.args.state_dir) / 'files'
            cache.mkdir(parents=True, exist_ok=True)
            for entry in cache.iterdir():
                if entry.is_file() or entry.is_symlink():
                    entry.unlink()
            sources = Path(self.args.state_dir) / 'inputs'
            sources.mkdir(mode=0o700, exist_ok=True)
            sources.chmod(0o700)
            args = ['/opt/judger/go-judge', '-http-addr', '127.0.0.1:%s' % self.args.engine_port,
                '-parallelism', str(capacity), '-pre-fork', str(capacity), '-container-cred-start', '10000',
                '-cgroup-prefix', self.args.cgroup_prefix, '-no-fallback', '-mount-conf', self.args.mount_conf,
                '-output-limit', '32m', '-copy-out-limit', '32m', '-dir', self.args.state_dir + '/files',
                '-file-timeout', '10m',
                '-src-prefix', str(sources),
                '-tmp-fs-param', 'size=64m,nr_inodes=8k']
            if cpu_sets:
                args.extend(['-cpuset', ','.join(cpu_sets[:capacity])])
            if Path('/sys/fs/cgroup/cgroup.controllers').exists():
                # go-judge derives its v2 prefix from its own process cgroup.
                # Move only the engine child into the delegated subtree before exec.
                group = Path('/sys/fs/cgroup') / self.args.cgroup_prefix / 'engine'
                # A previous engine leaves a branch with subtree controllers enabled.
                # Linux rejects placing a process in that branch (EBUSY). Recreate
                # only our empty engine subtree before the next launch.
                if group.exists():
                    if any(p.read_text().strip() for p in group.rglob('cgroup.procs')):
                        raise RuntimeError('Engine cgroup still contains processes')
                    for child in sorted((p for p in group.rglob('*') if p.is_dir()),
                                        key=lambda p: len(p.parts), reverse=True):
                        child.rmdir()
                    group.rmdir()
                group.mkdir(exist_ok=True)
                # Flush explicitly: buffered close errors must abort the launch,
                # never execute outside the dedicated resource budget.
                launcher = 'import os,sys; f=open(sys.argv[1],"w"); f.write(str(os.getpid())); f.flush(); f.close(); os.execv(sys.argv[2],sys.argv[2:])'
                args = [sys.executable, '-c', launcher, str(group / 'cgroup.procs'), *args]
            with self.lock:
                if not self.desired:
                    return
                with open(self.args.state_dir + '/engine.log', 'ab') as out:
                    self.process = subprocess.Popen(args, env={'PATH': '/usr/bin:/bin', 'HOME': self.args.state_dir,
                        'ES_AUTH_TOKEN': self.token}, stdout=out, stderr=out, start_new_session=True)
            for _ in range(60):
                with self.lock:
                    if not self.desired or not self.process or self.process.poll() is not None:
                        raise RuntimeError('Engine did not remain running')
                try:
                    validate_engine_config(self.engine('GET', '/config', timeout=1))
                    self.verify_execution_isolation()
                    with self.lock:
                        if self.desired:
                            self.state, self.started_at = 'RUNNING', int(time.time() * 1000)
                    return
                except (OSError, urllib.error.URLError):
                    time.sleep(.25)
            raise RuntimeError('Engine startup timeout')
        except Exception:
            self.terminate()
            with self.lock:
                if self.desired:
                    self.state, self.error = 'ERROR', '判题进程启动失败，请检查节点的 cgroup、挂载与隔离配置'

    def verify_execution_isolation(self):
        # One bounded startup check also covers existing engine binaries whose
        # /config does not expose whether seccomp was actually installed.
        code = """import errno,json,os
s=dict(l.split(':',1) for l in open('/proc/self/status') if ':' in l)
r,w=os.pipe()
children=[]
limited=False
try:
    for _ in range(12):
        try:
            pid=os.fork()
        except OSError as e:
            limited=e.errno==errno.EAGAIN
            break
        if pid==0:
            os.close(w)
            os.read(r,1)
            os._exit(0)
        children.append(pid)
finally:
    os.close(w)
    os.close(r)
    for pid in children:
        os.waitpid(pid,0)
print(json.dumps({'uid':os.getuid(),'gid':os.getgid(),'caps':s['CapEff'].strip(),
    'nnp':s['NoNewPrivs'].strip(),'seccomp':s['Seccomp'].strip(),
    'net':os.readlink('/proc/self/ns/net'),'pidsLimited':limited and 0<len(children)<=7}))
"""
        command = {'args': ['/usr/local/bin/python3', '-c', code],
                   'env': ['PATH=/usr/local/bin:/usr/bin:/bin', 'HOME=/w'],
                   'files': [{'content': ''}, {'name': 'stdout', 'max': 4096},
                             {'name': 'stderr', 'max': 4096}],
                   'cpuLimit': 1_000_000_000, 'clockLimit': 3_000_000_000,
                   'memoryLimit': 64 * MIB, 'stackLimit': 8 * MIB,
                   'procLimit': 8, 'strictMemoryLimit': True, 'copyIn': {}}
        result = json.loads(self.engine('POST', '/run', json.dumps({'cmd': [command]}).encode(), timeout=5))
        if len(result) != 1 or result[0].get('status') != 'Accepted':
            raise ValueError('Sandbox startup probe failed')
        actual = json.loads(result[0]['files']['stdout'])
        if (actual.get('uid') != 1000 or actual.get('gid') != 1000 or
                int(actual.get('caps', '1'), 16) != 0 or actual.get('nnp') != '1' or
                actual.get('seccomp') != '2' or actual.get('pidsLimited') is not True or
                not actual.get('net') or actual.get('net') == os.readlink('/proc/self/ns/net')):
            raise ValueError('Sandbox isolation is unavailable')

    def stop(self):
        with self.lock:
            self.desired = False
            self.persist()
            self.state = 'STOPPING'
            self.leases.clear()
        threading.Thread(target=self.terminate, daemon=True).start()

    def terminate(self):
        with self.lock:
            process = self.process
        if process and process.poll() is None:
            try:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=4)
            except (ProcessLookupError, subprocess.TimeoutExpired):
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=2)
                except (ProcessLookupError, subprocess.TimeoutExpired):
                    pass
        # Only this dedicated judge cgroup; application processes never enter it.
        roots = [Path('/sys/fs/cgroup/memory') / self.args.cgroup_prefix,
                 Path('/sys/fs/cgroup') / self.args.cgroup_prefix]
        for root in roots:
            if root.is_dir():
                for file in root.rglob('cgroup.procs'):
                    try:
                        for pid in file.read_text().split():
                            if int(pid) > 1 and int(pid) != os.getpid():
                                try:
                                    os.kill(int(pid), signal.SIGKILL)
                                except ProcessLookupError:
                                    pass
                    except (OSError, ValueError):
                        pass
        with self.lock:
            self.process, self.state = None, 'STOPPED'

    def status(self):
        with self.lock:
            now = time.monotonic()
            for key in list(self.leases):
                if self.leases[key]['expires'] < now and not self.leases[key]['executing']:
                    del self.leases[key]
            if self.state == 'RUNNING' and (not self.process or self.process.poll() is not None):
                self.state, self.error = 'ERROR', '判题进程已退出'
                self.leases.clear()
            mem, cpu, capacity = self.budget()
            used = number('/sys/fs/cgroup/memory/%s/memory.usage_in_bytes' % self.args.cgroup_prefix)
            used = number('/sys/fs/cgroup/%s/memory.current' % self.args.cgroup_prefix, used)
            info = {}
            for line in Path('/proc/meminfo').read_text().splitlines():
                key, value = line.split(':', 1)
                info[key] = int(value.strip().split()[0]) * 1024
            available = info.get('MemAvailable', info.get('MemFree', 0)) // 1048576
            free = max(0, capacity - len(self.leases)) if self.state == 'RUNNING' else 0
            if available < 160:
                free = 0
            return {'protocol': 1, 'runtimeId': RUNTIME, 'state': self.state, 'enabled': self.desired,
                'capacity': capacity, 'active': len(self.leases), 'freeSlots': free, 'cpuCores': cpu,
                'cpuPercent': self.cpu_percent, 'memoryMb': mem, 'memoryUsedMb': round(used / 1048576, 1),
                'hostAvailableMb': available, 'hostMemoryMb': info['MemTotal'] // 1048576,
                'startedAt': self.started_at, 'reportedAt': int(time.time() * 1000), 'error': self.error}

    def reserve(self, runtime):
        with self.lock:
            status = self.status()
            if runtime != RUNTIME or not status['freeSlots']:
                return None
            key = secrets.token_urlsafe(32)
            self.leases[key] = {'expires': time.monotonic() + 180, 'executing': 0,
                                'artifacts': set(), 'released': False}
            return key

    def monitor(self):
        while True:
            try:
                cpu = [int(x) for x in Path('/proc/stat').read_text().splitlines()[0].split()[1:]]
                current = (sum(cpu), cpu[3] + cpu[4])
                if self.last_cpu and current[0] > self.last_cpu[0]:
                    self.cpu_percent = round(100 * (1 - (current[1] - self.last_cpu[1]) / (current[0] - self.last_cpu[0])), 1)
                self.last_cpu = current
                status = self.status()
                if self.args.push_url:
                    request = urllib.request.Request(self.args.push_url, data=json.dumps(status).encode(),
                        headers={'Authorization': 'Bearer ' + self.token, 'Content-Type': 'application/json'})
                    with self.requester.open(request, timeout=3) as response:
                        response.read(8192)
            except Exception:
                pass
            time.sleep(5)


class DeadlineReader(io.RawIOBase):
    """A total header/body deadline, including clients that trickle bytes."""
    def __init__(self, connection):
        self.connection = connection
        self.raw = connection.makefile('rb', buffering=0)
        self.deadline = time.monotonic()
        self.remaining = 0

    def readable(self):
        return True

    def readinto(self, buffer):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0 or self.remaining <= 0:
            raise TimeoutError('Request deadline exceeded')
        self.connection.settimeout(remaining)
        size = self.raw.readinto(memoryview(buffer)[:self.remaining])
        self.remaining -= size
        return size

    def close(self):
        self.raw.close()
        super().close()


class NodeServer(http.server.ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 64
    header_timeout = 10
    header_limit = 65536
    body_timeout = 15
    handshake_timeout = 5

    def __init__(self, address, agent, tls_context=None, max_connections=128):
        self.agent, self.tls_context = agent, tls_context
        self.slots = threading.BoundedSemaphore(max_connections)
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        connection = request
        try:
            if self.tls_context is not None:
                connection.settimeout(self.handshake_timeout)
                connection = self.tls_context.wrap_socket(connection, server_side=True,
                                                          do_handshake_on_connect=False)
                connection.do_handshake()
            self.finish_request(connection, client_address)
        except (OSError, ssl.SSLError):
            pass
        finally:
            self.shutdown_request(connection)
            self.slots.release()


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def setup(self):
        super().setup()
        self.rfile.close()
        self.reader = DeadlineReader(self.connection)
        self.rfile = io.BufferedReader(self.reader)

    def handle_one_request(self):
        self.reader.deadline = time.monotonic() + self.server.header_timeout
        self.reader.remaining = self.server.header_limit
        super().handle_one_request()

    def log_message(self, *_):
        pass  # Never log tokens, code or test inputs.

    def reply(self, code, value):
        content = value if isinstance(value, bytes) else json.dumps(value).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(content)))
        if code >= 400:
            # Failed authentication/framing may leave an unread request body.
            self.close_connection = True
            self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(content)

    def handle_request(self):
        agent = self.server.agent
        supplied = self.headers.get('Authorization', '')
        if not hmac.compare_digest(supplied.encode(), ('Bearer ' + agent.token).encode()):
            return self.reply(401, {'error': 'Unauthorized'})
        lengths = self.headers.get_all('Content-Length', [])
        if (self.headers.get('Transfer-Encoding') is not None or len(lengths) > 1 or
                (lengths and not re.fullmatch(r'[0-9]{1,10}', lengths[0]))):
            return self.reply(400, {'error': 'Invalid request framing'})
        length = int(lengths[0]) if lengths else 0
        if length < 0 or length > 4 * 1024 * 1024:
            return self.reply(413, {'error': 'Request too large'})
        self.reader.deadline = time.monotonic() + self.server.body_timeout
        self.reader.remaining = 4 * MIB
        body = self.rfile.read(length) if length else None
        if length and len(body) != length:
            return self.reply(400, {'error': 'Incomplete request body'})
        self.connection.settimeout(65)
        path, method = self.path, self.command
        if method == 'GET' and path == '/status':
            return self.reply(200, agent.status())
        if method == 'POST' and path in ('/start', '/stop'):
            with agent.lock:
                if agent.state == 'STOPPING':
                    return self.reply(409, {'error': 'Node is stopping'})
            (agent.start if path == '/start' else agent.stop)()
            return self.reply(200, agent.status())
        if method == 'POST' and path == '/leases':
            key = agent.reserve(json.loads(body or b'{}').get('runtimeId'))
            return self.reply(200 if key else 409, {'lease': key})
        if method == 'DELETE' and re.fullmatch(r'/leases/[\w-]{20,100}', path):
            with agent.lock:
                lease_id = path.rsplit('/', 1)[1]
                lease = agent.leases.get(lease_id)
                if lease and lease['executing']:
                    # Keep the slot counted until the in-flight engine call ends.
                    lease['released'] = True
                else:
                    agent.leases.pop(lease_id, None)
            return self.reply(200, {})
        if ((method == 'POST' and path == '/engine/run') or
                (method == 'DELETE' and re.fullmatch(r'/engine/file/[\w-]+', path))):
            key = self.headers.get('X-Judge-Lease', '')
            with agent.lock:
                lease = agent.leases.get(key)
                if (not lease or agent.state != 'RUNNING' or lease.get('released') or
                        lease['expires'] <= time.monotonic() or lease['executing']):
                    return self.reply(409, {'error': 'Lease unavailable'})
                artifacts = lease.setdefault('artifacts', set())
                if method == 'POST':
                    cached = validate_run(body, artifacts)
                elif path.rsplit('/', 1)[1] not in artifacts:
                    return self.reply(404, {'error': 'Artifact unavailable'})
                lease['executing'] = 1
                lease['expires'] = time.monotonic() + 180
            try:
                result = agent.engine(method, path[len('/engine'):], body)
                with agent.lock:
                    if key not in agent.leases or agent.state != 'RUNNING' or lease.get('released'):
                        raise ValueError('Node stopped during execution')
                    if method == 'POST':
                        response = json.loads(result)
                        if not isinstance(response, list) or len(response) != 1:
                            raise ValueError('Invalid engine response')
                        ids = response[0].get('fileIds', {})
                        if (not isinstance(ids, dict) or set(ids) - set(cached) or
                                any(not isinstance(v, str) or not FILE_ID.fullmatch(v) for v in ids.values())):
                            raise ValueError('Invalid engine artifacts')
                        artifacts.update(ids.values())
                    else:
                        artifacts.discard(path.rsplit('/', 1)[1])
                code, response_body = 200, result
            except Exception:
                code, response_body = 503, {'error': 'Engine unavailable'}
            finally:
                with agent.lock:
                    lease['executing'] -= 1
                    lease['expires'] = time.monotonic() + 180
                    if lease.get('released'):
                        agent.leases.pop(key, None)
            return self.reply(code, response_body)
        return self.reply(404, {'error': 'Not found'})

    def do_GET(self):
        self.run_request()
    do_POST = do_GET
    do_DELETE = do_GET

    def run_request(self):
        try:
            self.handle_request()
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception:
            self.reply(400, {'error': 'Invalid request'})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=5051)
    parser.add_argument('--engine-port', type=int, default=5050)
    parser.add_argument('--token-file', default='/run/judger/token')
    parser.add_argument('--state-dir', default='/app/data/judger-agent')
    parser.add_argument('--mount-conf', default='/opt/judger/mount.yaml')
    parser.add_argument('--cgroup-prefix', default='sapjudger')
    parser.add_argument('--memory-mb', type=int, default=640)
    parser.add_argument('--cpu', type=float, default=1)
    parser.add_argument('--cpu-sets', default='')
    parser.add_argument('--max-concurrency', type=int, default=1)
    parser.add_argument('--push-url', default='')
    parser.add_argument('--tls-cert', default='')
    parser.add_argument('--tls-key', default='')
    parser.add_argument('--initial-stopped', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,50}', args.cgroup_prefix) or not 1 <= args.max_concurrency <= 32:
        parser.error('Invalid cgroup prefix or concurrency')
    if args.cpu_sets and not re.fullmatch(r'\d+(?:,\d+)*', args.cpu_sets):
        parser.error('CPU sets must be a comma-separated list of individual CPU IDs')
    agent = Agent(args)
    context = None
    if args.tls_cert:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(args.tls_cert, args.tls_key)
    server = NodeServer((args.bind, args.port), agent, context)
    if agent.desired:
        agent.start()
    threading.Thread(target=agent.monitor, daemon=True).start()
    server.serve_forever()


if __name__ == '__main__':
    main()
