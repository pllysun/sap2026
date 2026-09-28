// Authenticate once, then verify production with GET requests only.
// Credentials are provided by the ignored local environment file; never log PII.
const assert = require('node:assert/strict')
const base = (process.env.SAP_BASE_URL || 'https://csuftsap.top').replace(/\/$/, '')
let token
async function api(path, body, anonymous = false) {
  const response = await fetch(base + path, {
    method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', ...(!anonymous && token ? { 'sap-token': token } : {}) },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000),
  })
  const result = await response.json()
  if (anonymous) { assert([401, 403].includes(result.code)); return }
  assert.equal(result.code, 200, `Request failed: ${path.split('?')[0]} (${result.code})`)
  return result.data
}
const descending = rows => rows.every((row, index) => !index || new Date(rows[index - 1].startedAt).getTime() >= new Date(row.startedAt).getTime())
;(async () => {
  assert(process.env.SAP_ADMIN_ACCOUNT && process.env.SAP_ADMIN_PASSWORD, 'Administrator credentials missing')
  token = (await api('/api/auth/admin/login', { studentId: process.env.SAP_ADMIN_ACCOUNT, password: process.env.SAP_ADMIN_PASSWORD })).token
  assert(token)
  const batches = await api('/api/class-schedule/admin/batches?page=1&size=30')
  assert(descending(batches.records), 'Collection batches are not newest first')
  for (const batch of batches.records.slice(0, 3)) {
    const history = await api(`/api/class-schedule/admin/pull/history?batchId=${encodeURIComponent(batch.batchId)}&page=1&size=20`)
    assert(descending(history.records), 'Collection history is not newest first')
  }
  const grades = await api('/api/term/grades')
  assert(grades.length > 0)
  const members = await api(`/api/term/list?grade=${encodeURIComponent(grades[0])}&current=1&size=24`)
  assert(members.records.length > 0 && members.records.length <= 24)
  const id = members.records[0].userId
  const profile = await api(`/api/user/${id}/profile`)
  assert.equal(profile.user.id, id)
  assert.equal(profile.sections.length, 12)
  assert(!/password|credential|secret|paymentCode/i.test(JSON.stringify(profile)))
  for (const section of profile.sections) {
    const data = await api(`/api/user/${id}/profile/relations?section=${section.key}&current=1&size=2`)
    assert.equal(Number(data.total), Number(section.total))
    assert(data.records.length <= 2)
    if (data.total > 2) {
      const next = await api(`/api/user/${id}/profile/relations?section=${section.key}&current=2&size=2`)
      assert(next.records.length > 0 && next.records.length <= 2)
      assert(!next.records.some(row => data.records.some(first => row.id === first.id && row.kind === first.kind)))
    }
  }
  await api(`/api/user/${id}/profile`, undefined, true)
  await api(`/api/user/${id}/profile/relations?section=terms`, undefined, true)
  const version = await api('/api/app/version')
  console.log(JSON.stringify({ apiChecks: 'passed', batchesChecked: batches.records.length,
    newestFirst: true, profileSections: profile.sections.length, anonymousDenied: true,
    appVersion: version.versionName, appBuild: version.versionCode }))
  if (!process.env.PLAYWRIGHT_MODULE) return
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE)
  const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1050 } })
    const errors = [], writes = []
    page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(({ token, base }) => { if (location.origin === base) localStorage.setItem('sap-token', token) }, { token, base })
    await page.route('**/api/**', route => {
      if (route.request().method() === 'GET') return route.continue()
      writes.push(route.request().method()); return route.abort()
    })
    await page.goto(base + '/admin/member')
    const cards = page.locator('.term-card-main')
    await cards.first().waitFor()
    assert.equal(await page.locator('.term-card-right').count(), 0)
    assert.equal(await page.getByRole('button', { name: '移除', exact: true }).count(), 0)
    assert.equal(await page.locator('.term-grid').evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length), 4)
    assert((await page.locator('.term-pagination').innerText()).includes('条/页'))
    await cards.first().click()
    const dialog = page.getByRole('dialog', { name: '成员详情' })
    await dialog.locator('.profile-nav button').first().waitFor()
    assert.equal(await dialog.locator('.profile-nav button').count(), 12)
    await Promise.all([
      page.waitForResponse(res => res.url().includes('/profile/relations?section=study')),
      dialog.locator('.profile-nav').getByRole('button', { name: /学习小队/ }).click(),
    ])
    await page.waitForFunction(() => !document.querySelector('[class*="enter-active"]'))
    const bounds = await dialog.boundingBox()
    assert(bounds.y >= 0 && bounds.y + bounds.height <= 1051)
    await dialog.getByRole('button', { name: '关闭', exact: true }).last().click()
    await page.goto(base + '/admin/settings')
    const nav = page.getByRole('navigation', { name: '设置分类' })
    await nav.waitFor(); assert.equal(await nav.locator('button').count(), 4)
    for (const title of ['身份管理', '文件存储', '账号与安全', '基础配置']) {
      await nav.getByRole('button', { name: new RegExp(title) }).click()
      assert.equal(await page.locator('.settings-section-heading h3').textContent(), title)
    }
    await page.getByText('页脚与二维码', { exact: true }).waitFor({ state: 'visible' })
    assert.deepEqual(errors, []); assert.deepEqual(writes, [])
    console.log(JSON.stringify({ browserChecks: 'passed', compactCards: true, memberDialog: true, settingsGroups: 4, businessWrites: 0 }))
  } finally { await browser.close() }
})().catch(error => { console.error(error.message); process.exitCode = 1 })
