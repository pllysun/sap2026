/**
 * wx.request 封装：注入 sap-token 头、统一解包后端 Result{code,message,data}、登录失效跳转。
 */
const { BASE_URL, STORAGE } = require('../config')

let redirecting = false

function getToken() {
  try { return wx.getStorageSync(STORAGE.TOKEN) || '' } catch (e) { return '' }
}

function toLogin() {
  if (redirecting) return
  redirecting = true
  try {
    wx.removeStorageSync(STORAGE.TOKEN)
    wx.removeStorageSync(STORAGE.MEMBER)
  } catch (e) {}
  wx.reLaunch({
    url: '/pages/login/login',
    complete: () => { setTimeout(() => { redirecting = false }, 800) },
  })
}

function friendly(errMsg) {
  if (!errMsg) return '请求失败'
  if (errMsg.indexOf('timeout') >= 0) return '连接超时，请稍后重试'
  if (errMsg.indexOf('fail') >= 0) return '无法连接服务器，请检查网络或后端地址'
  return errMsg
}

/**
 * 发起请求。
 * @returns Promise<{code,message,data}>（HTTP 成功即 resolve，由调用方按 code 判定业务成败）
 * 登录失效(code=401)会自动清 token 并跳登录，同时 reject。
 */
function request(options) {
  const { url, method = 'GET', data, header = {}, timeout } = options
  const token = getToken()
  if (token) header['sap-token'] = token
  const full = /^https?:\/\//.test(url) ? url : BASE_URL.replace(/\/$/, '') + '/' + url.replace(/^\//, '')

  return new Promise((resolve, reject) => {
    wx.request({
      url: full,
      method,
      data,
      header,
      timeout: timeout || 60000,
      success(res) {
        const body = res.data
        // HTTP 层非 2xx
        if (res.statusCode >= 500) { reject(new Error('服务器异常(' + res.statusCode + ')')); return }
        if (res.statusCode === 401 || (body && body.code === 401)) {
          toLogin()
          reject(new Error('登录已失效，请重新登录'))
          return
        }
        if (body && typeof body === 'object') resolve(body)
        else resolve({ code: res.statusCode === 200 ? 200 : res.statusCode, message: '', data: body })
      },
      fail(err) { reject(new Error(friendly(err && err.errMsg))) },
    })
  })
}

/** 取 data；code!=200 抛错（message 为提示）。 */
async function fetchData(options) {
  const r = await request(options)
  if (r.code === 200) return r.data
  const e = new Error(r.message || '请求失败')
  e.code = r.code
  e.data = r.data
  throw e
}

const get = (url, data, opts) => request(Object.assign({ url, method: 'GET', data }, opts))
const post = (url, data, opts) => request(Object.assign({ url, method: 'POST', data }, opts))
const put = (url, data, opts) => request(Object.assign({ url, method: 'PUT', data }, opts))
const del = (url, data, opts) => request(Object.assign({ url, method: 'DELETE', data }, opts))

module.exports = { request, fetchData, get, post, put, del, getToken, toLogin }
