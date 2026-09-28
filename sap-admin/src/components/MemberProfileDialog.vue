<template>
  <el-dialog :model-value="modelValue" title="成员详情" width="1080px" append-to-body align-center
    modal-class="member-profile-modal" class="member-profile-dialog" @update:model-value="emit('update:modelValue', $event)">
    <div v-if="outstanding" class="standalone-profile">
      <h2>{{ outstanding.name }}</h2><p class="profile-muted">优秀成员档案 · 未关联注册账号，不按同名匹配其他成员数据</p>
      <dl class="profile-facts"><div v-for="[key, label] in outstandingFields" :key="key"><dt>{{ label }}</dt><dd>{{ outstanding[key] || '未记录' }}</dd></div></dl>
      <p class="standalone-bio">{{ outstanding.bio || '暂无个人简介' }}</p>
    </div>
    <div v-else v-loading="loading" class="profile-content">
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-button v-if="error && !profile" @click="loadProfile">重新加载</el-button>
      <template v-if="profile">
        <div class="profile-identity"><div class="profile-avatar">{{ profile.user.name?.slice(0, 1) || '?' }}</div>
          <div><h2>{{ profile.user.name }} <small v-if="profile.user.nickname && profile.user.nickname !== profile.user.name">{{ profile.user.nickname }}</small></h2>
            <div class="profile-roles"><el-tag v-for="role in profile.roles" :key="role" size="small" effect="plain">{{ roles[role] || role }}</el-tag><el-tag size="small" :type="profile.user.status === 1 ? 'success' : 'danger'">{{ profile.user.status === 1 ? '正常' : '已禁用' }}</el-tag></div>
          </div><span class="profile-muted profile-id">ID {{ profile.user.id }}</span>
        </div>
        <dl class="profile-facts">
          <div><dt>账号 / 学号</dt><dd>{{ profile.user.studentId || '未记录' }}</dd></div>
          <div><dt>QQ</dt><dd>{{ profile.user.qq || '未填写' }}</dd></div>
          <div><dt>年级 / 性别</dt><dd>{{ profile.user.grade || '未记录' }} · {{ profile.user.gender === 1 ? '男' : profile.user.gender === 0 ? '女' : '未填写' }}</dd></div>
          <div><dt>注册时间</dt><dd>{{ date(profile.user.createdAt) }}</dd></div>
          <div><dt>入会审核通过</dt><dd>{{ profile.joinedAt ? date(profile.joinedAt) : '无入会审核记录' }}</dd></div>
          <div><dt>首次任职建档</dt><dd>{{ date(profile.firstArchivedAt) }}</dd></div>
        </dl>
        <div class="profile-business">
          <nav class="profile-nav" aria-label="成员业务资料分类"><button v-for="item in profile.sections" :key="item.key" type="button"
              :class="{ selected: section === item.key }" :aria-pressed="section === item.key" @click="selectSection(item.key)">
              <span>{{ item.label }}</span><b>{{ item.total }}</b></button></nav>
          <section class="profile-records" v-loading="recordsLoading">
            <div class="profile-records-heading"><h3>{{ sectionLabel }}</h3><span class="profile-muted">共 {{ total }} 条</span></div>
            <el-alert v-if="recordsError" :title="recordsError" type="error" :closable="false" /><el-button v-if="recordsError" @click="loadRecords">重试</el-button>
            <el-table :key="section" :data="records" stripe empty-text="暂无关联记录" max-height="345" size="small">
              <el-table-column v-for="column in columns" :key="column[0]" :prop="column[0]" :label="column[1]" :min-width="column[2] || 130">
                <template #default="{ row }"><span class="profile-cell">{{ cell(row, column[0]) }}</span></template>
              </el-table-column>
            </el-table>
            <div class="profile-pagination"><span class="profile-muted">每页 12 条 · 最新在前</span><el-pagination small background :disabled="recordsLoading" layout="prev, pager, next" :pager-count="5" :current-page="page" :page-size="12" :total="total" @current-change="changePage" /></div>
          </section>
        </div>
        <p class="profile-footnote">仅关联明确的用户 ID，不含接口日志、访问轨迹、密码及教务凭据。未记录的日期不以注册时间推算。</p>
      </template>
    </div>
    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { getMemberProfile, getMemberRelations } from '../api'
