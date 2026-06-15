import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 清理本地登录态（token + 用户信息）
export function clearAuth() {
  localStorage.removeItem('sap-token')
  localStorage.removeItem('sap-user')
}

const request = axios.create({
  baseURL: '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('sap-token')
    if (token) {
      config.headers['sap-token'] = token
    }
    return config
  },
  error => Promise.reject(error)
)

// 401 去重处理：会话失效时常有多个请求并发返回 401，
// 这里保证只提示一次、只跳转一次，且已在登录页时不再弹"过期"提示。
let handling401 = false
function handleUnauthorized() {
  clearAuth()
  if (handling401) return
  handling401 = true
  const cur = router.currentRoute.value
  if (cur.path !== '/login') {
    ElMessage.error('登录已过期，请重新登录')
    router.push({ path: '/login', query: { redirect: cur.fullPath } }).catch(() => {})
  }
  // 跳转完成后复位，便于下次会话失效再次提示
  setTimeout(() => { handling401 = false }, 2000)
}

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401) {
        handleUnauthorized()
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    if (error.response?.status === 401) {
      handleUnauthorized()
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
