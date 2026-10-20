#!/usr/bin/env python3
import json,sys,urllib.request
from pathlib import Path
try:
    op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with op.open('http://127.0.0.1:8080/api/ping',timeout=3) as r:assert json.load(r)['code']==200
    token=Path('/app/data/.system/judge-token').read_text().strip()
    req=urllib.request.Request('http://127.0.0.1:5051/status',headers={'Authorization':'Bearer '+token})
    with op.open(req,timeout=3) as r:state=json.load(r)
    assert state.get('protocol')==1 and state.get('state') in ('RUNNING','STOPPED','STOPPING')
except Exception:sys.exit(1)
