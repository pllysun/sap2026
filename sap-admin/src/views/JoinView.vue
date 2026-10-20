<template>
  <div class="join-page zen-fade-in">
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: flex-end;">
      <div>
        <h2>入会管理</h2>
        <p>入会管理与审核</p>
      </div>
      <div v-if="isLeader">
        <el-button type="primary" plain @click="$router.push('/settings')">
          <el-icon><Setting /></el-icon> 配置会费与入群信息
        </el-button>
      </div>
    </div>

    <!-- 顶部卡片：收款码 + 入会开关 -->
    <div class="zen-card" v-if="isLeader || isManager" style="margin-bottom: 16px;">
      <div class="top-control-bar">
        <!-- 左侧：我的收款码 -->
        <div class="qr-section" v-if="isManager">
          <div class="stat-title" style="margin-bottom:8px;"><el-icon><Wallet /></el-icon> 我的收款码</div>
          <div style="display:flex;gap:16px;">
            <div>
              <p style="margin-bottom:4px;font-size:12px;color:#999;">支付宝</p>
              <el-upload :show-file-list="false" :http-request="(opt) => handleUploadQr(opt, 'alipay')" accept="image/*">
                <el-image v-if="myAlipayQr" :src="myAlipayQr" :preview-src-list="[myAlipayQr]"
                  style="width:160px;height:160px;border-radius:8px;cursor:pointer;" fit="cover" />
                <el-button v-else size="small" type="primary" plain>上传</el-button>
              </el-upload>
            </div>
            <div>
              <p style="margin-bottom:4px;font-size:12px;color:#999;">微信</p>
              <el-upload :show-file-list="false" :http-request="(opt) => handleUploadQr(opt, 'wechat')" accept="image/*">
                <el-image v-if="myWechatQr" :src="myWechatQr" :preview-src-list="[myWechatQr]"
                  style="width:160px;height:160px;border-radius:8px;cursor:pointer;" fit="cover" />
                <el-button v-else size="small" type="primary" plain>上传</el-button>
              </el-upload>
            </div>
          </div>
        </div>
        <div class="qr-section" v-else></div>

        <!-- 右侧：入会通道状态 -->
        <div class="toggle-section">
          <div class="stat-title"><el-icon><Switch /></el-icon> 入会通道</div>
          <p class="join-control-description">开启后首页展示醒目入会入口，网页和新版 App 注册仅需图形验证码；关闭后恢复邮箱验证。已有入会申请仍可办理。</p>
          <div style="margin-top:8px;">
            <el-switch v-if="isLeader" v-model="joinEnabled" @change="handleToggle" :loading="toggleLoading"
              :disabled="toggleLoading || readinessLoading || (!joinEnabled && !readiness?.ready)"
              active-text="开启" inactive-text="关闭" />
            <el-tag v-else :type="joinEnabled ? 'success' : 'info'" size="large" effect="light">
              {{ joinEnabled ? '已开启' : '已关闭' }}
            </el-tag>
          </div>
        </div>
      </div>
      <div class="join-readiness" v-if="readiness">
        <div class="join-readiness__summary"><strong><el-icon><InfoFilled /></el-icon> 开放前检查</strong><span>{{ readiness.grade }} 届</span><span>会费 ¥ {{ readiness.membershipFee ?? '待配置' }}</span><span>可用负责人 {{ readiness.readyManagerCount }} / {{ readiness.managerCount }}</span><el-tag :type="readiness.ready ? 'success' : 'warning'" size="small">{{ readiness.ready ? '已具备接待条件' : '待完善' }}</el-tag><el-button link :loading="readinessLoading" @click="loadReadiness">重新检查</el-button></div>
        <ul v-if="readiness.issues?.length"><li v-for="issue in readiness.issues" :key="issue">{{ issue }}</li></ul>
        <p>先同步或添加负责人，再由负责人上传收款码；会长可查看全部申请，并在人工核验后直接通过待提交申请。</p>
      </div>
      <el-alert v-else-if="readinessError" title="入会准备信息加载失败" type="warning" :closable="false"><el-button link @click="loadReadiness">重新检查</el-button></el-alert>
    </div>

    <!-- 统一双Tab -->
    <template v-if="isLeader || isManager">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="入会审核" name="review">
          <!-- 筛选 -->
          <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap;">
            <el-radio-group v-model="statusFilter" @change="loadApplications">
              <el-radio-button :value="0">已分配负责人</el-radio-button>
              <el-radio-button :value="1">已提交交易单号</el-radio-button>
              <el-radio-button :value="3">待补充资料</el-radio-button>
              <el-radio-button :value="2">已审核通过</el-radio-button>
              <el-radio-button :value="null">全部</el-radio-button>
            </el-radio-group>
            <el-switch v-if="isLeader" v-model="onlyMine" @change="loadApplications"
              active-text="只看个人" inactive-text="查看全部" />
            <el-button :loading="applicationsLoading" @click="loadApplications">刷新申请</el-button>
          </div>
          <el-alert v-if="applicationsError" title="申请列表加载失败，请点击刷新重试" type="warning" :closable="false" style="margin-bottom:16px;" />

          <div class="review-grid">
            <div class="review-card" v-for="app in applications" :key="app.id">
              <div class="review-card-header">
                <div>
                  <span class="review-card-name">{{ app.userName }}</span>
                  <el-tag :type="statusTag(app.status)" size="small" style="margin-left:6px;">{{ statusText(app.status) }}</el-tag>
                </div>
                <template v-if="isLeader || isManager">
                  <span v-if="app.status === 2" style="color:#999;font-size:12px;">已通过</span>
                  <span v-else-if="app.status === 0" style="color:#e6a23c;font-size:12px;">等待凭证上传</span>
                  <span v-else-if="app.status === 1" style="color:#3B82F6;font-size:12px;">待平台审核</span>
                  <span v-else-if="app.status === 3" style="color:#b17a30;font-size:12px;">等待资料更正</span>
                </template>
              </div>
              <div class="review-card-body">
                <div class="review-card-row">
                  <span class="review-label">学号</span>
                  <span>{{ app.studentId }}</span>
                </div>
                <div class="review-card-row">
                  <span class="review-label">QQ</span>
                  <span>{{ app.userQq || '—' }}</span>
                </div>
                <div class="review-card-row">
                  <span class="review-label">负责人</span>
                  <span>{{ app.managerName || '—' }}</span>
                </div>
                <div class="review-card-row">
                  <span class="review-label">交易单号</span>
                  <span>{{ app.paymentCode || '—' }}</span>
                </div>
                <div class="review-card-row"><span class="review-label">入会会费</span><span>¥ {{ app.membershipFee }} · {{ app.grade }} 届</span></div>
                <div v-if="app.reviewNote" class="review-card-row"><span class="review-label">补充说明</span><span>{{ app.reviewNote }}</span></div>
                <div class="review-card-row">
                  <span class="review-label">分配时间</span>
                  <span>{{ formatTime(app.assignedAt) }}</span>
                </div>
                <div class="review-card-row" v-if="app.submittedAt">
                  <span class="review-label">提交时间</span>
                  <span>{{ formatTime(app.submittedAt) }}</span>
                </div>
              </div>
              <div v-if="canReview(app) && app.status !== 2" class="join-review-actions">
                <el-button v-if="app.status !== 3" @click="handleReturn(app)" :loading="app._returning" :disabled="app._approving">退回补充</el-button>
                <el-button type="primary" @click="handleApprove(app)" :loading="app._approving" :disabled="app._returning">{{ app.status === 1 ? '核对并审批通过' : '人工核验并通过' }}</el-button>
              </div>
            </div>
            <div v-if="!applications.length && !applicationsError" style="grid-column:1/-1;text-align:center;padding:40px;color:#888;">
              {{ applicationsLoading ? '正在加载申请…' : '当前筛选条件下暂无申请' }}
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="负责人 & 升级" name="managers">
          <div class="managers-layout">
            <!-- 左列：负责人卡片列表（双列） -->
            <div class="managers-left">
              <div class="join-manager-heading"><div class="stat-title"><el-icon><User /></el-icon> 负责人列表 <el-tag size="small" type="info" effect="plain">{{ managers.length }}人</el-tag></div><el-button v-if="isLeader" :loading="syncingManagers" @click="handleSyncManagers"><el-icon><Refresh /></el-icon> 同步本届管理层</el-button></div>
              <div class="manager-grid">
                <div class="manager-card" v-for="m in managers" :key="m.id">
                  <div class="manager-card-top">
                    <div class="manager-info">
                      <span class="manager-name">{{ m.name }}</span>
                      <span class="manager-sid">{{ m.studentId }}</span>
                    </div>
                    <el-popconfirm v-if="isLeader" title="确定移除?" @confirm="handleRemoveManager(m)">
                      <template #reference>
                        <el-button type="danger" size="small" text circle>
                          <el-icon><Close /></el-icon>
                        </el-button>
                      </template>
                    </el-popconfirm>
                  </div>
                  <div class="manager-card-bottom">
                    <span class="manager-qq" v-if="m.qq">QQ: {{ m.qq }}</span>
                    <div class="manager-qr-thumbs">
                      <el-image v-if="m.alipayQr" :src="m.alipayQr" :preview-src-list="[m.alipayQr]"
                        style="width:50px;height:50px;border-radius:4px;" fit="cover" />
                      <el-image v-if="m.wechatQr" :src="m.wechatQr" :preview-src-list="[m.wechatQr]"
                        style="width:50px;height:50px;border-radius:4px;" fit="cover" />
                      <span v-if="!m.alipayQr && !m.wechatQr" class="no-qr">未上传</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 右列：操作区域（仅会长/超管） -->
            <div class="managers-right" v-if="isLeader">
              <!-- 添加负责人 -->
              <div class="zen-card" style="margin-bottom:16px;">
                <div class="stat-title" style="margin-bottom:12px;"><el-icon><Plus /></el-icon> 添加负责人</div>
                <el-select v-model="newManagerId" placeholder="选择管理员" filterable style="width:100%;margin-bottom:10px;">
                  <el-option v-for="m in adminUsers" :key="m.id" :label="`${m.name} (${m.studentId})`" :value="m.id" />
                </el-select>
                <el-button type="primary" @click="handleAddManager" :loading="addingManager"
                  :disabled="!newManagerId" style="width:100%;">添加负责人</el-button>
              </div>

              <!-- 直接升级 -->
              <div class="zen-card">
                <div class="stat-title" style="margin-bottom:12px;"><el-icon><Promotion /></el-icon> 直接升级会员</div>
                <el-input v-model="upgradeStudentId" placeholder="输入学号" clearable style="margin-bottom:10px;" />
                <el-button type="warning" @click="handleDirectUpgrade" :loading="upgrading"
                  :disabled="!upgradeStudentId" style="width:100%;">直接升级</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 无权限提示 -->
    <div v-else class="zen-card" style="text-align:center;padding:60px;">
      <p style="color:#999;font-size:15px;">暂无权限访问此页面</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Setting, Wallet, Switch, User, Refresh, Plus, Promotion, InfoFilled } from '@element-plus/icons-vue'
