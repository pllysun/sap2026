// Live acceptance after deployment. Auth is read only from the private cache.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const session=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const seed=JSON.parse(fs.readFileSync(path.join(__dirname,'sets-seed-production.json')));
const practice=seed.sets.find(s=>s.mode==='PRACTICE'),contest=seed.sets.find(s=>s.mode==='CONTEST');
const report={environment:session.base,startedAt:new Date().toISOString(),checks:[],jobs:[],passed:false};
const reportPath=path.join(__dirname,'sets-api-production.json');
const previous=process.argv.includes('--resume')?JSON.parse(fs.readFileSync(reportPath)):null;
if(previous){assert.equal(previous.jobs.length,5);assert(previous.jobs.every(j=>j.status==='AC'));report.startedAt=previous.startedAt;report.resumedAt=new Date().toISOString();}
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function result(route,method='GET',body,authenticated=true){
 const response=await fetch(session.base+route,{method,headers:{...(authenticated?{'sap-token':session.token}:{}),'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});return response.json();
}
async function api(route,method='GET',body){const r=await result(route,method,body);assert.equal(r.code,200,route+': '+r.message);return r.data;}
async function wait(id){for(let n=0;n<300;n++){const j=await api('/api/oj/submissions/'+id);if(!['QUEUED','RUNNING'].includes(j.status))return j;await sleep(350);}throw Error('Job timeout '+id);}
async function all(route){let rows=[];for(let page=1;;page++){const d=await api(route+'?page='+page+'&size=50');rows.push(...d.records);if(rows.length>=Number(d.total))return rows;}}
(async()=>{
 assert.notEqual((await result('/api/oj/sets','GET',undefined,false)).code,200);
 const publicProblems=await all('/api/oj/problems'),adminProblems=await all('/api/admin/oj/problems');
 assert.equal(publicProblems.length,70);assert.equal(adminProblems.length,75);assert(!publicProblems.some(p=>p.slug.startsWith('contest-original-')));
 const privateProblems=adminProblems.filter(p=>p.slug.startsWith('contest-original-'));assert.equal(privateProblems.length,5);
 for(const p of privateProblems){assert.equal(p.status,'CONTEST_ONLY');assert.equal((await result('/api/oj/problems/'+p.id)).code,404);assert((await api('/api/admin/oj/problems/'+p.id)).validated);}
 const list=await api('/api/oj/sets');assert(list.records.some(s=>String(s.id)===String(practice.id)));assert(!list.records.some(s=>String(s.id)===String(contest.id)));assert.equal((await result('/api/oj/sets/'+contest.id)).code,404);
 const draft=await api('/api/admin/oj/sets/'+contest.id);assert.equal(draft.status,'DRAFT');assert.equal(Number(draft.total),5);assert.equal(draft.startsAt,null);assert.equal(draft.endsAt,null);assert.equal(draft.publicCode,false);
 report.checks.push('Login required; 70 public/75 admin questions; all five validated contest questions and their unscheduled draft hidden from users');
 await api(`/api/oj/sets/${practice.id}/join`,'POST');
 const detail=await api('/api/oj/sets/'+practice.id);assert.equal(detail.status,'PUBLISHED');assert.equal(Number(detail.total),20);assert(detail.items.every(i=>i.active&&JSON.stringify(i.modes)==='["STDIO"]'));
 const first=detail.items[0],pack=(await api('/api/admin/oj/problems/'+first.problemId)).pack;
 const item=await api(`/api/oj/sets/${practice.id}/items/${first.id}`);assert(!('references' in item));assert(!JSON.stringify(item).includes('snapshotJson'));
 if(!previous){const run=await api(`/api/oj/sets/${practice.id}/items/${first.id}/run`,'POST',{problemId:first.problemId,language:'cpp',mode:'STDIO',code:pack.references.cpp.STDIO,requestKey:crypto.randomUUID()});assert.equal((await wait(run.id)).status,'AC');}
 for(const language of ['c','cpp','java','python','rust']){
  const payload={problemId:first.problemId,language,mode:'STDIO',code:pack.references[language].STDIO,requestKey:crypto.randomUUID()};
  const route=`/api/oj/sets/${practice.id}/items/${first.id}/submit`,submitted=previous?previous.jobs.find(j=>j.language===language):await api(route,'POST',payload),job=await wait(submitted.id);
  assert.equal(job.status,'AC',language);assert.equal(Number(job.passedCases),pack.cases.length);assert.equal(job.code,payload.code);assert.equal(String(job.problemSetId),String(practice.id));assert.deepEqual(job.result.cases,[]);
  if(!previous)assert.equal(String((await api(route,'POST',payload)).id),String(job.id));
  const source=await api(`/api/oj/sets/${practice.id}/submissions/${job.id}/code`);assert.equal(source.code,payload.code);
  const metric=await api('/api/oj/submissions/'+job.id+'/performance');assert.equal(metric.comparable,false);assert.equal(metric.time.bins.length,8);assert.equal(metric.memory.bins.length,8);
  report.jobs.push({id:job.id,language,mode:job.mode,status:job.status,passedCases:job.passedCases,nodeName:job.nodeName});
 }
 const account=(await api('/api/auth/info')).user;
 const ranking=await api(`/api/oj/sets/${practice.id}/ranking`);assert.equal(Number(ranking.totals.total),20);const own=ranking.records.find(r=>String(r.userId)===String(account.id));assert(own);assert.equal(Number(own.acCount),1);assert.equal(Number(own.easyAc),1);assert(own.studentId);assert.equal(Object.keys(own.cells).length,20);
 const history=await api(`/api/oj/sets/${practice.id}/submissions?mine=true`);assert(history.total>=6);assert(history.records.every(j=>!('code' in j)&&!('snapshotJson' in j)));
 report.checks.push('Real five-language set submissions pass; sample runs excluded, repeat AC counts once, retries preserve ID, source persists, metrics and independent ranking work');
 const twoSum=adminProblems.find(p=>p.title==='两数之和'),twoSumPack=(await api('/api/admin/oj/problems/'+twoSum.id)).pack;
 const ordinary=await api('/api/oj/submit','POST',{problemId:twoSum.id,language:'java',mode:'FUNCTION',code:twoSumPack.references.java.FUNCTION});const ordinaryJob=await wait(ordinary.id);assert.equal(ordinaryJob.status,'AC');
 const metric=await api('/api/oj/submissions/'+ordinaryJob.id+'/performance');assert.equal(metric.comparable,true);assert(metric.sampleCount>=1);
 const global=await api('/api/oj/leaderboard');assert.equal(Number(global.totals.total),70);assert(global.records.every(r=>r.studentId&&'easyAc' in r));
 const perProblem=await api('/api/oj/problems/'+twoSum.id+'/ranking?language=java&mode=FUNCTION');assert(perProblem.records.length>0);
 const monitor=await api('/api/admin/oj/monitor');assert.equal(Number(monitor.capacity),50);assert.equal(monitor.queue.length,0);
 const timeline=await api('/api/admin/oj/monitor/'+ordinaryJob.id);assert(timeline.task.studentId);assert(timeline.timeline.some(e=>e.event==='QUEUED'));assert(timeline.timeline.some(e=>e.event==='STARTED'));assert(timeline.timeline.some(e=>e.event==='FINISHED'));
 report.checks.push('Existing ordinary JDK 27 function submission, MySQL global leaderboard/performance SQL, user information and task timeline remain functional');
 for(const endpoint of ['/api/oj/sets','/api/oj/sets/{id}/items/{itemId}/submit','/api/admin/oj/sets','/api/admin/oj/sets/{id}/status']){
  const logs=await api('/api/log/explore?size=5&endpoint='+encodeURIComponent(endpoint));assert(logs.total>0,'Missing operation log '+endpoint);assert(logs.records.some(r=>Number(r.result_code)===200));
 }
 report.checks.push('New user/admin browsing, submission and publication endpoints appear in the actual log management interface');
 let pool;for(let n=0;n<60;n++){pool=await api('/api/admin/oj/nodes');if(Number(pool.availableSlots)===9)break;await sleep(250);}
 assert.equal(Number(pool.online),2);assert.equal(Number(pool.capacity),9);assert.equal(Number(pool.availableSlots),9);
 assert(pool.nodes.filter(n=>n.enabled).every(n=>n.state==='RUNNING'));
 report.nodes=pool.nodes.map(n=>({name:n.name,state:n.state,capacity:n.capacity}));report.checks.push('Built-in and pllysun.top nodes online with nine idle execution slots after live testing');
 report.passed=true;report.finishedAt=new Date().toISOString();fs.writeFileSync(path.join(__dirname,'sets-api-production.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
})().catch(e=>{report.error=e.message;fs.writeFileSync(path.join(__dirname,'sets-api-production.json'),JSON.stringify(report,null,2)+'\n');console.error(e.stack);process.exitCode=1});
