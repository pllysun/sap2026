<template>
  <div class="join-wrapper">
    <div class="join-content anim-in">
      <!-- 审核已通过 或 已是成员 -->
      <template v-if="isMember || (application && application.status === 2)">
        <div class="join-flow success-flow anim-in">
          <div class="join-result" style="padding-bottom: 24px;">
            <div class="success-icon-wrap">
              <span class="success-icon">🏆</span>
            </div>
            <h2 class="t-heading gradient-text" style="font-size: 28px; font-weight: 800;">欢迎入会</h2>
            <p class="t-body mt-2" style="color: #666; font-size: 15px;">你已是软件协会正式成员，开启极客之旅！</p>
          </div>

          <!-- 负责人信息 (卡片化更美观) -->
          <div class="premium-card" v-if="application && application.managerName">
            <div class="card-header-flex">
              <span class="emoji-icon">🎓</span>
              <h3 class="card-title">你的专属迎新负责人</h3>
            </div>
            <div class="premium-info-list">
              <div class="premium-info-item">
                <span class="label">姓名</span>
                <span class="val fw-600">{{ application.managerName }}</span>
              </div>
              <div class="premium-info-item">
                <span class="label">联系方式 (QQ)</span>
                <div class="val-flex">
                  <span class="fw-500">{{ application.managerQq }}</span>
                  <button class="copy-btn" @click="copyText(application.managerQq)">一键复制</button>
                </div>
              </div>
            </div>
          </div>

          <!-- QQ群信息 -->
          <div class="premium-card mt-4" v-if="publicSettings.join_qq_group_url || publicSettings.join_group_link" style="margin-top: 20px;">
            <div class="card-header-flex justify-center" style="margin-bottom: 16px;">
              <span class="emoji-icon">💬</span>
              <h3 class="card-title">加入 {{ publicSettings.join_qq_group_name || '官方新生群' }}</h3>
            </div>
            
            <div class="qr-glass-container">
              <!-- Using margin: 0 auto and block display guarantees horizontal centering explicitly -->
              <img v-if="publicSettings.join_qq_group_url" :src="publicSettings.join_qq_group_url" class="premium-qr" alt="QQ群二维码" />
              <div v-if="publicSettings.join_group_link" class="action-btn-wrap" style="margin-top: 20px;">
                <a :href="publicSettings.join_group_link" target="_blank" rel="noopener noreferrer" class="cyber-btn">
                  🚀 唤起 QQ 一键加群
                </a>
              </div>
            </div>
          </div>

          <div class="bottom-action-area">
            <router-link to="/study" class="giant-primary-btn">
              ⚡ 开始学习
            </router-link>
          </div>
        </div>
      </template>

      <!-- 已提交等待审核 -->
      <template v-else-if="application && application.status === 1">
        <div class="join-result">
          <div class="join-result__icon">⏳</div>
          <h2 class="t-heading">等待审核</h2>
          <p class="t-body mt-2">您的支付信息已提交，截图加负责人QQ发送截图来加速审核</p>
          <div class="join-info-card mt-4">
            <div class="join-info-row">
              <span class="join-info-label">负责人</span>
              <span>{{ application.managerName }}</span>
            </div>
            <div class="join-info-row">
              <span class="join-info-label">QQ号</span>
              <div style="display:flex; align-items:center; gap:8px;">
                <span>{{ application.managerQq }}</span>
                <button class="btn btn--secondary btn--pill" style="padding: 2px 10px; font-size: 12px; height: 26px;" @click="copyText(application.managerQq)">复制</button>
              </div>
            </div>
            <div class="join-info-row">
              <span class="join-info-label">交易单号</span>
              <span>{{ application.paymentCode || '（空）' }}</span>
            </div>
          </div>
        </div>
      </template>

      <!-- 已分配负责人，待支付 -->
      <template v-else-if="application && application.status === 0">
        <div class="join-flow">
          <h2 class="t-heading" style="text-align:center;">加入软件协会</h2>
          <p class="t-body mt-2" style="text-align:center;color:#888;">请扫描负责人收款码完成缴费</p>

          <!-- 负责人信息 -->
          <div class="join-info-card mt-4">
            <div class="join-info-row">
              <span class="join-info-label">负责人</span>
              <span style="font-weight:600;">{{ application.managerName }}</span>
            </div>
            <div class="join-info-row">
              <span class="join-info-label">QQ号</span>
              <div style="display:flex; align-items:center; gap:8px;">
                <span>{{ application.managerQq }}</span>
                <button class="btn btn--secondary btn--pill" style="padding: 2px 10px; font-size: 12px; height: 26px;" @click="copyText(application.managerQq)">复制</button>
              </div>
            </div>
          </div>

          <!-- 收款码 -->
          <div class="qr-display mt-4">
            <div class="qr-tabs" v-if="hasWechat && hasAlipay">
              <button :class="['qr-tab', qrType === 'wechat' ? 'active' : '']" @click="qrType = 'wechat'">
                微信支付
              </button>
              <button :class="['qr-tab', qrType === 'alipay' ? 'active' : '']" @click="qrType = 'alipay'">
                支付宝
              </button>
            </div>
            <div class="qr-image-wrap">
              <img v-if="qrType === 'wechat' && hasWechat" :src="application.wechatQr" class="qr-image" />
              <img v-else-if="qrType === 'alipay' && hasAlipay" :src="application.alipayQr" class="qr-image" />
              <img v-else-if="hasWechat" :src="application.wechatQr" class="qr-image" />
              <img v-else-if="hasAlipay" :src="application.alipayQr" class="qr-image" />
              <p v-else style="color:#ccc;">暂无收款码</p>
            </div>
          </div>

          <!-- 支付编码输入 -->
          <div class="payment-input mt-4">
            <input v-model="paymentCode" class="input-field" placeholder="输入交易单号（可选）" />
            <button class="btn btn--primary btn--pill" @click="handleSubmit" :disabled="submitting"
              style="height:44px;padding:0 28px;margin-top:12px;width:100%;">
              {{ submitting ? '提交中...' : '提交' }}
            </button>
          </div>

          <!-- 刷新负责人 -->
          <div v-if="application.canRefresh" class="mt-3" style="text-align:center;">
            <button class="btn btn--secondary btn--pill" @click="handleRefresh" :disabled="refreshing"
              style="height:38px;padding:0 20px;font-size:13px;">
              {{ refreshing ? '刷新中...' : '🔄 更换负责人' }}
            </button>
          </div>
        </div>
      </template>

      <!-- 未发起申请：入口 -->
      <template v-else>
        <div class="join-result">
          <div class="join-result__icon">🏠</div>
          <h2 class="t-heading">加入软件协会</h2>
          <p class="t-body mt-2" style="max-width:400px;margin:0 auto;">
            加入中南林业科技大学软件协会，与志同道合的伙伴一起学习编程技术
          </p>
          <button class="btn btn--primary btn--pill mt-4" @click="handleApply" :disabled="applying"
            style="height:46px;padding:0 32px;">
            {{ applying ? '申请中...' : '🎯 申请加入' }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const userStore = useUserStore()

const application = ref(null)
const isMember = computed(() => userStore.roles.some(r => r <= 3 && r !== 4))
const paymentCode = ref('')
const submitting = ref(false)
const refreshing = ref(false)
const applying = ref(false)
const qrType = ref('wechat')
const publicSettings = ref({})
const hasWechat = computed(() => application.value?.wechatQr)
const hasAlipay = computed(() => application.value?.alipayQr)

const loadApplication = async () => {
  try {
    const res = await request.get('/api/join/my-application')
    application.value = res.data
  } catch (e) {}
}

const loadPublicSettings = async () => {
  try {
    const res = await request.get('/api/setting/public')
    publicSettings.value = res.data || {}
  } catch (e) {}
}

const copyText = (text) => {
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    alert('QQ号已复制成功')
  }).catch(() => {
    alert('复制失败，请手动选择复制')
  })
}

