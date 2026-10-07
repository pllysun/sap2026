#!/usr/bin/env python3
"""Deployment acceptance for the dedicated native Linux node; run on its host."""
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT = Path('/opt/sap-judger')
TOKEN = (ROOT / 'node.token').read_text().strip()


def api(route, body=None, lease=None, auth=True, method=None):
    headers = {'Content-Type': 'application/json'}
    if auth:
        headers['Authorization'] = 'Bearer ' + TOKEN
    if lease:
        headers['X-Judge-Lease'] = lease
    request = urllib.request.Request('http://127.0.0.1:5051' + route,
        data=json.dumps(body).encode() if body is not None else None,
        headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def wait(state):
    for _ in range(150):
        status = api('/status')[1]
        if status['state'] == state:
            return status
        if status['state'] == 'ERROR':
            raise RuntimeError(status['error'])
        time.sleep(.2)
    raise RuntimeError('Node state timed out: ' + state)


def command(args, cpu=2_000_000_000, memory=384):
    return {'cmd': [{'args': args,
        'env': ['PATH=/usr/local/bin:/usr/bin:/bin:/opt/rust/bin:/opt/java27/bin', 'HOME=/w'],
        'files': [{'content': ''}, {'name': 'stdout', 'max': 262144}, {'name': 'stderr', 'max': 65536}],
        'cpuLimit': cpu, 'clockLimit': 15_000_000_000, 'memoryLimit': memory * 1048576,
        'stackLimit': 32 * 1048576, 'procLimit': 32, 'strictMemoryLimit': True,
        'copyIn': {}, 'copyOutCached': [], 'copyOutMax': 33554432}]}


def python(source, **limits):
    return command(['/usr/local/bin/python3', '-c', source], **limits)


def acquire(runtime):
    code, response = api('/leases', {'runtimeId': runtime})
    assert code == 200, response
    return response['lease']


def release(lease):
    assert api('/leases/' + lease, method='DELETE')[0] == 200


def accepted(payload, lease):
    code, result = api('/engine/run', payload, lease)
    assert code == 200 and result[0]['status'] == 'Accepted', result
    return result[0]['files']['stdout']


def anonymous_memory():
    data = dict(line.split() for line in Path('/sys/fs/cgroup/sapjudgerpllysun/memory.stat').read_text().splitlines())
    return int(data['anon'])


def main():
    report = {'passed': False, 'native': True, 'cgroupVersion': 2, 'checks': []}
    assert Path('/sys/fs/cgroup/cgroup.controllers').exists()
    assert api('/status', auth=False)[0] == 401
    assert api('/start', {})[0] == 200
    status = wait('RUNNING')
    assert status['capacity'] == 8 and status['memoryMb'] == 6144 and status['cpuCores'] == 8
    runtime = status['runtimeId']
    lease = acquire(runtime)
    try:
        assert api('/engine/run', python('print(1)'))[0] == 409
        versions = {}
        for language, args, expected in [
            ('c', ['/usr/local/bin/gcc', '--version'], '15.3'),
            ('cpp', ['/usr/local/bin/g++', '--version'], '15.3'),
            ('java', ['/opt/java27/bin/java', '-Xmx64m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1', '--version'], '27'),
            ('python', ['/usr/local/bin/python3', '--version'], '3.14.7'),
            ('rust', ['/opt/rust/bin/rustc', '--version'], '1.98.1'),
        ]:
            output = accepted(command(args), lease)
            assert expected in output, language
            versions[language] = output.splitlines()[0]
        accepted(python('import os; assert not os.path.exists("/app"); assert not os.path.exists("/opt/sap-judger/node.token"); assert not any(k.startswith("SAP_") for k in os.environ); print("isolated")'), lease)
        code, result = api('/engine/run', python('while True: pass', cpu=100_000_000, memory=64), lease)
        assert code == 200 and 'Time Limit' in result[0]['status'], result
        report['versions'] = versions
        report['checks'].extend(['authenticated control and leased execution', 'five actual sandbox toolchain versions', 'private host paths and environment excluded', 'CPU time limit enforced'])
    finally:
        release(lease)

    leases = [acquire(runtime) for _ in range(8)]
    try:
        full = api('/status')[1]
        assert full['active'] == 8 and full['freeSlots'] == 0
        assert api('/leases', {'runtimeId': runtime})[0] == 409
        started = time.monotonic()
        with ThreadPoolExecutor(max_workers=8) as pool:
            outputs = list(pool.map(lambda pair: accepted(python('import os,time,json; time.sleep(2); print(json.dumps({"index":%d,"cpus":sorted(os.sched_getaffinity(0))}))' % pair[0], memory=128), pair[1]), enumerate(leases)))
        elapsed = time.monotonic() - started
        measured = [json.loads(output) for output in outputs]
        assert [item['index'] for item in measured] == list(range(8))
        assert all(len(item['cpus']) == 1 for item in measured), measured
        assert len({item['cpus'][0] for item in measured}) == 8, measured
        assert elapsed < 6, elapsed
        report['concurrency'] = {'simultaneousTasks': 8, 'distinctCpuSets': sorted(item['cpus'][0] for item in measured), 'ninthTaskRejected': True, 'elapsedSeconds': round(elapsed, 3)}
        report['checks'].append('eight parallel native executions and full-capacity rejection')
    finally:
        for lease in leases:
            release(lease)

    memory_before = anonymous_memory()
    assert api('/stop', {})[0] == 200
    wait('STOPPED')
    memory_after = anonymous_memory()
    assert memory_before > memory_after and memory_after < 1024 * 1024, (memory_before, memory_after)
    assert not any(p.read_text().strip() for p in Path('/sys/fs/cgroup/sapjudgerpllysun').rglob('cgroup.procs'))
    for cycle in range(3):
        assert api('/start', {})[0] == 200
        wait('RUNNING')
        lease = acquire(runtime)
        try:
            assert accepted(python('print(%d)' % cycle), lease).strip() == str(cycle)
        finally:
            release(lease)
        assert api('/stop', {})[0] == 200
        wait('STOPPED')
    report['memory'] = {'runningAnonymousBytes': memory_before, 'stoppedAnonymousBytes': memory_after}
    report['checks'].extend(['stop terminates every sandbox process and releases anonymous memory', 'three repeated stop/start cycles with real execution'])
    report['passed'] = True
    report['finishedAt'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    (ROOT / 'native-probe.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
