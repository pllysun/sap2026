"""Isolated review app on the existing dedicated judge host. No production DB access."""
import json,os,secrets,socket,subprocess,zipfile
from pathlib import Path
root=Path('/opt/sap-judger/fixes-1513')
docker=['docker','-H','unix:///run/sap-judger-docker.sock']
name='sap-fixes-candidate'
assert name not in subprocess.check_output(docker+['ps','-a','--format','{{.Names}}']).decode().splitlines()
for port in [19130,19131,19132]:
    with socket.socket() as sock:sock.bind(('127.0.0.1',port))
for name in ['data','logs','uploads']:(root/name).mkdir(exist_ok=True)
private={'studentId':'ojfixadmin','password':secrets.token_urlsafe(24),'aes':secrets.token_hex(16)}
(root/'candidate-private.json').write_text(json.dumps(private));(root/'candidate-private.json').chmod(0o600)
with zipfile.ZipFile(root/'context/app.jar') as jar:
    entry=next(n for n in jar.namelist() if n.startswith('BOOT-INF/lib/h2-') and n.endswith('.jar'))
    (root/'h2.jar').write_bytes(jar.read(entry))
# Only this disposable H2 has an 8-slot built-in descriptor. The production
# built-in node retains its resource-enforced one-slot configuration.
sql="CREATE TABLE IF NOT EXISTS oj_node(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(80) NOT NULL,endpoint VARCHAR(500) NOT NULL,token_cipher VARCHAR(1000),builtin BOOLEAN,enabled BOOLEAN,max_concurrency INT,created_at TIMESTAMP); INSERT INTO oj_node(name,endpoint,builtin,enabled,max_concurrency,created_at) VALUES('隔离验收节点','http://127.0.0.1:5051',TRUE,TRUE,8,CURRENT_TIMESTAMP);"
subprocess.run(docker+['run','--rm','--network=host','-v',str(root)+':/candidate','--entrypoint','java','pllysun/sap:1.5.12','-cp','/candidate/h2.jar','org.h2.tools.Shell','-url','jdbc:h2:file:/candidate/data/sap;MODE=MySQL;DATABASE_TO_LOWER=TRUE','-user','sa','-password','','-sql',sql],check=True,stdout=subprocess.DEVNULL)
(root/'candidate-nginx.conf').write_text('''worker_processes 1;
pid /run/nginx.pid;
events {worker_connections 1024;}
http {
 include /etc/nginx/mime.types;
 types {application/wasm wasm;}
 client_max_body_size 16m;
 access_log /app/logs/nginx-access.log;
 server {
  listen 127.0.0.1:19130;
  root /app/static/user;
  location /api/ {proxy_pass http://127.0.0.1:19131; proxy_read_timeout 120s;}
  location /admin/ {alias /app/static/admin/;try_files $uri $uri/ /admin/index.html;}
  location / {try_files $uri $uri/ /index.html;}
 }
}''')
startup=(root/'candidate-start.sh')
startup.write_text('''#!/bin/sh
set -eu
mkdir -p /app/data/redis /app/logs /app/uploads
cp /run/judger/token /app/data/candidate-agent.token
chmod 600 /app/data/candidate-agent.token
chown -R sapapp:sapapp /app/data /app/logs /app/uploads
setpriv --reuid=sapapp --regid=sapapp --init-groups --bounding-set=-all --no-new-privs redis-server --bind 127.0.0.1 --port 19132 --dir /app/data/redis --appendonly yes --save '' > /app/logs/redis.log 2>&1 &
nginx -t
nginx
exec setpriv --reuid=sapapp --regid=sapapp --init-groups --bounding-set=-all --no-new-privs java -Xms128m -Xmx512m -XX:ActiveProcessorCount=2 -jar /app/app.jar --spring.profiles.active=docker --server.address=127.0.0.1 --server.port=19131 --spring.data.redis.port=19132 --file.upload.path=/app/uploads --judger.enabled=true --judger.token-file=/app/data/candidate-agent.token --judger.agent-endpoint=http://127.0.0.1:5051
''');startup.chmod(0o755)
args=docker+['run','-d','--name','sap-fixes-candidate','--network=host','--no-healthcheck','--memory=1g','--cpus=2','--pids-limit=256','--entrypoint','/bin/sh',
 '-e','JUDGER_ENABLED=false','-e','JW_AES_KEY='+private['aes'],'-e','SAP_BOOTSTRAP_ADMIN_ACCOUNT='+private['studentId'],'-e','SAP_BOOTSTRAP_ADMIN_PASSWORD='+private['password']]
for source,target,mode in [(root/'data','/app/data','rw'),(root/'logs','/app/logs','rw'),(root/'uploads','/app/uploads','rw'),(root/'candidate-nginx.conf','/etc/nginx/nginx.conf','ro'),(root/'context/app.jar','/app/app.jar','ro'),(root/'context/static/user','/app/static/user','ro'),(root/'context/static/admin','/app/static/admin','ro'),(startup,'/app/candidate-start.sh','ro'),(Path('/opt/sap-judger/node.token'),'/run/judger/token','ro')]:args+=['-v',str(source)+':'+target+':'+mode]
args+=['pllysun/sap:1.5.12','/app/candidate-start.sh']
subprocess.run(args,check=True,stdout=subprocess.DEVNULL)
print(json.dumps({'candidate':'sap-fixes-candidate','isolatedData':True,'httpPort':19130}))
