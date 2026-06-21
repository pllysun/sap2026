/**
 * 后端 API（对接 sap-backend 的 /api/auth 与 /api/jw）。
 * 每个函数返回 Promise<{code,message,data}>，调用方按 code===200 判定成功，
 * 绑定相关需读 data.needCaptcha / data.needMfa。
 */
const r = require('./request')

const qs = (obj) => {
  const parts = []
  Object.keys(obj || {}).forEach((k) => { if (obj[k] != null && obj[k] !== '') parts.push(k + '=' + encodeURIComponent(obj[k])) })
  return parts.length ? '?' + parts.join('&') : ''
}

module.exports = {
  // ---- 鉴权 ----
  ping: () => r.get('api/ping', null, { timeout: 3000 }),
  appLogin: (studentId, password) => r.post('api/auth/app/login', { studentId, password }),
  me: () => r.get('api/auth/info'),
  meLight: () => r.get('api/auth/info/light'),
  logout: () => r.post('api/auth/logout'),
  updateProfile: (body) => r.put('api/auth/profile', body),

  // ---- 教务绑定 ----
  jwStatus: () => r.get('api/jw/status'),
  jwAccounts: () => r.get('api/jw/accounts'),
  jwBind: (account, password) => r.post('api/jw/bind', { account, password }),
  jwBindCaptcha: (challengeId, code) => r.post('api/jw/bind/captcha', { challengeId, code }),
  jwBindMfa: (challengeId, code) => r.post('api/jw/bind/mfa', { challengeId, code }),
  jwBindMfaResend: (challengeId, code) => r.post('api/jw/bind/mfa/resend', { challengeId, code }),
  jwUnbind: (account) => r.del('api/jw/unbind' + qs({ account })),

  // ---- 教务数据 ----
  schedule: (account, term) => r.get('api/jw/schedule' + qs({ account, term })),
  terms: (account) => r.get('api/jw/terms' + qs({ account })),
  grades: (account) => r.get('api/jw/grades' + qs({ account })),
  exams: (account, term) => r.get('api/jw/exams' + qs({ account, term })),
  evalList: (account, term) => r.get('api/jw/eval/list' + qs({ account, term })),
  evalAuto: (body) => r.post('api/jw/eval/auto', body),
}
