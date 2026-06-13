<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">学习小组</h1>
      <p class="page-desc">在这里管理你的学习任务、查看成绩和排名</p>
    </div>

    <!-- Tab Bar -->
    <div class="tab-bar anim-in">
      <div class="tab-bar__item" :class="{ active: activeTab === 'task' }" @click="activeTab = 'task'">📋 学习任务</div>
      <div class="tab-bar__item" :class="{ active: activeTab === 'scores' }" @click="activeTab = 'scores'">📊 我的成绩</div>
      <div class="tab-bar__item" :class="{ active: activeTab === 'ranking' }" @click="activeTab = 'ranking'">🏆 成绩统计</div>
    </div>

    <!-- ========== TAB: 学习任务 ========== -->
    <div v-if="activeTab === 'task'" class="tab-content" :key="'task'">
      <!-- 游客提示 -->
      <div v-if="isGuest" class="guest-block anim-in">
        <div class="guest-block__icon">🔒</div>
        <h3 class="guest-block__title">成为正式成员后可使用</h3>
        <p class="guest-block__desc">学习任务、提交作业等功能仅对正式成员开放，<br/>请先加入协会成为正式成员。</p>
        <router-link to="/join" class="btn btn--primary btn--pill">🌟 加入协会</router-link>
      </div>

      <template v-else>
        <div v-if="taskLoading" class="loading"><div class="loading__spinner"></div></div>

        <div v-else-if="!status.hasActivity" class="empty">
          <div class="empty__text">当前暂无进行中的学习活动</div>
          <p class="t-caption mt-2">请等待管理员创建新的学习活动</p>
        </div>

        <template v-else>
          <div class="status-banner mb-4 anim-in">
            <div>
              <div class="status-banner__title">
                第{{ status.activity.activeWeek }}周匹配周期
                <span class="badge badge--gradient" style="margin-left:8px;">进行中</span>
              </div>
              <div class="status-banner__desc">{{ status.activity.title || `第${status.activity.seqNum}次学习活动` }}</div>
            </div>
            <button v-if="!status.joined" class="btn btn--primary btn--pill" @click="joinActivity" :disabled="joining">
              {{ joining ? '加入中…' : '＋ 加入学习小组' }}
            </button>
          </div>

          <div v-if="joinError" class="error-text mb-3">{{ joinError }}</div>

          <template v-if="status.joined">
            <div class="card anim-in" style="animation-delay:0.05s;">
              <h3 class="t-heading mb-3">学习任务与作业管理</h3>
              <div class="hw-grid">
                <!-- 左栏：本周学习任务 -->
                <div class="hw-col">
                  <div class="split__title">📄 本周学习任务</div>
                  <div v-if="status.homework" class="card card--gradient hw-file-card">
                    <div class="flex gap-2" style="align-items:center;">
                      <div class="hw-icon" style="background:rgba(59,130,246,0.1);">📘</div>
                      <div style="flex:1; min-width:0;">
                        <div style="font-weight:600;color:var(--ink-800);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ status.homework.title || status.homework.fileName || '学习资料' }}</div>
                        <div class="t-caption" v-if="status.homework.createdAt">{{ formatTime(status.homework.createdAt) }}</div>
                      </div>
                    </div>
                    <a
                      v-if="status.homework.fileUrl"
                      :href="'/api/file/download?url=' + encodeURIComponent(status.homework.fileUrl) + '&name=' + encodeURIComponent(status.homework.fileName || 'file')"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="btn btn--primary btn--full btn--sm btn--pill mt-3"
                    >↓ 下载文件</a>
                  </div>
                  <p v-else class="t-caption" style="color:var(--ink-400); padding: var(--s6) 0; text-align: center;">本周暂无学习任务</p>
                </div>

                <!-- 右栏：我的作业 -->
                <div class="hw-col">
                  <div class="split__title">⬆ 我的作业</div>
                  <!-- 已提交：文件卡 + 下载/替换/删除 -->
                  <div v-if="status.submitted && status.submissions && status.submissions.length">
                    <div v-for="s in status.submissions" :key="s.id" class="card card--gradient hw-file-card">
                      <div class="flex gap-2" style="align-items:center;">
                        <div class="hw-icon" style="background:rgba(34,197,94,0.1);">📎</div>
                        <div style="flex:1; min-width:0;">
                          <div style="font-weight:600;color:var(--ink-800);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ s.fileName }}</div>
                          <div class="t-caption">{{ formatTime(s.createdAt) }}</div>
                        </div>
                        <span class="badge badge--success" style="flex-shrink:0;">已提交</span>
                      </div>
                      <div class="flex gap-1 mt-3" style="flex-wrap: wrap;">
                        <a
                          :href="'/api/file/download?url=' + encodeURIComponent(s.fileUrl) + '&name=' + encodeURIComponent(s.fileName || 'file')"
                          target="_blank"
                          rel="noopener noreferrer"
                          class="btn btn--primary btn--sm btn--pill"
                        >↓ 下载</a>
                        <template v-if="!status.weekScore">
                          <label class="btn btn--secondary btn--sm btn--pill" style="cursor:pointer;">
                            🔄 替换
                            <input type="file" @change="replaceHomework($event, s)" style="display:none;" />
                          </label>
                          <button class="btn btn--danger btn--sm btn--pill" @click="deleteHomework(s)" :disabled="deleting">
                            🗑 删除
                          </button>
                        </template>
                      </div>
                    </div>

                    <!-- 评分结果 -->
                    <div v-if="status.weekScore" class="card mt-3" style="padding: var(--s4); border-left: 4px solid var(--success);">
                      <div class="flex-between mb-2">
                        <span class="t-heading" style="font-size: 0.9rem;">📊 本周评分</span>
                        <span class="badge" :class="status.weekScore.score >= 7 ? 'badge--success' : status.weekScore.score >= 4 ? 'badge--warning' : 'badge--error'">
                          {{ status.weekScore.score }} 分
                        </span>
                      </div>
                      <p class="t-body" v-if="status.weekScore.comment" style="font-size: 0.85rem; color: var(--ink-600);">{{ status.weekScore.comment }}</p>
                      <p class="t-caption mt-1">评分人：{{ status.weekScore.leaderName }}</p>
                    </div>

                    <div v-if="submitError" class="error-text mt-1">{{ submitError }}</div>
                    <div v-if="submitSuccess" class="success-text mt-1">{{ submitSuccess }}</div>
                  </div>
                  <!-- 未提交：上传区域 -->
                  <div v-else>
                    <div class="upload-zone" :class="{ 'upload-zone--active': isDragOver }"
                      @dragover.prevent="isDragOver = true" @dragleave="isDragOver = false"
                      @drop.prevent="handleDrop($event)">
                      <div v-if="selectedFile" style="text-align:center;">
                        <div style="font-size:2rem; margin-bottom: var(--s2);">📄</div>
                        <div class="t-body" style="font-weight:600;">{{ selectedFile.name }}</div>
                        <div class="t-caption">{{ (selectedFile.size / 1024).toFixed(1) }} KB</div>
                        <button class="btn btn--ghost btn--sm mt-2" @click="clearFile">✕ 移除</button>
                      </div>
                      <div v-else style="text-align:center;">
                        <div style="font-size:2rem; margin-bottom: var(--s2); opacity:0.5;">📂</div>
                        <p class="t-body" style="color:var(--ink-500);">拖拽文件到此处，或
                          <label style="color:var(--primary);font-weight:600;cursor:pointer;">
                            点击选择
                            <input type="file" ref="fileInput" @change="handleFileSelect" style="display:none;" />
                          </label>
                        </p>
                      </div>
                    </div>
                    <button class="btn btn--primary btn--full btn--pill mt-3" @click="submitHomework" :disabled="submitting || !selectedFile">
                      {{ submitting ? '提交中…' : '📤 提交作业' }}
                    </button>
                    <div v-if="submitError" class="error-text mt-1">{{ submitError }}</div>
                    <div v-if="submitSuccess" class="success-text mt-1">{{ submitSuccess }}</div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </template>
      </template>
    </div>

    <!-- ========== TAB: 我的成绩 ========== -->
    <div v-if="activeTab === 'scores'" class="tab-content" :key="'scores'">
      <!-- 游客提示 -->
      <div v-if="isGuest" class="guest-block anim-in">
        <div class="guest-block__icon">🔒</div>
        <h3 class="guest-block__title">成为正式成员后可查看</h3>
        <p class="guest-block__desc">我的成绩功能仅对正式成员开放，<br/>请先加入协会成为正式成员。</p>
        <router-link to="/join" class="btn btn--primary btn--pill">🌟 加入协会</router-link>
      </div>

      <template v-else>
        <div v-if="scoresLoading" class="loading"><div class="loading__spinner"></div></div>

        <div v-else-if="activities.length === 0" class="empty">
          <div class="empty__text">还没有成绩记录</div>
          <p class="t-caption mt-2">参加学习活动后即可在此查看成绩</p>
        </div>

        <template v-else>
          <div class="filter-bar mb-4 anim-in">
            <div class="form-group" style="margin-bottom:0; min-width:120px;">
              <select v-model="selectedIdx" class="select">
                <option v-for="(a, i) in activities" :key="i" :value="i">
                  {{ a.title || `第${a.seqNum}次` }} ({{ a.grade }})
                </option>
              </select>
            </div>
          </div>

          <div v-if="currentActivity" class="card mb-4 anim-in">
            <div class="flex-between mb-3">
              <h3 class="t-heading">{{ currentActivity.title || `第${currentActivity.seqNum}次学习活动` }}</h3>
              <div class="flex gap-1">
                <span class="badge badge--primary">总分 {{ currentActivity.totalScore }}</span>
                <span class="badge badge--gradient">第 {{ currentActivity.rank }} 名</span>
              </div>
            </div>

            <div class="t-heading mb-3" style="display:flex;align-items:center;gap:6px;">⏱ 成绩时间线</div>

            <div v-if="currentActivity.scores.length > 0">
              <div v-for="(s, idx) in currentActivity.scores" :key="idx"
                class="card card--gradient mb-2 anim-in"
                :style="{ animationDelay: (idx * 0.05) + 's', padding: 'var(--s4)' }">
                <div class="flex-between">
                  <span class="t-heading">第 {{ s.week }} 周</span>
                  <span class="badge" :class="s.score >= 7 ? 'badge--success' : s.score >= 4 ? 'badge--warning' : 'badge--error'">{{ s.score }} 分</span>
                </div>
                <p class="t-body mt-1" v-if="s.comment">{{ s.comment }}</p>
                <p class="t-caption mt-1">评分人：{{ s.leaderName }}</p>
              </div>
            </div>
            <div v-else class="empty" style="padding: var(--s6);"><div class="empty__text">暂无成绩记录</div></div>
          </div>
        </template>
      </template>
    </div>

    <!-- ========== TAB: 成绩统计 ========== -->
    <div v-if="activeTab === 'ranking'" class="tab-content" :key="'ranking'">
      <div v-if="rankingLoading && allActivities.length === 0" class="loading"><div class="loading__spinner"></div></div>

      <div v-else-if="allActivities.length === 0" class="empty"><div class="empty__text">暂无学习活动数据</div></div>

      <template v-else>
        <!-- 按年级分组展示 -->
        <div v-for="(group, gi) in groupedActivities" :key="group.grade" class="rank-year-section anim-in" :style="{ animationDelay: (gi * 0.08) + 's' }">
          <div class="rank-year-header">
            <span class="rank-year-badge">{{ group.grade }} 届</span>
            <span class="rank-year-count">{{ group.activities.length }} 个活动</span>
          </div>

          <!-- 该年级的活动卡片 -->
          <div class="rank-acts">
            <div
              v-for="(act, ai) in group.activities" :key="act.id"
              class="rank-act-card anim-in"
              :class="{ 'rank-act-card--active': act.status === 1, 'rank-act-card--featured': gi === 0 && ai === 0 }"
              :style="{ animationDelay: ((gi * 0.08) + (ai * 0.04)) + 's' }"
              @click="openRankingDetail(act)"
            >
              <div class="rank-act-card__header">
                <div class="rank-act-card__icon">🏆</div>
                <div class="rank-act-card__status">
                  <span v-if="act.status === 1" class="badge badge--gradient">进行中</span>
                  <span v-else class="badge badge--default">已归档</span>
                </div>
              </div>
              <h3 class="rank-act-card__title">{{ act.title || `第${act.seqNum}次活动` }}</h3>
              <div class="rank-act-card__meta">
                <span>📋 第{{ act.seqNum }}次</span>
                <span>📅 共{{ act.currentWeek }}周</span>
                <span v-if="act.memberCount">👥 {{ act.memberCount }}人</span>
              </div>
              <div class="rank-act-card__action">
                <span>查看排名 →</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 排名详情弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="rankDetailAct" class="rank-modal-overlay" @click.self="rankDetailAct = null">
          <div class="rank-modal">
            <div class="rank-modal__header">
              <div>
                <h2 class="rank-modal__title">{{ rankDetailAct.title || `第${rankDetailAct.seqNum}次活动` }}</h2>
                <div class="rank-modal__subtitle">
                  <span class="badge" :class="rankDetailAct.status === 1 ? 'badge--gradient' : 'badge--default'" style="margin-right:8px;">{{ rankDetailAct.status === 1 ? '进行中' : '已归档' }}</span>
                  {{ rankDetailAct.grade }}届 · 共{{ rankDetailAct.currentWeek }}周
                </div>
              </div>
              <button class="rank-modal__close" @click="rankDetailAct = null">✕</button>
            </div>

            <div class="rank-modal__body">
              <div v-if="detailLoading" class="loading"><div class="loading__spinner"></div></div>

              <template v-else-if="records.length > 0">
                <div class="table-wrap">
                  <table class="table">
                    <thead><tr><th>排名</th><th>学号</th><th>姓名</th><th>总分</th></tr></thead>
                    <tbody>
                      <tr v-for="r in records" :key="r.userId">
                        <td><span v-if="r.rank <= 3" class="badge badge--gradient">{{ r.rank }}</span><span v-else>{{ r.rank }}</span></td>
                        <td>{{ r.studentId }}</td><td>{{ r.userName }}</td><td style="font-weight:700;">{{ r.totalScore }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div class="pagination" v-if="total > pageSize">
                  <button class="pagination__btn" :disabled="currentPage<=1" @click="currentPage--;loadRankingDetail()">‹</button>
                  <span class="t-caption">{{ currentPage }} / {{ Math.ceil(total/pageSize) }}</span>
                  <button class="pagination__btn" :disabled="currentPage>=Math.ceil(total/pageSize)" @click="currentPage++;loadRankingDetail()">›</button>
                </div>
              </template>
              <div v-else class="empty" style="padding: var(--s6);"><div class="empty__text">暂无排名数据</div></div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isGuest = computed(() => {
  const roles = userStore.roles || []
  return roles.length === 0 || (roles.includes(4) && !roles.some(r => r <= 3))
})

const activeTab = ref('task')

// ===== 学习任务 =====
const taskLoading = ref(true)
const status = ref({})
const joining = ref(false); const joinError = ref('')
const selectedFile = ref(null); const fileInput = ref(null)
const submitting = ref(false); const submitError = ref(''); const submitSuccess = ref('')
const isDragOver = ref(false); const deleting = ref(false)

async function loadStatus() { taskLoading.value = true; try { const r = await request.get('/api/study/my-status'); status.value = r.data } catch {} finally { taskLoading.value = false } }
async function joinActivity() { joining.value = true; joinError.value = ''; try { await request.post('/api/study/member/auto-join'); await loadStatus() } catch (e) { joinError.value = e.message || '加入失败' } finally { joining.value = false } }
function handleFileSelect(e) { selectedFile.value = e.target.files[0] || null }
function handleDrop(e) { isDragOver.value = false; const f = e.dataTransfer?.files?.[0]; if (f) selectedFile.value = f }
async function submitHomework() {
  if (!selectedFile.value) return; submitting.value = true; submitError.value = ''; submitSuccess.value = ''
  try {
    const fd = new FormData(); fd.append('file', selectedFile.value)
    const up = await request.post('/api/file/upload', fd)
    await request.post('/api/study/homework/submit', { activityId: status.value.activity.id, week: status.value.activity.activeWeek, title: up.data.originalName || selectedFile.value.name, fileUrl: up.data.url, fileName: up.data.originalName || selectedFile.value.name })
    submitSuccess.value = '提交成功'; selectedFile.value = null; await loadStatus()
  } catch (e) { submitError.value = e.message || '提交失败' } finally { submitting.value = false }
}
function clearFile() { selectedFile.value = null; if (fileInput.value) fileInput.value.value = '' }
async function replaceHomework(e, submission) {
  const file = e.target.files[0]; if (!file) return
  submitting.value = true; submitError.value = ''; submitSuccess.value = ''
  try {
    const fd = new FormData(); fd.append('file', file)
    const up = await request.post('/api/file/upload', fd)
    await request.post('/api/study/homework/submit', { activityId: status.value.activity.id, week: status.value.activity.activeWeek, title: up.data.originalName || file.name, fileUrl: up.data.url, fileName: up.data.originalName || file.name })
    submitSuccess.value = '替换成功'; await loadStatus()
  } catch (e2) { submitError.value = e2.message || '替换失败' } finally { submitting.value = false }
}
async function deleteHomework(submission) {
  if (!confirm('确定删除此作业？')) return
  deleting.value = true; submitError.value = ''
  try { await request.delete('/api/study/homework/my', { params: { activityId: status.value.activity.id, week: status.value.activity.activeWeek } }); await loadStatus() }
  catch (e) { submitError.value = e.message || '删除失败' }
  finally { deleting.value = false }
}

// ===== 我的成绩 =====
const scoresLoading = ref(false)
const activities = ref([])
const selectedIdx = ref(0)
const currentActivity = computed(() => activities.value[selectedIdx.value] || null)

async function loadScores() { scoresLoading.value = true; try { const r = await request.get('/api/study/my-scores'); activities.value = r.data.activities || [] } catch {} finally { scoresLoading.value = false } }

// ===== 成绩统计 =====
const allActivities = ref([])
const rankingLoading = ref(false)
const rankDetailAct = ref(null)
const detailLoading = ref(false)
const records = ref([]); const total = ref(0); const currentPage = ref(1); const pageSize = 20

const groupedActivities = computed(() => {
  const map = {}
  for (const act of allActivities.value) {
    const g = act.grade || '未知'
    if (!map[g]) map[g] = []
    map[g].push(act)
  }
  // 按年级降序排列
  return Object.keys(map)
    .sort((a, b) => b.localeCompare(a))
    .map(grade => ({ grade, activities: map[grade] }))
})

async function loadAllActivities() {
  rankingLoading.value = true
  try {
    const r = await request.get('/api/study/activity/all-with-stats')
    allActivities.value = r.data || []
  } catch {}
  finally { rankingLoading.value = false }
}

async function openRankingDetail(act) {
  rankDetailAct.value = act
  currentPage.value = 1
  records.value = []
  total.value = 0
  await loadRankingDetail()
}

async function loadRankingDetail() {
  if (!rankDetailAct.value) return
  detailLoading.value = true
  try {
    const r = await request.get('/api/study/ranking', { params: { activityId: rankDetailAct.value.id, current: currentPage.value, size: pageSize } })
    records.value = r.data.records || []
    total.value = r.data.total || 0
  } catch {}
  finally { detailLoading.value = false }
}

// ===== 工具函数 =====
function formatTime(t) { return t ? new Date(t).toLocaleString('zh-CN') : '' }

// ===== 懒加载：切换 tab 时才加载数据 =====
const scoresLoaded = ref(false)
const rankingLoaded = ref(false)

watch(activeTab, (tab) => {
  if (tab === 'scores' && !scoresLoaded.value && !isGuest.value) { scoresLoaded.value = true; loadScores() }
  if (tab === 'ranking' && !rankingLoaded.value) { rankingLoaded.value = true; loadAllActivities() }
})

onMounted(() => { if (!isGuest.value) loadStatus() })
</script>

<style scoped>
/* 游客权限提示块 */
.guest-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--s8) var(--s6);
  background: var(--bg-card);
  border: 1px dashed var(--ink-200);
  border-radius: var(--r-xl);
  min-height: 300px;
}
.guest-block__icon {
  font-size: 3rem;
  margin-bottom: var(--s4);
  opacity: 0.6;
}
.guest-block__title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--ink-700);
  margin-bottom: var(--s3);
}
.guest-block__desc {
  font-size: 0.9rem;
  color: var(--ink-400);
  line-height: 1.7;
  margin-bottom: var(--s5);
}

