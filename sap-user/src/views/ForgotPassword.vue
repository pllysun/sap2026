<template>
  <main class="recovery-page">
    <section class="recovery-card">
      <img src="/logo.png" alt="软件协会" width="52" height="52" />
      <h1>找回密码</h1>
      <p class="subtitle">使用注册时填写的 QQ 邮箱，找回软件协会账号。</p>
      <div v-if="done" class="success" role="status">
        <h2>密码已重置</h2>
        <p>请使用新密码重新登录，其他设备的旧登录已失效。若仍提示密码不正确，请联系管理员。</p>
        <a :href="loginUrl" class="btn btn--primary btn--full">返回登录</a>
        <p v-if="isApp">在 App 中操作的同学，请返回软协课表重新登录。</p>
      </div>
      <template v-else>
        <p class="notice">验证码只发送到账号资料中的 QQ 邮箱，不支持在此更换收件人。QQ 填写有误、收不到邮件或密码仍不正确，请联系软件协会管理员核实。</p>
        <form @submit.prevent="sendCode">
          <label for="recovery-account">注册账号（学号）</label>
          <input id="recovery-account" v-model.trim="account" class="input" autocomplete="username" maxlength="20" required :disabled="busy" @input="clearChallenge" />
          <label for="recovery-captcha">图形验证码</label>
          <div class="captcha-row">
            <input id="recovery-captcha" v-model.trim="captcha" class="input" maxlength="8" autocomplete="off" required :disabled="busy" />
            <button type="button" class="captcha-button" :disabled="busy || captchaLoading" aria-label="刷新图形验证码" @click="loadCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="图形验证码，点击刷新" width="120" height="40" />
              <span v-else>{{ captchaLoading ? '加载中…' : '点击加载' }}</span>
            </button>
          </div>
          <button class="btn btn--primary btn--full" type="submit" :disabled="busy || captchaLoading || remaining > 0 || !captchaId">
            {{ sending ? '正在申请…' : remaining > 0 ? `${remaining} 秒后可再次发送` : requestId ? '重新发送验证码' : '发送邮箱验证码' }}
          </button>
          <p class="small">点击发送才会发邮件。每 3 分钟可申请一次，同一账号 / QQ 邮箱 24 小时最多 3 次。验证码有效期 15 分钟，请使用最近一次申请的验证码。</p>
        </form>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <form v-if="requestId" class="reset-form" @submit.prevent="resetPassword">
          <h2>设置新密码</h2>
          <label for="email-code">邮箱验证码</label>
          <input id="email-code" v-model.trim="code" class="input" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required :disabled="busy" placeholder="6 位数字" />
          <label for="new-password">新密码</label>
          <input id="new-password" v-model="password" class="input" type="password" autocomplete="new-password" minlength="6" maxlength="64" required :disabled="busy" placeholder="6–64 位，请勿与其他平台密码相同" />
          <label for="confirm-password">再次输入新密码</label>
          <input id="confirm-password" v-model="confirmation" class="input" type="password" autocomplete="new-password" minlength="6" maxlength="64" required :disabled="busy" />
          <button type="submit" class="btn btn--primary btn--full" :disabled="busy">{{ resetting ? '正在重置…' : '验证并重置密码' }}</button>
        </form>
        <p v-if="error" class="error-text" role="alert">{{ error }}</p>
        <a :href="loginUrl" class="back-link">返回登录</a>
      </template>
      <footer>中南林业科技大学软件协会</footer>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const loginUrl = route.query.from === 'admin' ? '/admin/login' : '/login'
