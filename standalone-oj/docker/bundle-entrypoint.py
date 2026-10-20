#!/usr/bin/env python3
"""Supervise the website and a private local judge; no host cgroup bind or Redis."""
import ctypes,errno,json,os,platform,secrets,signal,subprocess,sys,time,urllib.request
from pathlib import Path
LIB=ctypes.CDLL(None,use_errno=True)
BASE=Path('/sys/fs/cgroup')
DATA=Path('/app/data')

def number(name,default,minimum,maximum):
    value=int(os.environ.get(name,str(default)))
    if not minimum<=value<=maximum:raise ValueError('Invalid resource setting: '+name)
    return value

def prepare(cpu,memory,heap):
    if platform.machine()!='x86_64' or not (BASE/'cgroup.controllers').is_file():raise RuntimeError('Native Linux amd64 and cgroup v2 are required')
    if Path('/proc/self/cgroup').read_text().strip()!='0::/':raise RuntimeError('Use --cgroupns=private; host cgroup namespaces are refused')
    if not {'cpu','memory','pids'}<=set((BASE/'cgroup.controllers').read_text().split()):raise RuntimeError('Missing CPU, memory or pids controller')
    # Remount only this container's private cgroup namespace. No host subtree is exposed.
    if LIB.mount(None,b'/sys/fs/cgroup',None,32|2|4|8,None)!=0:raise RuntimeError('Cannot delegate cgroup; use the documented capabilities and seccomp profile')
    total=(BASE/'memory.max').read_text().strip()
    worker=(memory+256)*1048576;service=(heap+512)*1048576
    if total!='max' and int(total)<worker+service:raise RuntimeError('Container memory budget is below website + judge reservations')
    (BASE/'service').mkdir(exist_ok=True)
    for pid in (BASE/'cgroup.procs').read_text().split():(BASE/'service/cgroup.procs').write_text(pid+'\n')
    (BASE/'cgroup.subtree_control').write_text('+cpu +memory +pids\n')
    (BASE/'service/memory.max').write_text(str(service))
    (BASE/'sandbox').mkdir(exist_ok=True)
    (BASE/'sandbox/cpu.max').write_text(str(cpu*100000)+' 100000\n')
    (BASE/'sandbox/memory.max').write_text(str(worker))
    (BASE/'sandbox/memory.swap.max').write_text('0\n')
    (BASE/'sandbox/pids.max').write_text('512\n')
    (BASE/'sandbox/cgroup.subtree_control').write_text('+cpu +memory +pids\n')

def credentials():
    DATA.mkdir(parents=True,exist_ok=True);os.chown(DATA,2000,2000)
    folder=DATA/'.system';folder.mkdir(mode=0o750,exist_ok=True);os.chown(folder,0,2000);folder.chmod(0o750)
    path=folder/'judge-token'
    if not path.exists():
        fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o440)
        with os.fdopen(fd,'w') as out:out.write(secrets.token_hex(32)+'\n')
    if len(path.read_text().strip())<32:raise RuntimeError('Local node token is invalid')
    os.chown(path,0,2000);path.chmod(0o440)
    judge=DATA/'judge';judge.mkdir(mode=0o700,exist_ok=True);os.chown(judge,0,0);judge.chmod(0o700)
    return path,judge

def drop_application_privileges():
    # The privileged supervisor is needed for sandbox setup; Java gets no capabilities.
    for cap in range(64):
        if LIB.prctl(24,cap,0,0,0)!=0 and ctypes.get_errno()!=errno.EINVAL:raise RuntimeError('Cannot drop Java capability bounding set')
    os.setgroups([]);os.setgid(2000);os.setuid(2000)
    if LIB.prctl(38,1,0,0,0)!=0:raise RuntimeError('Cannot set Java no-new-privileges')

def stop(process):
    if process is None or process.poll() is not None:return
    try:os.killpg(process.pid,signal.SIGTERM)
    except ProcessLookupError:return
    try:process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        try:os.killpg(process.pid,signal.SIGKILL)
        except ProcessLookupError:pass
        process.wait(timeout=5)

def main():
    os.umask(0o077)
    cpu=number('NODE_CPU',2,1,32);memory=number('NODE_MEMORY_MB',1280,512,32768);heap=number('APP_HEAP_MB',1024,256,16384);slots=number('NODE_CONCURRENCY',2,1,32)
    prepare(cpu,memory,heap);token,state=credentials()
    agent=subprocess.Popen(['/usr/local/bin/python3','/opt/judger/agent.py','--bind','127.0.0.1','--token-file',str(token),'--state-dir',str(state),'--cgroup-prefix','sandbox','--cpu',str(cpu),'--memory-mb',str(memory),'--max-concurrency',str(slots)],
        env={'PATH':'/usr/local/bin:/usr/bin:/bin','HOME':'/run','PYTHONUNBUFFERED':'1'},start_new_session=True)
    java=None;stopping=False
    def terminate(signum,frame):
        nonlocal stopping
        stopping=True
    signal.signal(signal.SIGTERM,terminate);signal.signal(signal.SIGINT,terminate)
    try:
        opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
        ready=False
        for _ in range(120):
            if stopping:return 0
            if agent.poll() is not None:raise RuntimeError('Local judge supervisor exited')
            try:
                req=urllib.request.Request('http://127.0.0.1:5051/status',headers={'Authorization':'Bearer '+token.read_text().strip()})
                with opener.open(req,timeout=2) as response:status=json.load(response)
                if status.get('state')=='RUNNING' and int(status.get('capacity',0))>0:ready=True;break
                if status.get('state')=='STOPPED':ready=True;break # an intentional admin stop survives restart
                if status.get('state')=='ERROR':raise RuntimeError('Sandbox startup failed; check documented resource and kernel requirements')
            except (OSError,urllib.error.URLError):pass
            time.sleep(.5)
        if not ready:raise RuntimeError('Local judge startup timed out')
        allowed=('DB_URL','DB_USER','DB_PASSWORD','OJ_ADMIN_ACCOUNT','OJ_ADMIN_PASSWORD','S3_ENABLED','S3_ENDPOINT','S3_REGION','S3_BUCKET','S3_ACCESS_KEY','S3_SECRET_KEY','S3_PATH_STYLE','S3_CREATE_BUCKET')
        env={k:os.environ[k] for k in allowed if k in os.environ}
        env.update(PATH='/opt/application-java/bin:/usr/bin:/bin',HOME=str(DATA),TZ='Asia/Shanghai',LD_LIBRARY_PATH='/opt/application-libs',SPRING_PROFILES_ACTIVE='docker',JUDGER_ENABLED='true',JUDGER_BUILTIN_ENABLED='true',JUDGER_AGENT_ENDPOINT='http://127.0.0.1:5051',JUDGER_TOKEN_FILE=str(token))
        java=subprocess.Popen(['/opt/application-java/bin/java','-Xms128m','-Xmx'+str(heap)+'m','-XX:+ExitOnOutOfMemoryError','-Duser.timezone=Asia/Shanghai','-jar','/app/app.jar'],env=env,preexec_fn=drop_application_privileges,start_new_session=True,cwd='/app')
        print('Lume website and private local judge started',flush=True)
        while not stopping:
            if java.poll() is not None or agent.poll() is not None:raise RuntimeError('A required service exited; restarting the container is required')
            time.sleep(.5)
        return 0
    finally:stop(java);stop(agent)

if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:
        # Do not print argv, environment, database errors or node credentials.
        print('Lume startup failed: '+str(error) if isinstance(error,(RuntimeError,ValueError)) else 'Lume startup failed; check deployment permissions and resources',file=sys.stderr)
        sys.exit(1)
