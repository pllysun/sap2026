<template>
  <div class="schedule-app-page zen-fade-in">
    <div class="page-header app-header">
      <div>
        <h2>软协课表</h2>
        <p>使用概览、用户意见反馈与 App 版本发布的一体化运营台</p>
      </div>
      <el-radio-group v-model="days" size="small" @change="loadSummary">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
    </div>

    <div class="summary-grid" v-loading="summaryLoading">
      <div class="summary-card primary">
        <div class="summary-icon">▦</div>
        <div><strong>{{ formatInt(summary.scheduleUsers) }}</strong><span>课表使用人数 · 近 {{ days }} 天去重</span></div>
      </div>
      <div class="summary-card open">
        <div class="summary-icon">●</div>
        <div><strong>{{ formatInt(summary.openIssues) }}</strong><span>待处理 Issue</span></div>
      </div>
      <div class="summary-card closed">
        <div class="summary-icon">✓</div>
        <div><strong>{{ formatInt(summary.closedIssues) }}</strong><span>已关闭 Issue</span></div>
      </div>
      <div class="summary-card version">
        <div class="summary-icon">↑</div>
        <div>
          <strong>{{ summary.latestVersion?.versionName ? 'v' + summary.latestVersion.versionName : '未发布' }}</strong>
          <span>线上版本{{ summary.latestVersion?.versionCode ? ' · build ' + summary.latestVersion.versionCode : '' }}</span>
        </div>
      </div>
    </div>

    <div class="zen-card workspace-card">
      <el-tabs v-model="activeTab" class="workspace-tabs">
        <el-tab-pane label="意见反馈" name="issues">
          <div class="issue-toolbar">
            <el-segmented v-model="filters.status" :options="statusOptions" @change="reloadIssues" />
            <el-select v-model="filters.category" style="width: 140px" @change="reloadIssues">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input v-model="filters.keyword" clearable placeholder="搜索标题或内容" style="max-width: 320px"
                      @keyup.enter="reloadIssues" @clear="reloadIssues">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button :icon="Refresh" :loading="issueLoading" @click="reloadIssues">刷新</el-button>
          </div>

          <el-table :data="issues" v-loading="issueLoading" row-key="id" class="issue-table"
                    @row-click="openIssue">
            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'" effect="plain" round>
                  {{ row.status === 'OPEN' ? '开放' : '已关闭' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Issue" min-width="360">
              <template #default="{ row }">
                <div class="issue-title"><span>#{{ row.id }}</span>{{ row.title }}</div>
                <div class="issue-preview">{{ excerpt(row.content) }}</div>
                <div v-if="row.images?.length" class="issue-attachments">图片附件 {{ row.images.length }} 张</div>
              </template>
            </el-table-column>
            <el-table-column label="分类" width="110">
              <template #default="{ row }"><el-tag size="small" :type="categoryType(row.category)">{{ row.categoryText }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="reporterName" label="提交人" width="130" />
            <el-table-column label="账号 / QQ" min-width="170">
              <template #default="{ row }">
                <div>账号：{{ row.reporterAccount || '—' }}</div>
                <div>QQ：{{ row.reporterQq || '—' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="回复" width="76" align="center">
              <template #default="{ row }"><span class="comment-count">◌ {{ row.commentCount }}</span></template>
            </el-table-column>
            <el-table-column label="版本" width="100">
              <template #default="{ row }">{{ row.appVersionName ? 'v' + row.appVersionName : '-' }}</template>
            </el-table-column>
            <el-table-column label="最近更新" width="170">
              <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <span>共 {{ total }} 条反馈</span>
            <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total"
                           layout="prev, pager, next" @current-change="loadIssues" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="课表云控" name="cloud" lazy>
          <ScheduleCloudPanel :can-edit="isLeaderOrSuper" />
        </el-tab-pane>

        <el-tab-pane label="班级课表" name="class-schedule" lazy>
          <ClassSchedulePanel :can-edit="isAdmin" />
        </el-tab-pane>

        <el-tab-pane v-if="isLeaderOrSuper" label="版本发布" name="release" lazy>
          <AppReleaseView embedded />
        </el-tab-pane>
        <el-tab-pane label="下载防护" name="downloads" lazy>
          <AppDownloadPanel :can-edit="isLeaderOrSuper" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 必须挂到 body：页面入场动画保留 transform，会让未 Teleport 的 fixed 抽屉只覆盖内容高度。 -->
    <el-drawer v-model="drawerOpen" size="min(760px, 92vw)" :with-header="false" destroy-on-close append-to-body>
      <div v-if="detail" class="issue-detail" v-loading="detailLoading">
        <div class="issue-scroll">
          <div class="detail-head">
            <div>
              <div class="detail-kicker">Issue #{{ detail.id }}</div>
              <h3>{{ detail.title }}</h3>
              <div class="detail-meta">
                <el-tag :type="detail.status === 'OPEN' ? 'success' : 'info'" effect="plain" round>
                  {{ detail.status === 'OPEN' ? '开放' : '已关闭' }}
                </el-tag>
                <el-tag size="small" :type="categoryType(detail.category)">{{ detail.categoryText }}</el-tag>
                <span>{{ detail.reporterName }} 提交于 {{ formatDate(detail.createdAt) }}</span>
              </div>
            </div>
            <el-button text :icon="Close" @click="drawerOpen = false" />
          </div>

          <section class="issue-body">
            <div class="author-line">
              <el-avatar :size="32" :src="detail.reporterAvatar">{{ detail.reporterName?.slice(0, 1) }}</el-avatar>
              <strong>{{ detail.reporterName }}</strong>
              <span>账号：{{ detail.reporterAccount || '—' }} · QQ：{{ detail.reporterQq || '—' }}</span>
              <span>反馈正文</span>
            </div>
            <div class="issue-content">{{ detail.content }}</div>
            <div v-if="detail.images?.length" class="issue-images">
              <el-image
                v-for="(url, index) in detail.images"
                :key="url"
                :src="url"
                :preview-src-list="detail.images"
                :initial-index="index"
                preview-teleported
                fit="cover"
                class="issue-image"
              />
            </div>
          </section>

          <el-descriptions v-if="detail.appVersionName" :column="1" border size="small" class="environment">
            <el-descriptions-item label="App 版本">
              {{ `v${detail.appVersionName} (build ${detail.appVersionCode || '-'})` }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="timeline-title">讨论时间线 · {{ detail.commentCount }} 条回复</div>
          <div v-if="detail.comments?.length" class="comment-list">
            <div v-for="thread in commentThreads" :key="thread.root.id" class="comment-thread">
              <article class="comment-card" :class="{ maintainer: thread.root.adminReply }">
                <div class="comment-head">
                  <div class="comment-author">
                    <el-avatar :size="28" :src="thread.root.authorAvatar">{{ thread.root.authorName?.slice(0, 1) }}</el-avatar>
                    <strong>{{ thread.root.authorName }}</strong>
                    <el-tag v-if="thread.root.adminReply" size="small" type="primary" effect="dark">维护者</el-tag>
                    <el-tag v-if="thread.root.questioner" size="small" type="warning" effect="plain">提问者</el-tag>
                  </div>
                  <span>{{ formatDate(thread.root.createdAt) }}</span>
                </div>
                <div class="comment-content">{{ thread.root.content }}</div>
                <div v-if="detail.status === 'OPEN' || isAdmin" class="comment-tools">
                  <el-button link type="primary" @click="beginReply(thread.root)">回复</el-button>
                </div>
              </article>
              <div v-if="thread.replies.length" class="nested-comments">
                <article
                  v-for="comment in visibleReplies(thread)"
                  :key="comment.id"
                  class="comment-card nested"
                  :class="{ maintainer: comment.adminReply }"
                >
                  <div class="comment-head">
                    <div class="comment-author">
                      <el-avatar :size="26" :src="comment.authorAvatar">{{ comment.authorName?.slice(0, 1) }}</el-avatar>
                      <strong>{{ comment.authorName }}</strong>
                      <el-tag v-if="comment.adminReply" size="small" type="primary" effect="dark">维护者</el-tag>
                      <el-tag v-if="comment.questioner" size="small" type="warning" effect="plain">提问者</el-tag>
                    </div>
                    <span>{{ formatDate(comment.createdAt) }}</span>
                  </div>
                  <div class="comment-content">{{ comment.content }}</div>
                  <div v-if="detail.status === 'OPEN' || isAdmin" class="comment-tools">
                    <el-button link type="primary" @click="beginReply(comment)">回复</el-button>
                  </div>
                </article>
                <el-button
                  v-if="visibleReplyCount(thread) < thread.replies.length"
                  link
                  type="primary"
                  class="more-replies"
                  @click="showMoreReplies(thread)"
                >
                  展开更多回复（剩余 {{ thread.replies.length - visibleReplyCount(thread) }} 条）
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else class="comment-empty" description="暂时还没有回复" :image-size="48" />
        </div>

        <section class="reply-box">
          <template v-if="detail.status === 'OPEN' || isAdmin">
            <div class="reply-label">
              <span>{{ replyTarget ? `回复 ${replyTarget.authorName}` : '维护者回复' }}</span>
              <el-button v-if="replyTarget" link @click="replyTarget = null">取消回复</el-button>
            </div>
            <el-input v-model="replyText" type="textarea" :rows="4" maxlength="2000" show-word-limit
                      :placeholder="replyTarget ? `回复 @${replyTarget.authorName}…` : '说明处理进展、解决方案或需要补充的信息…'" />
          </template>
          <el-alert v-else type="info" :closable="false" show-icon
                    title="该 Issue 已关闭，不能继续回复；新问题应新建 Issue。误关闭时可由管理端重新打开。" />
          <div class="reply-actions">
            <el-button class="delete-action" type="danger" :loading="deleteLoading" :disabled="replyLoading || statusLoading"
                       @click="removeIssue">删除</el-button>
            <el-button v-if="detail.status === 'OPEN'" type="warning" plain :loading="statusLoading"
                       :disabled="replyLoading || deleteLoading" @click="changeStatus('CLOSED')">
              关闭 Issue
            </el-button>
            <el-button v-else class="issue-reopen-button" type="success" plain :loading="statusLoading"
                       :disabled="replyLoading || deleteLoading" @click="changeStatus('OPEN')">
              重新打开
            </el-button>
            <el-button v-if="detail.status === 'OPEN' || isAdmin" type="primary" :loading="replyLoading" :disabled="statusLoading || deleteLoading"
                       @click="submitReply">发表回复</el-button>
          </div>
        </section>
      </div>
      <div v-else class="drawer-loading" v-loading="detailLoading">
        <span>{{ detailError || (detailLoading ? '正在加载 Issue…' : '未能加载 Issue') }}</span>
        <el-button v-if="detailError && selectedIssueId" size="small" @click="openIssue({ id: selectedIssueId })">重试</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Refresh, Search } from '@element-plus/icons-vue'
import AppReleaseView from './AppReleaseView.vue'
import AppDownloadPanel from './AppDownloadPanel.vue'
import ScheduleCloudPanel from './ScheduleCloudPanel.vue'
import ClassSchedulePanel from './ClassSchedulePanel.vue'
import {
  deleteFeedbackIssue,
  getFeedbackIssue,
  getFeedbackIssues,
  getScheduleAppSummary,
  getUserInfo,
  replyFeedbackIssue,
  updateFeedbackStatus,
} from '../api'
import { formatInt } from '../utils/format'

const route = useRoute()
const router = useRouter()
const roles = ref([])
const isLeaderOrSuper = computed(() => roles.value.includes(0) || roles.value.includes(1))
const isAdmin = computed(() => isLeaderOrSuper.value || roles.value.includes(2))
const activeTab = ref('issues')
const days = ref(7)
const summaryLoading = ref(false)
const summary = reactive({ scheduleUsers: 0, openIssues: 0, closedIssues: 0, latestVersion: null })
let summaryGeneration = 0

const statusOptions = [
  { label: '开放', value: 'OPEN' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '全部', value: 'ALL' },
]
const categoryOptions = [
  { label: '全部分类', value: 'ALL' },
  { label: '问题反馈', value: 'BUG' },
  { label: '功能建议', value: 'FEATURE' },
  { label: '体验优化', value: 'EXPERIENCE' },
  { label: '其他', value: 'OTHER' },
]
const filters = reactive({ status: 'OPEN', category: 'ALL', keyword: '' })
const issues = ref([])
const issueLoading = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)
let issueGeneration = 0

const drawerOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const selectedIssueId = ref(null)
const detailError = ref('')
const replyText = ref('')
const replyTarget = ref(null)
const visibleReplyCounts = reactive({})
const replyLoading = ref(false)
const statusLoading = ref(false)
const deleteLoading = ref(false)

onMounted(async () => {
  try {
    const result = await getUserInfo()
    roles.value = (result.data?.roles || []).map(Number)
    if (!isAdmin.value) {
      ElMessage.warning('无权访问软协课表管理页')
      router.push('/dashboard')
      return
    }
    if (route.query.tab === 'release' && isLeaderOrSuper.value) activeTab.value = 'release'
    if (route.query.tab === 'downloads') activeTab.value = 'downloads'
    await Promise.all([loadSummary(), loadIssues()])
  } catch (e) {}
})

async function loadSummary() {
  const generation = ++summaryGeneration
  const requestedDays = days.value
  summaryLoading.value = true
  try {
    const result = await getScheduleAppSummary(requestedDays)
    if (generation === summaryGeneration) Object.assign(summary, result.data || {})
  } catch (e) {
    // 全局请求拦截器负责提示，保留上一次概览数据。
  } finally {
    if (generation === summaryGeneration) summaryLoading.value = false
  }
}

async function loadIssues() {
  const generation = ++issueGeneration
  issueLoading.value = true
  try {
    const result = await getFeedbackIssues({
      current: page.value,
      size: pageSize,
      status: filters.status,
      category: filters.category,
      keyword: filters.keyword.trim() || undefined,
    })
    if (generation === issueGeneration) {
      issues.value = result.data?.records || []
      total.value = Number(result.data?.total || 0)
    }
  } catch (e) {
    // 全局请求拦截器负责提示，保留当前列表。
  } finally {
    if (generation === issueGeneration) issueLoading.value = false
  }
}

function reloadIssues() {
  page.value = 1
  loadIssues()
}

async function openIssue(row) {
  const issueId = row.id
  selectedIssueId.value = issueId
  drawerOpen.value = true
  detailLoading.value = true
  detail.value = null
  detailError.value = ''
  replyText.value = ''
  replyTarget.value = null
  Object.keys(visibleReplyCounts).forEach(key => delete visibleReplyCounts[key])
  try {
    const result = await getFeedbackIssue(issueId)
    if (selectedIssueId.value === issueId && drawerOpen.value) detail.value = result.data
  } catch (e) {
    if (selectedIssueId.value === issueId) detailError.value = 'Issue 加载失败，请重试'
  } finally {
    if (selectedIssueId.value === issueId) detailLoading.value = false
  }
}

async function submitReply() {
  if (detail.value?.status !== 'OPEN' && !isAdmin.value) return ElMessage.warning('该 Issue 已关闭，不能继续回复')
  const text = replyText.value.trim()
  if (!text) return ElMessage.warning('请先填写回复内容')
  replyLoading.value = true
  try {
    const result = await replyFeedbackIssue(detail.value.id, text, replyTarget.value?.id || null)
    detail.value = { ...detail.value, ...result.data }
    replyText.value = ''
    replyTarget.value = null
    ElMessage.success('回复已发布')
    await Promise.all([loadIssues(), loadSummary()])
  } finally {
    replyLoading.value = false
  }
}

const commentThreads = computed(() => {
  const comments = detail.value?.comments || []
  const rootIds = new Set(comments.filter(comment => comment.parentId == null).map(comment => comment.id))
  const roots = comments.filter(comment => comment.parentId == null || !rootIds.has(comment.parentId))
  return roots.map(root => ({
    root,
    replies: comments.filter(comment => comment.parentId === root.id),
  }))
})

function beginReply(comment) {
  if (detail.value?.status !== 'OPEN' && !isAdmin.value) return
  replyTarget.value = comment
}

function visibleReplyCount(thread) {
  return visibleReplyCounts[thread.root.id] || 5
}

function visibleReplies(thread) {
  return thread.replies.slice(0, visibleReplyCount(thread))
}

function showMoreReplies(thread) {
  visibleReplyCounts[thread.root.id] = Math.min(
    visibleReplyCount(thread) + 5,
    thread.replies.length,
  )
}

async function changeStatus(status) {
  const closing = status === 'CLOSED'
  try {
    await ElMessageBox.confirm(
      closing ? '关闭后会员端将显示为已解决且不能继续跟进，确定关闭？' : '确定重新打开这个 Issue？',
      closing ? '关闭 Issue' : '重新打开',
      { type: closing ? 'warning' : 'info', confirmButtonText: closing ? '确认关闭' : '重新打开' },
    )
  } catch (e) { return }
  statusLoading.value = true
  try {
    const result = await updateFeedbackStatus(detail.value.id, status)
    detail.value = { ...detail.value, ...result.data }
    if (closing) {
      replyText.value = ''
      replyTarget.value = null
    }
    ElMessage.success(closing ? 'Issue 已关闭' : 'Issue 已重新打开')
    await Promise.all([loadIssues(), loadSummary()])
  } finally {
    statusLoading.value = false
  }
}

async function removeIssue() {
  const issueId = detail.value?.id
  if (!issueId || deleteLoading.value) return
  try {
    await ElMessageBox.confirm(
      '此操作会永久删除反馈正文、图片引用和全部回复，且无法恢复。确定继续？',
      `删除 Issue #${issueId}`,
      {
        type: 'error',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch (e) { return }

  deleteLoading.value = true
  try {
    await deleteFeedbackIssue(issueId)
    if (issues.value.length === 1 && page.value > 1) page.value -= 1
    drawerOpen.value = false
    detail.value = null
    selectedIssueId.value = null
    ElMessage.success('Issue 已删除')
    await Promise.all([loadIssues(), loadSummary()])
  } finally {
    deleteLoading.value = false
  }
}

const excerpt = (text) => {
  const value = String(text || '').replace(/\s+/g, ' ').trim()
  return value.length > 86 ? value.slice(0, 86) + '…' : value
}
const formatDate = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const categoryType = (category) => ({ BUG: 'danger', FEATURE: 'primary', EXPERIENCE: 'warning', OTHER: 'info' }[category] || 'info')
</script>

<style scoped>
.schedule-app-page { min-width: 0; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0 0 4px; font-size: 22px; }
.page-header p { margin: 0; color: var(--zen-text-muted); font-size: 13px; }
.app-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; min-height: 94px; }
.summary-card { display: flex; align-items: center; gap: 14px; padding: 18px; border-radius: var(--zen-radius-lg); background: var(--zen-card); border: 1px solid var(--zen-border-light); box-shadow: var(--zen-shadow-sm); }
.summary-icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; color: #fff; font-size: 20px; font-weight: 700; flex: 0 0 auto; }
.summary-card.primary .summary-icon { background: linear-gradient(135deg, #14b8a6, #0ea5e9); }
.summary-card.open .summary-icon { background: linear-gradient(135deg, #22c55e, #14b8a6); }
.summary-card.closed .summary-icon { background: linear-gradient(135deg, #64748b, #94a3b8); }
.summary-card.version .summary-icon { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.summary-card strong { display: block; font-size: 22px; line-height: 1.15; color: var(--zen-text); }
.summary-card span { display: block; margin-top: 5px; color: var(--zen-text-muted); font-size: 12px; }
.workspace-card { padding: 8px 20px 20px; background: var(--zen-card); border-radius: var(--zen-radius-lg); box-shadow: var(--zen-shadow-sm); }
.issue-toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 8px 0 16px; }
.issue-table { width: 100%; cursor: pointer; }
.issue-title { font-weight: 600; color: var(--zen-text); }
.issue-title span { color: var(--zen-text-muted); font-weight: 500; margin-right: 8px; }
.issue-preview { color: var(--zen-text-muted); font-size: 12px; margin-top: 5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.issue-attachments { color: var(--el-color-primary); font-size: 11px; margin-top: 4px; }
.comment-count { color: var(--zen-text-secondary); font-size: 12px; }
.pagination-wrap { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; color: var(--zen-text-muted); font-size: 12px; }
:deep(.el-drawer__body) { padding: 0; overflow: hidden; color: var(--zen-text); background: var(--zen-card); }
.issue-detail { height: 100%; min-height: 0; display: flex; flex-direction: column; }
.issue-scroll { flex: 1; min-height: 0; overflow-y: auto; padding: 24px; color: var(--zen-text); background: var(--zen-card); }
.detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; border-bottom: 1px solid var(--zen-border-light); padding-bottom: 18px; }
.detail-kicker { color: var(--zen-text-muted); font-size: 12px; }
.detail-head h3 { margin: 5px 0 10px; font-size: 22px; line-height: 1.35; }
.detail-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--zen-text-muted); font-size: 12px; }
.issue-body { margin-top: 20px; border: 1px solid var(--zen-border-light); border-radius: 12px; overflow: hidden; }
.author-line { display: flex; align-items: center; gap: 9px; background: var(--zen-bg); padding: 11px 14px; font-size: 13px; }
.author-line span { color: var(--zen-text-muted); }
.issue-content, .comment-content { padding: 16px; white-space: pre-wrap; word-break: break-word; line-height: 1.7; font-size: 14px; color: var(--zen-text); background: var(--zen-card); }
.issue-images { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 10px; padding: 0 16px 16px; }
.issue-image { width: 100%; height: 128px; border-radius: 10px; cursor: zoom-in; background: var(--zen-bg); }
.environment { margin-top: 16px; }
.timeline-title { margin: 24px 0 12px; font-weight: 600; font-size: 14px; }
.comment-list { display: grid; gap: 12px; }
.comment-thread { display: grid; gap: 8px; }
.comment-card { border: 1px solid var(--zen-border-light); border-radius: 12px; overflow: hidden; color: var(--zen-text); background: var(--zen-card); }
.comment-card.nested { border-radius: 10px; background: color-mix(in srgb, var(--zen-bg) 65%, white); }
.comment-card.maintainer { border-color: color-mix(in srgb, var(--el-color-primary) 35%, white); }
.comment-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 13px; background: var(--zen-bg); color: var(--zen-text-muted); font-size: 12px; }
.comment-card.maintainer .comment-head { background: color-mix(in srgb, var(--el-color-primary) 8%, white); }
.comment-author { display: flex; align-items: center; gap: 8px; color: var(--zen-text); }
.comment-tools { display: flex; justify-content: flex-end; padding: 0 12px 8px; }
.nested-comments { display: grid; gap: 8px; margin-left: 28px; padding-left: 12px; border-left: 2px solid var(--zen-border-light); }
.more-replies { justify-self: start; }
.comment-empty { padding: 8px 0 4px; }
.reply-box { flex: 0 0 auto; padding: 16px 24px 20px; border-top: 1px solid var(--zen-border-light); background: var(--zen-card); box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.06); }
.reply-label { display: flex; align-items: center; justify-content: space-between; font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.reply-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 12px; }
.reply-actions .el-button { min-width: 80px; margin-left: 0; }
/* 全局 default 按钮覆盖会同时命中 Element 的 danger/default 类；这里显式保留危险操作的可见性。 */
:deep(.delete-action.el-button.el-button--danger) { background: var(--el-color-danger) !important; border-color: var(--el-color-danger) !important; color: #fff !important; }
:deep(.delete-action.el-button.el-button--danger:hover), :deep(.delete-action.el-button.el-button--danger:focus) { background: #dc2626 !important; border-color: #dc2626 !important; color: #fff !important; }
/* 关闭后的 Issue 仍可由管理端重新打开；显式设置浅色背景和深色文字，
   避免主题/Element plain 样式把按钮渲染成白底白字。 */
:deep(.issue-reopen-button.el-button.el-button--success) { color: #15803d !important; background: #f0fdf4 !important; border-color: #86efac !important; }
:deep(.issue-reopen-button.el-button.el-button--success:hover), :deep(.issue-reopen-button.el-button.el-button--success:focus) { color: #fff !important; background: #16a34a !important; border-color: #16a34a !important; }
.drawer-loading { min-height: 320px; display: grid; place-content: center; justify-items: center; gap: 12px; color: var(--zen-text-muted); font-size: 13px; }
@media (max-width: 1100px) { .summary-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 700px) {
  .app-header { align-items: flex-start; flex-direction: column; }
  .summary-grid { grid-template-columns: 1fr; }
  .issue-toolbar > * { width: 100% !important; max-width: none !important; }
  .issue-scroll { padding: 18px; }
  .reply-box { padding: 14px 18px 18px; }
  .reply-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: stretch; }
  .reply-actions .el-button { width: 100%; margin-left: 0; }
  .reply-actions .el-button:last-child { grid-column: span 2; }
}
</style>
