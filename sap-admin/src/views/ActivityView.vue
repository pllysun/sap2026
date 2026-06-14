<template>
  <div class="activity-page zen-fade-in">
    <div class="page-header">
      <h2>行 · 迹</h2>
      <p>社团活动记录管理</p>
    </div>

    <div class="zen-card">
      <div class="filter-bar">
        <el-select v-model="gradeInput" placeholder="选择年份" style="width: 140px" @change="loadActivities">
          <el-option v-for="y in yearOptions" :key="y" :label="y + '年'" :value="y" />
        </el-select>
        <el-button type="primary" @click="openAddDialog">新增活动</el-button>
      </div>

      <div class="activity-list">
        <div v-for="act in activities" :key="act.id" class="activity-item">
          <div class="act-header">
            <div class="act-seq">第{{ act.seqNum }}次</div>
            <h3 class="act-title">{{ act.title }}</h3>
            <div class="act-actions">
              <el-button text size="small" type="primary" @click="openViewDialog(act)">查看</el-button>
              <el-button text size="small" @click="openEditDialog(act)">编辑</el-button>
              <el-button text type="danger" size="small" @click="handleDelete(act.id)">删除</el-button>
            </div>
          </div>
          <p class="act-content" v-if="act.content">{{ act.content }}</p>
          <div class="act-images" v-if="act.images && act.images.length">
            <el-image
              v-for="img in act.images" :key="img.id"
              :src="img.imageUrl"
              fit="cover"
              class="act-img"
              :preview-src-list="act.images.map(i => i.imageUrl)"
            />
          </div>
          <div class="act-time">{{ act.createdAt }}</div>
        </div>
        <el-empty v-if="!activities.length" description="暂无活动" />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="showDialog" :title="isEdit ? '编辑活动' : '新增活动'" width="560px" append-to-body>
      <el-form :model="form" label-width="80px">
        <el-form-item label="年份" v-if="!isEdit">
          <el-select v-model="form.grade" placeholder="选择年份" style="width: 100%" @change="onFormGradeChange">
            <el-option v-for="y in createYearOptions" :key="y" :label="y + '年'" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="第N次" v-if="!isEdit">
          <el-input :model-value="nextSeqNum" disabled />
        </el-form-item>
        <el-form-item label="活动名称">
          <el-input v-model="form.title" placeholder="活动名称" />
        </el-form-item>
        <el-form-item label="活动内容">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="活动描述(选填)" />
        </el-form-item>
        <el-form-item label="活动图片">
          <div style="width: 100%">
            <el-upload
              action="/api/file/upload"
              :headers="uploadHeaders"
              multiple
              list-type="picture-card"
              :file-list="fileList"
              :on-success="handleUploadSuccess"
              :on-remove="handleUploadRemove"
              :on-error="handleUploadError"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <div v-if="cosConfigured === false" style="color: var(--zen-text-muted); font-size: 12px; margin-top: 4px;">
              对象存储未配置，图片上传将不可用，请联系管理员在系统设置中配置
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确 认</el-button>
      </template>
    </el-dialog>

    <!-- 查看弹窗 -->
    <el-dialog v-model="showViewDialog" title="活动详情" width="680px" append-to-body>
      <div class="view-detail" v-if="viewAct">
        <div class="view-info-grid">
          <div class="view-info-item">
            <span class="view-label">年份</span>
            <span class="view-value">{{ viewAct.grade }}年</span>
          </div>
          <div class="view-info-item">
            <span class="view-label">次数</span>
            <span class="view-value">第{{ viewAct.seqNum }}次</span>
          </div>
          <div class="view-info-item">
            <span class="view-label">名称</span>
            <span class="view-value">{{ viewAct.title }}</span>
          </div>
          <div class="view-info-item">
            <span class="view-label">时间</span>
            <span class="view-value">{{ viewAct.createdAt }}</span>
          </div>
        </div>
        <div class="view-content" v-if="viewAct.content">
          <span class="view-label">描述</span>
          <p>{{ viewAct.content }}</p>
        </div>
        <div class="view-images" v-if="viewAct.images && viewAct.images.length">
          <span class="view-label" style="display: block; margin-bottom: 12px">活动图片（{{ viewAct.images.length }}张）</span>
          <div class="view-images-grid">
            <el-image
              v-for="(img, idx) in viewAct.images" :key="img.id || idx"
              :src="img.imageUrl"
              fit="cover"
              class="view-img"
              :preview-src-list="viewAct.images.map(i => i.imageUrl)"
              :initial-index="idx"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import { Plus } from '@element-plus/icons-vue'
