<template>
  <div class="auth-page">
    <!-- Floating decoration particles -->
    <div class="auth-particle auth-particle--1"></div>
    <div class="auth-particle auth-particle--2"></div>
    <div class="auth-particle auth-particle--3"></div>

    <div class="auth-card" style="max-width: 460px;">
      <div class="text-center mb-4">
        <div class="auth-logo-wrap">
          <img src="/logo.png" alt="CSUFTSAP Logo" class="auth-logo" />
        </div>
        <h1 class="auth-card__title mt-1">创建账号</h1>
        <p class="auth-card__subtitle">加入中南林业科技大学软件协会</p>
      </div>

      <div v-if="errorMsg" class="error-text text-center mb-2">{{ errorMsg }}</div>
      <div v-if="successMsg" class="success-text text-center mb-2">{{ successMsg }}</div>

      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <label class="form-label">学号</label>
          <input v-model="form.studentId" type="text" class="input" placeholder="请输入学号" required />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="form.password" type="password" class="input" placeholder="请输入密码" required />
        </div>
        <div class="form-group">
          <label class="form-label">姓名</label>
          <input v-model="form.name" type="text" class="input" placeholder="请输入真实姓名" required />
        </div>
        <div class="form-group">
          <label class="form-label">性别</label>
          <select v-model="form.gender" class="select">
            <option :value="1">男</option>
            <option :value="0">女</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">QQ号</label>
          <input v-model="form.qq" type="text" class="input" placeholder="请输入QQ号" required />
        </div>
        <button type="submit" class="btn btn--primary btn--full btn--pill" :disabled="loading">
          {{ loading ? '注册中…' : '立即注册' }}
        </button>
      </form>

      <div class="auth-card__footer">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const form = reactive({ studentId: '', password: '', name: '', gender: 1, qq: '' })
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

async function handleRegister() {
  loading.value = true; errorMsg.value = ''; successMsg.value = ''
  try { await userStore.register(form); successMsg.value = '注册成功，正在跳转…'; setTimeout(() => router.push('/login'), 1200) }
  catch (e) { errorMsg.value = e.message || '注册失败' }
  finally { loading.value = false }
}
</script>

<style scoped>
.auth-logo-wrap {
  display: inline-flex; align-items: center; justify-content: center;
  animation: iconBounce 2s ease-in-out infinite;
}
.auth-logo {
  width: 72px; height: 72px; object-fit: contain;
  border-radius: 16px;
}
@keyframes iconBounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.auth-particle {
  position: absolute; border-radius: 50%; pointer-events: none; z-index: 0;
}
.auth-particle--1 {
  width: 8px; height: 8px; background: var(--teal); opacity: 0.3;
  top: 20%; left: 15%; animation: particleDrift 6s ease-in-out infinite;
}
.auth-particle--2 {
  width: 6px; height: 6px; background: var(--primary); opacity: 0.25;
  top: 60%; right: 20%; animation: particleDrift 8s ease-in-out infinite reverse;
}
.auth-particle--3 {
  width: 10px; height: 10px; background: var(--purple); opacity: 0.2;
  bottom: 25%; left: 30%; animation: particleDrift 7s ease-in-out infinite 2s;
}
@keyframes particleDrift {
  0%, 100% { transform: translate(0, 0); }
  25% { transform: translate(20px, -15px); }
  50% { transform: translate(-10px, -25px); }
  75% { transform: translate(15px, 10px); }
}
</style>
