#!/usr/bin/env python3
"""Non-destructive OJ boundary tests; run on the app host over server-ssh.cjs.

Creates one disposable container from the exact running image. No app data,
credentials, production leases or submissions are used. The host sysctl is
never changed. All resource abuse is bounded by a dedicated cgroup plus outer
container limits. Only sacrificial files in this disposable container are used.
Python 3.6 compatible. Prints a single JSON evidence document.
"""
import collections
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import time
import uuid

REPORT = {'startedAt': time.strftime('%Y-%m-%dT%H:%M:%S+08:00'), 'tests': []}
PREFIX = 'sapojsec-' + uuid.uuid4().hex[:10]
NAME = PREFIX
TOKEN = 'audit-only-' + uuid.uuid4().hex
GROUPS = []
CREATED = False
TMP = tempfile.mkdtemp(prefix='sap-oj-security-')


def docker(*args):
    return subprocess.check_output(['docker', *args], encoding='utf-8').strip()


def inside(source, timeout=30):
    p = subprocess.run(['docker', 'exec', '-i', NAME, 'python3', '-'],
                       input=source, encoding='utf-8', stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, timeout=timeout)
    if p.returncode:
        raise RuntimeError('Disposable container command failed: ' + p.stderr[-2000:])
    return p.stdout.strip()


def api(path, payload=None, authenticated=True, method=None, timeout=25):
    source = '''import urllib.request,urllib.error,json
body = {body}
headers = {{'Content-Type':'application/json'}}
if {auth}: headers['Authorization'] = 'Bearer ' + {token}
r=urllib.request.Request('http://127.0.0.1:5050' + {path},
    data=json.dumps(body).encode() if body is not None else None,
    headers=headers, method={method})
try:
    with urllib.request.urlopen(r, timeout={timeout}) as response:
        raw=response.read()
        print(json.dumps({{'code':response.status,'data':json.loads(raw) if raw else None}}))
except urllib.error.HTTPError as e:
    print(json.dumps({{'code':e.code,'error':'HTTP request rejected'}}))
'''.format(body=repr(payload), auth=repr(authenticated), token=repr(TOKEN),
           path=repr(path), method=repr(method), timeout=timeout)
    return json.loads(inside(source, timeout + 5))


