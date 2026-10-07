"""Local launcher sends compressed packs and server script via SSH helper stdin."""
import base64,json,pathlib,subprocess,zlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
HERE=ROOT/'ops/judger'
packroot=HERE/'problem-packs'
manifest=json.loads((packroot/'manifest.json').read_text())
packs=[]
for item in manifest['items']:
    file=packroot/item['file']
    if item['status']=='PILOT_OWNED_BY_SEPARATE_AGENT':file=packroot/item['slug']/'pack.json'
    packs.append(json.loads(file.read_text()))
assert len(packs)==50 and len({p['slug'] for p in packs})==50
encoded=base64.b64encode(zlib.compress(json.dumps(packs,ensure_ascii=False).encode(),9)).decode()
script=(HERE/'corpus-probe-server.py').read_text().replace('__PACKS_PAYLOAD__',encoded)
child=subprocess.Popen(['node',str(ROOT/'ops/server-ssh.cjs'),'python3 -'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,cwd=ROOT)
child.stdin.write(script);child.stdin.close()
failed = False
with (HERE/'corpus-gojudge-progress.jsonl').open('w') as log:
    for line in child.stdout:
        log.write(line);log.flush()
        try:event=json.loads(line)
        except ValueError:print('Remote non-JSON output omitted',flush=True);continue
        if event.get('event')=='final-report':
            final=event['report'];cleanup=final.get('cleanup',{})
            failed=bool(final.get('failed') or final.get('harnessError') or not cleanup.get('namespaceRestored') or not cleanup.get('containerRemoved') or cleanup.get('remainingCgroups'))
            (HERE/'corpus-gojudge-validation.json').write_text(json.dumps(event['report'],ensure_ascii=False,indent=2)+'\n')
            print(json.dumps({k:v for k,v in event['report'].items() if k not in ['results','judgeConfig']}),flush=True)
        else:print(line.rstrip(),flush=True)
error=child.stderr.read();code=child.wait()
if error:print(error[:2000],flush=True)
raise SystemExit(code or (1 if failed else 0))
