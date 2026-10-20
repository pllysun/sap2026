#!/usr/bin/env python3
"""Package public documentation and deployment helpers using an explicit allowlist."""
import argparse,hashlib,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FILES=('LICENSE','VERSION','.env.example','compose.yml','compose.split.yml',
       'judge/judger-seccomp.json','docker/nginx.conf.example',
       'tools/init.py','tools/run-container.sh','tools/prepare-judge-host.sh',
       'tools/ojctl.py','tools/generate_example.py')

def package(output):
    if output.exists():raise ValueError('目标文件已存在，拒绝覆盖')
    version=(ROOT/'VERSION').read_text().strip();prefix='lume-docs-'+version+'/'
    paths=[ROOT/name for name in FILES]
    paths.extend(p for p in (ROOT/'docs').rglob('*') if p.is_file() and p.suffix in ('.md','.json','.png','.svg'))
    paths.extend(p for p in (ROOT/'examples/original-sum').rglob('*') if p.is_file() and p.suffix in ('.json','.md','.py'))
    if any(p.is_symlink() for p in paths):raise ValueError('文档包不接受符号链接')
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        archive.writestr(prefix+'README.md',
            '# Lume '+version+' 文档与部署包\n\n'
            '先阅读 docs/SINGLE_CONTAINER.md：本包包含单容器与分离版部署配置、初始化器、seccomp、AI 工具及完整操作文档。'
            '不含应用源码或部署凭据，首次题库为空。\n\n'
            '运行 python3 tools/init.py，编辑私有 .env 的外部 MySQL，再运行 bash tools/run-container.sh 或 docker compose up -d。'
            'RustFS / S3 可选。\n\n'
            'AI 出题看 docs/AI_OPERATIONS.md、PROBLEM_AUTHORING.md、SOLUTION_STANDARD.md。'
            'examples 仅供协议参考，不自动导入。需要修改应用源码、查看 DTO 或重新构建镜像时，另使用完整源码包。\n')
        archive.writestr(prefix+'AGENTS.md',
            '本包是文档与部署包，不含应用源码。源码修改先取得完整源码包，以下为该源码包的操作规则。\n\n'+(ROOT/'AGENTS.md').read_text())
        for path in sorted(paths):archive.write(path,prefix+str(path.relative_to(ROOT)))
    print('文档与部署文件：'+str(len(paths)+2)+'；字节：'+str(output.stat().st_size))
    print('SHA-256：'+hashlib.sha256(output.read_bytes()).hexdigest())

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('output',type=Path);args=parser.parse_args()
    try:package(args.output.resolve())
    except (OSError,ValueError) as error:raise SystemExit(str(error))
