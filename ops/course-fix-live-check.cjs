// Read-only deployment checks (apart from obtaining a login session and audit logs).
// Credentials are environment-only. Does not send emails, collect from school, or edit issues.
const assert = require('node:assert/strict')
const base = process.env.SAP_BASE_URL || 'https://csuftsap.top'
let token = process.env.SAP_ADMIN_TOKEN
async function call(path, body, anonymous = false) {
  const response = await fetch(base + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', ...(!anonymous && token ? { 'sap-token': token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(30000),
  })
  return response.json()
}
async function data(path, body) {
  const result = await call(path, body)
  assert.equal(result.code, 200, `${path}: ${result.code} ${result.message || ''}`)
  return result.data
}
;(async () => {
  for (const path of ['/api/app/feedback/admin/issues', '/api/app/feedback/admin/issues/1']) {
    const result = await call(path, undefined, true)
    assert.notEqual(result.code, 200, 'Anonymous admin access must be denied')
  }
  if (!token) {
    assert(process.env.SAP_ADMIN_ACCOUNT && process.env.SAP_ADMIN_PASSWORD, 'Provide administrator credentials')
    const login = await data('/api/auth/admin/login', {
      studentId: process.env.SAP_ADMIN_ACCOUNT, password: process.env.SAP_ADMIN_PASSWORD,
    })
    token = login.token
    assert(token)
  }
  const list = await data('/api/app/feedback/admin/issues?size=3')
  assert(list.records.length, 'Need an existing issue for read-only comparison')
  for (const row of list.records) {
    assert(Object.hasOwn(row, 'reporterAccount') && Object.hasOwn(row, 'reporterQq'))
    const detail = await data(`/api/app/feedback/admin/issues/${row.id}`)
    assert.equal(detail.reporterAccount, row.reporterAccount)
    assert.equal(detail.reporterQq, row.reporterQq)
    const app = await data(`/api/app/feedback/issues/${row.id}`)
    assert(!Object.hasOwn(app, 'reporterAccount') && !Object.hasOwn(app, 'reporterQq'))
  }
  const terms = await data('/api/class-schedule/terms')
  assert(terms.length)
  const classes = await data('/api/class-schedule/classes?term=' + encodeURIComponent(terms[0].value))
  assert(classes.length)
  const selections = classes.slice(0, 2).map((c, i) => ({ ...c, key: `deployment-check-${i}`, revision: null }))
  const first = await data('/api/class-schedule/sync', { selections, force: false })
  assert(first.changed && first.items.length === selections.length)
  assert(first.items.every(i => i.data && /^[a-f0-9]{64}$/.test(i.revision)))
  const current = selections.map(s => ({ ...s, revision: first.items.find(i => i.key === s.key).revision }))
  const same = await data('/api/class-schedule/sync', { selections: current, force: false })
  assert.equal(same.changed, false)
  assert(same.items.every(i => i.data == null))
  const changed = await data('/api/class-schedule/sync', {
    selections: current.map((s, i) => i ? s : { ...s, revision: '0'.repeat(64) }), force: false,
  })
  assert(changed.changed && changed.items.every(i => i.data != null))
  console.log(JSON.stringify({ adminContactRowsVerified: list.records.length,
    appContactFieldsAbsent: true, anonymousAdminDenied: true,
    classSyncSelections: selections.length, unchangedSkipped: true, oneChangedReturnsEntireBatch: true }))
})().catch(error => { console.error(error.message); process.exitCode = 1 })
