const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const session=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const checks=[];
function remote(script){try{return JSON.parse(execFileSync(process.execPath,[path.resolve(__dirname,'../server-ssh.cjs'),'python3 -'],{input:script,encoding:'utf8',stdio:['pipe','pipe','pipe']}))}catch{throw Error('Remote node probe failed; inspect its private server log')}}
async function api(route,method='GET',body,expected=200,headers={}){
 const r=await(await fetch(session.base+route,{method,headers:{'Content-Type':'application/json','sap-token':session.token,...headers},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)})).json();
 assert.equal(r.code,expected,route+': '+r.message);return r.data;
}
async function nodeState(id,state){for(let i=0;i<80;i++){const n=await api(`/api/admin/oj/nodes/${id}/refresh`,'POST');if(n.state===state)return n;await sleep(500)}throw Error('Node state timeout: '+state)}
async function slots(count){for(let i=0;i<60;i++){const p=await api('/api/admin/oj/nodes');if(Number(p.availableSlots)>=count)return p;await sleep(500)}throw Error('Available capacity timeout')}
async function finished(id){for(let i=0;i<160;i++){const r=await api('/api/oj/submissions/'+id);if(!['RUNNING','QUEUED'].includes(r.status)){assert.equal(r.status,'AC','job '+id+' result');return r}await sleep(300)}throw Error('Judge timeout')}
async function running(id){for(let i=0;i<60;i++){const r=await api('/api/oj/submissions/'+id);if(r.status==='RUNNING'&&r.nodeName)return r;assert(['QUEUED','RUNNING'].includes(r.status));await sleep(100)}throw Error('Run start timeout')}
(async()=>{
 let remoteId,builtin,problem;
 try{
 const pool=await api('/api/admin/oj/nodes');builtin=pool.nodes.find(n=>n.builtin);assert(builtin);assert.equal(pool.nodes.length,1,'Only the expected built-in node may exist before this probe');
 await slots(1);assert.equal(typeof builtin.cpuPercent,'number');assert.equal(typeof builtin.memoryMb,'number');
 const rows=(await api('/api/oj/problems')).records;problem=rows.find(r=>r.slug==='luogu-p1001');assert(problem);
 const code='import time\ntime.sleep(1.5)\na, b = map(int, input().split())\nprint(a + b)\n';
 const submit=()=>api('/api/oj/run','POST',{problemId:problem.id,language:'python',mode:'STDIO',code,input:'1 2\n'});
 const one=await submit();await running(one.id);await api('/api/oj/run','POST',{problemId:problem.id,language:'python',mode:'STDIO',code,input:'1 2\n'},503);await finished(one.id);
 const saved=await api(`/api/admin/oj/monitor/${one.id}/code`);assert.equal(saved.code,code);assert.equal((await api('/api/oj/submissions/'+one.id)).code,code);checks.push('submitted source preserved byte-for-byte; full pool returns 503');
 await api(`/api/admin/oj/nodes/${builtin.id}/stop`,'POST');await nodeState(builtin.id,'STOPPED');
 assert.equal((await api('/api/oj/runtime')).available,false);assert.equal((await api('/api/oj/runtime')).freeSlots,0);
 await api('/api/oj/run','POST',{problemId:problem.id,language:'python',mode:'STDIO',code,input:'1 2\n'},503);await api('/api/ping');
 const stopped=remote("import subprocess,json\nlines=subprocess.check_output(['docker','top','sap','-eo','pid,rss,comm'],universal_newlines=True).splitlines()[1:]\nprint(json.dumps({'engineProcesses':sum('go-judge' in l for l in lines)}))\n");assert.equal(stopped.engineProcesses,0);checks.push('built-in engine really stopped; web stays available; no capacity rejected');
 await api(`/api/admin/oj/nodes/${builtin.id}/start`,'POST');await nodeState(builtin.id,'RUNNING');await slots(1);
 const info=remote("import json,subprocess\nfrom pathlib import Path\nn=json.loads(subprocess.check_output(['docker','inspect','sap-oj-158-node-probe'],universal_newlines=True))[0]\nassert n['Config']['Labels']['com.sap.oj.probe']=='1.5.8'\nip=n['NetworkSettings']['Networks']['bridge']['IPAddress']\nprint(json.dumps({'endpoint':'http://'+ip+':5051','token':Path('/tmp/sap-oj-158-node-probe/token').read_text().strip()}))\n");
 const remoteNode=await api('/api/admin/oj/nodes','POST',{name:'部署验收临时节点 1.5.8',endpoint:info.endpoint,token:info.token,maxConcurrency:1});remoteId=remoteNode.id;
 assert(!JSON.stringify(remoteNode).includes(info.token));await api(`/api/admin/oj/nodes/${remoteId}/start`,'POST');const remoteStatus=await nodeState(remoteId,'RUNNING');
 const heartbeat={protocol:1,runtimeId:remoteStatus.runtimeId,state:'RUNNING',capacity:1,active:0,freeSlots:1,cpuCores:1,cpuPercent:10,memoryMb:640,memoryUsedMb:20,hostAvailableMb:500};
 await api(`/api/oj-nodes/${remoteId}/heartbeat`,'POST',heartbeat,401,{Authorization:'Bearer invalid'});
 await api(`/api/oj-nodes/${remoteId}/heartbeat`,'POST',heartbeat,200,{Authorization:'Bearer '+info.token});
 await api(`/api/admin/oj/nodes/${remoteId}/refresh`,'POST');await slots(2);checks.push('external node registration and authenticated resource heartbeat');
 const jobs=[await submit(),await submit()];const started=await Promise.all(jobs.map(j=>running(j.id)));assert.equal(new Set(started.map(j=>j.nodeName)).size,2);await Promise.all(jobs.map(j=>finished(j.id)));checks.push('two real nodes concurrently execute distinct tasks');
 await slots(2);const failedOver=await submit();const first=await running(failedOver.id);assert.equal(first.nodeName,builtin.name);
 await api(`/api/admin/oj/nodes/${builtin.id}/stop`,'POST');const retried=await finished(failedOver.id);assert.equal(Number(retried.attempt),2);assert.equal(retried.nodeName,'部署验收临时节点 1.5.8');
 const timeline=(await api(`/api/admin/oj/monitor/${failedOver.id}`)).timeline;for(const event of ['NODE_ERROR','REASSIGNED','FINISHED'])assert(timeline.some(r=>r.event===event));checks.push('interrupted task retries on second node and finishes AC with complete timeline');
 await nodeState(builtin.id,'STOPPED');await api(`/api/admin/oj/nodes/${builtin.id}/start`,'POST');await nodeState(builtin.id,'RUNNING');
 for(const endpoint of ['/api/oj/run','/api/admin/oj/nodes','/api/admin/oj/monitor/{id}/code','/api/oj-nodes/{id}/heartbeat']){
   const logs=await api('/api/log/explore?dimension=detail&endpoint='+encodeURIComponent(endpoint)+'&size=20');
   assert(logs.records?.length,'operation log missing for '+endpoint);assert(logs.records.some(r=>r.description?.includes('算法题库')));
   assert(!JSON.stringify(logs).includes(info.token));
 }
 checks.push('new and existing OJ endpoints present in central log management');
 }finally{
   if(builtin){await api(`/api/admin/oj/nodes/${builtin.id}/start`,'POST');await nodeState(builtin.id,'RUNNING')}
   if(remoteId){await api(`/api/admin/oj/nodes/${remoteId}/stop`,'POST');await nodeState(remoteId,'STOPPED');for(let i=0;i<40;i++){const p=await api('/api/admin/oj/nodes');if(!Number(p.nodes.find(n=>n.id===remoteId)?.active))break;await sleep(300)}await api(`/api/admin/oj/nodes/${remoteId}`,'DELETE')}
 }
 await slots(1);const report={passed:true,checks,builtInRestored:true,temporaryNodeRemoved:true};fs.writeFileSync(path.join(__dirname,'nodes-api-production.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
})().catch(e=>{console.error(e.message);process.exitCode=1});
