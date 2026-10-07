// Acceptance of the registered external node. Uses private cached credentials.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const session = JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const registration = JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-judger-pllysun/registration.json'));
const report = { passed: false, nodeId: registration.id, nodeName: registration.name, checks: [], submissions: [] };
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function api(route, method = 'GET', body) {
  const response = await fetch(session.base + route, { method,
    headers: { 'sap-token': session.token, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000) });
  const result = await response.json();
  assert.equal(Number(result.code), 200, route + ': ' + result.message);
  return result.data;
}
async function state(wanted) {
  for (let i = 0; i < 80; i++) {
    const node = await api(`/api/admin/oj/nodes/${registration.id}/refresh`, 'POST');
    if (node.state === wanted) return node;
    assert.notEqual(node.state, 'ERROR', node.error);
    await sleep(300);
  }
  throw Error('Node state timeout: ' + wanted);
}
async function finished(id) {
  for (let i = 0; i < 250; i++) {
    const job = await api('/api/oj/submissions/' + id);
    if (!['RUNNING', 'QUEUED'].includes(job.status)) return job;
    await sleep(400);
  }
  throw Error('Submission timeout: ' + id);
}
async function idlePool() {
  for (let i = 0; i < 60; i++) {
    const pool = await api('/api/admin/oj/nodes');
    if (Number(pool.capacity) === 9 && Number(pool.availableSlots) === 9) return pool;
    await sleep(300);
  }
  throw Error('Task leases did not return to the idle pool');
}
async function running(id) {
  for (let i = 0; i < 80; i++) {
    const job = await api('/api/oj/submissions/' + id);
    if (job.status === 'RUNNING' && job.nodeName) return job;
    assert(['RUNNING', 'QUEUED'].includes(job.status), job.status);
    await sleep(100);
  }
  throw Error('Job did not begin execution');
}
function tunnel(command) {
  execFileSync(process.execPath, [path.resolve(__dirname, '../server-ssh.cjs'), command],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 25000 });
}
async function main() {
  try {
    await api(`/api/admin/oj/nodes/${registration.id}/start`, 'POST');
    const node = await state('RUNNING');
    assert.equal(Number(node.capacity), 8);
    let pool = await api('/api/admin/oj/nodes');
    assert.equal(Number(pool.capacity), 9);
    assert.equal(Number(pool.online), 2);
    report.checks.push('two online nodes with combined capacity nine; resource weighted scheduling');
    const rows = (await api('/api/oj/problems?size=50')).records;
    const problem = rows.find(row => row.title === '两数之和');
    assert(problem);
    const detail = await api('/api/admin/oj/problems/' + problem.id);
    assert.deepEqual(new Set(detail.pack.modes), new Set(['STDIO', 'FUNCTION']));
    for (const language of ['c', 'cpp', 'java', 'python', 'rust']) {
      for (const mode of ['STDIO', 'FUNCTION']) {
        const code = detail.pack.references[language][mode];
        const submitted = await api('/api/oj/submit', 'POST', { problemId: problem.id, language, mode, code });
        const job = await finished(submitted.id);
        assert.equal(job.status, 'AC', `${language} ${mode} ${job.status}`);
        assert.equal(job.nodeName, registration.name);
        assert.equal(job.code, code);
        assert.equal(Number(job.passedCases), detail.pack.cases.length);
        assert.deepEqual(job.result.cases, []);
        report.submissions.push({ id: job.id, language, mode, status: job.status, nodeName: job.nodeName, passedCases: job.passedCases });
        console.log(JSON.stringify({ language, mode, status: job.status, node: job.nodeName }));
      }
    }
    report.checks.push('five languages times both modes pass full test sets on external node; source preserved and hidden cases withheld');

    const addition = rows.find(row => row.slug === 'luogu-p1001');
    assert(addition);
    const code = 'import time\ntime.sleep(1.5)\na, b = map(int, input().split())\nprint(a + b)\n';
    const submitRun = () => api('/api/oj/run', 'POST', { problemId: addition.id, language: 'python', mode: 'STDIO', code, input: '1 2\n' });
    const interrupted = await submitRun();
    assert.equal((await running(interrupted.id)).nodeName, registration.name);
    await api(`/api/admin/oj/nodes/${registration.id}/stop`, 'POST');
    await state('STOPPED');
    const recovered = await finished(interrupted.id);
    assert.equal(recovered.status, 'AC');
    assert.equal(Number(recovered.attempt), 2);
    assert.equal(recovered.nodeName, '容器内置节点');
    const timeline = (await api(`/api/admin/oj/monitor/${interrupted.id}`)).timeline;
    for (const name of ['NODE_ERROR', 'REASSIGNED', 'FINISHED']) assert(timeline.some(event => event.event === name));
    assert.equal(Number((await api('/api/admin/oj/nodes')).capacity), 1);
    report.failover = { id: interrupted.id, status: recovered.status, attempts: recovered.attempt, finalNode: recovered.nodeName };
    report.checks.push('stopping external engine interrupts task and retries whole task on built-in node with complete timeline');
    await api(`/api/admin/oj/nodes/${registration.id}/start`, 'POST');
    await state('RUNNING');

    // Reconnect the persistent encrypted link while no accepted task is using it.
    tunnel('systemctl restart sap-judger-pllysun-tunnel.service');
    await sleep(1200);
    await state('RUNNING');
    assert.equal((await finished((await submitRun()).id)).status, 'AC');
    report.checks.push('encrypted tunnel reconnects and platform executes again');
    for (const endpoint of ['/api/admin/oj/nodes', '/api/oj-nodes/{id}/heartbeat']) {
      const logs = await api('/api/log/explore?dimension=detail&endpoint=' + encodeURIComponent(endpoint) + '&size=10');
      assert(logs.records.length > 0);
      assert(!JSON.stringify(logs).includes(session.token));
    }
    report.checks.push('management and machine heartbeat present in central operation logs');
    await api('/api/ping');
    pool = await idlePool();
    assert.equal(Number(pool.capacity), 9);
    assert.equal(Number(pool.availableSlots), 9);
    report.finalNodes = pool;
    report.passed = true;
    report.finishedAt = new Date().toISOString();
    console.log(JSON.stringify({ passed: true, capacity: 9, formalSubmissions: report.submissions.length, checks: report.checks }));
  } finally {
    await api(`/api/admin/oj/nodes/${registration.id}/start`, 'POST');
    await state('RUNNING');
    fs.writeFileSync(path.join(__dirname, 'pllysun-node-production.json'), JSON.stringify(report, null, 2) + '\n');
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
