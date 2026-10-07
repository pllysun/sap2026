// Candidate checks use isolated data; production checks only import the six approved pilots.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict')
const base=process.env.SAP_BASE_URL||'http://127.0.0.1:18080'
const production=process.env.SAP_OJ_PRODUCTION==='true'
const report={environment:production?'production':'candidate',checks:[],validations:[],submissions:[]}
const root=path.resolve(__dirname,'../..'),cache=path.join(require('node:os').homedir(),'Library/Caches/sap-oj-test-session.json')
const wait=ms=>new Promise(r=>setTimeout(r,ms))
let token
async function api(route,method='GET',data,who=token,expected=200){const res=await fetch(base+route,{method,headers:{'Content-Type':'application/json',...(who?{'sap-token':who}:{})},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(30000)});const result=await res.json();assert.equal(result.code,expected,route+': '+result.message);return result.data}
async function job(id,admin=false){for(let n=0;n<1200;n++){const data=await api((admin?'/api/admin/oj/jobs/':'/api/oj/submissions/')+id);if(!['RUNNING','QUEUED'].includes(data.status))return data;await wait(1000)}throw Error('Job timeout '+id)}
const slugs=['luogu-p1001','leetcode-1','leetcode-20','leetcode-704','leetcode-53','leetcode-206']
async function main(){
 token=(await api('/api/auth/admin/login','POST',{studentId:process.env.SAP_ADMIN_ACCOUNT||'oj_admin',password:process.env.SAP_ADMIN_PASSWORD||'JudgerLocal2026!'},null)).token
 fs.writeFileSync(cache,JSON.stringify({token,base}),{mode:0o600})
 const health=await api('/api/admin/oj/health');assert.equal(health.available,true);assert.equal(health.enabled,true)
 const languages=await api('/api/admin/oj/languages');assert.equal(languages.length,5);assert(languages.find(x=>x.languageKey==='java').version.includes('27'))
 report.checks.push('sandbox ready; five toolchains; Java 27')
 const before=await api('/api/admin/oj/problems?size=50');const pilots=[]
 for(const slug of slugs){const pack=JSON.parse(fs.readFileSync(path.join(root,'ops/judger/problem-packs',slug,'pack.json')));let row=before.records.find(x=>x.slug===slug)
 if(!row){row=await api('/api/admin/oj/problems','POST',pack);console.log(JSON.stringify({imported:slug,id:row.id}))}
 pilots.push({pack,id:row.id})
 const detail=await api('/api/admin/oj/problems/'+row.id)
 if(!detail.validated){await api('/api/admin/oj/problems/'+row.id+'/status','PUT',{status:'PUBLISHED'},token,400);const queued=await api('/api/admin/oj/problems/'+row.id+'/validate','POST');const result=await job(queued.id,true);assert.equal(result.status,'AC',slug+': '+JSON.stringify(result.result));report.validations.push({slug,status:result.status,passed:result.passedCases,total:result.totalCases});console.log(JSON.stringify({validated:slug,passed:result.passedCases,total:result.totalCases}))}
 if(detail.validated && detail.validationJobId){const result=await job(detail.validationJobId,true);assert.equal(result.status,'AC');report.validations.push({slug,status:result.status,passed:result.passedCases,total:result.totalCases})}
 await api('/api/admin/oj/problems/'+row.id+'/status','PUT',{status:'PUBLISHED'})
 const view=await api('/api/oj/problems/'+row.id);for(const key of ['references','profiles','cases','pack','packJson'])assert(!(key in view));assert(view.sourcePlatform);assert.equal(view.samples.length,pack.cases.filter(x=>x.sample).length)
 }
 report.checks.push('six pilots imported, fully validated, published; public projection excludes hidden tests/reference/driver')
 for(const {pack,id} of pilots){for(const language of ['c','cpp','java','python','rust']){for(const mode of pack.modes){const queued=await api('/api/oj/submit','POST',{problemId:id,language,mode,code:pack.references[language][mode]});const result=await job(queued.id);assert.equal(result.status,'AC',`${pack.slug}/${language}/${mode}: ${JSON.stringify(result.result)}`);assert(!('snapshotJson'in result));assert.deepEqual(result.result.cases,[]);report.submissions.push({slug:pack.slug,language,mode,status:result.status,passed:result.passedCases});console.log(JSON.stringify({submitted:pack.slug,language,mode,status:result.status}))}}}
 const first=pilots[0],submit=async(code,kind='submit',extra={})=>job((await api('/api/oj/'+kind,'POST',{problemId:first.id,language:'python',mode:'STDIO',code,...extra})).id)
 assert.equal((await submit('print(0)')).status,'WA');assert.equal((await submit('this is invalid python!')).status,'CE');assert.equal((await submit('while True: pass')).status,'TLE');assert.equal((await submit('x=bytearray(300*1024*1024); print(len(x))')).status,'MLE');assert.equal((await submit("print('x'*1000000)")).status,'OLE')
 const custom=await submit('a,b=map(int,input().split());print(a+b)','run',{input:'41 1\n'});assert.equal(custom.status,'AC');assert.equal(custom.result.cases[0].output.trim(),'42')
 report.checks.push('AC, WA, CE, TLE, MLE, OLE; custom input output; cache cleanup')
 await api('/api/oj/submit','POST',{problemId:first.id,language:'python',mode:'FUNCTION',code:'print(0)'},token,400)
 await api('/api/oj/submit','POST',{problemId:first.id,language:'ruby',mode:'STDIO',code:'puts 0'},token,400)
 report.checks.push('unsupported language and mode rejected')
 if(!production){
 const cfg=await api('/api/setting/registration-protection');for(const key of ['captcha','quotas','requests'])cfg.config[key].enabled=false
 await api('/api/setting/registration-protection','PUT',{revision:cfg.revision,config:cfg.config})
 const users=[];for(let i=1;i<=2;i++){const studentId='oj_test_'+i;const existing=await fetch(base+'/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({studentId,password:'LocalJudge2026!',name:'OJ测试'+i,qq:'10000000'+i,gender:0})});const result=await existing.json();assert([200,400].includes(result.code));users.push((await api('/api/auth/login','POST',{studentId,password:'LocalJudge2026!'},null)).token)}
 await api('/api/admin/oj/problems','GET',undefined,users[0],403)
 const q=await api('/api/oj/run','POST',{problemId:first.id,language:'python',mode:'STDIO',code:'print(3)'},users[0]);await api('/api/oj/submissions/'+q.id,'GET',undefined,users[1],404);await api('/api/oj/submissions/'+q.id,'GET',undefined,users[0]);
 fs.writeFileSync(cache,JSON.stringify({token,base,userToken:users[0]}),{mode:0o600})
 await api('/api/admin/oj/problems/'+first.id+'/status','PUT',{status:'DISABLED'});await api('/api/oj/problems/'+first.id,'GET',undefined,token,404);await api('/api/admin/oj/problems/'+first.id+'/status','PUT',{status:'PUBLISHED'})
 const clone=structuredClone(first.pack);clone.slug='integration-revision-test';clone.title='集成测试：版本控制';let row=(await api('/api/admin/oj/problems?size=50')).records.find(x=>x.slug===clone.slug);if(!row)row=await api('/api/admin/oj/problems','POST',clone);const original=await api('/api/admin/oj/problems/'+row.id);await api('/api/admin/oj/problems/'+row.id,'PUT',{revision:original.revision,pack:clone});await api('/api/admin/oj/problems/'+row.id,'PUT',{revision:original.revision,pack:clone},token,409);await api('/api/oj/problems/'+row.id,'GET',undefined,token,404)
 const altered=structuredClone(languages);altered[0].timeLimitMs+=100;await api('/api/admin/oj/languages','PUT',altered);await api('/api/oj/problems/'+first.id,'GET',undefined,token,404);await api('/api/admin/oj/languages','PUT',languages);await api('/api/oj/problems/'+first.id)
 report.checks.push('ordinary user admin forbidden; submission ownership; disabled/draft hidden; revision conflict; changed language limit invalidates publication')
 }
 report.passed=true;fs.writeFileSync(path.join(__dirname,production?'application-production-validation.json':'application-candidate-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({passed:true,validations:report.validations.length,submissions:report.submissions.length,checks:report.checks}))
}
main().catch(e=>{report.passed=false;report.error=e.message;fs.writeFileSync(path.join(__dirname,production?'application-production-validation.json':'application-candidate-validation.json'),JSON.stringify(report,null,2)+'\n');console.error(e.message);process.exitCode=1})
