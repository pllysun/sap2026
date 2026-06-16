<template>
  <div class="finance-page zen-fade-in">
    <div class="page-header">
      <h2>财务管理</h2>
      <p>社团财务收支管理</p>
    </div>

    <!-- 统计卡片 -->
    <div class="fin-stat-bar">
      <div class="fin-stat income">
        <span class="fs-label">收入</span>
        <span class="fs-value">¥{{ stats.totalIncome || '0.00' }}</span>
      </div>
      <div class="fin-stat expense">
        <span class="fs-label">支出</span>
        <span class="fs-value">¥{{ stats.totalExpense || '0.00' }}</span>
      </div>
      <div class="fin-stat balance">
        <span class="fs-label">结余</span>
        <span class="fs-value">¥{{ stats.balance || '0.00' }}</span>
      </div>
    </div>

    <div class="zen-card">
      <div class="filter-bar">
        <el-input v-model="gradeFilter" placeholder="年级" style="width: 120px" />
        <el-button @click="loadBills">查询</el-button>
        <el-button type="primary" @click="openAddDialog">新增账单</el-button>
        <el-button @click="handleExport">导出Excel</el-button>
      </div>

      <el-table :data="bills" stripe>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.billType === 1 ? 'success' : 'danger'" effect="plain" size="small">
              {{ row.billType === 1 ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="180" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.billType === 1 ? 'var(--zen-success)' : 'var(--zen-danger)' }">
              {{ row.billType === 1 ? '+' : '-' }}¥{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="billTime" label="时间" width="170" />
        <el-table-column prop="remark" label="备注" width="150" />
        <el-table-column label="凭证" width="120">
          <template #default="{ row }">
            <div v-if="row.images && row.images.length" class="bill-thumbs">
              <el-image
                v-for="(img, idx) in row.images.slice(0, 2)"
                :key="idx"
                :src="img.imageUrl"
                fit="cover"
                class="bill-thumb"
                :preview-src-list="row.images.map(i => i.imageUrl)"
                :initial-index="idx"
                preview-teleported
              />
              <span v-if="row.images.length > 2" class="bill-thumb-more" @click="openViewDialog(row)">+{{ row.images.length - 2 }}</span>
            </div>
            <span v-else style="color: var(--zen-text-muted); font-size: 12px;">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" width="80" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 4px;">
              <el-button text size="small" @click="openViewDialog(row)">查看</el-button>
              <el-button text size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button text type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          layout="total, prev, pager, next"
          @current-change="loadBills"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="showDialog" :title="isEdit ? '编辑账单' : '新增账单'" width="520px" append-to-body>
      <el-form :model="form" label-width="80px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.billType">
            <el-radio :value="0">支出</el-radio>
            <el-radio :value="1">收入</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" placeholder="账单内容" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="form.amount" :precision="2" :min="0" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="form.billTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="消费时间" />
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model="form.grade" disabled />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
        <el-form-item label="凭证图片">
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
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="handleSubmit">确 认</el-button>
      </template>
    </el-dialog>

    <!-- 查看弹窗（只读） -->
    <el-dialog v-model="showViewDialog" title="账单详情" width="560px" append-to-body>
      <div class="bill-view-detail">
        <div class="bill-view-row">
          <span class="bill-view-label">类型</span>
          <el-tag :type="viewRow.billType === 1 ? 'success' : 'danger'" effect="plain" size="small">
            {{ viewRow.billType === 1 ? '收入' : '支出' }}
          </el-tag>
        </div>
        <div class="bill-view-row">
          <span class="bill-view-label">内容</span>
          <span>{{ viewRow.content }}</span>
        </div>
        <div class="bill-view-row">
          <span class="bill-view-label">金额</span>
          <span :style="{ color: viewRow.billType === 1 ? 'var(--zen-success)' : 'var(--zen-danger)', fontWeight: 500 }">
            {{ viewRow.billType === 1 ? '+' : '-' }}¥{{ viewRow.amount }}
          </span>
        </div>
        <div class="bill-view-row">
          <span class="bill-view-label">时间</span>
          <span>{{ viewRow.billTime }}</span>
        </div>
        <div class="bill-view-row">
          <span class="bill-view-label">年级</span>
          <span>{{ viewRow.grade }}</span>
        </div>
        <div v-if="viewRow.remark" class="bill-view-row">
          <span class="bill-view-label">备注</span>
          <span>{{ viewRow.remark }}</span>
        </div>
        <div class="bill-view-row bill-view-images-row">
          <span class="bill-view-label">凭证图片</span>
          <div v-if="viewRow.images && viewRow.images.length" class="bill-view-images">
            <el-image
              v-for="(img, idx) in viewRow.images"
              :key="idx"
              :src="img.imageUrl"
              fit="cover"
              class="bill-view-img"
              :preview-src-list="viewRow.images.map(i => i.imageUrl)"
              :initial-index="idx"
              preview-teleported
            />
          </div>
          <span v-else style="color: var(--zen-text-muted);">暂无凭证图片</span>
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
import { getBills, addBill, updateBill, deleteBill, getBillStats, getSettingValue } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const gradeFilter = ref('')
const bills = ref([])
const stats = reactive({})
const pagination = reactive({ current: 1, size: 10, total: 0 })
const showDialog = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const fileList = ref([])
const submitting = ref(false)
const cosConfigured = ref(null)
const showViewDialog = ref(false)
const viewRow = reactive({ billType: 0, content: '', amount: 0, billTime: '', remark: '', grade: '', images: [] })

const form = reactive({
  billType: 0, content: '', amount: 0, billTime: '', remark: '', grade: '', imageUrls: []
})

const uploadHeaders = computed(() => ({
  'sap-token': localStorage.getItem('sap-token') || ''
}))

onMounted(async () => {
  try {
    const res = await getSettingValue('current_grade')
    gradeFilter.value = res.data || '2025'
    form.grade = gradeFilter.value
    loadBills()
    loadStats()
  } catch (e) {}
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

const loadBills = async () => {
  try {
    const res = await getBills({ grade: gradeFilter.value, current: pagination.current, size: pagination.size })
    const page = res.data
    bills.value = page.records || []
    pagination.total = Number(page.total || 0)
  } catch (e) {}
}

const loadStats = async () => {
  try {
    const res = await getBillStats(gradeFilter.value)
    Object.assign(stats, res.data)
  } catch (e) {}
}

const openAddDialog = () => {
  isEdit.value = false
  Object.assign(form, { billType: 0, content: '', amount: 0, billTime: '', remark: '', grade: gradeFilter.value, imageUrls: [] })
  fileList.value = []
  showDialog.value = true
}

const openViewDialog = (row) => {
  Object.assign(viewRow, {
    billType: row.billType,
    content: row.content,
    amount: row.amount,
    billTime: row.billTime,
    remark: row.remark || '',
    grade: row.grade || '',
    images: row.images || []
  })
  showViewDialog.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    billType: row.billType, content: row.content, amount: row.amount,
    billTime: row.billTime, remark: row.remark || '', grade: row.grade || '',
    imageUrls: row.images ? row.images.map(i => i.imageUrl) : []
  })
  fileList.value = row.images ? row.images.map(i => ({ url: i.imageUrl })) : []
  showDialog.value = true
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
  if (!(form.amount > 0)) { ElMessage.warning('金额必须大于 0'); return }
  if (!form.content || !form.content.trim()) { ElMessage.warning('请填写账单内容'); return }
  if (!form.billTime) { ElMessage.warning('请选择账单时间'); return }
  if (submitting.value) return
  submitting.value = true
  try {
    if (isEdit.value) { await updateBill(editId.value, form) }
    else { await addBill(form) }
    ElMessage.success(isEdit.value ? '修改成功' : '添加成功')
    showDialog.value = false
    loadBills()
    loadStats()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除？', '提示')
  } catch (e) {
    return // 用户取消，静默
  }
  try {
    await deleteBill(id)
    ElMessage.success('已删除')
    // 若删的是当前页最后一条且不在首页，回退一页避免停在空白页
    if (bills.value.length === 1 && pagination.current > 1) {
      pagination.current--
    }
    loadBills()
    loadStats()
  } catch (e) {}
}

const handleExport = () => {
  const token = localStorage.getItem('sap-token')
  window.open(`/api/bill/export?grade=${gradeFilter.value}&sap-token=${token}`)
}
</script>


