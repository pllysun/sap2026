#!/usr/bin/env python3
"""Local boundary tests: real HTTP/TLS Agent, synthetic execution engine.

Run: python3 -m unittest discover -s ops/judger -p 'test_agent_security.py' -v
SAP_JUDGE_CONTRACT optionally supplies requests captured by JudgeProtocolTest.
No production connections, host file imports or untrusted code execution.
"""
import argparse
from contextlib import contextmanager
import http.client
import importlib.util
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import threading
import time
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('judger_agent', ROOT / 'docker/judger-agent.py')
agent_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(agent_module)
TOKEN = 'local-test-token-' + 'x' * 32
LEASE = 'local-test-lease-' + 'y' * 24


def request_body():
    return {'cmd': [{'args': ['/usr/local/bin/python3', '/w/main.py'],
        'env': ['PATH=/usr/local/bin:/usr/bin:/bin', 'HOME=/w'],
        'files': [{'content': '42\n'}, {'name': 'stdout', 'max': 262144}, {'name': 'stderr', 'max': 65536}],
        'cpuLimit': 1000000000, 'clockLimit': 3000000000, 'memoryLimit': 128 * 1048576,
        'stackLimit': 32 * 1048576, 'procLimit': 32, 'strictMemoryLimit': True,
        'copyIn': {'main.py': {'content': 'print(input())'}}, 'copyOutCached': [], 'copyOutMax': 32 * 1048576}]}


def new_lease():
    return {'expires': time.monotonic() + 180, 'executing': 0, 'artifacts': set(), 'released': False}


class SyntheticAgent:
    reserve = agent_module.Agent.reserve

    def __init__(self):
        self.token, self.state = TOKEN, 'RUNNING'
        self.lock, self.leases = threading.RLock(), {LEASE: new_lease()}
        self.calls = []

    def status(self):
        return {'state': self.state, 'protocol': 1, 'runtimeId': agent_module.RUNTIME,
                'freeSlots': 2 - len(self.leases), 'capacity': 2, 'active': len(self.leases)}

    def engine(self, method, path, body=None):
        data = json.loads(body) if body else None
        self.calls.append((method, path, data))
        if method == 'DELETE':
            return b'{}'
        cmd = data['cmd'][0]
        return json.dumps([{'status': 'Accepted', 'files': {'stdout': cmd['files'][0]['content'], 'stderr': ''},
            'fileIds': {name: 'artifact-' + name.replace('.', '-') for name in cmd.get('copyOutCached', [])}}]).encode()


@contextmanager
def server(tls=None, **settings):
    agent = SyntheticAgent()
    httpd = agent_module.NodeServer(('127.0.0.1', 0), agent, tls, settings.pop('max_connections', 8))
    for key, value in settings.items():
        setattr(httpd, key, value)
    thread = threading.Thread(target=httpd.serve_forever, kwargs={'poll_interval': .01}, daemon=True)
    thread.start()
    try:
        yield httpd, agent
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(2)


def call(httpd, method='GET', path='/status', body=None, lease=LEASE, connection=None):
    conn = connection or http.client.HTTPConnection('127.0.0.1', httpd.server_port, timeout=2)
    try:
        conn.request(method, path, body=json.dumps(body).encode() if body is not None else None,
                     headers={'Authorization': 'Bearer ' + TOKEN, 'X-Judge-Lease': lease})
        response = conn.getresponse()
        return response.status, json.loads(response.read())
    finally:
        if connection is None:
            conn.close()


