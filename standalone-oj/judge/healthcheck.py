#!/usr/bin/env python3
import json,sys,urllib.request
from pathlib import Path
try:
    token=Path('/run/judger/token').read_text().strip()
    request=urllib.request.Request('http://127.0.0.1:5051/status',headers={'Authorization':'Bearer '+token})
    with urllib.request.urlopen(request,timeout=3) as response:data=json.load(response)
    if data.get('state')!='RUNNING' or data.get('protocol')!=1 or int(data.get('capacity',0))<1:sys.exit(1)
except Exception:sys.exit(1)