def command(args, inputs=None, stdin='', cpu_ms=1200, memory_mb=96,
            processes=16, stdout_max=32768, cached=None):
    c = {'args': args,
         'env': ['PATH=/usr/local/bin:/usr/bin:/bin:/opt/rust/bin:/opt/java27/bin',
                 'HOME=/w', 'LANG=C.UTF-8', 'TMPDIR=/tmp'],
         'files': [{'content': stdin}, {'name': 'stdout', 'max': stdout_max},
                   {'name': 'stderr', 'max': 65536}],
         'cpuLimit': cpu_ms * 1000000,
         'clockLimit': max(3000, cpu_ms * 3) * 1000000,
         'memoryLimit': memory_mb * 1048576, 'stackLimit': 32 * 1048576,
         'procLimit': processes, 'strictMemoryLimit': True,
         'copyIn': inputs or {}, 'copyOutCached': cached or [],
         'copyOutMax': 32 * 1048576}
    result = api('/run', {'cmd': [c]}, timeout=max(15, cpu_ms * 3 // 1000 + 5))
    if result['code'] != 200:
        raise RuntimeError('Sandbox API rejected bounded test: ' + str(result))
    return result['data'][0]


def python(code, **kw):
    return command(['/usr/local/bin/python3', '/w/main.py'],
                   {'main.py': {'content': code}}, **kw)


def record(name, category, result, check, note=''):
    ok = bool(check(result))
    REPORT['tests'].append({'name': name, 'category': category,
                            'passed': ok, 'result': result, 'note': note})
    if not ok:
        REPORT.setdefault('failedTests', []).append(name)
    return ok


def accepted(result):
    return result.get('status') == 'Accepted'


def out(result):
    return result.get('files', {}).get('stdout', '').strip()


def smoke(language, filename, source, compile_args, run_args, artifacts):
    inputs = {filename: {'content': source}}
    if compile_args:
        result = command(compile_args, inputs, cpu_ms=10000,
                         memory_mb=320, processes=64, cached=artifacts)
        record(language + '_compile', 'language', result, accepted,
               'Compile inside sandbox. Outer test budget is stricter than production.')
        if not accepted(result):
            return
        inputs = {name: {'fileId': result['fileIds'][name]} for name in artifacts}
    result = command(run_args, inputs, memory_mb=256, processes=32)
    record(language + '_run', 'language', result,
           lambda r: accepted(r) and out(r) == '42')
    for value in inputs.values():
        if 'fileId' in value:
            api('/file/' + value['fileId'], method='DELETE')


try:
    current = json.loads(docker('inspect', 'sap'))[0]
    image = current['Image']
    host = current['HostConfig']
    seccomp = next(s[8:] for s in host.get('SecurityOpt', []) if s.startswith('seccomp='))
    seccomp_path = os.path.join(TMP, 'seccomp.json')
    with open(seccomp_path, 'w') as f:
        f.write(seccomp)
    REPORT['environment'] = {'productionImage': current['Config']['Image'],
        'exactImageId': image, 'kernel': os.uname().release,
        'productionHealthyBefore': current['State'].get('Health', {}).get('Status'),
        'outerTestMemoryMb': 448, 'sandboxGroupMemoryMb': 384,
        'outerTestCpuCores': 0.5, 'outerTestPids': 192,
        'productionDataMounted': False, 'hostSysctlChanged': False,
        'network': 'outer network none; additionally verify inner socket syscall denial',
        'seccompSha256': hashlib.sha256(seccomp.encode()).hexdigest()}
    if os.path.exists('/sys/fs/cgroup/cgroup.controllers'):
        raise RuntimeError('This disposable test runner expects the verified cgroup v1 host')
    if int(open('/proc/sys/user/max_user_namespaces').read()) < 1:
        raise RuntimeError('User namespaces unavailable; do not change host sysctl during audit')
    seen = set()
    mounts = []
    for controller in ['memory', 'pids', 'cpu', 'cpuacct', 'cpuset']:
        parent = os.path.realpath('/sys/fs/cgroup/' + controller)
        path = parent + '/' + PREFIX
        if parent in seen:
            continue
        seen.add(parent)
        if not os.path.isdir(parent):
            raise RuntimeError('Missing host cgroup controller: ' + controller)
        os.mkdir(path)
        GROUPS.append(path)
        for key, value in {'memory.limit_in_bytes': str(384 * 1048576),
                           'pids.max': '192', 'cpu.cfs_period_us': '100000',
                           'cpu.cfs_quota_us': '50000'}.items():
            if os.path.isfile(path + '/' + key):
                with open(path + '/' + key, 'w') as f:
                    f.write(value)
        for key in ['cpuset.cpus', 'cpuset.mems']:
            if os.path.isfile(path + '/' + key):
                with open(path + '/' + key, 'w') as f:
                    f.write('0' if key == 'cpuset.cpus' else open(parent + '/' + key).read())
        mounts += ['-v', path + ':' + parent + ':rw']
    args = ['run', '-d', '--name', NAME, '--label', 'sap.security.audit=20261003',
            '--network=none', '--no-healthcheck', '--memory=448m', '--memory-swap=448m',
            '--cpus=0.5', '--cpuset-cpus=0', '--pids-limit=192', '--shm-size=32m',
            '--cap-add=SYS_ADMIN', '--cap-add=SYS_PTRACE', '--cap-add=SYS_RESOURCE',
            '--security-opt=systempaths=unconfined', '--security-opt=seccomp=' + seccomp_path,
            '-e', 'ES_AUTH_TOKEN=' + TOKEN, '-e', 'MYSQL_PASSWORD=dummy-audit-fixture',
            '--entrypoint', '/opt/judger/go-judge'] + mounts + [image,
            '-http-addr', '127.0.0.1:5050', '-parallelism', '1', '-pre-fork', '1',
            '-cgroup-prefix', PREFIX, '-container-cred-start', '10000', '-no-fallback',
            '-mount-conf', '/opt/judger/mount.yaml', '-cpuset', '0',
            '-output-limit', '32m', '-copy-out-limit', '32m',
            '-dir', '/tmp/audit-cache', '-file-timeout', '10m',
            '-tmp-fs-param', 'size=64m,nr_inodes=8k']
    docker(*args)
    CREATED = True
    for _ in range(40):
        try:
            response = api('/config', timeout=2)
            if response['code'] == 200:
                REPORT['engineConfig'] = response['data']
                break
        except Exception:
            pass
        if json.loads(docker('inspect', NAME))[0]['State']['Status'] == 'exited':
            raise RuntimeError('Disposable engine exited: ' + docker('logs', NAME)[-2500:])
        time.sleep(.25)
    else:
        raise RuntimeError('Disposable engine unavailable')
    inside("import os\nopen('/app/SECURITY_AUDIT_CANARY','w').write('sacrificial-outer-container-canary')\nos.makedirs('/run/judger',exist_ok=True)\nopen('/run/judger/token','w').write('FAKE-TEST-TOKEN-ONLY')")
    record('engine_run_without_token', 'authentication',
           api('/run', {'cmd': []}, authenticated=False), lambda r: r['code'] == 401,
           'Upstream /config is intentionally registered before auth middleware; execution must require auth.')
    result = python("import os,json\ns=open('/proc/self/status').read()\nkeys=['CapEff','CapBnd','NoNewPrivs','Seccomp','Uid','Gid']\nprint(json.dumps({k:next((x.split(':',1)[1].strip() for x in s.splitlines() if x.startswith(k+':')),None) for k in keys}))")
    record('privilege_status', 'isolation', result,
           lambda r: accepted(r) and json.loads(out(r))['CapEff'] == '0000000000000000'
           and json.loads(out(r))['NoNewPrivs'] == '1' and json.loads(out(r))['Seccomp'] == '2')
    result = python("import os,json\npaths=['/app/SECURITY_AUDIT_CANARY','/app/app.jar','/run/judger/token','/app/data','/var/run/docker.sock','/sys/fs/cgroup','/opt/judger/rootfs','/proc/1/root/app/SECURITY_AUDIT_CANARY','/proc/1/environ']\nseen={}\nfor p in paths:\n try:\n  with open(p,'rb') as f: f.read(1)\n  seen[p]='READABLE'\n except OSError as e: seen[p]=e.errno\nprint(json.dumps(seen))")
    record('private_file_reads', 'isolation', result,
           lambda r: accepted(r) and 'READABLE' not in out(r))
    result = python("import os,json\nprint(json.dumps({k:v for k,v in os.environ.items() if k.startswith(('MYSQL_','SAP_','ES_','SPRING_','AWS_','NODE_'))}))")
    record('private_environment', 'isolation', result,
           lambda r: accepted(r) and out(r) == '{}')
    result = python("import os,json,shutil\nos.mkdir('/w/sacrificial');open('/w/sacrificial/canary','w').write('test')\nshutil.rmtree('/w/sacrificial')\nr={'ownTemporaryFilesDeleted':not os.path.exists('/w/sacrificial')}\ntry:os.unlink('/app/SECURITY_AUDIT_CANARY');r['outerCanaryDeleted']=True\nexcept OSError as e:r['outerCanaryDeleted']=False;r['errno']=e.errno\nprint(json.dumps(r))")
    record('bounded_destructive_file_operation', 'destructive-code', result,
           lambda r: accepted(r) and json.loads(out(r))['ownTemporaryFilesDeleted']
           and not json.loads(out(r))['outerCanaryDeleted'],
           'Deleting own disposable workspace is allowed; outer canary must remain protected.')
    result = python("import os,json\nr={}\nfor p in ['/usr/local/bin/AUDIT_WRITE','/etc/AUDIT_WRITE','/proc/sys/kernel/hostname']:\n try:open(p,'w').write('audit');r[p]='WRITABLE'\n except OSError as e:r[p]=e.errno\nprint(json.dumps(r))")
    record('readonly_toolchain_and_proc', 'isolation', result,
           lambda r: accepted(r) and 'WRITABLE' not in out(r))
    docker('exec','-d',NAME,'python3','-c',
           "from http.server import HTTPServer,BaseHTTPRequestHandler;HTTPServer(('127.0.0.1',18081),BaseHTTPRequestHandler).serve_forever()")
    result = python("import socket,json\nr={}\nfor name,addr in [('outerLoopback',('127.0.0.1',18081)),('engineLoopback',('127.0.0.1',5050)),('metadata',('169.254.169.254',80)),('documentationAddress',('192.0.2.1',80))]:\n s=socket.socket();s.settimeout(.2)\n try:s.connect(addr);r[name]='CONNECTED'\n except OSError as e:r[name]=e.errno\n finally:s.close()\nprint(json.dumps(r))")
    record('network_namespace_connections', 'network', result,
           lambda r: accepted(r) and 'CONNECTED' not in out(r),
           'Socket creation is permitted; independent network namespace blocks parent loopback and destinations. Outer network=none additionally prevents external traffic.')
    result = python("import os,ctypes,json\nc=ctypes.CDLL(None,use_errno=True);r={}\nfor name,flags in [('mount',0x20000),('network',0x40000000),('user',0x10000000)]:\n ctypes.set_errno(0);v=c.unshare(flags);r[name]={'return':v,'errno':ctypes.get_errno()}\nprint(json.dumps(r))")
    record('nested_namespace_attempts', 'syscalls', result,
           lambda r: accepted(r) and all(v['return'] != 0 for v in json.loads(out(r)).values()),
           'A successful namespace creation is a surface finding, not proof of escape.')
    result = python("import ctypes,json,os\nc=ctypes.CDLL(None,use_errno=True);r={}\nfor name in ['setuid','setgid']:\n ctypes.set_errno(0);v=getattr(c,name)(0);r[name]={'return':v,'errno':ctypes.get_errno()}\nr['uid']=os.getuid();r['gid']=os.getgid();print(json.dumps(r))")
    record('identity_escalation', 'syscalls', result,
           lambda r: accepted(r) and json.loads(out(r))['uid'] != 0 and json.loads(out(r))['gid'] != 0)
    result = python("import ctypes,json\nc=ctypes.CDLL(None,use_errno=True);r={}\nctypes.set_errno(0);v=c.mount(b'none',b'/tmp',b'tmpfs',0,None);r['mount']={'return':v,'errno':ctypes.get_errno()}\nctypes.set_errno(0);v=c.ptrace(0,0,None,None);r['ptrace']={'return':v,'errno':ctypes.get_errno()}\nprint(json.dumps(r))")
    record('mount_and_ptrace', 'syscalls', result,
           lambda r: accepted(r) and all(v['return'] != 0 for v in json.loads(out(r)).values()))
    result = python("import os,json\nfd={}\ntry:\n for f in os.listdir('/proc/1/fd'):\n  try:fd[f]=os.readlink('/proc/1/fd/'+f)\n  except OSError as e:fd[f]='errno '+str(e.errno)\nexcept OSError as e:fd['directoryAccessDenied']=e.errno\nprint(json.dumps({'pid':os.getpid(),'pid1fds':fd,'processIds':[p for p in os.listdir('/proc') if p.isdigit()]}))")
    record('pid_namespace', 'isolation', result,
           lambda r: accepted(r) and len(json.loads(out(r))['processIds']) <= 4,
           'Evidence records sandbox-visible process/fd set; does not print parent process environments.')
    record('persistent_marker_write', 'cleanup', python("open('/tmp/AUDIT_MARKER','w').write('test');open('/w/AUDIT_MARKER','w').write('test');print('created')"),
           lambda r: accepted(r) and out(r) == 'created')
    record('persistent_marker_next_request', 'cleanup', python("import os;assert not os.path.exists('/tmp/AUDIT_MARKER');assert not os.path.exists('/w/AUDIT_MARKER');print('clean')"),
           lambda r: accepted(r) and out(r) == 'clean')
    record('cpu_loop_limit', 'resource', python('while True: pass', cpu_ms=100),
           lambda r: r['status'] == 'Time Limit Exceeded')
    record('wall_sleep_limit', 'resource', python('import time;time.sleep(8)', cpu_ms=100),
           lambda r: r['status'] == 'Time Limit Exceeded')
    record('memory_allocation_limit', 'resource', python('a=bytearray(96*1024*1024);print(len(a))', memory_mb=32),
           lambda r: r['status'] == 'Memory Limit Exceeded')
    record('output_flood_limit', 'resource', python("import os\nfor i in range(512):os.write(1,b'X'*1024)", stdout_max=4096),
           lambda r: r['status'] == 'Output Limit Exceeded')
    result = python("import subprocess,json\na=[];limited=False\ntry:\n for i in range(32):a.append(subprocess.Popen(['/bin/sleep','2']))\nexcept OSError:limited=True\nfinally:\n for p in a:p.terminate()\n for p in a:p.wait()\nprint(json.dumps({'spawned':len(a),'limited':limited}))", processes=8)
    record('bounded_process_limit', 'resource', result,
           lambda r: accepted(r) and json.loads(out(r))['limited'] and json.loads(out(r))['spawned'] < 32)
    result = python("import os,json\nn=0;r={}\ntry:\n with open('/w/disk-test','wb') as f:\n  for i in range(80):f.write(b'X'*1048576);f.flush();n+=1\n r={'boundedWriteFinished':True,'megabytes':n}\nexcept OSError as e:r={'errno':e.errno,'megabytes':n}\nprint(json.dumps(r))", memory_mb=160, cpu_ms=3000)
    record('bounded_disk_limit', 'resource', result,
           lambda r: accepted(r) and json.loads(out(r)).get('errno') in (27,28)
           and json.loads(out(r))['megabytes'] <= 64)
    result = python("import os,json\nn=0\ntry:\n for i in range(9000):open('/w/f'+str(i),'w').close();n+=1\n print(json.dumps({'created':n,'limited':False}))\nexcept OSError as e:print(json.dumps({'created':n,'limited':True,'errno':e.errno}))", cpu_ms=3000)
    record('bounded_inode_limit', 'resource', result,
           lambda r: accepted(r) and json.loads(out(r))['limited']
           and json.loads(out(r)).get('errno') == 28)
    record('child_process_cleanup', 'cleanup', python("import subprocess;subprocess.Popen(['/bin/sleep','8']);print('child-started')"),
           lambda r: accepted(r) and out(r) == 'child-started')
    time.sleep(.5)
    processes = inside("import os,json\nr=[]\nfor p in os.listdir('/proc'):\n if p.isdigit():\n  try:\n   a=open('/proc/'+p+'/cmdline','rb').read().decode(errors='replace')\n   if '/bin/sleep' in a and a.split('\\x00')[0]=='/bin/sleep':r.append(int(p))\n  except OSError:pass\nprint(json.dumps(r))")
    record('no_lingering_children', 'cleanup', {'pids': json.loads(processes)}, lambda r: not r['pids'])
    result = python("import os;os.symlink('/app/SECURITY_AUDIT_CANARY','/w/leak');print('symlink-created')",
                    cached=['leak'])
    record('copyout_symlink_to_outer_canary', 'isolation', result,
           lambda r: 'sacrificial-outer-container-canary' not in json.dumps(r)
           and not r.get('fileIds',{}).get('leak'),
           'Current engine has fixSymlinkEscape enabled. Sacrificial canary only.')
    # This is a privileged control-plane test: possession of the disposable
    # engine/node token is required. Never read any real token/configuration.
    payload = {'cmd': [{'args': ['/usr/bin/cat'], 'env': ['PATH=/usr/bin:/bin'],
               'files': [{'src': '/app/SECURITY_AUDIT_CANARY'},
                         {'name': 'stdout', 'max': 4096}, {'name': 'stderr', 'max': 4096}],
               'cpuLimit': 100000000, 'clockLimit': 3000000000,
               'memoryLimit': 32 * 1048576, 'procLimit': 4}]}
    control = api('/run', payload)
    record('control_plane_host_source_canary', 'control-plane', control,
           lambda r: r['code'] != 200 or 'sacrificial-outer-container-canary' not in json.dumps(r),
           'Failure means a token holder can import arbitrary outer-container files. Ordinary OJ users do not send engine payloads.')
    smoke('c', 'main.c', '#include <stdio.h>\nint main(){puts("42");}',
          ['/usr/local/bin/gcc','-std=c23','main.c','-o','answer'], ['/w/answer'], ['answer'])
    smoke('cpp', 'main.cpp', '#include <iostream>\nint main(){std::cout<<42;}',
          ['/usr/local/bin/g++','-std=c++23','main.cpp','-o','answer'], ['/w/answer'], ['answer'])
    smoke('python', 'main.py', 'print(42)', None, ['/usr/local/bin/python3','/w/main.py'], [])
    smoke('java', 'Main.java', 'public class Main{public static void main(String[] a){System.out.println(42);}}',
          ['/bin/sh','-c','/opt/java27/bin/javac -J-Xmx96m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 Main.java && /opt/java27/bin/jar c *.class > answer.jar'],
          ['/opt/java27/bin/java','-Xmx96m','-Xss512k','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:MaxMetaspaceSize=64m','-XX:ReservedCodeCacheSize=24m','-XX:CompressedClassSpaceSize=32m','-cp','/w/answer.jar','Main'], ['answer.jar'])
    smoke('rust', 'main.rs', 'fn main(){println!("42");}',
          ['/opt/rust/bin/rustc','--edition=2024','main.rs','-o','answer'], ['/w/answer'], ['answer'])
    canary = inside("import os,json;print(json.dumps({'outerCanaryExists':os.path.exists('/app/SECURITY_AUDIT_CANARY')}))")
    record('outer_canary_integrity', 'cleanup', json.loads(canary), lambda r: r['outerCanaryExists'])
except Exception as e:
    REPORT['runnerError'] = str(e)
finally:
    if CREATED:
        subprocess.run(['docker', 'rm', '-fv', NAME], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    residual = []
    for path in GROUPS:
        for root, dirs, files in os.walk(path, topdown=False):
            try:
                os.rmdir(root)
            except OSError:
                residual.append(root)
    shutil.rmtree(TMP, ignore_errors=True)
    try:
        now = json.loads(docker('inspect', 'sap'))[0]
        REPORT['cleanup'] = {'temporaryContainerRemoved': not docker('ps','-aq','--filter','name=^/'+NAME+'$'),
            'residualCgroups': residual, 'temporaryFilesRemoved': not os.path.exists(TMP),
            'productionHealthyAfter': now['State'].get('Health',{}).get('Status'),
            'productionContainerUnchanged': now['Id'] == current['Id'],
            'productionRestartCount': now['RestartCount']}
    except Exception as e:
        REPORT['cleanupError'] = str(e)
    REPORT['finishedAt'] = time.strftime('%Y-%m-%dT%H:%M:%S+08:00')
    REPORT['summary'] = {'testCount':len(REPORT['tests']),
        'passed':sum(t['passed'] for t in REPORT['tests']),
        'failed':sum(not t['passed'] for t in REPORT['tests']),
        'categories':dict(collections.Counter(t['category'] for t in REPORT['tests']))}
    print(json.dumps(REPORT, ensure_ascii=True, indent=2), flush=True)
