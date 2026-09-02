<template>
  <div class="cloud-panel" v-loading="loading">
    <section class="cloud-section">
      <div class="section-head">
        <div>
          <h3>游客权限设置</h3>
          <p>控制游客账号能否登录，以及登录后可使用的软协课表能力。真实会员不受此设置影响。</p>
        </div>
        <el-tag v-if="!canEdit" type="info" effect="plain">仅超级管理员可修改</el-tag>
      </div>

      <el-radio-group v-model="selectedLevel" class="level-grid" :disabled="!canEdit || savingLevel">
        <el-radio-button v-for="item in levels" :key="item.value" :value="item.value" class="level-option">
          <span class="level-number">{{ item.value }}</span>
          <span class="level-copy"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span>
          <span class="level-selected" aria-hidden="true">✓</span>
        </el-radio-button>
      </el-radio-group>

      <div class="level-actions">
        <span>当前生效：{{ currentLevelLabel }}</span>
        <el-button v-if="canEdit" type="primary" :loading="savingLevel"
                   :disabled="selectedLevel === savedLevel" @click="saveLevel">
          保存权限等级
        </el-button>
      </div>
    </section>

    <section class="cloud-section">
      <div class="section-head">
        <div>
          <h3>课表公告</h3>
          <p>维护 App 内公告列表；已发布公告按更新时间倒序展示，用户进入后首先看到最新一条。</p>
        </div>
        <el-button v-if="canEdit" type="primary" @click="openCreate">新建公告</el-button>
      </div>

      <el-table :data="announcements" row-key="id" stripe empty-text="暂无公告">
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'" effect="plain">
              {{ row.published ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">{{ canEdit ? '编辑' : '查看' }}</el-button>
            <el-button v-if="canEdit" link type="danger" @click="removeAnnouncement(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogOpen" :title="dialogTitle" width="620px" append-to-body destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="公告标题" required>
          <el-input v-model="form.title" maxlength="120" show-word-limit :readonly="!canEdit" />
        </el-form-item>
        <el-form-item label="公告内容" required>
          <el-input v-model="form.content" type="textarea" :rows="10" maxlength="10000"
                    show-word-limit resize="vertical" :readonly="!canEdit" />
        </el-form-item>
        <el-form-item v-if="canEdit" label="发布状态">
          <el-switch v-model="form.published" active-text="已发布" inactive-text="草稿" />
        </el-form-item>
        <el-form-item v-else label="发布状态">
          <el-tag :type="form.published ? 'success' : 'info'" effect="plain">
            {{ form.published ? '已发布' : '草稿' }}
          </el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">{{ canEdit ? '取消' : '关闭' }}</el-button>
        <el-button v-if="canEdit" type="primary" :loading="savingAnnouncement" @click="saveAnnouncement">
          保存公告
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createScheduleAnnouncement,
  deleteScheduleAnnouncement,
  getScheduleCloud,
  updateGuestAccessLevel,
  updateScheduleAnnouncement,
} from '../api'

const props = defineProps({ canEdit: { type: Boolean, default: false } })
const canEdit = computed(() => props.canEdit)
const loading = ref(false)
const savingLevel = ref(false)
const savedLevel = ref(0)
const selectedLevel = ref(0)
const announcements = ref([])
const levels = [
  { value: 0, title: '完全关闭', description: '游客账号无法登录 App' },
  { value: 1, title: '基础能力', description: '游客登录后使用 Web 本地课表' },
  { value: 2, title: '完整能力', description: '开放完整 App 能力，最多绑定一个教务账号' },
]
const currentLevelLabel = computed(() => levels.find(item => item.value === savedLevel.value)?.title || '-')

const dialogOpen = ref(false)
const editingId = ref(null)
const savingAnnouncement = ref(false)
const form = reactive({ title: '', content: '', published: true })
const dialogTitle = computed(() => editingId.value ? (canEdit.value ? '编辑公告' : '查看公告') : '新建公告')

onMounted(loadCloud)

async function loadCloud() {
  loading.value = true
  try {
    const result = await getScheduleCloud()
    const level = Number(result.data?.guestAccessLevel ?? 0)
    savedLevel.value = level
    selectedLevel.value = level
    announcements.value = result.data?.announcements || []
  } finally {
    loading.value = false
  }
}

async function saveLevel() {
  if (!canEdit.value || selectedLevel.value === savedLevel.value) return
  const target = levels.find(item => item.value === selectedLevel.value)
  try {
    await ElMessageBox.confirm(
      `确认把游客权限调整为「${target?.title || selectedLevel.value}」？App 将在下次同步云控时执行。`,
      '修改游客权限',
      { type: selectedLevel.value < savedLevel.value ? 'warning' : 'info', confirmButtonText: '确认修改' },
    )
  } catch {
    selectedLevel.value = savedLevel.value
    return
  }
  savingLevel.value = true
  try {
    await updateGuestAccessLevel(selectedLevel.value)
    savedLevel.value = selectedLevel.value
    ElMessage.success('游客权限等级已更新')
  } catch (e) {
    selectedLevel.value = savedLevel.value
  } finally {
    savingLevel.value = false
  }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, { title: '', content: '', published: true })
}

