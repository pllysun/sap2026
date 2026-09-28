<template>
  <div class="class-panel" v-loading="loading">
    <section class="class-section">
      <div class="section-head">
        <div><h3>班级课表采集</h3><p>更新班级、教师、教室和课程四类课表数据。</p></div>
        <el-button type="primary" :disabled="!canEdit" @click="activeBatchId ? openProgress() : switchDialog('setup')">
          {{ activeBatchId ? '查看采集进度' : '立即采集' }}
        </el-button>
      </div>
      <div class="toolbar"><span class="muted">采集时选择学期并验证教务账号，密码不会保存</span><el-button :icon="Refresh" @click="load">刷新</el-button></div>
      <div class="term-grid">
        <div v-for="term in terms" :key="term.value" class="term-card">
          <strong>{{ term.label || term.value }}</strong>
          <span>班级 {{ number(term.classCount ?? term.rowCount) }} 条</span>
          <span>教师 {{ number(term.teacherCount) }} · 教室 {{ number(term.roomCount) }} · 课程 {{ number(term.courseCount) }}</span>
          <small>开学：{{ term.semesterStartDate || '待教学日历补全' }}</small>
        </div>
        <el-empty v-if="!terms.length" description="暂无采集数据" :image-size="50" />
      </div>
    </section>

    <el-alert v-if="refreshError" :title="refreshError" type="warning" :closable="false" show-icon />
    <AcademicCalendarPanel ref="calendarPanel" :can-edit="canEdit" @changed="loadSummary" />

    <section class="class-section collection-records">
      <div class="section-head"><div><h3>采集记录</h3><p>按采集时间倒序 · 采集结束后自动更新全部数据</p></div>
        <el-button :icon="Refresh" :loading="logsLoading" @click="loadLogs">刷新记录</el-button></div>
      <div class="log-summary"><div><strong>{{ stats.total || 0 }}</strong><span>采集批次</span></div><div><strong>{{ stats.success || 0 }}</strong><span>已完成</span></div><div><strong>{{ stats.failed || 0 }}</strong><span>失败</span></div></div>
      <div class="log-toolbar"><el-segmented v-model="logFilter" :options="logFilterOptions" />
        <el-input v-model="logSearch" clearable placeholder="筛选学期、执行人或批次" style="max-width: 280px" /></div>
      <el-empty v-if="!batches.length && !logsLoading" description="暂无符合条件的采集记录" :image-size="60" />
      <el-collapse v-else v-model="expandedBatches" class="batch-list" v-loading="logsLoading">
        <el-collapse-item v-for="batch in batches" :key="batch.batchId" :name="batch.batchId">
          <template #title><div class="batch-heading">
            <el-tag :type="statusType(batch.status)" effect="light" round>{{ statusText(batch.status) }}</el-tag>
            <strong>{{ batch.term || '全部学期' }}</strong><span>{{ batch.triggerType === 'AUTO' ? '自动任务' : '手动触发' }} · {{ batch.actorName || '系统' }}</span>
            <span class="batch-time">{{ formatDate(batch.startedAt) }}</span><b>{{ number(batch.totalRows) }} 条</b>
          </div></template>
          <div class="batch-detail">
            <div class="batch-detail-head"><small>批次 {{ batch.batchId }}</small><el-button v-if="isWorking(batch.status)" size="small" @click="openProgress(batch.batchId)">查看进度</el-button></div>
            <p v-if="batch.status === 'FAILED'" class="progress-error">{{ batch.latest?.message || '采集已中断' }}</p>
            <div class="source-results">
              <div v-for="item in orderedSources(batch.sources)" :key="`${item.term}:${item.sourceType}`" class="source-result">
                <div><strong>{{ sourceText(item.sourceType) }}</strong><span class="muted">{{ item.term }}</span></div>
                <el-tag :type="statusType(resultStatus(item, batch.status))" size="small">{{ statusText(resultStatus(item, batch.status)) }}</el-tag>
                <span class="result-pages">{{ pageLabel(item) }}</span><b>{{ number(item.rowCount) }} 条</b>
              </div>
            </div>
            <div class="history-toggle"><el-button text :icon="histories[batch.batchId]?.open ? ArrowUp : ArrowDown" @click="toggleHistory(batch.batchId)">{{ histories[batch.batchId]?.open ? '收起过程' : `查看历史过程（${batch.eventCount || 0}）` }}</el-button></div>
            <div v-if="histories[batch.batchId]?.open" v-loading="histories[batch.batchId].loading" class="history-panel">
              <small class="muted">历史过程 · 最新在前</small>
              <el-timeline class="batch-events"><el-timeline-item v-for="event in histories[batch.batchId].records" :key="event.id" :type="statusType(event.status)" :timestamp="formatDate(event.startedAt)">
                <div class="event-heading"><strong>{{ sourceText(event.sourceType) }}</strong><span>{{ event.term }}</span><el-tag size="small" :type="statusType(event.status)">{{ historyStatus(event.status) }}</el-tag></div><p>{{ event.message }}</p>
              </el-timeline-item></el-timeline>
              <el-pagination small layout="prev, pager, next" :page-size="20" :total="histories[batch.batchId].total" :current-page="histories[batch.batchId].page" @current-change="page => loadHistory(batch.batchId, page)" />
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
      <el-pagination v-if="logTotal > 10" class="batch-pagination" background layout="total, prev, pager, next" :total="logTotal" :page-size="10" v-model:current-page="logPage" @current-change="loadLogs" />
    </section>

    <el-dialog :model-value="dialog === 'setup'" title="开始采集课表" width="500px" append-to-body align-center modal-class="class-modal" class="class-dialog" @update:model-value="value => closeDialog('setup', value)" @closed="dialogClosed('setup')">
      <el-form label-position="top" @submit.prevent="submitPull">
        <el-form-item label="采集范围" required><el-select v-model="pullForm.term" :empty-values="[null, undefined]" class="term-select" placeholder="请选择采集学期">
          <el-option label="全部学期" value="" /><el-option v-for="term in terms" :key="term.value" :label="term.label || term.value" :value="term.value" />
        </el-select></el-form-item>
        <p class="scope-hint">{{ pullForm.term ? `仅更新 ${pullForm.term} 学期的四类课表` : '依次更新教务系统中的全部学期及四类课表' }}</p>
        <el-form-item label="教务账号" required><el-input v-model="credentials.account" autocomplete="username" placeholder="请输入学校教务账号" /></el-form-item>
        <el-form-item label="教务密码" required><el-input v-model="credentials.password" type="password" show-password autocomplete="current-password" placeholder="请输入教务密码" @keyup.enter="submitPull" /></el-form-item>
      </el-form>
      <p class="dialog-tip">密码仅用于本次身份验证，不会保存或显示在日志中。</p>
      <template #footer><el-button @click="dialog = null">取消</el-button><el-button type="primary" :loading="submitting" @click="submitPull">开始采集</el-button></template>
    </el-dialog>

    <el-dialog :model-value="dialog === 'verify'" title="短信二次验证" width="460px" append-to-body align-center modal-class="class-modal" class="class-dialog" @update:model-value="value => closeDialog('verify', value)" @closed="dialogClosed('verify')">
      <p class="dialog-tip">验证码已发送至 {{ mfa.phone || '安全手机' }}，验证后继续本次采集。</p>
      <el-input v-model="mfa.code" inputmode="numeric" maxlength="8" autocomplete="one-time-code" placeholder="请输入短信验证码" @keyup.enter="submitMfa" />
      <p v-if="progress.status === 'PENDING' && progress.latest?.message !== '等待短信二次验证'" class="progress-error">{{ progress.latest?.message }}</p>
      <template #footer><el-button text :loading="mfaResending" @click="resendMfa">重新发送</el-button><el-button @click="switchDialog('progress')">返回进度</el-button><el-button type="primary" :loading="mfaSubmitting" @click="submitMfa">验证并继续</el-button></template>
    </el-dialog>

    <el-dialog :model-value="dialog === 'progress'" title="班级课表采集进度" width="min(760px, 94vw)" append-to-body align-center modal-class="class-modal" class="class-dialog progress-dialog" :close-on-click-modal="false" @update:model-value="value => closeDialog('progress', value)" @closed="dialogClosed('progress')">
      <div class="progress-overview" :class="{ working: isWorking(progress.status), failed: progress.status === 'FAILED' }">
        <div class="page-stack" aria-hidden="true"><i></i><i></i><i></i><span>{{ progress.status === 'SUCCESS' ? '✓' : progress.status === 'FAILED' ? '!' : '↓' }}</span></div>
        <div class="progress-title"><h3>{{ progressTitle }}</h3><p>{{ currentTerm || pullForm.term || '全部学期' }}<template v-if="progress.totalTerms"> · 已完成 {{ progress.completedTerms || 0 }} / {{ progress.totalTerms }} 个学期</template></p></div>
        <div class="total-counter"><strong>{{ number(displayCounts.total) }}</strong><span>条已解析数据</span></div>
      </div>
      <div class="progress-steps"><div v-for="(label, index) in stepLabels" :key="label" :class="{ reached: stage >= index, current: stage === index && isWorking(progress.status) }"><span>{{ stage > index || progress.status === 'SUCCESS' ? '✓' : index + 1 }}</span>{{ label }}</div></div>
      <div class="source-progress-grid">
        <div v-for="source in sources" :key="source.key" class="source-progress" :class="{ collecting: currentSources[source.key]?.phase === 'COLLECT' && isWorking(progress.status), complete: currentSources[source.key]?.status === 'SUCCESS' }">
          <div class="source-card-head"><strong>{{ source.label }}课表</strong><span>{{ statusText(resultStatus(currentSources[source.key], progress.status)) }}</span></div>
          <div class="page-counter"><b>{{ currentSources[source.key]?.completedPages ?? 0 }}</b><span>/ {{ knownCount(currentSources[source.key]?.totalPages) ? currentSources[source.key].totalPages : '—' }} 页</span></div>
          <div class="page-meter" :class="{ indeterminate: !knownCount(currentSources[source.key]?.totalPages) && currentSources[source.key]?.phase === 'COLLECT' && isWorking(progress.status) }"><i :style="{ width: `${pagePercent(currentSources[source.key])}%` }"></i></div>
          <div class="source-card-count"><strong>{{ number(displayCounts[source.key]) }} 条</strong><span>{{ knownCount(currentSources[source.key]?.reportedRows) ? `源站共 ${number(currentSources[source.key].reportedRows)} 条` : '等待源站返回总数' }}</span></div>
        </div>
      </div>
      <div class="live-message" aria-live="polite"><span class="live-dot" :class="{ pulse: isWorking(progress.status) }"></span><span>{{ progress.latest?.message || '正在创建采集任务…' }}</span></div>
      <p v-if="pollError" class="progress-error">{{ pollError }}</p>
      <p v-if="refreshError" class="progress-error">{{ refreshError }}</p>
      <div class="progress-meta"><span>批次 {{ progress.batchId || activeBatchId }}</span><span>{{ elapsedLabel }}</span></div>
      <template #footer><span v-if="isWorking(progress.status)" class="footer-hint">关闭弹窗后任务仍会继续</span><el-button v-if="progress.needMfa" type="primary" @click="switchDialog('verify')">输入验证码</el-button><el-button @click="dialog = null">{{ isWorking(progress.status) ? '在后台继续' : '完成' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import AcademicCalendarPanel from './AcademicCalendarPanel.vue'
