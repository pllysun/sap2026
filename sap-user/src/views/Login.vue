<template>
  <div class="auth-page">
    <!-- Floating decoration particles -->
    <div class="auth-particle auth-particle--1"></div>
    <div class="auth-particle auth-particle--2"></div>
    <div class="auth-particle auth-particle--3"></div>

    <div class="auth-card">
      <div class="text-center mb-4">
        <div class="auth-logo-wrap">
          <img src="/logo.png" alt="CSUFTSAP Logo" class="auth-logo" />
        </div>
        <h1 class="auth-card__title mt-1">CSUFTSAP</h1>
        <p class="auth-card__subtitle">中南林业科技大学软件协会</p>
      </div>

      <div v-if="errorMsg" class="error-text text-center mb-2">{{ errorMsg }}</div>

      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label">学号</label>
          <input v-model="form.studentId" type="text" class="input" placeholder="请输入学号" required />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="form.password" type="password" class="input" placeholder="请输入密码" required />
        </div>
        <button type="submit" class="btn btn--primary btn--full btn--pill" :disabled="loading">
          {{ loading ? '登录中…' : '立即登录' }}
        </button>
      </form>

      <div class="auth-card__footer">
        没有账号？<router-link to="/register">立即注册</router-link>
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
const form = reactive({ studentId: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  loading.value = true; errorMsg.value = ''
  try { await userStore.login(form.studentId, form.password); router.push('/home') }
  catch (e) { errorMsg.value = e.message || '登录失败' }
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
