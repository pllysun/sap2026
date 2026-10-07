<template>
  <div class="home-app-download" :aria-busy="loading || downloading">
    <span class="app-download-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><rect x="6" y="2" width="12" height="20" rx="3" /><path d="M9 6h6m-3 12h.01M12 9v6m-3-3 3 3 3-3" /></svg></span>
    <div class="app-download-copy"><router-link v-if="introduction" class="app-introduction-link" to="/schedule-app" aria-label="了解软协课表 App 的功能"><strong>软协课表 App</strong><em>功能介绍</em><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg></router-link><strong v-else>软协课表 App</strong><span v-if="loading" class="app-download-skeleton"></span><span v-else-if="version?.versionCode > 0">Android · v{{ version.versionName }} · {{ (Number(version.size) / 1048576).toFixed(1) }} MB</span><span v-else>{{ error || '安装包暂未发布' }}</span></div>
    <button v-if="error && !version" class="app-download-button app-download-retry" :disabled="loading" @click="load">重试</button>
    <button v-else class="app-download-button" :disabled="loading || downloading || !version?.versionCode" @click="download"><svg v-if="!downloading" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 3v12m-5-5 5 5 5-5M4 16v4h16v-4" /></svg><span v-else class="app-download-spinner" aria-hidden="true"></span>{{ downloading ? '准备下载' : '下载 App' }}</button>
    <p v-if="error && version" class="app-download-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import request from '@/utils/request'
defineProps({ introduction: { type: Boolean, default: true } })
const version = ref(null), loading = ref(true), downloading = ref(false), error = ref('')
let disposed = false
async function load() {
  loading.value = true; error.value = ''
  try { const result = await request.get('/api/app/latest'); if (!disposed) version.value = result.data }
  catch { if (!disposed) error.value = '安装包信息未能加载' }
  finally { if (!disposed) loading.value = false }
}
async function download() {
  if (downloading.value) return
  downloading.value = true; error.value = ''
  try {
    const result = await request.post('/api/app/download/tickets')
    if (disposed) return
    const url = new URL(result.data.downloadUrl, window.location.origin)
    if (url.origin !== window.location.origin || url.pathname !== '/api/app/download/file') throw new Error('下载地址无效')
    const link = document.createElement('a'); link.href = url.href; link.download = `sap-${version.value.versionCode}.apk`; link.referrerPolicy = 'no-referrer'; document.body.append(link); link.click(); link.remove()
  } catch (e) { if (!disposed) error.value = e.response?.data?.message || e.message || '下载暂不可用，请稍后再试' }
  finally { if (!disposed) downloading.value = false }
}
onMounted(load)
onUnmounted(() => { disposed = true })
</script>

<style scoped>
.app-introduction-link{display:flex;align-items:center;gap:5px;width:fit-content;max-width:100%;line-height:normal;text-decoration:none}.app-introduction-link strong{white-space:nowrap}.app-introduction-link em{font-size:10px;font-style:normal;color:#577998;white-space:nowrap}.home-app-download .app-introduction-link svg{width:11px;height:11px;flex-shrink:0;color:#577998}.app-introduction-link:hover strong{color:#2b6dae}.app-introduction-link:focus-visible{outline:2px solid #3564dc;outline-offset:4px;border-radius:2px}@media(max-width:390px){.app-introduction-link em{display:none}}
.home-app-download{display:grid;grid-template-columns:40px 1fr auto;align-items:center;column-gap:13px;max-width:510px;margin-top:24px;padding:13px 15px;border:1px solid #d9e7f5;border-radius:12px;background:linear-gradient(112deg,#f0f7ff,#fff 80%)}.app-download-mark{display:grid;place-items:center;width:40px;height:40px;border-radius:10px;color:#397fbd;background:#dfedfd}.home-app-download svg{width:20px;height:20px;stroke:currentColor;stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round}.app-download-copy{display:flex;flex-direction:column;gap:5px;min-width:0}.app-download-copy strong{font-size:13px;color:#294665}.app-download-copy span{font-size:10px;line-height:1.6;color:#7a91aa}.app-download-button{display:flex;gap:7px;align-items:center;padding:9px 12px;border:1px solid #cbdff3;border-radius:8px;background:#fff;color:#2b6dae;font-size:11px;font-weight:600;white-space:nowrap;cursor:pointer;transition:background .2s,transform .2s}.app-download-button:hover:not(:disabled){background:#eaf3ff;transform:translateY(-1px)}.app-download-button:disabled{opacity:.5;cursor:default}.app-download-button svg{width:14px;height:14px}.app-download-error{grid-column:2/-1;margin:8px 0 0;font-size:11px;color:#ae6348;line-height:1.6}.app-download-skeleton{width:135px;height:12px;border-radius:4px;background:linear-gradient(90deg,#e1eaf4,#f6f9ff,#e1eaf4);background-size:200% 100%;animation:app-skeleton 1.6s linear infinite}.app-download-spinner{width:12px;height:12px;border:1.5px solid #c1d4e8;border-top-color:#2b6dae;border-radius:50%;animation:app-spin .7s linear infinite}@keyframes app-spin{to{transform:rotate(360deg)}}@keyframes app-skeleton{to{background-position:-200% 0}}@media(max-width:390px){.home-app-download{padding:12px;gap:10px;grid-template-columns:32px 1fr}.app-download-mark{width:32px;height:36px}.app-download-button{grid-column:2;justify-self:start;margin-top:3px}}@media(prefers-reduced-motion:reduce){.home-app-download *{animation:none!important;transition:none!important}}
</style>
