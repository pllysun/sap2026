<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">个人信息</h1>
    </div>

    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <template v-else-if="userStore.user">
      <div class="card anim-in" style="max-width: 520px; margin: 0 auto;">

        <!-- Avatar -->
        <div class="text-center mb-4">
          <div class="avatar-upload" @click="triggerUpload">
            <img :src="form.avatar || '/default-avatar.png'" class="avatar-upload__img" />
            <div class="avatar-upload__overlay">更换</div>
          </div>
          <input type="file" ref="fileInput" accept="image/*" @change="handleAvatarChange" style="display:none;" />
          <div v-if="uploadingAvatar" class="t-caption mt-1">上传中…</div>
        </div>

        <div class="divider"></div>

        <!-- Read-only -->
        <div class="form-group">
          <label class="form-label">学号</label>
          <input :value="userStore.user.studentId" class="input" disabled style="opacity:0.5;" />
        </div>

        <div class="form-group">
          <label class="form-label">姓名</label>
          <input :value="userStore.user.name" class="input" disabled style="opacity:0.5;" />
        </div>

        <div class="divider"></div>

        <!-- Editable -->
        <div class="form-group">
          <label class="form-label">网名</label>
          <input v-model="form.nickname" class="input" placeholder="输入网名" />
        </div>

        <div class="form-group">
          <label class="form-label">性别</label>
          <select v-model="form.gender" class="select">
            <option :value="1">男</option>
            <option :value="0">女</option>
          </select>
        </div>

        <div class="form-group">
          <label class="form-label">QQ号</label>
          <input :value="userStore.user.qq" class="input" disabled style="opacity:0.5;" />
        </div>

        <button class="btn btn--primary btn--full" @click="saveProfile" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>

        <div v-if="saveError" class="error-text text-center mt-2">{{ saveError }}</div>
        <div v-if="saveSuccess" class="success-text text-center mt-2">{{ saveSuccess }}</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const userStore = useUserStore()
const loading = ref(true)
const saving = ref(false)
const saveError = ref('')
const saveSuccess = ref('')
const uploadingAvatar = ref(false)
const fileInput = ref(null)

const form = reactive({ nickname: '', gender: 1, avatar: '' })

onMounted(async () => {
  try {
    await userStore.fetchUserInfo()
    if (userStore.user) { form.nickname = userStore.user.nickname || ''; form.gender = userStore.user.gender ?? 1; form.avatar = userStore.user.avatar || '' }
  } catch {} finally { loading.value = false }
})

function triggerUpload() { fileInput.value?.click() }

async function handleAvatarChange(e) {
  const file = e.target.files[0]; if (!file) return
  uploadingAvatar.value = true; saveError.value = ''; saveSuccess.value = ''
  try {
    const fd = new FormData(); fd.append('file', file)
    const r = await request.post('/api/file/upload', fd)
    form.avatar = r.data.url
  } catch (e) {
    // 不再静默吞掉：把后端真实原因显示出来（如「对象存储(COS)尚未配置…」），便于定位
    saveError.value = e.message || '头像上传失败，请稍后重试'
  } finally { uploadingAvatar.value = false }
}

async function saveProfile() {
  saving.value = true; saveError.value = ''; saveSuccess.value = ''
  try {
    await userStore.updateProfile({ nickname: form.nickname, gender: form.gender, avatar: form.avatar })
    await userStore.fetchUserInfo()
    saveSuccess.value = '已保存'
    setTimeout(() => saveSuccess.value = '', 3000)
  } catch (e) { saveError.value = e.message || '保存失败' }
  finally { saving.value = false }
}
</script>
