// Preserve existing content/configuration; only reference-code RUNs and a repeat
// Java submission for the already-accepted Two Sum question exercise production.
const fs=require('node:fs'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root='/Users/pllysun/Library/Caches/sap-oj-fixes-1513',session=JSON.parse(fs.readFileSync(root+'/production-session.json'));
const before=JSON.parse(fs.readFileSync(root+'/before-production.json')),packs=JSON.parse(fs.readFileSync(root+'/production-packs.json'));
const report={passed:false,image:'pllysun/sap:1.5.13',environment:session.base,checks:[],jobs:[]};
const save=()=>fs.writeFileSync(__dirname+'/fixes-api-production.json',JSON.stringify(report,null,2)+'\n');
const hash=s=>crypto.createHash('sha256').update(s).digest('hex'),delay=ms=>new Promise(r=>setTimeout(r,ms));
async function api(path,method='GET',body,token=session.token,expected=200){const r=await(await fetch(session.base+path,{method,headers:{'Content-Type':'application/json',...(token?{'sap-token':token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)})).json();assert.equal(r.code,expected,path+': '+r.message);return r.data;}
const descriptor=s=>({id:s.id,name:s.name,mode:s.mode,status:s.status,startsAt:s.startsAt,endsAt:s.endsAt,publicCode:s.publicCode,items:s.items.map(i=>({id:i.id,problemId:i.problemId,label:i.label,title:i.title,active:i.active,revision:i.revision}))});
function check(text){report.checks.push(text);save();console.log(text);}
async function finish(id){for(let n=0;n<500;n++){const j=await api('/api/oj/submissions/'+id);if(!['QUEUED','RUNNING'].includes(j.status)){assert.equal(j.status,'AC','Job '+id+': '+j.status);report.jobs.push({id,status:j.status,language:j.language,mode:j.mode,kind:j.kind,node:j.nodeName,passedCases:j.passedCases,totalCases:j.totalCases});save();return j;}await delay(350);}throw Error('Timeout '+id);}
(async()=>{
 assert.equal((await api('/api/oj/problems')).total,before.publicProblems);
 for(const old of before.sets)assert.deepEqual(descriptor(await api('/api/admin/oj/sets/'+old.id)),old);
 assert.deepEqual((await api('/api/admin/oj/sets/1/ranking')).records,before.practiceRank);
 for(const old of before.sources)assert.equal(hash((await api('/api/admin/oj/monitor/'+old.id+'/code')).code),old.sha256);
 const langs=await api('/api/admin/oj/languages');assert.deepEqual(langs,before.languages);
 let rows=[];for(let page=1;;page++){const data=await api('/api/admin/oj/problems?page='+page+'&size=50');rows.push(...data.records);if(rows.length>=Number(data.total))break;}
 assert.equal(rows.length,before.problems.length);
 for(const p of packs){const d=await api('/api/admin/oj/problems/'+p.id);assert.equal(d.status,p.status);assert.equal(d.revision,p.revision);assert.equal(hash(JSON.stringify(d.pack)),p.packSha);}
 check('70 public / 75 admin problems and all pack hashes preserved; existing practice/contest draft, ordering, code hashes, language configuration and exact practice standings unchanged');
 const runtime=await api('/api/oj/runtime');assert.equal(runtime.availableSlots,runtime.freeSlots);assert.equal(Number(runtime.capacity),9);
 const health=await api('/api/admin/oj/health');assert.equal(health.dailySubmissionLimit,0);assert.equal(Number(health.queued),0);assert.equal(Number(health.capacity),9);
 const monitor=await api('/api/admin/oj/monitor');assert(Number(monitor.total)>0);
 await api('/api/oj/submit','POST',{},null,401);await api('/api/oj/submit','POST',{},session.token,400);
 const logs=(await api('/api/log/explore?endpoint='+encodeURIComponent('/api/oj/submit')+'&size=30')).records;
 for(const code of [401,400])assert(logs.some(row=>Number(row.result_code)===code));
 check('Both nodes online with 9 slots; live MySQL monitor/standings queries, runtime API and authentication/validation audit entries work');
 const p=packs.find(p=>p.slug==='leetcode-1');assert(p);
 for(const language of ['c','cpp','java','python','rust']){
  const code=p.pack.references[language].STDIO,q=await api('/api/oj/run','POST',{problemId:p.id,language,mode:'STDIO',code});const j=await finish(q.id);assert.equal(j.code,code);
 }
 const functionCode=p.pack.references.java.FUNCTION;
 const sample=await api('/api/oj/run','POST',{problemId:p.id,language:'java',mode:'FUNCTION',code:functionCode});await finish(sample.id);
 const formal=await api('/api/oj/submit','POST',{problemId:p.id,language:'java',mode:'FUNCTION',code:functionCode});await finish(formal.id);
 const perf=await api('/api/oj/submissions/'+formal.id+'/performance');assert.equal(perf.comparable,true);assert(perf.sampleCount>=1);
 assert((await api('/api/oj/problems/'+p.id+'/ranking?language=java&mode=FUNCTION')).records.length>=1);
 await api('/api/oj/leaderboard');
 assert.deepEqual((await api('/api/admin/oj/sets/1/ranking')).records,before.practiceRank);
 check('Live C/C++/JDK27/Python/Rust sample runs and Java function-mode sample/formal submissions all AC; saved code, performance chart data and question ranking correct');
 for(let n=0;n<30;n++){const h=await api('/api/admin/oj/health');if(Number(h.availableSlots)===9&&Number(h.queued)===0){report.health={capacity:h.capacity,availableSlots:h.availableSlots,queued:h.queued};break;}await delay(500);}
 assert(report.health);report.formalJob=formal.id;report.finishedAt=new Date().toISOString();report.passed=true;save();console.log(JSON.stringify({passed:true,jobs:report.jobs.length,checks:report.checks.length,health:report.health}));
})().catch(e=>{report.error=e.message;save();console.error(e.stack);process.exitCode=1});
