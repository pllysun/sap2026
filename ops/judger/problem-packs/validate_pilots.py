"""Compile and run every reference against all pilot cases. No network or dependencies.
Usage: python3 validate_pilots.py --languages c cpp java python rust --rounds 3
Toolchain overrides: CC CXX JAVAC JAVA PYTHON RUSTC.
"""
import argparse,json,os,pathlib,shutil,subprocess,sys,tempfile,time
ROOT=pathlib.Path(__file__).resolve().parent
SLUGS=['luogu-p1001','leetcode-1','leetcode-20','leetcode-704','leetcode-53','leetcode-206']
ap=argparse.ArgumentParser();ap.add_argument('--languages',nargs='+',default=['c','cpp','java','python','rust']);ap.add_argument('--rounds',type=int,default=3);ap.add_argument('--report',default=str(ROOT/'pilot-validation.json'));args=ap.parse_args()
report={'rounds':args.rounds,'results':[]}
def run(cmd,**kw):return subprocess.run(cmd,capture_output=True,text=True,timeout=60,**kw)
def exe(name,env):return os.environ.get(env) or shutil.which(name)
TOOL={'c':exe('cc','CC'),'cpp':exe('c++','CXX'),'java':exe('javac','JAVAC'),'python':exe('python3','PYTHON'),'rust':exe('rustc','RUSTC')}
for slug in SLUGS:
    p=json.loads((ROOT/slug/'pack.json').read_text())
    assert len(p['cases'])>=25
    for l in args.languages:
        if not TOOL[l]:raise RuntimeError(f'Missing {l} toolchain; configure environment override')
        for mode in p['modes']:
            source=p['references'][l][mode]
            if mode=='FUNCTION':
                driver=p['profiles'][l]['functionDriver'];assert driver.count('__USER_CODE__')==1
                source=driver.replace('__USER_CODE__',source)
            with tempfile.TemporaryDirectory(prefix='sap-pilot-') as tmp:
                wd=pathlib.Path(tmp);name={'c':'main.c','cpp':'main.cpp','java':'Main.java','python':'main.py','rust':'main.rs'}[l];f=wd/name;f.write_text(source)
                binary=wd/'main'
                if l=='c':cmd=[TOOL[l],'-std=c17','-O2',str(f),'-o',str(binary)]
                elif l=='cpp':cmd=[TOOL[l],'-std=c++17','-O2',str(f),'-o',str(binary)]
                elif l=='java':cmd=[TOOL[l],str(f)]
                elif l=='rust':cmd=[TOOL[l],'--edition=2024','-O',str(f),'-o',str(binary)]
                else:cmd=[TOOL[l],'-m','py_compile',str(f)]
                compiled=run(cmd,cwd=tmp)
                if compiled.returncode:raise RuntimeError(f'{slug}/{l}/{mode} compile failed:\n{compiled.stderr}')
                execute=[str(binary)] if l in ['c','cpp','rust'] else [exe('java','JAVA'),'-cp',tmp,'Main'] if l=='java' else [TOOL[l],str(f)]
                start=time.monotonic()
                for roundno in range(args.rounds):
                    for case in p['cases']:
                        result=run(execute,input=case['input'],cwd=tmp)
                        actual=result.stdout.split();expected=case['expectedOutput'].split()
                        if p['checker']=='UNORDERED_TOKENS':actual=sorted(actual);expected=sorted(expected)
                        if result.returncode or actual!=expected:raise RuntimeError(f'{slug}/{l}/{mode}/round{roundno+1}/{case["name"]}: exit {result.returncode}, output {result.stdout[:200]!r}, expected {case["expectedOutput"][:200]!r}, stderr {result.stderr[:200]}')
                record=dict(slug=slug,language=l,mode=mode,caseCount=len(p['cases']),rounds=args.rounds,status='PASS',seconds=round(time.monotonic()-start,3))
                report['results'].append(record);print(json.dumps(record),flush=True)
pathlib.Path(args.report).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
