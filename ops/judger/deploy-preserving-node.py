"""Upgrade the independent node via SSH stdin, preserving its Docker configuration.

Usage: python3 - IMAGE EXPECTED_CURRENT CONFIG_ID EXPECTED_AGENT_SHA RELEASE_ROOT
Credentials and the previous container specification remain only on the server.
"""
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.request

IMAGE, PREVIOUS, CONFIG_ID, AGENT_SHA, ROOT = sys.argv[1:]
ROOT = Path(ROOT)
SOCKET = '/run/sap-judger-docker.sock'
NAME = 'sap-judger-node'
D = ['docker', '-H', 'unix://' + SOCKET]


def docker(*args):
    return subprocess.check_output(D + list(args), encoding='utf-8').strip()


class DockerConnection(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(SOCKET)


def create_container(body):
    conn = DockerConnection('localhost', timeout=30)
    try:
        conn.request('POST', '/containers/create?name=' + NAME,
                     json.dumps(body).encode(), {'Content-Type': 'application/json'})
        response = conn.getresponse()
        data = response.read()
        if response.status != 201:
            raise RuntimeError('Docker container creation failed: HTTP %s' % response.status)
        return json.loads(data)['Id']
    finally:
        conn.close()


token = Path('/opt/sap-judger/node.token').read_text().strip()


def status():
    request = urllib.request.Request('http://127.0.0.1:5051/status',
                                    headers={'Authorization': 'Bearer ' + token})
    with urllib.request.urlopen(request, timeout=4) as response:
        return json.loads(response.read(65536))


old = json.loads(docker('inspect', NAME))[0]
assert old['Config']['Image'] == PREVIOUS, 'Live node version changed'
image = json.loads(docker('image', 'inspect', IMAGE))[0]
assert image['Id'] == CONFIG_ID and image['Architecture'] == 'amd64'
source = ROOT / 'context/judger-agent.py'
assert hashlib.sha256(source.read_bytes()).hexdigest() == AGENT_SHA
agent_mount = next(m for m in old['Mounts'] if m['Destination'] == '/opt/judger/agent.py')
assert agent_mount['Source'] == '/opt/sap-judger/agent.py' and not agent_mount['RW']
for _ in range(60):
    prior_status = status()
    if prior_status['active'] == 0:
        break
    time.sleep(1)
else:
    raise SystemExit('Node remains busy; deployment aborted')
assert prior_status['state'] in ('RUNNING', 'STOPPED'), 'Node is not stable'

backup = NAME + '-rollback-' + time.strftime('%Y%m%d-%H%M%S')
backup_dir = ROOT / backup
backup_dir.mkdir(mode=0o700)
(backup_dir / 'container.json').write_text(json.dumps(old))
(backup_dir / 'container.json').chmod(0o600)
agent = Path(agent_mount['Source'])
shutil.copy2(agent, backup_dir / 'agent.py')
body = dict(old['Config'])
body['Image'] = IMAGE
host = dict(old['HostConfig'])
mounts = list(host.get('Mounts') or [])
targets = {m['Target'] for m in mounts}
for mount in old['Mounts']:
    if mount['Type'] == 'volume' and mount['Destination'] not in targets:
        mounts.append({'Type': 'volume', 'Source': mount['Name'], 'Target': mount['Destination'],
                       'ReadOnly': not mount['RW'], 'VolumeOptions': {'NoCopy': True}})
host['Mounts'] = mounts
body['HostConfig'] = host

docker('stop', '--time', '30', NAME)
docker('rename', NAME, backup)
try:
    pending = agent.with_name('agent.py.release-pending')
    shutil.copy2(source, pending)
    os.replace(str(pending), str(agent))
    create_container(body)
    docker('start', NAME)
    for _ in range(90):
        try:
            new_status = status()
            if new_status['state'] == 'STOPPED' and prior_status['state'] == 'RUNNING':
                request = urllib.request.Request('http://127.0.0.1:5051/start', data=b'{}',
                    headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
                with urllib.request.urlopen(request, timeout=4) as response:
                    response.read(65536)
            if new_status['state'] == 'ERROR':
                raise RuntimeError('New node isolation self-check failed')
            if new_status['state'] == prior_status['state']:
                current = json.loads(docker('inspect', NAME))[0]
                if current['State'].get('Health', {}).get('Status') == 'healthy':
                    break
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    else:
        raise RuntimeError('Node startup timeout')
    assert current['Image'] == CONFIG_ID
    assert current['Config']['Env'] == old['Config']['Env']
    assert current['Config']['Cmd'] == old['Config']['Cmd']
    for key in ['NetworkMode', 'PortBindings', 'RestartPolicy', 'CapAdd', 'SecurityOpt',
                'Memory', 'MemorySwap', 'NanoCpus', 'PidsLimit', 'CgroupnsMode']:
        assert current['HostConfig'].get(key) == old['HostConfig'].get(key), key
    project_mounts = lambda c: sorted((m['Type'], m.get('Name', m['Source']), m['Destination'], m['RW']) for m in c['Mounts'])
    assert project_mounts(current) == project_mounts(old), 'Node mounts changed'
    assert new_status['capacity'] == prior_status['capacity']
    assert docker('exec', NAME, 'sha256sum', '/opt/judger/agent.py').split()[0] == AGENT_SHA
    print(json.dumps({'passed': True, 'image': IMAGE, 'configId': CONFIG_ID,
        'agentSha': AGENT_SHA, 'state': new_status['state'], 'capacity': new_status['capacity'],
        'configurationPreserved': True, 'isolationSelfCheck': True,
        'rollbackContainer': backup, 'rollbackAgent': str(backup_dir / 'agent.py')}))
except Exception:
    subprocess.run(D + ['rm', '-f', NAME], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    pending = agent.with_name('agent.py.rollback-pending')
    shutil.copy2(backup_dir / 'agent.py', pending)
    os.replace(str(pending), str(agent))
    docker('rename', backup, NAME)
    docker('start', NAME)
    raise SystemExit('Node deployment failed; previous container and Agent restored')