const isApp = route.query.from === 'app'
const account = ref(''), captcha = ref(''), captchaId = ref(''), captchaImage = ref('')
const requestId = ref(''), code = ref(''), password = ref(''), confirmation = ref('')
const error = ref(''), notice = ref(''), done = ref(false)
const sending = ref(false), resetting = ref(false), captchaLoading = ref(false)
const busy = computed(() => sending.value || resetting.value)
const remaining = ref(0)
let timer, deadline = 0
async function api(path, body) {
  const response = await fetch(`/api/auth/${path}`, {
    method: body ? 'POST' : 'GET', credentials: 'omit', cache: 'no-store',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000)
  })
  if (!response.ok) throw new Error('服务暂不可用，请稍后重试或联系管理员')
  const result = await response.json()
  if (result.code !== 200) throw new Error(result.message || '操作失败，请联系管理员')
  return result.data
}
async function loadCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true; captcha.value = ''; captchaId.value = ''; captchaImage.value = ''
  try {
    const data = await api('captcha')
    captchaId.value = data.captchaId; captchaImage.value = data.image
  } catch (e) { error.value = e.message || '无法加载图形验证码，请重试' }
  finally { captchaLoading.value = false }
}
function clearChallenge() { requestId.value = ''; code.value = ''; password.value = ''; confirmation.value = ''; notice.value = '' }
async function sendCode() {
  if (busy.value || remaining.value || !captchaId.value) return
  error.value = ''; sending.value = true
  try {
    const data = await api('password-recovery/send', { account: account.value, captchaId: captchaId.value, captcha: captcha.value })
    requestId.value = data.requestId; code.value = ''; notice.value = data.notice
    deadline = Date.now() + data.cooldownSeconds * 1000
    try { sessionStorage.setItem('password-recovery-cooldown', String(deadline)) } catch { /* 服务端仍强制限流 */ }
    tick()
  } catch (e) { error.value = e.message || '申请失败，请稍后重试；请勿连续点击发送' }
  finally { sending.value = false; await loadCaptcha() }
}
async function resetPassword() {
  if (busy.value) return
  error.value = ''
  if (password.value !== confirmation.value) { error.value = '两次输入的新密码不一致'; return }
  resetting.value = true
  try {
    await api('password-recovery/reset', { requestId: requestId.value, code: code.value, newPassword: password.value })
    clearChallenge(); done.value = true
  } catch (e) { error.value = e.message || '重置失败，请联系管理员' }
  finally { resetting.value = false }
}
function tick() { remaining.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000)) }
onMounted(() => {
  try { deadline = Number(sessionStorage.getItem('password-recovery-cooldown')) || 0 } catch { /* 可不使用本地存储 */ }
  tick(); timer = setInterval(tick, 1000); loadCaptcha() // 只加载图片验证码，绝不自动发送邮件。
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.recovery-page { min-height: 100dvh; padding: 36px 16px; background: #f5f7fb; display: flex; justify-content: center; align-items: flex-start; }
.recovery-card { width: 100%; max-width: 500px; padding: 30px; border-radius: 22px; background: white; box-shadow: 0 8px 40px #2339510b; }
h1 { margin: 16px 0 8px; font-size: 26px; } h2 { font-size: 19px; margin-bottom: 16px; }
.subtitle, .small, footer { color: #657080; line-height: 1.65; }
.notice { background: #edf7fc; color: #34566b; padding: 14px; border-radius: 12px; font-size: 14px; line-height: 1.7; margin: 20px 0; }
label { display: block; margin: 16px 0 8px; font-size: 14px; font-weight: 600; }
.input { width: 100%; min-width: 0; min-height: 46px; }
.captcha-row { display: flex; align-items: center; gap: 12px; }
.captcha-button { flex-shrink: 0; min-width: 120px; padding: 0; border: 1px solid #dce3ed; border-radius: 8px; background: white; cursor: pointer; overflow: hidden; min-height: 42px; }
.captcha-button img { display: block; }.btn { margin-top: 20px; min-height: 46px; text-align: center; }
.small { margin-top: 12px; font-size: 12px; }.reset-form { border-top: 1px solid #e5eaf0; padding-top: 24px; margin-top: 24px; }
.error-text { margin-top: 16px; line-height: 1.65; }.back-link { display: inline-block; margin-top: 20px; color: #18668d; }
footer { margin-top: 28px; font-size: 12px; text-align: center; }.success p { margin: 16px 0; line-height: 1.7; }
@media(max-width: 480px) { .recovery-page { padding: 16px 12px; }.recovery-card { padding: 22px 18px; } }
</style>
