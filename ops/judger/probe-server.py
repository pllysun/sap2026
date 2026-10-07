"""Run via existing SSH helper: python3 - < probe-server.py.

Temporary, unpublished sandbox probe. Never touches the live app or its data.
Restores the namespace sysctl and removes its container and cgroups on exit.
Python 3.6 compatible for the current CentOS server.
"""
import json
import os
import subprocess
import time
import urllib.request

NAME = 'sap-judger-probe'
PREFIX = 'sapjudger-probe'
run = lambda *a: subprocess.check_output(a, encoding='utf-8').strip()
namespace_path = '/proc/sys/user/max_user_namespaces'
old_namespace_limit = open(namespace_path).read().strip()
cgroup_paths = []
results = {}
image = os.environ.get('SAP_PROBE_IMAGE', 'pllysun/sap:1.4.114')

def command_api(args, cpu=1000000000, memory=67108864, processes=16, copy_in=None, cached=None):
    payload = {'cmd': [{'args': args,
        'env': ['PATH=/usr/bin:/bin', 'HOME=/w', 'LANG=C.UTF-8'],
        'files': [{'content': ''}, {'name': 'stdout', 'max': 16384}, {'name': 'stderr', 'max': 16384}],
        'cpuLimit': cpu, 'clockLimit': max(3000000000, cpu * 3),
        'memoryLimit': memory, 'stackLimit': 16777216, 'procLimit': processes,
        'strictMemoryLimit': True}]}
    if copy_in:
        payload['cmd'][0]['copyIn'] = copy_in
    if cached:
        payload['cmd'][0]['copyOutCached'] = cached
    request = urllib.request.Request('http://' + address + ':5050/run',
        json.dumps(payload).encode(), {'Content-Type': 'application/json', 'Authorization': 'Bearer probe-only'})
    return json.loads(urllib.request.urlopen(request, timeout=15).read())[0]

def api(command, **kwargs):
    return command_api(['/usr/bin/python3', '-c', command], **kwargs)