import {
  getJoinStatus, toggleJoin, getJoinManagers, addJoinManager,
  removeJoinManager, uploadManagerQr, getJoinApplications,
  approveJoinApplication, directUpgradeMember, getUserInfo,
  getUserList, uploadFile, getJoinReadiness, syncJoinManagers, returnJoinApplication
} from '../api'

// 用户角色
const userRoles = ref([])
const userId = ref(null)
const isLeader = computed(() => userRoles.value.includes(0) || userRoles.value.includes(1))
const isManager = ref(false)

// 入会开关
const joinEnabled = ref(false)
const toggleLoading = ref(false)
const readiness = ref(null), readinessLoading = ref(false), readinessError = ref(false), syncingManagers = ref(false)

// Tab
const activeTab = ref('review')

// 审核
const applications = ref([])
const applicationsLoading = ref(false), applicationsError = ref(false)
const statusFilter = ref(1)
const onlyMine = ref(false)

// 负责人
const managers = ref([])
const adminUsers = ref([])
const newManagerId = ref(null)
const addingManager = ref(false)

// 直接升级
const upgradeStudentId = ref('')
const upgrading = ref(false)

// 负责人自己的二维码
const myAlipayQr = ref('')
const myWechatQr = ref('')

const formatTime = (t) => {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
const statusText = (s) => ({ 0: '待提交', 1: '已提交', 2: '已通过', 3: '待补充' }[s] || '未知')
const statusTag = (s) => ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }[s] || '')
const canReview = (app) => isLeader.value || (isManager.value && String(app.managerId) === String(userId.value))

