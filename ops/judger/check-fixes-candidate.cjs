// Complete reference verification uses the release jar, real node and disposable H2.
const fs=require('node:fs'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root='/Users/pllysun/Library/Caches/sap-oj-fixes-1513',base='http://127.0.0.1:18113';
const source=JSON.parse(fs.readFileSync(root+'/production-packs.json')),privateData=JSON.parse(fs.readFileSync(root+'/candidate-private.json'));
const delay=ms=>new Promise(r=>setTimeout(r,ms));let token;
const report={passed:false,environment:'isolated real-node candidate',rounds:3,results:[]};
async function api(path,method='GET',body,who=token,expected=200){
 for(let n=0;n<120;n++){
  const r=await(await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(who?{'sap-token':who}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)})).json();
  if(expected===200 && [429,503].includes(r.code)){await delay(1200);continue;}
  assert.equal(r.code,expected,path+': '+r.message);return r.data;
 }throw Error('Resource/limit retry timeout: '+path);
}
const save=()=>fs.writeFileSync(__dirname+'/fixes-reference-candidate.json',JSON.stringify(report,null,2)+'\n');
async function done(id){for(let n=0;n<1800;n++){const job=await api('/api/admin/oj/jobs/'+id);if(!['QUEUED','RUNNING'].includes(job.status))return job;await delay(600);}throw Error('Timeout: '+id);}
(async()=>{
 token=(await api('/api/auth/admin/login','POST',{studentId:privateData.studentId,password:privateData.password},null)).token;
 fs.writeFileSync(root+'/candidate-session.json',JSON.stringify({base,token}),{mode:0o600});
 const prodSession=JSON.parse(fs.readFileSync(root+'/production-session.json'));
 const languages=(await(await fetch(prodSession.base+'/api/admin/oj/languages',{headers:{'sap-token':prodSession.token}})).json()).data;
 await api('/api/admin/oj/languages','PUT',languages);
 assert.equal((await api('/api/admin/oj/health')).dailySubmissionLimit,0); // Practice remains unlimited.
 const catalog=[];
 for(const original of source){const p=await api('/api/admin/oj/problems','POST',original.pack);catalog.push({...original,candidateId:p.id});}
 fs.writeFileSync(root+'/candidate-packs.json',JSON.stringify(catalog),{mode:0o600});
 let cursor=0;await Promise.all(Array.from({length:6},async()=>{
  for(;;){const p=catalog[cursor++];if(!p)return;const queued=await api('/api/admin/oj/problems/'+p.candidateId+'/validate','POST');const job=await done(queued.id);
   assert.equal(job.status,'AC',p.slug+' reference validation');assert.equal(job.passedCases,job.totalCases);
   assert.equal(job.result.reports.length,p.pack.modes.length*5);assert(job.result.reports.every(r=>r.result.verdict==='AC'));
   await api('/api/admin/oj/problems/'+p.candidateId+'/status','PUT',{status:p.status});
   report.results.push({slug:p.slug,jobId:job.id,modes:p.pack.modes,languages:job.result.reports.map(r=>r.language),passedCases:job.passedCases,totalCases:job.totalCases});save();
   console.log(JSON.stringify({validated:report.results.length,total:catalog.length,slug:p.slug,cases:job.totalCases}));
  }
 }));
 assert.equal(report.results.length,75);assert.equal((await api('/api/oj/problems')).total,70);
 report.passed=true;report.caseExecutions=report.results.reduce((n,r)=>n+r.totalCases,0);save();
 console.log(JSON.stringify({passed:true,problems:75,caseExecutions:report.caseExecutions,rounds:3}));
})().catch(e=>{report.error=e.message;save();console.error(e.message);process.exitCode=1});
