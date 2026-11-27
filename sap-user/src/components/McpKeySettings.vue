<template>
  <section class="card mcp-key-card" aria-labelledby="mcp-key-title">
    <div class="mcp-key-heading">
      <div>
        <h3 id="mcp-key-title">MCP 密钥管理</h3>
        <p>让你的 Agent 查询已授权的教务数据。</p>
      </div>
      <span v-if="settings" class="mcp-key-count">{{ settings.keys.length }} / {{ settings.limit }} 把</span>
    </div>

    <p v-if="loading" role="status">正在加载密钥…</p>
    <div v-if="error" class="mcp-key-error" role="alert">
      <p class="error-text">{{ error }}</p>
      <button v-if="!settings" class="btn btn--outline" type="button" @click="load">重试</button>
    </div>

    <template v-if="settings">
      <div class="mcp-endpoint">
        <label class="form-label" for="mcp-endpoint">MCP 地址</label>
        <div class="mcp-copy-row">
          <input id="mcp-endpoint" class="input" :value="endpoint" readonly />
          <button class="btn btn--outline" type="button" @click="copy(endpoint, '地址')">复制地址</button>
        </div>
      </div>

      <div v-if="newKey" class="mcp-new-key" aria-labelledby="mcp-new-key-title">
        <h4 id="mcp-new-key-title">密钥已创建</h4>
        <p>完整密钥只在这里显示一次。复制到 Agent 客户端的 Bearer 密钥设置后，再关闭此提示。</p>
        <label class="form-label" for="mcp-secret">完整密钥</label>
        <div class="mcp-copy-row">
          <input id="mcp-secret" class="input" :value="newKey.secret" readonly />
          <button class="btn btn--primary" type="button" @click="copy(newKey.secret, '密钥')">复制密钥</button>
          <button class="btn btn--outline" type="button" @click="newKey = null">已保存</button>
        </div>
      </div>
      <p v-if="notice" role="status" class="mcp-key-notice">{{ notice }}</p>

      <div v-if="settings.keys.length" class="mcp-key-list">
        <div v-for="key in settings.keys" :key="key.id" class="mcp-key-item">
          <div>
            <strong>{{ key.name }}</strong>
            <p><code>{{ key.prefix }}…</code> · {{ bindingLabel(key.credentialId) }}</p>
            <p><time :datetime="key.createdAt">{{ formatDate(key.createdAt) }}</time> 创建</p>
            <div class="mcp-key-scopes">
              <span v-for="scope in key.scopes" :key="scope" class="badge badge--primary">{{ scopeLabels[scope] }}</span>
            </div>
          </div>
          <button class="btn btn--danger" type="button" :disabled="deletingId !== null" @click="destroy(key)">
            {{ deletingId === key.id ? '销毁中…' : '销毁' }}
          </button>
        </div>
      </div>
      <p v-else class="mcp-key-empty">还没有密钥。</p>

      <form v-if="settings.canCreate && settings.bindings.length && settings.keys.length < settings.limit"
            class="mcp-key-form" @submit.prevent="create">
        <div>
          <label for="mcp-key-name" class="form-label">密钥名称</label>
          <input id="mcp-key-name" v-model.trim="name" class="input" maxlength="40" placeholder="例如 日程助手" required />
        </div>
        <div>
          <label for="mcp-key-account" class="form-label">授权的教务账号</label>
          <select id="mcp-key-account" v-model="credentialId" class="select" required>
            <option value="" disabled>请选择已绑定的学号</option>
            <option v-for="binding in settings.bindings" :key="binding.id" :value="binding.id">
              {{ bindingLabel(binding.id) }}
            </option>
          </select>
        </div>
        <fieldset class="mcp-scope-field">
          <legend class="form-label">允许查询</legend>
          <div class="mcp-scope-options">
            <label v-for="(label, scope) in scopeLabels" :key="scope">
              <input v-model="scopes" type="checkbox" :value="scope" />
              {{ label }}
            </label>
          </div>
        </fieldset>
        <button class="btn btn--primary" type="submit" :disabled="busy || !!newKey || !scopes.length">
          {{ busy ? '正在创建…' : '创建密钥' }}
        </button>
      </form>
      <p v-else-if="!settings.canCreate" class="mcp-key-empty">当前账号没有个人教务功能权限。</p>
      <p v-else-if="!settings.bindings.length" class="mcp-key-empty">请先在 SAP App 绑定教务账号，再刷新此页。</p>
      <p v-else class="mcp-key-empty">已达到密钥上限。销毁一把后可继续创建。</p>
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '@/utils/request'

