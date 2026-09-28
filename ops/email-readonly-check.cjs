// Live regression: login + GET only. Does not enqueue mail or modify configuration.
const assert = require('node:assert/strict');
const base = 'https://csuftsap.top';
const account = process.env.SAP_TEST_ACCOUNT || process.env.SAP_ADMIN_ACCOUNT;
if (!account) throw new Error("Set SAP_TEST_ACCOUNT or SAP_ADMIN_ACCOUNT in private local configuration");
let token;
async function api(route, body) {
  const response = await fetch(base + route, {
    method: body ? 'POST' : 'GET',
    headers: {'Content-Type': 'application/json', ...(token ? {'sap-token': token} : {})},
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(30000),
  });
  const result = await response.json();
  assert.equal(result.code, 200, route + ': ' + result.message);
  return result.data;
}
async function all(kind) {
  const records = [];
  for (let page = 1; page <= 100; page++) {
    const data = await api(`/api/email/delivery/${kind}?page=${page}&size=100`);
    records.push(...data.records);
    if (records.length >= data.total) return records;
  }
  throw new Error('Too many audit records for bounded smoke check');
}
(async () => {
  assert(process.env.SAP_TEST_PASSWORD, 'Set SAP_TEST_PASSWORD');
  token = (await api('/api/auth/admin/login', {studentId: account, password: process.env.SAP_TEST_PASSWORD})).token;
  assert(token);
  const templates = await api('/api/email/templates');
  const bindings = await api('/api/email/bindings');
  const definitions = await api('/api/email/hooks');
  assert.equal(definitions.length, 5);
  for (const hook of definitions) {
    const binding = bindings.find(b => b.eventKey === hook.eventKey);
    assert(binding);
    assert.deepEqual(hook.variables, binding.variables);
    assert.deepEqual(hook.parameters.map(p => p.name), hook.variables);
    assert(hook.guidance);
    assert(hook.parameters.every(p => p.label && p.description && p.example));
  }
  assert.equal(definitions.find(h => h.eventKey === 'PASSWORD_CODE').parameters.length, 4);
  console.log('PASS: live code hook definitions, descriptions and examples');
  const before = templates.map(t => [t.id, t.enabled]);
  assert.equal(bindings.length, 5);
  for (const binding of bindings) {
    assert.equal(binding.compatible, true, binding.eventKey);
    const template = templates.find(t => String(t.id) === String(binding.templateId));
    assert(template);
    const variables = [...new Set([...((template.subject || '') + '\n' + (template.htmlContent || '')).matchAll(/\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}/g)].map(m => m[1]))].sort();
    assert.deepEqual(variables, [...binding.variables].sort(), binding.eventKey);
  }
  console.log('PASS: five live bindings have exactly matching parameter sets');
  const raw = await all('logs'), messages = await all('messages');
  const ids = ['59ffd3ef-0a81-4d28-9570-561db8707d10', '490ddb5c-9916-4b7d-b464-ce81a6c599b0'];
  for (const id of ids) {
    const rows = messages.filter(r => r.message_id === id);
    assert.equal(rows.length, 1);
    assert.equal(rows[0].status, 'SUCCESS');
    const history = await api(`/api/email/delivery/logs/${rows[0].id}/history?size=100`);
    assert.deepEqual(history.records.map(r => Number(r.id)), raw.filter(r => r.message_id === id).map(r => Number(r.id)).sort((a,b) => b-a));
    for (const state of ['QUEUED', 'SENDING', 'SUCCESS']) assert(history.records.some(r => r.status === state));
  }
  assert.equal(messages.filter(r => !r.message_id).length, raw.filter(r => !r.message_id).length);
  console.log('PASS: previous two test emails show one SUCCESS row each; full history and system events retained');
  if (process.env.PLAYWRIGHT_MODULE) {
    const {chromium} = require(process.env.PLAYWRIGHT_MODULE);
    const browser = await chromium.launch({executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true});
    try {
      const page = await browser.newPage({viewport: {width: 1500, height: 1050}}), errors = [];
      page.on('pageerror', e => errors.push(e.message));
      await page.addInitScript(t => {if (window === window.top) localStorage.setItem('sap-token', t)}, token);
      // Enforce read-only behavior even if a UI action unexpectedly changes.
      await page.route('**/api/**', route => {
        assert.equal(route.request().method(), 'GET', 'Unexpected browser write');
        return route.continue();
      });
      await page.goto(base + '/admin/email');
      const issue = page.locator('.hook-card').filter({hasText: 'ISSUE_REPLIED'});
      await issue.waitFor();
      await issue.locator('.el-select').click();
      const options = page.locator('.el-select-dropdown:visible .el-select-dropdown__item');
      await options.first().waitFor();
      const eligible = templates.filter(t => {
        const b = bindings.find(b => b.eventKey === 'ISSUE_REPLIED');
        return new Set(t.variables).size === new Set(b.variables).size && t.variables.every(v => b.variables.includes(v));
      });
      assert.equal(await options.count(), eligible.length);
      for (const option of await options.all()) {
        const text = await option.innerText();
        assert(eligible.some(t => t.templateName && text.includes(t.templateName)));
      }
      assert(!(await options.allTextContents()).some(t => t.includes('加入协会')));
      await page.keyboard.press('Escape');
      await page.getByRole('button', {name: '新增模板', exact: true}).click();
      const editor = page.getByRole('dialog', {name: '新增邮件模板'});
      const save = editor.getByRole('button', {name: '保存模板', exact: true});
      assert(await save.isDisabled());
      assert.equal(await editor.getByRole('switch').getAttribute('aria-checked'), 'false');
      for (const hook of definitions) {
        await editor.locator('.el-select').click();
        await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({hasText: hook.title}).click();
        assert.equal(await editor.locator('.contract-parameter').count(), hook.parameters.length);
        for (const parameter of hook.parameters) {
          assert((await editor.locator('.contract-parameters').innerText()).includes(parameter.label));
          assert((await editor.locator('.contract-parameters').innerText()).includes('{{' + parameter.name + '}}'));
        }
        await editor.getByRole('button', {name: '生成基础内容', exact: true}).click();
        if (hook !== definitions[0]) await page.getByRole('button', {name: '替换内容', exact: true}).click();
        await editor.getByText('参数完整，可保存后在代码事件中绑定', {exact: true}).waitFor();
        assert(await save.isEnabled());
        const html = await editor.locator('.html-editor textarea').inputValue();
        assert.deepEqual([...html.matchAll(/\{\{([^}]+)\}\}/g)].map(m => m[1]).sort(), [...hook.variables].sort());
        if (hook.eventKey === 'PASSWORD_CODE') {
          const subject = editor.locator('.el-form-item').filter({has: page.locator('.el-form-item__label').getByText('邮件主题', {exact: true})}).locator('input');
          await subject.fill('{{code}}'); assert(await save.isDisabled());
          await subject.fill('修改密码验证码');
          await editor.locator('.html-editor textarea').fill(html + '{{unknownParameter}}'); assert(await save.isDisabled());
          await editor.locator('.html-editor textarea').fill(html.replace('{{code}}', '')); assert(await save.isDisabled());
          await editor.locator('.html-editor textarea').fill(html); assert(await save.isEnabled());
          await editor.getByRole('button', {name: '预览', exact: true}).click();
          const preview = page.getByRole('dialog', {name: '预览：修改密码验证码'});
          await preview.waitFor();
          assert((await preview.locator('iframe').getAttribute('srcdoc')).includes('123456'));
          await preview.getByRole('button', {name: 'Close this dialog'}).click();
          await preview.waitFor({state: 'hidden'});
          await editor.locator('.el-dialog__body').evaluate(el => el.scrollTop = 0);
          await editor.screenshot({path: '/tmp/sap-mail-hook-editor-online.png'});
        }
      }
      // Close the unsaved draft; never create or enable a live template.
      await editor.getByRole('button', {name: '取消', exact: true}).click();
      await editor.waitFor({state: 'hidden'});
      console.log('PASS: five live editor contracts, scaffold confirmation, password guards and sample preview; draft discarded');
      await page.getByRole('tab', {name: '发送日志', exact: true}).click();
      await page.getByText('最新状态', {exact: true}).waitFor();
      const subject = messages.find(r => r.message_id === ids[1]).subject;
      const row = page.locator('.mail-ops').last().locator('.el-table__body-wrapper .el-table__row').filter({hasText: subject});
      await row.waitFor();
      assert.equal(await row.count(), 1);
      await row.getByText('SMTP 已接受', {exact: true}).waitFor();
      await row.locator('.el-table__expand-icon').click();
      await page.locator('.mail-history').getByText('开始发送', {exact: true}).waitFor();
      assert.equal(await page.locator('.mail-history').getByText('发送中', {exact: true}).count(), 0);
      await page.waitForFunction(() => ![...document.querySelectorAll('.mail-ops .el-loading-mask')].some(e => e.getBoundingClientRect().width > 0));
      await page.locator('.mail-ops').last().screenshot({path: '/tmp/sap-mail-fixes-online.png'});
      assert.deepEqual(errors, []);
      console.log('PASS: live dropdown excludes mismatched templates; latest-state and expanded-history UI verified');
    } finally {await browser.close();}
  }
  assert.deepEqual((await api('/api/email/templates')).map(t => [t.id, t.enabled]), before);
  assert.deepEqual(await api('/api/email/bindings'), bindings);
  const status = await api('/api/email/delivery/status');
  console.log(JSON.stringify({result: 'PASS', queue: status.queue, failed: status.failed, templatesUnchanged: true, emailsSentByCheck: 0}));
})().catch(e => {console.error(e.message); process.exitCode = 1});