const props = defineProps({ modelValue: Boolean, userId: [Number, String], outstanding: Object })
const emit = defineEmits(['update:modelValue'])
const profile = ref(null), loading = ref(false), error = ref(''), recordsError = ref(''), recordsLoading = ref(false)
const section = ref('terms'), page = ref(1), records = ref([]), total = ref(0)
let generation = 0, recordRequest = 0
const roles = { 0: '超级管理员', 1: '会长', 2: '管理员', 3: '会员', 4: '游客' }
const outstandingFields = [['grade', '届别'], ['gender', '性别'], ['major', '专业'], ['destination', '去向'], ['destinationDetail', '学校 / 单位']]
const schemas = {
  terms: [['grade', '届别', 85], ['positionName', '任职身份'], ['createdAt', '建档时间', 160]],
  joins: [['status', '申请状态', 100], ['managerName', '负责人'], ['approverName', '审核人'], ['createdAt', '申请时间', 160], ['submittedAt', '提交时间', 160], ['approvedAt', '通过时间', 160]],
  recruitment: [['grade', '负责年级'], ['createdAt', '设置时间', 160]],
  study: [['activityName', '学习活动', 180], ['grade', '年级', 80], ['week', '周期', 65], ['leaderName', '负责人'], ['score', '评分', 65], ['comment', '评语', 180], ['status', '活动状态', 90], ['createdAt', '加入时间', 160]],
  leading: [['activityName', '学习活动', 180], ['grade', '年级', 80], ['memberCount', '带队人数', 90], ['status', '活动状态', 90], ['createdAt', '开始时间', 160]],
  materials: [['activityName', '学习活动', 170], ['week', '周期', 65], ['fileType', '类型', 100], ['title', '标题', 160], ['fileName', '文件名', 180], ['createdAt', '上传时间', 160]],
  reviews: [['activityName', '学习活动', 160], ['week', '周期', 65], ['memberName', '被评分成员'], ['score', '评分', 65], ['comment', '评语', 220], ['createdAt', '评分时间', 160]],
  notes: [['title', '笔记标题', 180], ['description', '简介', 220], ['viewCount', '阅读', 70], ['downloadCount', '下载', 70], ['createdAt', '发布时间', 160]],
  messages: [['kind', '类型', 70], ['content', '内容（最多 2000 字）', 350], ['createdAt', '时间', 160]],
  likes: [['kind', '点赞对象', 90], ['content', '内容摘要', 350], ['createdAt', '时间', 160]],
  feedback: [['title', '问题标题', 180], ['content', '内容（最多 2000 字）', 250], ['status', '状态', 90], ['category', '分类', 90], ['appVersion', 'App 版本', 90], ['createdAt', '提交时间', 160], ['updatedAt', '最后更新', 160]],
  feedbackReplies: [['issueId', '问题编号', 80], ['title', '关联问题', 180], ['content', '回复（最多 2000 字）', 300], ['adminReply', '回复身份', 100], ['createdAt', '回复时间', 160]],
}
const columns = computed(() => schemas[section.value] || [])
const sectionLabel = computed(() => profile.value?.sections.find(item => item.key === section.value)?.label || '')
const date = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未记录'
function cell(row, key) {
  const value = row[key]
  if (key.endsWith('At')) return date(value)
  if (value == null || value === '') return '—'
  if (key === 'fileType') return ['学习资料', '作业题目', '提交作业'][value] || value
  if (key === 'adminReply') return value ? '管理员' : '用户'
  if (key === 'status') {
    if (section.value === 'joins') return ['待提交', '已提交', '已通过'][value] || value
    if (['study', 'leading'].includes(section.value)) return value === 1 ? '进行中' : '已结束'
    return { OPEN: '待处理', CLOSED: '已关闭', open: '待处理', closed: '已关闭' }[value] || value
  }
  return value
}
async function loadProfile() {
  const current = ++generation; recordRequest++; profile.value = null; records.value = []; error.value = ''; loading.value = true
  try {
    const data = (await getMemberProfile(props.userId)).data
    if (current !== generation) return
    profile.value = data; section.value = data.sections.find(item => Number(item.total) > 0)?.key || 'terms'; page.value = 1
    await loadRecords()
  } catch (e) { if (current === generation) error.value = e.message || '成员详情加载失败' }
  finally { if (current === generation) loading.value = false }
}
async function loadRecords() {
  const current = ++recordRequest, owner = generation
  recordsLoading.value = true; recordsError.value = ''; records.value = []
  total.value = Number(profile.value?.sections.find(item => item.key === section.value)?.total || 0)
  try {
    const data = (await getMemberRelations(props.userId, { section: section.value, current: page.value, size: 12 })).data || {}
    if (current !== recordRequest || owner !== generation) return
    records.value = data.records || []; total.value = Number(data.total || 0)
  } catch (e) { if (current === recordRequest && owner === generation) recordsError.value = e.message || '关联资料加载失败' }
  finally { if (current === recordRequest && owner === generation) recordsLoading.value = false }
}
function selectSection(key) { if (section.value === key) return; section.value = key; page.value = 1; loadRecords() }
function changePage(value) { page.value = value; loadRecords() }
watch(() => [props.modelValue, props.userId, props.outstanding], () => {
  if (props.modelValue && props.userId && !props.outstanding) loadProfile()
  else { generation++; recordRequest++; profile.value = null; records.value = []; loading.value = false; recordsLoading.value = false }
}, { immediate: true })
</script>