class RequestTests(unittest.TestCase):
    def test_normal_unicode_and_maximum_source_and_input(self):
        body = request_body()
        body['cmd'][0]['copyIn']['main.py']['content'] = '中' * 196608
        body['cmd'][0]['files'][0]['content'] = 'a' * 1048576
        self.assertEqual([], agent_module.validate_run(json.dumps(body, ensure_ascii=False).encode(), set()))

    def test_rejects_host_sources_in_every_file_position(self):
        for field in ('stdin', 'stdout', 'copyIn'):
            with self.subTest(field=field):
                body = request_body()
                cmd = body['cmd'][0]
                source = {'src': '/outside/fixture-canary'}
                if field == 'copyIn':
                    cmd[field]['main.py'] = source
                else:
                    cmd['files'][0 if field == 'stdin' else 1] = source
                with self.assertRaises(ValueError):
                    agent_module.validate_run(json.dumps(body).encode(), set())

    def test_duplicate_json_fields_cannot_hide_an_earlier_host_source(self):
        body = json.dumps(request_body()).replace('"copyIn":', '"copyIn":{"main.py":{"src":"/outside/canary"}},"copyIn":')
        with server() as (httpd, agent):
            conn = http.client.HTTPConnection('127.0.0.1', httpd.server_port, timeout=2)
            try:
                conn.request('POST', '/engine/run', body=body,
                             headers={'Authorization': 'Bearer ' + TOKEN, 'X-Judge-Lease': LEASE})
                response = conn.getresponse()
                self.assertEqual(400, response.status)
                response.read()
            finally:
                conn.close()
            self.assertEqual([], agent.calls)

    def test_rejects_protocol_extensions_and_path_traversal(self):
        variations = [({'pipeMapping': []}, None), ({}, {'symlinks': {'answer': '/etc/passwd'}}),
            ({}, {'copyOut': ['/etc/passwd']}), ({}, {'addressSpaceLimit': False}),
            ({}, {'copyIn': {'../main.py': {'content': ''}}}),
            ({}, {'args': ['/usr/bin/cat', '/etc/passwd']}), ({}, {'env': ['LD_PRELOAD=escape.so']}),
            ({}, {'strictMemoryLimit': False})]
        for top, command in variations:
            with self.subTest(top=top, command=command):
                body = request_body()
                body.update(top)
                body['cmd'][0].update(command or {})
                with self.assertRaises(ValueError):
                    agent_module.validate_run(json.dumps(body).encode(), set())
        body = request_body()
        body['cmd'] *= 2
        with self.assertRaises(ValueError):
            agent_module.validate_run(json.dumps(body).encode(), set())

    def test_resource_bounds_reject_missing_zero_negative_boolean_and_excess(self):
        for key in ('cpuLimit', 'clockLimit', 'memoryLimit', 'stackLimit', 'procLimit', 'copyOutMax'):
            for value in (None, 0, -1, True, 10**20):
                with self.subTest(key=key, value=value):
                    body = request_body()
                    body['cmd'][0][key] = value
                    with self.assertRaises(ValueError):
                        agent_module.validate_run(json.dumps(body).encode(), set())

    def test_compile_cache_run_and_cleanup_reuse_one_lease(self):
        with server() as (httpd, agent):
            for artifact in ('answer', 'answer.jar', 'answer'):
                body = request_body()
                body['cmd'][0]['copyOutCached'] = [artifact]
                status, response = call(httpd, 'POST', '/engine/run', body)
                self.assertEqual(200, status)
                file_id = response[0]['fileIds'][artifact]
                body['cmd'][0]['copyIn'] = {artifact: {'fileId': file_id}}
                body['cmd'][0]['copyOutCached'] = []
                self.assertEqual(200, call(httpd, 'POST', '/engine/run', body)[0])
                self.assertEqual(200, call(httpd, 'DELETE', '/engine/file/' + file_id)[0])
                self.assertFalse(agent.leases[LEASE]['artifacts'])
            self.assertEqual(9, len(agent.calls))

    def test_artifacts_cannot_cross_leases(self):
        with server() as (httpd, agent):
            agent.leases[LEASE]['artifacts'].add('owned-by-original')
            other = agent.reserve(agent_module.RUNTIME)
            body = request_body()
            body['cmd'][0]['copyIn'] = {'answer': {'fileId': 'owned-by-original'}}
            self.assertEqual(400, call(httpd, 'POST', '/engine/run', body, lease=other)[0])
            self.assertEqual(404, call(httpd, 'DELETE', '/engine/file/owned-by-original', lease=other)[0])
            self.assertEqual([], agent.calls)

    def test_invalid_requests_never_reach_engine(self):
        with server() as (httpd, agent):
            body = request_body()
            body['cmd'][0]['copyIn'] = {'main.py': {'src': '/outside/fixture-canary'}}
            self.assertEqual(400, call(httpd, 'POST', '/engine/run', body)[0])
            self.assertEqual([], agent.calls)

    def test_expired_lease_is_denied_without_monitor_cleanup(self):
        with server() as (httpd, agent):
            agent.leases[LEASE]['expires'] = time.monotonic() - .01
            self.assertEqual(409, call(httpd, 'POST', '/engine/run', request_body())[0])
            self.assertEqual([], agent.calls)

    def test_one_lease_cannot_reenter_but_another_can(self):
        with server() as (httpd, agent):
            entered, release = threading.Event(), threading.Event()
            engine = agent.engine
            def blocked(*args):
                if not entered.is_set():
                    entered.set()
                    self.assertTrue(release.wait(2))
                return engine(*args)
            agent.engine = blocked
            first = []
            worker = threading.Thread(target=lambda: first.append(call(httpd, 'POST', '/engine/run', request_body())))
            worker.start()
            try:
                self.assertTrue(entered.wait(1))
                self.assertEqual(409, call(httpd, 'POST', '/engine/run', request_body())[0])
                other = agent.reserve(agent_module.RUNTIME)
                self.assertEqual(200, call(httpd, 'POST', '/engine/run', request_body(), lease=other)[0])
                self.assertEqual(200, call(httpd)[0])
            finally:
                release.set()
                worker.join(2)
            self.assertEqual(200, first[0][0])

    def test_release_during_execution_keeps_capacity_reserved(self):
        with server() as (httpd, agent):
            entered, release = threading.Event(), threading.Event()
            engine = agent.engine
            def blocked(*args):
                entered.set()
                release.wait(2)
                return engine(*args)
            agent.engine = blocked
            result = []
            worker = threading.Thread(target=lambda: result.append(call(httpd, 'POST', '/engine/run', request_body())))
            worker.start()
            try:
                self.assertTrue(entered.wait(1))
                self.assertEqual(200, call(httpd, 'DELETE', '/leases/' + LEASE)[0])
                self.assertEqual(1, agent.status()['freeSlots'])
                self.assertEqual(409, call(httpd, 'POST', '/engine/run', request_body())[0])
            finally:
                release.set()
                worker.join(2)
            self.assertEqual(503, result[0][0])
            self.assertNotIn(LEASE, agent.leases)

    def test_actual_backend_language_contracts(self):
        path = os.environ.get('SAP_JUDGE_CONTRACT')
        if not path:
            self.skipTest('Set SAP_JUDGE_CONTRACT to JudgeProtocolTest output for cross-language contract validation')
        groups = json.loads(Path(path).read_text())
        self.assertEqual(10, len(groups))
        with server() as (httpd, agent):
            for group in groups:
                with self.subTest(language=group['language'], mode=group['mode']):
                    for req in group['requests']:
                        self.assertEqual(200, call(httpd, req['method'], req['path'], req.get('body'))[0])
                    self.assertFalse(agent.leases[LEASE]['artifacts'])


