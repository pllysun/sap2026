<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="zen-circle"></div>
      <div class="zen-circle zen-circle-2"></div>
    </div>

    <div class="login-card zen-fade-in">
      <div class="login-header">
        <h1 class="login-title">软件协会</h1>
        <p class="login-subtitle">管理平台</p>
        <div class="zen-divider"></div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @submit.prevent="handleLogin">
        <el-form-item prop="studentId">
          <el-input
            v-model="form.studentId"
            placeholder="学号"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            size="large"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-footer">仅管理员可登录</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { adminLogin } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  studentId: '',
  password: ''
})

const rules = {
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
  } catch { return }

  loading.value = true
  try {
    const res = await adminLogin(form)
    localStorage.setItem('sap-token', res.data.token)
    localStorage.setItem('sap-user', JSON.stringify(res.data.user))
    ElMessage.success('欢迎回来')
    const redirect = route.query.redirect
    // 仅允许站内路径（以 / 开头且非 //），避免畸形或外站跳转
    const safeRedirect = typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/'
    router.push(safeRedirect)
  } catch (e) {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>