<style>
html body .member-profile-modal .el-overlay-dialog { display:flex; align-items:center; padding:18px; overflow:hidden !important; }
html body .el-dialog.member-profile-dialog { width:min(1080px,100%); max-height:calc(100dvh - 36px) !important; display:flex; flex-direction:column; margin:auto !important; overflow:hidden !important; }
html body .member-profile-dialog .el-dialog__body { flex:1; min-height:0; overflow:auto !important; }
.member-profile-dialog .profile-content { min-height:180px; }
.member-profile-dialog h2 { margin:0 0 8px; font-size:22px; }.member-profile-dialog h2 small { font-weight:400; font-size:13px; color:var(--zen-text-muted); }
.profile-identity { display:flex; align-items:center; gap:14px; }.profile-avatar { width:54px; height:54px; flex:none; border-radius:16px; display:grid; place-items:center; background:var(--el-color-primary-light-9); color:var(--el-color-primary); font-size:24px; font-weight:700; }
.profile-roles { display:flex; gap:6px; flex-wrap:wrap; }.profile-id { margin-left:auto; }.profile-muted { font-size:12px; color:var(--zen-text-muted); }
.profile-facts { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; padding:18px; background:var(--zen-bg-warm); border-radius:12px; margin:18px 0; }
.profile-facts dt { font-size:12px; color:var(--zen-text-muted); margin-bottom:5px; }.profile-facts dd { margin:0; font-size:13px; overflow-wrap:anywhere; }.standalone-bio { line-height:1.8; white-space:pre-wrap; }
.profile-business { display:grid; grid-template-columns:150px minmax(0,1fr); gap:20px; }
.profile-nav { display:flex; flex-direction:column; gap:3px; max-height:440px; overflow:auto; }.profile-nav button { border:0; background:transparent; color:var(--zen-text-secondary); cursor:pointer; display:flex; justify-content:space-between; align-items:center; gap:8px; padding:9px 12px; border-radius:9px; font:inherit; font-size:13px; text-align:left; }.profile-nav button b { font-size:11px; font-weight:500; }.profile-nav button.selected { background:var(--el-color-primary-light-9); color:var(--el-color-primary); font-weight:600; }.profile-nav button:hover { background:var(--zen-bg-warm); }.profile-nav button:focus-visible { outline:2px solid var(--el-color-primary); }
.profile-records { min-width:0; }.profile-records-heading { display:flex; align-items:center; justify-content:space-between; margin:4px 0 12px; }.profile-records-heading h3 { margin:0; font-size:15px; }.profile-cell { white-space:pre-wrap; overflow-wrap:anywhere; }.profile-pagination { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:8px; margin-top:14px; }.profile-footnote { font-size:11px; color:var(--zen-text-muted); margin:16px 0 0; line-height:1.6; }
@media(max-width:700px) { .profile-facts { grid-template-columns:repeat(2,minmax(0,1fr)); padding:12px; gap:12px; }.profile-business { grid-template-columns:1fr; gap:12px; }.profile-nav { flex-direction:row; flex-wrap:nowrap; overflow:auto; }.profile-nav button { flex:none; }.profile-id { display:none; } }
</style>
