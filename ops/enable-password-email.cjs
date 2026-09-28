// Explicitly enables only the currently bound password-code template. No email is sent.
const assert = require('node:assert/strict');
const base = 'https://csuftsap.top';
const account = process.env.SAP_TEST_ACCOUNT || process.env.SAP_ADMIN_ACCOUNT;
if (!account) throw new Error("Set SAP_TEST_ACCOUNT or SAP_ADMIN_ACCOUNT in private local configuration");
let token;
async function api(path, method = 'GET', body) {
  const res = await fetch(base + path, {method, headers: {'Content-Type':'application/json', ...(token?{'sap-token':token}:{})},
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000)});
  const data = await res.json();
  assert.equal(data.code, 200, `${path}: ${data.message}`);
  return data.data;
}
(async () => {
  assert(process.env.SAP_TEST_PASSWORD, 'Set SAP_TEST_PASSWORD');
  token = (await api('/api/auth/admin/login', 'POST', {studentId:account,password:process.env.SAP_TEST_PASSWORD})).token;
  const before = await api('/api/email/templates');
  const binding = (await api('/api/email/bindings')).find(b=>b.eventKey==='PASSWORD_CODE');
  assert(binding && binding.templateId && binding.compatible, 'Password hook must already have a compatible binding');
  const template = await api('/api/email/templates/' + binding.templateId);
  assert.deepEqual([...template.variables].sort(), ['account','code','expiresInMinutes','name']);
  console.log(JSON.stringify({templateId:template.id, templateName:template.templateName, enabled:template.enabled}));
  if(process.env.ENABLE_PASSWORD_EMAIL !== 'yes') return;
  if(!template.enabled) {
    const body = Object.fromEntries(['templateKey','templateName','subject','htmlContent','description'].map(k=>[k,template[k]]));
    await api('/api/email/templates/'+template.id,'PUT',{...body,enabled:true,eventKey:'PASSWORD_CODE'});
  }
  const after = await api('/api/email/templates');
  for(const old of before) {
    const current=after.find(t=>t.id===old.id);assert(current);
    for(const key of ['templateKey','templateName','subject','htmlContent','description']) assert.deepEqual(current[key],old[key]);
    assert.equal(current.enabled,old.id===template.id ? true : old.enabled);
  }
  assert.deepEqual((await api('/api/email/bindings')).find(b=>b.eventKey==='PASSWORD_CODE').templateId,binding.templateId);
  console.log('PASS: password-code template enabled, content/bindings/other template states preserved; no email sent.');
})().catch(e=>{console.error(e.message);process.exitCode=1});