import { getClassScheduleAdmin, getClassScheduleBatches, getClassScheduleHistory, getClassScheduleProgress, pullClassSchedule, resendClassScheduleMfa, submitClassScheduleMfa } from '../api'

const props = defineProps({ canEdit: { type: Boolean, default: false } })
const loading = ref(false), submitting = ref(false), logsLoading = ref(false), mfaSubmitting = ref(false), mfaResending = ref(false)
const terms = ref([]), batches = ref([]), stats = ref({}), activeBatchId = ref('')
const calendarPanel = ref(null)
const pullForm = reactive({ term: '' }), credentials = reactive({ account: '', password: '' })
const dialog = ref(null), nextDialog = ref(null), mfa = reactive({ challengeId: '', phone: '', code: '' })
const sources = [{ key: 'class', label: '班级' }, { key: 'teacher', label: '教师' }, { key: 'room', label: '教室' }, { key: 'course', label: '课程' }]
const stepLabels = ['身份验证', '获取学期', '逐页采集', '保存课表']
const progress = ref({ status: 'QUEUED', sources: [] }), pollError = ref(''), refreshError = ref(''), now = ref(Date.now())
const displayCounts = reactive({ total: 0, class: 0, teacher: 0, room: 0, course: 0 })
const logFilter = ref('ALL'), logSearch = ref(''), logPage = ref(1), logTotal = ref(0), expandedBatches = ref([]), histories = reactive({})
const logFilterOptions = [{ label: '全部', value: 'ALL' }, { label: '进行中', value: 'RUNNING' }, { label: '待验证', value: 'PENDING' }, { label: '已完成', value: 'SUCCESS' }, { label: '失败', value: 'FAILED' }]
let pollTimer, clockTimer, countFrame, searchTimer, refreshTimer, polling = false, disposed = false, pollGeneration = 0, logRequest = 0, lastTarget = '', shownMfa = ''
const currentTerm = computed(() => progress.value.latest?.term || progress.value.sources?.[0]?.term || progress.value.term || '')
const currentSources = computed(() => Object.fromEntries((progress.value.sources || []).filter(item => item.term === currentTerm.value).map(item => [item.sourceType, item])))
const progressTitle = computed(() => ({ QUEUED: '正在准备采集', PENDING: '等待短信验证', SUCCESS: '课表已更新', FAILED: '本次采集未完成' })[progress.value.status] || '正在采集课表')
const stage = computed(() => progress.value.status === 'SUCCESS' || ['WRITE', 'COMPLETE'].includes(progress.value.latest?.phase) ? 3 : (progress.value.sources?.length ? 2 : progress.value.latest?.phase === 'TERMS' ? 1 : 0))
const elapsedLabel = computed(() => {
  const start = new Date(progress.value.startedAt || now.value).getTime()
  const end = progress.value.finishedAt ? new Date(progress.value.finishedAt).getTime() : now.value
  const seconds = Math.max(0, Math.floor((end - start) / 1000))
  return `用时 ${Math.floor(seconds / 60)} 分 ${String(seconds % 60).padStart(2, '0')} 秒`
})
const number = value => Number(value || 0).toLocaleString('zh-CN')
const knownCount = value => value != null && Number(value) >= 0
const isWorking = status => ['QUEUED', 'RUNNING', 'PENDING'].includes(status)
const sourceText = value => ({ class: '班级课表', teacher: '教师课表', room: '教室课表', course: '课程课表', all: '整体采集' })[value] || '学期准备'
const statusText = value => ({ QUEUED: '准备中', RUNNING: '采集中', PENDING: '待验证', COLLECTED: '待写入', SUCCESS: '已完成', FAILED: '失败', INTERRUPTED: '已中断', WAITING: '等待采集' })[value] || value
const statusType = value => ({ SUCCESS: 'success', FAILED: 'danger', INTERRUPTED: 'info', RUNNING: 'warning', COLLECTED: 'warning' })[value] || 'info'
const historyStatus = value => ({ RUNNING: '过程记录', QUEUED: '任务创建', PENDING: '验证记录', COLLECTED: '读取完成' })[value] || statusText(value)
const formatDate = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
function resultStatus(item, batchStatus) {
  if (!item) return batchStatus === 'FAILED' ? 'INTERRUPTED' : 'WAITING'
  if (batchStatus === 'SUCCESS') return 'SUCCESS'
  if (batchStatus === 'FAILED' && ['RUNNING', 'COLLECTED'].includes(item.status)) return 'INTERRUPTED'
  if (item.phase === 'PREPARING') return 'WAITING'
  return item.status
}
function pageLabel(item) { return item.completedPages == null ? '页数未记录' : `${item.completedPages} / ${knownCount(item.totalPages) ? item.totalPages : '—'} 页` }
function pagePercent(item) { return ['COLLECTED', 'SUCCESS'].includes(item?.status) ? 100 : item?.totalPages > 0 ? Math.min(100, item.completedPages / item.totalPages * 100) : 0 }
function orderedSources(items = []) { return [...items].sort((a, b) => String(b.term).localeCompare(String(a.term)) || sources.findIndex(s => s.key === a.sourceType) - sources.findIndex(s => s.key === b.sourceType)) }
function switchDialog(view) { if (dialog.value && dialog.value !== view) { nextDialog.value = view; dialog.value = null } else dialog.value = view }
// Element Plus 在 closed 之后仍会发送 modelValue=false，旧弹窗不能关闭下一步的新弹窗。
function closeDialog(view, value) { if (!value && dialog.value === view) dialog.value = null }
function dialogClosed(view) { if (view === 'setup') { credentials.account = ''; credentials.password = '' } if (nextDialog.value) { dialog.value = nextDialog.value; nextDialog.value = null } }

