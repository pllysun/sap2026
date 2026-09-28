// 本地 UI 回归，所有 API 均被拦截，不访问线上、不发送邮件。
// PLAYWRIGHT_MODULE=/path/to/playwright node scripts/email-ui-check.cjs
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 try {
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{if(window===window.top)localStorage.setItem('sap-token','local-mail-ui-test')});
 const templates=JSON.parse(fs.readFileSync(path.join(__dirname,'../../templates/email/templates.json'),'utf8')).map((t,i)=>({...t,id:String(i+2),variables:[...new Set([...(t.subject+t.htmlContent).matchAll(/\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}/g)].map(m=>m[1]))]}));
 const keys=['ACCOUNT_REGISTERED','PASSWORD_CODE','MEMBER_JOINED','ISSUE_REPLIED','STUDY_REVIEW_REQUESTED','APP_REGISTRATION_CODE'];
 const bindings=templates.map((t,i)=>({eventKey:keys[i],title:t.templateName,templateId:t.id,variables:t.variables,defaults:{associationIntro:'协会介绍'}}));
 const hookDefinitions=bindings.map(h=>({...h,parameters:h.variables.map(name=>({name,label:name,description:'业务提供的 '+name,example:name==='code'?'123456':name==='expiresInMinutes'?'5':'示例'+name})),guidance:['PASSWORD_CODE','APP_REGISTRATION_CODE'].includes(h.eventKey)?'验证码和有效期必须放在正文，主题不得含验证码。':'所有代码参数必须完整使用。'}));
 let createdTemplate;
 const auditContext={source:'snapshot',eventTitle:'App 问题管理员回复',reason:'管理员回复问题反馈，通知问题提交人跟进',recipient:{id:10,account:'20260001',name:'测试同学'},initiator:{id:20,account:'20200020',name:'回复管理员'},operator:{name:'系统工作器',account:'系统'},details:{'问题编号':8,'问题标题':'课表显示异常','问题内容摘要':'周次显示不正确','回复摘要':'已修正周次计算'}};
 let retried=false;
 await page.route('http://127.0.0.1:3000/api/**',route=>{
   const u=new URL(route.request().url());let data={};
   if(u.pathname==='/api/auth/info')data={user:{name:'邮件 UI 验证'},roles:[0]};
   if(u.pathname==='/api/email/config')data={configured:true,passwordSet:true,enabled:true};
   if(u.pathname==='/api/email/templates'){
     if(route.request().method()==='POST'){createdTemplate=route.request().postDataJSON();data={...createdTemplate,id:'new-draft'};}
     else data=templates;
   }
   if(u.pathname==='/api/email/hooks')data=hookDefinitions;
   if(u.pathname==='/api/email/bindings')data=bindings;
   if(u.pathname.startsWith('/api/email/bindings/')){const h=bindings.find(h=>u.pathname.endsWith(h.eventKey));h.templateId=route.request().postDataJSON().templateId;}
   if(u.pathname==='/api/email/delivery/status')data={queue:1,failed:1,enabled:true};
   if(u.pathname==='/api/email/delivery/messages')data={total:1,records:[{id:123,message_id:'test-message',event_key:'ISSUE_REPLIED',recipient:'test@example.com',subject:'你的问题有新回复',status:'SUCCESS',context:auditContext,created_at:Date.now()}]};
   if(u.pathname==='/api/email/delivery/logs/123/history')data={total:3,records:[{id:123,status:'SUCCESS',detail:'SMTP 已接受',created_at:Date.now()},{id:122,status:'SENDING',detail:'开始处理',created_at:Date.now()-1000},{id:121,status:'QUEUED',detail:'已加入发送队列',created_at:Date.now()-2000}]};
   if(['/api/email/delivery/queue','/api/email/delivery/logs','/api/email/delivery/failed'].includes(u.pathname))data={total:1,records:[{id:'test-message',event_key:'ISSUE_REPLIED',recipient:'test@example.com',subject:'你的问题有新回复',state:u.pathname.endsWith('/queue')?'PENDING':null,status:u.pathname.endsWith('/logs')?'SUCCESS':'FAILED',created_at:Date.now(),reason:'模拟发送失败',detail:'SMTP 已接受',actor_id:1}]};
   if(u.pathname==='/api/email/failed/test-message')data={recipient:'test@example.com',subject:'你的问题有新回复',reason:'模拟失败',html:'<p>失败邮件正文</p>'};
   if(u.pathname==='/api/email/failed/actions/retry'){assert.deepEqual(route.request().postDataJSON().ids,['test-message']);retried=true;}
   return route.fulfill({json:{code:200,data}});
 });
 await page.goto('http://127.0.0.1:3000/email');
 await page.getByText('代码事件绑定',{exact:true}).waitFor();
 const appHook=page.locator('.hook-card').filter({hasText:'APP_REGISTRATION_CODE'});
 await appHook.waitFor();
 assert((await appHook.innerText()).includes('邮箱'));
 const issueHook=page.locator('.hook-card').filter({hasText:'ISSUE_REPLIED'});
 await issueHook.locator('.el-select').click();
 const options=page.locator('.el-select-dropdown:visible .el-select-dropdown__item');
 await options.first().waitFor();
 assert.equal(await options.count(),1);assert((await options.first().innerText()).includes('问题反馈'));
 await page.keyboard.press('Escape');
 await page.getByRole('button',{name:'新增模板',exact:true}).click();
 const editor=page.getByRole('dialog',{name:'新增邮件模板'});
 const save=editor.getByRole('button',{name:'保存模板',exact:true});
 assert(await save.isDisabled());
 await editor.locator('.el-select').click();
 await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({hasText:'修改密码'}).click();
 assert.equal(await editor.locator('.contract-parameter').count(),4);
 await editor.getByRole('button',{name:'插入正文',exact:true}).first().click();
 assert((await editor.locator('.html-editor textarea').inputValue()).includes('{{name}}'));
 await editor.getByRole('button',{name:'生成基础内容',exact:true}).click();
 await page.getByRole('button',{name:'替换内容',exact:true}).click();
 await editor.getByText('参数完整，可保存后在代码事件中绑定',{exact:true}).waitFor();
 const subject=editor.locator('.el-form-item').filter({has:page.locator('.el-form-item__label').getByText('邮件主题',{exact:true})}).locator('input');
 await subject.fill('{{code}}');assert(await save.isDisabled());
 await subject.fill('修改密码验证码');assert(await save.isEnabled());
 await editor.locator('.html-editor textarea').fill('{{name}} {{account}} {{code}} {{expiresInMinutes}} {{extra}}');
 assert(await save.isDisabled());
 await editor.locator('.html-editor textarea').fill('{{name}} {{account}} {{code}} {{expiresInMinutes}}');
 assert(await save.isEnabled());
 await editor.locator('.el-form-item').filter({has:page.locator('.el-form-item__label').getByText('模板标识',{exact:true})}).locator('input').fill('test.password-draft');
 await editor.getByRole('button',{name:'预览',exact:true}).click();
 const preview=page.getByRole('dialog',{name:'预览：修改密码验证码'});
 await preview.waitFor();
 assert((await preview.locator('iframe').getAttribute('srcdoc')).includes('123456'));
 await preview.getByRole('button',{name:'Close this dialog'}).click();
 await editor.locator('.el-dialog__body').evaluate(el=>el.scrollTop=0);
 await page.waitForTimeout(300);
 const box=await editor.boundingBox();assert(box.y>=0&&box.y+box.height<=1000);
 await editor.screenshot({path:'/tmp/mail-hook-template-editor.png'});
 await save.click();await editor.waitFor({state:'hidden'});
 assert.equal(createdTemplate.eventKey,'PASSWORD_CODE');assert.equal(createdTemplate.enabled,false);
 assert(createdTemplate.htmlContent.includes('{{expiresInMinutes}}'));
 console.log('PASS: code-first editor, four password parameters, scaffold/insert, missing/extra and secret-subject guards, sample preview, inactive draft save');
 const deletes=page.getByRole('button',{name:'删除',exact:true});assert.equal(await deletes.count(),templates.length);
 for(const b of await deletes.all())assert(await b.isDisabled());
 await page.locator('.templates-card').screenshot({path:'/tmp/mail-template-buttons.png'});
 await page.locator('.hook-card').first().getByRole('button',{name:'解绑',exact:true}).click();
 await page.getByRole('button',{name:'确定',exact:true}).click();
 await page.waitForTimeout(300);assert(await deletes.first().isEnabled());
 await page.getByRole('tab',{name:'失败邮件',exact:true}).click();
 await page.getByRole('button',{name:'查看内容',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'失败邮件内容快照'});await dialog.waitFor();
 await page.waitForTimeout(400); // 等待 Element Plus 入场动画完成后检查最终视口位置。
 const g=await dialog.boundingBox();assert(g.y>=0&&g.y+g.height<=1000);
 await dialog.getByRole('button',{name:'关闭',exact:true}).click();
 await page.getByRole('button',{name:'重试',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();
 await page.waitForTimeout(300);assert(retried);
 await page.getByRole('tab',{name:'发送日志',exact:true}).click();
 await page.getByText('最新状态',{exact:true}).waitFor();
 await page.getByText('SMTP 已接受',{exact:true}).first().waitFor();
 await page.getByText('测试同学 · 20260001（ID 10）',{exact:true}).waitFor();
 await page.getByText('触发人：回复管理员 · 20200020（ID 20）',{exact:true}).waitFor();
 assert.equal(await page.locator('.mail-ops').last().locator('.el-table__body-wrapper .el-table__row').count(),1);
 await page.locator('.el-table__expand-icon').click();
 await page.getByText('开始发送',{exact:true}).waitFor();
 await page.locator('.business-context').getByText('课表显示异常',{exact:true}).waitFor();
 await page.locator('.business-context').getByText('已修正周次计算',{exact:true}).waitFor();
 assert.equal(await page.locator('.mail-history').getByText('发送中',{exact:true}).count(),0);
 assert(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth),'Wide audit table must scroll internally, not push the page under the sidebar');
 await page.locator('.mail-ops').last().screenshot({path:'/tmp/mail-delivery-center.png'});
 assert.deepEqual(errors,[]);console.log('PASS: business cause/user/initiator/issue/reply context, exact template contracts, latest state and history, bound delete protection, unbind, failed preview, retry; no live requests');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