class ConnectionTests(unittest.TestCase):
    def test_normal_large_request_body_has_its_own_budget(self):
        with server() as (httpd, _):
            body = request_body()
            body['cmd'][0]['files'][0]['content'] = 'a' * 1048576
            body['cmd'][0]['copyIn']['main.py']['content'] = 'b' * 196608
            self.assertEqual(200, call(httpd, 'POST', '/engine/run', body)[0])

    def test_large_unauthenticated_headers_are_closed_at_total_byte_limit(self):
        with server(header_limit=4096) as (httpd, _):
            with socket.create_connection(httpd.server_address, timeout=1) as sock:
                sock.sendall(b'GET /status HTTP/1.1\r\n' + (b'X-Pad: ' + b'x' * 900 + b'\r\n') * 10 + b'\r\n')
                try:
                    self.assertEqual(b'', sock.recv(1))
                except ConnectionResetError:
                    pass
            self.assertEqual(200, call(httpd)[0])

    def test_http11_keepalive_preserves_normal_sequential_requests(self):
        with server() as (httpd, _):
            conn = http.client.HTTPConnection('127.0.0.1', httpd.server_port, timeout=2)
            try:
                self.assertEqual(200, call(httpd, connection=conn)[0])
                sock = conn.sock
                for _ in range(5):
                    self.assertEqual(200, call(httpd, 'POST', '/engine/run', request_body(), connection=conn)[0])
                    self.assertIs(sock, conn.sock)
            finally:
                conn.close()

    def test_framing_errors_and_authentication_close_the_connection(self):
        headers = ['Content-Length: -1', 'Content-Length: 1\r\nContent-Length: 1',
                   'Transfer-Encoding: chunked', 'Content-Length: 4194305', 'Authorization: wrong']
        for header in headers:
            with self.subTest(header=header), server() as (httpd, _):
                with socket.create_connection(httpd.server_address, timeout=1) as sock:
                    auth = '' if header.startswith('Authorization:') else 'Authorization: Bearer ' + TOKEN + '\r\n'
                    sock.sendall(('POST /engine/run HTTP/1.1\r\nHost: localhost\r\n' + auth + header + '\r\n\r\n').encode())
                    response = http.client.HTTPResponse(sock)
                    response.begin()
                    self.assertIn(response.status, (400, 401, 413))
                    self.assertEqual('close', response.getheader('Connection'))
                    response.read()
                    self.assertEqual(b'', sock.recv(1))

    def test_idle_connections_are_bounded_and_slots_are_recovered(self):
        with server(max_connections=2, header_timeout=.2) as (httpd, _):
            with socket.create_connection(httpd.server_address, timeout=1) as first:
                with socket.create_connection(httpd.server_address, timeout=1) as second:
                    deadline = time.monotonic() + 1
                    while httpd.slots._value and time.monotonic() < deadline:
                        time.sleep(.005)
                    self.assertEqual(0, httpd.slots._value)
                    with socket.create_connection(httpd.server_address, timeout=1) as excess:
                        self.assertEqual(b'', excess.recv(1))
                    self.assertEqual(b'', first.recv(1))
                    self.assertEqual(b'', second.recv(1))
            self.assertEqual(200, call(httpd)[0])

    def test_trickling_headers_cannot_extend_total_deadline(self):
        with server(header_timeout=.2) as (httpd, _):
            with socket.create_connection(httpd.server_address, timeout=1) as sock:
                started = time.monotonic()
                for _ in range(10):
                    try:
                        sock.sendall(b'G')
                    except OSError:
                        break
                    time.sleep(.04)
                try:
                    self.assertEqual(b'', sock.recv(1))
                except ConnectionResetError:
                    pass  # Both EOF and RST mean the deadline closed the peer.
                self.assertLess(time.monotonic() - started, .7)
            self.assertEqual(200, call(httpd)[0])

    def test_incomplete_body_times_out_and_does_not_reach_engine(self):
        with server(body_timeout=.15) as (httpd, agent):
            with socket.create_connection(httpd.server_address, timeout=1) as sock:
                sock.sendall(('POST /engine/run HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer ' + TOKEN +
                              '\r\nContent-Length: 100\r\n\r\n{').encode())
                response = http.client.HTTPResponse(sock)
                response.begin()
                self.assertEqual(400, response.status)
                response.read()
            self.assertEqual([], agent.calls)
            self.assertEqual(200, call(httpd)[0])

    def test_idle_tls_client_does_not_block_authenticated_tls_client(self):
        with tempfile.TemporaryDirectory() as tmp:
            cert, key = str(Path(tmp) / 'cert.pem'), str(Path(tmp) / 'key.pem')
            subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                            '-subj', '/CN=localhost', '-keyout', key, '-out', cert],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.load_cert_chain(cert, key)
            client = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            client.check_hostname = False
            client.verify_mode = ssl.CERT_NONE
            with server(context, handshake_timeout=.4) as (httpd, _):
                with socket.create_connection(httpd.server_address, timeout=1) as idle:
                    conn = http.client.HTTPSConnection('127.0.0.1', httpd.server_port, context=client, timeout=1)
                    try:
                        self.assertEqual(200, call(httpd, connection=conn)[0])
                    finally:
                        conn.close()
                    self.assertEqual(b'', idle.recv(1))


