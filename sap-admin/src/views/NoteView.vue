<template>
  <div class="note-page zen-fade-in">
    <div class="page-header">
      <h2>笔 · 记</h2>
      <p>软协知识库，Markdown 笔记管理</p>
    </div>

    <div class="zen-card">
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索笔记标题…" clearable style="width: 240px"
          @keyup.enter="handleSearch" @clear="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="openAdd">新增笔记</el-button>
      </div>

      <el-table :data="noteList" stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="500" show-overflow-tooltip />
        <el-table-column prop="description" label="简介" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="color: var(--el-text-color-secondary);">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="上传者" width="100" align="center" />
        <el-table-column label="浏览" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ row.viewCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下载" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success" effect="plain">{{ row.downloadCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="200" align="center">
          <template #default="{ row }">
            <el-button text size="small" @click="openPreview(row)">预览</el-button>
            <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" type="success" @click="handleDownloadPdf(row)">下载</el-button>
            <el-button text size="small" type="warning" @click="openStats(row)">统计</el-button>
            <el-button text size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="note-pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="loadNotes"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="showDialog" :title="isEdit ? '编辑笔记' : '新增笔记'" width="800px" append-to-body top="5vh" class="note-dialog">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="可选，留空自动从内容提取" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2"
            placeholder="可选，用于列表页卡片展示的简要描述" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="上传方式" v-if="!isEdit">
          <el-radio-group v-model="uploadMode">
            <el-radio value="text">粘贴文本</el-radio>
            <el-radio value="file">上传文件</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Markdown" v-if="uploadMode === 'text' || isEdit">
          <el-input v-model="form.content" type="textarea" :rows="16"
            placeholder="在此粘贴 Markdown 内容…" style="font-family: 'Fira Code', monospace;" />
        </el-form-item>
        <el-form-item label="文件" v-if="uploadMode === 'file' && !isEdit">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".md,.markdown,.txt"
            :on-change="handleFileChange"
            drag
          >
            <el-icon style="font-size: 48px; color: var(--el-text-color-placeholder);"><Upload /></el-icon>
            <div style="margin-top: 8px; color: var(--el-text-color-secondary);">拖拽或点击上传 .md 文件</div>
          </el-upload>
        </el-form-item>
      </el-form>

      <!-- 预览 -->
      <div v-if="form.content" class="preview-section">
        <div class="preview-header">📖 预览</div>
        <div class="preview-body markdown-body" v-html="previewHtml"></div>
      </div>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确 认</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="showPreview" title="笔记预览" width="800px" append-to-body top="5vh">
      <h2 style="margin-bottom: 16px;">{{ previewNote.title }}</h2>
      <div class="preview-body markdown-body" v-html="previewNoteHtml"></div>
    </el-dialog>

    <!-- 统计弹窗 -->
    <el-dialog v-model="showStatsDialog" title="浏览/下载统计" width="700px" append-to-body>
      <div v-if="statsData" class="stats-content">
        <h3 style="margin-bottom: 12px;">{{ statsData.title }}</h3>
        <div class="stats-summary">
          <el-tag type="info" size="large" effect="dark">👁 浏览 {{ statsData.viewCount }} 人</el-tag>
          <el-tag type="success" size="large" effect="dark">⬇ 下载 {{ statsData.downloadCount }} 人</el-tag>
        </div>

        <el-tabs>
          <el-tab-pane label="浏览明细">
            <el-table :data="statsData.views" stripe size="small" max-height="400">
              <el-table-column prop="userName" label="姓名" width="100" />
              <el-table-column prop="studentId" label="学号" width="140" />
              <el-table-column prop="viewCount" label="次数" width="80" align="center" />
              <el-table-column label="最后浏览" min-width="160">
                <template #default="{ row }">{{ formatTime(row.lastViewAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!statsData.views?.length" description="暂无浏览记录" />
          </el-tab-pane>
          <el-tab-pane label="下载明细">
            <el-table :data="statsData.downloads" stripe size="small" max-height="400">
              <el-table-column prop="userName" label="姓名" width="100" />
              <el-table-column prop="studentId" label="学号" width="140" />
              <el-table-column prop="downloadCount" label="次数" width="80" align="center" />
              <el-table-column label="最后下载" min-width="160">
                <template #default="{ row }">{{ formatTime(row.lastDownloadAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!statsData.downloads?.length" description="暂无下载记录" />
          </el-tab-pane>
        </el-tabs>
      </div>
      <div v-else v-loading="true" style="height: 200px;"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getNoteList, addNote, uploadNote, updateNote, deleteNote, getNoteDetail, getNoteStats } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const keyword = ref('')
const noteList = ref([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const showDialog = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const uploadMode = ref('text')
const submitting = ref(false)
const form = reactive({ title: '', description: '', content: '' })
const uploadRef = ref(null)
const uploadFile = ref(null)

const showPreview = ref(false)
const previewNote = reactive({ title: '', content: '' })

const showStatsDialog = ref(false)
const statsData = ref(null)

const previewHtml = computed(() => {
  if (!form.content) return ''
  try { return DOMPurify.sanitize(marked(form.content)) } catch { return '' }
})

const previewNoteHtml = computed(() => {
  if (!previewNote.content) return ''
  try { return DOMPurify.sanitize(marked(previewNote.content)) } catch { return '' }
})

const formatTime = (t) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

const loadNotes = async () => {
  loading.value = true
  try {
    const res = await getNoteList({ current: page.value, size: pageSize, keyword: keyword.value || undefined })
    noteList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {}
  loading.value = false
}

// 搜索时重置到第 1 页，避免在非首页搜出只有少量结果的空白页
const handleSearch = () => {
  page.value = 1
  loadNotes()
}

const openAdd = () => {
  isEdit.value = false
  editId.value = null
  form.title = ''
  form.description = ''
  form.content = ''
  uploadMode.value = 'text'
  uploadFile.value = null
  showDialog.value = true
}

const openEdit = async (row) => {
  isEdit.value = true
  editId.value = row.id
  uploadMode.value = 'text'
  try {
    const res = await getNoteDetail(row.id)
    form.title = res.data.title || ''
    form.description = res.data.description || ''
    form.content = res.data.content || ''
  } catch (e) {}
  showDialog.value = true
}

const openPreview = async (row) => {
  try {
    const res = await getNoteDetail(row.id)
    previewNote.title = res.data.title || ''
    previewNote.content = res.data.content || ''
    showPreview.value = true
  } catch (e) {}
}

const openStats = async (row) => {
  statsData.value = null
  showStatsDialog.value = true
  try {
    const res = await getNoteStats(row.id)
    statsData.value = res.data
  } catch (e) {}
}

const handleFileChange = (file) => {
  uploadFile.value = file.raw
  // 读取文件内容用于预览
  const reader = new FileReader()
  reader.onload = (e) => { form.content = e.target.result }
  reader.readAsText(file.raw)
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateNote(editId.value, { title: form.title, description: form.description, content: form.content })
      ElMessage.success('更新成功')
    } else if (uploadMode.value === 'file' && uploadFile.value) {
      const fd = new FormData()
      fd.append('file', uploadFile.value)
      if (form.title) fd.append('title', form.title)
      if (form.description) fd.append('description', form.description)
      await uploadNote(fd)
      ElMessage.success('上传成功')
    } else {
      if (!form.content?.trim()) { ElMessage.warning('请输入内容'); submitting.value = false; return }
      await addNote({ title: form.title, description: form.description, content: form.content })
      ElMessage.success('添加成功')
    }
    showDialog.value = false
    loadNotes()
  } catch (e) {}
  submitting.value = false
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除此笔记？删除后不可恢复。', '提示', { type: 'warning' })
  } catch (e) {
    return // 用户取消，静默
  }
  try {
    await deleteNote(id)
    ElMessage.success('删除成功')
    // 若删的是当前页最后一条且不在首页，回退一页避免停在空白页
    if (noteList.value.length === 1 && page.value > 1) {
      page.value--
    }
    loadNotes()
  } catch (e) {}
}

/**
 * 下载 PDF：通过新窗口打开后端 PDF 下载接口
 */
const handleDownloadPdf = (row) => {
  const token = localStorage.getItem('sap-token') || ''
  const url = `/api/note/${row.id}/pdf?sap-token=${encodeURIComponent(token)}`
  window.open(url, '_blank')
  // 刷新列表以更新下载计数
  setTimeout(() => loadNotes(), 2000)
}

onMounted(() => { loadNotes() })
</script>

<style scoped>
.note-page .filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.note-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}