.hw-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--s5);
  align-items: start;
}
.hw-col {
  display: flex;
  flex-direction: column;
}
.hw-file-card {
  padding: var(--s4);
  margin-bottom: var(--s3);
}
.hw-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  flex-shrink: 0;
}
@media (max-width: 768px) {
  .hw-grid { grid-template-columns: 1fr; }
}

/* ---- 成绩统计：年级分组 ---- */
.rank-year-section {
  margin-bottom: var(--s7);
}
.rank-year-header {
  display: flex;
  align-items: center;
  gap: var(--s3);
  margin-bottom: var(--s4);
}
.rank-year-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 18px;
  font-size: 0.9rem;
  font-weight: 700;
  border-radius: var(--r-pill);
  background: var(--gradient-btn);
  color: #fff;
  box-shadow: var(--shadow-gradient);
}
.rank-year-count {
  font-size: 0.8125rem;
  color: var(--ink-400);
}

/* ---- 活动卡片网格 ---- */
.rank-acts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--s4);
}

.rank-act-card {
  background: var(--bg-card);
  border: 1px solid var(--ink-100);
  border-radius: var(--r-lg);
  padding: var(--s5);
  cursor: pointer;
  transition: all 0.3s var(--ease);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}
.rank-act-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--ink-200);
  transition: background 0.3s;
}
.rank-act-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
}
.rank-act-card:hover::before {
  background: var(--gradient-btn);
}

