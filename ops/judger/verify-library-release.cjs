const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const session=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const root=path.join(__dirname,'problem-packs'),report={image:'pllysun/sap:1.5.10',passed:false,checks:[],submissions:[]};
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function api(route,method='GET',body,authenticated=true){
  const response=await fetch(session.base+route,{method,headers:{'Content-Type':'application/json',...(authenticated?{'sap-token':session.token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  const result=await response.json();if(!authenticated)return result;assert.equal(result.code,200,route+': '+result.message);return result.data;
}
async function allRows(admin=false){let rows=[],page=1,data;do{data=await api((admin?'/api/admin/oj':'/api/oj')+'/problems?size=50&page='+page++);rows.push(...data.records);}while(rows.length<data.total);return rows;}
async function finished(id){for(let attempt=0;attempt<300;attempt++){const job=await api('/api/oj/submissions/'+id);if(!['RUNNING','QUEUED'].includes(job.status))return job;await sleep(1000);}throw Error('Submission timed out');}
async function main(){
  const classic=JSON.parse(fs.readFileSync(path.join(__dirname,'classic-import-production.json'))),original=JSON.parse(fs.readFileSync(path.join(__dirname,'original-import-production.json')));
  for(const group of [classic,original]){assert.equal(group.passed,true);assert(group.results.every(row=>row.status==='AC'&&row.passedCases===row.totalCases));}
  const rows=await allRows(),admin=await allRows(true);assert.equal(rows.length,70);assert.equal(admin.length,70);
  assert.equal(new Set(rows.map(row=>row.slug)).size,70);assert(admin.every(row=>row.validated&&row.status==='PUBLISHED'));
  assert.deepEqual(rows.map(row=>row.id),admin.map(row=>row.id));
  const count=key=>Object.fromEntries([...new Set(rows.map(row=>row[key]))].map(value=>[value,rows.filter(row=>row[key]===value).length]));
  report.sources=count('sourcePlatform');report.difficulties=count('difficulty');assert.deepEqual(report.sources,{'洛谷':5,'LeetCode':40,'原创':25});assert.deepEqual(report.difficulties,{EASY:40,MEDIUM:22,HARD:8});
  const originals=rows.filter(row=>row.slug.startsWith('original-acm-'));assert.equal(originals.length,20);assert(originals.every(row=>row.modes.length===1&&row.modes[0]==='STDIO'&&!row.tags.includes('链表')));
  report.checks.push('70 unique published and validated problems; order matches admin','40 LeetCode / 5 Luogu / 25 original; difficulty 40/22/8','20 new beginner STDIO originals without linked lists');
  for(const row of rows){
    const problem=await api('/api/oj/problems/'+row.id);assert(problem.sourcePlatform&&problem.sourceNote);
    if(!['原创','ORIGINAL'].includes(problem.sourcePlatform))assert(problem.sourceUrl?.startsWith('https://'));
    assert(problem.samples.length>0);
    for(const key of ['pack','packJson','profiles','references','cases','validationSignature'])assert(!(key in problem),key);
    assert.equal(problem.languages.length,5);assert(problem.languages.find(language=>language.languageKey==='java').version.includes('27'));
    assert.equal(Object.keys(problem.templates).length,5);
  }
  report.checks.push('all sources and samples visible; private tests and solutions excluded; five templates each');
  const anonymous=await api('/api/oj/filters','GET',undefined,false);assert.equal(anonymous.code,401);
  await api('/api/oj/filters');
  const logs=await api('/api/log/explore?dimension=detail&endpoint=%2Fapi%2Foj%2Ffilters&size=5');assert(logs.records.length>0);assert(!JSON.stringify(logs).includes(session.token));
  report.checks.push('authentication required; new filter endpoint audited without leaking the token');
  const row=originals.find(row=>row.slug==='original-acm-008'),pack=JSON.parse(fs.readFileSync(path.join(root,'beginner-original/a/original-acm-008/pack.json')));
  for(const language of ['c','cpp','java','python','rust']){
    const code=pack.references[language].STDIO,q=await api('/api/oj/submit','POST',{problemId:row.id,language,mode:'STDIO',code}),job=await finished(q.id);
    assert.equal(job.status,'AC',language+': '+job.status);assert.equal(job.code,code);assert.deepEqual(job.result.cases,[]);
    const performance=await api('/api/oj/submissions/'+q.id+'/performance');assert(performance);
    report.submissions.push({slug:pack.slug,language,status:job.status,passedCases:job.passedCases,codePreserved:true});
  }
  const textRow=originals.find(row=>row.slug==='original-acm-017'),textPack=JSON.parse(fs.readFileSync(path.join(root,'beginner-original/b/original-acm-017/pack.json')));
  const q=await api('/api/oj/run','POST',{problemId:textRow.id,language:'python',mode:'STDIO',code:textPack.references.python.STDIO,input:'     \n'}),job=await finished(q.id);
  assert.equal(job.status,'AC');assert.equal(job.result.cases[0].output.trim(),'0');assert.equal(job.code,textPack.references.python.STDIO);
  report.checks.push('five-language formal submissions; code persistence and performance API','valid all-space custom input run');
  const board=await api('/api/oj/leaderboard');assert.deepEqual(Object.fromEntries(Object.entries(board.totals).map(([key,value])=>[key,Number(value)])),{total:70,easy:40,medium:22,hard:8});
  const health=await api('/api/admin/oj/health');assert.equal(String(health.queued),'0');assert.equal(health.available,true);report.health={available:health.available,queued:health.queued};
  report.publishedProblems=70;report.validatedCombinations=440;report.threeRoundExecutions=classic.results.reduce((n,row)=>n+row.passedCases,0)+original.results.reduce((n,row)=>n+row.passedCases,0);
  assert.equal(report.threeRoundExecutions,30090);report.passed=true;report.finishedAt=new Date().toISOString();
  fs.writeFileSync(path.join(__dirname,'library-release-production.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({passed:true,image:report.image,published:70,executions:report.threeRoundExecutions,smokeSubmissions:report.submissions.length,checks:report.checks}));
}
main().catch(error=>{report.error=error.message;fs.writeFileSync(path.join(__dirname,'library-release-production.json'),JSON.stringify(report,null,2)+'\n');console.error(error.message);process.exitCode=1;});
