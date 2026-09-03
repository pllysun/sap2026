<template>
  <div class="class-panel" v-loading="loading">
    <section class="class-section">
      <div class="section-head">
        <div>
          <h3>班级课表采集</h3>
          <p>按新版教务四个数据页串行采集，四表分别保存并在 App 查询时合并。</p>
        </div>
        <el-button type="primary" :loading="pulling" :disabled="!canEdit" @click="pullNow">立即采集</el-button>
      </div>
      <div class="toolbar">
        <el-select v-model="pullForm.term" clearable placeholder="全部学期" style="width: 220px">
          <el-option v-for="term in terms" :key="term.value" :label="`${term.label || term.value}（${term.rowCount || 0} 条）`" :value="term.value" />
        </el-select>
        <span class="toolbar-hint">采集时输入一次教务账号和密码，密码不会保存</span>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <div class="term-grid">
        <div v-for="term in terms" :key="term.value" class="term-card">
          <strong>{{ term.label || term.value }}</strong>
          <span>班级 {{ term.classCount ?? term.rowCount ?? 0 }} 条</span>
          <span>教师 {{ term.teacherCount || 0 }} · 教室 {{ term.roomCount || 0 }} · 课程 {{ term.courseCount || 0 }}</span>
          <small>开学：{{ term.semesterStartDate || '待教学日历补全' }}</small>
        </div>
        <el-empty v-if="!terms.length" description="暂无采集数据" :image-size="50" />
      </div>
    </section>

    <section class="class-section">
      <div class="section-head"><div><h3>采集日志</h3><p>记录触发方式、执行人、学期、来源页、状态与写入条数。</p></div></div>
      <el-table :data="logs" stripe empty-text="暂无日志" max-height="460">
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发" width="90" />
        <el-table-column prop="actorName" label="执行人" width="120" />
        <el-table-column prop="term" label="学期" width="140" />
        <el-table-column prop="sourceType" label="来源" width="100" />
        <el-table-column prop="rowCount" label="条数" width="90" />
        <el-table-column prop="message" label="详情" min-width="320" show-overflow-tooltip />
        <el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDate(row.finishedAt || row.startedAt) }}</template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="credentialDialogVisible" title="输入教务账号" width="460px" destroy-on-close @closed="clearCredentialForm">
      <p class="dialog-tip">本次采集会读取所有学期。密码仅用于本次登录，不会写入会员教务账号，也不会显示在采集日志中。</p>
      <el-form label-position="top" @submit.prevent="submitPull">
        <el-form-item label="教务账号" required>
          <el-input v-model="pullCredentials.account" autocomplete="username" placeholder="请输入学校教务账号" @keyup.enter="submitPull" />
        </el-form-item>
        <el-form-item label="教务密码" required>
          <el-input v-model="pullCredentials.password" type="password" show-password autocomplete="current-password" placeholder="请输入教务密码" @keyup.enter="submitPull" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="credentialDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pulling" @click="submitPull">开始采集</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mfaDialogVisible" title="短信二次验证" width="460px" destroy-on-close @closed="clearMfaForm">
      <p class="dialog-tip">该教务账号已向 {{ mfa.phone || '安全手机' }} 发送验证码。请输入验证码后继续本次采集。</p>
      <el-form label-position="top" @submit.prevent="submitMfa">
        <el-form-item label="短信验证码" required>
          <el-input v-model="mfa.code" inputmode="numeric" maxlength="8" autocomplete="one-time-code"
                    placeholder="请输入短信验证码" @keyup.enter="submitMfa" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button link :loading="mfaResending" @click="resendMfa">重新发送</el-button>
        <el-button @click="mfaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="mfaSubmitting" @click="submitMfa">继续采集</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getClassScheduleAdmin, pullClassSchedule, resendClassScheduleMfa, submitClassScheduleMfa } from '../api'

