<template>
  <div class="page join-page">
    <PageHeader title="加入软件协会" label="GROW WITH US / 同行成长" description="和志同道合的伙伴，一起把想法变成作品。" />
    <div v-if="loading" class="loading" role="status" aria-label="正在加载入会信息"><div class="loading__spinner"></div></div>
    <div v-else-if="loadError" class="empty"><p role="alert">{{ loadError }}</p><button class="btn btn--secondary mt-3" @click="loadPage">重新加载</button></div>
    <div v-else class="join-layout">
      <aside class="join-guide">
        <span class="join-eyebrow">YOUR NEXT CHAPTER</span>
        <h2>从这里，<br>找到一起成长的伙伴。</h2><p>中南林业科技大学软件协会</p>
        <ol class="join-steps" aria-label="入会进度"><li v-for="(step, index) in steps" :key="step" :class="{ active: stage === index, done: stage > index }" :aria-current="stage === index ? 'step' : undefined"><span><UiIcon v-if="stage > index" name="check" :size="16" /><template v-else>0{{ index + 1 }}</template></span><div>{{ step }}<small>{{ stepDescriptions[index] }}</small></div></li></ol>
        <div class="join-guide__footer">万维网连接五大洲<br>二进制写尽天下事</div>
      </aside>
      <section class="join-content" :aria-label="stateTitle" :aria-busy="refreshing">
        <div class="join-state-heading"><span class="join-state-icon" aria-hidden="true"><UiIcon :name="stage === 3 ? 'users' : stage === 2 ? 'clock' : stage === 1 ? 'credit-card' : 'user-plus'" :size="26" /></span><span class="join-eyebrow">{{ stage === 3 ? 'WELCOME ABOARD' : 'MEMBERSHIP / 入会申请' }}</span><h2>{{ stateTitle }}</h2><p>{{ stateDescription }}</p></div>
        <p v-if="!joinInfo.enabled && application && stage !== 3" class="join-notice">新申请已暂停，你的已有申请仍可继续办理。</p>
        <template v-if="stage === 3">
          <div v-if="application?.managerName" class="join-info-card"><h3>迎新负责人</h3><dl><div><dt>姓名</dt><dd>{{ application.managerName }}</dd></div><div v-if="application.managerQq"><dt>QQ 号</dt><dd class="join-contact"><span>{{ application.managerQq }}</span><button class="copy-btn" @click="copyText(application.managerQq)">复制</button></dd></div></dl></div>
          <div v-if="publicSettings.join_qq_group_url || groupLink" class="join-group"><h3>{{ publicSettings.join_qq_group_name || '协会新生群' }}</h3><img v-if="publicSettings.join_qq_group_url" :src="publicSettings.join_qq_group_url" class="join-qr" alt="协会 QQ 群二维码" /><a v-if="groupLink" :href="groupLink" target="_blank" rel="noopener noreferrer" class="btn btn--secondary">打开 QQ 加群 <UiIcon name="link" /></a></div>
          <p v-else class="join-inline-note">会员权限已生效，可联系迎新负责人了解入群方式。</p>
          <router-link to="/study" class="btn btn--primary join-primary">开始学习 <UiIcon name="book-open" /></router-link>
          <router-link to="/profile" class="join-secondary join-center">查看我的会员信息 <UiIcon name="chevron-right" :size="14" /></router-link>
        </template>
        <template v-else-if="application">
          <div class="join-info-card"><h3>你的入会信息</h3><dl><div><dt>负责人</dt><dd>{{ application.managerName || '请联系协会核对' }}</dd></div><div v-if="application.managerQq"><dt>QQ 号</dt><dd class="join-contact"><span>{{ application.managerQq }}</span><button class="copy-btn" @click="copyText(application.managerQq)">复制</button></dd></div><div><dt>入会会费</dt><dd class="join-fee">¥ {{ feeText }}</dd></div><div><dt>申请编号</dt><dd>#{{ application.id }}</dd></div><div v-if="stage === 2"><dt>交易单号</dt><dd class="transaction-number">{{ application.paymentCode || '未填写，请联系负责人核对' }}</dd></div></dl></div>
          <template v-if="stage === 1">
            <p v-if="needsCorrection" class="join-notice join-notice--correction" role="status"><strong>请补充信息</strong>{{ application.reviewNote }}<span>已付款请勿重复缴费，更正信息后重新提交即可。</span></p>
            <div v-else class="payment-section"><div v-if="hasWechat && hasAlipay" class="qr-tabs" role="group" aria-label="付款方式"><button :aria-pressed="qrType === 'wechat'" :class="{ active: qrType === 'wechat' }" @click="qrType = 'wechat'">微信支付</button><button :aria-pressed="qrType === 'alipay'" :class="{ active: qrType === 'alipay' }" @click="qrType = 'alipay'">支付宝</button></div><img v-if="paymentQr && !qrBroken" :src="paymentQr" class="join-qr" :alt="qrType === 'wechat' ? '负责人微信收款码' : '负责人支付宝收款码'" @error="qrBroken = true" /><p v-else class="join-inline-note">收款码暂不可用，请通过 QQ 联系负责人核对，勿向其他账户重复付款。</p></div>
            <form class="payment-form" @submit.prevent="handleSubmit"><label class="form-label" for="payment-code">交易单号 <span>（选填）</span></label><input id="payment-code" v-model="paymentCode" class="input" autocomplete="off" maxlength="100" placeholder="填写交易单号，便于负责人核对" /><p class="join-inline-note">未填写单号时，请通过 QQ 把付款凭证发给负责人核对。</p><button class="btn btn--primary join-primary" :disabled="submitting || refreshing">{{ submitting ? '提交中…' : needsCorrection ? '更正信息，重新提交' : '已完成付款，提交审核' }}<UiIcon name="clipboard-check" /></button></form>
            <button v-if="application.canRefresh && !needsCorrection" class="join-secondary" @click="handleRefresh" :disabled="refreshing || submitting">{{ refreshing ? '更换中…' : '尚未付款，更换负责人' }}</button>
          </template>
          <template v-else>
            <p class="join-inline-note">请勿重复缴费。页面会自动更新审核结果，也可以联系负责人核对。</p>
            <button class="btn btn--secondary join-primary" :disabled="refreshing" @click="refreshApplication">{{ refreshing ? '更新中…' : '刷新审核状态' }}<UiIcon name="refresh-cw" :size="17" /></button>
            <button class="join-secondary" @click="editingPayment = !editingPayment">{{ editingPayment ? '收起更正' : '更正交易单号' }}</button>
            <form v-if="editingPayment" class="payment-form" @submit.prevent="handleSubmit"><label class="form-label" for="correct-payment">交易单号（选填）</label><input id="correct-payment" v-model="paymentCode" class="input" maxlength="100" autocomplete="off" /><button class="btn btn--secondary join-primary" :disabled="submitting || refreshing">{{ submitting ? '保存中…' : '保存更正信息' }}</button></form>
          </template>
        </template>
        <template v-else-if="joinInfo.enabled && joinInfo.ready">
          <div class="join-fee-summary"><span>{{ joinInfo.grade }} 届 · 入会会费</span><strong>¥ {{ feeText }}</strong><small>申请后分配迎新负责人，核对信息后完成缴费。</small></div>
          <div class="join-benefits"><span>结伴学习</span><span>交流实践</span><span>分享所学</span></div><button class="btn btn--primary join-primary" @click="handleApply" :disabled="applying">{{ applying ? '申请中…' : '申请加入' }}<UiIcon name="user-plus" /></button>
        </template>
        <template v-else><div class="join-closed"><UiIcon name="calendar" :size="30" /><p>{{ joinInfo.enabled ? '迎新信息正在准备中，请稍后再来。' : '本期入会申请暂未开放，请关注协会后续通知。' }}</p><button class="btn btn--secondary" :disabled="refreshing" @click="refreshApplication">刷新入会状态</button><router-link to="/activities" class="join-secondary">先看看协会活动 <UiIcon name="chevron-right" :size="14" /></router-link></div></template>
        <p v-if="stage !== 3 && publicSettings.footer_qq" class="join-support">需要协助？协会 QQ：<button @click="copyText(publicSettings.footer_qq)">{{ publicSettings.footer_qq }} <UiIcon name="copy" :size="13" /></button></p>
        <p v-if="settingsError" class="join-inline-note">联系与群信息暂未加载，<button class="join-inline-retry" @click="loadSettings">重新加载</button></p>
        <p v-if="actionError" class="join-feedback join-feedback--error" role="alert">{{ actionError }}</p><p v-if="feedback" class="join-feedback" role="status">{{ feedback }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import PageHeader from '@/components/PageHeader.vue'
import UiIcon from '@/components/UiIcon.vue'
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
const userStore = useUserStore()
const application = ref(null), joinInfo = ref({ enabled: false, ready: false }), publicSettings = ref({}), loading = ref(true), loadError = ref(''), settingsError = ref(false)
const paymentCode = ref(''), qrType = ref('wechat'), qrBroken = ref(false), editingPayment = ref(false), submitting = ref(false), refreshing = ref(false), applying = ref(false)
const actionError = ref(''), feedback = ref('')
const isMember = computed(() => userStore.roles.some(role => [0, 1, 2, 3].includes(Number(role))))
const needsCorrection = computed(() => Number(application.value?.status) === 3)
const stage = computed(() => isMember.value ? 3 : application.value ? Number(application.value.status) === 2 ? 2 : Number(application.value.status) === 1 ? 2 : 1 : 0)
const steps = ['提交申请', '联系与缴费', '等待审核', '欢迎加入'], stepDescriptions = ['认识软件协会', '对接迎新负责人', '核对入会信息', '一起学习与创造']
const stateTitle = computed(() => !application.value && !isMember.value && !joinInfo.value.enabled ? '等待下一次相聚' : needsCorrection.value ? '补充入会信息' : ['开启你的新篇章', '完成入会缴费', '等待审核', '欢迎加入软件协会'][stage.value])
const stateDescription = computed(() => needsCorrection.value ? '请按负责人的说明补充信息，已付款无需再次缴费。' : ['带上好奇心，和伙伴一起探索。', '请核对会费与负责人信息，扫码完成缴费。', '信息已提交，正在等待负责人核对。', '会员权限已生效，一起开启新的学习旅程。'][stage.value])
const feeText = computed(() => { const value = application.value?.membershipFee ?? joinInfo.value.membershipFee; return value !== '' && value != null && Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '待确认' })
const hasWechat = computed(() => !!application.value?.wechatQr), hasAlipay = computed(() => !!application.value?.alipayQr)
const paymentQr = computed(() => qrType.value === 'wechat' ? application.value?.wechatQr : application.value?.alipayQr)
const groupLink = computed(() => { try { const url = new URL(publicSettings.value.join_group_link); return ['https:', 'http:'].includes(url.protocol) ? url.href : '' } catch { return '' } })
watch(qrType, () => { qrBroken.value = false })
let timer, disposed = false
function setApplication(data, preserveDraft = false) {
  application.value = data
  if (!preserveDraft) { paymentCode.value = data?.paymentCode || ''; editingPayment.value = false }
  qrType.value = data?.wechatQr ? 'wechat' : 'alipay'; qrBroken.value = false
}
async function loadSettings() { try { publicSettings.value = (await request.get('/api/setting/public')).data || {}; settingsError.value = false } catch { settingsError.value = true } }
async function loadState(preserveDraft = false) {
  const [user, app, info] = await Promise.all([userStore.fetchUserInfo(), request.get('/api/join/my-application'), request.get('/api/join/info')])
  if (!disposed) { setApplication(app.data, preserveDraft); joinInfo.value = info.data }
}
async function loadPage() {
  loading.value = true; loadError.value = ''
  try { await loadState(); await loadSettings() }
  catch (error) { loadError.value = error.message || '入会信息暂时无法加载，请重试。' } finally { loading.value = false }
}
function clearFeedback() { actionError.value = ''; feedback.value = '' }
async function copyText(value) { clearFeedback(); try { await navigator.clipboard.writeText(String(value)); feedback.value = 'QQ 号已复制' } catch { actionError.value = '复制失败，请长按 QQ 号手动复制。' } }
async function handleApply() { if (applying.value || !joinInfo.value.enabled || !joinInfo.value.ready) return; applying.value = true; clearFeedback(); try { setApplication((await request.post('/api/join/apply')).data) } catch (error) { actionError.value = error.message || '申请失败，请稍后重试。'; await request.get('/api/join/info').then(res => { joinInfo.value = res.data }).catch(() => {}) } finally { applying.value = false } }
async function handleSubmit() { if (submitting.value || refreshing.value) return; submitting.value = true; clearFeedback(); try { await request.post('/api/join/submit-payment', { paymentCode: paymentCode.value }); await loadState(); feedback.value = '资料已提交，请等待负责人审核。' } catch (error) { actionError.value = error.message || '提交失败，请稍后重试。' } finally { submitting.value = false } }
async function handleRefresh() { if (refreshing.value || submitting.value) return; refreshing.value = true; clearFeedback(); try { setApplication((await request.post('/api/join/refresh-manager')).data) } catch (error) { actionError.value = error.message || '更换失败，请稍后重试。' } finally { refreshing.value = false } }
async function refreshApplication(options = {}) {
  if (refreshing.value || submitting.value || applying.value || loading.value || disposed) return
  refreshing.value = true
  if (!options.silent) clearFeedback()
  try { await loadState(options.silent); if (!options.silent) feedback.value = '入会状态已更新' }
  catch (error) { if (!options.silent) actionError.value = error.message || '刷新失败，请稍后重试。' }
  finally { refreshing.value = false }
}
function refreshWhenVisible() { if (document.visibilityState === 'visible') refreshApplication({ silent: true }) }
onMounted(() => { loadPage(); timer = setInterval(refreshWhenVisible, 30000); window.addEventListener('focus', refreshWhenVisible); document.addEventListener('visibilitychange', refreshWhenVisible) })
onUnmounted(() => { disposed = true; clearInterval(timer); window.removeEventListener('focus', refreshWhenVisible); document.removeEventListener('visibilitychange', refreshWhenVisible) })
</script>

<style scoped>
.join-layout { display: grid; grid-template-columns: minmax(0, .85fr) minmax(0, 1.15fr); gap: 28px; align-items: start; }.join-guide { border: 1px solid #d4e6f7; border-radius: 20px; background: linear-gradient(145deg, #e6f3ff, #f7fbff); padding: 36px; position: sticky; top: 96px; }.join-eyebrow { font-size: 0.75rem; letter-spacing: 1.7px; color: #557594; font-weight: 600; }.join-guide h2 { font-size: 1.8125rem; line-height: 1.6; margin: 20px 0 12px; color: var(--ink-900); }.join-guide > p { font-size: 0.75rem; color: var(--ink-500); }
.join-steps { margin: 35px 0; display: grid; gap: 0; }.join-steps li { display: flex; align-items: flex-start; gap: 15px; position: relative; min-height: 76px; color: #657584; font-size: 0.875rem; }.join-steps li:not(:last-child)::before { content: ''; width: 1px; background: #c7daee; position: absolute; top: 34px; bottom: 0; left: 16px; }.join-steps li > span { width: 33px; height: 33px; display: grid; place-items: center; flex-shrink: 0; border: 1px solid #c7daee; background: #f6fbff; border-radius: 50%; font-size: 0.75rem; }.join-steps li > div { padding-top: 3px; }.join-steps small { display: block; margin-top: 5px; font-size: 0.75rem; font-weight: 400; color: #607588; }.join-steps .active { color: #246dbe; font-weight: 650; }.join-steps .active > span { background: #2878d5; color: #fff; border-color: #2878d5; box-shadow: 0 0 0 5px #2878d511; }.join-steps .done > span { color: #2674ce; background: #e4f1ff; }.join-guide__footer { font-size: 0.75rem; line-height: 2; color: #5e758a; letter-spacing: 2px; }
.join-content { min-width: 0; padding: 35px; background: #fff; border: 1px solid var(--border-blue); border-radius: 20px; box-shadow: var(--shadow-sm); }.join-state-heading { text-align: center; margin-bottom: 28px; }.join-state-icon { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 18px; margin: 0 auto 18px; background: #e9f4ff; color: #2674ce; font: 1.4375rem ui-monospace, monospace; }.join-state-heading .join-eyebrow { display: block; margin-bottom: 10px; font-size: 0.75rem; }.join-state-heading h2 { font-size: 1.625rem; color: var(--ink-900); }.join-state-heading p { font-size: 0.8125rem; color: var(--ink-500); line-height: 1.9; margin-top: 10px; }
.join-info-card { background: #f4f9ff; padding: 22px; border: 1px solid #e0ecf8; border-radius: 13px; }.join-info-card h3, .join-group h3 { font-size: 0.875rem; color: var(--ink-700); margin-bottom: 12px; }.join-info-card dl > div { display: flex; justify-content: space-between; gap: 15px; padding: 12px 0; border-bottom: 1px solid #e0ecf8; font-size: 0.8125rem; align-items: flex-start; }.join-info-card dl > div:last-child { border: 0; padding-bottom: 0; }.join-info-card dt { color: #607486; flex-shrink: 0; }.join-info-card dd { min-width: 0; text-align: right; overflow-wrap: anywhere; color: var(--ink-700); }.join-contact { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 8px; }.join-contact > span { user-select: text; }.copy-btn { border: 1px solid #ccdef0; color: var(--primary); border-radius: 7px; padding: 5px 10px; background: #fff; font-size: 0.75rem; }
.payment-section { margin: 25px 0; text-align: center; }.qr-tabs { display: inline-flex; padding: 4px; gap: 5px; border: 1px solid #dbe8f5; background: #f3f8ff; border-radius: 11px; margin-bottom: 18px; }.qr-tabs button { min-height: 38px; padding: 8px 22px; border-radius: 7px; color: #61758a; font-size: 0.8125rem; }.qr-tabs .active { background: #fff; color: var(--primary); box-shadow: 0 2px 6px #35699b0b; }.join-qr { display: block; width: min(240px, 100%); height: auto; aspect-ratio: 1; object-fit: contain; margin: 0 auto; border: 1px solid #e1edf8; border-radius: 12px; padding: 10px; background: #fff; }.payment-form .form-label > span { color: var(--ink-400); font-weight: 400; }.join-primary { display: flex; width: 100%; justify-content: space-between; min-height: 47px; margin-top: 22px; }.join-secondary { display: block; margin: 17px auto 0; color: #61768a; font-size: 0.75rem; min-height: 38px; }.join-secondary:hover { color: var(--primary); }
.join-group { text-align: center; margin-top: 25px; }.join-group .btn { margin-top: 18px; }.join-benefits { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-block: 35px; }.join-benefits span { border: 1px solid #dceaf8; background: #f5faff; border-radius: 10px; font-size: 0.75rem; color: #597794; padding: 18px 6px; text-align: center; }.join-inline-note { font-size: 0.75rem; color: var(--ink-500); line-height: 1.9; margin-top: 18px; text-align: center; overflow-wrap: anywhere; }.join-feedback { border-radius: 9px; padding: 12px; font-size: 0.75rem; line-height: 1.7; margin-top: 16px; background: #edf6ff; color: #346faa; }.join-feedback--error { background: #fff1f1; color: #c14e4e; }
@media (max-width: 760px) { .join-layout { grid-template-columns: minmax(0, 1fr); gap: 16px; }.join-guide { position: static; padding: 22px; }.join-guide > :is(h2, p), .join-guide__footer { display: none; }.join-steps { grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 20px 0 0; gap: 3px; }.join-steps li { flex-direction: column; align-items: center; text-align: center; gap: 9px; font-size: 0.75rem; min-height: 0; }.join-steps li > span { z-index: 1; }.join-steps small { display: none; }.join-steps li:not(:last-child)::before { left: 50%; right: -50%; width: auto; height: 1px; top: 16px; bottom: auto; }.join-content { padding: 26px 22px; border-radius: 17px; }.join-state-heading h2 { font-size: 1.4375rem; }.join-info-card { padding: 18px; }.join-qr { width: min(250px, 100%); }.payment-form .input { font-size: 1rem; } }
@media (max-width: 360px) { .join-guide { padding: 20px 14px; }.join-content { padding: 24px 16px; }.join-info-card { padding: 15px; }.join-info-card dl > div { gap: 10px; }.join-benefits { gap: 5px; }.join-benefits span { font-size: 0.75rem; } }
</style>

<style scoped>
.join-guide { background: #eff1f8; border-color: #d9deeb; padding: 2rem; top: calc(var(--nav-height) + 1.5rem); }
.join-content { padding: 2rem; }
.join-guide h2 { font-size: 1.75rem; }
.join-steps .active { color: var(--primary-dark); }
.join-steps .active > span { background: var(--primary); border-color: var(--primary); }
.join-steps .done > span { color: #4055a1; background: #e2e7f6; }
.join-state-icon { background: #eef0fb; color: var(--primary); width: 3rem; height: 3rem; border-radius: .875rem; }
.join-state-heading p, .join-info-card dl > div { font-size: .9375rem; }
.join-info-card { background: #f6f7fa; border-color: #e0e4ed; }
.join-info-card h3, .join-group h3 { font-size: 1rem; }
.join-inline-note, .join-feedback, .join-guide > p { font-size: .8125rem; }
.join-benefits span { background: #f4f3fa; border-color: #e4e0f0; color: #61517c; font-size: .8125rem; }
.join-notice { border: 1px solid #e7dbbb; background: #fffaf0; color: #715b31; padding: .875rem 1rem; border-radius: .75rem; font-size: .8125rem; line-height: 1.8; margin-bottom: 1rem; }
.join-notice--correction { margin-top: 1.25rem; }
.join-notice--correction :is(strong, span) { display: block; }
.join-notice--correction strong { margin-bottom: .25rem; }
.join-notice--correction span { margin-top: .5rem; }
.join-fee-summary { display: grid; gap: .5rem; padding: 1.25rem; border: 1px solid #e0e4ef; border-radius: .875rem; background: #f7f8fc; }
.join-fee-summary > span { color: var(--ink-600); font-size: .8125rem; }
.join-fee-summary strong { color: var(--primary-dark); font-size: 1.75rem; font-variant-numeric: tabular-nums; }
.join-fee-summary small { font-size: .75rem; line-height: 1.7; color: var(--ink-500); }
.join-fee { font-weight: 650; color: var(--primary-dark); }
.join-support { margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--border-blue); font-size: .75rem; color: var(--ink-500); text-align: center; line-height: 1.8; }
.join-support button { display: inline-flex; align-items: center; gap: .375rem; color: var(--primary-dark); }
.join-closed { text-align: center; padding: 1.25rem 0; color: var(--ink-500); }
.join-closed > .ui-icon { color: var(--primary); margin: 0 auto; }
.join-closed p { margin: 1rem 0 1.5rem; font-size: .875rem; line-height: 1.8; }
.join-inline-retry { color: var(--primary); text-decoration: underline; }
.join-center { display: flex; align-items: center; justify-content: center; gap: .375rem; }
@media(max-width: 760px) { .join-guide { position: static; padding: 1.25rem; }.join-content { padding: 1.5rem; }.join-steps { margin-top: 1rem; }.join-state-heading p, .join-info-card dl > div { font-size: .875rem; } }
</style>