async function loadReadiness() {
  readinessLoading.value = true; readinessError.value = false
  try { readiness.value = (await getJoinReadiness()).data }
  catch { readinessError.value = true; readiness.value = null }
  finally { readinessLoading.value = false }
}

async function handleSyncManagers() {
  syncingManagers.value = true
  try { await syncJoinManagers(); await loadManagers(); await loadReadiness(); ElMessage.success('已同步，请由负责人上传收款码并完善联系 QQ') }
  catch { /* 请求拦截器展示具体原因 */ }
  finally { syncingManagers.value = false }
}

// 加载数据
const loadUserInfo = async () => {
  try {
    const res = await getUserInfo()
    userRoles.value = (res.data.roles || []).map(Number)
    userId.value = res.data.user.id
  } catch (e) {}
}

const loadJoinStatus = async () => {
  try {
    const res = await getJoinStatus()
    joinEnabled.value = res.data === true
  } catch (e) {}
}

const loadManagers = async () => {
  try {
    const res = await getJoinManagers()
    managers.value = res.data || []
    // 如果我是负责人
    if (userId.value) {
      const me = managers.value.find(m => String(m.userId) === String(userId.value))
      isManager.value = !!me
      if (me) {
        isManager.value = true
        myAlipayQr.value = me.alipayQr || ''
        myWechatQr.value = me.wechatQr || ''
      }
    }
  } catch (e) {}
}

