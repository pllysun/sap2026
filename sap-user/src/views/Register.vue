<template>
  <div class="auth-page">
    <!-- Floating decoration particles -->
    <div class="auth-particle auth-particle--1"></div>
    <div class="auth-particle auth-particle--2"></div>
    <div class="auth-particle auth-particle--3"></div>

    <div class="auth-card auth-card--register">
      <div class="text-center mb-4">
        <div class="auth-logo-wrap">
          <img src="/logo.png" alt="CSUFTSAP Logo" class="auth-logo" />
        </div>
        <h1 class="auth-card__title mt-1">创建账号</h1>
        <p class="auth-card__subtitle">加入中南林业科技大学软件协会</p>
      </div>

      <div v-if="errorMsg" class="error-text text-center mb-2" role="alert">{{ errorMsg }}</div>
      <div v-if="successMsg" class="success-text text-center mb-2" role="status">{{ successMsg }}</div>
      <div class="registration-policy" :class="{ 'registration-policy--open': policyLoaded && !emailRequired }" role="status">
        <span>{{ policyLoading ? '正在获取注册方式…' : !policyLoaded ? '注册方式加载失败，请重试' : emailRequired ? '验证 QQ 邮箱后即可创建账号' : '入会通道开放中 · 图形验证即可注册' }}</span>
        <button v-if="!policyLoaded && !policyLoading" type="button" class="btn btn--text" @click="loadPolicy">重试</button>
      </div>

      <form class="registration-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <label for="register-studentId" class="form-label">学号</label>
          <input autocomplete="username" id="register-studentId" v-model.trim="form.studentId" type="text" class="input" placeholder="请输入学号" maxlength="20" required />
        </div>
        <div class="form-group">
          <label for="register-password" class="form-label">密码</label>
          <input autocomplete="new-password" id="register-password" v-model="form.password" type="password" class="input" placeholder="请输入密码" minlength="6" maxlength="64" required />
        </div>
        <div class="form-group">
          <label for="register-name" class="form-label">姓名</label>
          <input autocomplete="name" id="register-name" v-model.trim="form.name" type="text" class="input" placeholder="请输入真实姓名" maxlength="50" required />
        </div>
        <div class="form-group">
          <label for="register-gender" class="form-label">性别</label>
          <select id="register-gender" v-model="form.gender" class="select">
            <option :value="1">男</option>
            <option :value="0">女</option>
          </select>
        </div>
        <div class="form-group">
          <label for="register-qq" class="form-label">QQ 号</label>
          <input id="register-qq" v-model.trim="form.qq" type="text" class="input" placeholder="请输入QQ号" inputmode="numeric" pattern="[1-9][0-9]{4,14}" maxlength="15" required />
          <p class="registration-hint">{{ emailRequired ? `验证码将发送到${validQq ? `${form.qq}@qq.com` : '你的 QQ 邮箱'}，也用于找回密码。` : '请准确填写 QQ 号，用于联系和找回密码，无需收取注册邮件。' }}</p>
        </div>
        <fieldset class="registration-verification">
          <legend>{{ emailRequired ? '验证邮箱' : '安全验证' }}</legend>
        <div class="form-group">
          <label for="register-captchaCode" class="form-label">图形验证码</label>
          <div class="captcha-row">
            <input id="register-captchaCode" v-model.trim="form.captchaCode" type="text" class="input captcha-input"
                   placeholder="请输入图中字符" maxlength="6" autocomplete="off" :required="!emailRequired || !emailRequestId" />
            <button type="button" class="captcha-refresh" :disabled="captchaLoading || loading || sendingCode"
                    aria-label="刷新验证码" @click="loadCaptcha">
              <img v-if="captchaImg" :src="captchaImg" class="captcha-img" alt="验证码，点击刷新" />
              <span v-else>{{ captchaLoading ? '加载中…' : '点击获取验证码' }}</span>
            </button>
          </div>
        </div>
        <div v-if="emailRequired" class="form-group">
          <label for="register-emailCode" class="form-label">邮箱验证码</label>
          <div class="captcha-row">
            <input id="register-emailCode" v-model.trim="form.emailCode" class="input captcha-input" type="text"
                   placeholder="六位数字验证码" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required />
            <button type="button" class="btn btn--secondary email-code-button" :disabled="!canSendCode" @click="sendEmailCode">
              {{ sendingCode ? '发送中…' : cooldown > 0 ? `${cooldown} 秒后重发` : '获取验证码' }}
            </button>
          </div>
          <p class="registration-hint" aria-live="polite">{{ emailNotice || '先填写学号、姓名和 QQ 号，再输入图形验证码获取邮件。' }}</p>
        </div>
        </fieldset>
        <button type="submit" class="btn btn--primary btn--full btn--pill"
                :disabled="!policyLoaded || policyLoading || loading || sendingCode || !!successMsg || (emailRequired ? !emailRequestId || !/^[0-9]{6}$/.test(form.emailCode) : captchaLoading || !captchaId || !form.captchaCode)">
          {{ loading ? '注册中…' : emailRequired ? '验证邮箱并注册' : '创建账号' }}
        </button>
      </form>

      <div class="auth-card__footer">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const form = reactive({ studentId: '', password: '', name: '', gender: 1, qq: '', captchaCode: '', emailCode: '' })
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const policyLoading = ref(false), policyLoaded = ref(false), emailRequired = ref(true)

