// Local regression fixtures; no business writes or test content sent to production.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || '/Users/pllysun/Library/Caches/ms-playwright-go/1.57.0/package')
const base = process.env.UI_BASE_URL || 'http://127.0.0.1:3001'
const fixtures = {
  '/api/auth/info': { user: { id: '1', nickname: '软协同学', name: '测试同学', studentId: '99000001', gender: 1, qq: '123456789' }, roles: [3] },
  '/api/setting/public': { footer_copyright: '中南林业科技大学软件协会' },
  '/api/join/status': true,
  '/api/home/overview': { memberCount: 281, registeredCount: 852, activityCount: 13, noteCount: 1, studyActivityCount: 10, memberArchiveByTerm: [{ grade: '2024', count: 120 }, { grade: '2025', count: 144 }, { grade: '2026', count: 17 }] },
  '/api/activity/page': { records: [
    { id: 1, title: '程序设计大赛', grade: '2026', createdAt: '2026-09-25', content: '在一次次思考、调试与协作中，将问题转化为答案。' },
    { id: 2, title: '学习小组作品分享', grade: '2026', createdAt: '2026-09-20', content: '把学习的收获带到伙伴面前，一起交流与成长。' },
    { id: 3, title: '新生宣讲与协会团建', grade: '2026', createdAt: '2026-09-15', content: '认识新朋友，让新的故事从这里开始。' },
  ] },
  '/api/note/list': { records: [{ id: 1, title: '软协就业 / 考研 / 保研学长经验分享', description: '来自学长学姐的个人经验，为下一阶段的选择提供参考。', readMinutes: 8, createdAt: '2026-09-18' }] },
  '/api/study/activity/list': [{ id: 1, title: '一起开始新的学习计划', grade: '2026', status: 1 }],
  '/api/study/my-status': { hasActivity: true, joined: true, activity: { id: 1, title: '本学期学习计划', activeWeek: 3 }, homework: { title: '基础练习与知识整理', fileName: '本周任务.pdf' }, submitted: false, submissions: [] },
  '/api/study/my-scores': { activities: [] },
  '/api/study/activity/all-with-stats': [],
  '/api/join/my-application': null,
  '/api/auth/captcha': { captchaId: 'local-only-captcha', image: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="120" height="40"%3E%3Ctext x="20" y="27" fill="%232878d5"%3ETEST%3C/text%3E%3C/svg%3E' },
  '/api/note/1': { id: 1, title: '一起开始你的学习计划', authorName: '软协同学', createdAt: '2026-09-20', content: '## 从基础开始\n\n把每一次尝试记录下来。\n\n> 一起学习，一起分享。\n\n```js\nconst together = true;\n```', viewCount: 12, downloadCount: 2 },
  '/api/outstanding-member/all': Array.from({ length: 6 }, (_, i) => ({ id: i + 1, name: `成员${i + 1}`, grade: '2024', destination: '就业', destinationDetail: '探索新的技术方向' })),
  '/api/log/public/calendar': Array.from({ length: 180 }, (_, i) => { const d = new Date(); d.setDate(d.getDate() - i); return [d.toISOString().slice(0, 10), i % 6 ? i % 30 : 0] }),
}
async function main() {
  // Optional: use real public activity/note content for visual review, never account details.
  if (process.env.SAP_ADMIN_ACCOUNT && process.env.SAP_ADMIN_PASSWORD) {
    const live = process.env.SAP_BASE_URL || 'https://csuftsap.top'
    const login = await (await fetch(live + '/api/auth/admin/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ studentId: process.env.SAP_ADMIN_ACCOUNT, password: process.env.SAP_ADMIN_PASSWORD }), signal: AbortSignal.timeout(30000) })).json()
    assert.equal(login.code, 200)
    for (const [path, query] of [['/api/activity/page', '?current=1&size=3'], ['/api/note/list', '?current=1&size=3']]) {
      const result = await (await fetch(live + path + query, { headers: { 'sap-token': login.data.token }, signal: AbortSignal.timeout(30000) })).json()
      assert.equal(result.code, 200); fixtures[path] = result.data
    }
  }
  const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const screenshotDir = process.env.UI_SCREENSHOT_DIR || '/tmp/sap-public-ui'
  fs.mkdirSync(screenshotDir, { recursive: true })
  let likeWrites = 0, unlikeWrites = 0, failNextLike = false, liked = false
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' })
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  await page.addInitScript(() => localStorage.setItem('sap_token', 'local-fixture-only'))
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname
    if (path === '/api/message/like') {
      if (route.request().method() === 'POST') likeWrites++; else unlikeWrites++
      await new Promise(resolve => setTimeout(resolve, 250))
      if (failNextLike) { failNextLike = false; return route.fulfill({ json: { code: 500, message: '测试：请求失败，请重试' } }) }
      liked = route.request().method() === 'POST'
      return route.fulfill({ json: { code: 200, data: { liked, likeCount: liked ? '7' : '6' } } })
    }
    if (path === '/api/message/list') return route.fulfill({ json: { code: 200, data: { total: '2', records: [
      { id: '1', userName: '修复后的姓名', avatar: '/not-found-avatar.jpg', content: '欢迎新同学，期待一起学习。', createdAt: '2026-09-17', liked, likeCount: liked ? '7' : '6', replies: [{ id: '11', userName: ' ', content: '一起加油！', liked: false, likeCount: '2' }] },
      { id: '2', userName: '', content: '历史账号昵称为空时，也要有可见的名称。', liked: false, likeCount: 0, replies: [] },
    ] } } })
    if (Object.hasOwn(fixtures, path)) return route.fulfill({ json: { code: 200, data: fixtures[path] } })
    throw Error('Unexpected fixture request: ' + path)
  })
  try {
    await page.goto(base + '/home'); await page.getByText('平台注册用户', { exact: true }).waitFor()
    await page.waitForFunction(() => document.querySelector('.community-metrics strong')?.textContent === '281')
    assert.equal(await page.locator('.nav__links-wrap').isVisible(), true)
    assert.equal(await page.locator('.activity-story').count(), 3)
    assert.equal(await page.locator('.heatmap-grid>span').count(), 182)
    assert(await page.evaluate(() => Math.abs(document.querySelector('.home-intro').getBoundingClientRect().height + document.querySelector('.nav').getBoundingClientRect().height - innerHeight) <= 2), 'Desktop hero must fill the viewport below the nav')
    await page.screenshot({ path: screenshotDir + '/home-first-screen.png' })
    await page.screenshot({ path: screenshotDir + '/home-desktop.png', fullPage: true })
    await page.getByRole('tab', { name: '算法思维' }).click()
    await page.getByRole('heading', { name: '让解决问题更有章法。' }).waitFor()
    await page.getByRole('tab', { name: '算法思维' }).press('ArrowRight')
    assert.equal(await page.getByRole('tab', { name: '应用实践' }).getAttribute('aria-selected'), 'true')
    for (const width of [320, 390, 768, 1024, 1920]) {
      await page.setViewportSize({ width, height: 900 })
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `Horizontal overflow at ${width}`)
      if (width === 390) { await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' })); await page.screenshot({ path: screenshotDir + '/home-mobile.png', fullPage: true }); await page.getByRole('button', { name: '打开导航菜单' }).click(); await page.getByRole('dialog', { name: '导航菜单' }).waitFor(); await page.getByRole('button', { name: '关闭导航菜单' }).click() }
    }
    await page.setViewportSize({ width: 1280, height: 900 }); await page.goto(base + '/message-board')
    await page.getByText('修复后的姓名', { exact: true }).waitFor()
    assert((await page.locator('.message__name').allTextContents()).every(name => name.trim()))
    const like = page.getByRole('button', { name: '点赞', exact: true }).first()
    await like.evaluate(button => { button.click(); button.click(); button.click() })
    await page.waitForFunction(() => document.querySelector('.like-toggle')?.getAttribute('aria-pressed') === 'true')
    assert.equal(likeWrites, 1); assert.equal(await page.locator('.like-toggle').first().innerText(), '7')
    await page.reload(); await page.getByRole('button', { name: '取消点赞' }).first().click()
    await page.waitForFunction(() => document.querySelector('.like-toggle')?.getAttribute('aria-pressed') === 'false')
    assert.equal(unlikeWrites, 1); assert.equal(await page.locator('.like-toggle').first().innerText(), '6')
    failNextLike = true; await page.getByRole('button', { name: '点赞', exact: true }).first().click()
    await page.getByRole('alert').filter({ hasText: '测试：请求失败' }).waitFor()
    assert.equal(await page.locator('.like-toggle').first().getAttribute('aria-pressed'), 'false')
    assert.equal(await page.locator('.like-toggle').first().innerText(), '6')
    await page.getByRole('button', { name: '点赞', exact: true }).first().click()
    await page.waitForFunction(() => document.querySelector('.like-toggle')?.getAttribute('aria-pressed') === 'true')
    await page.screenshot({ path: screenshotDir + '/message-board.png', fullPage: true })
    const pageRoutes = [['/study', '学习小组'], ['/activities', '软协活动'], ['/notes', '软协笔记'], ['/profile', '个人信息'], ['/join', '加入软件协会'], ['/notes/1', '一起开始你的学习计划']]
    for (const [route, title] of pageRoutes) {
      await page.goto(base + route); await page.getByRole('heading', { name: title, exact: true }).waitFor()
      await page.locator('.loading__spinner').waitFor({ state: 'hidden' })
      if (route === '/study') {
        await page.getByText('基础练习与知识整理', { exact: true }).waitFor()
        await page.getByRole('button', { name: '我的成绩', exact: true }).click()
        await page.getByRole('button', { name: '成绩统计', exact: true }).click()
        await page.getByRole('button', { name: '学习任务', exact: true }).click()
      }
      if (route === '/activities') {
        await page.locator('.act-featured').press('Enter'); await page.locator('.act-modal').waitFor()
        await page.locator('.act-modal__close').click()
      }
      for (const width of [320, 390, 768, 1440]) {
        await page.setViewportSize({ width, height: 900 })
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${route} overflow at ${width}`)
        if (width === 390 || width === 1440) await page.screenshot({ path: screenshotDir + '/' + route.replaceAll('/', '-') + '-' + width + '.png', fullPage: true })
      }
    }
    for (const route of ['/login', '/register', '/forgot-password']) {
      await page.goto(base + route); await page.locator('h1').waitFor()
      for (const width of [320, 390, 1440]) {
        await page.setViewportSize({ width, height: 900 })
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${route} overflow at ${width}`)
        if (width === 1440) await page.screenshot({ path: screenshotDir + route + '.png', fullPage: true })
      }
    }
    fixtures['/api/auth/info'].roles = ['4']
    await page.goto(base + '/home'); await page.getByRole('link', { name: /加入软件协会/ }).waitFor()
    assert.equal(await page.locator('.note-entry').first().getAttribute('href'), '/notes')
    await page.emulateMedia({ reducedMotion: 'no-preference' })
    await page.reload(); await page.locator('.activity-story').first().waitFor({ state: 'attached' })
    for (const selector of ['.life-section', '.community-section', '.join-section']) {
      await page.locator(selector).scrollIntoViewIfNeeded()
      await page.waitForFunction(selector => document.querySelector(selector).classList.contains('is-visible'), selector)
    }
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ checks: 'passed', fullscreenHero: true, allUserRoutes: true, responsiveWidths: [320, 390, 768, 1024, 1440, 1920], guestLinks: true, scrollAnimations: true, repeatedLikeRequests: 1, refreshState: true, failureFeedback: true, noRuntimeErrors: true, screenshotDir }))
  } finally { await browser.close() }
}
main().catch(e => { console.error(e.message); process.exitCode = 1 })
