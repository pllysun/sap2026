import axios from 'axios'

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

// 防止 401 重复跳转
let isRedirecting = false

// 请求拦截器 - 添加 token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('sap_token')
  if (token) {
    config.headers['satoken'] = token
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
        localStorage.removeItem('sap_token')
        if (!isRedirecting) {
          isRedirecting = true
          window.location.href = '/login'
        }
        return Promise.reject(new Error(''))
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('sap_token')
      if (!isRedirecting) {
        isRedirecting = true
        window.location.href = '/login'
      }
      return Promise.reject(new Error(''))
    }
    return Promise.reject(error)
  }
)

export default request
