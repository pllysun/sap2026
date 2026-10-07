"""Start only the separate 1.5.11 review container on pllysun.top.

Files live outside the Docker build context; this uses dedicated Docker storage.
The real node provides unchanged runtimes, while business records use a fresh H2.
"""
import json,os,pathlib,subprocess
root=pathlib.Path('/opt/sap-judger/problemsets-1511')
docker=['docker','-H','unix:///run/sap-judger-docker.sock']
private=json.loads((root/'candidate-private.json').read_text())
names=subprocess.check_output(docker+['ps','-a','--format','{{.Names}}'],text=True).splitlines()
if 'sap-sets-candidate' in names:
    subprocess.run(docker+['rm','-f','sap-sets-candidate'],check=True,stdout=subprocess.DEVNULL)
for p in ['data','logs','uploads']:(root/p).mkdir(exist_ok=True)
for p in [root/'context/static',*(root/'context/static').rglob('*')]:p.chmod(0o755 if p.is_dir() else 0o644)
args=docker+['run','-d','--name','sap-sets-candidate','--network=host','--no-healthcheck',
    '--memory=1g','--cpus=2','--pids-limit=256','--entrypoint','/bin/sh',
    '-e','JUDGER_ENABLED=false','-e','JW_AES_KEY='+private['aes'],
    '-e','SAP_BOOTSTRAP_ADMIN_ACCOUNT='+private['studentId'],
    '-e','SAP_BOOTSTRAP_ADMIN_PASSWORD='+private['password']]
for source,target,mode in [(str(root/'data'),'/app/data','rw'),(str(root/'logs'),'/app/logs','rw'),
    (str(root/'uploads'),'/app/uploads','rw'),(str(root/'candidate-nginx.conf'),'/etc/nginx/nginx.conf','ro'),
    (str(root/'context/app.jar'),'/app/app.jar','ro'),(str(root/'context/static/user'),'/app/static/user','ro'),
    (str(root/'context/static/admin'),'/app/static/admin','ro'),
    (str(root/'candidate-sets-start.sh'),'/app/candidate-start.sh','ro'),('/opt/sap-judger/node.token','/run/judger/token','ro')]:
    args+=['-v',source+':'+target+':'+mode]
args+=['pllysun/sap:1.5.11','/app/candidate-start.sh']
subprocess.run(args,check=True,stdout=subprocess.DEVNULL)
print(json.dumps({'candidate':'started','separateData':True,'http':'127.0.0.1:18080','engine':'unchanged existing node'}))
