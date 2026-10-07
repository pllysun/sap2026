"""Disposable native Agent acceptance on cgroup v1/v2. No business mounts or credentials.
Run via SSH stdin: python3 - ROOT IMAGE [DOCKER_SOCKET]. Python 3.6 compatible.
"""
import hashlib, json, os, secrets, shutil, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path
ROOT=Path(sys.argv[1]);IMAGE=sys.argv[2];D=['docker']+(['-H',sys.argv[3]] if len(sys.argv)>3 else [])
NAME=PREFIX='sapjudge1525probe'
report={'passed':False,'kernel':os.uname().release,'cgroupVersion':2 if Path('/sys/fs/cgroup/cgroup.controllers').exists() else 1,'checks':[],'image':IMAGE,'agentSha':hashlib.sha256((ROOT/'agent.py').read_bytes()).hexdigest()}
groups=[];created=False
old_ns=Path('/proc/sys/user/max_user_namespaces').read_text()
def docker(*args):return subprocess.check_output(D+list(args),universal_newlines=True).strip()
def inside(source,timeout=65):
 p=subprocess.run(D+['exec','-i',NAME,'python3','-'],input=source,universal_newlines=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if p.returncode:raise RuntimeError(p.stderr[-1200:])
 return json.loads(p.stdout)
def api(path,body=None,method=None,lease=None,auth=True):
 return inside('''import json,urllib.request,urllib.error
headers={'Content-Type':'application/json'}
if %r:headers['Authorization']='Bearer '+open('/probe/token').read().strip()
if %r:headers['X-Judge-Lease']=%r
body=%r
request=urllib.request.Request('http://127.0.0.1:5051'+%r,data=None if body is None else json.dumps(body).encode(),headers=headers,method=%r)
try:
 with urllib.request.urlopen(request,timeout=55) as response:
  raw=response.read();print(json.dumps({'code':response.status,'data':json.loads(raw) if raw else None}))
except urllib.error.HTTPError as e:print(json.dumps({'code':e.code}))
'''%(auth,lease,lease,body,path,method))
def wait(state):
 for _ in range(100):
  try:
   s=api('/status')['data']
   if s['state']==state:return s
   if s['state']=='ERROR':raise RuntimeError(s['error'])
  except (subprocess.CalledProcessError,ConnectionError):pass
  time.sleep(.2)
 raise RuntimeError('State timeout '+state)
def command(args,inputs=None,stdin='',compile=False,cached=None,memory=128,cpu=1000,processes=32):
 return {'cmd':[{'args':args,'env':['PATH=/usr/local/bin:/usr/bin:/bin:/opt/rust/bin:/opt/java27/bin','HOME=/w','LANG=C.UTF-8','TMPDIR=/tmp'],
  'files':[{'content':stdin},{'name':'stdout','max':262144},{'name':'stderr','max':65536}],
  'cpuLimit':(15000 if compile else cpu)*1000000,'clockLimit':(45000 if compile else max(3000,cpu*3))*1000000,
  'memoryLimit':(512 if compile else memory)*1048576,'stackLimit':32*1048576,'procLimit':64 if compile else processes,
  'strictMemoryLimit':True,'copyIn':inputs or {},'copyOutCached':cached or [],'copyOutMax':33554432}]}
def run(payload,lease):
 r=api('/engine/run',payload,lease=lease);assert r['code']==200,r;return r['data'][0]
def python(code,lease,**limits):return run(command(['/usr/local/bin/python3','-c',code],**limits),lease)
def checked(name,ok,**details):
 report['checks'].append(dict(name=name,passed=bool(ok),**details));assert ok,name
try:
 assert NAME not in docker('ps','-a','--format','{{.Names}}').splitlines()
 ns={'__name__':'probe_prepare'};exec((ROOT/'prepare.py').read_text(),ns);groups=ns['prepare'](PREFIX,1,640,1)
 (ROOT/'token').write_text(secrets.token_hex(32));os.chmod(str(ROOT/'token'),0o600)
 args=['run','-d','--name',NAME,'--no-healthcheck','--memory=768m','--cpus=1','--pids-limit=256','--cap-add=SYS_ADMIN','--cap-add=SYS_PTRACE','--cap-add=SYS_RESOURCE',
  '--security-opt=systempaths=unconfined','--security-opt=seccomp='+str(ROOT/'seccomp.json'),'-v',str(ROOT)+':/probe:ro']
 if report['cgroupVersion']==2:args+=['--cgroupns=host','--security-opt=apparmor=unconfined']
 for group in groups:args+=['-v',group+':'+(group if report['cgroupVersion']==2 else os.path.dirname(group))+':rw']
 args+=['--entrypoint','/usr/bin/python3',IMAGE,'/probe/agent.py','--token-file','/probe/token','--state-dir','/run/release-probe','--cgroup-prefix',PREFIX,'--initial-stopped']
 docker(*args);created=True;wait('STOPPED');checked('unauthenticated control rejected',api('/status',auth=False)['code']==401)
 api('/start',{});status=wait('RUNNING');checked('isolation startup self-check succeeds',status['capacity']==1)
 runtime=status['runtimeId'];lease=api('/leases',{'runtimeId':runtime})['data']['lease']
 checked('capacity and lease required',api('/leases',{'runtimeId':runtime})['code']==409 and api('/engine/run',command(['/usr/local/bin/python3','-c','print(7)']))['code']==409)
 raw=python("import os,json; print(json.dumps({'uid':os.getuid(),'gid':os.getgid(),'appVisible':os.path.exists('/app'),'secretVisible':os.path.exists('/probe/token')}))",lease)
 r=json.loads(raw['files']['stdout']);checked('UID and filesystem isolation',raw['status']=='Accepted' and r=={'uid':1000,'gid':1000,'appVisible':False,'secretVisible':False})
 bad=command(['/usr/local/bin/python3','-c','print(7)']);bad['cmd'][0]['copyIn']={'main.py':{'src':'/probe/token'}};checked('outer file imports rejected',api('/engine/run',bad,lease=lease)['code']==400)
 for key,filename,stdio,function,compile_args,execute,artifacts in [
  ('c','main.c','#include <stdio.h>\nint main(){long long a,b;scanf("%lld%lld",&a,&b);printf("%lld\\n",a+b);}', '#include <stdio.h>\nlong long add(long long a,long long b){return a+b;}\nint main(){long long a,b;scanf("%lld%lld",&a,&b);printf("%lld\\n",add(a,b));}', ['/usr/local/bin/gcc','-std=c23','-O2','-pipe','main.c','-o','answer','-lm'],['/w/answer'],['answer']),
  ('cpp','main.cpp','#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}', '#include <iostream>\nclass Solution{public:long long add(long long a,long long b){return a+b;}};int main(){long long a,b;std::cin>>a>>b;std::cout<<Solution().add(a,b)<<"\\n";}', ['/usr/local/bin/g++','-std=c++23','-O2','-pipe','main.cpp','-o','answer'],['/w/answer'],['answer']),
  ('java','Main.java','import java.util.*;class Main{public static void main(String[]args){Scanner s=new Scanner(System.in);System.out.println(s.nextLong()+s.nextLong());}}', 'import java.util.*;class Solution{long add(long a,long b){return a+b;}}class Main{public static void main(String[]args){Scanner s=new Scanner(System.in);System.out.println(new Solution().add(s.nextLong(),s.nextLong()));}}', ['/bin/sh','-c','/opt/java27/bin/javac -J-Xmx96m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 -encoding UTF-8 Main.java && /opt/java27/bin/jar c *.class > answer.jar'], ['/opt/java27/bin/java','-Xmx96m','-Xss512k','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:MaxMetaspaceSize=64m','-XX:ReservedCodeCacheSize=24m','-XX:CompressedClassSpaceSize=32m','-cp','/w/answer.jar','Main'],['answer.jar']),
  ('python','main.py','a,b=map(int,input().split());print(a+b)', 'def add(a,b):\n    return a+b\na,b=map(int,input().split());print(add(a,b))', ['/usr/local/bin/python3','-c',"import ast; ast.parse(open('main.py',encoding='utf-8').read(),filename='main.py')"],['/usr/local/bin/python3','/w/main.py'],[]),
  ('rust','main.rs','use std::io::{self,Read};fn main(){let mut s=String::new();io::stdin().read_to_string(&mut s).unwrap();let v:Vec<i64>=s.split_whitespace().map(|x|x.parse().unwrap()).collect();println!("{}",v[0]+v[1]);}', 'use std::io::{self,Read};fn add(a:i64,b:i64)->i64{a+b}fn main(){let mut s=String::new();io::stdin().read_to_string(&mut s).unwrap();let v:Vec<i64>=s.split_whitespace().map(|x|x.parse().unwrap()).collect();println!("{}",add(v[0],v[1]));}', ['/opt/rust/bin/rustc','--edition=2024','-C','opt-level=2','-C','link-arg=-Wl,--threads=1','main.rs','-o','answer'],['/w/answer'],['answer'])]:
  for mode,source in [('STDIO',stdio),('FUNCTION',function)]:
   inputs={filename:{'content':source}};compiled=run(command(compile_args,inputs,compile=True,cached=artifacts),lease)
   checked(key+'/'+mode+' compilation',compiled['status']=='Accepted',status=compiled['status'],stderr=compiled.get('files',{}).get('stderr','')[:3000])
   if artifacts:inputs={name:{'fileId':compiled['fileIds'][name]} for name in artifacts}
   result=run(command(execute,inputs,stdin='3 4\n',memory=256 if key=='java' else 128),lease)
   checked(key+'/'+mode+' execution',result['status']=='Accepted' and result['files']['stdout'].strip()=='7')
   for item in inputs.values():
    if 'fileId' in item:checked(key+'/'+mode+' artifact cleanup',api('/engine/file/'+item['fileId'],method='DELETE',lease=lease)['code']==200)
 checked('TLE result preserved','Time Limit' in python('while True: pass',lease,cpu=100)['status'])
 memory_result=python("a=[]\nwhile True:a.append(bytearray(1048576))",lease,memory=32,cpu=3000)
 # A kernel may deny allocation before an OOM kill; CPython then reports
 # MemoryError (RE). Both must enforce the ceiling; do not relabel real results.
 allocation_denied=(memory_result['status']=='Nonzero Exit Status' and
                    'MemoryError' in memory_result.get('files',{}).get('stderr','') and
                    0<memory_result.get('memory',0)<=32*1048576)
 checked('memory ceiling enforced','Memory Limit' in memory_result['status'] or allocation_denied,
         status=memory_result['status'],memory=memory_result.get('memory'))
 checked('OLE result preserved','Output Limit' in python("print('x'*300000)",lease)['status'])
 checked('runtime error preserved',python('raise RuntimeError("test")',lease)['status']=='Nonzero Exit Status')
 checked('compiler error preserved',run(command(['/usr/local/bin/python3','-c',"import ast;ast.parse('x =')"],compile=True),lease)['status']=='Nonzero Exit Status')
 api('/leases/'+lease,method='DELETE');api('/stop',{});wait('STOPPED');checked('stop releases execution processes',inside("import glob,json;print(json.dumps({'running':any(open(p).read().strip()=='go-judge' for p in glob.glob('/proc/[0-9]*/comm'))}))")['running'] is False)
 api('/start',{});wait('RUNNING');lease=api('/leases',{'runtimeId':runtime})['data']['lease'];checked('restart and lease reuse',python('print(7)',lease)['files']['stdout'].strip()=='7');api('/leases/'+lease,method='DELETE')
 report['passed']=True
except Exception as e:
 report['error']=str(e)
 if created:
  report['diagnostic']=docker('logs','--tail','30',NAME)[-3000:]
  report['engineDiagnostic']=docker('exec',NAME,'sh','-c','tail -15 /run/release-probe/engine.log 2>/dev/null || true')[-2000:]
finally:
 if created:subprocess.run(D+['rm','-f',NAME],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 remaining=[]
 for group in groups:
  base=Path(group)
  if base.exists():
   for child in sorted([p for p in base.rglob('*') if p.is_dir()],key=lambda p:len(p.parts),reverse=True):
    try:child.rmdir()
    except OSError:pass
   try:base.rmdir()
   except OSError:remaining.append(str(base))
 if Path('/proc/sys/user/max_user_namespaces').read_text()!=old_ns:Path('/proc/sys/user/max_user_namespaces').write_text(old_ns)
 report['cleanup']={'containerRemoved':NAME not in docker('ps','-a','--format','{{.Names}}').splitlines(),'remainingCgroups':remaining}
 print(json.dumps(report,ensure_ascii=False))
 if not report['passed']:sys.exit(1)