class StartupTests(unittest.TestCase):
    def config(self):
        return json.loads((ROOT / 'ops/security-audit/oj-sandbox-results.json').read_text())['engineConfig']

    def test_recorded_native_config_and_cgroup_v2_layout_are_accepted(self):
        cfg = self.config()
        agent_module.validate_engine_config(json.dumps(cfg))
        cfg['runnerConfig']['cgroupType'] = 2
        cfg['runnerConfig']['cgroupControllers'] = ['memory', 'pids', 'cpu']
        agent_module.validate_engine_config(json.dumps(cfg))

    def test_degraded_controllers_identity_and_mounts_are_rejected(self):
        for edit in ('pids', 'memory', 'cpu', 'uid', 'gid', 'readOnly', 'mountSource', 'missingMount', 'fixSymlinkEscape'):
            with self.subTest(edit=edit):
                cfg = self.config()
                r = cfg['runnerConfig']
                if edit in ('pids', 'memory', 'cpu'):
                    r['cgroupControllers'].remove(edit)
                elif edit in ('uid', 'gid'):
                    r[edit] = 0
                elif edit == 'readOnly':
                    r['mount'][0]['Flags'] &= ~1
                elif edit == 'mountSource':
                    r['mount'][0]['Source'] = '/usr'
                elif edit == 'missingMount':
                    r['mount'].pop()
                else:
                    cfg[edit] = False
                with self.assertRaises(ValueError):
                    agent_module.validate_engine_config(json.dumps(cfg))

    def make_agent(self, directory):
        token = directory / 'token'
        token.write_text(TOKEN)
        args = argparse.Namespace(token_file=str(token), state_dir=str(directory), initial_stopped=False,
            memory_mb=640, cpu=1, max_concurrency=1, cgroup_prefix='localfixture', cpu_sets='', engine_port=5050,
            mount_conf=str(directory / 'mount.yaml'))
        agent = agent_module.Agent(args)
        agent.budget = lambda: (640, 1, 1)
        agent.terminate = Mock()
        return agent

    def test_missing_mount_file_never_spawns_engine(self):
        with tempfile.TemporaryDirectory() as tmp:
            agent = self.make_agent(Path(tmp))
            with patch.object(agent_module.subprocess, 'Popen') as popen:
                agent.launch()
            popen.assert_not_called()
            self.assertEqual('ERROR', agent.state)

    def test_launch_restricts_source_prefix_and_checks_isolation_before_ready(self):
        with tempfile.TemporaryDirectory() as tmp:
            agent = self.make_agent(Path(tmp))
            Path(agent.args.mount_conf).touch()
            agent.engine = Mock(return_value=json.dumps(self.config()).encode())
            agent.verify_execution_isolation = Mock()
            # The launch wrapper's Linux cgroup filesystem is not touched on any host.
            exists = Path.exists
            def local_exists(path):
                return False if str(path) == '/sys/fs/cgroup/cgroup.controllers' else exists(path)
            with patch.object(Path, 'exists', local_exists), patch.object(agent_module.subprocess, 'Popen', return_value=Mock(poll=lambda: None)) as popen:
                agent.launch()
            self.assertEqual('RUNNING', agent.state)
            agent.verify_execution_isolation.assert_called_once()
            command = popen.call_args.args[0]
            self.assertEqual(str(Path(tmp) / 'inputs'), command[command.index('-src-prefix') + 1])
            self.assertEqual(0o700, (Path(tmp) / 'inputs').stat().st_mode & 0o777)

    def test_probe_rejects_missing_seccomp_caps_or_process_limit(self):
        for key, value in [('seccomp', '0'), ('nnp', '0'), ('caps', '1'), ('pidsLimited', False), ('net', 'outer')]:
            with self.subTest(key=key), tempfile.TemporaryDirectory() as tmp:
                agent = self.make_agent(Path(tmp))
                actual = {'uid': 1000, 'gid': 1000, 'caps': '0', 'nnp': '1', 'seccomp': '2', 'net': 'inner', 'pidsLimited': True}
                actual[key] = value
                agent.engine = Mock(return_value=json.dumps([{'status': 'Accepted', 'files': {'stdout': json.dumps(actual)}}]).encode())
                with patch.object(agent_module.os, 'readlink', return_value='outer'), self.assertRaises(ValueError):
                    agent.verify_execution_isolation()


if __name__ == '__main__':
    unittest.main(verbosity=2)
