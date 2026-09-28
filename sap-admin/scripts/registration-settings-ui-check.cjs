// 本地回归：API 全部使用测试数据，验证配置加载、保存、冲突、异常与窄屏布局。
// PLAYWRIGHT_MODULE=/path/to/playwright node scripts/registration-settings-ui-check.cjs
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const base = process.env.REGISTRATION_UI_URL || 'http://127.0.0.1:3187'
const output = process.env.REGISTRATION_UI_OUTPUT || '/tmp'
const copy = value => JSON.parse(JSON.stringify(value))
const defaults = {
  captcha: { enabled: true, freeLimit: 0, freeWindowHours: 24, length: 6, ttlSeconds: 180, minSolveSeconds: 1 },
  quotas: { enabled: true, cooldownSeconds: 10, ipHourlyLimit: 20, ipDailyLimit: 50, qqDailyLimit: 3, globalMinuteLimit: 30, maxConcurrent: 4, maxConcurrentPerIp: 1 },
  requests: { enabled: true, registerCapacity: 20, registerPerMinute: 20, captchaCapacity: 10, captchaPerMinute: 10, captchaGlobalCapacity: 60, captchaGlobalPerMinute: 60 },
  trustedProxies: '127.0.0.1,::1',
}

;(async () => {
  fs.mkdirSync(output, { recursive: true })
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => localStorage.setItem('sap-token', 'registration-settings-local-test'))
    let view = { revision: 'revision-1', config: copy(defaults), defaults: copy(defaults) }
    view.config.quotas.ipHourlyLimit = 17 // 与推荐值不同，确认 UI 使用接口返回值。
    view.config.quotas.cooldownSeconds = 23
    let saves = 0, failRead = false
    await page.route('**/*', async route => {
      const url = new URL(route.request().url())
      if (url.origin !== new URL(base).origin) return route.abort()
      if (!url.pathname.startsWith('/api/')) return route.continue()
      let data = {}
      if (url.pathname === '/api/auth/info') data = { user: { name: '注册防护测试管理员' }, roles: [0] }
      if (url.pathname === '/api/position/list') data = []
      if (url.pathname === '/api/setting/value') data = url.searchParams.get('key') === 'current_grade' ? '2026' : '30'
      if (url.pathname === '/api/setting/registration-protection') {
        if (route.request().method() === 'PUT') {
          const request = route.request().postDataJSON()
          saves++
          if (request.revision !== view.revision) return route.fulfill({ json: { code: 409, message: '配置已被其他管理员更新，请重新加载后修改' } })
          assert.deepEqual(Object.keys(request).sort(), ['config', 'revision'])
          view = { revision: 'saved-' + saves, config: copy(request.config), defaults: copy(defaults) }
        } else if (failRead) {
          return route.fulfill({ json: { code: 503, message: '配置加载暂不可用' } })
        }
        data = copy(view)
      }
      return route.fulfill({ json: { code: 200, data } })
    })

    await page.goto(base + '/settings')
    await page.getByRole('button', { name: /账号与安全/ }).click()
    const panel = page.locator('.registration-settings')
    const save = panel.getByRole('button', { name: '保存注册防护配置', exact: true })
    const hourly = panel.locator('#registration-ipHourlyLimit')
    await hourly.waitFor()
    assert.equal(await hourly.inputValue(), '17')
    assert.equal(await panel.locator('#registration-cooldownSeconds').inputValue(), '23')
    assert(await save.isDisabled())

    await panel.locator('#registration-maxConcurrentPerIp').fill('5')
    await panel.locator('#registration-maxConcurrentPerIp').press('Tab')
    await save.click()
    await panel.getByText('同 IP 并发上限不能超过实例总并发上限', { exact: true }).waitFor()
    assert.equal(saves, 0)
    await panel.getByRole('button', { name: '撤销修改', exact: true }).click()

    await hourly.fill('37')
    await hourly.press('Tab')
    await save.click()
    await panel.getByText('已与数据库同步', { exact: true }).waitFor()
    assert.equal(view.config.quotas.ipHourlyLimit, 37)
    assert.equal(view.config.quotas.cooldownSeconds, 23)
    assert.equal(saves, 1)
    await page.reload()
    await page.getByRole('button', { name: /账号与安全/ }).click()
    await hourly.waitFor()
    assert.equal(await hourly.inputValue(), '37')

    // Element Plus 的原生 checkbox 不可见，点击它所在的可见开关控件。
    await panel.locator('.el-switch').filter({ has: page.getByRole('switch', { name: '注册验证码开关' }) }).click()
    assert(await panel.locator('#registration-freeLimit').isDisabled())
    assert(await panel.locator('#registration-ttlSeconds').isEnabled())
    await save.click()
    await panel.getByText('已与数据库同步', { exact: true }).waitFor()
    assert.equal(view.config.captcha.enabled, false)
    await panel.getByRole('button', { name: '填入推荐值', exact: true }).click()
    assert.equal(await hourly.inputValue(), '20')
    assert.equal(saves, 2)
    await panel.getByRole('button', { name: '撤销修改', exact: true }).click()

    view.revision = 'another-administrator'
    view.config.quotas.ipHourlyLimit = 12
    await hourly.fill('38')
    await hourly.press('Tab')
    await save.click()
    await panel.getByText('配置已被其他管理员更新，请重新加载后修改', { exact: true }).waitFor()
    assert.equal(await hourly.inputValue(), '38')
    assert.equal(view.config.quotas.ipHourlyLimit, 12)
    await panel.getByRole('button', { name: '重新加载', exact: true }).click()
    await panel.getByText('已与数据库同步', { exact: true }).waitFor()
    assert.equal(await hourly.inputValue(), '12')

    await page.setViewportSize({ width: 1440, height: 1500 })
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.waitForFunction(() => !document.querySelector('.el-message'))
    await panel.screenshot({ path: path.join(output, 'registration-settings-desktop.png') })
    for (const width of [900, 390]) {
      await page.setViewportSize({ width, height: 1000 })
      await panel.scrollIntoViewIfNeeded()
      assert((await panel.boundingBox()).width >= 220, '侧栏不能将配置面板挤压到无法编辑')
      const overflow = await panel.evaluate(element => {
        const card = element.getBoundingClientRect()
        return [...element.querySelectorAll('.registration-settings__group, .el-input-number')]
          .some(item => { const rect = item.getBoundingClientRect(); return rect.right > card.right + 1 || rect.left < card.left - 1 })
      })
      assert.equal(overflow, false, `配置面板在 ${width}px 不应溢出`)
    }
    await panel.screenshot({ path: path.join(output, 'registration-settings-mobile.png') })
    failRead = true
    await page.reload()
    await page.getByRole('button', { name: /账号与安全/ }).click()
    await panel.getByText('配置加载暂不可用', { exact: true }).waitFor()
    assert.equal(await panel.locator('.el-input-number').count(), 0)
    failRead = false
    await panel.getByRole('button', { name: '重新加载配置', exact: true }).click()
    await hourly.waitFor()
    assert.equal(await hourly.inputValue(), '12')
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ status: 'passed', saveRequests: saves, checks: ['database_values', 'cross_field_validation', 'save_reload', 'switches', 'defaults_draft', 'version_conflict', 'load_retry', 'responsive_layout'], screenshots: output }))
  } catch (error) {
    const page = browser.contexts()[0]?.pages()[0]
    if (page) {
      console.error('UI failure context:', JSON.stringify({ url: page.url(), text: (await page.locator('body').innerText()).slice(0, 3000) }))
      await page.screenshot({ path: path.join(output, 'registration-settings-failure.png'), fullPage: true })
    }
    throw error
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
