const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root='/Users/pllysun/Library/Caches/sap-problemsets-1511';const admin=JSON.parse(fs.readFileSync(root+'/candidate-session.json')),users=JSON.parse(fs.readFileSync(root+'/candidate-users.json')),packs=JSON.parse(fs.readFileSync(root+'/candidate-packs.json'));
assert.equal(admin.base,'http://127.0.0.1:18111');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function api(r,m='GET',b,u=admin){const x=await(await fetch(admin.base+r,{method:m,headers:{'Content-Type':'application/json','sap-token':u.token},body:b===undefined?undefined:JSON.stringify(b),signal:AbortSignal.timeout(10000)})).json();assert.equal(x.code,200,r+': '+x.message);return x.data;}
(async()=>{
 for(let n=0;n<900;n++){const h=await api('/api/admin/oj/health');if(Number(h.availableSlots)>0)break;if(n===899)throw Error('Candidate node did not become idle');await delay(300);}
 const original=packs.find(p=>p.slug==='original-acm-001');let s=await api('/api/admin/oj/sets','POST',{name:'候选环境排队恢复验收',mode:'PRACTICE',items:[{problemId:original.id,modes:['STDIO']}]});await api('/api/admin/oj/sets/'+s.id+'/status','PUT',{status:'PUBLISHED'});
 const item=s.items[0];for(const u of [users.A,users.B])await api('/api/oj/sets/'+s.id+'/join','POST',undefined,u);
 const request=(language,code)=>({problemId:item.problemId,mode:'STDIO',language,code,requestKey:crypto.randomUUID()});
 const blocker=await api(`/api/oj/sets/${s.id}/items/${item.id}/submit`,'POST',request('python','import time\ntime.sleep(5)\nprint("wrong")'),users.A);
 let running=false;for(let n=0;n<40;n++){const j=await api('/api/oj/submissions/'+blocker.id,'GET',undefined,users.A);if(j.status==='RUNNING'){running=true;break;}await delay(50);}assert(running,'Restart test requires a genuinely running blocker');
 const body=request('cpp',original.pack.references.cpp.STDIO);const queued=await api(`/api/oj/sets/${s.id}/items/${item.id}/submit`,'POST',body,users.B);assert.equal(queued.status,'QUEUED');
 const ssh=['-i','/Users/pllysun/Library/Caches/sap-judger-pllysun/identity','-o','IdentitiesOnly=yes','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UserKnownHostsFile=/Users/pllysun/Library/Caches/sap-judger-pllysun/known_hosts','root@pllysun.top','python3 -'];
 const code="import subprocess\nsubprocess.run(['docker','-H','unix:///run/sap-judger-docker.sock','stop','-t','20','sap-sets-candidate'],check=True,stdout=subprocess.DEVNULL)\nsubprocess.run(['python3','/opt/sap-judger/problemsets-1511/start-sets-candidate.py'],check=True)\n";
 const restarted=cp.spawnSync('ssh',ssh,{input:code,encoding:'utf8',timeout:60000});assert.equal(restarted.status,0,'Candidate restart failed');
 let result;for(let n=0;n<900;n++){try{result=await api('/api/oj/submissions/'+queued.id,'GET',undefined,users.B);if(!['QUEUED','RUNNING'].includes(result.status))break;}catch(e){if(n>180)throw e;}await delay(300);}
 assert.equal(result.status,'AC');assert.equal(result.id,queued.id);assert.equal(result.acceptedAt,queued.acceptedAt);
 const replay=await api(`/api/oj/sets/${s.id}/items/${item.id}/submit`,'POST',body,users.B);assert.equal(replay.id,queued.id);
 const rank=await api('/api/oj/sets/'+s.id+'/ranking','GET',undefined,users.B);assert.equal(Number(rank.records.find(r=>r.userId===users.B.id).acCount),1);
 const interrupted=await api('/api/oj/submissions/'+blocker.id,'GET',undefined,users.A);assert.equal(interrupted.status,'WA');assert.equal(interrupted.acceptedAt,blocker.acceptedAt);
 const report={passed:true,environment:admin.base,problemSetId:s.id,queuedId:queued.id,acceptedAt:queued.acceptedAt,result:result.status,replayId:replay.id,recoveredRunningStatus:interrupted.status,checks:['durable queued submission survives real application/container restart','existing Redis sessions survive restart','same submission ID and server receipt timestamp','retry does not duplicate acceptance or AC','interrupted running task is requeued and judged using its original ID and receipt time']};
 fs.writeFileSync(path.join(__dirname,'sets-queue-restart-candidate.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
})().catch(e=>{console.error(e.stack);process.exitCode=1});
