#!/usr/bin/env python3
"""Check annotated editorials in the existing native isolated judge (one slot).
Uses no platform submission API and changes no problem packs, verdicts or statistics.
Private connection/token material stays in the established host environment.
"""
import argparse,base64,hashlib,json,pathlib,subprocess,zlib
ROOT=pathlib.Path(__file__).resolve().parents[3]
HERE=pathlib.Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--partial',action='store_true');args=parser.parse_args()
private=pathlib.Path.home()/'Library/Caches/sap-solutions-1539'
limits=json.loads((private/'limits.json').read_text())
manifest=json.loads((HERE/'manifest.json').read_text())
report_file=HERE/'validation.json';previous=json.loads(report_file.read_text()) if report_file.exists() else {'results':[]}
passed={r['verificationSha256']:r for r in previous['results'] if r.get('status')=='PASSED'}
tasks=[];expected=[];documents=0
for item in manifest['items']:
 file=HERE/item['document']
 if not file.exists():
  if args.partial:continue
  raise SystemExit('Missing editorial '+item['slug'])
 document=json.loads(file.read_text());pack=json.loads((HERE/item['source']).read_text())['pack'];documents+=1
 assert set(document['codes'])==set(pack['modes']),item['slug']
 for mode,codes in document['codes'].items():
  assert set(codes)=={'c','cpp','java','python','rust'},item['slug']
  for language,code in codes.items():
   task={'slug':item['slug'],'mode':mode,'language':language,'code':code,'codeSha256':hashlib.sha256(code.encode()).hexdigest(),'checker':pack['checker'],'cases':pack['cases'],'driver':pack['profiles'][language]['functionDriver'] if mode=='FUNCTION' else ''}
   limit=next(row for row in limits if row['language_key']==language)
   task['verificationSha256']=hashlib.sha256(json.dumps({'task':task,'limits':limit},sort_keys=True,ensure_ascii=False).encode()).hexdigest()
   expected.append(task['verificationSha256'])
   if task['verificationSha256'] not in passed:tasks.append(task)
payload={'limits':limits,'tasks':tasks}
compressed=base64.b64encode(zlib.compress(json.dumps(payload,ensure_ascii=False).encode(),9)).decode()
script=(private/'native-editorial-check.py').read_text().replace('__EDITORIAL_PAYLOAD__',"__import__('json').loads(__import__('zlib').decompress(__import__('base64').b64decode('"+compressed+"')))")
identity=pathlib.Path.home()/'Library/Caches/sap-judger-pllysun'
command=['ssh','-i',str(identity/'identity'),'-o','IdentitiesOnly=yes','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UserKnownHostsFile='+str(identity/'known_hosts'),'-o','ConnectTimeout=15','-o','ServerAliveInterval=15','root@pllysun.top','python3 -']
child=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
child.stdin.write(script);child.stdin.close()
results=[];errors=[]
print(json.dumps({'documents':documents,'programs':len(expected),'remaining':len(tasks),'resumePassed':len(expected)-len(tasks)}),flush=True)
with (private/'native-check-progress.jsonl').open('w') as log:
 for line in child.stdout:
  log.write(line);log.flush()
  try:event=json.loads(line)
  except ValueError:continue
  if event.get('event')=='program':
   results.append(event['result']);print(json.dumps({k:event['result'][k] for k in ('slug','language','mode','status','cases')}),flush=True)
  elif event.get('event')=='harnessError':errors.append(event['error']);print(json.dumps(event),flush=True)
  else:print(json.dumps(event),flush=True)
exit_code=child.wait();stderr=child.stderr.read()
if exit_code and stderr:print(stderr[-1500:],flush=True)
current={**passed,**{r['verificationSha256']:r for r in results}}
selected=[current[key] for key in expected if key in current]
report={'environment':'NATIVE_LEASED_ISOLATED_JUDGE','runtime':'GCC15.3-C23/C++23,OpenJDK27,CPython3.14.7,Rust1.98.1-2024','rounds':1,'documents':documents,'expectedPrograms':len(expected),'completedPrograms':len(selected),'passed':sum(r['status']=='PASSED' for r in selected),'failed':sum(r['status']!='PASSED' for r in selected),'executions':sum(r['cases'] for r in selected),'limits':limits,'harnessErrors':errors,'results':selected,'platformSubmissionsCreated':0}
report_file.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k not in ('results','limits')}),flush=True)
raise SystemExit(exit_code or (1 if errors or report['failed'] or len(selected)!=len(expected) else 0))