const loadApplications = async () => {
  applicationsLoading.value = true; applicationsError.value = false
  try {
    const params = {}
    if (statusFilter.value !== null) params.status = statusFilter.value
    if (!isLeader.value) {
      params.onlyMine = true
    } else {
      params.onlyMine = onlyMine.value
    }
    const res = await getJoinApplications(params)
    let list = res.data || []
    
    // 按优先级排序：处理中的放最前面
    list.sort((a, b) => {
      // 权重 0 优先级最高
      // 状态1：已提交交易单号，待管理员审核 (最高优先级，需要处理)
      // 状态0：等待用户凭证上传
      // 状态2：已通过审批
      const order = { 1: 0, 3: 1, 0: 2, 2: 3 }
      const wa = order[a.status] ?? 99
      const wb = order[b.status] ?? 99
      
      if (wa !== wb) {
        return wa - wb
      }
      
      // 同一状态下，按最新时间倒序排列
      const timeA = new Date(a.submittedAt || a.assignedAt || 0).getTime()
      const timeB = new Date(b.submittedAt || b.assignedAt || 0).getTime()
      return timeB - timeA
    })
    
    applications.value = list.map(a => ({ ...a, _approving: false, _returning: false }))
  } catch { applicationsError.value = true; applications.value = [] }
  finally { applicationsLoading.value = false }
}

const loadAdminUsers = async () => {
  try {
    const res = await getUserList({ size: 1000, current: 1 })
    adminUsers.value = (res.data?.records || [])
  } catch (e) {}
}

// 操作
const handleToggle = async (val) => {
  toggleLoading.value = true
  try {
    await toggleJoin(val)
    ElMessage.success(val ? '入会通道已开启' : '入会通道已关闭')
    if (val) loadManagers()
  } catch (e) {
    joinEnabled.value = !val
    // 接口失败由全局拦截器统一提示
  } finally {
    toggleLoading.value = false
    await loadReadiness()
  }
}

const handleAddManager = async () => {
  addingManager.value = true
  try {
    await addJoinManager(newManagerId.value)
    ElMessage.success('添加成功')
    newManagerId.value = null
    loadManagers()
    loadReadiness()
  } catch (e) {
    // 接口失败由全局拦截器统一提示
  } finally {
    addingManager.value = false
  }
}

