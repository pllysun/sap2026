// Browser regression against local Vite; every API call mocked, never sends real email.
const assert = require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE);
(async()=>{
  const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
  try {
    const page=await browser.newPage({viewport:{width:390,height:844}}), calls=[], errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.route('**/api/**',async route=>{
      const request=route.request(),path=new URL(request.url()).pathname;calls.push(path);
      const reply=data=>route.fulfill({json:{code:200,data}});
      if(path==='/api/auth/captcha')return reply({captchaId:'test-image',image:'data:image/svg+xml;base64,'+Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40"><text x="12" y="28">ABCD</text></svg>').toString('base64')});
      if(path.endsWith('/send')) {
        assert.deepEqual(request.postDataJSON(),{account:'20260007',captchaId:'test-image',captcha:'ABCD'});
        return reply({requestId:'12345678-1234-1234-1234-123456789abc',cooldownSeconds:180,notice:'如账号存在且注册 QQ 有效，验证码将发送到该 QQ 邮箱。'});
      }
      if(path.endsWith('/reset')) {
        if(request.postDataJSON().code==='000000')return route.fulfill({json:{code:400,message:'验证码无效或已过期，请重新申请'}});
        assert.equal(request.postDataJSON().newPassword,'new-password');return reply('密码已重置');
      }
      throw new Error('Unexpected API '+path);
    });
    await page.goto('http://127.0.0.1:3001/forgot-password?from=app');
    await page.getByRole('img',{name:'图形验证码，点击刷新',exact:true}).waitFor();
    await page.screenshot({path:'/tmp/password-recovery-form.png',fullPage:true});
    assert.deepEqual(calls,['/api/auth/captcha']);
    await page.getByLabel('注册账号（学号）').fill('20260007');
    await page.getByLabel('图形验证码',{exact:true}).fill('ABCD');
    await page.getByRole('button',{name:'发送邮箱验证码',exact:true}).click();
    await page.getByRole('heading',{name:'设置新密码'}).waitFor();
    assert.equal(calls.filter(p=>p.endsWith('/send')).length,1);
    assert(await page.getByRole('button',{name:/秒后可再次发送/}).isDisabled());
    await page.getByLabel('邮箱验证码',{exact:true}).fill('000000');
    await page.getByLabel('新密码',{exact:true}).fill('new-password');
    await page.getByLabel('再次输入新密码').fill('mismatched');
    await page.getByRole('button',{name:'验证并重置密码'}).click();
    assert.equal(await page.getByRole('alert').innerText(),'两次输入的新密码不一致');
    assert(!calls.some(p=>p.endsWith('/reset')));
    await page.getByLabel('再次输入新密码').fill('new-password');
    await page.getByRole('button',{name:'验证并重置密码'}).click();
    await page.getByText('验证码无效或已过期，请重新申请',{exact:true}).waitFor();
    await page.getByLabel('邮箱验证码',{exact:true}).fill('123456');
    await page.getByRole('button',{name:'验证并重置密码'}).click();
    await page.getByRole('heading',{name:'密码已重置'}).waitFor();
    assert(await page.getByText('在 App 中操作的同学，请返回软协课表重新登录。').isVisible());
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.screenshot({path:'/tmp/password-recovery-mobile.png',fullPage:true});
    await page.goto('http://127.0.0.1:3001/forgot-password?from=admin');
    assert.equal(await page.getByRole('link',{name:'返回登录'}).getAttribute('href'),'/admin/login');
    assert.equal(calls.filter(p=>p.endsWith('/send')).length,1);
    assert.deepEqual(errors,[]);console.log('PASS: explicit send only, fixed account request, cooldown, password confirmation, invalid-code handling, reset success, mobile layout, admin/App returns');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
