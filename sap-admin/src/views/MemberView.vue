<template>
  <div class="member-page zen-fade-in">
    <div class="page-header">
      <h2>成员管理</h2>
      <p>社团成员换届档案</p>
    </div>

    <el-tabs v-model="activeTab" class="member-tabs">
      <!-- Tab 1: 换届档案 -->
      <el-tab-pane label="换届档案" name="term">
        <div class="zen-card">
          <div class="filter-bar">
            <el-select v-model="currentGrade" placeholder="选择年级" @change="onGradeChange" style="width: 140px">
              <el-option v-for="g in grades" :key="g" :label="g + '届'" :value="g" />
            </el-select>
            <el-button type="primary" @click="showAddDialog = true">添加成员</el-button>
            <el-button @click="openChangeover">执行换届</el-button>
          </div>

          <div class="term-grid" v-if="termList.length">
            <div v-for="item in termList" :key="item.id" class="term-card">
              <div class="term-card-left">
                <div class="term-avatar">{{ item.userName ? item.userName.charAt(0) : '?' }}</div>
                <div class="term-info">
                  <div class="term-name">{{ item.userName }}</div>
                  <div class="term-sid">{{ item.studentId }}</div>
                </div>
              </div>
              <div class="term-card-right">
                <el-tag :type="getTagType(item.positionName)" effect="plain" size="small">
                  {{ item.positionName }}
                </el-tag>
                <el-button type="danger" text size="small" @click="handleDelete(item.id)">移除</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无换届记录" />

          <div class="term-pagination" v-if="termTotal > 0">
            <el-pagination
              v-model:current-page="termPage"
              :page-size="termPageSize"
              :page-sizes="[40, 100, 200, 500]"
              :total="termTotal"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadTerms"
              @size-change="onTermSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 优秀成员 -->
      <el-tab-pane label="优秀成员" name="outstanding">
        <div class="zen-card">
          <div class="filter-bar">
            <el-button type="primary" @click="openOmAdd">添加优秀成员</el-button>
            <el-tag effect="plain" style="margin-left: auto">共 {{ outstandingList.length }} 人</el-tag>
          </div>

          <div class="outstanding-grid" v-if="outstandingList.length">
            <div v-for="m in outstandingList" :key="m.id" class="om-card">
              <div class="om-card-header">
                <div class="om-avatar">{{ m.name ? m.name.charAt(0) : '?' }}</div>
                <div class="om-header-info">
                  <div class="om-name">{{ m.name }}</div>
                  <div class="om-tags">
                    <el-tag size="small" :type="m.gender === '女' ? 'danger' : 'info'" effect="plain" round>{{ m.gender || '未知' }}</el-tag>
                    <el-tag size="small" type="success" effect="plain" round>{{ m.grade }}届</el-tag>
                  </div>
                </div>
              </div>

              <div class="om-card-body">
                <div class="om-field" v-if="m.major">
                  <span class="om-field-icon">🎓</span>
                  <span class="om-field-text">{{ m.major }}</span>
                </div>
                <div class="om-field" v-if="m.destination">
                  <span class="om-field-icon">🚀</span>
                  <span class="om-field-text">
                    <el-tag size="small" type="warning" effect="dark" round>{{ m.destination }}</el-tag>
                    <span v-if="m.destinationDetail" style="margin-left: 6px">{{ m.destinationDetail }}</span>
                  </span>
                </div>
                <el-tooltip :content="m.bio || '暂无简介'" placement="top" :disabled="!m.bio" :show-after="300">
                  <div class="om-bio">{{ m.bio || '暂无简介' }}</div>
                </el-tooltip>
              </div>

              <div class="om-card-footer">
                <el-button text size="small" @click="openOmEdit(m)">编辑</el-button>
                <el-button text type="danger" size="small" @click="handleOmDelete(m.id)">删除</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无优秀成员" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="showAddDialog" title="添加换届记录" width="460px" append-to-body>
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="用户">
          <el-select-v2 v-model="addForm.userId" :options="allUserOptions" filterable
            placeholder="输入学号或姓名搜索" style="width: 100%">
            <template #default="{ item }">
              <div class="user-option">
                <span class="user-name">{{ item.name }}</span>
                <span class="user-sid">{{ item.studentId }}</span>
              </div>
            </template>
          </el-select-v2>
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model="addForm.grade" disabled />
        </el-form-item>
        <el-form-item label="身份">
          <el-select v-model="addForm.positionId" placeholder="选择身份" style="width: 100%">
            <el-option v-for="p in positions" :key="p.id" :label="p.positionName" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确 认</el-button>
      </template>
    </el-dialog>

    <!-- 换届选人弹窗 -->
    <el-dialog v-model="showChangeover" title="执行换届" width="640px" append-to-body class="changeover-dialog">
      <div class="changeover-header">
        <div class="grade-transition">
          <div class="grade-box current">{{ currentGradeValue }} 届</div>
          <span class="grade-arrow">→</span>
          <div class="grade-box next">{{ nextGradeValue }} 届</div>
        </div>
        <p class="changeover-hint">
          请为新一届选择各身份人员，<el-tag type="danger" size="small" effect="dark" round>必选</el-tag> 标记的身份必须指定人员。
        </p>
      </div>
      <div class="changeover-form">
        <div v-for="pos in changeoverPositions" :key="pos.id" class="pos-row">
          <div class="pos-label">
            <div class="pos-title">
              <span class="pos-name">{{ pos.positionName }}</span>
              <el-tag v-if="pos.isSystem === 1" type="danger" size="small" effect="dark" round>必选</el-tag>
            </div>
            <span class="pos-limit"><span class="pos-limit-num">{{ pos.maxCount }}</span> 人上限</span>
          </div>
          <el-select-v2 v-model="changeoverForm[pos.id]" :options="optionsForPositions[pos.id]"
            :multiple="pos.maxCount > 1" filterable
            :placeholder="'输入学号搜索 ' + pos.positionName" style="width: 100%"
            @change="(val) => onSelectChange(pos, val)">
            <template #default="{ item }">
              <div class="user-option">
                <span class="user-name">{{ item.name }}</span>
                <span class="user-sid">{{ item.studentId }}</span>
              </div>
            </template>
          </el-select-v2>
        </div>
      </div>
      <template #footer>
        <div style="display: flex; justify-content: space-between; width: 100%;">
          <el-button @click="handleClearChangeover">清空条件</el-button>
          <div>
            <el-button @click="showChangeover = false">取消</el-button>
            <el-button type="primary" @click="handleChangeover">确认换届</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 换届确认警告弹窗 -->
    <el-dialog v-model="showConfirmDialog" title="⚠️ 极度危险操作确认" width="500px" append-to-body center :close-on-click-modal="false" :show-close="false">
      <div style="background-color: #fef0f0; color: #f56c6c; padding: 15px; border-radius: 8px; border: 1px solid #fde2e2; font-size: 14px; text-align: left;">
        <p style="font-weight: bold; margin-bottom: 10px;">您即将执行换届操作！换届后系统会<strong>立刻结算当前届的数据，且该操作不可逆转，出错将无法修改！</strong></p>
        <p style="margin-bottom: 8px; color: #333;">请仔细核对以下人员任命：</p>
        <div style="background: white; padding: 10px; border-radius: 4px; color: #333;">
          <div v-for="item in confirmList" :key="item.posName" style="margin-bottom: 5px; display: flex;">
            <span style="font-weight: 600; min-width: 90px; text-align: right; margin-right: 10px; flex-shrink: 0;">{{ item.posName }}：</span>
            <span style="color: #3B82F6;">{{ item.names }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="cancelChangeoverConfirm">我再想想</el-button>
          <el-button :loading="changeoverExecuting" :disabled="countdown > 0 || changeoverExecuting" @click="executeChangeover" :style="countdown > 0 ? 'background: #f5f7fa; color: #a8abb2; border-color: #e4e7ed;' : 'background: #f56c6c; color: white; border-color: #f56c6c;'">
            确认执行换届 {{ countdown > 0 ? `(${countdown}s)` : '' }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 优秀成员弹窗 -->
    <el-dialog v-model="showOmDialog" :title="omIsEdit ? '编辑优秀成员' : '添加优秀成员'" width="520px" append-to-body>
      <el-form :model="omForm" label-width="80px">
        <el-form-item label="姓名" required>
          <el-input v-model="omForm.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" required>
          <el-radio-group v-model="omForm.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年级" required>
          <el-input-number v-model="omForm.gradeNum" :min="2018" :max="2040" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="omForm.major" placeholder="请输入专业" />
        </el-form-item>
        <el-form-item label="去向" required>
          <el-select v-model="omForm.destination" placeholder="请选择去向" style="width: 100%">
            <el-option v-for="d in destinations" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="去向内容" required>
          <el-input v-model="omForm.destinationDetail" placeholder="具体学校或公司" />
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="omForm.bio" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="请输入个人简介（最多100字）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showOmDialog = false">取消</el-button>
        <el-button type="primary" @click="handleOmSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, computed } from 'vue'
import {
  getTermList, getGrades, addTerm, deleteTerm, doChangeover,
  getUserList, getPositions, getSettingValue, getMemberUsers,
  getAllOutstandingMembers, addOutstandingMember, updateOutstandingMember, deleteOutstandingMember
} from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('term')
const currentGrade = ref('')
const currentGradeValue = ref('')
const nextGradeValue = ref('')
const grades = ref([])
const termList = ref([])
const termPage = ref(1)
const termPageSize = ref(200)
const termTotal = ref(0)
const userList = ref([])
const memberList = ref([])
const positions = ref([])
const showAddDialog = ref(false)
const showChangeover = ref(false)
const changeoverForm = reactive({})
const addForm = reactive({ userId: null, grade: '', positionId: null })

const showConfirmDialog = ref(false)
const changeoverExecuting = ref(false)
const countdown = ref(10)
const confirmList = ref([])
let countdownTimer = null

// 优秀成员
const outstandingList = ref([])
const showOmDialog = ref(false)
const omIsEdit = ref(false)
const omEditId = ref(null)
const omForm = reactive({
  name: '', gender: '男', gradeNum: 2026, major: '',
  destination: '', destinationDetail: '', bio: ''
})
const destinations = ['考研', '保研', '就业', '出国', '创业', '其他']

const allUserOptions = computed(() => {
  return userList.value.map(u => ({
    value: u.id,
    label: u.name + ' (' + u.studentId + ')',
    name: u.name,
    studentId: u.studentId
  }))
})

const optionsForPositions = computed(() => {
  const result = {}
  for (const pos of changeoverPositions.value) {
    const taken = getSelectedUserIds(pos.id)
    result[pos.id] = memberList.value.map(u => ({
      value: u.id,
      label: u.name + ' (' + u.studentId + ')',
      name: u.name,
      studentId: u.studentId,
      disabled: taken.has(u.id)
    }))
  }
  return result
})

const getSelectedUserIds = (excludePosId) => {
  const ids = new Set()
  for (const pos of changeoverPositions.value) {
    if (pos.id === excludePosId) continue
    const val = changeoverForm[pos.id]
    if (val === null || val === undefined) continue
    if (Array.isArray(val)) val.forEach(id => ids.add(id))
    else ids.add(val)
  }
  return ids
}

const changeoverPositions = computed(() => positions.value.filter(p => p.positionName !== '成员'))

const getTagType = (name) => {
  if (name === '会长') return 'danger'
  if (name === '团支书' || name === '副会长') return 'warning'
  if (name && name.includes('部长')) return 'primary'
  return 'info'
}

const loadGrades = async () => {
  try {
    const res = await getGrades()
    grades.value = res.data || []
    if (grades.value.length > 0 && !currentGrade.value) {
      currentGrade.value = grades.value[0]
      loadTerms()
    }
  } catch (e) {}
}

const loadTerms = async () => {
  if (!currentGrade.value) return
  try {
    const res = await getTermList({ grade: currentGrade.value, current: termPage.value, size: termPageSize.value })
    termList.value = res.data?.records || []
    termTotal.value = Number(res.data?.total || 0)
  } catch (e) {}
}

const onTermSizeChange = (newSize) => {
  termPageSize.value = newSize
  termPage.value = 1
  loadTerms()
}

const onGradeChange = () => {
  termPage.value = 1
  loadTerms()
}

const loadOutstanding = async () => {
  try {
    const res = await getAllOutstandingMembers()
    outstandingList.value = res.data || []
  } catch (e) {}
}

const handleAdd = async () => {
  try {
    await addTerm(addForm)
    ElMessage.success('添加成功')
    showAddDialog.value = false
    loadTerms()
    loadGrades()
  } catch (e) {}
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确认移除此记录？', '提示')
  } catch (e) {
    return // 用户取消，静默
  }
  try {
    await deleteTerm(id)
    ElMessage.success('已移除')
    // 若删的是当前页最后一条且不在首页，回退一页避免停在空白页
    if (termList.value.length === 1 && termPage.value > 1) {
      termPage.value--
    }
    loadTerms()
  } catch (e) {}
}

const onSelectChange = (pos, val) => {
  if (pos.maxCount === 1) return
  if (Array.isArray(val) && val.length > pos.maxCount) {
    ElMessage.warning(`${pos.positionName} 最多选 ${pos.maxCount} 人`)
    changeoverForm[pos.id] = val.slice(0, pos.maxCount)
  }
}

const openChangeover = async () => {
  try {
    const res = await getSettingValue('current_grade')
    if (res.data) {
      currentGradeValue.value = res.data
      nextGradeValue.value = String(parseInt(currentGradeValue.value) + 1)
    } else {
      ElMessage.warning('系统当前未配置年级参数，请先在【系统管理】->【系统配置】中设置当前年级！')
      return
    }
  } catch (e) {
    ElMessage.error('无法获取年级配置，请先在【系统管理】中完成配置')
    return 
  }
  for (const pos of changeoverPositions.value) {
    if (changeoverForm[pos.id] === undefined) {
      changeoverForm[pos.id] = pos.maxCount > 1 ? [] : null
    }
  }
  showChangeover.value = true
}

const handleClearChangeover = async () => {
  try {
    await ElMessageBox.confirm('确认清空当前所有已选人员？', '清空确认', { type: 'warning' })
    for (const pos of changeoverPositions.value) {
      changeoverForm[pos.id] = pos.maxCount > 1 ? [] : null
    }
  } catch(e) {}
}

const handleChangeover = async () => {
  for (const pos of changeoverPositions.value) {
    if (pos.isSystem === 1) {
      const val = changeoverForm[pos.id]
      const empty = val === null || val === undefined || (Array.isArray(val) && val.length === 0)
      if (empty) { ElMessage.warning(`请选择${pos.positionName}`); return }
    }
  }

  const list = []
  for (const pos of changeoverPositions.value) {
    const val = changeoverForm[pos.id]
    if (val === null || val === undefined || (Array.isArray(val) && val.length === 0)) continue
    
    const sidArray = Array.isArray(val) ? val : [val]
    const names = sidArray.map(id => {
      const user = memberList.value.find(u => u.id === id)
      return user ? user.name : '未知'
    }).join('、')
    
    list.push({ posName: pos.positionName, names, id: pos.id, userIds: sidArray })
  }
  
  if (list.length === 0) {
    ElMessage.warning('换届人员不能为空')
    return
  }

  confirmList.value = list
  countdown.value = 10
  showConfirmDialog.value = true
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
    }
  }, 1000)
}

