// Read-only production verification. Never prints tokens, names, messages or credentials.
const assert = require('node:assert/strict')
const base = (process.env.SAP_BASE_URL || 'https://csuftsap.top').replace(/\/$/, '')
async function main() {
  assert(process.env.SAP_ADMIN_ACCOUNT && process.env.SAP_ADMIN_PASSWORD, 'Local credentials missing')
  const login = await (await fetch(base + '/api/auth/admin/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ studentId: process.env.SAP_ADMIN_ACCOUNT, password: process.env.SAP_ADMIN_PASSWORD }), signal: AbortSignal.timeout(30000) })).json()
  assert.equal(login.code, 200); const token = login.data.token
  const api = async path => { const response = await fetch(base + path, { headers: { 'sap-token': token }, signal: AbortSignal.timeout(30000) }); const result = await response.json(); assert.equal(result.code, 200, path); return result.data }
  const stats = await api('/api/home/overview')
  assert(Number(stats.registeredCount) >= Number(stats.memberCount)); assert(Number(stats.memberCount) > 0)
  assert(!('financeStats' in stats) && !('users' in stats))
  const gradeTotal = stats.membersByGrade.reduce((n, grade) => n + Number(grade.count), 0)
  assert(gradeTotal <= Number(stats.memberCount))
  assert(Array.isArray(stats.memberArchiveByTerm) && stats.memberArchiveByTerm.length > 0)
  const data = await api('/api/message/list?current=1&size=50')
  const all = data.records.flatMap(row => [row, ...(row.replies || [])])
  assert(all.every(row => typeof row.userName === 'string' && row.userName.trim().length > 0))
  assert(all.every(row => typeof row.liked === 'boolean' && Number(row.likeCount) >= 0))
  for (const path of ['/', '/admin/']) {
    const response = await fetch(base + path, { signal: AbortSignal.timeout(30000) }); assert.equal(response.status, 200)
    const html = await response.text()
    const assets = [...html.matchAll(/(?:src|href)="(\/[^"\s]+\.(?:js|css))"/g)].map(match => match[1])
    assert(assets.length >= 2)
    for (const asset of assets) { const response = await fetch(base + asset, { signal: AbortSignal.timeout(30000) }); assert.equal(response.status, 200, asset); assert(!response.headers.get('content-type')?.includes('text/html'), asset) }
  }
  console.log(JSON.stringify({ apiChecks: 'passed', formalMembers: Number(stats.memberCount), registeredUsers: Number(stats.registeredCount), gradeTotal, messagesChecked: data.records.length, entriesWithNames: all.length, assets: 'passed' }))
  if (!process.env.PLAYWRIGHT_MODULE) return
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE)
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = [], writes = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(token => localStorage.setItem('sap_token', token), token)
    await page.route('**/api/**', route => { if (route.request().method() === 'GET') return route.continue(); writes.push(route.request().method()); return route.abort() })
    await page.goto(base + '/home')
    await page.getByRole('heading', { name: '软件协会', level: 1, exact: true }).waitFor()
    assert(await page.evaluate(() => Math.abs(document.querySelector('.home-intro').getBoundingClientRect().height + document.querySelector('.nav').getBoundingClientRect().height - innerHeight) <= 2), 'Fullscreen hero')
    assert.equal(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--primary').trim()), '#2878d5')
    assert.equal(await page.locator('.editor-file').innerText(), 'hello.c')
    await page.setViewportSize({ width: 2560, height: 1280 })
    assert(await page.evaluate(() => {
      const hero = document.querySelector('.home-intro').getBoundingClientRect(), copy = document.querySelector('.intro-copy').getBoundingClientRect(), stage = document.querySelector('.creative-stage').getBoundingClientRect()
      return Math.abs(hero.width - innerWidth) < 2 && Math.abs((copy.left + stage.right) / 2 - innerWidth / 2) < 2 && stage.right - copy.left <= 1281 && parseFloat(getComputedStyle(document.querySelector('h1')).fontSize) <= 78 && getComputedStyle(document.querySelector('.home-intro'), '::before').backgroundImage.includes('linear-gradient')
    }), 'Hero background fills viewport while content remains centered at its original measure')
    assert((await page.locator('.hero-editor').evaluate(element => getComputedStyle(element).animationName)).includes('window-float'))
    assert((await page.locator('.logo-stamp').evaluate(element => getComputedStyle(element).animationName)).includes('badge-float'))
    await page.addStyleTag({ content: '.hero-editor, .logo-stamp { animation-play-state: paused !important; }' })
    await page.getByRole('button', { name: '切换编程语言' }).click()
    assert.equal(await page.getByRole('menuitemradio').count(), 11)
    await page.getByRole('menuitemradio', { name: 'Python', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('.editor-file')?.textContent === 'hello.py')
    await page.getByRole('menu').waitFor({ state: 'detached' })
    await page.getByRole('button', { name: '演示代码输出' }).click()
    await page.waitForFunction(() => document.querySelector('.demo-trigger')?.textContent === '再演示')
    assert.equal(await page.locator('.terminal-output code').innerText(), 'Hello, 软件协会!')
    await page.waitForFunction(count => document.querySelector('.community-metrics strong')?.textContent.replaceAll(',', '') === count, String(stats.memberCount))
    assert.equal(await page.locator('.activity-story').count(), 3)
    assert(await page.locator('.note-entry').count() > 0)
    assert.equal(await page.locator('.intro-update').count(), 2)
    assert.equal(await page.locator('.intro-update__title').first().innerText(), await page.locator('.activity-story h3').first().innerText())
    assert.equal(await page.locator('.intro-update__title').last().innerText(), await page.locator('.note-entry h3').first().innerText())
    assert.equal(await page.locator('.data-notice').count(), 0)
    for (const width of [390, 1024, 1920]) { await page.setViewportSize({ width, height: 900 }); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)) }
    await page.goto(base + '/message-board'); await page.locator('.message__name').first().waitFor()
    assert((await page.locator('.message__name').allTextContents()).every(name => name.trim().length > 0))
    assert(await page.locator('.like-toggle').count() > 0)
    for (const [path, title] of [['/study', '学习小组'], ['/activities', '软协活动'], ['/notes', '软协笔记'], ['/profile', '个人信息'], ['/join', '加入软件协会']]) {
      await page.goto(base + path); await page.getByRole('heading', { name: title, level: 1, exact: true }).waitFor()
      await page.locator('.page-header .page-kicker').waitFor()
      for (const width of [390, 1440]) { await page.setViewportSize({ width, height: 900 }); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), path) }
    }
    assert.deepEqual(errors, []); assert.deepEqual(writes, [])
    console.log(JSON.stringify({ browserChecks: 'passed', fullBleed2560: true, defaultLanguage: 'C', languages: 11, realDataVisible: true, namesVisible: true, businessWrites: 0, runtimeErrors: 0 }))
  } finally { await browser.close() }
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