try:
    if run('docker', 'ps', '-aq', '--filter', 'name=^/' + NAME + '$'):
        raise RuntimeError('Existing probe container; refusing to replace it')
    # Root-only temporary folders; writable mounts expose this prefix only.
    for controller in ['memory', 'pids', 'cpu', 'cpuacct', 'cpuset']:
        parent = os.path.realpath('/sys/fs/cgroup/' + controller)
        path = parent + '/' + PREFIX
        if path not in cgroup_paths and os.path.isdir(parent):
            os.makedirs(path, exist_ok=True)
            cgroup_paths.append(path)
            limits = {'memory.limit_in_bytes': str(640 * 1024 * 1024),
                      'pids.max': '256', 'cpu.cfs_period_us': '100000', 'cpu.cfs_quota_us': '100000'}
            for file, value in limits.items():
                if os.path.isfile(path + '/' + file):
                    with open(path + '/' + file, 'w') as target:
                        target.write(value)
            if controller == 'cpuset':
                for file in ['cpuset.cpus', 'cpuset.mems']:
                    with open(path + '/' + file, 'w') as target:
                        target.write(open(parent + '/' + file).read())
    with open(namespace_path, 'w') as target:
        target.write('1024')
    args = ['docker', 'run', '-d', '--name', NAME,
        '--cap-add=SYS_ADMIN', '--cap-add=SYS_PTRACE', '--cap-add=SYS_RESOURCE',
        '--security-opt=systempaths=unconfined',
        '--security-opt=seccomp=/tmp/sap-oj-probe/seccomp.json',
        '--memory=640m', '--cpus=1', '--pids-limit=256', '--shm-size=64m',
        '-v', '/tmp/sap-oj-probe/go-judge:/opt/judger/go-judge:ro']
    for path in cgroup_paths:
        args += ['-v', path + ':' + os.path.dirname(path) + ':rw']
    if os.environ.get('SAP_PROBE_TOOLCHAINS'):
        mounts = [{'type': 'bind', 'source': p, 'target': p, 'readonly': True}
                  for p in ['/bin', '/lib', '/lib64', '/usr', '/etc/ld.so.cache',
                            '/etc/alternatives', '/opt/rust', '/opt/java27']]
        mounts += [{'type': 'bind', 'source': p, 'target': p, 'readonly': False}
                   for p in ['/dev/null', '/dev/zero', '/dev/random', '/dev/urandom', '/dev/full']]
        mounts += [{'type': 'tmpfs', 'target': p, 'data': 'size=64m,nr_inodes=8k'}
                   for p in ['/w', '/tmp']]
        with open('/tmp/sap-oj-probe/mount.yaml', 'w') as target:
            json.dump({'mount': mounts, 'proc': True, 'workDir': '/w', 'uid': 1000, 'gid': 1000,
                'maskPath': ['/proc/kcore', '/proc/keys', '/proc/timer_list', '/proc/sched_debug']}, target)
        args += ['-v', '/tmp/sap-oj-probe/mount.yaml:/opt/judger/mount.yaml:ro']
    args += ['--entrypoint', '/opt/judger/go-judge', image,
        '-http-addr', '0.0.0.0:5050', '-auth-token', 'probe-only',
        '-parallelism', '1', '-pre-fork', '1', '-cgroup-prefix', PREFIX,
        '-container-cred-start', '10000', '-no-fallback',
        '-tmp-fs-param', 'size=32m,nr_inodes=4k', '-output-limit', '32m', '-copy-out-limit', '32m']
    if os.environ.get('SAP_PROBE_TOOLCHAINS'):
        args += ['-mount-conf', '/opt/judger/mount.yaml']
    run(*args)
    info = json.loads(run('docker', 'inspect', NAME))[0]
    address = info['NetworkSettings']['IPAddress']
    for attempt in range(30):
        try:
            req = urllib.request.Request('http://' + address + ':5050/config',
                headers={'Authorization': 'Bearer probe-only'})
            config = json.loads(urllib.request.urlopen(req, timeout=2).read())
            break
        except Exception:
            if json.loads(run('docker', 'inspect', NAME))[0]['State']['Status'] == 'exited':
                raise RuntimeError('Probe exited: ' + run('docker', 'logs', NAME)[-4000:])
            time.sleep(1)
    else:
        raise RuntimeError('Sandbox API did not become ready')
    results['config'] = config
    tests = {
        'basic': "print(2+3)",
        'filesystem': "import os; assert not os.path.exists('/app/app.jar'); assert not os.path.exists('/proc/1/root/app/app.jar'); print('isolated')",
        'network': "import socket\ns=socket.socket();s.settimeout(1)\ntry:\n s.connect(('127.0.0.1',8081));raise AssertionError('network escaped')\nexcept OSError: print('isolated')",
        'environment': "import os;assert not any(k.startswith(('SAP_','MYSQL_','JW_','SPRING_')) for k in os.environ);print('isolated')",
        'cpuLimit': "while True: pass",
        'memoryLimit': "a=bytearray(128*1024*1024);print(len(a))",
        'processLimit': "import subprocess\ntry:\n children=[subprocess.Popen(['/bin/sleep','1']) for _ in range(100)];raise AssertionError('process limit ineffective')\nexcept OSError: print('limited')",
    }
    for name, code in tests.items():
        res = api(code, cpu=150000000 if name == 'cpuLimit' else 1000000000,
                  memory=33554432 if name == 'memoryLimit' else 67108864,
                  processes=8 if name == 'processLimit' else 16)
        results[name] = res
        if name == 'cpuLimit':
            assert res['status'] == 'Time Limit Exceeded', res
        elif name == 'memoryLimit':
            assert res['status'] == 'Memory Limit Exceeded', res
        else:
            assert res['status'] == 'Accepted', res
            expected = '5' if name == 'basic' else 'limited' if name == 'processLimit' else 'isolated'
            assert res.get('files', {}).get('stdout', '').strip() == expected, res
    results['passed'] = True
    if os.environ.get('SAP_PROBE_TOOLCHAINS'):
        programs = {
            'c': ('main.c', '#include <stdio.h>\nint main(){puts("5");}', ['/usr/local/bin/gcc', '-std=c23', 'main.c', '-o', 'answer'], ['/w/answer']),
            'cpp': ('main.cpp', '#include <iostream>\nint main(){std::cout<<5;}', ['/usr/local/bin/g++', '-std=c++23', 'main.cpp', '-o', 'answer'], ['/w/answer']),
            'rust': ('main.rs', 'fn main(){println!("5");}', ['/opt/rust/bin/rustc', '--edition=2024', 'main.rs', '-o', 'answer'], ['/w/answer']),
            'java': ('Main.java', 'public class Main{public static void main(String[] a){System.out.println(5);}}', ['/opt/java27/bin/javac', '-J-Xmx96m', '-J-XX:+UseSerialGC', '-J-XX:ActiveProcessorCount=1', 'Main.java'], ['/opt/java27/bin/java', '-Xmx96m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1', '-XX:MaxMetaspaceSize=64m', '-XX:ReservedCodeCacheSize=24m', '-XX:CompressedClassSpaceSize=32m', '-cp', '/w', 'Main']),
            'python': ('main.py', 'print(5)', None, ['/usr/local/bin/python3', '/w/main.py']),
        }
        results['toolchains'] = {}
        for language, (filename, source, compile_args, execute_args) in programs.items():
            inputs = {filename: {'content': source}}
            if compile_args:
                artifact = 'Main.class' if language == 'java' else 'answer'
                compiled = command_api(compile_args, cpu=10000000000, memory=536870912,
                    processes=64, copy_in=inputs, cached=[artifact])
                assert compiled['status'] == 'Accepted', (language, compiled)
                inputs = {artifact: {'fileId': compiled['fileIds'][artifact]}}
            executed = command_api(execute_args, cpu=2000000000, memory=268435456,
                processes=64, copy_in=inputs)
            results['toolchains'][language] = executed
            assert executed['status'] == 'Accepted', (language, executed)
            assert executed['files']['stdout'].strip() == '5', (language, executed)
    print(json.dumps(results, indent=2), flush=True)
finally:
    subprocess.call(['docker', 'rm', '-f', NAME], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    with open(namespace_path, 'w') as target:
        target.write(old_namespace_limit)
    for path in cgroup_paths:
        for root, dirs, files in os.walk(path, topdown=False):
            try:
                os.rmdir(root)
            except OSError:
                pass
