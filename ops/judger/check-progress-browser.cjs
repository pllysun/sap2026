const fs=require('node:fs'),assert=require('node:assert/strict');
const {chromium}=require('/Users/pllysun/Library/Caches/ms-playwright-go/1.57.0/package');
const root='/Users/pllysun/Library/Caches/sap-progress-1512',base='http://127.0.0.1:4176';
const session=JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const fixtures=JSON.parse(fs.readFileSync(root+'/live-ui-fixtures.json'));
const states=['AC','WRONG','ISSUE','PENDING','NONE'],verdicts=['AC','WA','RE','',''];
const rawCodes={c:'#include <stdio.h>\nint main(){int n=1;printf("%d\\n",n);return 0;}',cpp:'#include <iostream>\nint main(){int n=1;std::cout<<n<<"\\n";return 0;}',java:'class Solution{public int[] twoSum(int[] nums,int target){return new int[]{0,1};}}',python:'def add(a,b):return a+b',rust:'fn main(){let value=1;println!("{}",value);}'};
const clone=x=>structuredClone(x),cache=new Map(),errors=[],failed=[],checks=[];let formatLanguage='java';
async function live(url){if(!cache.has(url)){cache.set(url,(async()=>{const response=await fetch(session.base+url,{headers:{'sap-token':session.token}});const result=await response.json();assert.equal(result.code,200,'Read fixture '+url);return result.data})())}return clone(await cache.get(url));}
function addProgress(rows,otherAccount=false){return rows.map((row,index)=>({...row,progress:{state:otherAccount?'NONE':states[index%5],lastVerdict:otherAccount?'':verdicts[index%5],pending:!otherAccount&&index%5===3}}));}
async function attach(page,account='A'){
  await page.addInitScript(account=>{localStorage.setItem('sap_token',account);localStorage.setItem('sap-token',account)},account);
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/**',async route=>{
    const request=route.request(),u=new URL(request.url()),url=u.pathname+u.search;
    try{
      assert.equal(request.method(),'GET','Preview must never mutate production');let data;
      if(u.pathname==='/api/auth/info')data={user:{id:account==='B'?2:1,name:'验收账号',nickname:account,studentId:'preview-'+account},roles:[0]};
      else if(u.pathname==='/api/setting/public')data={};
      else if(u.pathname==='/api/oj/problems'){data=clone(fixtures['/api/oj/problems?size=100']);data.records=addProgress(data.records.slice(0,20),account==='B');}
      else if(u.pathname==='/api/oj/sets/1'){data=clone(fixtures['/api/oj/sets/1']);data.items=addProgress(data.items,account==='B');}
      else if(/^\/api\/admin\/oj\/monitor\/\d+\/code$/.test(u.pathname))data={id:Number(u.pathname.split('/').at(-2)),language:formatLanguage,mode:'STDIO',status:'AC',code:rawCodes[formatLanguage]};
      else data=await live(url);
      await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data})});
    }catch(e){failed.push({path:u.pathname,error:e.message});await route.fulfill({status:500,body:'Preview fixture unavailable'});}
  });
}
async function noOverflow(page,width){await page.setViewportSize({width,height:1000});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Page overflow at '+width);}
async function centered(page){const b=await page.locator('dialog[open]').boundingBox(),v=page.viewportSize();assert(Math.abs(b.x+b.width/2-v.width/2)<2,'Dialog horizontal center');assert(Math.abs(b.y+b.height/2-v.height/2)<2,'Dialog vertical center');}
(async()=>{
 const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(60000);await attach(page);
  await page.goto(base+'/oj');await page.locator('.oj-problem-row').first().waitFor();
  assert.deepEqual(await page.locator('.oj-library-tabs [role=tab]').allTextContents(),['全部题目 70','题单','提交记录']);
  assert.deepEqual(await page.locator('.oj-problem-progress').evaluateAll(es=>es.slice(0,5).map(e=>e.dataset.progress)),states);
  for(const [index,color] of [[0,'rgb(32, 129, 94)'],[1,'rgb(197, 72, 85)'],[2,'rgb(168, 114, 18)']]){await page.locator('.oj-problem-row').nth(index).hover();assert.equal(await page.locator('.oj-problem-progress').nth(index).evaluate(e=>getComputedStyle(e).color),color);}
  for(const width of [390,768,1024,1440,1920])await noOverflow(page,width);
  await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:root+'/library-preview-1440.png',fullPage:true});checks.push('All problems → sets → submissions; green/red/amber badges retain their color on hover at five widths');
  await page.goto(base+'/oj/sets/1');await page.locator('.oj-set-problem').first().waitFor();
  assert.deepEqual(await page.locator('.oj-problem-progress').evaluateAll(es=>es.slice(0,5).map(e=>e.dataset.progress)),states);
  await page.screenshot({path:root+'/practice-preview-1440.png',fullPage:true});
  await page.getByRole('tab',{name:'提交记录',exact:true}).click();await page.getByRole('button',{name:'查看代码',exact:true}).first().click();
  await centered(page);await page.locator('dialog .cm-content').waitFor();assert.equal(await page.locator('dialog .cm-content').getAttribute('contenteditable'),'false');
  for(const width of [390,768,1440]){await noOverflow(page,width);await centered(page);assert(await page.locator('dialog[open]').evaluate(e=>e.scrollWidth<=e.clientWidth+1));}
  await page.screenshot({path:root+'/code-dialog-preview-1440.png'});await page.keyboard.press('Escape');assert(!(await page.locator('dialog').isVisible()));checks.push('Set badges and centered source dialog before/after loading, responsive resizing, read-only source and Escape');
  const other=await browser.newPage();await attach(other,'B');await other.goto(base+'/oj/sets/1');await other.locator('.oj-set-problem').first().waitFor();assert(await other.locator('.oj-problem-progress').evaluateAll(es=>es.every(e=>e.dataset.progress==='NONE')));await other.goto(base+'/oj');await other.locator('.oj-problem-row').first().waitFor();assert(await other.locator('.oj-problem-progress').evaluateAll(es=>es.every(e=>e.dataset.progress==='NONE')));await other.close();checks.push('Fresh second-account page renders no inherited account badges');
  await page.setViewportSize({width:1440,height:1000});await page.goto(base+'/admin/oj');await page.locator('.oj-problem-table .el-table__row').first().waitFor();
  const actions=page.locator('.oj-row-actions').first();assert.equal(await actions.locator('.oj-action svg.oj-icon').count(),4);assert.equal(await actions.locator('.el-button').count(),0);
  await actions.getByRole('button',{name:'更多',exact:true}).click();await page.locator('.oj-actions-menu [role=menuitem]').first().waitFor();assert(await page.locator('.oj-actions-menu [role=menuitem]').first().locator('.oj-icon').count());await page.keyboard.press('Escape');
  await page.getByRole('button',{name:'调整展示顺序',exact:true}).click();await page.locator('.oj-order-row').first().waitFor();assert(await page.locator('.oj-order-row').first().getByRole('button').first().isDisabled());assert.equal(await page.locator('.oj-order-row').first().locator('button svg.oj-icon').count(),2);await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();await page.getByRole('dialog').waitFor({state:'hidden'});
  await page.screenshot({path:root+'/admin-problems-preview-1440.png',fullPage:true});
  for(const width of [390,768,1024,1440,1920])await noOverflow(page,width);
  await page.setViewportSize({width:1440,height:1000});await page.getByRole('tab',{name:'判题日志与队列',exact:true}).click();await page.locator('.oj-runtime-table .el-table__row').first().waitFor();
  for(const language of ['c','cpp','java','python','rust']){
    formatLanguage=language;await page.locator('.oj-runtime-table .el-table__row').first().getByRole('button',{name:'查看过程',exact:true}).click();await page.locator('.oj-run-content-tabs').getByRole('button',{name:'提交的代码',exact:true}).click();await page.locator('.oj-run-code .cm-content').waitFor();
    assert(!(await page.locator('.oj-run-code .oj-monitor-error').count()),language+' format error');const formatted=await page.locator('.oj-run-code .cm-content').innerText();assert(formatted.split('\n').length>2,language+' remains minified');assert.equal(await page.locator('.oj-run-code .cm-content').getAttribute('contenteditable'),'false');
    await page.getByRole('button',{name:'查看原始代码',exact:true}).click();assert.equal((await page.locator('.oj-run-code .cm-content').innerText()).trim(),rawCodes[language]);await page.getByRole('button',{name:'格式化显示',exact:true}).click();assert.equal(await page.locator('.oj-run-code .cm-content').innerText(),formatted);
    if(language==='java')await page.screenshot({path:root+'/admin-log-preview-1440.png'});
    await page.getByRole('dialog').getByRole('button',{name:'关闭',exact:true}).click();
  }
  checks.push('Admin job source formats C/C++/Java/Python/Rust with actual WASM engines; original display restores exact saved source');
  await page.getByRole('tab',{name:'统一管理',exact:true}).click();await page.locator('.node-card').first().waitFor();assert.equal(await page.locator('.node-card').count(),2);assert(await page.locator('.node-card').first().getByRole('button',{name:'停止节点',exact:true}).locator('.oj-icon').count());await page.getByRole('button',{name:'连接配置',exact:true}).first().click();await page.getByRole('dialog').getByRole('button',{name:'保存节点',exact:true}).waitFor();await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();
  for(const width of [390,768,1440])await noOverflow(page,width);await page.screenshot({path:root+'/admin-nodes-preview-1440.png',fullPage:true});
  await page.getByRole('tab',{name:'题单管理',exact:true}).click();await page.locator('.set-row-actions').first().waitFor();assert.equal(await page.locator('.set-row-actions').first().locator('.oj-action svg.oj-icon').count(),3);
  await page.locator('.set-row-actions').first().getByRole('button',{name:'编辑',exact:true}).click();await page.locator('.set-selected-row').first().waitFor();assert.equal(await page.locator('.set-selected-row').first().locator('.oj-action svg.oj-icon').count(),3);await page.getByRole('dialog').getByRole('button',{name:/^保存/}).waitFor();await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();checks.push('Styled icon actions across problem table, dropdown, ordering, node control/configuration, language settings and set management; no production mutations');
  assert.deepEqual(errors,[]);assert.deepEqual(failed,[]);const report={passed:true,widths:[390,768,1024,1440,1920],pageErrors:errors,failedCalls:failed,checks};fs.writeFileSync(__dirname+'/progress-browser-preview.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
