// Production check: authenticate, then GET only. Never starts a school collection.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const base = (process.env.SAP_BASE_URL || 'https://csuftsap.top').replace(/\/$/, '')
let token
async function api(path, body) {
  const response = await fetch(base + path, {
    method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...(token ? { 'sap-token': token } : {}) },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000),
  })
  const data = await response.json()
  assert.equal(data.code, 200, `${path}: ${data.message || response.status}`)
  return data.data
}
;(async () => {
  assert(process.env.SAP_ADMIN_ACCOUNT && process.env.SAP_ADMIN_PASSWORD, 'Missing administrator environment')
  token = (await api('/api/auth/admin/login', { studentId: process.env.SAP_ADMIN_ACCOUNT, password: process.env.SAP_ADMIN_PASSWORD })).token
  assert(token)
  const admin = await api('/api/class-schedule/admin'), version = await api('/api/app/version')
  console.log(JSON.stringify({ termCount: admin.terms.length, activeTask: !!admin.activeBatchId, appVersion: version.versionName, appBuild: version.versionCode }))
  if (process.argv.includes('--preflight')) {
    assert(!admin.activeBatchId, 'Wait for the current collection before deployment')
    const logs = await api('/api/class-schedule/admin/logs?limit=200')
    const latest = logs[0]
    assert(!latest || ['SUCCESS', 'FAILED'].includes(latest.status), 'Most recent collection is not terminal; inspect before deployment')
    console.log(JSON.stringify({ preflight: 'passed', latestStatus: latest?.status, latestTime: latest?.startedAt }))
    return
  }
  const batches = await api('/api/class-schedule/admin/batches?page=1&size=5')
  assert(batches.records.length <= 5 && batches.total > 0)
  const checkedBatches = [...batches.records]
  if (batches.total > 5) {
    const next = await api('/api/class-schedule/admin/batches?page=2&size=5')
    assert(!next.records.some(row => batches.records.some(first => first.batchId === row.batchId)))
    checkedBatches.push(...next.records)
  }
  for (const batch of checkedBatches) {
    const keys = batch.sources.map(source => source.term + ':' + source.sourceType)
    assert.equal(keys.length, new Set(keys).size, 'Source results must be deduplicated')
    if (!admin.activeBatchId) assert(['SUCCESS', 'FAILED'].includes(batch.status), 'Historical interrupted task must have a terminal state')
  }
  const completed = await api('/api/class-schedule/admin/batches?page=1&size=5&status=SUCCESS')
  assert(completed.records.every(batch => batch.status === 'SUCCESS'))
  const batch = completed.records[0]
  assert(batch)
  const progress = await api(`/api/class-schedule/admin/pull/progress?batchId=${batch.batchId}`)
  assert.equal(progress.status, 'SUCCESS')
  assert.equal(progress.sources.length, batch.sources.length)
  const history = await api(`/api/class-schedule/admin/pull/history?batchId=${batch.batchId}&page=1&size=3`)
  assert(history.records.length <= 3 && history.total > 0)
  if (history.total > 3) {
    const second = await api(`/api/class-schedule/admin/pull/history?batchId=${batch.batchId}&page=2&size=3`)
    assert(!second.records.some(row => history.records.some(first => first.id === row.id)))
  }
  console.log(JSON.stringify({ apiChecks: 'passed', batches: batches.total, latestCompletedSources: batch.sources.length, historyEvents: history.total, duplicateSourceResults: false }))
  if (!process.env.PLAYWRIGHT_MODULE) return
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE)
  const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  try {
    const output = process.env.UI_OUTPUT || '/tmp/sap-class-schedule-live'
    fs.mkdirSync(output, { recursive: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }), errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(({ token, base }) => { if (location.origin === base) localStorage.setItem('sap-token', token) }, { token, base })
    await page.route('**/api/**', route => route.request().method() === 'GET' ? route.continue() : route.abort())
    await page.goto(base + '/admin/schedule-app')
    await page.getByRole('tab', { name: '班级课表', exact: true }).click()
    const panel = page.locator('.class-panel')
    await panel.locator('.batch-heading').first().waitFor()
    await Promise.all([
      page.waitForResponse(response => response.url().includes('/api/class-schedule/admin/batches') && response.url().includes('status=SUCCESS')),
      panel.locator('.log-toolbar').getByText('已完成', { exact: true }).click(),
    ])
    await panel.locator('.batch-heading').first().click()
    await panel.locator('.source-result').first().waitFor()
    assert.equal(await panel.locator('.history-panel').count(), 0)
    await panel.locator('.collection-records').screenshot({ path: output + '/records.png' })
    // Open the modal while retaining the scrolled viewport, without calling collection.
    await panel.getByRole('button', { name: '立即采集', exact: true }).dispatchEvent('click')
    const modal = page.getByRole('dialog', { name: '开始采集课表' })
    await modal.waitFor()
    await page.waitForFunction(() => !document.querySelector('[class*="enter-active"]'))
    const box = await modal.boundingBox()
    assert(box.y >= 0 && box.y + box.height <= 1000 && Math.abs(box.y + box.height / 2 - 500) < 3)
    assert.equal(await modal.locator('.el-select').count(), 1)
    await page.screenshot({ path: output + '/setup-scrolled.png' })
    await modal.getByRole('button', { name: '取消', exact: true }).click()
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ browserChecks: 'passed', centeredInScrolledViewport: true, writesAttempted: 0, screenshots: output }))
  } finally { await browser.close() }
})().catch(error => { console.error(error.message); process.exitCode = 1 })
