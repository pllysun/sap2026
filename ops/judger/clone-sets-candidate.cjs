// Candidate-only fixture restore from verified production packs. This is not a
// substitute for validating new questions: those are validated in production first.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root='/Users/pllysun/Library/Caches/sap-problemsets-1511';
const prod=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const candidate=JSON.parse(fs.readFileSync(root+'/candidate-session.json'));
async function api(s,r,m='GET',b){const res=await(await fetch(s.base+r,{method:m,headers:{'sap-token':s.token,'Content-Type':'application/json'},body:b===undefined?undefined:JSON.stringify(b),signal:AbortSignal.timeout(30000)})).json();assert.equal(res.code,200,r+': '+res.message);return res.data;}
(async()=>{
 const actual=await api(prod,'/api/admin/oj/languages'),local=await api(candidate,'/api/admin/oj/languages');
 const limits=l=>l.map(x=>[x.languageKey,x.enabled,x.timeLimitMs,x.memoryLimitMb]);assert.deepEqual(limits(actual),limits(local));
 const health=await api(prod,'/api/admin/oj/health'),localHealth=await api(candidate,'/api/admin/oj/health');assert.equal(health.runtimeId,localHealth.runtimeId);
 const signature=crypto.createHash('sha256').update(health.runtimeId+actual.filter(l=>l.enabled).map(l=>'|'+l.languageKey+':'+l.timeLimitMs+':'+l.memoryLimitMb).join('')).digest('hex');
 const source=[];for(let page=1;;page++){const d=await api(prod,'/api/admin/oj/problems?size=50&page='+page);source.push(...d.records);if(source.length>=Number(d.total))break;}
 const existing=[];for(let page=1;;page++){const d=await api(candidate,'/api/admin/oj/problems?size=50&page='+page);existing.push(...d.records);if(existing.length>=Number(d.total))break;}
 const restored=[];const sql=[];
 for(const p of source){
  if(p.status!=='PUBLISHED'&&!p.slug.startsWith('contest-original-'))continue;
  const d=await api(prod,'/api/admin/oj/problems/'+p.id);assert(d.validated,'Source not yet validated: '+p.slug);
  let clone=existing.find(x=>x.slug===p.slug);if(!clone)clone=await api(candidate,'/api/admin/oj/problems','POST',d.pack);
  const status=p.slug.startsWith('contest-original-')?'CONTEST_ONLY':'PUBLISHED';
  assert(/^\d+$/.test(String(clone.id)));sql.push(`UPDATE oj_problem SET validation_signature='${signature}',status='${status}' WHERE id=${clone.id};`);
  restored.push({slug:p.slug,id:clone.id,productionProblemId:p.id,productionValidationJobId:d.validationJobId,status,pack:d.pack});
 }
 assert.equal(restored.length,75);
 for(const student of ['OJTEST1511A','OJTEST1511B','OJTEST1511C']){
  sql.push(`INSERT INTO sys_user (student_id,password,name,nickname,qq,gender,grade,status,deleted,created_at,updated_at) SELECT '${student}',password,'题单测试${student.slice(-1)}','验证账号','10001',1,'2026',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM sys_user WHERE student_id='OJCANDIDATE1511' AND NOT EXISTS (SELECT 1 FROM sys_user WHERE student_id='${student}');`);
  sql.push(`INSERT INTO sys_user_role(user_id,role_code) SELECT id,4 FROM sys_user WHERE student_id='${student}' AND NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id=sys_user.id AND role_code=4);`);
 }
 fs.writeFileSync(root+'/candidate-fixtures.sql',sql.join('\n')+'\n',{mode:0o600});
 fs.writeFileSync(root+'/candidate-packs.json',JSON.stringify(restored),{mode:0o600});
 fs.writeFileSync(path.join(__dirname,'sets-candidate-fixtures.json'),JSON.stringify({environment:candidate.base,restored:restored.map(({pack,...p})=>p),source:'Existing verified production state; unchanged runtime and limits',signature},null,2)+'\n');
 console.log(JSON.stringify({restored:restored.length,signature,fixtureSqlReady:true}));
})().catch(e=>{console.error(e.message);process.exitCode=1});
