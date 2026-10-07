const fs=require('node:fs'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const session=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const before=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-progress-1512/before-production.json'));
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
async function api(path,method='GET',body){const response=await fetch(session.base+path,{method,headers:{'sap-token':session.token,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});const value=await response.json();assert.equal(value.code,200,path+': '+value.message);return value.data;}
const descriptor=s=>({id:s.id,name:s.name,mode:s.mode,status:s.status,startsAt:s.startsAt,endsAt:s.endsAt,publicCode:s.publicCode,items:s.items.map(i=>({id:i.id,problemId:i.problemId,label:i.label,title:i.title,active:i.active,revision:i.revision}))});
async function finished(id){for(let count=0;count<60;count++){const job=await api('/api/oj/submissions/'+id);if(!['QUEUED','RUNNING'].includes(job.status))return job;await new Promise(resolve=>setTimeout(resolve,1500));}throw Error('Judge timeout');}
(async()=>{
  const practice=await api('/api/admin/oj/sets/1'),draft=await api('/api/admin/oj/sets/2');assert.deepEqual(descriptor(practice),before.practice);assert.deepEqual(descriptor(draft),before.draft);
  const original=await api('/api/admin/oj/monitor/428/code');assert.equal(hash(original.code),before.source.sha256);
  let rows=[];for(let page=1;page<=4;page++)rows.push(...(await api('/api/oj/problems?page='+page)).records);
  assert.equal(rows.length,70);assert.equal((await api('/api/admin/oj/problems')).total,before.bankTotal);
  assert.equal(rows.find(p=>String(p.id)==='2').progress.state,'AC');
  const set=await api('/api/oj/sets/1');assert.equal(set.items[0].progress.state,'AC');assert.equal(rows.find(p=>String(p.id)===String(set.items[0].problemId)).progress.state,'NONE');
  const code='class Solution{public int[] twoSum(int[] nums,int target){java.util.Map<Integer,Integer> m=new java.util.HashMap<>();for(int i=0;i<nums.length;i++){int j=target-nums[i];if(m.containsKey(j))return new int[]{m.get(j),i};m.put(nums[i],i);}return new int[0];}}';
  const body={problemId:2,language:'java',mode:'FUNCTION',code,requestKey:crypto.randomUUID()};
  const run=await api('/api/oj/run','POST',body),runResult=await finished(run.id);assert.equal(runResult.status,'AC');
  const submit=await api('/api/oj/submit','POST',{...body,requestKey:crypto.randomUUID()}),submitResult=await finished(submit.id);assert.equal(submitResult.status,'AC');
  const saved=await api('/api/admin/oj/monitor/'+submit.id+'/code');assert.equal(saved.code,code);assert.equal(code.split('\n').length,1);
  const timeline=await api('/api/admin/oj/monitor/'+submit.id);assert(timeline.timeline.some(t=>t.event==='QUEUED'));assert(timeline.timeline.some(t=>t.event==='STARTED'));assert(timeline.timeline.some(t=>t.event==='FINISHED'));
  const performance=await api('/api/oj/submissions/'+submit.id+'/performance');assert.equal(performance.comparable,true);
  const stats=await api('/api/oj/leaderboard');assert.equal(Number(stats.totals.total),70);
  const nodes=await api('/api/admin/oj/nodes');assert.equal(nodes.nodes.filter(n=>n.state==='RUNNING').length,2);assert.equal(Number(nodes.capacity),9);
  const anonymous=await(await fetch(session.base+'/api/oj/problems')).json();assert.equal(anonymous.code,401);
  const report={passed:true,image:'pllysun/sap:1.5.12',practiceId:1,contestId:2,metadataPreserved:true,oldSourcePreserved:true,publicProblems:70,adminProblems:Number(before.bankTotal),runtime:'java27',jobs:[{id:run.id,kind:'RUN',status:runResult.status,cases:runResult.totalCases},{id:submit.id,kind:'SUBMIT',status:submitResult.status,cases:submitResult.totalCases}],rawSourceSha256:hash(code),separateScopeExample:{problemId:set.items[0].problemId,library:'NONE',set:'AC'},timelineVerified:true,performanceVerified:true,nodesOnline:2,capacity:9,anonymousRejected:true};
  fs.writeFileSync(__dirname+'/progress-api-production.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
})().catch(e=>{console.error(e.stack);process.exitCode=1});
