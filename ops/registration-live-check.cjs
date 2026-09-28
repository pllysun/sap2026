// Verify the deployed settings page and CAPTCHA flow without changing live settings.
// REGISTRATION_LIVE_AUTH_FILE contains a temporary administrator token and existing studentId.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

;(async () => {
  const auth = JSON.parse(fs.readFileSync(process.env.REGISTRATION_LIVE_AUTH_FILE, 'utf8'))
  const base = 'https://csuftsap.top'
  const output = process.env.REGISTRATION_UI_OUTPUT
  assert(auth.token && auth.studentId && output)
  fs.mkdirSync(output, { recursive: true })
  const browser = await chromium.launch({
    executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true,
  })
  const errors = []
  try {
    const admin = await browser.newPage({ viewport: { width: 1440, height: 1200 } })
    admin.on('pageerror', error => errors.push(error.message))
    await admin.addInitScript(({ token, origin }) => {
      if (location.origin === origin) localStorage.setItem('sap-token', token)
    }, { token: auth.token, origin: base })
    // The live administrator check is strictly read-only.
    await admin.route('**/api/**', route =>
      route.request().method() === 'GET' ? route.continue() : route.abort())
    const configResponse = admin.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/setting/registration-protection')
    await admin.goto(base + '/admin/settings')
    const view = await (await configResponse).json()
    assert.equal(view.code, 200)
    const panel = admin.locator('.registration-settings')
    const hourly = panel.locator('#registration-ipHourlyLimit')
    await hourly.waitFor()
    assert.equal(await hourly.inputValue(), String(view.data.config.quotas.ipHourlyLimit))
    assert.equal(await panel.locator('#registration-cooldownSeconds').inputValue(), String(view.data.config.quotas.cooldownSeconds))
    assert(await panel.getByRole('button', { name: '保存注册防护配置', exact: true }).isDisabled())
    await panel.screenshot({ path: path.join(output, 'registration-settings-live.png') })
    await admin.setViewportSize({ width: 390, height: 1000 })
    // Wait for the sidebar's 0.3 second collapse transition before measuring layout.
    await admin.waitForFunction(() => document.querySelector('.sidebar')?.getBoundingClientRect().width <= 65)
    await panel.scrollIntoViewIfNeeded()
    assert((await panel.boundingBox()).width >= 220)
    await panel.screenshot({ path: path.join(output, 'registration-settings-live-mobile.png') })

    assert(view.data.config.captcha.enabled && view.data.config.captcha.freeLimit === 0,
      'This live check requires mandatory CAPTCHA; do not modify the production policy for testing')
    const user = await browser.newPage({ viewport: { width: 1000, height: 1000 } })
    user.on('pageerror', error => errors.push(error.message))
    await user.route('**/api/**', route => {
      const request = route.request()
      if (request.method() === 'GET') return route.continue()
      if (new URL(request.url()).pathname === '/api/auth/register' && request.method() === 'POST') {
        const payload = request.postDataJSON()
        assert.equal(payload.studentId, auth.studentId, 'Only probe an existing account')
        assert(!payload.captchaCode || payload.captchaCode === '!!!!!!')
        return route.continue()
      }
      return route.abort()
    })
    await user.goto(base + '/register')
    await user.getByPlaceholder('请输入学号', { exact: true }).fill(auth.studentId)
    await user.getByPlaceholder('请输入密码', { exact: true }).fill('Registration-Verification-Only')
    await user.getByPlaceholder('请输入真实姓名', { exact: true }).fill('注册防护验证')
    await user.getByPlaceholder('请输入QQ号', { exact: true }).fill('100000001')
    const registration = () => user.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/auth/register')
    let response = registration()
    await user.getByRole('button', { name: '立即注册', exact: true }).click()
    let result = await (await response).json()
    assert.equal(result.code, 200)
    assert.equal(result.data.captchaRequired, true)
    await user.locator('.captcha-img').waitFor()
    await user.waitForFunction(() => document.querySelector('.captcha-img')?.naturalWidth > 0)
    await user.getByPlaceholder('请输入图中字符', { exact: true }).fill('!!!!!!')
    response = registration()
    await user.getByRole('button', { name: '立即注册', exact: true }).click()
    result = await (await response).json()
    assert.equal(result.code, 400)
    await user.locator('.error-text').filter({ hasText: '验证码' }).waitFor()
    await user.locator('.captcha-img').waitFor()
    await user.screenshot({ path: path.join(output, 'registration-captcha-live.png'), fullPage: true })
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ status: 'passed', checks: [
      'live_database_values', 'admin_desktop_and_mobile', 'mandatory_captcha', 'invalid_captcha_rejected', 'no_page_errors',
    ], liveSettingsChanged: false, accountsCreated: 0, screenshots: output }))
  } finally { await browser.close() }
})().catch(error => { console.error(error.message); process.exitCode = 1 })
