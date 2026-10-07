#!/usr/bin/env python3
"""Read-only production-source boundary probes; localhost mock engine, no real code execution.

This never starts Agent.launch(), touches production, or accesses real credentials.
All sockets bind to a random loopback port and all leases/tokens are synthetic.
"""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import http.client
import http.server
import importlib.util
import json
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docker/judger-agent.py'
spec = importlib.util.spec_from_file_location('audited_agent', SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
TOKEN = 'audit-synthetic-token-' + 'x' * 40
LEASE = 'audit_synthetic_lease_' + 'x' * 20


class FakeAgent:
    def __init__(self):
        self.token, self.state, self.lock = TOKEN, 'RUNNING', threading.RLock()
        self.leases = {LEASE: {'executing': 0, 'expires': time.monotonic() + 180}}
        self.calls, self.max_inflight, self.inflight, self.delay = [], 0, 0, 0

    def engine(self, method, path, body):
        with self.lock:
            self.calls.append((method, path, body))
            self.inflight += 1
            self.max_inflight = max(self.max_inflight, self.inflight)
        time.sleep(self.delay)
        with self.lock:
            self.inflight -= 1
        return b'[{"status":"Accepted"}]'

    def status(self):
        return {'state': self.state, 'active': len(self.leases)}


class ObservedHandler(module.Handler):
    """Record the accepted socket state, then execute the unmodified handler."""
    def setup(self):
        super().setup()
        with self.server.observation_lock:
            self.server.initial_socket_timeouts.append(self.connection.gettimeout())


def main():
    fake = FakeAgent()
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), ObservedHandler)
    server.daemon_threads, server.agent = True, fake
    server.initial_socket_timeouts, server.observation_lock = [], threading.Lock()
    threading.Thread(target=server.serve_forever, daemon=True).start()
    port = server.server_port
    findings = []

    def request(method, path, body=None, authorized=True, lease=LEASE, headers=None):
        conn = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
        h = {'Connection': 'close'}
        if authorized:
            h['Authorization'] = 'Bearer ' + TOKEN
        if lease:
            h['X-Judge-Lease'] = lease
        h.update(headers or {})
        conn.request(method, path, body, h)
        response = conn.getresponse()
        status, content = response.status, response.read().decode()
        conn.close()
        return status, content

    try:
        status, _ = request('GET', '/status', authorized=False)
        findings.append({'probe': 'unauthenticated_status', 'http_status': status,
                         'expected_security_control': status == 401})
        status, _ = request('POST', '/engine/run', b'{}', headers={'Content-Length': str(4 * 1024 * 1024 + 1)})
        findings.append({'probe': 'oversized_body_header', 'http_status': status,
                         'expected_security_control': status == 413})
        status, _ = request('POST', '/engine/run', b'{}', lease='does_not_exist')
        findings.append({'probe': 'invalid_lease', 'http_status': status,
                         'expected_security_control': status == 409})

        payload = json.dumps({'cmd': [{'args': ['/usr/bin/cat'],
                             'files': [{'src': '/tmp/audit-canary-only'},
                                       {'name': 'stdout', 'max': 4096}],
                             'clockLimit': 0, 'cpuLimit': 0, 'memoryLimit': 0,
                             'procLimit': 0}]}).encode()
        status, _ = request('POST', '/engine/run', payload)
        findings.append({'probe': 'arbitrary_engine_fields_forwarded', 'http_status': status,
                         'same_body_forwarded': fake.calls[-1][2] == payload,
                         'prerequisite': 'valid node token and lease; mock engine only'})

        fake.delay = .25
        with ThreadPoolExecutor(max_workers=6) as pool:
            outcomes = list(pool.map(lambda _: request('POST', '/engine/run', b'{"cmd":[{}]}')[0], range(6)))
        findings.append({'probe': 'same_lease_concurrent_requests', 'http_statuses': outcomes,
                         'max_forwarded_inflight': fake.max_inflight, 'lease_count': len(fake.leases),
                         'limitation': 'mock forwarding concurrency; real engine parallelism still caps actual sandbox concurrency'})
        fake.delay = 0

        fake.leases[LEASE]['expires'] = time.monotonic() - 30
        status, _ = request('POST', '/engine/run', b'{"cmd":[{}]}')
        findings.append({'probe': 'expired_lease_before_monitor_cleanup', 'http_status': status,
                         'limitation': 'real monitor calls status every five seconds; this is a bounded expiry race'})

        before = threading.active_count()
        with server.observation_lock:
            marker = len(server.initial_socket_timeouts)
        idle = []
        for _ in range(24):
            idle.append(socket.create_connection(('127.0.0.1', port), timeout=2))
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            with server.observation_lock:
                if len(server.initial_socket_timeouts) >= marker + 24:
                    break
            time.sleep(.01)
        time.sleep(.15)
        with server.observation_lock:
            observed = server.initial_socket_timeouts[marker:marker + 24]
        findings.append({'probe': 'unauthenticated_idle_connections', 'connections': len(idle),
                         'additional_threads': threading.active_count() - before,
                         'initial_socket_timeouts': observed,
                         'unbounded_pre_request_wait_confirmed': len(observed) == 24 and all(t is None for t in observed),
                         'safety': '24 localhost connections for less than three seconds; then all closed'})
        for conn in idle:
            conn.close()
        time.sleep(.1)

        # The deployed TLS setup wraps the listening socket. Its default TLS
        # handshake happens synchronously inside accept(), before a handler exists.
        with tempfile.TemporaryDirectory(prefix='sap-audit-tls-') as directory:
            cert, key = str(Path(directory) / 'cert.pem'), str(Path(directory) / 'key.pem')
            subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
                            '-keyout', key, '-out', cert, '-days', '1', '-subj', '/CN=localhost'],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            tls_server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), module.Handler)
            tls_server.daemon_threads, tls_server.agent = True, fake
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.load_cert_chain(cert, key)
            tls_server.socket = context.wrap_socket(tls_server.socket, server_side=True)
            threading.Thread(target=tls_server.serve_forever, daemon=True).start()
            pending = socket.create_connection(('127.0.0.1', tls_server.server_port), timeout=2)
            time.sleep(.15)
            client = http.client.HTTPSConnection('127.0.0.1', tls_server.server_port,
                        timeout=.6, context=ssl._create_unverified_context())
            blocked = False
            try:
                client.request('GET', '/status', headers={'Authorization': 'Bearer ' + TOKEN})
                client.getresponse()
            except TimeoutError:
                blocked = True
            finally:
                client.close()
                pending.close()
            recovery = http.client.HTTPSConnection('127.0.0.1', tls_server.server_port,
                        timeout=3, context=ssl._create_unverified_context())
            recovery.request('GET', '/status', headers={'Authorization': 'Bearer ' + TOKEN,
                                                       'Connection': 'close'})
            response = recovery.getresponse()
            recovery_status = response.status
            response.read()
            recovery.close()
            tls_server.shutdown()
            tls_server.server_close()
            findings.append({'probe': 'tls_pre_handshake_single_connection_blocks_accept',
                             'one_unauthenticated_raw_connection_blocks_valid_tls_client': blocked,
                             'http_status_after_closing_raw_connection': recovery_status,
                             'safety': 'local temp self-signed certificate, one raw connection for less than two seconds'})
    finally:
        server.shutdown()
        server.server_close()

    result = {'source': str(SOURCE), 'sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
              'scope': 'local unmodified Handler with mock Agent.engine; no sandbox command executed',
              'probes': findings}
    output = ROOT / 'ops/security-audit/agent-boundary-results.json'
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
