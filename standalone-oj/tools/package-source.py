#!/usr/bin/env python3
"""Package independent source; exclude private configuration, runtime data and build outputs."""
import argparse,hashlib,os,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SKIP={'node_modules','target','dist','.git','secrets','runtime','backups','__pycache__','reports','.system'}
def include(path):
    rel=path.relative_to(ROOT)
    if any(part in SKIP for part in rel.parts) or path.is_symlink() or not path.is_file():return False
    name=path.name
    if name.startswith('._') or name in {'.DS_Store','judge-token','judger-token'} or (name.startswith('.env') and name!='.env.example'):return False
    if '.local.' in name or name.endswith(('.log','.mv.db','.trace.db','.key','.zip','.tar','.tar.gz','.pyc')):return False
    return True

def package(output):
    if output.exists():raise ValueError('目标文件已存在，拒绝覆盖')
    output.parent.mkdir(parents=True,exist_ok=True)
    files=[p for p in ROOT.rglob('*') if include(p)]
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(files):archive.write(path,'lume-'+(ROOT/'VERSION').read_text().strip()+'/'+str(path.relative_to(ROOT)))
    print('源码文件：'+str(len(files))+'；字节：'+str(output.stat().st_size))
    print('SHA-256：'+hashlib.sha256(output.read_bytes()).hexdigest())

def main():
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);args=p.parse_args()
    try:package(args.output.resolve())
    except (OSError,ValueError) as e:raise SystemExit(str(e))
if __name__=='__main__':main()