const handleApply = async () => {
  applying.value = true
  try {
    const res = await request.post('/api/join/apply')
    application.value = res.data
    qrType.value = application.value?.wechatQr ? 'wechat' : 'alipay'
  } catch (e) {
    alert(e.response?.data?.msg || '申请失败')
  } finally {
    applying.value = false
  }
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    await request.post('/api/join/submit-payment', { paymentCode: paymentCode.value })
    await loadApplication()
  } catch (e) {
    alert(e.response?.data?.msg || '提交失败')
  } finally {
    submitting.value = false
  }
}

const handleRefresh = async () => {
  refreshing.value = true
  try {
    const res = await request.post('/api/join/refresh-manager')
    application.value = res.data
    qrType.value = application.value?.wechatQr ? 'wechat' : 'alipay'
  } catch (e) {
    alert(e.response?.data?.msg || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  await loadApplication()
  if (isMember.value || (application.value && application.value.status === 2)) {
    loadPublicSettings()
  }
})
</script>

<style scoped>
.join-wrapper {
  min-height: calc(100vh - 150px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}
.join-content {
  max-width: 480px;
  width: 100%;
}
.join-result {
  text-align: center;
  padding: 40px 20px;
}
.join-result__icon {
  font-size: 3rem;
  margin-bottom: 16px;
}
.join-flow {
  padding: 20px 0;
}
.join-info-card {
  background: rgba(139,115,85,0.04);
  border-radius: 12px;
  padding: 16px 20px;
}
.join-info-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid rgba(139,115,85,0.08);
}
.join-info-row:last-child { border-bottom: none; }
.join-info-label {
  color: #999;
  font-size: 14px;
}
.qr-display {
  text-align: center;
}
.qr-tabs {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 16px;
}
.qr-tab {
  padding: 8px 20px;
  border-radius: 20px;
  border: 1px solid #e0d5c3;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: #8b7355;
  transition: all 0.2s;
}
.qr-tab.active {
  background: linear-gradient(135deg, #c9a96e, #8b7355);
  color: #fff;
  border-color: transparent;
}
.qr-image-wrap {
  display: flex;
  justify-content: center;
}
.qr-image {
  width: 220px;
  height: 220px;
  border-radius: 12px;
  object-fit: cover;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
}
.payment-input {
  text-align: center;
}
.input-field {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e0d5c3;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  transition: border 0.2s;
}
.input-field:focus {
  border-color: #c9a96e;
}

@media (max-width: 768px) {
  .join-wrapper {
    min-height: calc(100vh - 100px);
    padding: 16px 12px;
    align-items: flex-start;
  }
  .join-content {
    max-width: 100%;
  }
  .join-result {
    padding: 28px 8px;
  }
  .join-result__icon {
    font-size: 2.5rem;
    margin-bottom: 12px;
  }
  .join-flow {
    padding: 12px 0;
  }
  .join-info-card {
    padding: 12px 14px;
    border-radius: 10px;
  }
  .join-info-row {
    padding: 6px 0;
    font-size: 0.9rem;
  }
  /* QR code enlarged on mobile */
  .qr-image {
    width: 260px;
    height: 260px;
    border-radius: 14px;
  }
  .qr-tabs {
    gap: 6px;
    margin-bottom: 12px;
  }
  .qr-tab {
    padding: 8px 18px;
    font-size: 13px;
    border-radius: 16px;
  }
  .payment-input {
    margin-top: 16px;
  }
  .input-field {
    padding: 14px 16px;
    font-size: 15px;
    border-radius: 12px;
  }
}

@media (max-width: 380px) {
  .qr-image {
    width: 220px;
    height: 220px;
  }
}

/* Premium Success UI Styles */
.success-flow {
  animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
.success-icon-wrap {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #f5f0e6 0%, #fae8c8 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  box-shadow: 0 8px 24px rgba(201, 169, 110, 0.2);
}
.success-icon {
  font-size: 40px;
}
.gradient-text {
  background: linear-gradient(135deg, #8b7355 0%, #c9a96e 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.premium-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03);
  border: 1px solid rgba(224, 213, 195, 0.4);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.premium-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.05);
}
.card-header-flex {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.card-header-flex.justify-center {
  justify-content: center;
}
.emoji-icon {
  font-size: 22px;
}
.card-title {
  font-size: 17px;
  font-weight: 700;
  color: #333;
  margin: 0;
}
.premium-info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.premium-info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  background: #fdfcfb;
  border-radius: 12px;
  border: 1px solid #f2eadb;
}
.premium-info-item .label {
  color: #888;
  font-size: 14px;
}
.fw-600 { font-weight: 600; color: #333; }
.val-flex {
  display: flex;
  align-items: center;
  gap: 12px;
}
.copy-btn {
  background: #f5f0e6;
  color: #8b7355;
  border: none;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.copy-btn:hover {
  background: #eadecb;
}
.qr-glass-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(145deg, #ffffff 0%, #fefcfb 100%);
  border-radius: 16px;
  border: 1px dashed #e0d5c3;
}
.premium-qr {
  width: 200px;
  height: 200px;
  object-fit: contain;
  border-radius: 12px;
  box-shadow: 0 12px 28px rgba(0,0,0,0.08);
  display: block;
  margin: 0 auto;
}
.action-btn-wrap {
  width: 100%;
  display: flex;
  justify-content: center;
}
.cyber-btn {
  background: linear-gradient(135deg, #12B7F5 0%, #0099d0 100%);
  color: #fff;
  padding: 14px 36px;
  border-radius: 30px;
  font-weight: 700;
  font-size: 16px;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(18, 183, 245, 0.35);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.cyber-btn:hover {
  transform: translateY(-2px) scale(1.03);
  box-shadow: 0 12px 30px rgba(18, 183, 245, 0.45);
}
.bottom-action-area {
  margin-top: 32px;
  text-align: center;
  padding: 0 12px;
}
.giant-primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 56px;
  background: linear-gradient(135deg, #2a2a2a 0%, #111111 100%);
  color: #fff !important;
  font-size: 16px;
  font-weight: 700;
  border-radius: 28px;
  text-decoration: none;
  box-shadow: 0 12px 28px rgba(0,0,0,0.2);
  transition: all 0.3s;
}
.giant-primary-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 36px rgba(0,0,0,0.25);
  background: linear-gradient(135deg, #333 0%, #1a1a1a 100%);
}

@keyframes slideUp {
  0% { opacity: 0; transform: translateY(20px); }
  100% { opacity: 1; transform: translateY(0); }
}
</style>
