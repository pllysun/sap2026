// Destructive fixture scenarios are restricted to the disposable H2 candidate.
const fs=require('node:fs'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root='/Users/pllysun/Library/Caches/sap-oj-fixes-1513';
const admin=JSON.parse(fs.readFileSync(root+'/candidate-session.json'));
assert.equal(admin.base,'http://127.0.0.1:18113');
const packs=JSON.parse(fs.readFileSync(root+'/candidate-packs.json'));
const resumeLanguage=process.argv.includes('--resume-language');
const resume=process.argv.includes('--resume-practice')||resumeLanguage;
const report=resume?JSON.parse(fs.readFileSync(__dirname+'/fixes-api-candidate.json')):{passed:false,checks:[],jobs:[],startedAt:new Date().toISOString()};
delete report.error;
const save=()=>fs.writeFileSync(__dirname+'/fixes-api-candidate.json',JSON.stringify(report,null,2)+'\n');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function api(path,method='GET',body,actor=admin,expected=200){
 const response=await fetch(admin.base+path,{method,headers:{'Content-Type':'application/json',...(actor?{'sap-token':actor.token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
 const r=await response.json();assert.equal(r.code,expected,path+': '+r.message);return r.data;
}
function check(text){report.checks.push(text);save();console.log(text);}
async function finish(id,actor=admin,expected='AC'){
 for(let n=0;n<600;n++){const job=await api((actor===admin?'/api/admin/oj/jobs/':'/api/oj/submissions/')+id,'GET',undefined,actor);
  if(!['QUEUED','RUNNING'].includes(job.status)){assert.equal(job.status,expected,JSON.stringify({id,status:job.status,message:job.result?.message}));report.jobs.push({id,status:job.status,cases:job.totalCases});save();return job;}await delay(350);
 }throw Error('Timeout '+id);
}
function payload(p,language='cpp',mode='STDIO',code){return {problemId:p.candidateId,language,mode,code:code??p.pack.references[language][mode],requestKey:crypto.randomUUID()};}
async function setSubmit(set,item,actor,language='cpp',code,kind='submit'){
 const p=packs.find(p=>p.candidateId===item.problemId),r=payload(p,language,item.modes[0],code);
 return {job:await api(`/api/oj/sets/${set.id}/items/${item.id}/${kind}`,'POST',r,actor),request:r};
}
async function rank(set,actor){return api('/api/oj/sets/'+set.id+'/ranking','GET',undefined,actor);}
const cell=(ranking,actor)=>ranking.records.find(r=>Number(r.userId)===actor.id);
const itemBody=p=>({problemId:p.candidateId,modes:['STDIO']});
(async()=>{
 let users={},practice;
 if(resume){users=JSON.parse(fs.readFileSync(root+'/candidate-users.json'));practice=(await api('/api/admin/oj/sets')).records.find(s=>s.name==='隔离验收入门20题');assert(practice);}
 else {
 const cfg=await api('/api/setting/registration-protection');
 for(const k of ['captcha','quotas','requests'])cfg.config[k].enabled=false;
 await api('/api/setting/registration-protection','PUT',{revision:cfg.revision,config:cfg.config});
 for(const [i,key] of ['A','B'].entries()){
  const studentId='ojfix'+key.toLowerCase()+Date.now().toString().slice(-8),password=crypto.randomBytes(18).toString('base64url');
  await api('/api/auth/register','POST',{studentId,password,name:'隔离验收'+key,nickname:'验证'+key,qq:String(77340000+i),gender:0},null);
  const auth=await api('/api/auth/login','POST',{studentId,password},null);users[key]={id:Number(auth.user.id),studentId,token:auth.token};
 }
 fs.writeFileSync(root+'/candidate-users.json',JSON.stringify(users),{mode:0o600});
 }
 const first=packs.find(p=>p.slug==='original-acm-001');
 await api('/api/oj/submit','POST',payload(first),null,401);
 await api('/api/oj/submit','POST',{},users.A,400);
 await api('/api/admin/oj/health','GET',undefined,users.A,403);
 const runtime=await api('/api/oj/runtime','GET',undefined,users.A);assert.equal(runtime.availableSlots,runtime.freeSlots);assert(runtime.availableSlots>0);
 assert.equal((await api('/api/admin/oj/health')).dailySubmissionLimit,0);
 check('Authentication, DTO/role denials, canonical availableSlots and unlimited practice default');
 const monitor=await api('/api/admin/oj/monitor');assert(Number(monitor.total)>=75);
 const original=packs.filter(p=>p.slug.startsWith('original-acm-')).sort((a,b)=>a.slug.localeCompare(b.slug));assert.equal(original.length,20);
 if(!practice){practice=await api('/api/admin/oj/sets','POST',{name:'隔离验收入门20题',mode:'PRACTICE',items:original.map(itemBody)});await api('/api/admin/oj/sets/'+practice.id+'/status','PUT',{status:'PUBLISHED'});}
 for(const actor of Object.values(users))await api('/api/oj/sets/'+practice.id+'/join','POST',undefined,actor);
 const pd=await api('/api/oj/sets/'+practice.id,'GET',undefined,users.A);assert.equal(pd.items.length,20);
 let firstSetJob;
 if(resume){const old=(await api('/api/oj/sets/'+practice.id+'/submissions','GET',undefined,users.A)).records.find(j=>j.itemId===pd.items[0].id);assert(old);const source=await api(`/api/oj/sets/${practice.id}/submissions/${old.id}/code`,'GET',undefined,users.A);firstSetJob={job:old,request:{code:source.code}};}
 else for(const item of pd.items){const submitted=await setSubmit(practice,item,users.A);const job=await finish(submitted.job.id,users.A);assert.equal(job.code,submitted.request.code);if(!firstSetJob)firstSetJob={...submitted,finished:job};}
 const pr=await rank(practice,users.A);assert.equal(Number(cell(pr,users.A).acCount),20);assert.equal(Number(cell(pr,users.A).penalty),0);assert(cell(pr,users.A).completedAt);
 assert.equal(Number(cell(pr,users.B).acCount),0);assert.equal((await rank(practice,users.A)).total,pr.total);
 assert.equal((await api(`/api/oj/sets/${practice.id}/submissions/${firstSetJob.job.id}/code`,'GET',undefined,users.B)).code,firstSetJob.request.code);
 await api('/api/oj/submissions/'+firstSetJob.job.id,'GET',undefined,users.B,404);
 assert((await api('/api/oj/sets/'+practice.id,'GET',undefined,users.A)).items.every(i=>i.progress.state==='AC'));
 assert((await api('/api/oj/sets/'+practice.id,'GET',undefined,users.B)).items.every(i=>i.progress.state==='NONE'));
 const ordinaryPath='/api/oj/problems?keyword='+encodeURIComponent(first.pack.title);
 const ordinary=await api(ordinaryPath,'GET',undefined,users.A);if(!resumeLanguage)assert.equal(ordinary.records.find(p=>p.id===first.candidateId).progress.state,'NONE');
 check('Real frozen 20-question practice, 20/20 completion, source persistence/sharing, cached rankings and independent per-account/set progress');
 const langJobs=[];
 for(const language of ['c','cpp','java','python','rust']){
  const request=payload(first,language),queued=resumeLanguage?(await api('/api/oj/submissions?problemId='+first.candidateId,'GET',undefined,users.A)).find(j=>j.language===language&&j.status==='AC'&&j.kind==='SUBMIT'):await api('/api/oj/submit','POST',request,users.A),job=await finish(queued.id,users.A);
  assert.equal(job.code,request.code);const perf=await api('/api/oj/submissions/'+job.id+'/performance','GET',undefined,users.A);assert.equal(perf.comparable,true);assert(perf.sampleCount>=1);
  assert((await api('/api/oj/problems/'+first.candidateId+'/ranking?language='+language+'&mode=STDIO','GET',undefined,users.A)).records.length>=1);langJobs.push(job.id);
 }
 await api('/api/oj/leaderboard','GET',undefined,users.A);
 const wrong=resumeLanguage?(await api('/api/oj/submissions?problemId='+first.candidateId,'GET',undefined,users.A)).find(j=>j.status==='WA'):await api('/api/oj/submit','POST',payload(first,'cpp','STDIO','int main(){return 0;}'),users.A);await finish(wrong.id,users.A,'WA');
 assert.equal((await api(ordinaryPath,'GET',undefined,users.A)).records.find(p=>p.id===first.candidateId).progress.state,'AC');
 const heapCode='public class Main { public static void main(String[] args) { byte[] data = new byte[100 * 1024 * 1024]; for (int i = 0; i < data.length; i += 4096) data[i] = 1; System.out.println(data.length + data[0]); } }';
 const heap=await api('/api/oj/run','POST',{...payload(first,'java','STDIO',heapCode),input:''},users.A);const heapResult=await finish(heap.id,users.A);
 assert(Number(heapResult.result.memoryBytes)>100*1024*1024);check('Five language formal submissions, metrics/ranking SQL, permanent accepted badge after WA and actual Java allocation beyond old 96 MiB heap');
 const contestPacks=packs.filter(p=>p.slug.startsWith('contest-original-'));assert.equal(contestPacks.length,5);
 const draft=await api('/api/admin/oj/sets','POST',{name:'隔离比赛草稿',mode:'CONTEST',items:contestPacks.map(itemBody)});
 await api('/api/oj/sets/'+draft.id,'GET',undefined,users.A,404);
 const startsAt=Date.now()+6000,endsAt=startsAt+45000;
 const contest=await api('/api/admin/oj/sets','POST',{name:'隔离验收比赛',mode:'CONTEST',startsAt,endsAt,items:contestPacks.map(itemBody)});
 await api('/api/admin/oj/sets/'+contest.id+'/status','PUT',{status:'PUBLISHED'});
 for(const actor of Object.values(users))await api('/api/oj/sets/'+contest.id+'/join','POST',undefined,actor);
 assert.equal((await api('/api/oj/sets/'+contest.id,'GET',undefined,users.A)).items.length,0);
 while(Date.now()<startsAt+150)await delay(300);
 const cd=await api('/api/oj/sets/'+contest.id,'GET',undefined,users.A),ci=cd.items[0];
 const wa=await setSubmit(contest,ci,users.A,'cpp','int main(){return 0;}');await finish(wa.job.id,users.A,'WA');
 const ce=await setSubmit(contest,ci,users.A,'cpp','compile error');await finish(ce.job.id,users.A,'CE');
 const accepted=await setSubmit(contest,ci,users.A);await finish(accepted.job.id,users.A);
 const score=cell(await rank(contest,users.A),users.A);assert.equal(Number(score.acCount),1);assert.equal(Number(score.penalty),20+Math.floor((accepted.job.acceptedAt-startsAt)/60000));assert.equal(Number(score.cells[ci.id].wrong),1);
 await api(`/api/oj/sets/${contest.id}/submissions/${accepted.job.id}/code`,'GET',undefined,users.B,403);
 await api('/api/admin/oj/sets/'+contest.id+'/public-code','PUT',{publicCode:true},admin,400);
 const body=(await api('/api/admin/oj/sets/'+contest.id));assert.equal(body.canExtend,true);
 while(Date.now()<endsAt+150)await delay(Math.min(500,endsAt+150-Date.now()));
 await api(`/api/oj/sets/${contest.id}/items/${ci.id}/submit`,'POST',payload(contestPacks.find(p=>p.candidateId===ci.problemId)),users.A,400);
 await api('/api/admin/oj/sets/'+contest.id+'/public-code','PUT',{publicCode:true});
 assert.equal((await api(`/api/oj/sets/${contest.id}/submissions/${accepted.job.id}/code`,'GET',undefined,users.B)).code,accepted.request.code);
 assert.equal((await api('/api/admin/oj/sets/'+contest.id)).canExtend,false);
 await api('/api/admin/oj/sets/'+contest.id+'/extend','POST',{endsAt:Date.now()+60000,reason:'拒绝已公开比赛复活'},admin,400);
 await api('/api/admin/oj/sets/'+contest.id+'/public-code','PUT',{publicCode:false});
 await api('/api/admin/oj/sets/'+contest.id+'/extend','POST',{endsAt:Date.now()+60000,reason:'关闭公开仍不复活'},admin,400);
 await api(`/api/admin/oj/sets/${contest.id}/submissions/${accepted.job.id}/rejudge`,'POST',{reason:'共享快照重判验收'});await finish(accepted.job.id,users.A);assert.equal(Number(cell(await rank(contest,users.A),users.A).penalty),20);
 check('Contest secrecy/cutoff, ACM WA penalty and CE exemption, shared-snapshot rejudge, peer source visibility and ended-contest extension blocked with public code both on/off');
 const blockingCode='import time\ntime.sleep(20)\n';
 const block1=await api('/api/oj/run','POST',payload(first,'python','STDIO',blockingCode),users.B);
 const block2=await api('/api/oj/run','POST',payload(first,'python','STDIO',blockingCode),users.B);
 await api('/api/oj/run','POST',payload(first,'python'),users.B,429);
 await finish(block1.id,users.B,'TLE');await finish(block2.id,users.B,'TLE');
 const logs=(await api('/api/log/explore?endpoint='+encodeURIComponent('/api/oj/submit')+'&size=100')).records;
 for(const code of [400,401])assert(logs.some(r=>Number(r.result_code)===code));
 const limited=(await api('/api/log/explore?endpoint='+encodeURIComponent('/api/oj/run')+'&size=100')).records;assert(limited.some(r=>Number(r.result_code)===429));
 const denied=(await api('/api/log/explore?endpoint='+encodeURIComponent('/api/admin/oj/health')+'&size=100')).records;assert(denied.some(r=>Number(r.result_code)===403));
 assert(!JSON.stringify(logs).includes(heapCode));
 await api('/api/user/'+users.B.id,'PUT',{status:0});
 await api('/api/oj/run','POST',payload(first),users.B,401);await api('/api/oj/submit','POST',payload(first),users.B,401);
 check('Real 400/401/403/429 recorded by log management without source bodies; disabling an account rejects both run/submit with its old session');
 const final=await api('/api/admin/oj/health');assert.equal(Number(final.queued),0);
 fs.writeFileSync(root+'/fixes-ui-fixtures.json',JSON.stringify({first: first.candidateId,practice:practice.id,contest:contest.id,draft:draft.id,oldJob:wrong.id,newJob:langJobs[1],firstSetJob:firstSetJob.job.id}),{mode:0o600});
 report.passed=true;report.finishedAt=new Date().toISOString();save();console.log(JSON.stringify({passed:true,jobs:report.jobs.length,checks:report.checks.length}));
})().catch(e=>{report.error=e.message;save();console.error(e.stack);process.exitCode=1});
