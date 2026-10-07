// New, explicitly requested contest questions stay private throughout validation.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const session=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const directory=path.join(__dirname,'problem-packs/contest-original');
const manifest=JSON.parse(fs.readFileSync(path.join(directory,'manifest.json')));
const reportFile=path.join(__dirname,'contest-import-production.json');
const report={environment:session.base,startedAt:new Date().toISOString(),rounds:3,results:[],passed:false};
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function api(route,method='GET',body){const r=await fetch(session.base+route,{method,headers:{'sap-token':session.token,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});const v=await r.json();if(v.code!==200){const e=Error(route+': '+v.message);e.code=v.code;throw e;}return v.data;}
const save=()=>fs.writeFileSync(reportFile,JSON.stringify(report,null,2)+'\n');
async function wait(id){for(let n=0;n<1800;n++){const j=await api('/api/admin/oj/jobs/'+id);if(!['RUNNING','QUEUED'].includes(j.status))return j;await delay(1500);}throw Error('Validation timeout');}
async function main(){
 assert.equal(manifest.length,5);let rows=[];for(let page=1;;page++){const d=await api('/api/admin/oj/problems?size=50&page='+page);rows.push(...d.records);if(rows.length>=d.total)break;}
 for(const entry of manifest){const file=path.join(directory,entry.path),pack=JSON.parse(fs.readFileSync(file));assert.deepEqual(pack.modes,['STDIO']);assert.equal(Object.keys(pack.references).length,5);
  let p=rows.find(r=>r.slug===pack.slug);if(!p){p=await api('/api/admin/oj/problems','POST',pack);rows.push({...p,slug:pack.slug});}
  const d=await api('/api/admin/oj/problems/'+p.id);assert(['DRAFT','CONTEST_ONLY'].includes(d.status),'Contest question unexpectedly public');
  assert.equal(d.pack.title,pack.title);assert.deepEqual(d.pack.cases,pack.cases);assert.deepEqual(d.pack.references,pack.references);
  let job;
  if(d.validated&&d.validationJobId)job=await wait(d.validationJobId);
  else{for(let n=0;;n++){try{job=await api('/api/admin/oj/problems/'+p.id+'/validate','POST');break;}catch(e){if(![429,503].includes(e.code)||n>=300)throw e;await delay(2000);}}job=await wait(job.id);}
  assert.equal(job.status,'AC',JSON.stringify({slug:pack.slug,job:job.id,status:job.status,message:job.result?.message,compiler:job.result?.compilerOutput}));
  assert.equal(job.totalCases,pack.cases.length*5*3);assert.equal(job.passedCases,job.totalCases);
  if(process.argv.includes('--mark-private'))await api('/api/admin/oj/problems/'+p.id+'/status','PUT',{status:'CONTEST_ONLY'});
  const result={slug:pack.slug,id:p.id,jobId:job.id,languages:Object.keys(pack.references),modes:pack.modes,cases:pack.cases.length,passedCases:job.passedCases,totalCases:job.totalCases,storedStatus:process.argv.includes('--mark-private')?'CONTEST_ONLY':d.status,packSha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')};report.results.push(result);save();console.log(JSON.stringify(result));
 }
 report.passed=true;report.finishedAt=new Date().toISOString();report.caseExecutions=report.results.reduce((n,r)=>n+r.passedCases,0);save();console.log(JSON.stringify({passed:true,problems:5,caseExecutions:report.caseExecutions}));
}
main().catch(e=>{report.error=e.message;save();console.error(e.message);process.exitCode=1;});
