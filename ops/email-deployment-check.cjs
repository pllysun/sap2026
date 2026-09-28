// Online smoke test. Explicit opt-in required; never changes SMTP/template switches.
// Sends exactly two labelled test emails to SAP_TEST_RECIPIENT.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const base='https://csuftsap.top';
const account = process.env.SAP_TEST_ACCOUNT || process.env.SAP_ADMIN_ACCOUNT;
if (!account) throw new Error("Set SAP_TEST_ACCOUNT or SAP_ADMIN_ACCOUNT in private local configuration");
const recipient=process.env.SAP_TEST_RECIPIENT;
let token;
async function api(route,body,method=body?'POST':'GET'){
 const response=await fetch(base+route,{method,headers:{'Content-Type':'application/json',...(token?{'sap-token':token}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});
 const result=await response.json();if(result.code!==200)throw new Error(route+': '+result.message);return result.data;
}
(async()=>{
 assert.equal(process.env.SAP_SEND_DEPLOY_TEST,'yes');
 assert(recipient && /^[1-9][0-9]{4,14}@qq\.com$/.test(recipient), "Set SAP_TEST_RECIPIENT explicitly");
 assert(process.env.SAP_TEST_PASSWORD);
 const login=await api('/api/auth/admin/login',{studentId:account,password:process.env.SAP_TEST_PASSWORD});
 assert.equal(recipient, String(login.user.qq) + '@qq.com', 'Only send to the authenticated test account');
 token=login.token;
 assert(token);
 const templates=await api('/api/email/templates'),bindings=await api('/api/email/bindings');
 assert.equal(bindings.length,5);assert(bindings.every(b=>b.templateId));
 const before=templates.map(t=>[t.id,t.enabled]);
 console.log('PASS: five code events bound; enabled flags',JSON.stringify(before));
 await api('/api/email/config/test',{});
 console.log('PASS: SMTP connection');
 const samples=JSON.parse(fs.readFileSync(path.join(__dirname,'../templates/email/samples.json'),'utf8'));
 for(const sample of samples){
   const t=templates.find(t=>t.templateKey===sample.templateKey);assert(t);
   const p=await api('/api/email/templates/'+t.id+'/preview',sample.variables);
   assert(p.html.includes('中南林业科技大学软件协会'));
   assert(p.html.includes('万维网连接五大洲，二进制写尽天下事'));
 }
 console.log('PASS: all five online template previews');
 const status=await api('/api/email/delivery/status');assert(status.enabled);
 assert.equal(Number(status.queue),0,'Existing work in queue: do not add test traffic now');
 const runId=new Date().toISOString();
 const subjects=[1,2].map(n=>'【部署验证 '+n+'/2】邮件队列 '+runId);
 for(let i=0;i<2;i++){
   const sample=samples[i===0?0:3];
   const t=templates.find(t=>t.templateKey===sample.templateKey);
   await api('/api/email/test-send',{to:recipient,subject:subjects[i],htmlContent:t.htmlContent,variables:{...sample.variables,name:'部署测试（无需操作）'}});
 }
 console.log('Queued exactly two test messages to '+recipient+'; monitoring at 10-second intervals');
 let result;
 for(let i=0;i<30;i++){
   const logs=(await api('/api/email/delivery/logs?page=1&size=100')).records.filter(r=>subjects.includes(r.subject));
   const failure=logs.find(r=>['FAILED','EXPIRED','UNKNOWN'].includes(r.status));
   if(failure)throw new Error('Test message '+failure.status+': '+failure.detail);
   const success=logs.filter(r=>r.status==='SUCCESS');
   if(success.length===2){result={logs,success};break;}
   await new Promise(r=>setTimeout(r,10000));
 }
 assert(result,'Timeout waiting for both test emails');
 const ordered=result.success.sort((a,b)=>Number(a.created_at)-Number(b.created_at));
 const secondStart=result.logs.find(r=>r.status==='SENDING'&&r.message_id===ordered[1].message_id);
 assert(Number(secondStart.created_at)-Number(ordered[0].created_at)>=65000,'Global cooldown was not respected');
 assert.equal(Number((await api('/api/email/delivery/status')).queue),0);
 assert.deepEqual((await api('/api/email/templates')).map(t=>[t.id,t.enabled]),before);
 if(process.env.PLAYWRIGHT_MODULE){
   const {chromium}=require(process.env.PLAYWRIGHT_MODULE);
   const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
   try{
     const page=await browser.newPage({viewport:{width:1500,height:1050}}),errors=[];
     page.on('pageerror',e=>errors.push(e.message));
     await page.addInitScript(t=>{if(window===window.top)localStorage.setItem('sap-token',t)},token);
     await page.goto(base+'/admin/email');
     await page.getByText('代码事件绑定',{exact:true}).waitFor();
     await page.getByText('邮件发送中心',{exact:true}).waitFor();
     await page.getByRole('tab',{name:'发送日志',exact:true}).click();
     await page.getByText(subjects[1],{exact:true}).first().waitFor();
     await page.waitForFunction(()=>![...document.querySelectorAll('.mail-ops .el-loading-mask')].some(e=>e.getBoundingClientRect().width>0));
     await page.locator('.mail-ops').last().screenshot({path:'/tmp/sap-mail-online.png'});
     assert.deepEqual(errors,[]);console.log('PASS: live admin delivery/log UI');
   }finally{await browser.close();}
 }
 console.log(JSON.stringify({result:'PASS',recipient,messages:ordered.map(r=>({id:r.message_id,status:r.status,at:r.created_at})),cooldownMs:Number(secondStart.created_at)-Number(ordered[0].created_at),queue:0,templateFlagsUnchanged:true}));
})().catch(e=>{console.error(e.message);process.exitCode=1});