const cancelChangeoverConfirm = () => {
  showConfirmDialog.value = false
  if (countdownTimer) clearInterval(countdownTimer)
}

const executeChangeover = async () => {
  const assignments = confirmList.value.map(item => ({
    positionId: item.id,
    userIds: item.userIds
  }))

  changeoverExecuting.value = true
  try {
    await doChangeover(assignments)
    ElMessage.success('换届成功！')
    showConfirmDialog.value = false
    showChangeover.value = false
    for (const pos of changeoverPositions.value) {
      changeoverForm[pos.id] = pos.maxCount > 1 ? [] : null
    }
    loadGrades()
    // 自动加载新当选的一届信息展示
    if (nextGradeValue.value) {
      currentGrade.value = nextGradeValue.value
    }
    loadTerms()
  } catch(e) {
  } finally {
    changeoverExecuting.value = false
  }
}

// 优秀成员
const openOmAdd = () => {
  omIsEdit.value = false
  omEditId.value = null
  omForm.name = ''; omForm.gender = '男'; omForm.gradeNum = 2026
  omForm.major = ''; omForm.destination = ''; omForm.destinationDetail = ''; omForm.bio = ''
  showOmDialog.value = true
}

const openOmEdit = (m) => {
  omIsEdit.value = true
  omEditId.value = m.id
  omForm.name = m.name; omForm.gender = m.gender || '男'; omForm.gradeNum = parseInt(m.grade) || 2026
  omForm.major = m.major || ''; omForm.destination = m.destination || ''
  omForm.destinationDetail = m.destinationDetail || ''; omForm.bio = m.bio || ''
  showOmDialog.value = true
}

