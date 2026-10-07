const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const candidate=process.argv.includes('--candidate');
const session=JSON.parse(fs.readFileSync(candidate?'/Users/pllysun/Library/Caches/sap-problemsets-1511/candidate-session.json':'/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const report={environment:session.base,startedAt:new Date().toISOString(),sets:[],passed:false};
async function api(r,m='GET',b){const res=await(await fetch(session.base+r,{method:m,headers:{'sap-token':session.token,'Content-Type':'application/json'},body:b===undefined?undefined:JSON.stringify(b),signal:AbortSignal.timeout(30000)})).json();assert.equal(res.code,200,r+': '+res.message);return res.data;}
(async()=>{
 let problems=[];for(let page=1;;page++){const d=await api('/api/admin/oj/problems?size=50&page='+page);problems.push(...d.records);if(problems.length>=Number(d.total))break;}
 let sets=[];for(let page=1;;page++){const d=await api('/api/admin/oj/sets?page='+page);sets.push(...d.records);if(sets.length>=Number(d.total))break;}
 for(const [mode,name,pattern,count,description] of [
  ['PRACTICE','原创入门 ACM 20 题',/^original-acm-\d{3}$/,20,'完整程序模式，按题单顺序练习。'],
  ['CONTEST','原创算法比赛（5 题）',/^contest-original-\d{3}$/,5,'完整程序模式。比赛时间待配置。']]){
  const selected=problems.filter(p=>pattern.test(p.slug)).sort((a,b)=>a.slug.localeCompare(b.slug));assert.equal(selected.length,count);
  for(const p of selected){assert.equal(p.status,mode==='PRACTICE'?'PUBLISHED':'CONTEST_ONLY');assert((await api('/api/admin/oj/problems/'+p.id)).validated,'Missing validation: '+p.slug);}
  const duplicates=sets.filter(s=>s.name===name);assert(duplicates.length<=1,'Duplicate set name');let s=duplicates[0];
  if(!s)s=await api('/api/admin/oj/sets','POST',{name,description,mode,accessType:'PUBLIC',students:[],languages:['c','cpp','java','python','rust'],items:selected.map(p=>({problemId:p.id,modes:['STDIO']}))});
  let detail=await api('/api/admin/oj/sets/'+s.id);assert.equal(detail.mode,mode);assert.deepEqual(detail.items.filter(i=>i.active).map(i=>String(i.problemId)),selected.map(p=>String(p.id)));assert.equal(Number(detail.total),count);assert(detail.items.every(i=>!i.active||JSON.stringify(i.modes)==='["STDIO"]'));
  if(mode==='PRACTICE'&&detail.status==='DRAFT'){await api('/api/admin/oj/sets/'+s.id+'/status','PUT',{status:'PUBLISHED'});detail=await api('/api/admin/oj/sets/'+s.id);}
  assert.equal(detail.status,mode==='PRACTICE'?'PUBLISHED':'DRAFT');
  if(mode==='CONTEST'){assert.equal(detail.startsAt,null);assert.equal(detail.endsAt,null);assert.equal(detail.publicCode,false);}
  report.sets.push({id:s.id,name,mode,status:detail.status,problemSlugs:selected.map(p=>p.slug),itemIds:detail.items.map(i=>i.id),startsAt:detail.startsAt,endsAt:detail.endsAt});console.log(JSON.stringify(report.sets.at(-1)));
 }
 report.passed=true;report.finishedAt=new Date().toISOString();fs.writeFileSync(path.join(__dirname,candidate?'sets-seed-candidate.json':'sets-seed-production.json'),JSON.stringify(report,null,2)+'\n');
})().catch(e=>{console.error(e.message);process.exitCode=1});