import { getActivities, addActivity, updateActivity, deleteActivity, getSettingValue, getActivityYears, getActivityCount } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const gradeInput = ref('')
const activities = ref([])
const getCurrentAcademicYear = () => {
  const now = new Date()
  const year = now.getFullYear()
  return now.getMonth() + 1 >= 9 ? String(year) : String(year - 1)
}

const yearOptions = ref([])      // 筛选下拉用的年份
const currentGrade = ref(getCurrentAcademicYear()) // 系统当前年级
const showDialog = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const nextSeqNum = ref(1)
const fileList = ref([])

// 查看弹窗
const showViewDialog = ref(false)
const viewAct = ref(null)

const cosConfigured = ref(null)

const form = reactive({
  grade: '',
  title: '',
  content: '',
  imageUrls: []
})

const uploadHeaders = computed(() => ({
  'sap-token': localStorage.getItem('sap-token') || ''
}))

// 新增活动时的年份选项：2020 ~ currentGrade
const createYearOptions = computed(() => {
  const end = parseInt(currentGrade.value) || parseInt(getCurrentAcademicYear())
  const years = []
  for (let y = end; y >= 2020; y--) {
    years.push(String(y))
  }
  return years
})

onMounted(async () => {
  try {
    const res = await getSettingValue('current_grade')
    currentGrade.value = res.data || getCurrentAcademicYear()
    gradeInput.value = currentGrade.value
  } catch (e) {
    currentGrade.value = getCurrentAcademicYear()
    gradeInput.value = currentGrade.value
  }
  await loadYears()
  loadActivities()
  checkCosStatus()
})

const checkCosStatus = async () => {
  // 直接用 axios 调用以静默处理（不触发全局错误 toast）
  try {
    const { data } = await axios.get('/api/file/cos-status', {
      headers: { 'sap-token': localStorage.getItem('sap-token') || '' }
    })
    if (data?.code === 200) cosConfigured.value = data.data?.configured !== false
  } catch (e) {
    cosConfigured.value = null
  }
}

const loadYears = async () => {
  try {
    const res = await getActivityYears()
    const years = res.data || []
    // 确保当前年级在列表中
    if (!years.includes(currentGrade.value)) years.unshift(currentGrade.value)
    yearOptions.value = years
  } catch (e) {
    yearOptions.value = [currentGrade.value]
  }
}

const loadActivities = async () => {
  try {
    const res = await getActivities(gradeInput.value)
    activities.value = res.data || []
  } catch (e) {}
}

const openAddDialog = () => {
  isEdit.value = false
  form.grade = currentGrade.value
  form.title = ''
  form.content = ''
  form.imageUrls = []
  fileList.value = []
  onFormGradeChange(form.grade)
  showDialog.value = true
}

const onFormGradeChange = async (grade) => {
  try {
    const res = await getActivityCount(grade)
    nextSeqNum.value = Number(res.data || 0) + 1
  } catch (e) {
    nextSeqNum.value = 1
  }
}

const openEditDialog = (act) => {
  isEdit.value = true
  editId.value = act.id
  form.title = act.title
  form.content = act.content || ''
  form.imageUrls = act.images ? act.images.map(i => i.imageUrl) : []
  fileList.value = act.images ? act.images.map(i => ({ url: i.imageUrl })) : []
  showDialog.value = true
}

const openViewDialog = (act) => {
  viewAct.value = act
  showViewDialog.value = true
}

const handleUploadSuccess = (res, file) => {
  if (res.code === 200) {
    form.imageUrls.push(res.data.url)
  } else {
    // 后端返回 HTTP 200 但 body code 非 200（如 COS 未配置），走的是 on-success
    ElMessage.error(res.message || '上传失败')
    fileList.value = fileList.value.filter(f => f.uid !== file.uid)
  }
}

const handleUploadError = () => {
  ElMessage.error('上传失败，请稍后重试')
}

const handleUploadRemove = (file) => {
  const url = file.url || file.response?.data?.url
  form.imageUrls = form.imageUrls.filter(u => u !== url)
}

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await updateActivity(editId.value, { title: form.title, content: form.content, imageUrls: form.imageUrls })
    } else {
      await addActivity({ grade: form.grade, title: form.title, content: form.content, imageUrls: form.imageUrls })
    }
    ElMessage.success(isEdit.value ? '修改成功' : '添加成功')
    showDialog.value = false
    await loadYears()
    loadActivities()
  } catch (e) {}
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除此活动？', '提示')
    await deleteActivity(id)
    ElMessage.success('已删除')
    await loadYears()
    loadActivities()
  } catch (e) {}
}
</script>


