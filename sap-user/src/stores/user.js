import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  const user = ref(null)
  const token = ref(localStorage.getItem('sap_token') || '')
  const roles = ref([])

  async function login(studentId, password) {
    const res = await request.post('/api/auth/login', { studentId, password })
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('sap_token', token.value)
    return res
  }

  async function register(form) {
    return await request.post('/api/auth/register', form)
  }

  async function fetchUserInfo() {
    const res = await request.get('/api/auth/info')
    user.value = res.data.user
    roles.value = res.data.roles || []
    return res
  }

  async function updateProfile(data) {
    return await request.put('/api/auth/profile', data)
  }

  function logout() {
    request.post('/api/auth/logout').catch(() => {})
    token.value = ''
    user.value = null
    roles.value = []
    localStorage.removeItem('sap_token')
  }

  return { user, token, roles, login, register, fetchUserInfo, updateProfile, logout }
})
