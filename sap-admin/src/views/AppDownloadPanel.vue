<template>
  <div class="download-panel" v-loading="loading">
    <div class="download-head">
      <div><h3>下载防护</h3><p>全平台下载次数按北京时间统计</p></div>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>
    <template v-if="state">
      <div class="download-overview">
        <article><el-icon><Download /></el-icon><span>今日下载</span><strong>{{ state.todayTotal }}</strong><small>{{ state.today }}</small></article>
        <article><el-icon><Odometer /></el-icon><span>防护计数</span><strong>{{ state.guardCount }}</strong><small>切换下载方式后重新计数</small></article>
        <article class="download-mode"><el-icon><component :is="state.mode === 'COS' ? Cloudy : Monitor" /></el-icon><span>当前下载方式</span><strong>{{ state.mode === 'COS' ? '对象存储' : '服务器' }}</strong><small>{{ state.autoSwitched ? '已超过阈值，等待管理员恢复' : '管理员设定' }}</small></article>
      </div>
      <div class="download-settings">
        <section><h4>下载方式</h4><p>对象存储使用短时签名链接；服务器模式使用本地校验缓存，不向客户端暴露存储地址。</p>
          <div class="mode-control"><el-switch :model-value="state.mode === 'COS'" :disabled="!canEdit || saving" :loading="switching" inline-prompt active-text="COS" inactive-text="服务器" @change="changeMode" /><span>{{ state.mode === 'COS' ? '对象存储下载' : '服务器下载' }}</span></div>
          <p class="download-note">切换方式会清空防护计数并使待使用凭证失效，历史每日统计保留。自动切换后不会在跨日或重启时恢复。</p>
        </section>
        <el-form label-position="top" :disabled="!canEdit || saving || switching" @submit.prevent="save">
          <el-form-item label="异常下载接收邮箱"><el-input v-model="form.alertEmail" maxlength="254" placeholder="1125887000@qq.com" /></el-form-item>
          <div class="threshold-fields"><el-form-item label="每日超过此次数发送邮件"><el-input-number v-model="form.alertLimit" :min="1" :max="99999" :precision="0" controls-position="right" /></el-form-item><el-form-item label="每日超过此次数使用服务器下载"><el-input-number v-model="form.proxyLimit" :min="2" :max="100000" :precision="0" controls-position="right" /></el-form-item></div>
          <el-button v-if="canEdit" type="primary" native-type="submit" :icon="Check" :loading="saving">保存配置</el-button>
          <span class="save-note">同一天、同一轮防护仅发送一次阈值告警</span>
        </el-form>
      </div>
      <div class="download-head history-head"><h4>每日下载统计</h4><el-select v-model="days" size="small" style="width:130px" @change="load"><el-option :value="7" label="近 7 天" /><el-option :value="30" label="近 30 天" /><el-option :value="90" label="近 90 天" /></el-select></div>
      <el-table :data="state.records" size="small" stripe><el-table-column prop="download_date" label="日期" min-width="130" /><el-table-column prop="total_count" label="下载次数" /><el-table-column prop="cos_count" label="对象存储" /><el-table-column prop="server_count" label="服务器" /><el-table-column prop="failed_count" label="服务端失败" /><el-table-column label="安装包计量" min-width="120"><template #default="{ row }">{{ (Number(row.estimated_bytes) / 1048576).toFixed(1) }} MiB</template></el-table-column><template #empty>暂无下载记录</template></el-table>
      <p class="download-note">下载次数按有效凭证被兑换统计，未使用或无效凭证不计数；对象存储下载不代表已完成传输。</p>
    </template>
    <div v-else-if="!loading" class="download-empty">下载防护未能加载 <el-button size="small" @click="load">重试</el-button></div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Cloudy, Download, Monitor, Odometer, Refresh } from '@element-plus/icons-vue'
import request from '../utils/request'
defineProps({ canEdit: { type: Boolean, default: false } })
const state = ref(null), days = ref(30), loading = ref(false), saving = ref(false), switching = ref(false)
const form = reactive({ alertEmail: '', alertLimit: 20, proxyLimit: 50 })
let generation = 0, disposed = false
function apply(data) { state.value = data; Object.assign(form, { alertEmail: data.alertEmail, alertLimit: Number(data.alertLimit), proxyLimit: Number(data.proxyLimit) }) }
async function load() {
  const current = ++generation; loading.value = true
  try { const result = await request.get('/api/app/download/admin', { params: { days: days.value } }); if (!disposed && current === generation) apply(result.data) }
  catch {} finally { if (!disposed && current === generation) loading.value = false }
}
async function save() {
  if (saving.value || switching.value) return
  if (!/^[^\s@,;<>]+@[^\s@,;<>]+\.[^\s@,;<>]+$/.test(form.alertEmail.trim()) || form.proxyLimit <= form.alertLimit) { ElMessage.warning('请填写有效邮箱，并确保服务器下载阈值大于告警阈值'); return }
  saving.value = true
  try { const result = await request.put('/api/app/download/admin', { ...form, alertEmail: form.alertEmail.trim(), revision: state.value.revision }); if (!disposed) { apply(result.data); ElMessage.success('下载防护配置已保存') } }
  catch {} finally { saving.value = false }
}
async function changeMode(cos) {
  if (switching.value || saving.value) return
  try { await ElMessageBox.confirm(`切换为${cos ? '对象存储' : '服务器'}下载，并清空本轮防护计数？每日历史统计保留。`, '切换下载方式', { confirmButtonText: '切换并重置', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  switching.value = true
  try { const result = await request.post('/api/app/download/admin/mode', { mode: cos ? 'COS' : 'SERVER', revision: state.value.revision }); if (!disposed) { apply(result.data); ElMessage.success('下载方式已切换') } }
  catch {} finally { switching.value = false }
}
onMounted(load)
onUnmounted(() => { disposed = true; generation++ })
</script>

<style scoped>
.download-panel{padding:8px 4px 20px}.download-head{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:20px}.download-head h3,.download-head h4{margin:0;color:#22374f}.download-head p{font-size:12px;color:#7b8ba0;margin:7px 0 0}.download-overview{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-bottom:26px}.download-overview article{position:relative;display:flex;flex-direction:column;gap:9px;padding:20px;border:1px solid #e2e9f3;border-radius:12px;background:#f9fbfe}.download-overview .el-icon{position:absolute;right:18px;top:20px;color:#7193bb;font-size:18px}.download-overview span{font-size:12px;color:#718399}.download-overview strong{font-size:28px;color:#244765;font-variant-numeric:tabular-nums}.download-overview small{font-size:11px;color:#8090a4}.download-overview .download-mode{background:#eff5fd}.download-mode strong{font-size:23px}.download-settings{display:grid;grid-template-columns:1fr 1fr;gap:34px;border-bottom:1px solid #e5ebf4;padding-bottom:24px}.download-settings h4{margin:0 0 12px}.download-settings p,.download-note{font-size:12px;line-height:1.8;color:#7b8ba0}.mode-control{display:flex;align-items:center;gap:14px;margin:20px 0;font-size:13px;color:#315173}.threshold-fields{display:grid;grid-template-columns:1fr 1fr;gap:18px}.threshold-fields .el-input-number{width:100%}.save-note{font-size:11px;color:#8798ac;margin-left:14px}.history-head{margin:24px 0 14px}.download-empty{padding:60px;text-align:center;color:#7b8ba0}@media(max-width:900px){.download-settings{grid-template-columns:1fr}.download-overview{grid-template-columns:1fr}.threshold-fields{grid-template-columns:1fr}.save-note{display:block;margin:14px 0 0}}
</style>