onMounted(load)
onBeforeUnmount(() => { disposed = true; stopPolling(); clearTimeout(searchTimer); clearTimeout(refreshTimer); cancelAnimationFrame(countFrame) })
watch([logFilter, logSearch], () => { clearTimeout(searchTimer); logPage.value = 1; searchTimer = setTimeout(loadLogs, 250) })
async function loadSummary() {
  const data = (await getClassScheduleAdmin()).data || {}; terms.value = data.terms || []
  if (!submitting.value) {
    const incoming = data.activeBatchId || '', changed = incoming !== activeBatchId.value
    activeBatchId.value = incoming
    if (changed && polling) { stopPolling(); if (incoming) startPolling() }
  }
  return data
}
async function load() {
  loading.value = true
  try { await refreshAllData() }
  finally { loading.value = false }
}
async function refreshAllData() {
  if (disposed) return
  const jobs = [loadSummary(), loadLogs(), calendarPanel.value?.reload(),
    ...Object.entries(histories).filter(([, state]) => state.open).map(([id, state]) => loadHistory(id, state.page))]
  const results = await Promise.allSettled(jobs)
  clearTimeout(refreshTimer)
  if (!disposed && results.some(result => result.status === 'rejected')) {
    refreshError.value = '部分页面数据暂未更新，将自动重试，无需手动刷新。'
    refreshTimer = setTimeout(refreshAllData, 5000)
  } else if (!disposed) refreshError.value = ''
  // 首次读取失败后也可从重试中恢复正在执行的任务，而不必重新进入页面。
  if (activeBatchId.value && !polling && !disposed) startPolling()
}
async function loadLogs() {
  const request = ++logRequest; logsLoading.value = true
  try { const data = (await getClassScheduleBatches({ page: logPage.value, size: 10, status: logFilter.value, search: logSearch.value.trim() || undefined })).data || {}
    if (request === logRequest && !disposed) { batches.value = data.records || []; logTotal.value = data.total || 0; stats.value = data.stats || {} }
  } finally { if (request === logRequest) logsLoading.value = false }
}
async function toggleHistory(id) { if (!histories[id]) histories[id] = { open: false, records: [], total: 0, page: 1, loading: false }; histories[id].open = !histories[id].open; if (histories[id].open) await loadHistory(id, 1) }
async function loadHistory(id, page) { const state = histories[id], request = (state.request || 0) + 1; state.request = request; state.loading = true
  try { const data = (await getClassScheduleHistory({ batchId: id, page, size: 20 })).data || {}; if (request === state.request && !disposed) Object.assign(state, { records: data.records || [], total: data.total || 0, page }) } finally { if (request === state.request) state.loading = false }
}
function animateCounts() {
  const target = { total: Number(progress.value.totalRows || 0), ...Object.fromEntries(sources.map(source => [source.key, Number(currentSources.value[source.key]?.rowCount || 0)])) }
  const signature = JSON.stringify(target); if (signature === lastTarget) return; lastTarget = signature
  cancelAnimationFrame(countFrame)
  if (globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) { Object.assign(displayCounts, target); return }
  const initial = { ...displayCounts }, start = performance.now()
  const tick = time => { const fraction = Math.min(1, (time - start) / 800), eased = 1 - (1 - fraction) ** 3
    for (const key of Object.keys(target)) displayCounts[key] = Math.round(initial[key] + (target[key] - initial[key]) * eased)
    if (fraction < 1) countFrame = requestAnimationFrame(tick)
  }; countFrame = requestAnimationFrame(tick)
}
function stopPolling() { polling = false; pollGeneration++; clearTimeout(pollTimer); clearInterval(clockTimer) }
function startPolling() { stopPolling(); polling = true; clockTimer = setInterval(() => { now.value = Date.now() }, 1000); refreshProgress() }
async function refreshProgress() {
  const batch = activeBatchId.value, generation = pollGeneration; if (!batch || !polling || disposed) return
  try {
    const data = (await getClassScheduleProgress(batch)).data || {}; if (disposed || generation !== pollGeneration || batch !== activeBatchId.value) return
    progress.value = data; pollError.value = ''; animateCounts()
    if (data.needMfa) {
      mfa.challengeId = data.challengeId; mfa.phone = data.phone || ''
      const marker = `${data.challengeId}:${data.latest?.id}`
      if (marker !== shownMfa) { shownMfa = marker; mfa.code = ''; switchDialog('verify') }
    }
    if (['SUCCESS', 'FAILED'].includes(data.status)) {
      stopPolling(); activeBatchId.value = ''; now.value = Date.now()
      logPage.value = 1
      await refreshAllData(); return
    }
  } catch { if (generation === pollGeneration) pollError.value = '暂时无法获取进度，正在自动重试；后台任务会继续执行。' }
  if (polling && !disposed && generation === pollGeneration) pollTimer = setTimeout(refreshProgress, 2000)
}
async function openProgress(batch = activeBatchId.value) { if (!batch) return; const changed = batch !== activeBatchId.value; activeBatchId.value = batch; switchDialog('progress'); if (!polling || changed) startPolling() }
async function submitPull() {
  if (submitting.value || activeBatchId.value) return
  const account = credentials.account.trim(), password = credentials.password, term = pullForm.term || null
  if (!account || !password) return ElMessage.warning('请输入教务账号和密码')
  const batchId = globalThis.crypto?.randomUUID?.() || 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c === 'x' ? r : r & 3 | 8).toString(16) })
  submitting.value = true; activeBatchId.value = batchId; pollError.value = ''; shownMfa = ''; lastTarget = ''
  progress.value = { batchId, status: 'QUEUED', term, startedAt: new Date().toISOString(), sources: [], totalRows: 0 }
  for (const key of Object.keys(displayCounts)) displayCounts[key] = 0
  switchDialog('progress'); await nextTick()
  try { await pullClassSchedule({ term, account, password, batchId }); startPolling() }
  catch (error) {
    // 响应丢失并不代表后台没有启动；先用同一批次核对，避免重复采集。
    let accepted = false
    try { const data = (await getClassScheduleProgress(batchId)).data; accepted = Number(data?.eventCount) > 0; if (accepted) startPolling() } catch { /* 保留原请求错误 */ }
    if (!accepted) { activeBatchId.value = ''; progress.value = { ...progress.value, status: 'FAILED', latest: { message: error?.message || '任务未能启动，请稍后重试' }, finishedAt: new Date().toISOString() }; await loadSummary() }
  } finally { submitting.value = false; credentials.password = '' }
}
async function submitMfa() {
  if (mfaSubmitting.value || !mfa.challengeId || !mfa.code.trim()) return
  mfaSubmitting.value = true
  try { await submitClassScheduleMfa({ challengeId: mfa.challengeId, code: mfa.code.trim() }); progress.value = { ...progress.value, status: 'RUNNING', needMfa: false }; switchDialog('progress'); mfa.code = ''; if (!polling) startPolling() }
  finally { mfaSubmitting.value = false }
}
async function resendMfa() { if (mfaResending.value || !mfa.challengeId) return; mfaResending.value = true
  try { await resendClassScheduleMfa({ challengeId: mfa.challengeId }); ElMessage.success('验证码已重新发送') } finally { mfaResending.value = false }
}
</script>

