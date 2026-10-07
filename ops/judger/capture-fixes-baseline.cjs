// Authorized read-only baseline. Credentials and problem packs stay in a private cache.
const fs=require('node:fs'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root='/Users/pllysun/Library/Caches/sap-oj-fixes-1513',base=process.env.SAP_BASE_URL||'https://csuftsap.top';
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
const descriptor=s=>({id:s.id,name:s.name,mode:s.mode,status:s.status,startsAt:s.startsAt,endsAt:s.endsAt,publicCode:s.publicCode,items:s.items.map(i=>({id:i.id,problemId:i.problemId,label:i.label,title:i.title,active:i.active,revision:i.revision}))});
(async()=>{
 const call=async(path,method='GET',body,token)=>{const v=await(await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{'sap-token':token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)})).json();assert.equal(v.code,200,path+': '+v.message);return v.data;};
 const login=await call('/api/auth/admin/login','POST',{studentId:process.env.SAP_ADMIN_ACCOUNT,password:process.env.SAP_ADMIN_PASSWORD});
 const api=(p,m,b)=>call(p,m,b,login.token);fs.writeFileSync(root+'/production-session.json',JSON.stringify({base,token:login.token}),{mode:0o600});
 let rows=[];for(let page=1;;page++){const data=await api('/api/admin/oj/problems?page='+page+'&size=50');rows.push(...data.records);if(rows.length>=data.total)break;}
 const packs=[];for(const p of rows){const d=await api('/api/admin/oj/problems/'+p.id);packs.push({...p,pack:d.pack,packSha:hash(JSON.stringify(d.pack))});}
 const sets=[];for(const id of [1,2])sets.push(descriptor(await api('/api/admin/oj/sets/'+id)));
 const sources=[];for(const id of [428,430])sources.push({id,sha256:hash((await api('/api/admin/oj/monitor/'+id+'/code')).code)});
 const ranks=await api('/api/admin/oj/sets/1/ranking');
 const before={sets,sources,languages:await api('/api/admin/oj/languages'),publicProblems:(await api('/api/oj/problems')).total,problems:packs.map(({pack,...p})=>p),practiceRank:ranks.records,health:await api('/api/admin/oj/health')};
 fs.writeFileSync(root+'/before-production.json',JSON.stringify(before),{mode:0o600});fs.writeFileSync(root+'/production-packs.json',JSON.stringify(packs),{mode:0o600});
 console.log(JSON.stringify({baseline:true,adminProblems:rows.length,publicProblems:before.publicProblems,setCount:sets.length,queued:before.health.queued}));
})().catch(e=>{console.error(e.message);process.exitCode=1});
