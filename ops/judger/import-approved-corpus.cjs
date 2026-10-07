// Explicitly authorized bulk import. Publish only after the application's three-round validation.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, 'problem-packs');
const session = JSON.parse(fs.readFileSync(path.join(require('node:os').homedir(), 'Library/Caches/sap-oj-test-session.json')));
const original = process.argv.includes('--original');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'manifest.json')));
const files = original
  ? ['a', 'b'].flatMap(group => fs.readdirSync(path.join(root, 'beginner-original', group), {withFileTypes: true})
      .filter(entry => entry.isDirectory() && fs.existsSync(path.join(root, 'beginner-original', group, entry.name, 'pack.json')))
      .map(entry => path.join(root, 'beginner-original', group, entry.name, 'pack.json'))).sort()
  : manifest.items.map(item => path.join(root, item.status === 'PILOT_OWNED_BY_SEPARATE_AGENT' ? item.slug + '/pack.json' : item.file));
const packs = files.map(file => ({file, pack: JSON.parse(fs.readFileSync(file))}));
assert.equal(packs.length, original ? 20 : 50);
assert.equal(new Set(packs.map(item => item.pack.slug)).size, packs.length);
const reportPath = path.join(__dirname, original ? 'original-import-production.json' : 'classic-import-production.json');
const report = {startedAt: new Date().toISOString(), environment: session.base, rounds: 3, results: [], passed: false};
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
function save() { fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n'); }
async function api(route, method = 'GET', body) {
  const response = await fetch(session.base + route, {
    method, headers: {'sap-token': session.token, 'Content-Type': 'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000),
  });
  const result = await response.json();
  if (result.code !== 200) {const error = Error(route + ': ' + result.message); error.code = result.code; throw error;}
  return result.data;
}
async function allRows() {
  let rows = [], page = 1, data;
  do { data = await api('/api/admin/oj/problems?size=50&page=' + page++); rows.push(...data.records); } while (rows.length < data.total);
  return rows;
}
async function waitForJob(id) {
  for (let attempt = 0; attempt < 1800; attempt++) {
    let data;
    try { data = await api('/api/admin/oj/jobs/' + id); } catch (e) {if (e.code) throw e; await delay(3000); continue;}
    if (!['RUNNING', 'QUEUED'].includes(data.status)) return data;
    await delay(1000);
  }
  throw Error('Validation timed out: ' + id);
}
async function enqueue(id) {
  for (let attempt = 0; attempt < 900; attempt++) {
    try { return await api('/api/admin/oj/problems/' + id + '/validate', 'POST'); }
    catch (e) {if (![429, 503].includes(e.code)) throw e; await delay(2000);}
  }
  throw Error('No available judge resource: ' + id);
}
async function main() {
  const before = await allRows();
  for (const {file, pack} of packs) {
    const started = Date.now();
    let row = before.find(item => item.slug === pack.slug);
    const preservedExisting = Boolean(row);
    if (!row) {row = await api('/api/admin/oj/problems', 'POST', pack); before.push({...row, slug: pack.slug});}
    const detail = await api('/api/admin/oj/problems/' + row.id);
    // Existing published pilots are preserved. Do not replace a live problem silently.
    const ordered = value => Array.isArray(value) ? value.map(ordered)
        : value && typeof value === 'object' ? Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, child]) => [key, ordered(child)])) : value;
    const canonical = value => JSON.stringify(ordered(value));
    // ProblemPack deliberately accepts judging fields only; provenance metadata remains in the local bundle.
    const dtoKeys = ['slug','title','difficulty','description','inputFormat','outputFormat','constraints','sourcePlatform','sourceUrl','sourceId','sourceNote','tags','modes','defaultMode','checker','profiles','cases','references'];
    const projected = Object.fromEntries(dtoKeys.map(key => [key, pack[key]]));
    projected.profiles = Object.fromEntries(Object.entries(pack.profiles).map(([key, profile]) => [key,
      Object.fromEntries(['starterStdio','starterFunction','functionDriver'].map(field => [field, profile[field] || '']))]));
    const localPackMatches = canonical(detail.pack) === canonical(projected);
    if (preservedExisting && !detail.validated && !localPackMatches) throw Error('Unvalidated existing problem differs: ' + pack.slug);
    let result;
    if (!detail.validated) {
      const queued = detail.validationJobId ? await api('/api/admin/oj/jobs/' + detail.validationJobId) : null;
      const job = queued && ['QUEUED', 'RUNNING'].includes(queued.status) ? queued : await enqueue(row.id);
      result = await waitForJob(job.id);
      if (result.status !== 'AC') {
        report.failedProblem = {slug: pack.slug, jobId: job.id, status: result.status, result: result.result}; save();
        throw Error(pack.slug + ' failed validation: ' + result.status);
      }
    } else if (detail.validationJobId) result = await waitForJob(detail.validationJobId);
    assert(result && result.status === 'AC', 'Missing successful validation: ' + pack.slug);
    if (detail.status !== 'PUBLISHED') await api('/api/admin/oj/problems/' + row.id + '/status', 'PUT', {status: 'PUBLISHED'});
    const entry = {slug: pack.slug, id: row.id, title: detail.pack.title, languageCount: Object.keys(detail.pack.references).length,
      modes: detail.pack.modes, cases: detail.pack.cases.length, jobId: result.id, status: result.status,
      passedCases: result.passedCases, totalCases: result.totalCases,
      localFileSha256: crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),
      storedPackSha256: crypto.createHash('sha256').update(canonical(detail.pack)).digest('hex'),
      localPackMatches, preservedExisting, elapsedSeconds: Math.round((Date.now() - started) / 1000)};
    report.results.push(entry); save(); console.log(JSON.stringify({event: 'published', ...entry}));
  }
  report.passed = true; report.finishedAt = new Date().toISOString(); save();
  console.log(JSON.stringify({event: 'finished', passed: true, problems: report.results.length}));
}
main().catch(error => {report.error = error.message; save(); console.error(error.message); process.exitCode = 1;});
