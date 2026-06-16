<template>
  <div class="app-release-page zen-fade-in">
    <div class="page-header">
      <h2>App 版本发布</h2>
      <p>上传新版 APK 并发布，安卓 App 将自动检查更新。APK 走平台对象存储(COS)，不占后端带宽。</p>
    </div>

    <el-alert
      v-if="cosConfigured === false"
      type="warning"
      show-icon
      :closable="false"
      title="对象存储(COS)尚未配置"
      description="请先到「系统设置 → 对象存储」填写并保存 SecretId / SecretKey / 地域 / Bucket，否则无法上传 APK。"
      style="margin-bottom: 16px;"
    />

    <div class="release-layout">
      <!-- 当前线上版本 -->
      <div class="zen-card">
        <div class="card-title">当前线上版本</div>
        <template v-if="current.versionCode > 0">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="versionName">{{ current.versionName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="versionCode">{{ current.versionCode }}</el-descriptions-item>
            <el-descriptions-item label="强制更新">
              <el-tag :type="current.forceUpdate ? 'danger' : 'info'" size="small">
                {{ current.forceUpdate ? '是' : '否' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最低支持 versionCode">{{ current.minSupportedVersionCode }}</el-descriptions-item>
            <el-descriptions-item label="安装包大小">{{ fmtSize(current.size) }}</el-descriptions-item>
            <el-descriptions-item label="SHA-256">
              <span class="mono">{{ shortSha(current.sha256) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="下载地址">
              <a v-if="current.downloadUrl" :href="current.downloadUrl" target="_blank" class="mono link">{{ current.downloadUrl }}</a>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="更新说明">
              <pre class="changelog">{{ current.changelog || '-' }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else description="尚未发布任何版本" :image-size="80" />
      </div>

      <!-- 发布新版本 -->
      <div class="zen-card">
        <div class="card-title">发布新版本</div>
        <el-form label-width="130px" label-position="right">
          <el-form-item label="APK 文件" required>
            <input ref="fileInput" type="file" accept=".apk,application/vnd.android.package-archive"
                   style="display:none" @change="onPickFile" />
            <el-button @click="fileInput.click()" :icon="UploadFilled">选择 APK</el-button>
            <span v-if="apkFile" class="file-info">{{ apkFile.name }} · {{ fmtSize(apkFile.size) }}</span>
          </el-form-item>

          <el-form-item label="versionCode" required>
            <el-input-number v-model="form.versionCode" :min="1" :precision="0" :step="1" controls-position="right" />
            <span class="hint">与 App build.gradle.kts 的 versionCode 一致，且必须比上次大</span>
          </el-form-item>

          <el-form-item label="versionName" required>
            <el-input v-model="form.versionName" placeholder="如 1.1.0" style="max-width: 240px;" />
          </el-form-item>

          <el-form-item label="更新说明">
            <el-input v-model="form.changelog" type="textarea" :rows="4" placeholder="每行一条，如：&#10;· 新增…&#10;· 修复…" />
          </el-form-item>

          <el-form-item label="强制更新">
            <el-switch v-model="form.forceUpdate" />
            <span class="hint">开启后用户必须升级才能继续使用</span>
          </el-form-item>

          <el-form-item label="最低支持 versionCode">
            <el-input-number v-model="form.minSupportedVersionCode" :min="1" :precision="0" :step="1" controls-position="right" />
            <span class="hint">低于此 versionCode 的旧版强制升级</span>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="publishing" :disabled="cosConfigured === false" @click="handlePublish">
              {{ publishing ? '上传发布中…' : '上传并发布' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getUserInfo, getAppVersion, getCosStatus, publishAppVersion } from '../api'

const router = useRouter()
const fileInput = ref(null)
const apkFile = ref(null)
const publishing = ref(false)
const cosConfigured = ref(null)
const userRoles = ref([])
const current = ref({ versionCode: 0 })

const form = reactive({
  versionCode: 1,
  versionName: '',
  changelog: '',
  forceUpdate: false,
  minSupportedVersionCode: 1,
})

const isLeaderOrSuper = computed(() => userRoles.value.includes(0) || userRoles.value.includes(1))

onMounted(async () => {
  try {
    const res = await getUserInfo()
    userRoles.value = (res.data?.roles || []).map(Number)
    if (!isLeaderOrSuper.value) {
      ElMessage.warning('无权访问此页面')
      router.push('/dashboard')
      return
    }
  } catch (e) { return }
  loadCurrent()
  getCosStatus().then(r => { cosConfigured.value = !!r.data?.configured }).catch(() => {})
})

const loadCurrent = async () => {
  try {
    const res = await getAppVersion()
    current.value = res.data || { versionCode: 0 }
    // 预填下次版本号 = 当前 + 1
    if (current.value.versionCode > 0) form.versionCode = current.value.versionCode + 1
  } catch (e) {}
}

const onPickFile = (e) => {
  const f = e.target.files && e.target.files[0]
  apkFile.value = f || null
  e.target.value = '' // 允许重复选同名文件
}

const handlePublish = async () => {
  if (!apkFile.value) return ElMessage.warning('请先选择 APK 文件')
  if (!form.versionName) return ElMessage.warning('请填写 versionName')
  if (!form.versionCode || form.versionCode < 1) return ElMessage.warning('versionCode 必须为正整数')

  if (current.value.versionCode > 0 && form.versionCode <= current.value.versionCode) {
    try {
      await ElMessageBox.confirm(
        `新 versionCode(${form.versionCode}) 不大于当前线上(${current.value.versionCode})，用户将收不到更新提示。仍要发布？`,
        '确认', { type: 'warning' },
      )
    } catch (e) { return }
  }

  const fd = new FormData()
  fd.append('file', apkFile.value)
  fd.append('versionCode', form.versionCode)
  fd.append('versionName', form.versionName)
  fd.append('changelog', form.changelog || '')
  fd.append('forceUpdate', form.forceUpdate)
  fd.append('minSupportedVersionCode', form.minSupportedVersionCode || 1)

  publishing.value = true
  try {
    const res = await publishAppVersion(fd)
    ElMessage.success('发布成功')
    current.value = res.data || current.value
    apkFile.value = null
    if (current.value.versionCode > 0) form.versionCode = current.value.versionCode + 1
  } catch (e) {
    // 错误已由全局拦截器提示
  } finally {
    publishing.value = false
  }
}

const fmtSize = (bytes) => {
  const n = Number(bytes) || 0
  if (n <= 0) return '-'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}
const shortSha = (s) => (!s ? '-' : (s.length > 20 ? s.slice(0, 16) + '…' + s.slice(-4) : s))
</script>

<style scoped>
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0 0 4px; font-size: 20px; }
.page-header p { margin: 0; color: #909399; font-size: 13px; }
.release-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
.zen-card { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.card-title { font-size: 15px; font-weight: 600; margin-bottom: 16px; }
.file-info { margin-left: 12px; color: #606266; font-size: 13px; }
.hint { margin-left: 12px; color: #909399; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; word-break: break-all; }
.link { color: var(--el-color-primary); }
.changelog { margin: 0; white-space: pre-wrap; font-family: inherit; font-size: 13px; }
@media (max-width: 1000px) { .release-layout { grid-template-columns: 1fr; } }
</style>
