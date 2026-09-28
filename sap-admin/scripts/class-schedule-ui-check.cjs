// 只使用本地 fixture，不请求教务系统、不修改线上数据。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const base = process.env.UI_BASE || 'http://127.0.0.1:3191'
const output = process.env.UI_OUTPUT || '/tmp/sap-class-schedule-ui'
const term = '2026-2027-1'
const sourceKeys = ['class', 'teacher', 'room', 'course']
const oldBatch = '11111111-1111-4111-a111-111111111111'
const doneSources = sourceKeys.map((sourceType, index) => ({ id: 10 + index, term, sourceType, status: 'SUCCESS', phase: 'COMPLETE', rowCount: 3450 + index, completedPages: 3, totalPages: 3, reportedRows: 3500 }))
const old = { batchId: oldBatch, status: 'SUCCESS', term, actorName: '测试管理员', triggerType: 'MANUAL', startedAt: '2026-09-28T10:00:00', sources: doneSources, totalRows: 13806, eventCount: 25, totalTerms: 1, completedTerms: 1 }
const terms = Array.from({ length: 7 }, (_, index) => ({ value: `${2026 - index}-${2027 - index}-1`, classCount: 37000, teacherCount: 13000, roomCount: 10000, courseCount: 11000, semesterStartDate: '2026-09-07' }))
;(async () => {
  fs.mkdirSync(output, { recursive: true })
  const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = [], submitted = []
    let activeBatchId = '', current = null, mfaCalls = 0
    const reads = { summary: 0, calendar: 0, batches: 0, history: 0 }
    let calendarDate = '2026-09-07', historyMessage = '第 1 页解析完成'
    let failNextSummary = false, failNextCalendar = false
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => localStorage.setItem('sap-token', 'class-schedule-fixture'))
    await page.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url())
      if (url.origin !== base) return route.abort()
      if (!url.pathname.startsWith('/api/')) return route.continue()
      let data = {}
      if (url.pathname === '/api/auth/info') data = { user: { name: '测试管理员' }, roles: [0] }
      if (url.pathname === '/api/app/feedback/admin/summary') data = { usageUsers: 1, openIssues: 0, closedIssues: 0 }
      if (url.pathname === '/api/app/feedback/admin/issues') data = { records: [], total: 0 }
      if (url.pathname === '/api/academic-calendar/admin') {
        reads.calendar++
        if (failNextCalendar) { failNextCalendar = false; return route.fulfill({ status: 503, json: { code: 503, message: 'fixture: 暂时不可用' } }) }
        data = terms.map(item => ({ term: item.value, semesterStartDate: calendarDate, source: 'MANUAL' }))
      }
      if (url.pathname === '/api/class-schedule/admin') {
        reads.summary++
        if (failNextSummary) { failNextSummary = false; return route.fulfill({ status: 503, json: { code: 503, message: 'fixture: 暂时不可用' } }) }
        data = { terms, activeBatchId }
      }
      if (url.pathname === '/api/class-schedule/admin/batches') { reads.batches++; data = { records: [old], total: 1, stats: { total: 1, success: 1, failed: 0 } } }
      if (url.pathname === '/api/class-schedule/admin/pull/history') { reads.history++; data = { records: [{ id: 12, sourceType: 'class', term, status: 'RUNNING', message: historyMessage, startedAt: old.startedAt }], total: 1 } }
      if (url.pathname === '/api/class-schedule/admin/pull') {
        const body = request.postDataJSON(); submitted.push(body); activeBatchId = body.batchId
        current = { batchId: body.batchId, status: 'RUNNING', term: body.term, startedAt: new Date().toISOString(), totalRows: 1200, totalTerms: body.term ? 1 : 7, completedTerms: 0, eventCount: 3,
          sources: [{ term, sourceType: 'class', phase: 'COLLECT', status: 'RUNNING', completedPages: 1, totalPages: 3, rowCount: 1200, reportedRows: 3500 }],
          latest: { id: 3, term, phase: 'COLLECT', message: '第 1 / 3 页已解析，累计 1200 条' } }
        data = { accepted: true, batchId: body.batchId }
      }
      if (url.pathname === '/api/class-schedule/admin/pull/progress') data = current || {}
      if (url.pathname === '/api/class-schedule/admin/pull/mfa') {
        mfaCalls++; assert.equal(request.postDataJSON().challengeId, 'test-challenge')
        current = { ...current, status: 'RUNNING', needMfa: false, latest: { ...current.latest, id: 10, message: '短信验证通过，继续采集' } }
        data = { accepted: true, batchId: activeBatchId }
      }
      return route.fulfill({ json: { code: 200, data } })
    })
    const settle = () => page.waitForFunction(() => !document.querySelector('[class*="enter-active"], [class*="leave-active"]'))
    const checkBounds = async selector => {
      await settle(); const box = await page.locator(selector).boundingBox(), { height } = page.viewportSize()
      assert(box && box.y >= 0 && box.y + box.height <= height + 1, '弹窗完整位于视口内')
      assert(Math.abs(box.y + box.height / 2 - height / 2) < 3, '弹窗垂直居中')
    }
    await page.goto(base + '/schedule-app?tab=class-schedule')
    await page.getByRole('tab', { name: '班级课表', exact: true }).click()
    const panel = page.locator('.class-panel')
    await panel.getByRole('button', { name: '立即采集', exact: true }).waitFor()
    assert.equal(await panel.locator('.toolbar .el-select').count(), 0, '主页面不保留采集范围')
    await panel.locator('.batch-heading').click()
    assert.equal(await panel.locator('.source-result').count(), 4, '每个来源只显示一条最终记录')
    assert.equal(await panel.locator('.history-panel').count(), 0, '历史过程默认隐藏')
    await panel.getByRole('button', { name: /查看历史过程/ }).click()
    await panel.locator('.history-panel').getByText('过程记录', { exact: true }).waitFor()
    await page.screenshot({ path: output + '/records.png' })
    await panel.getByRole('button', { name: '立即采集', exact: true }).click()
    let setup = page.getByRole('dialog', { name: '开始采集课表' })
    await setup.waitFor(); await checkBounds('.class-dialog:visible')
    await setup.locator('.el-select').click()
    await page.getByRole('option', { name: term, exact: true }).click()
    await setup.getByPlaceholder('请输入学校教务账号').fill('fixture-account')
    await setup.getByPlaceholder('请输入教务密码').fill('fixture-password')
    await page.screenshot({ path: output + '/setup.png' })
    await setup.getByRole('button', { name: '开始采集', exact: true }).click()
    let progress = page.getByRole('dialog', { name: '班级课表采集进度' })
    await progress.waitFor(); await setup.waitFor({ state: 'hidden' })
    await progress.locator('.total-counter strong').getByText('1,200', { exact: true }).waitFor()
    assert.equal(submitted[0].term, term)
    assert.equal(await progress.locator('.source-progress').count(), 4)
    assert.equal(await progress.locator('.page-counter b').first().textContent(), '1')
    await checkBounds('.progress-dialog:visible')
    current.sources[0] = { ...current.sources[0], completedPages: 2, rowCount: 2300 }
    current.totalRows = 2300; current.latest = { ...current.latest, id: 4, message: '第 2 / 3 页已解析，累计 2300 条' }
    await progress.locator('.total-counter strong').getByText('2,300', { exact: true }).waitFor()
    assert.equal(await progress.locator('.page-counter b').first().textContent(), '2')
    await page.screenshot({ path: output + '/progress.png' })
    await progress.getByRole('button', { name: '在后台继续' }).click()
    await progress.waitFor({ state: 'hidden' })
    failNextSummary = true
    await page.reload()
    await page.getByRole('tab', { name: '班级课表', exact: true }).click()
    await panel.getByText('部分页面数据暂未更新，将自动重试，无需手动刷新。', { exact: true }).waitFor()
    await panel.getByRole('button', { name: '查看采集进度', exact: true }).click()
    await progress.waitFor()
    await progress.locator('.total-counter strong').getByText('2,300', { exact: true }).waitFor()
    current = { ...current, status: 'PENDING', needMfa: true, challengeId: 'test-challenge', phone: '138****0000', latest: { id: 9, phase: 'AUTH', message: '等待短信二次验证' } }
    const verify = page.getByRole('dialog', { name: '短信二次验证' })
    await verify.waitFor(); await progress.waitFor({ state: 'hidden' })
    await verify.getByPlaceholder('请输入短信验证码').fill('123456')
    await verify.getByRole('button', { name: '验证并继续' }).click()
    await progress.waitFor(); await verify.waitFor({ state: 'hidden' })
    assert.equal(mfaCalls, 1)
    // 打开历史后，在完成采集时刷新概览、校历、批次、历史四块数据。
    await progress.getByRole('button', { name: '在后台继续' }).click()
    await progress.waitFor({ state: 'hidden' })
    await panel.locator('.batch-heading').click()
    await panel.getByRole('button', { name: /查看历史过程/ }).click()
    await panel.locator('.history-panel').getByText('第 1 页解析完成', { exact: true }).waitFor()
    await panel.getByRole('button', { name: '查看采集进度', exact: true }).click()
    await progress.waitFor()
    const beforeComplete = { ...reads }
    calendarDate = '2026-09-14'; terms[0].classCount = 45678; historyMessage = '自动刷新后的历史过程'
    failNextCalendar = true
    current = { ...current, status: 'SUCCESS', sources: doneSources, totalRows: 13806, completedTerms: 1, finishedAt: new Date().toISOString(), latest: { id: 20, phase: 'COMPLETE', message: '采集完成' } }
    activeBatchId = ''
    await progress.getByText('课表已更新', { exact: true }).waitFor()
    await progress.locator('.total-counter strong').getByText('13,806', { exact: true }).waitFor()
    for (const key of Object.keys(reads)) assert(reads[key] > beforeComplete[key], `采集完成后自动重取 ${key}`)
    await panel.locator('.term-card').first().getByText('班级 45,678 条', { exact: true }).waitFor()
    await panel.locator('.calendar-panel').getByText('2026-09-14', { exact: true }).first().waitFor()
    await panel.getByText('部分页面数据暂未更新，将自动重试，无需手动刷新。', { exact: true }).waitFor({ state: 'hidden' })
    await panel.locator('.history-panel').getByText('自动刷新后的历史过程', { exact: true }).waitFor()
    await page.setViewportSize({ width: 390, height: 740 }); await checkBounds('.progress-dialog:visible')
    await page.screenshot({ path: output + '/progress-mobile.png' })
    await progress.getByRole('button', { name: '完成', exact: true }).click()
    await progress.waitFor({ state: 'hidden' })
    await panel.getByRole('button', { name: '立即采集', exact: true }).click()
    await setup.waitFor(); await setup.locator('.el-select').click()
    await page.getByRole('option', { name: '全部学期', exact: true }).click()
    await setup.getByPlaceholder('请输入学校教务账号').fill('fixture-account')
    await setup.getByPlaceholder('请输入教务密码').fill('fixture-password')
    await setup.getByRole('button', { name: '开始采集', exact: true }).click()
    await progress.waitFor(); assert.equal(submitted[1].term, null)
    assert.deepEqual(errors, [])
    console.log('PASS: 最终记录去重、历史展开、单学期/全学期、视口居中、独立进度弹窗、逐页计数动画、后台恢复、短信续验、完成后全量刷新、刷新失败自动恢复、移动端布局')
  } catch (error) {
    const page = browser.contexts()[0]?.pages()[0]
    if (page) await page.screenshot({ path: output + '/failure.png' })
    throw error
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
