// Isolated browser fixtures. Do not use this script to create real applications/payments.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || '/Users/pllysun/Library/Caches/ms-playwright-go/1.57.0/package')
const base = process.env.UI_BASE_URL || 'http://127.0.0.1:3001'
assert(/^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/.test(base), 'Fixture test must run locally')
const out = process.env.UI_SCREENSHOT_DIR || '/tmp/sap-user-responsive-108'
const qr = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240"><rect width="240" height="240" fill="#edf6ff"/><rect x="25" y="25" width="190" height="190" fill="white" stroke="#78ace2"/><text x="120" y="112" text-anchor="middle" fill="#2878d5">LOCAL TEST</text><text x="120" y="138" text-anchor="middle" fill="#2878d5">NOT FOR PAYMENT</text></svg>')
const qr2 = qr.replaceAll('LOCAL%20TEST', 'ALIPAY%20TEST')
let roles = [3], application = null, failApplication = false, applyWrites = 0, submitWrites = 0, recoverySends = 0
const activity = { id: 1, title: '一起完成一份真实的小作品 · 本学期学习分享与交流', grade: '2026', status: 1, seqNum: 1, activeWeek: 3, currentWeek: 10, memberCount: 42, content: '活动内容与照片详情。'.repeat(40), images: [{ imageUrl: qr }, { imageUrl: qr2 }] }
const newApplication = status => ({ status, managerName: '测试迎新负责人同学', managerQq: '12345678901', canRefresh: true, wechatQr: qr, alipayQr: qr2, paymentCode: 'LOCAL-FIXTURE-'.repeat(8) })
const fixtures = {
  '/api/setting/public': { footer_copyright: '中南林业科技大学软件协会', qr_qq_group_url: qr, qr_qq_group_name: '协会交流群', join_qq_group_url: qr, join_qq_group_name: '软件协会新生交流群', join_group_link: 'https://example.com/local-qr-fixture' },
  '/api/join/status': true,
  '/api/auth/captcha': { captchaId: 'local-fixture-captcha', image: qr },
  '/api/auth/register': { captchaRequired: true },
  '/api/auth/password-recovery/reset': true,
  '/api/home/overview': { memberCount: 20, registeredCount: 30, activityCount: 1, noteCount: 1, memberArchiveByTerm: [] },
  '/api/activity/page': { records: [activity], total: 1, pages: 1 },
  '/api/note/list': { records: [{ id: 1, title: '一起学习与分享', createdAt: '2026-09-29' }], total: 1 },
  '/api/note/1': { id: 1, title: '长内容与表格阅读测试', authorName: '测试同学', content: '## 示例\n\n' + 'LongText'.repeat(32) + '\n\n```c\n' + 'int long_line_' + 'abc'.repeat(100) + ';\n```\n\n|甲|乙|丙|丁|戊|己|\n|---|---|---|---|---|---|\n|' + Array(6).fill('long_cell_'.repeat(8)).join('|') + '|', viewCount: 1, downloadCount: 1 },
  '/api/study/activity/list': [activity], '/api/study/activity/all-with-stats': [activity],
  '/api/study/my-status': { hasActivity: true, joined: true, activity, homework: { title: '本周学习任务与知识整理', fileName: 'long-file-name'.repeat(15) + '.pdf' }, submitted: true, submissions: [{ id: 1, fileName: 'long-file-name'.repeat(15) + '.pdf' }], weekScore: { score: 9, comment: '反馈说明'.repeat(60), leaderName: '评分同学' } },
  '/api/study/my-scores': { activities: [{ ...activity, totalScore: 90, rank: 1, scores: [{ week: 1, score: 9, comment: '学习反馈'.repeat(60), leaderName: '评分同学' }] }] },
  '/api/outstanding-member/all': [], '/api/log/public/calendar': [],
  '/api/message/list': { total: 0, records: [] },
}
async function mock(route) {
  const request = route.request(), url = new URL(request.url()), pathname = url.pathname
  let data
  if (pathname === '/api/auth/info') data = { user: { id: 1, nickname: '测试同学的很长很长的网名', studentId: '99000001', name: '测试同学', qq: '12345678901', gender: 1 }, roles }
  else if (pathname === '/api/join/my-application') { if (failApplication) return route.fulfill({ json: { code: 500, message: '测试：加载失败' } }); data = application }
  else if (pathname === '/api/join/apply') { applyWrites++; await new Promise(resolve => setTimeout(resolve, 150)); data = application = newApplication(0) }
  else if (pathname === '/api/join/refresh-manager') data = application = { ...application, managerName: '另一位迎新负责人' }
  else if (pathname === '/api/join/submit-payment') { submitWrites++; application = { ...application, status: 1, paymentCode: JSON.parse(request.postData()).paymentCode }; data = true }
  else if (pathname === '/api/auth/password-recovery/send') { recoverySends++; data = { requestId: 'local-fixture-request', cooldownSeconds: 180, notice: '本地界面测试，未发送邮件。' } }
  else if (pathname === '/api/study/ranking') { const page = Number(url.searchParams.get('current') || 1); data = { total: 42, records: Array.from({ length: 20 }, (_, i) => ({ rank: (page - 1) * 20 + i + 1, userId: (page - 1) * 20 + i + 1, studentId: '99000001', userName: '测试同学', totalScore: 99 - i })) } }
  else if (Object.hasOwn(fixtures, pathname)) data = fixtures[pathname]
  else throw new Error(`Missing isolated fixture: ${pathname}`)
  await route.fulfill({ json: { code: 200, data } })
}
async function noOverflow(page, label) { assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), label) }
async function withinViewport(locator, page, label) {
  await locator.waitFor()
  // Wait for the entrance animation, but still fail if the settled panel is clipped.
  await page.waitForFunction(element => { const box = element.getBoundingClientRect(); return box.x >= -1 && box.y >= -1 && box.right <= innerWidth + 1 && box.bottom <= innerHeight + 1 }, await locator.elementHandle(), { timeout: 5000 })
  const box = await locator.boundingBox(), viewport = page.viewportSize()
  assert(box && box.x >= -1 && box.y >= -1 && box.x + box.width <= viewport.width + 1 && box.y + box.height <= viewport.height + 1, `${label}: ${JSON.stringify(box)}`)
}
async function main() {
  const { heroLanguages, codeTokens } = await import(pathToFileURL(path.resolve('sap-user/src/components/heroLanguages.js')))
  assert.equal(heroLanguages.length, 11)
  for (const language of heroLanguages) for (const line of language.code.split('\n')) assert.equal(codeTokens(line).map(token => token.text).join(''), line, language.id)
  fs.mkdirSync(out, { recursive: true })
  const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const errors = []
  const page = await browser.newPage({ viewport: { width: 2560, height: 1280 }, reducedMotion: 'reduce' })
  await page.addInitScript(() => localStorage.setItem('sap_token', 'local-fixture-only'))
  await page.route('**/api/**', mock); page.on('pageerror', error => errors.push(error.message))
  try {
    await page.goto(base + '/home'); await page.locator('.editor-file').waitFor(); assert.equal(await page.locator('.editor-file').innerText(), 'hello.c')
    await page.locator('.intro-update').first().waitFor()
    assert.equal(await page.locator('#home-title').innerText(), '软件协会')
    assert.equal(await page.locator('.intro-update__title').first().innerText(), activity.title)
    assert.equal(await page.locator('.intro-update__title').last().innerText(), fixtures['/api/note/list'].records[0].title)
    assert.equal(await page.locator('.intro-update').last().getAttribute('href'), '/notes/1')
    assert.equal(await page.locator('.stage-tag, .stage-coordinate, .intro-footnote').count(), 0)
    for (const [width, height] of [[2560, 1280], [1920, 1080], [1440, 900], [1024, 768]]) {
      await page.setViewportSize({ width, height })
      const bounds = await page.evaluate(() => {
        const hero = document.querySelector('.home-intro').getBoundingClientRect(), copy = document.querySelector('.intro-copy').getBoundingClientRect(), stage = document.querySelector('.creative-stage').getBoundingClientRect()
        return { width: hero.width, left: copy.left, right: stage.right, contentWidth: stage.right - copy.left, contentCenter: (stage.right + copy.left) / 2, height: hero.height + document.querySelector('.nav').getBoundingClientRect().height, title: parseFloat(getComputedStyle(document.querySelector('h1')).fontSize), background: getComputedStyle(document.querySelector('.home-intro'), '::before').backgroundImage }
      })
      assert(Math.abs(bounds.width - width) < 2); assert(bounds.contentWidth <= 1281); assert(Math.abs(bounds.contentCenter - width / 2) < 2, JSON.stringify(bounds)); assert(Math.abs(bounds.height - height) < 2, JSON.stringify(bounds)); assert(bounds.title <= 78 && bounds.title >= 45); assert(bounds.background.includes('linear-gradient'))
      await noOverflow(page, `hero ${width}`)
      if (width === 2560 || width === 1440) await page.screenshot({ path: `${out}/hero-${width}.png` })
    }
    const motionPage = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); await motionPage.addInitScript(() => localStorage.setItem('sap_token', 'local-fixture-only')); await motionPage.route('**/api/**', mock); await motionPage.goto(base + '/home'); await motionPage.locator('.hero-editor').waitFor()
    assert((await motionPage.locator('.hero-editor').evaluate(element => getComputedStyle(element).animationName)).includes('window-float')); assert((await motionPage.locator('.logo-stamp').evaluate(element => getComputedStyle(element).animationName)).includes('badge-float'))
    const outputVisible = await motionPage.locator('.hero-editor').evaluate(async editor => { const output = editor.querySelector('.hero-editor__terminal code'), note = document.querySelector('.stage-note'), animation = editor.getAnimations()[0]; animation.pause(); const visible = []; for (const time of [0, 2000, 4000, 6000, 7999]) { animation.currentTime = time; await new Promise(requestAnimationFrame); const a = output.getBoundingClientRect(), b = note.getBoundingClientRect(); visible.push(a.right + 8 <= b.left || a.bottom + 8 <= b.top || b.bottom + 8 <= a.top) } return visible })
    assert(outputVisible.every(Boolean), 'Code output stays clear of the message-board link throughout the float')
    await motionPage.locator('.intro-update').first().waitFor()
    const demoRequests = []; motionPage.on('request', request => demoRequests.push(request.url()))
    await motionPage.getByRole('button', { name: '演示代码输出' }).click()
    await motionPage.locator('.terminal-output[aria-busy="true"]').waitFor()
    assert.equal(await motionPage.locator('.code-line--active').count(), 1)
    await motionPage.getByRole('button', { name: '切换编程语言' }).click(); await motionPage.getByRole('menuitemradio', { name: 'Python', exact: true }).click()
    await motionPage.waitForTimeout(850)
    assert.equal(await motionPage.locator('.demo-trigger').innerText(), '演示'); assert.equal(await motionPage.locator('.terminal-output code').innerText(), 'Hello, 软件协会!')
    await motionPage.getByRole('button', { name: '演示代码输出' }).click()
    await motionPage.waitForFunction(() => document.querySelector('.demo-trigger')?.textContent === '再演示')
    assert.equal(await motionPage.locator('.terminal-output code').innerText(), 'Hello, 软件协会!'); assert.equal(await motionPage.locator('.terminal-output').getAttribute('aria-busy'), 'false')
    assert.deepEqual(demoRequests, [], 'Code preview never executes code or makes network requests')
    await motionPage.screenshot({ path: `${out}/hero-desktop-demo.png` })
    await motionPage.addStyleTag({ content: '.hero-editor { animation: none !important; transition: none !important; }' }); const angleBeforeHover = await motionPage.locator('.hero-editor').evaluate(element => getComputedStyle(element).transform); await motionPage.locator('.hero-editor').hover(); assert.equal(await motionPage.locator('.hero-editor').evaluate(element => getComputedStyle(element).transform), angleBeforeHover); await motionPage.close()
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.getByRole('button', { name: '演示代码输出' }).click(); assert.equal(await page.locator('.terminal-output').getAttribute('aria-busy'), 'false'); assert.equal(await page.locator('.demo-trigger').innerText(), '再演示')
    for (const language of heroLanguages) {
      await page.getByRole('button', { name: '切换编程语言' }).click(); await page.getByRole('menuitemradio', { name: language.label, exact: true }).click()
      await page.waitForFunction(file => document.querySelector('.editor-file')?.textContent === file, language.file)
      await page.waitForFunction(code => [...document.querySelectorAll('.code-line code')].map(el => el.textContent).join('\n') === code, language.code)
    }
    await page.getByRole('button', { name: '切换编程语言' }).press('ArrowDown'); await page.getByRole('menuitemradio', { name: 'C#', exact: true }).press('Home'); await page.getByRole('menuitemradio', { name: 'C', exact: true }).press('Enter'); assert.equal(await page.locator('.editor-file').innerText(), 'hello.c')
    await page.getByRole('button', { name: '切换编程语言' }).click(); await page.keyboard.press('Escape'); await page.getByRole('menu').waitFor({ state: 'detached' })
    for (const width of [320, 360, 390, 768]) {
      await page.setViewportSize({ width, height: 900 }); await page.getByRole('button', { name: '切换编程语言' }).click(); await withinViewport(page.getByRole('menu'), page, `language menu ${width}`); await page.getByRole('menuitemradio', { name: 'Kotlin', exact: true }).click(); await page.getByRole('menu').waitFor({ state: 'detached' }); await noOverflow(page, `language ${width}`)
      assert(await page.evaluate(() => document.querySelector('.intro-scroll').getBoundingClientRect().top - document.querySelector('.hero-editor').getBoundingClientRect().bottom >= 12), `Mobile scroll affordance clears code panel at ${width}`)
      if (width === 390) await page.screenshot({ path: `${out}/hero-mobile.png`, fullPage: false })
    }
    await page.locator('.intro-update__media img').evaluate(img => img.dispatchEvent(new Event('error'))); assert.equal(await page.locator('.intro-update--activity .intro-update__media svg').count(), 1)
    roles = [4]
    await page.goto(base + '/home'); await page.locator('.intro-update').first().waitFor(); assert.equal(await page.locator('.intro-update').last().getAttribute('href'), '/notes'); await page.locator('.home-actions').getByRole('link', { name: /加入软件协会/ }).waitFor()
    for (const status of [null, 0, 1, 2]) {
      application = status === null ? null : newApplication(status)
      await page.goto(base + '/join'); await page.locator('.join-state-heading h2').waitFor()
      for (const width of [320, 360, 390, 768, 1440]) {
        await page.setViewportSize({ width, height: 900 }); await noOverflow(page, `join ${status} ${width}`)
        const card = await page.locator('.join-content').boundingBox(); for (const image of await page.locator('.join-content img').all()) { const box = await image.boundingBox(); assert(box.x >= card.x && box.x + box.width <= card.x + card.width) }
        if (width === 390 || width === 1440) await page.screenshot({ path: `${out}/join-${status}-${width}.png`, fullPage: true })
      }
      if (status === 0) { await page.getByRole('button', { name: '支付宝', exact: true }).click(); assert.equal(await page.locator('.payment-section img').getAttribute('src'), qr2); await page.getByRole('button', { name: '更换负责人', exact: true }).click(); await page.getByText('另一位迎新负责人').waitFor() }
    }
    application = null; await page.goto(base + '/join'); const apply = page.getByRole('button', { name: '申请加入', exact: true }); await apply.waitFor(); await apply.evaluate(button => { button.click(); button.click() }); await page.getByLabel('交易单号', { exact: false }).fill('LOCAL-TEST-TRANSACTION'); await page.getByRole('button', { name: /提交审核/ }).click(); await page.getByRole('heading', { name: '等待审核', exact: true }).waitFor(); assert.equal(applyWrites, 1); assert.equal(submitWrites, 1)
    application.status = 2; await page.getByRole('button', { name: '刷新审核状态' }).click(); await page.getByRole('heading', { name: '欢迎加入软件协会', exact: true }).waitFor()
    failApplication = true; await page.goto(base + '/join'); await page.getByRole('alert').filter({ hasText: '测试：加载失败' }).waitFor(); assert.equal(await page.getByRole('button', { name: '申请加入', exact: true }).count(), 0); failApplication = false; await page.getByRole('button', { name: '重新加载' }).click(); await page.locator('.join-content').waitFor()
    roles = [3]; await page.setViewportSize({ width: 390, height: 844 }); await page.goto(base + '/study'); await page.getByRole('button', { name: '我的成绩', exact: true }).click(); await page.getByText('成绩时间线', { exact: false }).waitFor(); await noOverflow(page, 'scores')
    await page.getByRole('button', { name: '成绩统计', exact: true }).click(); await page.locator('.rank-act-card').press('Enter'); const ranking = page.getByRole('dialog'); await ranking.waitFor(); await page.locator('.rank-modal .table tbody tr').first().waitFor(); await withinViewport(ranking, page, 'ranking'); assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden'); await page.getByRole('button', { name: '下一页排名' }).click(); await page.waitForFunction(() => document.querySelector('.rank-modal tbody tr td')?.textContent === '21'); await page.screenshot({ path: `${out}/ranking-mobile.png` }); await page.keyboard.press('Escape'); await ranking.waitFor({ state: 'detached' })
    await page.goto(base + '/activities'); await page.locator('.act-featured').click(); await withinViewport(page.getByRole('dialog'), page, 'activity dialog'); await page.locator('.act-modal__slide img').first().click(); await page.getByRole('dialog', { name: '活动照片预览' }).waitFor(); await page.keyboard.press('Escape'); await page.locator('.act-preview-overlay').waitFor({ state: 'detached' }); assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden'); await page.keyboard.press('Escape'); await page.locator('.act-modal').waitFor({ state: 'detached' }); assert.notEqual(await page.evaluate(() => document.body.style.overflow), 'hidden')
    await page.goto(base + '/notes/1'); await page.locator('.markdown-body pre').first().waitFor(); await noOverflow(page, 'markdown long content'); await page.screenshot({ path: `${out}/note-mobile.png`, fullPage: true })
    await page.getByRole('button', { name: '查看 QQ 群二维码', exact: true }).press('Enter'); await page.getByRole('dialog', { name: '协会交流群' }).waitFor(); await page.keyboard.press('Escape'); await page.getByRole('dialog').waitFor({ state: 'detached' }); assert.equal(await page.locator(':focus').getAttribute('aria-label'), '查看 QQ 群二维码')
    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2, reducedMotion: 'reduce' }); mobile.on('pageerror', error => errors.push(error.message)); await mobile.addInitScript(() => localStorage.setItem('sap_token', 'local-fixture-only')); await mobile.route('**/api/**', mock)
    await mobile.goto(base + '/home'); await mobile.getByRole('button', { name: '切换编程语言' }).tap(); await mobile.getByRole('menuitemradio', { name: 'Python', exact: true }).tap(); assert.equal(await mobile.locator('.editor-file').innerText(), 'hello.py'); await mobile.getByRole('button', { name: '打开导航菜单' }).tap(); await mobile.getByRole('link', { name: /个人信息/ }).tap(); await mobile.getByRole('heading', { name: '个人信息', level: 1 }).waitFor(); await noOverflow(mobile, 'touch profile'); await mobile.getByRole('button', { name: '查看 QQ 群二维码', exact: true }).tap(); await mobile.getByRole('dialog', { name: '协会交流群' }).waitFor(); await withinViewport(mobile.getByRole('dialog'), mobile, 'contact qr'); await mobile.getByRole('button', { name: '关闭二维码预览' }).tap(); await mobile.close()
    await page.goto(base + '/register'); await page.getByPlaceholder('请输入学号', { exact: true }).fill('99000001'); await page.getByPlaceholder('请输入密码', { exact: true }).fill('local-fixture-pass'); await page.getByPlaceholder('请输入真实姓名').fill('测试同学'); await page.getByPlaceholder('请输入QQ号').fill('123456789'); await page.getByRole('button', { name: '立即注册', exact: true }).click(); await page.locator('.captcha-img').waitFor()
    for (const width of [320, 390]) { await page.setViewportSize({ width, height: 844 }); await noOverflow(page, `registration captcha ${width}`); assert((await page.locator('.captcha-input').boundingBox()).width >= 110) }
    await page.goto(base + '/forgot-password'); await page.getByLabel('注册账号（学号）').fill('99000001'); await page.getByLabel('图形验证码', { exact: true }).fill('TEST'); await page.getByRole('button', { name: '发送邮箱验证码', exact: true }).click(); await page.getByRole('heading', { name: '设置新密码', exact: true }).waitFor(); assert.equal(recoverySends, 1)
    for (const width of [320, 390]) { await page.setViewportSize({ width, height: 844 }); await noOverflow(page, `password reset form ${width}`) }
    await page.getByLabel('邮箱验证码', { exact: true }).fill('123456'); await page.getByLabel('新密码', { exact: true }).fill('local-fixture-pass'); await page.getByLabel('再次输入新密码', { exact: true }).fill('local-fixture-pass'); await page.getByRole('button', { name: '验证并重置密码', exact: true }).click(); await page.getByRole('heading', { name: '密码已重置', exact: true }).waitFor(); await noOverflow(page, 'password reset result')
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ checks: 'passed', fullBleed2560: true, languages: heroLanguages.map(item => item.label), joinStates: 4, joinActionsLocalOnly: true, errorRetry: true, mobileDialogs: true, rankingPagination: true, touchNavigationAndQr: true, longContent: true, authHiddenStatesLocalOnly: true, runtimeErrors: 0, screenshots: out }))
  } finally { await browser.close() }
}
main().catch(error => { console.error(error.stack); process.exitCode = 1 })
