#!/usr/bin/env python3
"""Generate private deployment credentials; never prints their values."""
import argparse,re,secrets,os
from pathlib import Path

def initialize(root: Path,account: str):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{2,31}',account):raise ValueError('账号须为 3–32 位安全字符')
    env=root/'.env';folder=root/'secrets';token=folder/'judger-token'
    if env.exists() or token.exists():raise ValueError('配置已经存在，拒绝覆盖；需要重装时使用新的目录')
    root.mkdir(parents=True,exist_ok=True);folder.mkdir(mode=0o700,exist_ok=True);folder.chmod(0o700)
    values={
      'OJ_ADMIN_ACCOUNT':account.lower(),'OJ_ADMIN_PASSWORD':secrets.token_urlsafe(24),
      'DB_PASSWORD':secrets.token_hex(24),'MYSQL_ROOT_PASSWORD':secrets.token_hex(24),
      'HTTP_PORT':'8080','BIND_ADDRESS':'127.0.0.1',
      'DB_URL':'jdbc:mysql://mysql:3306/oj?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false','DB_USER':'oj',
      'S3_ENABLED':'false','S3_ENDPOINT':'http://rustfs:9000','S3_REGION':'us-east-1','S3_BUCKET':'sap-oj-private','S3_PATH_STYLE':'true','S3_CREATE_BUCKET':'false',
      'S3_ACCESS_KEY':secrets.token_hex(10).upper(),'S3_SECRET_KEY':secrets.token_hex(24),
      'RUSTFS_IMAGE':'rustfs/rustfs:1.0.1',
      'LUME_IMAGE':'pllysun/lume:1.1.2','LUME_SERVER_IMAGE':'pllysun/lume-server:1.1.2','LUME_WORKER_IMAGE':'pllysun/lume-worker:1.1.2',
      'NODE_CGROUP_PREFIX':'lume-node','APP_HEAP_MB':'1024','NODE_CONCURRENCY':'2','NODE_CPU':'2','NODE_MEMORY_MB':'1280',
      'JUDGER_BUILTIN_ENABLED':'true'}
    fd=os.open(env,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:f.write('\n'.join(f'{k}={v}' for k,v in values.items())+'\n')
    fd=os.open(token,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o400)
    with os.fdopen(fd,'w') as f:f.write(secrets.token_hex(32)+'\n')
    # The directory is 0700 on the host. Read-only bind consumers have distinct UIDs.
    token.chmod(0o444)
    print('已生成 .env（0600）和 secrets/judger-token；不会输出密码或令牌。')
    print('默认账号：'+account.lower()+'；登录密码请在本机私有 .env 中查看。')

def main():
    p=argparse.ArgumentParser();p.add_argument('--dir',type=Path,default=Path(__file__).resolve().parents[1]);p.add_argument('--account',default='admin');args=p.parse_args()
    try:initialize(args.dir,args.account)
    except (OSError,ValueError) as e:raise SystemExit(str(e))
if __name__=='__main__':main()
