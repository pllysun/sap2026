// 本地权限回归：所有 API 使用测试数据，不连接线上或修改真实游客等级。
// PLAYWRIGHT_MODULE=/path/to/playwright SCHEDULE_UI_URL=http://127.0.0.1:3188 node scripts/schedule-permissions-ui-check.cjs
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const base = process.env.SCHEDULE_UI_URL || 'http://127.0.0.1:3188'

;(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    headless: true,
  })
  try {
    for (const roles of [[0], [1], [1, 2], [2]]) {
      const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
      const errors = []
      let level = 1, saves = 0
      page.on('pageerror', error => errors.push(error.message))
      await page.addInitScript(() => localStorage.setItem('sap-token', 'schedule-permission-test'))
      await page.route('**/*', async route => {
        const url = new URL(route.request().url())
        if (url.origin !== new URL(base).origin) return route.abort()
        if (!url.pathname.startsWith('/api/')) return route.continue()
        let data = {}
        if (url.pathname === '/api/auth/info') data = { user: { id: 9001, name: '权限测试账号' }, roles }
        if (url.pathname === '/api/app/feedback/issues') data = { records: [], total: 0 }
        if (url.pathname === '/api/app/feedback/admin/summary') data = { scheduleUsers: 0, openIssues: 0, closedIssues: 0 }
        if (url.pathname === '/api/app/cloud/admin') data = { guestAccessLevel: level, announcements: [] }
        if (url.pathname === '/api/app/cloud/admin/guest-access-level') {
          assert(roles.includes(0) || roles.includes(1), '普通管理员不应发出修改请求')
          assert.equal(route.request().method(), 'PUT')
          level = route.request().postDataJSON().level
          saves++
          data = { guestAccessLevel: level }
        }
        return route.fulfill({ json: { code: 200, data } })
      })

      await page.goto(base + '/admin/schedule-app')
      await page.getByRole('tab', { name: '课表云控', exact: true }).click()
      const panel = page.locator('.cloud-panel')
      await panel.getByText('当前生效：基础能力', { exact: true }).waitFor()
      if (roles.includes(0) || roles.includes(1)) {
        assert.equal(await panel.getByText('仅超级管理员或会长可修改', { exact: true }).count(), 0)
        assert(await panel.getByRole('button', { name: '新建公告', exact: true }).isEnabled())
        await panel.locator('.level-option').filter({ hasText: '完整能力' }).click()
        await panel.getByRole('button', { name: '保存权限等级', exact: true }).click()
        await page.getByRole('button', { name: '确认修改', exact: true }).click()
        await panel.getByText('当前生效：完整能力', { exact: true }).waitFor()
        assert.equal(level, 2)
        assert.equal(saves, 1)
      } else {
        await panel.getByText('仅超级管理员或会长可修改', { exact: true }).waitFor()
        assert.equal(await panel.getByRole('button', { name: '新建公告', exact: true }).count(), 0)
        assert.equal(await panel.getByRole('button', { name: '保存权限等级', exact: true }).count(), 0)
        for (const radio of await panel.getByRole('radio').all()) assert(await radio.isDisabled())
        assert.equal(saves, 0)
      }
      assert.deepEqual(errors, [])
      console.log('PASS: schedule cloud roles=' + JSON.stringify(roles))
      if (roles.length === 1 && roles[0] === 1 && process.env.SCHEDULE_UI_SCREENSHOT) {
        await panel.screenshot({ path: process.env.SCHEDULE_UI_SCREENSHOT })
      }
      await page.close()
    }
  } finally {
    await browser.close()
  }
})().catch(error => { console.error(error); process.exitCode = 1 })
