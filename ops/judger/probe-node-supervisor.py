"""Run via SSH stdin. An isolated temporary node uses existing verified runtime layers."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import threading
import time
import urllib.request
import urllib.error

NAME='sap-oj-158-node-probe'
ROOT=Path('/tmp/sap-oj-158-node-probe')
PREFIX='sapjudgerprobe'
def docker(*args):
    return subprocess.check_output(['docker']+list(args),universal_newlines=True).strip()

if subprocess.call(['docker','inspect',NAME],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)!=0:
    ns={'__name__':'node_probe'}
    exec(Path('/usr/local/lib/sap/judger-host.py').read_text(),ns)
    groups=ns['prepare'](PREFIX)
    (ROOT/'token').write_text(secrets.token_hex(32))
    os.chmod(str(ROOT/'token'),0o600)
    args=['run','-d','--name',NAME,'--label','com.sap.oj.probe=1.5.8','--no-healthcheck',
          '--cap-add=SYS_ADMIN','--cap-add=SYS_PTRACE','--cap-add=SYS_RESOURCE',
          '--security-opt=systempaths=unconfined','--security-opt=seccomp=/etc/sap/judger-seccomp.json',
          '-v',str(ROOT)+':/probe:ro','-p','127.0.0.1:15051:5051']
    for group in groups:
        args+=['-v',group+':'+os.path.dirname(group)+':rw']
    args+=['--entrypoint','/usr/bin/python3','pllysun/sap:1.5.7','/probe/agent.py','--bind','0.0.0.0',
           '--token-file','/probe/token','--state-dir','/run/node-probe','--cgroup-prefix',PREFIX,'--initial-stopped']
    docker(*args)
token=(ROOT/'token').read_text().strip()
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def api(path,body=None,method=None,auth=True,lease=None):
    headers={'Content-Type':'application/json'}
    if auth:headers['Authorization']='Bearer '+token
    if lease:headers['X-Judge-Lease']=lease
    request=urllib.request.Request('http://127.0.0.1:15051'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method or ('POST' if body is not None else 'GET'))
    try:
        with opener.open(request,timeout=60) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
def wait(state):
    for _ in range(100):
        try:
            code,status=api('/status')
            if code==200 and status['state']==state:return status
            if status.get('state')=='ERROR':raise RuntimeError(status.get('error'))
        except (urllib.error.URLError, OSError):pass
        time.sleep(.2)
    raise RuntimeError('State timeout: '+state)
def rss():
    lines=docker('top',NAME,'-eo','pid,rss,comm').splitlines()[1:]
    return sum(int(line.split()[1]) for line in lines if len(line.split())>=3)

for _ in range(100):
    try:
        api('/stop',{})
        break
    except (urllib.error.URLError,OSError):time.sleep(.2)
wait('STOPPED')
assert api('/status',auth=False)[0]==401
api('/start',{})
status=wait('RUNNING')
assert status['capacity']==1
runtime=status['runtimeId']
code,res=api('/leases',{'runtimeId':runtime});assert code==200
lease=res['lease']
assert api('/leases',{'runtimeId':runtime})[0]==409
def command(source):
    return {'cmd':[{'args':['/usr/local/bin/python3','-c',source],'env':['PATH=/usr/local/bin:/usr/bin:/bin','HOME=/w'],
       'files':[{'content':''},{'name':'stdout','max':262144},{'name':'stderr','max':65536}],
       'cpuLimit':2000000000,'clockLimit':30000000000,'memoryLimit':128*1048576,'stackLimit':32*1048576,
       'procLimit':32,'strictMemoryLimit':True,'copyIn':{},'copyOutCached':[],'copyOutMax':33554432}]}
assert api('/engine/run',command('print(7)'))[0]==409
code,result=api('/engine/run',command('print(7)'),lease=lease)
assert code==200 and result[0]['status']=='Accepted' and result[0]['files']['stdout'].strip()=='7',result
before=rss()
out=[]
thread=threading.Thread(target=lambda:out.append(api('/engine/run',command('import time; time.sleep(20)'),lease=lease)))
thread.start();time.sleep(.5)
api('/stop',{})
wait('STOPPED');thread.join(65)
assert out and out[0][0]==503,out
after=rss()
assert after<before,(before,after)
assert api('/leases',{'runtimeId':runtime})[0]==409
api('/start',{});wait('RUNNING')
code,res=api('/leases',{'runtimeId':runtime});assert code==200
assert api('/engine/run',command('print(9)'),lease=res['lease'])[1][0]['status']=='Accepted'
api('/leases/'+res['lease'],method='DELETE')
api('/stop',{});wait('STOPPED')
report={'passed':True,'checks':['authenticated node control','real sandbox execution','atomic capacity reservation','reject unleased execution','stop interrupts execution','restart execution'],
        'rssBeforeStopKiB':before,'rssAfterStopKiB':after,'releasedKiB':before-after,'mainApplicationUntouched':True}
(ROOT/'result.json').write_text(json.dumps(report))
print(json.dumps(report))
