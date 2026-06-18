<template>
  <div class="settings-page zen-fade-in">
    <div class="page-header">
      <h2>系统设置</h2>
      <p>系统配置与身份管理</p>
    </div>

    <!-- ===== 顶部横幅：当前年级 ===== -->
    <div class="grade-banner">
      <div class="grade-banner__left">
        <div class="grade-banner__icon">🎓</div>
        <div class="grade-banner__info">
          <span class="grade-banner__label">当前年级</span>
          <span class="grade-banner__desc">此参数决定学习小组活动的年级归属，影响全局数据</span>
        </div>
      </div>
      <div class="grade-banner__right">
        <template v-if="canEdit">
          <el-input-number v-model="currentGrade" :min="2018" :max="2099" :step="1" controls-position="right" size="large" style="width: 160px" placeholder="未设置" />
          <el-button type="primary" @click="handleSaveGrade" :loading="gradeSaving" :disabled="!gradeChanged">
            {{ gradeChanged ? '保存' : (originalGrade ? '已同步' : '待配置') }}
          </el-button>
        </template>
        <template v-else>
          <span class="grade-banner__value">{{ currentGrade || '未设置' }}</span>
          <el-tag type="info" effect="plain" size="small">仅超管/会长可修改</el-tag>
        </template>
      </div>
    </div>

    <!-- ===== 三栏网格 ===== -->
    <div class="settings-layout">

      <!-- 左列：对象存储 + 身份管理 -->
      <div class="settings-col">

        <!-- 对象存储配置 -->
        <div class="zen-card">
          <div class="card-header">
            <span class="card-header__icon">☁️</span>
            <span class="card-header__title">对象存储</span>
          </div>
          <div class="cos-config-grid">
            <div class="cos-field">
              <label>Bucket 名称</label>
              <el-input v-model="cosConfig.bucketName" size="small" placeholder="如 sap-1256773093" />
            </div>
            <div class="cos-field">
              <label>Region</label>
              <el-input v-model="cosConfig.region" size="small" placeholder="如 cos.ap-nanjing" />
            </div>
            <div class="cos-field">
              <label>SecretId</label>
              <div class="cos-secret-row">
                <el-input :model-value="cosConfig.secretId" size="small" disabled class="secret-display" />
                <el-button size="small" @click="showReplaceSecret('secretId')">替换</el-button>
              </div>
            </div>
            <div class="cos-field">
              <label>SecretKey</label>
              <div class="cos-secret-row">
                <el-input :model-value="cosConfig.secretKey" size="small" disabled class="secret-display" />
                <el-button size="small" @click="showReplaceSecret('secretKey')">替换</el-button>
              </div>
            </div>
            <div class="cos-field" style="grid-column: 1 / -1">
              <label>自定义下载域名（CDN，选填）</label>
              <el-input v-model="cosConfig.cdnDomain" size="small" placeholder="如 dl.csuftsap.top —— APK 必须用自定义域名下载；留空则用 COS 默认域名" />
            </div>
          </div>
          <div class="cos-actions">
            <el-button type="primary" size="small" @click="handleSaveCosConfig" :loading="cosSaving">保存配置</el-button>
            <el-button size="small" @click="handleTestCos" :loading="cosTesting">
              {{ cosTesting ? '检测中...' : '检测连通性' }}
            </el-button>
            <span v-if="cosTestResult" :class="['cos-test-result', cosTestResult.ok ? 'success' : 'fail']">
              {{ cosTestResult.msg }}
            </span>
          </div>
          <p style="font-size:12px;color:#aaa;margin-top:8px;">「检测连通性」测试的是<strong>已保存</strong>的配置，修改后请先点「保存配置」再检测</p>
        </div>

        <!-- 重置账号密码（仅超管/会长可见可用） -->
        <div class="zen-card" v-if="canEdit">
          <div class="card-header">
            <span class="card-header__icon">🔑</span>
            <span class="card-header__title">重置账号密码</span>
          </div>
          <div class="cos-config-grid">
            <div class="cos-field" style="grid-column: 1 / -1">
              <label>账号（学号）</label>
              <el-input v-model="resetPwd.studentId" size="small" placeholder="输入要重置密码的账号学号" clearable />
            </div>
            <div class="cos-field" style="grid-column: 1 / -1">
              <label>新密码</label>
              <el-input v-model="resetPwd.newPassword" type="password" show-password size="small" placeholder="6-64 位" clearable />
            </div>
          </div>
          <div class="cos-actions">
            <el-button type="danger" size="small" @click="handleResetPassword" :loading="resetPwd.loading">重置密码</el-button>
          </div>
          <p style="font-size:12px;color:#aaa;margin-top:8px;">直接将该账号密码改为新密码（无需原密码）。出于安全，非超级管理员不能重置超管/会长的密码。</p>
        </div>

        <!-- 入会会费配置 -->
        <div class="zen-card">
          <div class="card-header">
            <span class="card-header__icon">💰</span>
            <span class="card-header__title">入会会费</span>
          </div>
          <div style="display:flex;align-items:center;gap:12px;">
            <div style="flex:1;">
              <label style="font-size:12px;color:#888;display:block;margin-bottom:4px;">会费金额（元）</label>
              <el-input-number v-model="membershipFee" :min="0" :max="9999" :step="5" :precision="0" size="small" style="width:100%;" />
            </div>
            <el-button type="primary" size="small" @click="handleSaveFee" :loading="feeSaving" :disabled="!feeChanged" style="margin-top:18px;">
              {{ feeChanged ? '保存' : '已保存' }}
            </el-button>
          </div>
          <p style="font-size:12px;color:#aaa;margin-top:8px;">审核通过入会申请时，将自动记录此金额为收入</p>
        </div>

        <!-- 非会员登录开关 -->
        <div class="zen-card">
          <div class="card-header">
            <span class="card-header__icon">🔓</span>
            <span class="card-header__title">非会员登录</span>
          </div>
          <div style="display:flex;align-items:center;gap:12px;">
            <p style="flex:1;font-size:13px;color:#666;margin:0;">
              开启后，非协会会员也可登录 App（仅课表功能，需自行 WebVPN 导入）；关闭则仅会员可登录。
            </p>
            <el-switch v-model="guestLogin" :loading="guestSaving" @change="handleSaveGuestLogin" />
          </div>
        </div>

        <!-- 招新群配置 -->
        <div class="zen-card">
          <div class="card-header">
            <span class="card-header__icon">💬</span>
            <span class="card-header__title">新生群配置 (入会后展示)</span>
          </div>
          <div class="qr-config-grid" style="grid-template-columns: 1fr;">
            <div class="qr-card" style="box-shadow: none; border: 1px dashed #e4e7ed; padding: 12px; margin: 0;">
              <div class="qr-preview" style="height: 120px;">
                <img v-if="footerConfig.join_qq_group_url" :src="footerConfig.join_qq_group_url" alt="招新群二维码" style="max-height: 120px; object-fit: contain;" />
                <div v-else class="qr-placeholder" style="height: 120px;">暂无群二维码</div>
              </div>
              <el-input v-model="footerConfig.join_qq_group_name" size="small" placeholder="群名称，如 2026软件协会新生群" style="margin: 8px 0;" />
              <div class="qr-upload-row">
                <el-input v-model="footerConfig.join_qq_group_url" size="small" placeholder="二维码图片URL" style="flex:1;" />
                <el-upload :action="uploadAction" :headers="uploadHeaders" :show-file-list="false" accept="image/*"
                  :on-success="(res) => onQrUploaded(res, 'join_qq_group_url')">
                  <el-button size="small" type="primary">上传</el-button>
                </el-upload>
              </div>
              <el-input v-model="footerConfig.join_group_link" size="small" placeholder="一键加群邀请链接 (从手机QQ获取Url)" style="margin-top: 8px;" />
              <div style="margin-top: 12px; text-align: right;">
                <el-button type="primary" size="small" @click="handleSaveFooterConfig" :loading="footerSaving">保存入会配置</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右列：身份管理 + 页脚配置 -->
      <div class="settings-col">
        <!-- 身份管理 -->
        <div class="zen-card">
          <div class="card-header">
            <span class="card-header__icon">👤</span>
            <span class="card-header__title">身份管理</span>
          </div>
          <el-table :data="positions" stripe size="small">
            <el-table-column prop="positionName" label="身份名称" />
            <el-table-column prop="maxCount" label="最大人数" width="100">
              <template #default="{ row }">
                <span v-if="row.positionName === '成员'">不限</span>
                <span v-else-if="row.isSystem">{{ row.maxCount }}（固定）</span>
                <span v-else>{{ row.maxCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="roleCode" label="权限码" width="90">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ roleCodeLabel(row.roleCode) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="60" />
            <el-table-column label="类型" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.isSystem" type="warning" effect="plain" size="small">内置</el-tag>
                <el-tag v-else effect="plain" size="small">自定义</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <template v-if="!row.isSystem">
                  <el-button text size="small" @click="openEditPosition(row)">编辑</el-button>
                  <el-button text type="danger" size="small" @click="handleDeletePosition(row.id)">删除</el-button>
                </template>
                <span v-else style="color: var(--zen-text-muted); font-size: 12px">不可操作</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="add-setting" style="margin-top: 16px">
            <el-input v-model="newPosition.positionName" placeholder="身份名称" style="width: 120px" size="small" />
            <el-input-number v-model="newPosition.maxCount" :min="1" placeholder="最大人数" style="width: 110px" size="small" />
            <el-select v-model="newPosition.roleCode" placeholder="权限" style="width: 100px" size="small">
              <el-option :value="1" label="会长" />
              <el-option :value="2" label="管理员" />
              <el-option :value="3" label="成员" />
            </el-select>
            <el-input-number v-model="newPosition.sortOrder" :min="1" placeholder="排序" style="width: 90px" size="small" />
            <el-button type="primary" size="small" @click="handleAddPosition">添加</el-button>
          </div>
        </div>

        <!-- 页脚与二维码配置 -->
        <div class="zen-card">
          <div class="card-header">
            <span class="card-header__icon">🌐</span>
            <span class="card-header__title">页脚与二维码</span>
          </div>
          <div class="footer-config-grid">
            <div class="footer-field">
              <label>版权主体名称</label>
              <el-input v-model="footerConfig.footer_copyright" size="small" placeholder="如 中南林业科技大学软件协会" />
            </div>
            <div class="footer-field">
              <label>地址</label>
              <el-input v-model="footerConfig.footer_address" size="small" placeholder="如 中南林业科技大学 学生活动中心1701" />
            </div>
            <div class="footer-field">
              <label>官方 QQ</label>
              <el-input v-model="footerConfig.footer_qq" size="small" placeholder="如 1576316531" />
            </div>
            <div class="footer-field">
              <label>联系邮箱</label>
              <el-input v-model="footerConfig.footer_email" size="small" placeholder="如 sap@csuft.edu.cn" />
            </div>
          </div>

          <el-divider content-position="left">页脚二维码配置（值为空则不展示）</el-divider>

          <div class="qr-config-grid" style="grid-template-columns: 1fr;">
            <div class="qr-card">
              <h4>官方QQ群二维码</h4>
              <div class="qr-preview">
                <img v-if="footerConfig.qr_qq_group_url" :src="footerConfig.qr_qq_group_url" alt="官方QQ群二维码" />
                <div v-else class="qr-placeholder">暂无图片</div>
              </div>
              <el-input v-model="footerConfig.qr_qq_group_name" size="small" placeholder="名称，如 官方QQ群" style="margin: 8px 0;" />
              <div class="qr-upload-row">
                <el-input v-model="footerConfig.qr_qq_group_url" size="small" placeholder="图片URL" style="flex:1;" />
                <el-upload :action="uploadAction" :headers="uploadHeaders" :show-file-list="false" accept="image/*"
                  :on-success="(res) => onQrUploaded(res, 'qr_qq_group_url')">
                  <el-button size="small" type="primary">上传</el-button>
                </el-upload>
              </div>
            </div>
            
            <div class="qr-card">
              <h4>官方QQ号二维码</h4>
              <div class="qr-preview">
                <img v-if="footerConfig.qr_qq_account_url" :src="footerConfig.qr_qq_account_url" alt="QQ号二维码" />
                <div v-else class="qr-placeholder">暂无图片</div>
              </div>
              <el-input v-model="footerConfig.qr_qq_account_name" size="small" placeholder="名称，如 官方QQ号" style="margin: 8px 0;" />
              <div class="qr-upload-row">
                <el-input v-model="footerConfig.qr_qq_account_url" size="small" placeholder="图片URL" style="flex:1;" />
                <el-upload :action="uploadAction" :headers="uploadHeaders" :show-file-list="false" accept="image/*"
                  :on-success="(res) => onQrUploaded(res, 'qr_qq_account_url')">
                  <el-button size="small" type="primary">上传</el-button>
                </el-upload>
              </div>
            </div>
          </div>

          <div style="margin-top: 16px; text-align: right;">
            <el-button type="primary" size="small" @click="handleSaveFooterConfig" :loading="footerSaving">保存页脚配置</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑身份弹窗 -->
    <el-dialog v-model="showEditPosition" title="编辑身份" width="400px" append-to-body>
      <el-form :model="editPositionForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="editPositionForm.positionName" /></el-form-item>
        <el-form-item label="最大人数"><el-input-number v-model="editPositionForm.maxCount" :min="1" /></el-form-item>
        <el-form-item label="权限码">
          <el-select v-model="editPositionForm.roleCode" style="width: 100%">
            <el-option :value="1" label="1 - 会长" />
            <el-option :value="2" label="2 - 管理员" />
            <el-option :value="3" label="3 - 成员" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editPositionForm.sortOrder" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditPosition = false">取消</el-button>
        <el-button type="primary" @click="handleUpdatePosition">确 认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { updateSetting, getPublicSettings, getSettingValue, getUserInfo, getPositions, addPosition, updatePosition, deletePosition, getCosConfig, updateCosConfig, testCosConnection, resetUserPassword } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const positions = ref([])
const showEditPosition = ref(false)
const editPositionId = ref(null)
const isSuperAdmin = ref(false)
const canEdit = ref(false)

// 重置账号密码
const resetPwd = reactive({ studentId: '', newPassword: '', loading: false })

const newPosition = reactive({ positionName: '', sortOrder: 99, maxCount: 1, roleCode: 2 })
const editPositionForm = reactive({ positionName: '', sortOrder: 0, maxCount: 1, roleCode: 2 })

// 当前年级
const currentGrade = ref(null)
const originalGrade = ref(null)
const gradeSaving = ref(false)
const gradeChanged = computed(() => currentGrade.value && currentGrade.value !== originalGrade.value)

// 会费
const membershipFee = ref(30)
const originalFee = ref(30)
const feeSaving = ref(false)
const feeChanged = computed(() => membershipFee.value !== originalFee.value)

// 非会员登录开关
const guestLogin = ref(false)
const guestSaving = ref(false)

const roleCodeLabel = (code) => {
  const map = { 0: '超管', 1: '会长', 2: '管理员', 3: '成员', 4: '游客' }
  return map[code] || code
}

// COS config
const cosConfig = reactive({ bucketName: '', region: '', cdnDomain: '', secretId: '', secretKey: '' })
const cosSaving = ref(false)
const cosTesting = ref(false)
const cosTestResult = ref(null)

onMounted(async () => {
  // 获取当前用户角色
  try {
    const res = await getUserInfo()
    const roles = res.data?.roles || []
    isSuperAdmin.value = roles.includes(0) || roles.includes('0')
    canEdit.value = roles.includes(0) || roles.includes('0') || roles.includes(1) || roles.includes('1')
    // 仅超管/会长可进入设置页：非授权用户直敲 URL 时拦回首页（后端亦有 SaCheckRole 兜底）
    if (!canEdit.value) {
      ElMessage.warning('无权访问系统设置')
      router.push('/dashboard')
      return
    }
  } catch (e) {}

  loadPositions()
  loadCosConfig()
  loadFooterConfig()
  loadCurrentGrade()
  loadMembershipFee()
  loadGuestLogin()
})

const loadGuestLogin = async () => {
  try {
    const res = await getSettingValue('allow_guest_login')
    guestLogin.value = res.data === 'true' || res.data === true
  } catch (e) {}
}

const handleSaveGuestLogin = async (val) => {
  guestSaving.value = true
  try {
    await updateSetting({ settingKey: 'allow_guest_login', settingValue: String(val) })
    ElMessage.success(val ? '已开放非会员登录' : '已关闭非会员登录')
  } catch (e) {
    guestLogin.value = !val // 失败回滚开关
  } finally {
    guestSaving.value = false
  }
}

const loadCurrentGrade = async () => {
  try {
    const res = await getSettingValue('current_grade')
    const val = parseInt(res.data)
    if (!isNaN(val)) {
      currentGrade.value = val
      originalGrade.value = val
    }
  } catch (e) {}
}

const handleSaveGrade = async () => {
  try {
    await ElMessageBox.confirm(
      '修改当前年级会影响入会负责人匹配、学习活动归属、财务年级统计等全局数据，确认修改？',
      '修改当前年级',
      { confirmButtonText: '确认修改', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  gradeSaving.value = true
  try {
    await updateSetting({ settingKey: 'current_grade', settingValue: String(currentGrade.value) })
    originalGrade.value = currentGrade.value
    ElMessage.success('当前年级已更新')
  } catch (e) {
    // 接口失败由全局拦截器统一提示
  } finally {
    gradeSaving.value = false
  }
}

const loadMembershipFee = async () => {
  try {
    const res = await getSettingValue('membership_fee')
    const val = parseInt(res.data)
    if (!isNaN(val)) {
      membershipFee.value = val
      originalFee.value = val
    }
  } catch (e) {}
}

const handleSaveFee = async () => {
  feeSaving.value = true
  try {
    await updateSetting({ settingKey: 'membership_fee', settingValue: String(membershipFee.value) })
    originalFee.value = membershipFee.value
    ElMessage.success('会费金额已更新')
  } catch (e) {
    // 失败由全局响应拦截器统一提示，避免重复弹窗
  } finally {
    feeSaving.value = false
  }
}

const handleResetPassword = async () => {
  const studentId = resetPwd.studentId.trim()
  const newPassword = resetPwd.newPassword
  if (!studentId) { ElMessage.warning('请输入账号学号'); return }
  if (!newPassword || newPassword.length < 6 || newPassword.length > 64) {
    ElMessage.warning('新密码长度需为 6-64 位'); return
  }
  try {
    await ElMessageBox.confirm(
      `确认将账号 ${studentId} 的密码重置为新密码？此操作立即生效。`,
      '重置密码', { type: 'warning' }
    )
  } catch (e) { return }
  resetPwd.loading = true
  try {
    await resetUserPassword({ studentId, newPassword })
    ElMessage.success('密码已重置')
    resetPwd.studentId = ''
    resetPwd.newPassword = ''
  } catch (e) {
    // 失败由全局响应拦截器统一提示（如账号不存在、无权重置）
  } finally {
    resetPwd.loading = false
  }
}

const loadCosConfig = async () => {
  try {
    const res = await getCosConfig()
    const d = res.data || {}
    cosConfig.bucketName = d.bucketName || ''
    cosConfig.region = d.region || ''
    cosConfig.cdnDomain = d.cdnDomain || ''
    cosConfig.secretId = d.secretId || ''
    cosConfig.secretKey = d.secretKey || ''
  } catch (e) {}
}

const showReplaceSecret = async (field) => {
  try {
    const { value } = await ElMessageBox.prompt(
      field === 'secretId' ? 'SecretId' : 'SecretKey',
      { inputPlaceholder: '粘贴完整密钥', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
    if (value && value.trim()) {
      cosConfig[field] = value.trim()
      ElMessage.info('点击保存生效')
    }
  } catch (e) {}
}

const handleSaveCosConfig = async () => {
  cosSaving.value = true
  cosTestResult.value = null
  try {
    await updateCosConfig(cosConfig)
    ElMessage.success('COS 配置已保存')
    await loadCosConfig()
  } catch (e) {}
  cosSaving.value = false
}

const handleTestCos = async () => {
  try {
    await ElMessageBox.confirm(
      '检测的是已保存的配置，请确认已先点击「保存配置」。是否继续检测？',
      '检测连通性',
      { confirmButtonText: '继续检测', cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }

  cosTesting.value = true
  cosTestResult.value = null
  try {
    await testCosConnection()
    cosTestResult.value = { ok: true, msg: '✓ 连通正常' }
  } catch (e) {
    cosTestResult.value = { ok: false, msg: '✗ 连通失败' }
  }
  cosTesting.value = false
}


const loadPositions = async () => {
  try {
    const res = await getPositions()
    positions.value = res.data || []
  } catch (e) {}
}

const handleAddPosition = async () => {
  try {
    await addPosition(newPosition)
    ElMessage.success('已添加')
    newPosition.positionName = ''
    newPosition.sortOrder = 99
    loadPositions()
  } catch (e) {}
}

const openEditPosition = (row) => {
  editPositionId.value = row.id
  editPositionForm.positionName = row.positionName
  editPositionForm.sortOrder = row.sortOrder
  editPositionForm.maxCount = row.maxCount || 1
  editPositionForm.roleCode = row.roleCode || 3
  showEditPosition.value = true
}

const handleUpdatePosition = async () => {
  try {
    await updatePosition(editPositionId.value, editPositionForm)
    ElMessage.success('修改成功')
    showEditPosition.value = false
    loadPositions()
  } catch (e) {}
}

const handleDeletePosition = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除此身份？', '提示')
    await deletePosition(id)
    ElMessage.success('已删除')
    loadPositions()
  } catch (e) {}
}

// ===== 页脚与二维码配置 =====
const footerConfig = reactive({
  footer_address: '', footer_qq: '', footer_email: '', footer_copyright: '',
  qr_qq_group_url: '', qr_qq_group_name: '',
  qr_qq_account_url: '', qr_qq_account_name: '',
  join_qq_group_url: '', join_qq_group_name: '',
  join_group_link: ''
})
const footerSaving = ref(false)
const uploadAction = '/api/file/upload'
const uploadHeaders = { 'sap-token': localStorage.getItem('sap-token') || '' }

const loadFooterConfig = async () => {
  try {
    const res = await getPublicSettings()
    const d = res.data || {}
    Object.keys(footerConfig).forEach(k => {
      if (d[k] !== undefined) footerConfig[k] = d[k]
    })
  } catch (e) {}
}

const onQrUploaded = (res, field) => {
  if (res && res.data && res.data.url) {
    footerConfig[field] = res.data.url
    ElMessage.success('图片上传成功，记得点击保存')
  } else {
    // 原生上传返回 HTTP200+{code!=200}，显示后端原因（如 COS 未配置时的引导文案）
    ElMessage.error(res?.message || '上传失败')
  }
}

const handleSaveFooterConfig = async () => {
  footerSaving.value = true
  try {
    const keys = Object.keys(footerConfig)
    // 并发提交所有配置，避免逐项串行导致中途失败半保存
    await Promise.all(
      keys.map(key => updateSetting({ settingKey: key, settingValue: footerConfig[key] }))
    )
    ElMessage.success('页脚配置保存成功')
  } catch (e) {
    // 单项失败时全局响应拦截器已弹出具体原因，此处不再重复提示
  } finally {
    footerSaving.value = false
  }
}
</script>


