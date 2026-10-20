#!/bin/sh
set -eu
# 凭据只在 Python 进程中读取，不放入 wget/curl 的命令行参数。
exec python3 - <<'PYHEALTH'
import os, ssl, urllib.request
from pathlib import Path
if os.environ.get('JUDGER_NODE_ONLY', 'false') != 'true':
    with urllib.request.urlopen('http://127.0.0.1:8081/api/ping', timeout=4) as response:
        if response.status != 200: raise SystemExit(1)
if os.environ.get('JUDGER_ENABLED', 'false') == 'true' or os.environ.get('JUDGER_NODE_ONLY', 'false') == 'true':
    tls = bool(os.environ.get('NODE_TLS_CERT'))
    # 仅容器内部回环探针沿用原先的自签名证书兼容方式。
    context = ssl._create_unverified_context() if tls else None
    token = Path('/run/judger/token').read_text().strip()
    request = urllib.request.Request(('https' if tls else 'http') + '://127.0.0.1:5051/status', headers={'Authorization': 'Bearer ' + token})
    with urllib.request.urlopen(request, timeout=4, context=context) as response:
        if response.status != 200: raise SystemExit(1)
PYHEALTH
