"""Python 3.6-compatible isolated go-judge corpus validator, supplied over SSH stdin.
PACKS_PAYLOAD is replaced by local runner; never reads application credentials/data.
"""
import base64,json,os,subprocess,time,urllib.request,zlib,traceback
PACKS_PAYLOAD = '__PACKS_PAYLOAD__'
PACKS=json.loads(zlib.decompress(base64.b64decode(PACKS_PAYLOAD)).decode())
NAME=PREFIX='sap-judger-corpus-probe'
IMAGE='pllysun/sap-judger-sdk:20261001-1'
namespace_path='/proc/sys/user/max_user_namespaces'
old_namespace_limit=open(namespace_path).read().strip()
cgroup_paths=[]
container_owned=False
namespace_changed=False
MOUNT='/tmp/sap-oj-probe/corpus-mount.json'
report={'environment':'SERVER_GO_JUDGE','image':IMAGE,'rounds':3,'packCount':len(PACKS),'results':[],'startedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
def run(*a):return subprocess.check_output(a,encoding='utf-8').strip()
def request(path,payload=None,method=None):
    req=urllib.request.Request('http://'+address+':5050'+path,data=json.dumps(payload).encode() if payload is not None else None,headers={'Authorization':'Bearer probe-only','Content-Type':'application/json'},method=method)
    return json.loads(urllib.request.urlopen(req,timeout=90).read())
def execute(args,inputs,stdin='',compile=False,cached=None):
    cmd={'args':args,'env':['PATH=/usr/local/bin:/usr/bin:/bin:/opt/java27/bin:/opt/rust/bin','HOME=/w','LANG=C.UTF-8'],
      'files':[{'content':stdin},{'name':'stdout','max':32*1024*1024},{'name':'stderr','max':32*1024*1024}],
      'cpuLimit':30000000000 if compile else 4000000000,'clockLimit':80000000000 if compile else 12000000000,
      'memoryLimit':536870912 if compile else 268435456,'stackLimit':16777216,'procLimit':64,'strictMemoryLimit':True,'copyIn':inputs}
    if cached:cmd['copyOutCached']=cached
    return request('/run',{'cmd':[cmd]})[0]
def summary_error(res):
    # Compiler diagnostics contain source snippets, but never hidden testcase stdin/output.
    return {'judgeStatus':res.get('status'),'exitStatus':res.get('exitStatus'),'error':res.get('error'),'stderr':res.get('files',{}).get('stderr','')[:1500]}
def compile_program(lang,source):
    filename={'c':'main.c','cpp':'main.cpp','java':'Main.java','python':'main.py','rust':'main.rs'}[lang]
    inputs={filename:{'content':source}}
    flags=['-Xmx96m','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:MaxMetaspaceSize=64m','-XX:ReservedCodeCacheSize=24m','-XX:CompressedClassSpaceSize=32m']
    if lang=='python':return inputs,['/usr/local/bin/python3','/w/main.py'],None
    artifact='answer.jar' if lang=='java' else 'answer'
    commands={
     'c':['/usr/local/bin/gcc','-std=c23','-O2','main.c','-o','answer'],
     'cpp':['/usr/local/bin/g++','-std=c++23','-O2','main.cpp','-o','answer'],
     'rust':['/opt/rust/bin/rustc','--edition=2024','-C','opt-level=2','main.rs','-o','answer'],
     'java':['/bin/sh','-c','/opt/java27/bin/javac '+' '.join('-J'+f for f in flags)+' Main.java && /opt/java27/bin/jar '+ ' '.join('-J'+f for f in flags)+' c *.class > answer.jar']}
    res=execute(commands[lang],inputs,compile=True,cached=[artifact])
    if res.get('status')!='Accepted' or res.get('exitStatus',0)!=0:return None,None,summary_error(res)
    cacheid=res.get('fileIds',{}).get(artifact)
    if not cacheid:return None,None,{'error':'missing cached artifact'}
    args=['/opt/java27/bin/java']+flags+['-cp','answer.jar','Main'] if lang=='java' else ['/w/answer']
    return {artifact:{'fileId':cacheid}},args,None
start=time.monotonic()
try:
    if run('docker','ps','-aq','--filter','name=^/'+NAME+'$'):raise RuntimeError('Existing corpus probe container; refusing to replace')
    for controller in ['memory','pids','cpu','cpuacct','cpuset']:
        parent=os.path.realpath('/sys/fs/cgroup/'+controller);path=parent+'/'+PREFIX
        if path not in cgroup_paths and os.path.isdir(parent):
            os.makedirs(path,exist_ok=True);cgroup_paths.append(path)
            limits={'memory.limit_in_bytes':str(640*1024*1024),'pids.max':'256','cpu.cfs_period_us':'100000','cpu.cfs_quota_us':'100000'}
            for f,value in limits.items():
                if os.path.isfile(path+'/'+f):
                    with open(path+'/'+f,'w') as target:target.write(value)
            if controller=='cpuset':
                for f in ['cpuset.cpus','cpuset.mems']:
                    with open(path+'/'+f,'w') as target:target.write(open(parent+'/'+f).read())
    mounts=[{'type':'bind','source':p,'target':p,'readonly':True} for p in ['/bin','/lib','/lib64','/usr','/etc/ld.so.cache','/etc/alternatives','/opt/rust','/opt/java27']]
    mounts += [{'type':'bind','source':p,'target':p,'readonly':False} for p in ['/dev/null','/dev/zero','/dev/random','/dev/urandom','/dev/full']]
    mounts += [{'type':'tmpfs','target':p,'data':'size=64m,nr_inodes=8k'} for p in ['/w','/tmp']]
    with open(MOUNT,'w') as target:json.dump({'mount':mounts,'proc':True,'workDir':'/w','uid':1000,'gid':1000,'maskPath':['/proc/kcore','/proc/keys','/proc/timer_list','/proc/sched_debug']},target)
    with open(namespace_path,'w') as target:target.write('1024')
    namespace_changed=True
    cmd=['docker','run','-d','--name',NAME,'--cap-add=SYS_ADMIN','--cap-add=SYS_PTRACE','--cap-add=SYS_RESOURCE','--security-opt=systempaths=unconfined','--security-opt=seccomp=/tmp/sap-oj-probe/seccomp.json','--memory=640m','--cpus=1','--pids-limit=256','--shm-size=64m','-v','/tmp/sap-oj-probe/go-judge:/opt/judger/go-judge:ro','-v',MOUNT+':/opt/judger/mount.yaml:ro']
    for path in cgroup_paths:cmd += ['-v',path+':'+os.path.dirname(path)+':rw']
    cmd += ['--entrypoint','/opt/judger/go-judge',IMAGE,'-http-addr','0.0.0.0:5050','-auth-token','probe-only','-parallelism','1','-pre-fork','1','-cgroup-prefix',PREFIX,'-container-cred-start','10000','-no-fallback','-tmp-fs-param','size=64m,nr_inodes=8k','-output-limit','32m','-copy-out-limit','32m','-mount-conf','/opt/judger/mount.yaml']
    run(*cmd);container_owned=True;address=run('docker','inspect','--format','{{.NetworkSettings.IPAddress}}',NAME)
    for attempt in range(30):
        try:report['judgeConfig']=request('/config');break
        except Exception:time.sleep(1)
    else:raise RuntimeError('go-judge not ready')
    for pack in PACKS:
        for lang in ['c','cpp','java','python','rust']:
            for mode in pack['modes']:
                source=pack['references'][lang][mode]
                if mode=='FUNCTION':
                    driver=pack['profiles'][lang]['functionDriver']
                    if driver.count('__USER_CODE__')!=1:raise RuntimeError('Invalid driver insertion count '+pack['slug']+'/'+lang)
                    source=driver.replace('__USER_CODE__',source)
                record={'slug':pack['slug'],'language':lang,'mode':mode,'caseCount':len(pack['cases']),'rounds':3,'status':'PASSED','executions':0,'maxCpuNs':0,'maxMemoryBytes':0}
                inputs,args,error=compile_program(lang,source)
                if error:record.update(status='COMPILE_FAILED',details=error)
                else:
                    for repeat in range(3):
                        for case in pack['cases']:
                            result=execute(args,inputs,case['input']);record['executions']+=1
                            actual=result.get('files',{}).get('stdout','').split();expected=case['expectedOutput'].split()
                            if pack.get('checker')=='UNORDERED_TOKENS':actual=sorted(actual);expected=sorted(expected)
                            record['maxCpuNs']=max(record['maxCpuNs'],result.get('time',0));record['maxMemoryBytes']=max(record['maxMemoryBytes'],result.get('memory',0))
                            if result.get('status')!='Accepted' or result.get('exitStatus',0)!=0 or actual!=expected:
                                record.update(status='FAILED',caseName=case['name'],round=repeat+1,details=summary_error(result));break
                        if record['status']!='PASSED':break
                    for file in inputs.values():
                        if 'fileId' in file:
                            try:request('/file/'+file['fileId'],method='DELETE')
                            except Exception:pass
                report['results'].append(record);print(json.dumps({'event':'validation','result':record}),flush=True)
    report['passed']=sum(r['status']=='PASSED' for r in report['results']);report['failed']=len(report['results'])-report['passed']
except Exception as error:
    report['harnessError']=str(error);print(json.dumps({'event':'harness-error','message':str(error)}),flush=True)
finally:
    if container_owned:subprocess.call(['docker','rm','-f',NAME],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if namespace_changed:
        with open(namespace_path,'w') as target:target.write(old_namespace_limit)
    for path in cgroup_paths:
        for root,dirs,files in os.walk(path,topdown=False):
            try:os.rmdir(root)
            except OSError:pass
    try:os.unlink(MOUNT)
    except OSError:pass
    report['cleanup']={'namespaceRestored':open(namespace_path).read().strip()==old_namespace_limit,'containerRemoved':not run('docker','ps','-aq','--filter','name=^/'+NAME+'$'),'remainingCgroups':[p for p in cgroup_paths if os.path.isdir(p)]}
    sap=json.loads(run('docker','inspect','--format','{{json .State}}','sap'))
    report['liveSap']={'running':sap['Running'],'health':sap.get('Health',{}).get('Status')}
    report['elapsedSeconds']=round(time.monotonic()-start,2)
    print(json.dumps({'event':'final-report','report':report}),flush=True)