const scopeLabels = {
  'schedule:read': '课表',
  'grades:read': '成绩',
  'evaluations:read': '评教状态',
  'exams:read': '考试安排',
}

const endpoint = new URL('/mcp', window.location.origin).href
const dateFormatter = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
const settings = ref(null)
const name = ref('')
const credentialId = ref('')
const scopes = ref([])
const newKey = ref(null)
const notice = ref('')
const error = ref('')
const loading = ref(true)
const busy = ref(false)
const deletingId = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await request.get('/api/mcp-keys')
    settings.value = data
    if (data.bindings.length === 1) credentialId.value = data.bindings[0].id
  } catch (e) {
    error.value = e.message || '密钥加载失败'
  } finally {
    loading.value = false
  }
}

function bindingLabel(id) {
  const binding = settings.value.bindings.find(item => item.id === id)
  if (!binding) return '教务绑定已失效'
  return binding.remark ? `${binding.remark}（${binding.account}）` : binding.account
}

function formatDate(value) {
  return dateFormatter.format(new Date(value))
}

async function create() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const { data } = await request.post('/api/mcp-keys', {
      name: name.value, credentialId: credentialId.value, scopes: scopes.value,
    })
    settings.value.keys.unshift(data.key)
    newKey.value = { id: data.key.id, secret: data.secret }
    name.value = ''
    scopes.value = []
  } catch (e) {
    error.value = e.message || '密钥创建失败'
  } finally {
    busy.value = false
  }
}

async function destroy(key) {
  if (!window.confirm(`销毁「${key.name}」后，使用这把密钥的 Agent 会失去访问权限。确定销毁吗？`)) return
  deletingId.value = key.id
  error.value = ''
  notice.value = ''
  try {
    await request.delete(`/api/mcp-keys/${key.id}`)
    settings.value.keys = settings.value.keys.filter(item => item.id !== key.id)
    if (newKey.value?.id === key.id) newKey.value = null
    notice.value = '密钥已销毁'
  } catch (e) {
    error.value = e.message || '密钥销毁失败'
  } finally {
    deletingId.value = null
  }
}

async function copy(value, label) {
  try {
    await navigator.clipboard.writeText(value)
    notice.value = `${label}已复制`
  } catch {
    notice.value = `无法自动复制，请手动选中${label}复制`
  }
}
</script>

<style scoped>
.mcp-key-card { max-width: 760px; margin: 20px auto 0; padding: clamp(16px, 4vw, 32px); }
.mcp-key-heading, .mcp-key-item { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.mcp-key-heading h3, .mcp-new-key h4 { margin: 0 0 4px; }
.mcp-key-heading p, .mcp-key-item p, .mcp-new-key p, .mcp-key-empty, .mcp-key-count, .mcp-key-notice {
  color: var(--ink-600); font-size: 0.875rem;
}
.mcp-key-count { white-space: nowrap; }
.mcp-endpoint, .mcp-new-key, .mcp-key-list, .mcp-key-empty, .mcp-key-form { margin-top: 18px; }
.mcp-new-key { padding: 16px; border: 1px solid var(--border-blue); border-radius: 12px; }
.mcp-new-key .form-label { margin-top: 12px; }
.mcp-copy-row, .mcp-key-scopes, .mcp-scope-options { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.mcp-copy-row .input { flex: 1 1 240px; min-width: 0; }
.mcp-key-item { padding: 12px 0; border-bottom: 1px solid var(--border-blue); }
.mcp-key-item strong { overflow-wrap: anywhere; }
.mcp-key-item p { margin: 4px 0; }
.mcp-key-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; }
.mcp-scope-field { grid-column: 1 / -1; margin: 0; padding: 0; border: 0; }
.mcp-scope-options label { display: inline-flex; align-items: center; gap: 6px; padding: 4px 8px 4px 0; }
.mcp-key-form > .btn { justify-self: start; }
.mcp-key-notice, .mcp-key-error { margin-top: 8px; }
@media (max-width: 520px) { .mcp-key-form { grid-template-columns: minmax(0, 1fr); } }
</style>