<style scoped>
.class-panel { display: grid; gap: 20px; padding: 10px 0 2px; }
.class-section { min-width: 0; padding: 22px; border: 1px solid var(--zen-border-light); border-radius: 16px; background: var(--zen-card); }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 18px; }
.section-head h3 { margin: 0 0 6px; font-size: 17px; }.section-head p,.muted,.dialog-tip,.scope-hint { color: var(--zen-text-muted); font-size: 12px; line-height: 1.65; }.section-head p { margin: 0; }
.toolbar,.log-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }.toolbar { justify-content: space-between; }.term-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(190px,1fr)); gap: 10px; margin-top: 16px; }.term-card { display: grid; gap: 5px; padding: 14px; border: 1px solid var(--zen-border); border-radius: 12px; }.term-card span,.term-card small { color: var(--zen-text-muted); font-size: 12px; }
.term-select { width: 100%; }.scope-hint { margin: -10px 0 22px; }.dialog-tip { margin-bottom: 0; }.log-summary { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 10px; margin-bottom: 16px; }.log-summary > div { display: grid; gap: 4px; padding: 14px 17px; border-radius: 12px; background: var(--zen-bg-warm); }.log-summary strong { font-size: 23px; }.log-summary span { font-size: 12px; color: var(--zen-text-muted); }.log-toolbar { margin-bottom: 16px; }
.batch-list { border: 0; }.batch-heading { display: flex; flex: 1; align-items: center; flex-wrap: wrap; gap: 12px; padding: 12px 12px 12px 0; min-width: 0; }.batch-heading strong { font-size: 14px; }.batch-heading > span:not(.el-tag) { color: var(--zen-text-muted); font-size: 12px; }.batch-time { margin-left: auto; }.batch-heading b { color: var(--el-color-primary); font-size: 12px; }.batch-detail { border-radius: 12px; background: var(--zen-bg-warm); padding: 16px; }.batch-detail-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.batch-detail-head small { color: var(--zen-text-muted); overflow-wrap: anywhere; }.source-results { margin-top: 10px; }.source-result { display: flex; gap: 14px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--zen-border-light); font-size: 12px; }.source-result > div { display: flex; flex: 1; align-items: center; gap: 12px; }.source-result b { min-width: 75px; text-align: right; font-variant-numeric: tabular-nums; }.result-pages { color: var(--zen-text-muted); min-width: 95px; text-align: right; }.history-toggle { margin-top: 10px; }.history-panel { padding-top: 12px; border-top: 1px solid var(--zen-border); }.batch-events { margin: 18px 0 0; max-height: 400px; overflow: auto; padding-left: 3px; }.event-heading { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; font-size: 12px; }.batch-events p { color: var(--zen-text-secondary); font-size: 12px; margin: 6px 0; overflow-wrap: anywhere; }.batch-pagination { justify-content: flex-end; margin-top: 20px; }
.progress-overview { display: flex; align-items: center; gap: 20px; padding: 8px 0 22px; }.progress-title { flex: 1; }.progress-title h3 { margin: 0 0 7px; font-size: 20px; }.progress-title p { margin: 0; color: var(--zen-text-muted); font-size: 12px; }.total-counter { display: grid; text-align: right; gap: 4px; }.total-counter strong { color: var(--el-color-primary); font-size: 30px; line-height: 1.1; font-variant-numeric: tabular-nums; letter-spacing: -1px; }.total-counter span { font-size: 11px; color: var(--zen-text-muted); }
.page-stack { width: 55px; height: 62px; position: relative; flex: none; }.page-stack i { position: absolute; width: 36px; height: 45px; border: 1px solid var(--el-color-primary-light-5); border-radius: 8px; background: var(--el-color-primary-light-9); left: 2px; top: 2px; }.page-stack i:nth-child(2) { left: 9px; top: 8px; }.page-stack i:nth-child(3) { left: 16px; top: 14px; background: var(--zen-card); }.page-stack span { position: absolute; top: 24px; left: 27px; color: var(--el-color-primary); font-size: 22px; font-weight: 700; }.working .page-stack i { animation: page-flow 2s ease-in-out infinite; }.working .page-stack i:nth-child(2) { animation-delay: .15s; }.working .page-stack i:nth-child(3) { animation-delay: .3s; }.failed .page-stack span { color: var(--el-color-danger); }
.progress-steps { display: flex; border-top: 1px solid var(--zen-border-light); padding: 20px 0 23px; gap: 12px; }.progress-steps > div { display: flex; align-items: center; gap: 7px; flex: 1; font-size: 12px; color: var(--zen-text-muted); }.progress-steps span { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: var(--zen-bg-warm); flex: none; }.progress-steps .reached { color: var(--el-color-primary); }.progress-steps .reached span { background: var(--el-color-primary-light-9); }.progress-steps .current span { background: var(--el-color-primary); color: #fff; }
.source-progress-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; }.source-progress { border: 1px solid var(--zen-border-light); border-radius: 14px; padding: 17px; background: var(--zen-bg-warm); transition: border-color .4s,background .4s; }.source-progress.collecting { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }.source-card-head,.source-card-count { display: flex; justify-content: space-between; gap: 8px; align-items: center; }.source-card-head strong { font-size: 14px; }.source-card-head > span,.source-card-count > span { color: var(--zen-text-muted); font-size: 11px; }.page-counter { display: flex; align-items: baseline; gap: 6px; margin-top: 15px; font-variant-numeric: tabular-nums; }.page-counter b { font-size: 28px; line-height: 1.1; }.page-counter > span { color: var(--zen-text-muted); font-size: 12px; }.page-meter { height: 5px; overflow: hidden; border-radius: 8px; background: var(--zen-border-light); margin: 12px 0; }.page-meter i { display: block; height: 100%; border-radius: inherit; background: var(--el-color-primary); transition: width .7s ease; }.complete .page-meter i { background: var(--el-color-success); }.indeterminate i { width: 30% !important; animation: meter-flow 1.7s ease-in-out infinite; }.source-card-count strong { font-size: 13px; font-variant-numeric: tabular-nums; }
.live-message { display: flex; align-items: baseline; gap: 9px; margin-top: 20px; padding: 12px 14px; border-radius: 10px; background: var(--zen-bg-warm); font-size: 12px; color: var(--zen-text-secondary); line-height: 1.6; overflow-wrap: anywhere; }.live-dot { flex: none; width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-primary); }.live-dot.pulse { animation: live-pulse 1.4s infinite; }.progress-meta { display: flex; justify-content: space-between; gap: 12px; margin-top: 14px; font-size: 11px; color: var(--zen-text-muted); overflow-wrap: anywhere; }.progress-meta span:last-child { flex: none; }.progress-error { color: var(--el-color-danger); font-size: 12px; line-height: 1.6; }.footer-hint { color: var(--zen-text-muted); font-size: 12px; margin-right: auto; }
@keyframes page-flow { 50% { transform: translateY(-5px); } }@keyframes meter-flow { from { transform: translateX(-100%); } to { transform: translateX(440%); } }@keyframes live-pulse { 50% { opacity: .3; } }
@media (prefers-reduced-motion: reduce) { .working .page-stack i,.indeterminate i,.live-dot.pulse { animation: none; }.page-meter i { transition: none; } }
@media (max-width: 600px) { .class-section { padding: 16px; }.source-result { flex-wrap: wrap; gap: 9px; }.source-result > div { flex-basis: 100%; }.result-pages { text-align: left; }.batch-time { margin-left: 0; }.progress-overview { gap: 12px; flex-wrap: wrap; }.total-counter { margin-left: auto; }.progress-title h3 { font-size: 17px; }.progress-steps { gap: 5px; }.progress-steps > div { font-size: 10px; flex-direction: column; }.source-progress { padding: 12px; }.source-card-count { align-items: flex-start; flex-direction: column; }.progress-meta { flex-wrap: wrap; }.footer-hint { display: none; } }
</style>

<style>
html body .class-modal .el-overlay-dialog { display: flex; align-items: center; justify-content: center; padding: 16px; overflow: hidden !important; }
html body .el-dialog.class-dialog { margin: auto !important; max-width: 100%; max-height: calc(100dvh - 32px) !important; overflow: hidden !important; display: flex; flex-direction: column; }
html body .el-dialog.class-dialog .el-dialog__header,html body .el-dialog.class-dialog .el-dialog__footer { flex: none; }
html body .el-dialog.class-dialog .el-dialog__body { min-height: 0; overflow-y: auto !important; flex: 1; }
html body .progress-dialog .el-dialog__footer { display: flex; align-items: center; justify-content: flex-end; gap: 10px; }
html body .progress-dialog .el-dialog__footer .el-button + .el-button { margin-left: 0; }
</style>
