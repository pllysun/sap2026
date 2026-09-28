<template>
  <section class="calendar-panel" v-loading="loading">
    <div class="calendar-head">
      <div><h3>教学日历维护</h3><p>三种模式共用开学日期。缺失学期在教务同步或班级采集时补全，已有日期不会被采集覆盖。</p></div>
      <el-button type="primary" :disabled="!canEdit" @click="edit()">新增学期</el-button>
    </div>
    <div class="calendar-toolbar"><el-input v-model="search" clearable placeholder="查找学期" style="max-width:300px" /><el-button @click="load">刷新校历</el-button></div>
    <el-table :data="filtered" stripe empty-text="暂无校历，请新增或采集课表" max-height="380">
      <el-table-column prop="term" label="学期" min-width="160" />
      <el-table-column prop="semesterStartDate" label="开学日期（第 1 周周一）" min-width="200" />
      <el-table-column label="来源" width="120"><template #default="{ row }">{{ row.source === 'MANUAL' ? '手动维护' : '学校校历' }}</template></el-table-column>
      <el-table-column label="更新时间" min-width="170"><template #default="{ row }">{{ row.updatedAt?.replace('T', ' ') || '—' }}</template></el-table-column>
      <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }">
        <el-button link type="primary" :disabled="!canEdit" @click="edit(row)">编辑</el-button>
        <el-button link type="danger" :disabled="!canEdit" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="editing ? '编辑校历' : '新增校历'" width="440px" append-to-body align-center
               modal-class="class-modal" class="class-dialog" destroy-on-close>
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="学期" required><el-input v-model="form.term" :disabled="editing" placeholder="2026-2027-1" /></el-form-item>
        <el-form-item label="开学日期（第 1 周周一）" required><el-date-picker v-model="form.semesterStartDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAcademicCalendars, saveAcademicCalendar, deleteAcademicCalendar } from '../api'
defineProps({ canEdit: Boolean })
const emit = defineEmits(['changed'])
const rows = ref([]), loading = ref(false), search = ref(''), visible = ref(false), editing = ref(false), saving = ref(false)
const form = reactive({ term: '', semesterStartDate: '' })
const filtered = computed(() => rows.value.filter(row => row.term.includes(search.value.trim())))
async function load() {
  loading.value = true
  try { rows.value = (await getAcademicCalendars()).data || [] } finally { loading.value = false }
}
function edit(row) {
  editing.value = !!row
  Object.assign(form, { term: row?.term || '', semesterStartDate: row?.semesterStartDate || '' })
  visible.value = true
}
async function save() {
  if (!/^\d{4}-\d{4}-[12]$/.test(form.term) || !form.semesterStartDate) return ElMessage.warning('请填写有效学期及开学日期')
  if (!editing.value && rows.value.some(row => row.term === form.term)) return ElMessage.warning('该学期已存在，请在列表中编辑')
  saving.value = true
  try {
    await saveAcademicCalendar({ ...form }); visible.value = false
    ElMessage.success('校历已保存'); await load(); emit('changed')
  } finally { saving.value = false }
}
async function remove(row) {
  try { await ElMessageBox.confirm(`删除 ${row.term} 的校历？下次教务同步或班级采集时将重新补全。`, '删除校历', { type: 'warning' }) } catch { return }
  await deleteAcademicCalendar(row.term); await load(); emit('changed')
}
// 首次读取及采集完成后的刷新由父页面统一调度，避免重复请求。
defineExpose({ reload: load })
</script>
<style scoped>
.calendar-panel { margin:24px 0; padding:20px; border:1px solid var(--el-border-color-light); border-radius:12px; }
.calendar-head { display:flex; gap:16px; justify-content:space-between; align-items:center; }
h3 { margin:0 0 8px; } p { margin:0; color:var(--el-text-color-secondary); font-size:13px; line-height:1.7; }
.calendar-toolbar { display:flex; gap:12px; margin:16px 0; }
</style>
