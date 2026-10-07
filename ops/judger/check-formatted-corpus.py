"""Run formatted local reference solutions against the real, already isolated engine.
No import, database changes, additional container, host configuration or public port.
"""
import base64,json,pathlib,subprocess,zlib
ROOT=pathlib.Path(__file__).resolve().parents[2];HERE=ROOT/'ops/judger';directory=HERE/'problem-packs'
manifest=json.loads((directory/'manifest.json').read_text());packs=[]
for item in manifest['items']:
    file=directory/item['file']
    if item['status']=='PILOT_OWNED_BY_SEPARATE_AGENT':file=directory/item['slug']/'pack.json'
    packs.append(json.loads(file.read_text()))
original=(HERE/'corpus-probe-server.py').read_text()
helpers=original[original.index('def execute('):original.index('start=time.monotonic()')]
loop=original[original.index('    for pack in PACKS:'):original.index('except Exception as error:')].replace("'rounds':3","'rounds':1").replace('range(3)','range(1)')
script="""import base64,json,time,urllib.request,zlib
PACKS=json.loads(zlib.decompress(base64.b64decode('%s')).decode())
TOKEN=open('/run/judger/token').read().strip()
report={'environment':'LIVE_ISOLATED_GO_JUDGE','rounds':1,'packCount':len(PACKS),'results':[]}
def request(path,payload=None,method=None):
    req=urllib.request.Request('http://127.0.0.1:5050'+path,data=json.dumps(payload).encode() if payload is not None else None,headers={'Authorization':'Bearer '+TOKEN,'Content-Type':'application/json'},method=method)
    return json.loads(urllib.request.urlopen(req,timeout=90).read())
"""%base64.b64encode(zlib.compress(json.dumps(packs,ensure_ascii=False).encode(),9)).decode()
script+=helpers+'\nstart=time.monotonic()\ntry:\n'+loop+"""except Exception as e:
    report['harnessError']=str(e)
report['elapsedSeconds']=round(time.monotonic()-start,2)
print(json.dumps({'event':'final-report','report':report}),flush=True)
"""
child=subprocess.Popen(['node',str(ROOT/'ops/server-ssh.cjs'),'docker exec -i sap /usr/bin/python3 -'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,cwd=ROOT)
child.stdin.write(script);child.stdin.close();failed=True
with (HERE/'formatted-corpus-progress.jsonl').open('w') as log:
    for line in child.stdout:
        log.write(line);log.flush()
        try:event=json.loads(line)
        except ValueError:continue
        if event.get('event')=='final-report':
            report=event['report'];failed=bool(report.get('failed') or report.get('harnessError'));(HERE/'formatted-corpus-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='results'}),flush=True)
        else:
            result=event.get('result',{});print(json.dumps({k:result.get(k) for k in ['slug','language','mode','status','executions']}),flush=True)
code=child.wait();error=child.stderr.read()
if error:print(error[:1000])
raise SystemExit(code or (1 if failed else 0))