/* 进行中的活动 */
.rank-act-card--active {
  border-color: rgba(20,184,166,0.2);
  background: var(--gradient-soft);
}
.rank-act-card--active::before {
  background: var(--gradient-btn);
}

/* 第一个年级的第一个活动（最新） */
.rank-act-card--featured {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto auto;
  gap: 0 var(--s5);
  padding: var(--s6);
}
.rank-act-card--featured .rank-act-card__header {
  grid-column: 1;
  grid-row: 1 / 3;
}
.rank-act-card--featured .rank-act-card__icon {
  width: 64px;
  height: 64px;
  font-size: 2rem;
}
.rank-act-card--featured .rank-act-card__title {
  font-size: 1.25rem;
  grid-column: 2;
  grid-row: 1;
  align-self: end;
}
.rank-act-card--featured .rank-act-card__meta {
  grid-column: 2;
  grid-row: 2;
  align-self: start;
}
.rank-act-card--featured .rank-act-card__status {
  position: absolute;
  top: var(--s4);
  right: var(--s4);
}
.rank-act-card--featured .rank-act-card__action {
  grid-column: 1 / -1;
  grid-row: 3;
}

.rank-act-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--s3);
}
.rank-act-card__icon {
  width: 44px;
  height: 44px;
  border-radius: var(--r-md);
  background: var(--gradient-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.3rem;
}
.rank-act-card__title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--ink-900);
  margin-bottom: var(--s2);
  line-height: 1.4;
}
.rank-act-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s3);
  font-size: 0.8125rem;
  color: var(--ink-400);
  margin-bottom: var(--s3);
}
.rank-act-card__action {
  margin-top: auto;
  padding-top: var(--s3);
  border-top: 1px solid var(--ink-100);
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--primary);
  transition: color 0.2s;
}
.rank-act-card:hover .rank-act-card__action {
  color: var(--primary-dark);
}

