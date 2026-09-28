// Read-only deployment smoke check. Does not change cloud settings or collect schedules.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = 'https://csuftsap.top';
const account = process.env.SAP_TEST_ACCOUNT || process.env.SAP_ADMIN_ACCOUNT;
if (!account) throw new Error("Set SAP_TEST_ACCOUNT or SAP_ADMIN_ACCOUNT in private local configuration");
let token;
async function api(path, body, authenticated = true) {
  const response = await fetch(base + path, {
    method: body ? 'POST' : 'GET',
    headers: {'Content-Type':'application/json', ...(authenticated && token ? {'sap-token':token} : {})},
    body: body ? JSON.stringify(body) : undefined, signal:AbortSignal.timeout(30000),
  });
  return response.json();
}
(async () => {
  assert(process.env.SAP_TEST_PASSWORD);
  for (const path of ['/api/class-schedule/terms','/api/academic-calendar']) {
    const result = await api(path, null, false);
    assert.notEqual(result.code, 200, 'Anonymous access unexpectedly allowed: '+path);
  }
  const login = await api('/api/auth/app/login', {studentId:account,password:process.env.SAP_TEST_PASSWORD});
  assert.equal(login.code,200,'App login failed'); token=login.data.token; assert(token);
  assert.equal(login.data.appAccessLevel,2); assert(login.data.roles.some(r=>r<=3));
  const cloud=await api('/api/app/cloud/admin'); assert.equal(cloud.code,200);
  const terms=await api('/api/class-schedule/terms'); assert.equal(terms.code,200); assert(terms.data.length>0);
  const calendar=await api('/api/academic-calendar'); assert.equal(calendar.code,200); assert(calendar.data.length>0);
  const info=await api('/api/auth/info/light'); assert.equal(info.code,200);
  const assets='/Volumes/Newsmy/sap-access-1.4.87/admin-dist/assets';
  const name=fs.readdirSync(assets).find(n=>n.startsWith('ScheduleAppView-') && n.endsWith('-access87.js'));
  assert(name); const response=await fetch(base+'/admin/assets/'+name); assert.equal(response.status,200);
  const js=await response.text(); assert.equal(js,fs.readFileSync(assets+'/'+name,'utf8'));
  assert(js.includes('Web、班级课表默认开放')); assert(!js.includes('游客账号无法登录 App'));
  const after=await api('/api/app/cloud/admin'); assert.equal(after.data.guestAccessLevel,cloud.data.guestAccessLevel);
  const result={appLogin:'passed',memberAccessLevel:login.data.appAccessLevel,anonymousProtection:'passed',
    terms:terms.data.length,calendarTerms:calendar.data.length,adminAssets:'identical',guestAccessLevel:cloud.data.guestAccessLevel};
  fs.writeFileSync('/Volumes/Newsmy/sap-access-1.4.87/live-check.json',JSON.stringify(result,null,2));
  console.log(JSON.stringify(result));
})().catch(e=>{console.error(e.message);process.exitCode=1});
