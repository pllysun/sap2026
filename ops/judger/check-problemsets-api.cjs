// Real API, real judge, isolated candidate users and data. Never run this fixture
// suite against production: it deliberately exercises invalid/late submissions.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root='/Users/pllysun/Library/Caches/sap-problemsets-1511';
const admin=JSON.parse(fs.readFileSync(root+'/candidate-session.json'));
assert(admin.base==='http://127.0.0.1:18111');
const users=JSON.parse(fs.readFileSync(root+'/candidate-users.json'));
const packs=JSON.parse(fs.readFileSync(root+'/candidate-packs.json'));
const seed=JSON.parse(fs.readFileSync(path.join(__dirname,'sets-seed-candidate.json')));
const practice=seed.sets.find(s=>s.mode==='PRACTICE'),draft=seed.sets.find(s=>s.mode==='CONTEST');
const resume=process.argv.includes('--resume-contest')||process.argv.includes('--resume-restore');
const report=resume?JSON.parse(fs.readFileSync(path.join(__dirname,'sets-api-candidate.json'))):{environment:admin.base,startedAt:new Date().toISOString(),checks:[],jobs:[],passed:false};
delete report.error;if(resume)report.resumedAt=new Date().toISOString();
const save=()=>fs.writeFileSync(path.join(__dirname,'sets-api-candidate.json'),JSON.stringify(report,null,2)+'\n');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function api(route,method='GET',body,actor=admin,expected=200){const res=await(await fetch(admin.base+route,{method,headers:{'Content-Type':'application/json',...(actor?{'sap-token':actor.token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)})).json();assert.equal(res.code,expected,route+': '+res.message);return res.data;}
const check=m=>{if(!report.checks.includes(m))report.checks.push(m);save();console.log(m);};
const payload=(p,language='cpp',kind='SUBMIT',code)=>({problemId:p.problemId,language,mode:p.modes[0],code:code??packs.find(q=>String(q.id)===String(p.problemId)).pack.references[language][p.modes[0]],requestKey:crypto.randomUUID()});
async function submit(set,item,actor=users.A,language='cpp',kind='SUBMIT',code){const r=payload(item,language,kind,code);const job=await api(`/api/oj/sets/${set}/items/${item.id}/${kind==='RUN'?'run':'submit'}`,'POST',r,actor);return {job,request:r};}
async function finish(id,actor=users.A,expected='AC'){for(let i=0;i<400;i++){const j=await api((actor===admin?'/api/admin/oj/jobs/':'/api/oj/submissions/')+id,'GET',undefined,actor);if(!['RUNNING','QUEUED'].includes(j.status)){assert.equal(j.status,expected,JSON.stringify({id,status:j.status,result:j.result}));report.jobs.push({id:j.id,status:j.status,acceptedAt:j.acceptedAt,passedCases:j.passedCases,totalCases:j.totalCases,node:j.nodeName});save();return j;}await delay(250);}throw Error('Job timeout '+id);}
async function ranking(set,actor=users.A){return api('/api/oj/sets/'+set+'/ranking','GET',undefined,actor);}
const find=(rank,actor)=>rank.records.find(r=>String(r.userId)===String(actor.id));
const adminBody=d=>({name:d.name,description:d.description,mode:d.mode,revision:d.revision,accessType:d.accessType,students:d.students,languages:d.languages,startsAt:d.startsAt,endsAt:d.endsAt,items:d.items.filter(i=>i.active).map(i=>({problemId:i.problemId,modes:i.modes}))});
(async()=>{
 if(process.argv.includes('--resume-restore')){
  const p=packs.find(p=>p.slug==='original-acm-020');const d=await api('/api/admin/oj/problems/'+p.id);await finish(d.validationJobId,admin);await api('/api/admin/oj/problems/'+p.id+'/status','PUT',{status:'PUBLISHED'});
  check('Published snapshot stays usable after global question edit; old set completion remains tied to original version; original global pack restored and genuinely revalidated');
  const list=await api('/api/admin/oj/sets');report.liveContestId=list.records.find(s=>s.name==='候选环境比赛规则验收').id;report.practiceId=practice.id;report.passed=true;report.finishedAt=new Date().toISOString();save();return;
 }
 await api('/api/oj/sets','GET',undefined,null,401);await api('/api/admin/oj/sets','GET',undefined,users.A,403);
 await api('/api/oj/sets/'+draft.id,'GET',undefined,users.A,404);await api('/api/admin/oj/sets/'+draft.id+'/status','PUT',{status:'PUBLISHED'},admin,400);
 const catalog=await api('/api/oj/problems?size=50','GET',undefined,users.A);assert.equal(Number(catalog.total),70);assert(!catalog.records.some(p=>p.slug.startsWith('contest-original-')));
 const privateProblem=packs.find(p=>p.slug==='contest-original-001');await api('/api/oj/problems/'+privateProblem.id,'GET',undefined,users.A,404);
 await api('/api/oj/submit','POST',{problemId:privateProblem.id,language:'cpp',mode:'STDIO',code:privateProblem.pack.references.cpp.STDIO},users.A,404);
 check('Login/role enforcement; contest draft and private question hidden in catalog, direct detail, and ordinary submission');
 for(const actor of [users.A,users.B])await api('/api/oj/sets/'+practice.id+'/join','POST',undefined,actor);
 const pdetail=await api('/api/oj/sets/'+practice.id,'GET',undefined,users.A);assert.equal(pdetail.items.length,20);
 if(!resume){
 const pfirst=pdetail.items[0];const publicDetail=await api(`/api/oj/sets/${practice.id}/items/${pfirst.id}`,'GET',undefined,users.A);
 const serialized=JSON.stringify(publicDetail);for(const secret of ['references','functionDriver','snapshotJson','validationSignature'])assert(!serialized.includes('"'+secret+'"'));assert.equal(publicDetail.cases,undefined);assert.equal(publicDetail.samples.length,3);
 const run=await submit(practice.id,pfirst,users.A,'cpp','RUN');await finish(run.job.id);assert.equal(Number(find(await ranking(practice.id),users.A).acCount),0);
 await api(`/api/oj/sets/${practice.id}/submissions/${run.job.id}/code`,'GET',undefined,users.B,403);
 const first=await submit(practice.id,pfirst);await finish(first.job.id);assert.equal(Number(find(await ranking(practice.id),users.A).acCount),1);
 assert.equal((await api(`/api/oj/sets/${practice.id}/submissions/${first.job.id}/code`,'GET',undefined,users.B)).code,first.request.code);
 await api('/api/oj/submissions/'+first.job.id,'GET',undefined,users.B,404);
 const retry=await api(`/api/oj/sets/${practice.id}/items/${pfirst.id}/submit`,'POST',first.request,users.A);assert.equal(retry.id,first.job.id);
 await api(`/api/oj/sets/${practice.id}/items/${pfirst.id}/submit`,'POST',{...first.request,code:'different'},users.A,409);
 const duplicate=await submit(practice.id,pfirst,users.A,'rust');await finish(duplicate.job.id);assert.equal(Number(find(await ranking(practice.id),users.A).acCount),1);
 const perf=await api('/api/oj/submissions/'+duplicate.job.id+'/performance','GET',undefined,users.A);assert.equal(perf.comparable,false);assert.equal(perf.sampleCount,0);
 const own=await api(`/api/oj/sets/${practice.id}/submissions`,'GET',undefined,users.B);assert.equal(own.records.length,0);
 const all=await api(`/api/oj/sets/${practice.id}/submissions?mine=false`,'GET',undefined,users.B);assert(all.records.some(j=>j.id===first.job.id&&j.userId===users.A.id&&j.studentId===users.A.studentId&&j.canViewCode));assert(!JSON.stringify(all).includes('"code":'));
 const ordinary=await api('/api/oj/submissions?problemId='+pfirst.problemId,'GET',undefined,users.A);assert(!ordinary.some(j=>j.id===first.job.id));
 check('Practice sample runs excluded; formal source sharing bounded to participants; own/other histories, code persistence, retry idempotency and no duplicate AC');
 for(const item of pdetail.items.slice(1)){const j=await submit(practice.id,item);await finish(j.job.id);}
 const complete=find(await ranking(practice.id),users.A);assert.equal(Number(complete.acCount),20);assert(complete.completedAt);assert.equal(Number(complete.penalty),0);
 assert.equal((await api('/api/oj/sets/'+practice.id+'/ranking?completed=true','GET',undefined,users.A)).records.length,1);
 assert.equal(Number(complete.easyAc)+Number(complete.mediumAc)+Number(complete.hardAc),20);
 check('All 20 practice questions accepted through the actual set workflow; completion list, 20/20 and difficulty counts correct');
 }else assert.equal(Number(find(await ranking(practice.id),users.A).acCount),20);
 const copied=await api('/api/admin/oj/sets/'+draft.id+'/copy','POST');assert.equal(copied.status,'DRAFT');assert.equal(copied.publicCode,false);
 const start=Date.now()+8000,end=start+45000;let body=adminBody(copied);body.name='候选环境比赛规则验收';body.startsAt=start;body.endsAt=end;
 let contest=await api('/api/admin/oj/sets/'+copied.id,'PUT',body);report.liveContestId=contest.id;save();await api('/api/admin/oj/sets/'+contest.id+'/status','PUT',{status:'PUBLISHED'});
 await api('/api/oj/sets/'+contest.id+'/join','POST',undefined,users.A);await api('/api/oj/sets/'+contest.id+'/join','POST',undefined,users.B);
 assert.equal((await api('/api/oj/sets/'+contest.id,'GET',undefined,users.A)).items.length,0);
 await api(`/api/oj/sets/${contest.id}/items/${contest.items[0].id}`,'GET',undefined,users.A,403);
 while(Date.now()<start+200)await delay(Math.min(500,start+200-Date.now()));
 contest=await api('/api/admin/oj/sets/'+contest.id);assert.equal(contest.phase,'RUNNING');
 await api('/api/oj/sets/'+contest.id+'/join','POST',undefined,users.C,400);
 const items=contest.items.filter(i=>i.active);body=adminBody(contest);body.items.reverse();await api('/api/admin/oj/sets/'+contest.id,'PUT',body,admin,400);
 await api('/api/admin/oj/sets/'+contest.id+'/public-code','PUT',{publicCode:true},admin,400);
 const wa=await submit(contest.id,items[0],users.A,'cpp','SUBMIT','#include <iostream>\nint main(){std::cout << "wrong";}');await finish(wa.job.id,users.A,'WA');
 const ce=await submit(contest.id,items[0],users.A,'cpp','SUBMIT','compile error');await finish(ce.job.id,users.A,'CE');
 const ac=await submit(contest.id,items[0]);await finish(ac.job.id);const acAgain=await submit(contest.id,items[0],users.A,'java');await finish(acAgain.job.id);
 const sample=await submit(contest.id,items[1],users.A,'cpp','RUN');await finish(sample.job.id);
 const unsolved=await submit(contest.id,items[1],users.A,'cpp','SUBMIT','int main(){return 0;}');await finish(unsolved.job.id,users.A,'WA');
 const score=find(await ranking(contest.id),users.A);assert.equal(Number(score.acCount),1);assert.equal(Number(score.penalty),20+Math.floor((Number(ac.job.acceptedAt)-start)/60000));assert.equal(Number(score.cells[items[0].id].wrong),1);
 await api(`/api/oj/sets/${contest.id}/submissions/${ac.job.id}/code`,'GET',undefined,users.B,403);
 check('Registration and pre-start secrecy; post-start config lock; ACM WA/CE/RUN/unsolved/repeated-AC scoring; in-contest peer source denied');
 // Make the node busy just before the deadline; B must be durably accepted into
 // the bounded queue, judged after end, and replayable despite the expired clock.
 while(Date.now()<end-2000)await delay(Math.min(500,end-2000-Date.now()));
 const blocker=await submit(contest.id,items[1],users.A,'python','SUBMIT','import time\ntime.sleep(5)\nprint("wrong")\n');
 for(let i=0;i<8;i++){const j=await api('/api/oj/submissions/'+blocker.job.id,'GET',undefined,users.A);if(j.status==='RUNNING')break;await delay(50);}
 const queued=await submit(contest.id,items[0],users.B);assert.equal(queued.job.status,'QUEUED');assert(Number(queued.job.acceptedAt)<end);
 while(Date.now()<end+100)await delay(200);
 await api(`/api/oj/sets/${contest.id}/items/${items[0].id}/submit`,'POST',{...queued.request,requestKey:crypto.randomUUID()},users.B,400);
 const replay=await api(`/api/oj/sets/${contest.id}/items/${items[0].id}/submit`,'POST',queued.request,users.B);assert.equal(replay.id,queued.job.id);
 await finish(blocker.job.id,users.A,'WA');const lateResult=await finish(queued.job.id,users.B);assert(Date.now()>end);assert.equal(Number(find(await ranking(contest.id,users.B),users.B).acCount),1);
 await api(`/api/oj/sets/${contest.id}/submissions/${ac.job.id}/code`,'GET',undefined,users.B,403);await api('/api/admin/oj/sets/'+contest.id+'/public-code','PUT',{publicCode:true});
 assert.equal((await api(`/api/oj/sets/${contest.id}/submissions/${ac.job.id}/code`,'GET',undefined,users.B)).code,ac.request.code);
 await api('/api/admin/oj/sets/'+contest.id+'/public-code','PUT',{publicCode:false});
 check('Online-but-busy queue accepted; cutoff rejects new requests; earlier queued AC judged after deadline counts; same retry ID after deadline; admin controls post-contest source');
 const acceptedAt=lateResult.acceptedAt;await api(`/api/admin/oj/sets/${contest.id}/submissions/${queued.job.id}/rejudge`,'POST',{reason:'候选环境重判验收'});const rejudged=await finish(queued.job.id,users.B);assert.equal(rejudged.acceptedAt,acceptedAt);assert.equal(Number(find(await ranking(contest.id,users.B),users.B).acCount),1);
 await api('/api/admin/oj/sets/'+contest.id+'/participants','PUT',{userId:users.B.id,disqualified:true,reason:'候选环境资格验收'});assert.equal(find(await ranking(contest.id),users.B).rank,null);
 await api('/api/admin/oj/sets/'+contest.id+'/participants','PUT',{userId:users.B.id,disqualified:false,reason:'恢复测试资格'});
 await api(`/api/admin/oj/sets/${contest.id}/items/${items[1].id}/cancel`,'POST',{reason:'候选环境作废验收'});assert.equal(Number((await ranking(contest.id)).totals.total),4);
 const audit=await api('/api/admin/oj/sets/'+contest.id);for(const action of ['OPEN_CODE','CLOSE_CODE','REJUDGE','DISQUALIFY','RESTORE','CANCEL_ITEM'])assert(audit.audit.some(a=>a.action===action));
 check('Rejudge preserves original reception and unique AC; disqualification/restore, voided-question denominator, persistent audit records');
 // A global edit must not silently change the already-published practice snapshot.
 const last=pdetail.items.at(-1),original=packs.find(p=>String(p.id)===String(last.problemId));const global=await api('/api/admin/oj/problems/'+last.problemId);
 await api('/api/admin/oj/problems/'+last.problemId,'PUT',{revision:global.revision,pack:{...global.pack,description:global.pack.description+'\n候选环境快照修改验证。'}});
 const frozen=await api(`/api/oj/sets/${practice.id}/items/${last.id}`,'GET',undefined,users.A);assert(!frozen.description.includes('候选环境快照修改验证'));
 const stillAc=await submit(practice.id,last);await finish(stillAc.job.id);assert.equal(Number(find(await ranking(practice.id),users.A).acCount),20);
 const modified=await api('/api/admin/oj/problems/'+last.problemId);await api('/api/admin/oj/problems/'+last.problemId,'PUT',{revision:modified.revision,pack:original.pack});
 for(let n=0;n<60;n++){const h=await api('/api/admin/oj/health');if(Number(h.availableSlots)>0)break;await delay(500);}
 const validation=await api('/api/admin/oj/problems/'+last.problemId+'/validate','POST');await finish(validation.id,admin);await api('/api/admin/oj/problems/'+last.problemId+'/status','PUT',{status:'PUBLISHED'});
 check('Published snapshot stays usable after global question edit; old set completion remains tied to original version; original global pack restored and genuinely revalidated');
 report.liveContestId=contest.id;report.practiceId=practice.id;report.passed=true;report.finishedAt=new Date().toISOString();save();console.log(JSON.stringify({passed:true,checks:report.checks.length,jobs:report.jobs.length}));
})().catch(e=>{report.error=e.message;save();console.error(e.stack);process.exitCode=1;});