function openCreate() {
  resetForm()
  dialogOpen.value = true
}

function openView(row) {
  editingId.value = row.id
  Object.assign(form, { title: row.title || '', content: row.content || '', published: row.published !== false })
  dialogOpen.value = true
}

async function saveAnnouncement() {
  const payload = { title: form.title.trim(), content: form.content.trim(), published: form.published }
  if (!payload.title) return ElMessage.warning('请填写公告标题')
  if (!payload.content) return ElMessage.warning('请填写公告内容')
  savingAnnouncement.value = true
  try {
    if (editingId.value) await updateScheduleAnnouncement(editingId.value, payload)
    else await createScheduleAnnouncement(payload)
    dialogOpen.value = false
    ElMessage.success('公告已保存')
    await loadCloud()
  } finally {
    savingAnnouncement.value = false
  }
}

async function removeAnnouncement(row) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？删除后不可恢复。`, '删除公告', {
      type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger',
    })
  } catch { return }
  await deleteScheduleAnnouncement(row.id)
  ElMessage.success('公告已删除')
  await loadCloud()
}

const formatDate = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
</script>

<style scoped>
.cloud-panel { display: grid; gap: 18px; padding: 10px 0 2px; min-height: 280px; }
.cloud-section { border: 1px solid var(--zen-border-light); border-radius: 14px; padding: 20px; background: var(--zen-card); }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.section-head h3 { margin: 0 0 5px; font-size: 17px; }
.section-head p { margin: 0; color: var(--zen-text-muted); font-size: 12px; line-height: 1.6; }
.level-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; width: 100%; }
:deep(.level-option) { width: 100%; }
:deep(.level-option .el-radio-button__inner) {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 84px;
  padding: 14px 48px 14px 14px;
  color: var(--zen-text) !important;
  background: var(--zen-card) !important;
  border: 1px solid var(--zen-border) !important;
  border-radius: 12px !important;
  box-shadow: none !important;
  text-align: left;
  white-space: normal;
  transition: border-color .18s ease, background-color .18s ease, box-shadow .18s ease, transform .18s ease;
}
:deep(.level-option:not(.is-disabled) .el-radio-button__inner:hover) {
  background: var(--el-color-primary-light-9) !important;
  border-color: var(--el-color-primary-light-5) !important;
  transform: translateY(-1px);
}
:deep(.level-option.is-active .el-radio-button__inner) {
  color: var(--zen-text) !important;
  background: var(--gradient-soft) !important;
  border-color: var(--zen-primary) !important;
  box-shadow: 0 0 0 1px rgba(59, 130, 246, .12), 0 6px 18px rgba(59, 130, 246, .08) !important;
}
.level-selected {
  position: absolute;
  top: 12px;
  right: 12px;
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  color: white;
  background: var(--zen-primary);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  opacity: 0;
  transform: scale(.72);
  transition: opacity .18s ease, transform .18s ease;
}
:deep(.level-option.is-active) .level-selected { opacity: 1; transform: scale(1); }
.level-number { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid var(--zen-border-light); border-radius: 10px; color: var(--zen-text-secondary); background: var(--zen-bg); font-size: 18px; font-weight: 700; flex: 0 0 auto; transition: color .18s ease, background .18s ease, border-color .18s ease, box-shadow .18s ease; }
:deep(.level-option.is-active .level-number) { color: white; background: var(--gradient-btn); border-color: transparent; box-shadow: 0 4px 10px rgba(59, 130, 246, .18); }
.level-copy { display: grid; gap: 5px; }
.level-copy strong { color: var(--zen-text); font-size: 14px; }
.level-copy small { color: var(--zen-text-muted); font-size: 11px; line-height: 1.45; }
:deep(.level-option.is-active .level-copy strong) { color: var(--zen-primary); }
:deep(.level-option.is-active .level-copy small) { color: var(--zen-text-secondary); }
.level-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; color: var(--zen-text-muted); font-size: 12px; }
@media (max-width: 800px) { .level-grid { grid-template-columns: 1fr; } .section-head { flex-direction: column; } }
</style>