const sendingCode = ref(false), emailRequestId = ref(''), emailNotice = ref(''), cooldown = ref(0)
const validQq = computed(() => /^[1-9][0-9]{4,14}$/.test(form.qq))
const canSendCode = computed(() => policyLoaded.value && emailRequired.value && !policyLoading.value && !loading.value && !sendingCode.value && !captchaLoading.value && cooldown.value === 0
  && /^[A-Za-z0-9_-]{1,20}$/.test(form.studentId) && form.name.length > 0 && validQq.value && captchaId.value && form.captchaCode)
watch(() => [form.studentId, form.qq], () => {
  emailRequestId.value = ''; form.emailCode = ''; emailNotice.value = ''
})
const captchaImg = ref('')
const captchaId = ref('')
const captchaLoading = ref(false)
let countdown
onMounted(() => {
  loadPolicy()
  loadCaptcha()
  countdown = window.setInterval(() => { if (cooldown.value > 0) cooldown.value-- }, 1000)
})
onBeforeUnmount(() => window.clearInterval(countdown))

// 进入注册页时查询一次；仅在提交时服务端明确提示策略改变后重新查询。
async function loadPolicy() {
  if (policyLoading.value) return
  policyLoading.value = true; policyLoaded.value = false
  try {
    const { data } = await request.get('/api/auth/register/policy')
    if (typeof data?.joinEnabled !== 'boolean' || typeof data?.emailVerificationRequired !== 'boolean'
        || data.joinEnabled === data.emailVerificationRequired || data.captchaRequired !== true)
      throw new Error('注册方式返回异常，请重新加载')
    emailRequired.value = data.emailVerificationRequired
    emailRequestId.value = ''; form.emailCode = ''; emailNotice.value = ''
    policyLoaded.value = true
  } catch (e) { errorMsg.value = e.message || '无法获取注册方式，请重试' }
  finally { policyLoading.value = false }
}

async function sendEmailCode() {
  if (!canSendCode.value) return
  sendingCode.value = true; errorMsg.value = ''; emailNotice.value = ''
  const account = form.studentId, qq = form.qq
  try {
    const res = await request.post('/api/auth/register/email-code', {
      studentId: account, name: form.name, qq, captchaId: captchaId.value, captchaCode: form.captchaCode
    })
    if (res.data?.captchaRequired || !res.data?.requestId) throw new Error('请完成图形验证码后重新获取邮件')
    cooldown.value = Number(res.data.cooldownSeconds) || 180
    if (account !== form.studentId || qq !== form.qq) throw new Error('学号或 QQ 已变更，请为当前账号重新获取验证码')
    emailRequestId.value = res.data.requestId
    emailNotice.value = `已发送至 ${res.data.email}，15 分钟内有效，请同时检查垃圾箱。`
  } catch (e) { errorMsg.value = e.message || '验证码发送失败，请稍后重试' }
  finally { sendingCode.value = false; await loadCaptcha() }
}

async function loadCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  captchaImg.value = ''; captchaId.value = ''; form.captchaCode = ''
  try {
    const res = await request.get('/api/auth/captcha')
    captchaImg.value = res.data.image
    captchaId.value = res.data.captchaId
    return true
  } catch (e) {
    errorMsg.value = e.message || '验证码获取失败，请点击重新获取'
    return false
  } finally { captchaLoading.value = false }
}