const handleOmSubmit = async () => {
  if (!omForm.name) { ElMessage.warning('请输入姓名'); return }
  if (!omForm.destination) { ElMessage.warning('请选择去向'); return }
  if (!omForm.destinationDetail) { ElMessage.warning('请输入去向内容'); return }
  const data = {
    name: omForm.name, gender: omForm.gender, grade: String(omForm.gradeNum),
    major: omForm.major, destination: omForm.destination,
    destinationDetail: omForm.destinationDetail, bio: omForm.bio
  }
  try {
    if (omIsEdit.value) {
      await updateOutstandingMember(omEditId.value, data)
    } else {
      await addOutstandingMember(data)
    }
    ElMessage.success(omIsEdit.value ? '修改成功' : '添加成功')
    showOmDialog.value = false
    loadOutstanding()
  } catch (e) {}
}

const handleOmDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除此优秀成员？', '提示')
    await deleteOutstandingMember(id)
    ElMessage.success('已删除')
    loadOutstanding()
  } catch (e) {}
}

onMounted(async () => {
  loadGrades()
  loadOutstanding()
  try {
    const uRes = await getUserList({ current: 1, size: 1000 })
    userList.value = uRes.data?.records || []
    try {
      const mRes = await getMemberUsers()
      memberList.value = mRes.data || []
    } catch (e2) { memberList.value = userList.value }
    const pRes = await getPositions()
    positions.value = pRes.data || []
  } catch (e) {}
})

onBeforeUnmount(() => {
  // 清理换届倒计时定时器，避免路由离开后定时器泄漏
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>


