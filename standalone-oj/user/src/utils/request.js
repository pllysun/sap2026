import axios from 'axios'

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

// 防止 401 重复跳转
let isRedirecting = false

// 401 跳转登录：跳转后复位标志，避免后续 401 不再跳转
function redirectToLogin() {
  localStorage.removeItem('sap_token')
  if (!isRedirecting) {
    isRedirecting = true
    window.location.href = '/login'
    isRedirecting = false
  }
}

// 请求拦截器 - 添加 token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('sap_token')
  if (token) {
    config.headers['sap-token'] = token
  }
  return config
})

// 响应拦截器 - 统一错误处理
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      // 401 未授权：静默跳转登录，不弹错误提示
      if (res.code === 401) {
        redirectToLogin()
        return Promise.reject(new Error(''))
      }
      // 非 401 失败：统一记录日志便于排查
      console.error('[request] 业务失败:', res.code, res.message, response.config?.url)
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    if (error.response && error.response.status === 401) {
      redirectToLogin()
      return Promise.reject(new Error(''))
    }
    // 非 401 错误：统一记录日志便于排查
    console.error('[request] 请求异常:', error.config?.url, error.message)
    return Promise.reject(error)
  }
)

export default request
