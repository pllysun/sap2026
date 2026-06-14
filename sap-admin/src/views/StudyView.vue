<template>
  <div class="study-page zen-fade-in">
    <div class="page-header">
      <h2>学 · 修</h2>
      <p>学习小组活动与评分管理</p>
    </div>

    <!-- 年级切换 -->
    <div class="grade-tabs-bar" v-if="gradeList.length > 1">
      <button v-for="g in gradeList" :key="g"
        :class="['grade-tab', { active: gradeFilter === g }]"
        @click="switchGrade(g)">
        <span class="grade-tab-label">{{ g }}届</span>
        <span v-if="g !== systemGrade" class="archived-badge">归档</span>
      </button>
    </div>

    <!-- 当前活动头部 -->
    <div class="detail-header" v-if="selectedActivity">
      <div class="detail-title-row">
        <div class="title-left">
          <h3>{{ selectedActivity.title }}</h3>
          <el-tag :type="selectedActivity.status === 1 ? 'success' : 'info'" effect="dark" round size="small">
            {{ selectedActivity.status === 1 ? '进行中' : '已关闭' }}
          </el-tag>
          <el-tag v-if="!isCurrentGrade" type="info" effect="plain" round size="small" style="margin-left: 4px">归档</el-tag>
        </div>
        <div class="header-actions">
          <el-dropdown trigger="click" @command="handleSwitchActivity">
            <el-button text size="small" style="color: var(--zen-text-muted)">
              切换活动 <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="act in studyActivities" :key="act.id" :command="act"
                  :class="{ 'is-active': act.id === selectedActivity?.id }">
                  第{{ act.seqNum }}次 · {{ act.title }}
                  <el-tag v-if="act.status === 1" type="success" size="small" effect="plain" style="margin-left: 6px">进行中</el-tag>
                  <el-tag v-else type="info" size="small" effect="plain" style="margin-left: 6px">已归档</el-tag>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="isEditable" type="primary" size="small" plain @click="showCreateActivity = true" :icon="Plus">创建活动</el-button>
        </div>
      </div>
      <div class="detail-stats">
        <span>📅 {{ selectedActivity.grade }}届 · 第{{ selectedActivity.seqNum }}次</span>
        <span>🔄 总周期 {{ selectedActivity.currentWeek }}</span>
        <span>👥 成员 {{ activityDetail?.totalMembers || 0 }}人</span>
        <span>👤 负责人 {{ leaders.length }}人</span>
      </div>

      <!-- 操作栏 -->
      <div class="ops-toolbar" v-if="isEditable">
        <!-- 左侧：当前周期 + 开启下一周期 + 上传作业 -->
        <div class="ops-left">
          <el-tooltip content="成员端操作的周期，点击可切换" placement="top">
            <span class="active-week-display active-week-clickable">
              ⭐ 当前周期
              <el-dropdown trigger="click" @command="handleSetActiveWeek">
                <span class="active-week-trigger">
                  {{ selectedActivity.activeWeek || 1 }} <el-icon><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="w in selectedActivity.currentWeek" :key="w" :command="w">
                      周期 {{ w }}
                      <el-tag v-if="w === selectedActivity.activeWeek" type="success" size="small" effect="plain" style="margin-left: 6px">当前</el-tag>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </span>
          </el-tooltip>
          <template v-if="currentHomework">
            <el-tag effect="plain" size="small">📄 {{ currentHomework.fileName || currentHomework.title }}</el-tag>
            <el-button text size="small" type="primary" @click="downloadHomework">下载</el-button>
            <el-upload action="/api/file/upload" :headers="uploadHeaders" :show-file-list="false"
              :on-success="handleHomeworkReplace" accept=".md,.txt,.pdf,.doc,.docx,.zip">
              <el-button text size="small">替换</el-button>
            </el-upload>
            <el-button text size="small" type="danger" @click="handleDeleteHomework" :loading="loading.deleteHomework">删除</el-button>
          </template>
          <template v-else>
            <el-upload action="/api/file/upload" :headers="uploadHeaders" :show-file-list="false"
              :on-success="handleHomeworkUpload" accept=".md,.txt,.pdf,.doc,.docx,.zip">
              <el-button size="small" plain>📝 上传周期{{ selectedActivity.activeWeek || selectedActivity.currentWeek }}作业</el-button>
            </el-upload>
          </template>
        </div>
        <!-- 右侧：开启下一周期 -->
        <div class="ops-right">
          <el-tooltip v-if="leaders.length === 0" content="请先添加负责人后再开启下一周期" placement="top">
            <span>
              <el-button type="warning" size="small" disabled>
                🔄 开启第{{ selectedActivity.currentWeek + 1 }}周期
              </el-button>
            </span>
          </el-tooltip>
          <el-button v-else type="warning" size="small" @click="handleNextWeek" :loading="loading.nextWeek">
            🔄 开启第{{ selectedActivity.currentWeek + 1 }}周期
          </el-button>
        </div>
      </div>
      <!-- 非编辑模式下显示当前周期 -->
      <div v-else class="ops-toolbar readonly">
        <span class="active-week-display">
          ⭐ 当前周期 <span style="font-weight: 600; color: var(--zen-accent)">{{ selectedActivity.activeWeek || 1 }}</span>
        </span>
      </div>
    </div>

    <!-- 无活动 -->
    <div class="detail-header empty-main" v-else>
      <div class="empty-prompt">
        <span style="font-size: 48px; opacity: 0.2">📖</span>
        <p>{{ isCurrentGrade ? '暂无活动' : '该年级暂无活动记录' }}</p>
        <el-button v-if="isCurrentGrade" type="primary" @click="showCreateActivity = true" :icon="Plus" style="margin-top: 12px">创建活动</el-button>
      </div>
    </div>

    <!-- 主内容区 -->
    <template v-if="selectedActivity">
      <div class="main-section">
        <!-- 周期选择行：左边周期按钮，右边汇总 -->
        <div class="cycle-header">
          <div class="cycle-tabs-left">
            <button v-for="ws in weekSummaries" :key="ws.week"
              :class="['cycle-btn', { active: unifiedWeek === ws.week }]"
              @click="switchUnifiedWeek(ws.week)">
              <span class="cycle-num">{{ ws.week }}</span>
              <span class="cycle-avg" v-if="ws.scoredCount > 0">{{ ws.avgScore }}</span>
            </button>
          </div>
          <button class="cycle-btn summary" :class="{ active: unifiedWeek === 0 }"
            @click="switchUnifiedWeek(0)">📊 汇总</button>
        </div>

        <!-- 汇总模式：全部数据 -->
        <div v-if="unifiedWeek === 0" class="summary-view">
          <el-table :data="weekSummaries" stripe>
            <el-table-column prop="week" label="周期" width="70" />
            <el-table-column prop="memberCount" label="成员数" width="80" />
            <el-table-column prop="scoredCount" label="已评分" width="80" />
            <el-table-column label="平均分" width="80">
              <template #default="{ row }">
                <span :style="{ color: row.avgScore >= 7 ? 'var(--zen-success)' : row.avgScore >= 4 ? 'var(--zen-warning)' : 'var(--zen-danger)', fontWeight: 600 }">
                  {{ row.avgScore }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="作业">
              <template #default="{ row }">
                <span v-if="row.homeworkFileName || row.homeworkTitle">
                  {{ row.homeworkFileName || row.homeworkTitle }}
                  <el-button v-if="row.homeworkFileUrl" text size="small" type="primary" @click="openUrl(row.homeworkFileUrl)" style="margin-left: 4px">下载</el-button>
                </span>
                <span v-else style="color: var(--zen-text-muted)">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 周期模式：纵向排列负责人 + 我的成员 + 成员列表 -->
        <template v-else>
          <!-- 负责人管理 -->
          <div class="section-block">
            <div class="section-title" @click="showLeaderPanel = !showLeaderPanel">
              <span>👤 负责人管理</span>
              <el-icon><ArrowDown v-if="!showLeaderPanel" /><ArrowUp v-else /></el-icon>
            </div>
            <div class="section-body" v-show="showLeaderPanel">
              <div class="leader-cards" v-if="leaders.length > 0">
                <div class="leader-card" v-for="l in leaders" :key="l.id">
                  <div class="leader-card-top">
                    <div class="leader-card-info">
                      <span class="leader-card-name">{{ l.userName }}</span>
                      <span class="leader-card-sid">{{ l.studentId }}</span>
                      <el-tag v-if="l._memberDetails && l._memberDetails.length > 0" size="small" :type="l._scoredCount === l._memberDetails.length ? 'success' : 'warning'" effect="plain" round>
                        {{ l._scoredCount || 0 }}/{{ l._memberDetails.length }}
                      </el-tag>
                    </div>
                    <div class="leader-card-actions">
                      <el-button v-if="isEditable" text type="danger" size="small" @click="handleDeleteLeader(l.id)" :loading="loading.deleteLeader === l.id">移除</el-button>
                      <el-button v-if="l.memberStudentIds && l.memberStudentIds.length > 0" text type="primary" size="small" @click="showLeaderMemberDetail(l)">查看</el-button>
                    </div>
                  </div>
                  <div class="leader-card-members" v-if="l._sortedMembers && l._sortedMembers.length > 0">
                    <el-tag v-for="(m, idx) in l._sortedMembers.slice(0, 3)" :key="idx"
                      size="small" :type="(!m.submitted && (m.score === null || m.score === undefined)) ? 'danger' : 'info'" effect="plain" style="margin: 2px 4px 2px 0">
                      {{ m.studentId }}
                    </el-tag>
                    <span v-if="l._sortedMembers.length > 3" style="font-size: 11px; color: var(--zen-text-muted)">+{{ l._sortedMembers.length - 3 }}</span>
                  </div>
                  <div class="leader-card-members" v-else>
                    <span style="font-size: 12px; color: var(--zen-text-muted)">暂无分配成员</span>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无负责人" :image-size="60" />
              <div v-if="isEditable" class="ops-action-row" style="margin-top: 12px">
                <el-input v-model="leaderStudentId" placeholder="输入学号添加负责人" size="small" style="width: 180px" />
                <el-button type="primary" size="small" @click="handleAddLeader" :loading="loading.addLeader">添加</el-button>
              </div>
            </div>
          </div>

          <!-- 我的成员 + 成员列表 左右分布 -->
          <div class="members-row">
            <!-- 左：我的成员（打分） -->
            <div class="section-block members-left" v-if="myLeader">
              <div class="section-title" @click="showMyMembers = !showMyMembers">
                <span>🎯 我的成员 · {{ leaderMembers.length }}人</span>
                <el-icon><ArrowDown v-if="!showMyMembers" /><ArrowUp v-else /></el-icon>
              </div>
              <div class="section-body" v-show="showMyMembers">
                <div v-if="leaderMembers.length > 0">
                  <el-table :data="myMembersPage" stripe size="small" :row-class-name="({row}) => row.score !== null ? 'scored-row' : ''">
                    <el-table-column prop="studentId" label="学号" width="120" />
                    <el-table-column prop="userName" label="姓名" width="90" />
                    <el-table-column label="作业" width="55">
                      <template #default="{ row }">
                        <el-tag v-if="row.submitted" type="success" size="small" effect="plain">已交</el-tag>
                        <span v-else style="color: var(--zen-text-muted); font-size: 12px">未交</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="文件" min-width="70" show-overflow-tooltip>
                      <template #default="{ row }">
                        <span v-if="row.submitted && row.materials && row.materials.length > 0" style="font-size: 12px">{{ row.materials[0].fileName || '-' }}</span>
                        <span v-else style="color: var(--zen-text-muted); font-size: 12px">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="下载" width="55">
                      <template #default="{ row }">
                        <el-button v-if="row.submitted && row.materials && row.materials.length > 0" text size="small" type="primary" @click="downloadFile(row.materials[0])">下载</el-button>
                        <span v-else style="color: var(--zen-text-muted); font-size: 12px">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="分数" width="110">
                      <template #default="{ row }">
                        <span v-if="row.score !== null"
                          :style="{ color: row.score >= 7 ? 'var(--zen-success)' : row.score >= 4 ? 'var(--zen-warning)' : 'var(--zen-danger)', fontWeight: 600 }">
                          {{ row.score }}分
                        </span>
                        <el-input-number v-else-if="isEditable" v-model="scoreForm[row.userId]" :min="1" :max="10" :step="1" size="small"
                          controls-position="right" style="width: 90px" placeholder="1-10" />
                        <span v-else style="color: var(--zen-text-muted)">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="评语" min-width="160">
                      <template #default="{ row }">
                        <span v-if="row.score !== null" style="color: var(--zen-text-secondary)">{{ row.comment || '-' }}</span>
                        <el-input v-else-if="isEditable" v-model="commentForm[row.userId]" size="small" placeholder="不少于10个字" />
                        <span v-else style="color: var(--zen-text-muted)">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column v-if="isEditable" label="操作" width="70" fixed="right">
                      <template #default="{ row }">
                        <el-button v-if="row.score === null" type="primary" size="small" text
                          :disabled="scoreForm[row.userId] === undefined || scoreForm[row.userId] === null"
                          :loading="loading.score === row.userId"
                          @click="handleScore(row)">打分</el-button>
                        <el-tag v-else type="success" size="small" effect="plain">✓</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-pagination v-if="leaderMembers.length > myMembersPageSize" small background layout="total, sizes, prev, pager, next"
                    :total="leaderMembers.length" v-model:current-page="myMembersCurrentPage"
                    v-model:page-size="myMembersPageSize" :page-sizes="[10, 20, 50, 100]"
                    style="margin-top: 10px; justify-content: flex-end" />
                </div>
                <div v-else class="empty-hint">当前周期暂无成员</div>
              </div>
            </div>

            <!-- 右：成员列表（全部成员数据） -->
            <div class="section-block members-right">
              <div class="section-title">
                <span>📋 成员列表 · 周期{{ unifiedWeek }}</span>
              </div>
              <div class="section-body">
                <el-table :data="cycleMembersPage" stripe size="small" v-if="cycleMembers.length > 0">
                  <el-table-column prop="studentId" label="学号" width="100" />
                  <el-table-column prop="userName" label="姓名" min-width="50" />
                  <el-table-column prop="leaderName" label="负责人" width="75" />
                  <el-table-column label="作业" width="80">
                    <template #default="{ row }">
                      <el-tag v-if="row.submitted" type="success" size="small" effect="plain">已交</el-tag>
                      <span v-else style="color: var(--zen-text-muted); font-size: 12px">未交</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="分数" width="55">
                    <template #default="{ row }">
                      <span v-if="row.score !== null"
                        :style="{ color: row.score >= 7 ? 'var(--zen-success)' : row.score >= 4 ? 'var(--zen-warning)' : 'var(--zen-danger)', fontWeight: 600 }">
                        {{ row.score }}
                      </span>
                      <span v-else style="color: var(--zen-text-muted)">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="comment" label="评语" min-width="50" show-overflow-tooltip />
                  <el-table-column label="文件名" min-width="50" show-overflow-tooltip>
                    <template #default="{ row }">
                      <span v-if="row.submitted && row.materials && row.materials.length > 0" style="font-size: 12px">{{ row.materials[0].fileName || '-' }}</span>
                      <span v-else style="color: var(--zen-text-muted); font-size: 12px">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="下载" width="60">
                    <template #default="{ row }">
                      <el-button v-if="row.submitted && row.materials && row.materials.length > 0" text size="small" type="primary" @click="downloadFile(row.materials[0])">下载</el-button>
                      <span v-else style="color: var(--zen-text-muted); font-size: 12px">-</span>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else description="当前周期暂无数据" :image-size="60" />
                <el-pagination v-if="cycleMembers.length > cycleMembersPageSize" small background layout="total, sizes, prev, pager, next"
                  :total="cycleMembers.length" v-model:current-page="cycleMembersCurrentPage"
                  v-model:page-size="cycleMembersPageSize" :page-sizes="[10, 20, 50, 100]"
                  style="margin-top: 10px; justify-content: flex-end" />
              </div>
            </div>
          </div>
        </template>
      </div>
    </template>

    <!-- 创建活动弹窗 -->
    <el-dialog v-model="showCreateActivity" title="创建学习活动" width="400px" append-to-body>
      <el-alert v-if="selectedActivity && selectedActivity.status === 1" type="warning" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          <span style="font-weight: 600">创建新活动后，当前活动「{{ selectedActivity.title }}」将自动归档。</span>
        </template>
      </el-alert>
      <el-form :model="createForm" label-width="60px">
        <el-form-item label="年级"><el-input v-model="createForm.grade" disabled /></el-form-item>
        <el-form-item label="标题"><el-input v-model="createForm.title" placeholder="学习活动标题" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateActivity = false">取消</el-button>
        <el-button type="primary" @click="handleCreateActivity" :loading="loading.createActivity">确 认</el-button>
      </template>
    </el-dialog>

    <!-- 查看负责人全部成员弹窗 -->
    <el-dialog v-model="showLeaderMembers" :title="leaderDetailName + ' 的成员详情'" width="500px" append-to-body>
      <el-table :data="leaderDetailMembers" stripe size="small" max-height="400">
        <el-table-column prop="studentId" label="学号" width="130" />
        <el-table-column prop="userName" label="姓名" width="100" />
        <el-table-column label="总分" width="80">
          <template #default="{ row }">
            <span v-if="row.totalScore !== null && row.totalScore !== undefined"
              :style="{ fontWeight: 600, color: row.totalScore >= 7 ? 'var(--zen-success)' : row.totalScore >= 4 ? 'var(--zen-warning)' : 'var(--zen-danger)' }">
              {{ row.totalScore }}
            </span>
            <span v-else style="color: var(--zen-text-muted)">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import {
  getStudyActivities, createStudyActivity, getStudyActivityDetail,
  getStudyCycleDetail, addStudyLeader, deleteStudyLeader,
  nextWeek, uploadHomework, deleteHomework, setActiveWeek,
  getSettingValue, submitScore, getUserInfo, getGrades
} from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const studyActivities = ref([])
const selectedActivity = ref(null)
const activityDetail = ref(null)
const leaders = ref([])
const weekSummaries = ref([])
const cycleMembers = ref([])
const gradeFilter = ref('')
const systemGrade = ref('')
const gradeList = ref([])

const unifiedWeek = ref(1)

// 折叠面板
const showLeaderPanel = ref(true)
const showMyMembers = ref(true)
const leaderStudentId = ref('')

// 按钮加载状态防抖
const loading = reactive({
  createActivity: false,
  addLeader: false,
  deleteLeader: null, // 存放当前删除的leaderID
  deleteHomework: false,
  nextWeek: false,
  score: null, // 存放当前打分的userId
  setActiveWeek: false
})

// 负责人视图
const currentUserId = ref(null)
const leaderMembers = ref([])
const scoreForm = reactive({})
const commentForm = reactive({})

// 创建活动
const showCreateActivity = ref(false)
const createForm = reactive({ grade: '', title: '' })

// 查看负责人成员弹窗
const showLeaderMembers = ref(false)
const leaderDetailName = ref('')
const leaderDetailMembers = ref([])

// 分页
const myMembersCurrentPage = ref(1)
const myMembersPageSize = ref(10)
const cycleMembersCurrentPage = ref(1)
const cycleMembersPageSize = ref(10)

const myMembersPage = computed(() => {
  const start = (myMembersCurrentPage.value - 1) * myMembersPageSize.value
  return leaderMembers.value.slice(start, start + myMembersPageSize.value)
})
const cycleMembersPage = computed(() => {
  const start = (cycleMembersCurrentPage.value - 1) * cycleMembersPageSize.value
  return cycleMembers.value.slice(start, start + cycleMembersPageSize.value)
})

const uploadHeaders = computed(() => ({
  'sap-token': localStorage.getItem('sap-token') || ''
}))

const myLeader = computed(() => {
  if (!currentUserId.value || !leaders.value.length) return null
  return leaders.value.find(l => l.userId === currentUserId.value) || null
})

const isCurrentGrade = computed(() => gradeFilter.value === systemGrade.value)
const isEditable = computed(() => isCurrentGrade.value && selectedActivity.value?.status === 1)

const unscoredCount = computed(() => leaderMembers.value.filter(m => m.score === null).length)
// 当前周期（currentWeek）全部成员中未评分人数，用于开启下一周期前的提示
const currentCycleUnscoredCount = computed(() => {
  let n = 0
  for (const l of leaders.value) {
    for (const m of (l._memberDetails || [])) {
      if (m.score === null || m.score === undefined) n++
    }
  }
  return n
})
const unscoredWithScoreCount = computed(() =>
  leaderMembers.value.filter(m => m.score === null && scoreForm[m.userId] !== undefined && scoreForm[m.userId] !== null).length
)

const currentHomework = computed(() => {
  const w = selectedActivity.value?.activeWeek || selectedActivity.value?.currentWeek
  if (!w || !weekSummaries.value.length) return null
  const ws = weekSummaries.value.find(s => s.week === w)
  if (ws && ws.homeworkFileUrl) {
    return { title: ws.homeworkTitle, fileUrl: ws.homeworkFileUrl, fileName: ws.homeworkFileName }
  }
  return null
})

const showLeaderMemberDetail = (l) => {
  leaderDetailName.value = l.userName
  // 从 cycleMembers 中取该负责人下的成员详情（含姓名和分数）
  const members = (l._memberDetails || []).map(m => ({
    studentId: m.studentId,
    userName: m.userName,
    totalScore: m.score
  }))
  leaderDetailMembers.value = members
  showLeaderMembers.value = true
}

// ---- 数据加载 ----
const switchGrade = async (g) => {
  gradeFilter.value = g
  selectedActivity.value = null
  activityDetail.value = null
  leaders.value = []
  weekSummaries.value = []
  cycleMembers.value = []
  leaderMembers.value = []
  await loadStudyActivities()
  if (studyActivities.value.length > 0) {
    const latest = studyActivities.value.find(a => a.status === 1) || studyActivities.value[0]
    await selectActivity(latest)
  }
}

onMounted(async () => {
  try {
    const userRes = await getUserInfo()
    currentUserId.value = userRes.data?.user?.id || null
  } catch (e) {}
  try {
    const res = await getSettingValue('current_grade')
    systemGrade.value = res.data || '2025'
    gradeFilter.value = systemGrade.value
    createForm.grade = systemGrade.value
    try {
      const gRes = await getGrades()
      const grades = gRes.data || []
      if (!grades.includes(systemGrade.value)) grades.push(systemGrade.value)
      gradeList.value = grades.sort((a, b) => Number(b) - Number(a))
    } catch (e2) {
      gradeList.value = [systemGrade.value]
    }
    await loadStudyActivities()
    if (studyActivities.value.length > 0) {
      const latest = studyActivities.value.find(a => a.status === 1) || studyActivities.value[0]
      await selectActivity(latest)
    }
  } catch (e) {}
})

const loadStudyActivities = async () => {
  try {
    const res = await getStudyActivities(gradeFilter.value)
    studyActivities.value = res.data || []
  } catch (e) {}
}

const selectActivity = async (act) => {
  selectedActivity.value = act
  unifiedWeek.value = act.currentWeek
  leaderMembers.value = []
  await loadActivityDetail(act.id)
}

const handleSwitchActivity = (act) => selectActivity(act)

const loadActivityDetail = async (id) => {
  try {
    const res = await getStudyActivityDetail(id)
    const d = res.data
    activityDetail.value = d
    leaders.value = d.leaders || []
    weekSummaries.value = d.weekSummaries || []
    if (d.activity) {
      selectedActivity.value.activeWeek = d.activity.activeWeek
      selectedActivity.value.currentWeek = d.activity.currentWeek
    }
    await enrichLeadersWithMembers(id)
    loadUnifiedData(id, unifiedWeek.value)
  } catch (e) {}
}

const enrichLeadersWithMembers = async (activityId) => {
  const week = selectedActivity.value?.currentWeek || 1
  try {
    const res = await getStudyCycleDetail({ activityId, week })
    const allMembers = res.data || []
    for (const l of leaders.value) {
      const myMembers = allMembers.filter(m => m.leaderId === l.id)
      // 排序：未上传+未评分优先
      myMembers.sort((a, b) => {
        const aUrgent = (!a.submitted ? 2 : 0) + ((a.score === null || a.score === undefined) ? 1 : 0)
        const bUrgent = (!b.submitted ? 2 : 0) + ((b.score === null || b.score === undefined) ? 1 : 0)
        return bUrgent - aUrgent
      })
      l.memberStudentIds = myMembers.map(m => m.studentId)
      l._memberDetails = myMembers
      l._sortedMembers = myMembers
      l._scoredCount = myMembers.filter(m => m.score !== null && m.score !== undefined).length
    }
  } catch (e) {}
}

const loadUnifiedData = async (activityId, week) => {
  if (week === 0) return
  try {
    const res = await getStudyCycleDetail({ activityId, week })
    const allMembers = res.data || []
    // 排序：先按分数降序，未打分的按是否提交作业降序
    allMembers.sort((a, b) => {
      const sa = a.score !== null && a.score !== undefined ? a.score : -1
      const sb = b.score !== null && b.score !== undefined ? b.score : -1
      if (sa !== sb) return sb - sa
      const subA = a.submitted ? 1 : 0
      const subB = b.submitted ? 1 : 0
      return subB - subA
    })
    cycleMembers.value = allMembers
    if (myLeader.value) {
      leaderMembers.value = allMembers.filter(m => m.leaderId === myLeader.value.id)
      Object.keys(scoreForm).forEach(k => delete scoreForm[k])
      Object.keys(commentForm).forEach(k => delete commentForm[k])
    }
  } catch (e) {}
}

const switchUnifiedWeek = (week) => {
  unifiedWeek.value = week
  if (week > 0 && selectedActivity.value) {
    loadUnifiedData(selectedActivity.value.id, week)
  }
}

// ---- 操作 ----
const handleCreateActivity = async () => {
  if (loading.createActivity) return
  loading.createActivity = true
  ElMessage({ message: '创建中...', type: 'info', duration: 0, grouping: true })
  try {
    await createStudyActivity({ title: createForm.title })
    ElMessage.closeAll()
    ElMessage.success('创建成功')
    showCreateActivity.value = false
    createForm.title = ''
    await loadStudyActivities()
    if (studyActivities.value.length > 0) selectActivity(studyActivities.value[0])
  } catch (e) { ElMessage.closeAll() } finally { loading.createActivity = false }
}

const handleAddLeader = async () => {
  if (!leaderStudentId.value || loading.addLeader) return
  loading.addLeader = true
  ElMessage({ message: '添加中...', type: 'info', duration: 0, grouping: true })
  try {
    await addStudyLeader({ activityId: selectedActivity.value.id, studentId: leaderStudentId.value })
    ElMessage.closeAll()
    ElMessage.success('添加成功')
    leaderStudentId.value = ''
    await loadActivityDetail(selectedActivity.value.id)
  } catch (e) { ElMessage.closeAll() } finally { loading.addLeader = false }
}

const handleDeleteLeader = async (id) => {
  if (loading.deleteLeader) return
  try {
    await ElMessageBox.confirm('移除该负责人？其下成员将自动重新分配', '提示')
    loading.deleteLeader = id
    ElMessage({ message: '移除中...', type: 'info', duration: 0, grouping: true })
    await deleteStudyLeader(id)
    ElMessage.closeAll()
    ElMessage.success('已移除')
    await loadActivityDetail(selectedActivity.value.id)
  } catch (e) { ElMessage.closeAll() } finally { loading.deleteLeader = null }
}

const handleHomeworkUpload = async (res) => {
  if (res.code === 200 && res.data) {
    try {
      await uploadHomework({
        activityId: selectedActivity.value.id,
        week: selectedActivity.value.activeWeek || selectedActivity.value.currentWeek,
        fileUrl: res.data.url,
        fileName: res.data.name || res.data.url.split('/').pop()
      })
      ElMessage.success('作业上传成功')
      loadActivityDetail(selectedActivity.value.id)
    } catch (e) {}
  } else {
    // 原生上传返回 HTTP200+{code!=200}，需手动提示后端原因（如 COS 未配置）
    ElMessage.error(res.message || '文件上传失败')
  }
}

const handleHomeworkReplace = async (res) => {
  if (res.code === 200 && res.data) {
    try {
      await uploadHomework({
        activityId: selectedActivity.value.id,
        week: selectedActivity.value.activeWeek || selectedActivity.value.currentWeek,
        fileUrl: res.data.url,
        fileName: res.data.name || res.data.url.split('/').pop()
      })
      ElMessage.success('作业已替换')
      loadActivityDetail(selectedActivity.value.id)
    } catch (e) {}
  } else {
    ElMessage.error(res.message || '文件上传失败')
  }
}

const handleDeleteHomework = async () => {
  if (loading.deleteHomework) return
  try {
    await ElMessageBox.confirm('确认删除当前周期作业文件？', '提示')
    loading.deleteHomework = true
    ElMessage({ message: '删除中...', type: 'info', duration: 0, grouping: true })
    await deleteHomework({
      activityId: selectedActivity.value.id,
      week: selectedActivity.value.activeWeek || selectedActivity.value.currentWeek
    })
    ElMessage.closeAll()
    ElMessage.success('已删除')
    await loadActivityDetail(selectedActivity.value.id)
  } catch (e) { ElMessage.closeAll() } finally { loading.deleteHomework = false }
}

const downloadHomework = () => {
  if (currentHomework.value?.fileUrl) {
    downloadFileByUrl(currentHomework.value.fileUrl, currentHomework.value.fileName || 'homework')
  }
}

const downloadFile = (material) => {
  if (material?.fileUrl) {
    const name = material.fileName || material.fileUrl.split('/').pop()
    window.open(`/api/file/download?url=${encodeURIComponent(material.fileUrl)}&name=${encodeURIComponent(name)}`, '_blank')
  }
}

const downloadFileByUrl = (url, filename) => {
  window.open(`/api/file/download?url=${encodeURIComponent(url)}&name=${encodeURIComponent(filename)}`, '_blank')
}

const openUrl = (url) => window.open(url, '_blank')

const handleSetActiveWeek = async (week) => {
  if (loading.setActiveWeek) return
  loading.setActiveWeek = true
  try {
    await setActiveWeek(selectedActivity.value.id, week)
    selectedActivity.value.activeWeek = week
    ElMessage.success(`当前周期已切换为 ${week}`)
  } catch (e) {} finally { loading.setActiveWeek = false }
}

const handleNextWeek = async () => {
  if (loading.nextWeek) return
  // 前置校验：必须有负责人
  if (leaders.value.length === 0) {
    ElMessage.warning('请先添加负责人，再开启下一周期')
    return
  }
  const nextNum = selectedActivity.value.currentWeek + 1
  const unscored = currentCycleUnscoredCount.value
  let msg = `<div style="line-height:1.7;">
    <p>即将开启<strong>第${nextNum}周期</strong>。</p>
    <p style="color:#f56c6c;font-weight:bold;">⚠️ 此操作不可撤销，上一周期分组将被打乱并重新随机均分。</p>`
  if (unscored > 0) {
    msg += `<p style="color:#e6a23c;">当前周期尚有 ${unscored} 人未评分，是否继续？</p>`
  }
  msg += '</div>'
  try {
    await ElMessageBox.confirm(msg, '开启下一周期', {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '确认开启',
      cancelButtonText: '取消',
      type: 'warning'
    })
    loading.nextWeek = true
    ElMessage({ message: '生成中...', type: 'info', duration: 0, grouping: true })
    await nextWeek({ activityId: selectedActivity.value.id })
    ElMessage.closeAll()
    ElMessage.success('已进入下一周期')
    selectedActivity.value.currentWeek++
    selectedActivity.value.activeWeek = selectedActivity.value.currentWeek
    unifiedWeek.value = selectedActivity.value.currentWeek
    leaderMembers.value = []
    await loadStudyActivities()
    await loadActivityDetail(selectedActivity.value.id)
  } catch (e) { ElMessage.closeAll() } finally { loading.nextWeek = false }
}

const handleScore = async (row) => {
  const score = scoreForm[row.userId]
  if (score === undefined || score === null) return
  if (loading.score) return
  // 前端校验，与后端保持一致：分数 1-10、评语必填且不少于10字
  if (score < 1 || score > 10) {
    ElMessage.warning('分数需在 1-10 之间')
    return
  }
  const comment = (commentForm[row.userId] || '').trim()
  if (comment.length < 10) {
    ElMessage.warning('评语必填，且不得少于10个字')
    return
  }
  loading.score = row.userId
  try {
    await submitScore({
      activityId: selectedActivity.value.id,
      week: unifiedWeek.value,
      memberUserId: row.userId,
      score, comment
    })
    ElMessage.success(`${row.userName} 评分成功: ${score}分`)
    row.score = score
    row.comment = comment
    const cm = cycleMembers.value.find(m => m.userId === row.userId)
    if (cm) { cm.score = score; cm.comment = comment }
    delete scoreForm[row.userId]
    delete commentForm[row.userId]
  } catch (e) {} finally { loading.score = null }
}
</script>
