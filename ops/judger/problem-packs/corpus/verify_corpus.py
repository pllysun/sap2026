#!/usr/bin/env python3
"""Validate reference packs independently of application import/deployment.
Only local packs owned by corpus/build_corpus.py; pilot files are separate.
No privileges, network or database access. Actual go-judge validation is mandatory.
"""
import argparse,concurrent.futures,json,pathlib,shutil,subprocess,tempfile,time
HERE=pathlib.Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--languages',default='python,c,cpp,java,rust');parser.add_argument('--rounds',type=int,default=1);parser.add_argument('--jobs',type=int,default=4);parser.add_argument('--slugs',default='');parser.add_argument('--report',default='local-validation.json');args=parser.parse_args()
langs=args.languages.split(',')
manifest=json.loads((HERE.parent/'manifest.json').read_text())
items=[x for x in manifest['items'] if x['status']!='PILOT_OWNED_BY_SEPARATE_AGENT']
if args.slugs:items=[x for x in items if x['slug'] in args.slugs.split(',')]
cmds={'python':['python3'],'c':['clang'],'cpp':['clang++'],'java':['javac','java'],'rust':['rustc']}
missing=[l for l in langs if any(not shutil.which(c) for c in cmds[l])];langs=[l for l in langs if l not in missing]

def validate(item,lang):
    pack=json.loads((HERE.parent/item['file']).read_text());results=[]
    for mode in pack['modes']:
        source=pack['references'][lang][mode]
        if mode=='FUNCTION':source=pack['profiles'][lang]['functionDriver'].replace('__USER_CODE__',source)
        with tempfile.TemporaryDirectory(prefix='sap-corpus-') as tmp:
            d=pathlib.Path(tmp);file=d/({'c':'main.c','cpp':'main.cpp','java':'Main.java','python':'main.py','rust':'main.rs'}[lang]);file.write_text(source)
            compile_cmd={'c':['clang','-std=c17','-O2',str(file),'-o',str(d/'run')],'cpp':['clang++','-std=c++23','-O2',str(file),'-o',str(d/'run')],'java':['javac',str(file)],'rust':['rustc','--edition=2024','-O',str(file),'-o',str(d/'run')],'python':['python3','-m','py_compile',str(file)]}[lang]
            compiled=subprocess.run(compile_cmd,cwd=d,text=True,capture_output=True,timeout=60)
            if compiled.returncode:
                return dict(slug=pack['slug'],language=lang,mode=mode,status='COMPILE_FAILED',details=compiled.stderr[:5000])
            run={'python':['python3',str(file)],'java':['java','-cp',str(d),'Main']}.get(lang,[str(d/'run')])
            for repeat in range(args.rounds):
                for case in pack['cases']:
                    actual=subprocess.run(run,input=case['input'],text=True,capture_output=True,cwd=d,timeout=20)
                    if actual.returncode or actual.stdout.split()!=case['expectedOutput'].split():
                        return dict(slug=pack['slug'],language=lang,mode=mode,status='FAILED',case=case['name'],input=case['input'],expected=case['expectedOutput'],actual=actual.stdout,details=actual.stderr[:3000])
            results.append(dict(mode=mode,cases=len(pack['cases']),rounds=args.rounds,status='PASSED'))
    return dict(slug=pack['slug'],language=lang,status='PASSED',modes=results)

start=time.monotonic();results=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
    futures=[pool.submit(validate,i,l) for i in items for l in langs]
    for f in concurrent.futures.as_completed(futures):
        try:r=f.result()
        except Exception as e:r={'status':'HARNESS_ERROR','details':str(e)}
        results.append(r)
        if r['status']!='PASSED':print(json.dumps(r,ensure_ascii=False),flush=True)
report={'environment':'LOCAL_NATIVE_NOT_GO_JUDGE','elapsedSeconds':round(time.monotonic()-start,2),'requestedLanguages':args.languages.split(','),'missingLanguages':missing,'rounds':args.rounds,'packCount':len(items),'passed':sum(r['status']=='PASSED' for r in results),'failed':sum(r['status']!='PASSED' for r in results),'toolVersions':{},'results':sorted(results,key=lambda r:(r.get('slug',''),r.get('language','')))}
for lang in langs:
    c=cmds[lang][0];p=subprocess.run([c,'--version'] if c!='javac' else [c,'-version'],text=True,capture_output=True);report['toolVersions'][lang]=(p.stdout+p.stderr).strip().splitlines()[0]
(HERE/args.report).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k not in ['results','toolVersions']},ensure_ascii=False))
raise SystemExit(1 if report['failed'] else 0)