/* ---- 排名详情弹窗 ---- */
.rank-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.55);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--s5);
}
.rank-modal {
  background: var(--bg-card);
  border-radius: var(--r-xl);
  width: 100%;
  max-width: 700px;
  max-height: 85vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 80px rgba(0,0,0,0.2);
  animation: modalSlideIn 0.35s var(--ease) both;
}
@keyframes modalSlideIn {
  from { opacity: 0; transform: translateY(24px) scale(0.96); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
.rank-modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: var(--s5) var(--s6);
  background: var(--gradient-soft);
  border-bottom: 1px solid rgba(59,130,246,0.08);
}
.rank-modal__title {
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--ink-900);
  margin-bottom: 6px;
}
.rank-modal__subtitle {
  font-size: 0.8125rem;
  color: var(--ink-500);
  display: flex;
  align-items: center;
}
.rank-modal__close {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--white);
  border: 1px solid var(--ink-200);
  font-size: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--dur);
  color: var(--ink-600);
  flex-shrink: 0;
}
.rank-modal__close:hover {
  color: var(--ink-900);
  box-shadow: var(--shadow-md);
}
.rank-modal__body {
  padding: var(--s5) var(--s6);
  overflow-y: auto;
  flex: 1;
}

/* Transition */
.modal-enter-active { animation: modalSlideIn 0.35s var(--ease); }
.modal-leave-active { animation: modalSlideIn 0.25s var(--ease) reverse; }

@media (max-width: 768px) {
  .rank-acts { grid-template-columns: 1fr; }
  .rank-act-card--featured { grid-template-columns: auto 1fr; }
  .rank-modal {
    max-width: 100%;
    max-height: 90vh;
    margin: var(--s3);
  }
  .rank-modal__header,
  .rank-modal__body { padding: var(--s4); }
}
</style>

