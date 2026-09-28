// Live smoke test. Default read-only except login and deliberately invalid recovery requests.
// LIVE_RECOVERY_SEND=yes allows ONE email to the explicitly configured test account QQ mailbox.
// Never submits a valid reset request or changes any real password.
const assert = require('node:assert/strict');
const {randomUUID} = require('node:crypto');
const {createInterface} = require('node:readline');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE);
const base='https://csuftsap.top';
const account = process.env.SAP_TEST_ACCOUNT || process.env.SAP_ADMIN_ACCOUNT;
if (!account) throw new Error("Set SAP_TEST_ACCOUNT or SAP_ADMIN_ACCOUNT in private local configuration");
const recipient = process.env.SAP_TEST_RECIPIENT;
let token;
async function raw(path,body,authenticated=false) {
  const response=await fetch(base+path,{method:body?'POST':'GET',
    headers:{'Content-Type':'application/json',...(authenticated?{'sap-token':token}:{})},
    body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});
  assert(response.ok,`${path} HTTP ${response.status}`);
  return response.json();
}
async function api(path,body,authenticated=true) {
  const r=await raw(path,body,authenticated);assert.equal(r.code,200,`${path}: ${r.message}`);return r.data;
}
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
  assert(process.env.SAP_TEST_PASSWORD,'Set SAP_TEST_PASSWORD');
  const login=await api('/api/auth/admin/login',{studentId:account,password:process.env.SAP_TEST_PASSWORD},false);
  token=login.token;
  const before=await api('/api/email/templates');
  const bindings=await api('/api/email/bindings');
  const binding=bindings.find(b=>b.eventKey==='PASSWORD_CODE');
  assert(binding.compatible);
  assert(before.find(t=>String(t.id)===String(binding.templateId)).enabled);
  assert.equal((await raw('/api/auth/password-recovery/send',{account:account,captchaId:'invalid-captcha',captcha:'0000'})).code,400);
  assert.equal((await raw('/api/auth/password-recovery/reset',{requestId:randomUUID(),code:'000000',newPassword:'unused-validation-value'})).code,400);
  console.log('PASS: enabled password template, anonymous recovery endpoints, invalid captcha/code rejected');
  const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
  const input=createInterface({input:process.stdin,output:process.stdout});
  const answer=prompt=>new Promise(resolve=>input.question(prompt+'\n',resolve));
  try {
    const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    const sends=[];
    page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('password-recovery/send'))sends.push(r);});
    await page.goto(base+'/forgot-password?from=app');
    const captcha=page.getByRole('img',{name:'图形验证码，点击刷新',exact:true});
    await captcha.waitFor();
    assert.equal(sends.length,0);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.screenshot({path:'/tmp/password-recovery-online.png',fullPage:true});
    console.log('PASS: live mobile recovery page loads, no automatic email, layout fits viewport');
    if(process.env.LIVE_RECOVERY_SEND==='yes') {
      assert(recipient, 'Set SAP_TEST_RECIPIENT explicitly');
      assert.equal(String(login.user.qq) + '@qq.com', recipient, 'Refuse email to any other recipient');
      assert.equal(String(login.user.studentId),account);
      await captcha.screenshot({path:'/tmp/password-recovery-live-captcha.png'});
      console.log('CAPTCHA_READY /tmp/password-recovery-live-captcha.png');
      const text=(await answer('Enter displayed image captcha (not email verification code):')).trim();
      assert(/^[A-Za-z0-9]{4}$/.test(text));
      await page.getByLabel('注册账号（学号）').fill(account);
      await page.getByLabel('图形验证码',{exact:true}).fill(text);
      const resultPromise=page.waitForResponse(r=>r.url().endsWith('/api/auth/password-recovery/send')&&r.request().method()==='POST');
      const nextCaptchaPromise=page.waitForResponse(r=>r.url().endsWith('/api/auth/captcha'));
      await page.getByRole('button',{name:'发送邮箱验证码',exact:true}).click();
      const result=await (await resultPromise).json();
      assert.equal(result.code,200,result.message);
      assert.equal(result.data.cooldownSeconds,180);
      assert.equal(sends.length,1);
      console.log('PASS: one recovery request accepted for authorized QQ email');
      await page.getByRole('heading',{name:'设置新密码'}).waitFor();
      assert(await page.getByRole('button',{name:/秒后可再次发送/}).isDisabled());
      // Use a fresh, genuine image captcha to verify the SERVER cooldown, not just the button.
      const nextCaptcha=await (await nextCaptchaPromise).json();
      assert.equal(nextCaptcha.code,200);
      await page.waitForFunction(src=>document.querySelector('img[alt="图形验证码，点击刷新"]')?.src===src,nextCaptcha.data.image);
      await captcha.screenshot({path:'/tmp/password-recovery-live-captcha-2.png'});
      console.log('CAPTCHA_READY /tmp/password-recovery-live-captcha-2.png');
      const second=(await answer('Enter second displayed image captcha to test cooldown:')).trim();
      assert(/^[A-Za-z0-9]{4}$/.test(second));
      const throttled=await raw('/api/auth/password-recovery/send',{account:account,captchaId:nextCaptcha.data.captchaId,captcha:second});
      assert.equal(throttled.code,429,throttled.message);
      assert(throttled.message.includes('3 分钟'));
      console.log('PASS: server enforces 180-second cooldown even with fresh valid captcha; no second email queued');
      // Email verification code and password fields stay untouched throughout this script.
      console.log('CHECKING_SMTP');
      let delivered;
      for(let i=0;i<30;i++) {
        const messages=await api('/api/email/delivery/messages?size=100');
        const own=messages.records.filter(m=>m.event_key==='PASSWORD_CODE'&&m.recipient===recipient&&Number(m.created_at)>=startedAt);
        const row=own.find(m=>m.context?.reason?.includes('修改密码'));
        if(row) {
          assert.equal(String(row.context.recipient.account),account);
          assert(row.context.recipient.name && row.context.reason);
          if(row.status==='SUCCESS'){delivered=row;break;}
          assert(!['FAILED','EXPIRED','UNKNOWN'].includes(row.status),`Mail status ${row.status}`);
        }
        await sleep(10000);
      }
      assert(delivered,'SMTP acceptance not observed within timeout');
      const history=await api(`/api/email/delivery/logs/${delivered.id}/history?size=100`);
      for(const state of ['QUEUED','SENDING','SUCCESS'])assert(history.records.some(r=>r.status===state));
      console.log(JSON.stringify({result:'SMTP_ACCEPTED',messageId:delivered.message_id,recipient:delivered.recipient,businessContext:true}));
      // Confirm original password remains usable; no recovery reset was submitted.
      await api('/api/auth/admin/login',{studentId:account,password:process.env.SAP_TEST_PASSWORD},false);
      console.log('PASS: original login password unchanged');
    }
    assert.deepEqual((await api('/api/email/templates')).map(t=>[t.id,t.enabled]),before.map(t=>[t.id,t.enabled]));
    assert.deepEqual(errors,[]);
  } finally {input.close();await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1});
const startedAt=Date.now();