const handleRemoveManager = async (row) => {
  try {
    await removeJoinManager(row.id)
    ElMessage.success('已移除')
    loadManagers()
    loadReadiness()
  } catch (e) {
    // 接口失败由全局拦截器统一提示
  }
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(
      h('div', { class: 'join-approval-confirm' }, [
        h('p', { class: 'join-approval-confirm__notice' }, row.status === 1 ? '请核实实际收款后通过，系统将升级会员并记录一笔会费。' : '该申请尚未提交付款资料。你可以在人工确认入会资格及收款情况后直接通过。'),
        h('p', `姓名：${row.userName}`),
        h('p', `QQ 号：${row.userQq || '—'}`),
        h('p', `交易单号：${row.paymentCode || '未填写，请人工核对'}`),
        h('p', `会费：¥ ${row.membershipFee} · ${row.grade} 届`)
      ]),
      '审批资格确认',
      {
        confirmButtonText: '确认信息无误并通过',
        cancelButtonText: '取消'
      }
    )
  } catch { return }

  row._approving = true
  try {
    await approveJoinApplication(row.id)
    ElMessage.success('审核通过，已升级为正式成员')
    loadApplications()
  } catch (e) {
    // 接口失败由全局拦截器统一提示
  } finally {
    row._approving = false
  }
}

async function handleReturn(row) {
  let reason
  try {
    const answer = await ElMessageBox.prompt('说明需要补充或核对的资料，申请人会在入会页看到该说明。已付款无需重复缴费。', '退回补充资料', {
      inputType: 'textarea', inputPlaceholder: '例如：请核对交易单号，并通过 QQ 发来付款截图。',
      inputValidator: value => !!value?.trim() && value.trim().length <= 500 || '请填写 1 至 500 字说明',
      confirmButtonText: '退回补充', cancelButtonText: '取消'
    })
    reason = answer.value.trim()
  } catch { return }
  row._returning = true
  try { await returnJoinApplication(row.id, reason); ElMessage.success('已退回补充，申请人无需重新缴费'); await loadApplications() }
  catch { /* 请求拦截器展示具体原因 */ }
  finally { row._returning = false }
}

const handleDirectUpgrade = async () => {
  const sid = upgradeStudentId.value.trim()
  if (!sid) return

  // 用本地已加载的申请列表做前置查重，避免无效请求与重复操作
  const existing = applications.value.find(a => String(a.studentId) === sid)
  if (existing && existing.status === 2) {
    ElMessage.warning(`学号 ${sid} 已是正式会员，无需重复升级`)
    return
  }
  if (existing) {
    try {
      await ElMessageBox.confirm(
        `学号 ${sid} 已有进行中的入会申请（${statusText(existing.status)}），建议前往「入会审核」处理，是否仍要直接升级？`,
        '提示',
        { confirmButtonText: '仍要直接升级', cancelButtonText: '去审核', type: 'warning' }
      )
    } catch {
      activeTab.value = 'review'
      return
    }
  }

  try {
    await ElMessageBox.confirm(
      `将直接把学号 ${sid} 升级为正式会员并自动生成一笔会费收入记录，确认？`,
      '升级确认',
      { confirmButtonText: '确认升级', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  upgrading.value = true
  try {
    await directUpgradeMember(sid)
    ElMessage.success('升级成功')
    upgradeStudentId.value = ''
    loadApplications()
  } catch (e) {
    // 接口失败由全局拦截器统一提示
  } finally {
    upgrading.value = false
  }
}

const handleUploadQr = async (options, type) => {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const res = await uploadFile(formData)
    // 后端返回 {url, name}
    const url = res.data.url
    const body = {}
    if (type === 'alipay') {
      body.alipayQr = url
      myAlipayQr.value = url
    } else {
      body.wechatQr = url
      myWechatQr.value = url
    }
    await uploadManagerQr(body)
    ElMessage.success('上传成功')
    loadManagers()
    loadReadiness()
  } catch (e) {
    // 上传/接口失败由全局拦截器统一提示（COS 未配置时显示后端引导文案）
  }
}

onMounted(async () => {
  await loadUserInfo()
  await loadManagers()
  loadJoinStatus()
  if (isLeader.value || isManager.value) {
    loadReadiness()
    loadApplications()
    if (isLeader.value) loadAdminUsers()
  }
})
</script>


