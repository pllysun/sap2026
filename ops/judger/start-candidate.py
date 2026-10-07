import json,os,subprocess
exec(open('/tmp/sap-oj-build/host-runtime.py').read())
def docker(*args): return subprocess.check_output(['docker',*args],encoding='utf-8').strip()
old=json.loads(docker('inspect','sap-oj-candidate'))[0]
paths=prepare()
docker('rm','-f','sap-oj-candidate')
args=['run','-d','--name','sap-oj-candidate','--memory=512m','--cpus=1','--pids-limit=256',
      '--cap-add=SYS_ADMIN','--cap-add=SYS_PTRACE','--cap-add=SYS_RESOURCE',
      '--security-opt=systempaths=unconfined','--security-opt=seccomp=/tmp/sap-oj-build/seccomp.json',
      '-p','127.0.0.1:18080:80']
for value in old['Config']['Env']:
    if not value.startswith(('JUDGER_ENABLED=','JAVA_OPTS=')): args+=['-e',value]
args+=['-e','JUDGER_ENABLED=true','-e','JAVA_OPTS=-Xms48m -Xmx128m -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -XX:CompressedClassSpaceSize=16m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC']
os.makedirs('/tmp/sap-oj-candidate/empty-ocr',exist_ok=True)
args+=['-v','/tmp/sap-oj-candidate/empty-ocr:/app/ocr:ro']
for binding in old['HostConfig']['Binds'] or []:
    if binding.startswith('/tmp/sap-oj-candidate/empty-ocr:') or binding.startswith('/sys/fs/cgroup/') or binding.startswith('/tmp/sap-oj-build/context/'): continue
    args+=['-v',binding]
for path in paths: args+=['-v',path+':'+os.path.dirname(path)+':rw']
# Review candidate uses the latest tested jar and assets without rebuilding its large immutable base.
if not os.environ.get('SAP_CANDIDATE_IMAGE'):
    args+=['-v','/tmp/sap-oj-build/context/entrypoint.sh:/app/entrypoint.sh:ro',
           '-v','/tmp/sap-oj-build/context/app.jar:/app/app.jar:ro',
           '-v','/tmp/sap-oj-build/context/static/user:/app/static/user:ro',
           '-v','/tmp/sap-oj-build/context/static/admin:/app/static/admin:ro']
args.append(os.environ.get('SAP_CANDIDATE_IMAGE',old['Config']['Image']))
docker(*args)
print(json.dumps({'candidate':'started','judgerEnabled':True,'separateData':True}))