/* 预览区 */
.preview-section {
  margin-top: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}
.preview-header {
  background: var(--el-fill-color-light);
  padding: 8px 16px;
  font-weight: 600;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.preview-body {
  padding: 20px;
  max-height: 400px;
  overflow-y: auto;
  line-height: 1.8;
  font-size: 14px;
}

/* Markdown 渲染基础样式 */
.markdown-body :deep(h1) { font-size: 1.8em; margin: 0.6em 0 0.3em; border-bottom: 1px solid var(--el-border-color); padding-bottom: 0.3em; }
.markdown-body :deep(h2) { font-size: 1.5em; margin: 0.6em 0 0.3em; border-bottom: 1px solid var(--el-border-color-lighter); padding-bottom: 0.2em; }
.markdown-body :deep(h3) { font-size: 1.25em; margin: 0.5em 0 0.3em; }
.markdown-body :deep(h4) { font-size: 1.1em; margin: 0.5em 0 0.3em; }
.markdown-body :deep(code) {
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.9em;
}
.markdown-body :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.markdown-body :deep(blockquote) {
  border-left: 4px solid var(--el-color-primary);
  padding: 8px 16px;
  margin: 12px 0;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  border-radius: 0 6px 6px 0;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 8px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}
.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}
.markdown-body :deep(li) {
  margin: 4px 0;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--el-border-color);
  margin: 16px 0;
}
.markdown-body :deep(a) {
  color: var(--el-color-primary);
  text-decoration: none;
}
.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 统计摘要 */
.stats-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}
</style>
