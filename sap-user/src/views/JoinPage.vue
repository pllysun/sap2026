<template>
  <div class="page join-page">
    <PageHeader title="加入软件协会" label="GROW WITH US / 同行成长" description="和志同道合的伙伴，一起把想法变成作品。" />
    <div v-if="loading" class="loading" role="status" aria-label="正在加载入会信息"><div class="loading__spinner"></div></div>
    <div v-else-if="loadError" class="empty"><p role="alert">{{ loadError }}</p><button class="btn btn--secondary mt-3" @click="loadPage">重新加载</button></div>
    <div v-else class="join-layout">
      <aside class="join-guide">
        <span class="join-eyebrow">YOUR NEXT CHAPTER</span>
        <h2>从这里，<br>找到一起成长的伙伴。</h2><p>中南林业科技大学软件协会</p>
        <ol class="join-steps" aria-label="入会进度"><li v-for="(step, index) in steps" :key="step" :class="{ active: stage === index, done: stage > index }" :aria-current="stage === index ? 'step' : undefined"><span>{{ stage > index ? '✓' : `0${index + 1}` }}</span><div>{{ step }}<small>{{ stepDescriptions[index] }}</small></div></li></ol>
        <div class="join-guide__footer">万维网连接五大洲<br>二进制写尽天下事</div>
      </aside>
      <section class="join-content" :aria-label="stateTitle">
        <div class="join-state-heading"><span class="join-state-icon" aria-hidden="true">{{ stage === 3 ? '✓' : stage === 2 ? '◷' : stage === 1 ? '02' : '{ }' }}</span><span class="join-eyebrow">{{ stage === 3 ? 'WELCOME ABOARD' : 'MEMBERSHIP / 入会申请' }}</span><h2>{{ stateTitle }}</h2><p>{{ stateDescription }}</p></div>
        <template v-if="stage === 3">
          <div v-if="application?.managerName" class="join-info-card"><h3>迎新负责人</h3><dl><div><dt>姓名</dt><dd>{{ application.managerName }}</dd></div><div v-if="application.managerQq"><dt>QQ 号</dt><dd class="join-contact"><span>{{ application.managerQq }}</span><button class="copy-btn" @click="copyText(application.managerQq)">复制</button></dd></div></dl></div>
          <div v-if="publicSettings.join_qq_group_url || publicSettings.join_group_link" class="join-group"><h3>{{ publicSettings.join_qq_group_name || '协会新生群' }}</h3><img v-if="publicSettings.join_qq_group_url" :src="publicSettings.join_qq_group_url" class="join-qr" alt="协会 QQ 群二维码" /><a v-if="publicSettings.join_group_link" :href="publicSettings.join_group_link" target="_blank" rel="noopener noreferrer" class="btn btn--secondary">打开 QQ 加群 <UiIcon name="link" /></a></div>
          <router-link to="/study" class="btn btn--primary join-primary">开始学习 <UiIcon name="book-open" /></router-link>
        </template>
        <template v-else-if="application">
          <div class="join-info-card"><h3>你的入会信息</h3><dl><div><dt>负责人</dt><dd>{{ application.managerName || '待分配' }}</dd></div><div v-if="application.managerQq"><dt>QQ 号</dt><dd class="join-contact"><span>{{ application.managerQq }}</span><button class="copy-btn" @click="copyText(application.managerQq)">复制</button></dd></div><div v-if="stage === 2"><dt>交易单号</dt><dd class="transaction-number">{{ application.paymentCode || '未填写' }}</dd></div></dl></div>
          <template v-if="stage === 1">
            <div class="payment-section"><div v-if="hasWechat && hasAlipay" class="qr-tabs" role="group" aria-label="付款方式"><button :aria-pressed="qrType === 'wechat'" :class="{ active: qrType === 'wechat' }" @click="qrType = 'wechat'">微信支付</button><button :aria-pressed="qrType === 'alipay'" :class="{ active: qrType === 'alipay' }" @click="qrType = 'alipay'">支付宝</button></div><img v-if="paymentQr" :src="paymentQr" class="join-qr" :alt="qrType === 'wechat' ? '负责人微信收款码' : '负责人支付宝收款码'" /><p v-else class="join-inline-note">负责人暂未配置收款码，请通过 QQ 联系负责人。</p></div>
            <form class="payment-form" @submit.prevent="handleSubmit"><label class="form-label" for="payment-code">交易单号 <span>（选填）</span></label><input id="payment-code" v-model="paymentCode" class="input" autocomplete="off" placeholder="付款后可填写交易单号" /><button class="btn btn--primary join-primary" :disabled="submitting || refreshing">{{ submitting ? '提交中…' : '已完成付款，提交审核' }}<UiIcon name="clipboard-check" /></button></form>
            <button v-if="application.canRefresh" class="join-secondary" @click="handleRefresh" :disabled="refreshing || submitting">{{ refreshing ? '更换中…' : '更换负责人' }}</button>
          </template>
          <template v-else><p class="join-inline-note">可将付款截图发送给负责人，以便核对。审核通过后，即可使用会员功能。</p><button class="btn btn--secondary join-primary" :disabled="refreshing" @click="refreshApplication">{{ refreshing ? '更新中…' : '刷新审核状态' }}</button></template>
        </template>
        <template v-else><div class="join-benefits"><span>结伴学习</span><span>交流实践</span><span>分享所学</span></div><button class="btn btn--primary join-primary" @click="handleApply" :disabled="applying">{{ applying ? '申请中…' : '申请加入' }}<UiIcon name="user-plus" /></button><p class="join-inline-note">提交申请后，系统将为你分配迎新负责人。</p></template>
        <p v-if="actionError" class="join-feedback join-feedback--error" role="alert">{{ actionError }}</p><p v-if="feedback" class="join-feedback" role="status">{{ feedback }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import PageHeader from '@/components/PageHeader.vue'
import UiIcon from '@/components/UiIcon.vue'
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
const userStore = useUserStore()
const application = ref(null), publicSettings = ref({}), loading = ref(true), loadError = ref('')
const paymentCode = ref(''), qrType = ref('wechat'), submitting = ref(false), refreshing = ref(false), applying = ref(false)
const actionError = ref(''), feedback = ref('')
const isMember = computed(() => userStore.roles.some(role => [0, 1, 2, 3].includes(Number(role))))
const stage = computed(() => isMember.value || Number(application.value?.status) === 2 ? 3 : application.value ? Number(application.value.status) === 1 ? 2 : 1 : 0)
const steps = ['提交申请', '联系与缴费', '等待审核', '欢迎加入'], stepDescriptions = ['认识软件协会', '对接迎新负责人', '核对入会信息', '一起学习与创造']
const stateTitle = computed(() => ['开启你的新篇章', '完成入会缴费', '等待审核', '欢迎加入软件协会'][stage.value])
const stateDescription = computed(() => ['带上好奇心，和伙伴一起探索。', '请核对负责人信息，扫码完成缴费。', '付款信息已提交，请勿重复缴费。', '共同成长，共同进步。'][stage.value])
const hasWechat = computed(() => !!application.value?.wechatQr), hasAlipay = computed(() => !!application.value?.alipayQr)
const paymentQr = computed(() => (qrType.value === 'wechat' ? application.value?.wechatQr : application.value?.alipayQr) || application.value?.wechatQr || application.value?.alipayQr)
function setApplication(data) { application.value = data; qrType.value = data?.wechatQr ? 'wechat' : 'alipay' }
async function loadApplication() { const res = await request.get('/api/join/my-application'); setApplication(res.data) }
async function loadPage() {
  loading.value = true; loadError.value = ''
  try { await userStore.fetchUserInfo(); await loadApplication(); try { publicSettings.value = (await request.get('/api/setting/public')).data || {} } catch {} }
  catch (error) { loadError.value = error.message || '入会信息暂时无法加载，请重试。' } finally { loading.value = false }
}
function clearFeedback() { actionError.value = ''; feedback.value = '' }
async function copyText(value) { clearFeedback(); try { await navigator.clipboard.writeText(String(value)); feedback.value = 'QQ 号已复制' } catch { actionError.value = '复制失败，请长按 QQ 号手动复制。' } }
async function handleApply() { if (applying.value) return; applying.value = true; clearFeedback(); try { setApplication((await request.post('/api/join/apply')).data) } catch (error) { actionError.value = error.message || '申请失败，请稍后重试。' } finally { applying.value = false } }
async function handleSubmit() { if (submitting.value) return; submitting.value = true; clearFeedback(); try { await request.post('/api/join/submit-payment', { paymentCode: paymentCode.value }); await loadApplication() } catch (error) { actionError.value = error.message || '提交失败，请稍后重试。' } finally { submitting.value = false } }
async function handleRefresh() { if (refreshing.value) return; refreshing.value = true; clearFeedback(); try { setApplication((await request.post('/api/join/refresh-manager')).data) } catch (error) { actionError.value = error.message || '更换失败，请稍后重试。' } finally { refreshing.value = false } }
async function refreshApplication() { refreshing.value = true; clearFeedback(); try { await userStore.fetchUserInfo(); await loadApplication(); feedback.value = '审核状态已更新' } catch (error) { actionError.value = error.message || '刷新失败，请稍后重试。' } finally { refreshing.value = false } }
onMounted(loadPage)
</script>

<style scoped>
.join-layout { display: grid; grid-template-columns: minmax(0, .85fr) minmax(0, 1.15fr); gap: 28px; align-items: start; }.join-guide { border: 1px solid #d4e6f7; border-radius: 20px; background: linear-gradient(145deg, #e6f3ff, #f7fbff); padding: 36px; position: sticky; top: 96px; }.join-eyebrow { font-size: 10px; letter-spacing: 1.7px; color: #658aaf; font-weight: 600; }.join-guide h2 { font-size: 29px; line-height: 1.6; margin: 20px 0 12px; color: var(--ink-900); }.join-guide > p { font-size: 12px; color: var(--ink-500); }
.join-steps { margin: 35px 0; display: grid; gap: 0; }.join-steps li { display: flex; align-items: flex-start; gap: 15px; position: relative; min-height: 76px; color: #8a9fb4; font-size: 14px; }.join-steps li:not(:last-child)::before { content: ''; width: 1px; background: #c7daee; position: absolute; top: 34px; bottom: 0; left: 16px; }.join-steps li > span { width: 33px; height: 33px; display: grid; place-items: center; flex-shrink: 0; border: 1px solid #c7daee; background: #f6fbff; border-radius: 50%; font-size: 10px; }.join-steps li > div { padding-top: 3px; }.join-steps small { display: block; margin-top: 5px; font-size: 11px; font-weight: 400; color: #7f9ab3; }.join-steps .active { color: #246dbe; font-weight: 650; }.join-steps .active > span { background: #2878d5; color: #fff; border-color: #2878d5; box-shadow: 0 0 0 5px #2878d511; }.join-steps .done > span { color: #2878d5; background: #e4f1ff; }.join-guide__footer { font-size: 12px; line-height: 2; color: #7c99b5; letter-spacing: 2px; }
.join-content { min-width: 0; padding: 35px; background: #fff; border: 1px solid var(--border-blue); border-radius: 20px; box-shadow: var(--shadow-sm); }.join-state-heading { text-align: center; margin-bottom: 28px; }.join-state-icon { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 18px; margin: 0 auto 18px; background: #e9f4ff; color: #2878d5; font: 23px ui-monospace, monospace; }.join-state-heading .join-eyebrow { display: block; margin-bottom: 10px; font-size: 9px; }.join-state-heading h2 { font-size: 26px; color: var(--ink-900); }.join-state-heading p { font-size: 13px; color: var(--ink-500); line-height: 1.9; margin-top: 10px; }
.join-info-card { background: #f4f9ff; padding: 22px; border: 1px solid #e0ecf8; border-radius: 13px; }.join-info-card h3, .join-group h3 { font-size: 14px; color: var(--ink-700); margin-bottom: 12px; }.join-info-card dl > div { display: flex; justify-content: space-between; gap: 15px; padding: 12px 0; border-bottom: 1px solid #e0ecf8; font-size: 13px; align-items: flex-start; }.join-info-card dl > div:last-child { border: 0; padding-bottom: 0; }.join-info-card dt { color: #7b93aa; flex-shrink: 0; }.join-info-card dd { min-width: 0; text-align: right; overflow-wrap: anywhere; color: var(--ink-700); }.join-contact { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 8px; }.join-contact > span { user-select: text; }.copy-btn { border: 1px solid #ccdef0; color: var(--primary); border-radius: 7px; padding: 5px 10px; background: #fff; font-size: 11px; }
.payment-section { margin: 25px 0; text-align: center; }.qr-tabs { display: inline-flex; padding: 4px; gap: 5px; border: 1px solid #dbe8f5; background: #f3f8ff; border-radius: 11px; margin-bottom: 18px; }.qr-tabs button { min-height: 38px; padding: 8px 22px; border-radius: 7px; color: #7890a9; font-size: 13px; }.qr-tabs .active { background: #fff; color: var(--primary); box-shadow: 0 2px 6px #35699b0b; }.join-qr { display: block; width: min(240px, 100%); height: auto; aspect-ratio: 1; object-fit: contain; margin: 0 auto; border: 1px solid #e1edf8; border-radius: 12px; padding: 10px; background: #fff; }.payment-form .form-label > span { color: var(--ink-400); font-weight: 400; }.join-primary { display: flex; width: 100%; justify-content: space-between; min-height: 47px; margin-top: 22px; }.join-secondary { display: block; margin: 17px auto 0; color: #7891a9; font-size: 12px; min-height: 38px; }.join-secondary:hover { color: var(--primary); }
.join-group { text-align: center; margin-top: 25px; }.join-group .btn { margin-top: 18px; }.join-benefits { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-block: 35px; }.join-benefits span { border: 1px solid #dceaf8; background: #f5faff; border-radius: 10px; font-size: 12px; color: #6688a9; padding: 18px 6px; text-align: center; }.join-inline-note { font-size: 12px; color: var(--ink-500); line-height: 1.9; margin-top: 18px; text-align: center; overflow-wrap: anywhere; }.join-feedback { border-radius: 9px; padding: 12px; font-size: 12px; line-height: 1.7; margin-top: 16px; background: #edf6ff; color: #346faa; }.join-feedback--error { background: #fff1f1; color: #c75151; }
@media (max-width: 760px) { .join-layout { grid-template-columns: minmax(0, 1fr); gap: 16px; }.join-guide { position: static; padding: 22px; }.join-guide > :is(h2, p), .join-guide__footer { display: none; }.join-steps { grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 20px 0 0; gap: 3px; }.join-steps li { flex-direction: column; align-items: center; text-align: center; gap: 9px; font-size: 11px; min-height: 0; }.join-steps li > span { z-index: 1; }.join-steps small { display: none; }.join-steps li:not(:last-child)::before { left: 50%; right: -50%; width: auto; height: 1px; top: 16px; bottom: auto; }.join-content { padding: 26px 22px; border-radius: 17px; }.join-state-heading h2 { font-size: 23px; }.join-info-card { padding: 18px; }.join-qr { width: min(250px, 100%); }.payment-form .input { font-size: 16px; } }
@media (max-width: 360px) { .join-guide { padding: 20px 14px; }.join-content { padding: 24px 16px; }.join-info-card { padding: 15px; }.join-info-card dl > div { gap: 10px; }.join-benefits { gap: 5px; }.join-benefits span { font-size: 11px; } }
</style>