const props = defineProps({
  canEdit: { type: Boolean, default: false },
})
const loading = ref(false); const pulling = ref(false); const mfaSubmitting = ref(false); const mfaResending = ref(false)
const terms = ref([]); const logs = ref([])
const pullForm = reactive({ term: '' })
const pullCredentials = reactive({ account: '', password: '' })
const credentialDialogVisible = ref(false)
const mfaDialogVisible = ref(false)
const mfa = reactive({ challengeId: '', phone: '', code: '' })

onMounted(load)
async function load() {
  loading.value = true
  try {
    const result = await getClassScheduleAdmin(); const data = result.data || {}
    terms.value = data.terms || []; logs.value = data.logs || []
  } finally { loading.value = false }
}
async function pullNow() {
  if (!props.canEdit) return
  pullCredentials.account = ''
  pullCredentials.password = ''
  credentialDialogVisible.value = true
}
function clearCredentialForm() {
  pullCredentials.account = ''
  pullCredentials.password = ''
}
function clearMfaForm() {
  mfa.challengeId = ''
  mfa.phone = ''
  mfa.code = ''
}
async function submitPull() {
  if (pulling.value) return
  const account = pullCredentials.account.trim()
  const password = pullCredentials.password
  if (!account) return ElMessage.warning('请输入教务账号')
  if (!password) return ElMessage.warning('请输入教务密码')
  pulling.value = true
  try {
    const result = await pullClassSchedule({ term: pullForm.term || null, account, password })
    const data = result.data || {}
    if (data.needMfa) {
      mfa.challengeId = data.challengeId || ''
      mfa.phone = data.phone || ''
      mfa.code = ''
      credentialDialogVisible.value = false
      mfaDialogVisible.value = true
      ElMessage.info('短信验证码已发送，请输入后继续采集')
      return
    }
    credentialDialogVisible.value = false
    ElMessage.success('班级课表采集完成')
    await load()
  } finally {
    pulling.value = false
    // 不在组件状态中保留手动输入的密码。
    pullCredentials.password = ''
  }
}
async function submitMfa() {
  if (mfaSubmitting.value) return
  if (!mfa.challengeId) return ElMessage.warning('短信验证会话已失效，请重新开始采集')
  if (!mfa.code.trim()) return ElMessage.warning('请输入短信验证码')
  mfaSubmitting.value = true
  try {
    await submitClassScheduleMfa({ challengeId: mfa.challengeId, code: mfa.code.trim() })
    mfaDialogVisible.value = false
    ElMessage.success('班级课表采集完成')
    await load()
  } finally {
    mfaSubmitting.value = false
    mfa.code = ''
  }
}
async function resendMfa() {
  if (mfaResending.value || !mfa.challengeId) return
  mfaResending.value = true
  try {
    const result = await resendClassScheduleMfa({ challengeId: mfa.challengeId })
    const data = result.data || {}
    if (data.phone) mfa.phone = data.phone
    ElMessage.success('验证码已重新发送')
  } finally { mfaResending.value = false }
}
const formatDate = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
</script>

<style scoped>
.class-panel { display: grid; gap: 18px; padding: 10px 0 2px; }
.class-section { border: 1px solid var(--zen-border-light); border-radius: 14px; padding: 20px; background: var(--zen-card); }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 18px; }
.section-head h3 { margin: 0 0 5px; font-size: 17px; }.section-head p,.hint { margin: 0; color: var(--zen-text-muted); font-size: 12px; line-height: 1.6; }
.toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }.toolbar-hint { color: var(--zen-text-muted); font-size: 12px; }.term-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(190px,1fr)); gap: 10px; margin-top: 16px; }.term-card { display: grid; gap: 5px; padding: 12px; border: 1px solid var(--zen-border); border-radius: 10px; }.term-card span,.term-card small { color: var(--zen-text-muted); font-size: 12px; }.dialog-tip { margin: -4px 0 18px; color: var(--zen-text-muted); font-size: 13px; line-height: 1.65; }
</style>
