const fs=require('node:fs'),assert=require('node:assert/strict');
const {chromium}=require('/Users/pllysun/Library/Caches/ms-playwright-go/1.57.0/package');
const root='/Users/pllysun/Library/Caches/sap-oj-fixes-1513';
const admin=JSON.parse(fs.readFileSync(root+'/candidate-session.json'));
const user=JSON.parse(fs.readFileSync(root+'/candidate-users.json')).A;
const fixture=JSON.parse(fs.readFileSync(root+'/fixes-ui-fixtures.json'));
assert.equal(admin.base,'http://127.0.0.1:18113');
const errors=[],failed=[],checks=[];
const pushCheck=checks.push.bind(checks);checks.push=(value)=>{console.log(value);return pushCheck(value);};
async function api(path){const r=await(await fetch(admin.base+path,{headers:{'sap-token':user.token}})).json();assert.equal(r.code,200);return r.data;}
async function overflow(page,width){await page.setViewportSize({width,height:1000});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Overflow '+width);}
(async()=>{
 const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(45000);
  await page.addInitScript(s=>{localStorage.setItem('sap_token',s.user);localStorage.setItem('sap-token',s.admin)},{user:user.token,admin:admin.token});
  page.on('pageerror',e=>errors.push(e.message));page.on('response',async r=>{if(r.url().includes('/api/')){try{const x=await r.json();if(x.code!==200)failed.push({path:new URL(r.url()).pathname,code:x.code});}catch{}}});
  await page.goto(admin.base+'/oj');await page.locator('.oj-problem-row').first().waitFor();
  assert.deepEqual(await page.locator('.oj-library-tabs [role=tab]').allTextContents(),['全部题目 70','题单','提交记录']);
  for(const width of [390,768,1024,1440,1920])await overflow(page,width);
  checks.push('Real library API and original tabs/style at five viewport widths');
  await page.setViewportSize({width:1440,height:1000});await page.goto(admin.base+'/oj/'+fixture.first);
  await page.locator('.cm-content').first().waitFor();await page.locator('.oj-runtime-indicator').filter({hasText:'可以运行'}).waitFor();
  const history=await api('/api/oj/submissions?problemId='+fixture.first);
  const oldIndex=history.findIndex(j=>j.id===fixture.oldJob),newIndex=history.findIndex(j=>j.id===fixture.newJob);assert(oldIndex>=0&&newIndex>=0);
  const old=await api('/api/oj/submissions/'+fixture.oldJob),fresh=await api('/api/oj/submissions/'+fixture.newJob);
  let calls=0,release,entered;const held=new Promise(r=>release=r),pollStarted=new Promise(r=>entered=r);
  await page.route('**/api/oj/submissions/'+fixture.oldJob,async route=>{
    calls++;if(calls===1)await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data:{...old,status:'RUNNING'}})});
    else {entered();await held;try{await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data:old})});}catch{ /* Aborted old request is expected. */ }}
  });
  await page.getByRole('button',{name:'我的提交记录',exact:true}).click();await page.locator('.oj-history-row').nth(oldIndex).click();await pollStarted;
  await page.getByRole('button',{name:'我的提交记录',exact:true}).click();await page.locator('.oj-history-row').nth(newIndex).click();
  await page.locator('.oj-verdict .oj-status-badge[aria-label="通过 AC"]').waitFor();release();
  await page.waitForTimeout(1500);assert.equal(await page.locator('.oj-verdict .oj-status-badge').getAttribute('aria-label'),'通过 AC');
  await page.getByRole('button',{name:'查看提交代码',exact:true}).click();await page.locator('.oj-submitted-code .cm-content').waitFor();assert.equal((await page.locator('.oj-submitted-code .cm-line').allTextContents()).join('\n').trim(),fresh.code.trim());
  assert(!(await page.locator('.oj-task-wait').count()));checks.push('Delayed old polling response cannot replace selected AC, code or pending state');
  await page.unroute('**/api/oj/submissions/'+fixture.oldJob);
  let historyCalls=0,releaseHistory,enteredHistory;const heldHistory=new Promise(r=>releaseHistory=r),historyStarted=new Promise(r=>enteredHistory=r);
  await page.route('**/api/oj/submissions?*',async route=>{
    const p=new URL(route.request().url());if(p.searchParams.get('problemId')!==String(fixture.first)){await route.continue();return;}
    historyCalls++;if(historyCalls===1){enteredHistory();await heldHistory;try{await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data:history.slice(0,1)})});}catch{}}
    else await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data:history})});
  });
  await page.getByRole('button',{name:'我的提交记录',exact:true}).click();await historyStarted;
  await page.getByRole('button',{name:'测试输入',exact:true}).click();await page.getByRole('button',{name:'我的提交记录',exact:true}).click();
  await page.waitForFunction(n=>document.querySelectorAll('.oj-history-row').length===n,history.length);releaseHistory();await page.waitForTimeout(800);
  assert.equal(await page.locator('.oj-history-row').count(),history.length);checks.push('Delayed history response cannot overwrite a newer history request');
  await page.unroute('**/api/oj/submissions?*');
  await page.goto(admin.base+'/oj/sets/'+fixture.practice);await page.locator('.oj-set-problem').first().waitFor();
  assert(await page.locator('.oj-problem-progress').evaluateAll(es=>es.every(e=>e.dataset.progress==='AC')));
  await page.getByRole('tab',{name:'提交记录',exact:true}).click();await page.getByRole('button',{name:'查看代码',exact:true}).first().click();await page.locator('dialog[open] .cm-content').waitFor();
  for(const width of [390,768,1440]){await overflow(page,width);const b=await page.locator('dialog[open]').boundingBox(),v=page.viewportSize();assert(Math.abs(b.x+b.width/2-v.width/2)<2&&Math.abs(b.y+b.height/2-v.height/2)<2);}
  await page.keyboard.press('Escape');checks.push('20 accepted badges and centered source dialog preserved');
  await page.setViewportSize({width:1440,height:1000});await page.goto(admin.base+'/admin/oj');await page.locator('.oj-problem-table .el-table__row').first().waitFor();
  await page.getByRole('tab',{name:'判题日志与队列',exact:true}).click();await page.locator('.oj-runtime-table .el-table__row').first().waitFor();
  await page.locator('.oj-runtime-table .el-table__row').first().getByRole('button',{name:'查看过程',exact:true}).click();await page.locator('.oj-run-content-tabs').getByRole('button',{name:'提交的代码',exact:true}).click();await page.locator('.oj-run-code .cm-content').waitFor();
  assert(!(await page.locator('.oj-run-code .oj-monitor-error').count()));await page.getByRole('dialog').getByRole('button',{name:'关闭',exact:true}).click();
  await page.getByRole('tab',{name:'题单管理',exact:true}).click();await page.getByRole('button',{name:'隔离验收比赛',exact:true}).click();await page.getByRole('dialog').filter({hasText:'隔离验收比赛'}).waitFor();assert.equal(await page.getByRole('button',{name:'延长比赛',exact:true}).count(),0);
  checks.push('Real admin monitor/timeline/source and ended-contest extension action hidden');
  assert.deepEqual(errors,[]);assert.deepEqual(failed,[]);const report={passed:true,widths:[390,768,1024,1440,1920],pageErrors:errors,failedCalls:failed,checks};fs.writeFileSync(__dirname+'/fixes-browser-candidate.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }catch(e){fs.writeFileSync(root+'/browser-debug.json',JSON.stringify({errors,failed,checks}));throw e;}
 finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
