<template>
  <div class="join-page zen-fade-in">
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: flex-end;">
      <div>
        <h2>入 · 会</h2>
        <p>入会管理与审核</p>
      </div>
      <div v-if="isLeader">
        <el-button type="primary" plain @click="$router.push('/settings')">
          <span style="margin-right: 4px;">🛠️</span> 点击配置正式群信息
        </el-button>
      </div>
    </div>

    <!-- 顶部卡片：收款码 + 入会开关 -->
    <div class="zen-card" v-if="isLeader || isManager" style="margin-bottom: 16px;">
      <div class="top-control-bar">
        <!-- 左侧：我的收款码 -->
        <div class="qr-section" v-if="isManager">
          <div class="stat-title" style="margin-bottom:8px;">📱 我的收款码</div>
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
          <div class="stat-title">🚪 入会通道</div>
          <p style="color:#999;font-size:12px;margin-top:4px;">{{ isLeader ? '开启后游客可申请加入协会' : '当前入会通道状态' }}</p>
          <div style="margin-top:8px;">
            <el-switch v-if="isLeader" v-model="joinEnabled" @change="handleToggle" :loading="toggleLoading"
              active-text="开启" inactive-text="关闭" />
            <el-tag v-else :type="joinEnabled ? 'success' : 'info'" size="large" effect="light">
              {{ joinEnabled ? '已开启' : '已关闭' }}
            </el-tag>
          </div>
        </div>
      </div>
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
              <el-radio-button :value="2">已审核通过</el-radio-button>
              <el-radio-button :value="null">全部</el-radio-button>
            </el-radio-group>
            <el-switch v-if="isLeader" v-model="onlyMine" @change="loadApplications"
              active-text="只看个人" inactive-text="查看全部" />
          </div>

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
                  <span v-else-if="app.status === 1" style="color:#409eff;font-size:12px;">待平台审核</span>
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
                <div class="review-card-row">
                  <span class="review-label">分配时间</span>
                  <span>{{ formatTime(app.assignedAt) }}</span>
                </div>
                <div class="review-card-row" v-if="app.submittedAt">
                  <span class="review-label">提交时间</span>
                  <span>{{ formatTime(app.submittedAt) }}</span>
                </div>
              </div>
              <div v-if="(isLeader || isManager) && app.status === 1" style="padding: 12px 16px; border-top: 1px dashed #ebeef5; display: flex; justify-content: flex-end;">
                <el-button type="primary" @click="handleApprove(app)" :loading="app._approving">核对并审批通过</el-button>
              </div>
            </div>
            <div v-if="!applications.length" style="grid-column:1/-1;text-align:center;padding:40px;color:#ccc;">
              暂无数据
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="负责人 & 升级" name="managers">
          <div class="managers-layout">
            <!-- 左列：负责人卡片列表（双列） -->
            <div class="managers-left">
              <div class="stat-title" style="margin-bottom:12px;">👥 负责人列表 <el-tag size="small" type="info" effect="plain" style="margin-left:6px;">{{ managers.length }}人</el-tag></div>
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
                <div class="stat-title" style="margin-bottom:12px;">➕ 添加负责人</div>
                <el-select v-model="newManagerId" placeholder="选择管理员" filterable style="width:100%;margin-bottom:10px;">
                  <el-option v-for="m in adminUsers" :key="m.id" :label="`${m.name} (${m.studentId})`" :value="m.id" />
                </el-select>
                <el-button type="primary" @click="handleAddManager" :loading="addingManager"
                  :disabled="!newManagerId" style="width:100%;">添加负责人</el-button>
              </div>

              <!-- 直接升级 -->
              <div class="zen-card">
                <div class="stat-title" style="margin-bottom:12px;">⚡ 直接升级会员</div>
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
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import {
  getJoinStatus, toggleJoin, getJoinManagers, addJoinManager,
  removeJoinManager, uploadManagerQr, getJoinApplications,
  approveJoinApplication, directUpgradeMember, getUserInfo,
  getUserList, uploadFile
} from '../api'

// 用户角色
const userRoles = ref([])
const userId = ref(null)
const isLeader = computed(() => userRoles.value.includes(0) || userRoles.value.includes(1))
const isManager = ref(false)

// 入会开关
const joinEnabled = ref(false)
const toggleLoading = ref(false)

// Tab
const activeTab = ref('review')

// 审核
const applications = ref([])
const statusFilter = ref(1)
const onlyMine = ref(true)

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
const statusText = (s) => ({ 0: '待提交', 1: '已提交', 2: '已通过' }[s] || '未知')
const statusTag = (s) => ({ 0: 'info', 1: 'warning', 2: 'success' }[s] || '')

// 加载数据
const loadUserInfo = async () => {
  try {
    const res = await getUserInfo()
    userRoles.value = res.data.roles || []
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
      const me = managers.value.find(m => m.userId === userId.value)
      if (me) {
        isManager.value = true
        myAlipayQr.value = me.alipayQr || ''
        myWechatQr.value = me.wechatQr || ''
      }
    }
  } catch (e) {}
}

const loadApplications = async () => {
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
      const order = { 1: 0, 0: 1, 2: 2 }
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
    
    applications.value = list.map(a => ({ ...a, _approving: false }))
  } catch (e) {}
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
  }
}

const handleAddManager = async () => {
  addingManager.value = true
  try {
    await addJoinManager(newManagerId.value)
    ElMessage.success('添加成功')
    newManagerId.value = null
    loadManagers()
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
  } catch (e) {
    // 接口失败由全局拦截器统一提示
  }
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(
      `<div style="font-size: 14px; line-height: 1.6;">
        <p style="margin-bottom: 12px; font-weight: bold; color: #f56c6c;">⚠️ 请仔细核对以下付款信息，一旦通过不可撤回：</p>
        <p><strong>姓名：</strong>${row.userName}</p>
        <p><strong>QQ号：</strong>${row.userQq || '—'}</p>
        <p><strong>交易单号：</strong><span style="color: #409eff; font-weight: bold; font-family: monospace; font-size: 16px;">${row.paymentCode || '—'}</span></p>
      </div>`,
      '审批资格确认',
      {
        dangerouslyUseHTMLString: true,
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
  } catch (e) {
    // 上传/接口失败由全局拦截器统一提示（COS 未配置时显示后端引导文案）
  }
}

onMounted(async () => {
  await loadUserInfo()
  await loadManagers()
  loadJoinStatus()
  if (isLeader.value || isManager.value) {
    loadApplications()
    if (isLeader.value) loadAdminUsers()
  }
})
</script>