async function handleRegister() {
  if (loading.value || sendingCode.value || !policyLoaded.value || policyLoading.value || successMsg.value) return
  if (emailRequired.value && (!emailRequestId.value || !/^[0-9]{6}$/.test(form.emailCode))) {
    errorMsg.value = '请先获取并填写六位邮箱验证码'; return
  }
  if (!emailRequired.value && (!captchaId.value || !form.captchaCode)) {
    errorMsg.value = '请填写图形验证码'; return
  }
  loading.value = true; errorMsg.value = ''; successMsg.value = ''
  try {
    const { emailCode, captchaCode, ...fields } = form
    await userStore.register({ ...fields, ...(emailRequired.value
      ? { emailRequestId: emailRequestId.value, emailCode }
      : { captchaId: captchaId.value, captchaCode }) })
    successMsg.value = '注册成功，正在跳转…'
    setTimeout(() => router.push('/login'), 1200)
  } catch (e) {
    errorMsg.value = e.message || '注册失败'
    if (e.code === 409) { await loadPolicy(); await loadCaptcha() }
    else if (!emailRequired.value) await loadCaptcha()
  } finally { loading.value = false }
}
</script>

<style scoped>
.auth-page { padding: 16px; }
.auth-card--register { max-width: 520px; padding: 24px 28px; }
.auth-card--register > .text-center { display: grid; grid-template-columns: 56px minmax(0, 1fr); column-gap: 14px; align-items: center; text-align: left; margin-bottom: 22px !important; }
.auth-logo-wrap { grid-column: 1; grid-row: 1 / 3; }
.auth-card__title { grid-column: 2; margin: 0 !important; font-size: 24px; }
.auth-card__subtitle { grid-column: 2; margin: 4px 0 0; font-size: 12px; }
.auth-card__footer { margin-top: 14px; }
.registration-policy { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 16px; padding: 10px 12px; border-radius: 10px; background: var(--bg-secondary, #f6f8ff); color: var(--text-secondary, #62728a); font-size: 12px; line-height: 1.6; }
.registration-policy--open { background: #edf7f4; color: #227362; border: 1px solid #cde8df; }
.registration-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 14px; }
.registration-form .form-group { margin-bottom: 10px; }
.registration-form .form-label { margin-bottom: 4px; }
.registration-form .input, .registration-form .select { height: 40px; min-height: 40px; padding: 8px 12px; }
.registration-form > .form-group:nth-child(5), .registration-form > fieldset, .registration-form > button { grid-column: 1 / -1; }
@media (max-width: 480px) { .registration-form { grid-template-columns: minmax(0, 1fr); } }
.registration-verification { min-width: 0; margin: 2px 0 14px; padding: 10px 12px; border: 1px solid var(--border, #dce4f2); border-radius: 14px; background: var(--bg-secondary, #f6f8ff); }
.registration-verification legend { padding: 0 6px; font-size: 13px; font-weight: 600; color: var(--primary); }
.registration-verification .form-group:last-child { margin-bottom: 0; }
.registration-hint { margin: 7px 0 0; font-size: 12px; line-height: 1.7; color: var(--text-secondary, #62728a); overflow-wrap: anywhere; }
.email-code-button { min-width: 130px; flex-shrink: 0; padding-inline: 12px; }
.captcha-row {
  display: flex; align-items: center; gap: 10px;
}
.captcha-input { flex: 1; min-width: 0; }
.captcha-refresh {
  padding: 0; width: 140px; height: 42px; flex-shrink: 0; overflow: hidden;
  border: 1px solid var(--border, #e5e7eb); border-radius: 8px; background: white; cursor: pointer;
}
.captcha-img {
  display: block; height: 42px; width: 140px; object-fit: contain;
}
@media (max-width: 480px) {
  .captcha-row { flex-wrap: wrap; }
  .captcha-input { flex: 1 1 115px; min-width: 115px; }
  .captcha-refresh { width: 120px; max-width: 100%; }
  .captcha-img { width: 100%; }
}
.auth-logo-wrap {
  display: inline-flex; align-items: center; justify-content: center;
  animation: iconBounce 2s ease-in-out infinite;
}
.auth-logo {
  width: 56px; height: 56px; object-fit: contain;
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
