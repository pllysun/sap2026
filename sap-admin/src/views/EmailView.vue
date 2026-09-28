<template>
  <div class="email-page zen-fade-in">
    <div class="page-header">
      <div>
        <h2>邮件管理</h2>
        <p>统一管理 QQ SMTP 发件配置与 HTML 邮件模板，支持填充变量预览和测试发送。</p>
      </div>
      <el-tag :type="smtp.configured ? 'success' : 'warning'" effect="plain">
        {{ smtp.configured ? 'SMTP 已配置' : 'SMTP 待配置' }}
      </el-tag>
    </div>

    <section class="email-card">
      <div class="section-title">
        <div class="section-title__icon">✉️</div>
        <div>
          <h3>QQ 邮箱发件配置</h3>
          <p>建议使用 QQ 邮箱的 SMTP 授权码，不要填写 QQ 登录密码。授权码会加密保存，管理端不会回显。</p>
        </div>
      </div>

      <el-form :model="smtp" label-position="top" class="smtp-form">
        <el-form-item label="SMTP 服务器">
          <el-input v-model="smtp.host" placeholder="smtp.qq.com" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="smtp.port" :min="1" :max="65535" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="QQ 邮箱账号">
          <el-input v-model="smtp.username" type="email" placeholder="example@qq.com" />
        </el-form-item>
        <el-form-item label="SMTP 授权码">
          <el-input v-model="smtp.password" type="password" show-password autocomplete="new-password"
                    :placeholder="smtp.passwordSet ? '已配置，留空表示不修改' : '填写 QQ 邮箱生成的授权码'" />
        </el-form-item>
        <el-form-item label="默认发件人名称">
          <el-input v-model="smtp.fromName" placeholder="中南林业科技大学软件协会" />
        </el-form-item>
        <el-form-item label="邮件发送开关" class="switch-item">
          <el-switch v-model="smtp.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="安全连接" class="switch-item">
          <div class="switches">
            <el-switch v-model="smtp.ssl" active-text="SSL" />
            <el-switch v-model="smtp.starttls" active-text="STARTTLS" />
          </div>
        </el-form-item>
      </el-form>
      <div class="action-row">
        <el-button type="primary" :loading="smtpSaving" @click="saveSmtp">保存配置</el-button>
        <el-button :loading="smtpTesting" :disabled="!smtp.configured" @click="testSmtp">测试 SMTP 连通性</el-button>
        <span class="action-hint">QQ 邮箱常用配置：smtp.qq.com / 465 / SSL</span>
      </div>
    </section>

    <section class="email-card templates-card">
      <div class="section-title section-title--between">
        <div class="section-title__left">
          <div class="section-title__icon">🧩</div>
          <div>
            <h3>HTML 邮件模板</h3>
            <p>先选择代码事件，按必需参数编写模板，再绑定使用。停用模板不生成自动邮件；点击编辑可启用。</p>
          </div>
        </div>
        <el-button type="primary" @click="openEditor()">新增模板</el-button>
      </div>

      <el-table :data="templates" v-loading="templatesLoading" stripe empty-text="暂无邮件模板，请先新增">
        <el-table-column prop="templateName" label="模板名称" min-width="160" />
        <el-table-column prop="templateKey" label="标识" min-width="160">
          <template #default="{ row }"><code>{{ row.templateKey }}</code></template>
        </el-table-column>
        <el-table-column label="变量" min-width="190">
          <template #default="{ row }">
            <div v-if="row.variables?.length" class="variable-list">
              <el-tag v-for="name in row.variables" :key="name" size="small" effect="plain">{{ name }}</el-tag>
            </div>
            <span v-else class="muted">无变量</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <div class="template-actions">
              <el-button size="small" type="primary" plain @click="openPreview(row)">预览</el-button>
              <el-button size="small" @click="openEditor(row)">编辑</el-button>
              <el-button size="small" @click="openSend(row)">测试发送</el-button>
              <el-tooltip :content="boundIds.includes(String(row.id)) ? '先在代码事件中解绑，再删除模板' : '删除模板'"><span><el-button size="small" type="danger" plain :disabled="boundIds.includes(String(row.id))" @click="removeTemplate(row)">删除</el-button></span></el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <EmailOperations :templates="templates" @bindings-change="boundIds = $event" @hooks-loaded="bindings = $event" />

    <!-- 模板编辑：HTML 源码与变量提示并列，便于维护复杂邮件布局。 -->
    <el-dialog v-model="editorVisible" :title="editorId ? '编辑邮件模板' : '新增邮件模板'" width="920px" append-to-body modal-class="email-modal" destroy-on-close class="email-dialog">
      <el-form :model="editor" label-position="top" class="editor-form">
        <section class="contract-panel">
          <el-form-item label="模板用途 / 代码事件" required>
            <el-select v-model="editorEvent" :loading="hooksLoading" :disabled="!!editorBoundHook" placeholder="先选择事件，查看需要提供的参数" style="width:100%">
              <el-option v-for="hook in hookDefinitions" :key="hook.eventKey" :value="hook.eventKey" :label="hook.title" />
              <el-option value="custom" label="独立模板（手动测试发送，不绑定业务事件）" />
            </el-select>
          </el-form-item>
          <p v-if="hooksError" class="contract-error">代码参数加载失败，暂不能选择业务事件。<el-button link type="primary" @click="loadHooks">重新加载</el-button></p>
          <template v-if="editorHook">
            <div class="contract-heading"><strong>必需参数 · {{ editorHook.parameters.length }} 个</strong><code>{{ editorHook.eventKey }}</code></div>
            <p class="dialog-tip">以下参数由业务代码定义，必须全部使用，不能缺少或多出。示例仅用于预览，不会发送。</p>
            <div class="contract-parameters">
              <div v-for="parameter in editorHook.parameters" :key="parameter.name" class="contract-parameter">
                <div><strong>{{ parameter.label }}</strong> <code>{{ placeholder(parameter.name) }}</code><p>{{ parameter.description }} · 示例：{{ parameter.example }}</p></div>
                <el-button size="small" :disabled="editorVariables.includes(parameter.name)" @click="insertParameter(parameter)">{{ editorVariables.includes(parameter.name) ? '已使用' : '插入正文' }}</el-button>
              </div>
            </div>
            <p class="dialog-tip">{{ editorHook.guidance }}</p>
            <div class="contract-actions"><el-button size="small" @click="generateStarter">生成基础内容</el-button><span>仅填入主题与基础 HTML，可继续修改样式，不会自动绑定。</span></div>
            <p v-if="editorBoundHook" class="dialog-tip">此模板已绑定该事件，不能切换用途；停用后不再生成新邮件，已入队邮件不受影响。</p>
            <el-alert :type="contractError ? 'warning' : 'success'" :closable="false" :title="contractError || '参数完整，可保存后在代码事件中绑定'" show-icon />
          </template>
          <p v-else-if="editorEvent === 'custom'" class="dialog-tip">独立模板可自由定义占位符，仅用于手动发送。需要自动发送时，请选择对应代码事件并按契约编写。</p>
          <p v-else class="dialog-tip">请选择用途，参数说明会显示在这里。无需从旧模板猜测参数。</p>
        </section>
        <div class="editor-meta">
          <el-form-item label="模板标识" required>
            <el-input v-model="editor.templateKey" :disabled="!!editorId" placeholder="member.welcome" />
          </el-form-item>
          <el-form-item label="模板名称" required>
            <el-input v-model="editor.templateName" placeholder="会员注册欢迎邮件" />
          </el-form-item>
        </div>
        <el-form-item label="邮件主题" required>
          <el-input v-model="editor.subject" placeholder="欢迎加入软件协会，{{name}}" />
        </el-form-item>
        <el-form-item label="HTML 内容" required>
          <el-input v-model="editor.htmlContent" type="textarea" :rows="18" resize="vertical" class="html-editor"
                    placeholder="按上方代码事件提供的参数编写 HTML，点击「插入正文」可添加占位符" />
        </el-form-item>
        <div class="editor-foot">
          <div class="detected-variables">
            <span>检测到的变量：</span>
            <el-tag v-for="name in editorVariables" :key="name" size="small" effect="plain">{{ name }}</el-tag>
            <span v-if="!editorVariables.length" class="muted">暂无</span>
          </div>
          <el-switch v-model="editor.enabled" active-text="启用模板" />
        </div>
        <p class="dialog-tip">{{ editor.enabled ? '启用后：正确绑定事件且全局发送开启时，新业务事件才会生成邮件。' : '当前为停用草稿：可预览、手动测试，但不会生成自动邮件。审核后打开「启用模板」并保存。' }} 历史跳过邮件不会补发。</p>
        <el-form-item label="模板说明">
          <el-input v-model="editor.description" type="textarea" :rows="2" placeholder="给管理员看的用途说明，可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button @click="previewEditor">预览</el-button>
        <el-button type="primary" :loading="editorSaving" :disabled="!editorEvent || !!contractError || hooksLoading" @click="saveEditor">保存模板</el-button>
      </template>
    </el-dialog>

    <!-- 预览使用 sandbox iframe，避免模板中的内容影响管理端页面。 -->
    <el-dialog v-model="previewVisible" :title="previewSubject ? `预览：${previewSubject}` : '邮件预览'" width="850px" append-to-body modal-class="email-modal" class="email-dialog preview-dialog">
      <div class="preview-toolbar">
        <span>渲染视图</span>
        <el-radio-group v-model="previewDevice" size="small">
          <el-radio-button label="desktop">浏览器</el-radio-button>
          <el-radio-button label="mobile">手机邮件</el-radio-button>
        </el-radio-group>
      </div>
      <div class="preview-stage" :class="{ 'preview-stage--mobile': previewDevice === 'mobile' }">
        <iframe :srcdoc="previewHtml" title="HTML 邮件预览" sandbox="" class="preview-frame" />
      </div>
    </el-dialog>

    <el-dialog v-model="sendVisible" title="发送测试邮件" width="560px" append-to-body modal-class="email-modal" destroy-on-close class="email-dialog">
      <p class="dialog-tip">测试邮件会渲染后加入统一队列，不会修改模板。每次发送间隔至少 65 秒，可在发送日志查看结果。</p>
      <el-form label-position="top">
        <el-form-item label="收件邮箱" required>
          <el-input v-model="sendTo" type="email" placeholder="请输入用于接收测试邮件的邮箱" />
        </el-form-item>
        <el-form-item v-for="name in sendVariables" :key="name" :label="`变量：${name}`">
          <el-input v-model="sendValues[name]" :placeholder="`填写 ${name} 的显示内容`" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="sendTest">发送测试邮件</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmailOperations from './EmailOperations.vue'
import request from '../utils/request'
const boundIds = ref([])
const bindings = ref([])
import {
  createEmailTemplate,
  deleteEmailTemplate,
  getEmailConfig,
  getEmailTemplates,
  previewEmailTemplate,
  sendTestEmail,
  testEmailConfig,
  updateEmailConfig,
  updateEmailTemplate,
} from '../api'

const smtp = reactive({ host: 'smtp.qq.com', port: 465, username: '', password: '', fromName: '中南林业科技大学软件协会', ssl: true, starttls: false, enabled: true, passwordSet: false, configured: false })
const smtpSaving = ref(false)
const smtpTesting = ref(false)
const templates = ref([])
const templatesLoading = ref(false)
const editorVisible = ref(false)
const editorSaving = ref(false)
const editorId = ref(null)
const hookDefinitions = ref([])
const hooksLoading = ref(false)
const hooksError = ref(false)
const editorEvent = ref('')
const editor = reactive({ templateKey: '', templateName: '', subject: '', htmlContent: '', description: '', enabled: true })
const previewVisible = ref(false)
const previewDevice = ref('desktop')
const previewHtml = ref('')
const previewSubject = ref('')
const sendVisible = ref(false)
const sending = ref(false)
const sendTo = ref('')
const sendVariables = ref([])
const sendValues = reactive({})

const editorVariables = computed(() => extractVariables(`${editor.subject}\n${editor.htmlContent}`))
const editorHook = computed(() => hookDefinitions.value.find(hook => hook.eventKey === editorEvent.value))
const editorBoundHook = computed(() => editorId.value && bindings.value.find(hook => String(hook.templateId) === String(editorId.value)))
const contractError = computed(() => {
  if (!editorEvent.value || editorEvent.value === 'custom') return ''
  if (!editorHook.value) return '代码事件不可用，请重新加载参数'
  const missing = editorHook.value.variables.filter(name => !editorVariables.value.includes(name))
  const extra = editorVariables.value.filter(name => !editorHook.value.variables.includes(name))
  if (missing.length || extra.length) return [missing.length ? `缺少参数：${missing.join('、')}` : '', extra.length ? `多出参数：${extra.join('、')}` : ''].filter(Boolean).join('；')
  if (['PASSWORD_CODE', 'APP_REGISTRATION_CODE'].includes(editorEvent.value) && (extractVariables(editor.subject).includes('code') || !['code', 'expiresInMinutes'].every(name => extractVariables(editor.htmlContent).includes(name)))) return '验证码和有效分钟数必须放在正文，主题不能包含验证码'
  return ''
})
const placeholder = name => '{{' + name + '}}'

onMounted(loadAll)

async function loadAll() {
  await Promise.all([loadSmtp(), loadTemplates(), loadHooks()])
}

async function loadHooks() {
  hooksLoading.value = true
  hooksError.value = false
  try { hookDefinitions.value = (await request.get('/api/email/hooks')).data || [] }
  catch { hooksError.value = true }
  finally { hooksLoading.value = false }
}

async function loadSmtp() {
  const result = await getEmailConfig()
  Object.assign(smtp, result.data || {})
  smtp.password = ''
}

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const result = await getEmailTemplates()
    templates.value = result.data || []
  } finally {
    templatesLoading.value = false
  }
}

async function saveSmtp() {
  smtpSaving.value = true
  try {
    const result = await updateEmailConfig({
      host: smtp.host,
      port: String(smtp.port),
      username: smtp.username,
      password: smtp.password,
      fromName: smtp.fromName,
      ssl: String(smtp.ssl),
      starttls: String(smtp.starttls),
      enabled: String(smtp.enabled),
    })
    Object.assign(smtp, result.data || {})
    smtp.password = ''
    ElMessage.success('邮件 SMTP 配置已保存')
  } finally {
    smtpSaving.value = false
  }
}

async function testSmtp() {
  smtpTesting.value = true
  try {
    await testEmailConfig()
    ElMessage.success('SMTP 连通性测试通过')
  } finally {
    smtpTesting.value = false
  }
}

function blankEditor() {
  Object.assign(editor, { templateKey: '', templateName: '', subject: '', htmlContent: '', description: '', enabled: false })
  editorId.value = null
  editorEvent.value = ''
}

function openEditor(row = null) {
  if (!row) blankEditor()
  else {
    editorId.value = row.id
    Object.assign(editor, {
      templateKey: row.templateKey || '',
      templateName: row.templateName || '',
      subject: row.subject || '',
      htmlContent: row.htmlContent || '',
      description: row.description || '',
      enabled: row.enabled !== false,
    })
    const matching = hookDefinitions.value.find(hook => hook.variables.length === editorVariables.value.length && hook.variables.every(name => editorVariables.value.includes(name)))
    editorEvent.value = editorBoundHook.value?.eventKey || matching?.eventKey || 'custom'
  }
  editorVisible.value = true
}

async function saveEditor() {
  if (!editorEvent.value || contractError.value) return ElMessage.warning(contractError.value || '请先选择模板用途 / 代码事件')
  if (!editor.templateKey.trim() || !editor.templateName.trim() || !editor.subject.trim() || !editor.htmlContent.trim()) {
    ElMessage.warning('请填写模板标识、名称、主题和 HTML 内容')
    return
  }
  editorSaving.value = true
  try {
    const payload = { ...editor, eventKey: editorEvent.value === 'custom' ? null : editorEvent.value }
    if (editorId.value) await updateEmailTemplate(editorId.value, payload)
    else await createEmailTemplate(payload)
    editorVisible.value = false
    ElMessage.success('邮件模板已保存')
    await loadTemplates()
  } finally {
    editorSaving.value = false
  }
}

function insertParameter(parameter) {
  editor.htmlContent += `\n<p>${escapeHtml(parameter.label)}：${placeholder(parameter.name)}</p>`
}

async function generateStarter() {
  if (!editorHook.value) return
  if (editor.subject.trim() || editor.htmlContent.trim()) {
    try { await ElMessageBox.confirm('将替换当前主题和 HTML 正文，名称、标识和启用状态不变。', '生成基础内容', {type: 'warning', modalClass: 'email-confirm', confirmButtonText: '替换内容', cancelButtonText: '取消'}) }
    catch { return }
  }
  const hook = editorHook.value
  if (!editor.templateName.trim()) editor.templateName = hook.title
  editor.subject = hook.title + ' · 软件协会'
  editor.htmlContent = '<!doctype html>\n<html lang="zh-CN"><body style="font-family:Arial,sans-serif;line-height:1.8;color:#243047;padding:24px">\n<h2>' + escapeHtml(hook.title) + '</h2>\n' + hook.parameters.map(parameter => `<p>${escapeHtml(parameter.label)}：${placeholder(parameter.name)}</p>`).join('\n') + '\n<hr>\n<p>万维网连接五大洲，二进制写尽天下事</p>\n<p>中南林业科技大学软件协会</p>\n</body></html>'
}

function previewEditor() {
  const values = Object.fromEntries(editorVariables.value.map(name => [name, editorHook.value?.parameters.find(parameter => parameter.name === name)?.example || `示例${name}`]))
  previewSubject.value = renderText(editor.subject, values)
  previewHtml.value = renderText(editor.htmlContent, values, true)
  previewDevice.value = 'desktop'
  previewVisible.value = true
}

async function openPreview(row) {
  const names = row.variables || []
  const values = Object.fromEntries(names.map(name => [name, `示例${name}`]))
  const result = await previewEmailTemplate(row.id, values)
  previewSubject.value = result.data?.subject || row.subject || ''
  previewHtml.value = result.data?.html || row.htmlContent || ''
  previewDevice.value = 'desktop'
  previewVisible.value = true
}

function openSend(row) {
  editorId.value = row.id
  Object.assign(editor, {
    templateKey: row.templateKey || '',
    templateName: row.templateName || '',
    subject: row.subject || '',
    htmlContent: row.htmlContent || '',
    description: row.description || '',
    enabled: row.enabled !== false,
  })
  sendTo.value = ''
  sendVariables.value = extractVariables(`${row.subject || ''}\n${row.htmlContent || ''}`)
  Object.keys(sendValues).forEach(key => delete sendValues[key])
  sendVariables.value.forEach(name => { sendValues[name] = `示例${name}` })
  sendVisible.value = true
}

async function sendTest() {
  if (!sendTo.value.trim()) return ElMessage.warning('请输入收件邮箱')
  sending.value = true
  try {
    await sendTestEmail({
      to: sendTo.value.trim(),
      templateId: editorId.value,
      subject: editor.subject,
      htmlContent: editor.htmlContent,
      variables: { ...sendValues },
    })
    sendVisible.value = false
    ElMessage.success('测试邮件已加入队列，请在发送日志查看结果')
  } finally {
    sending.value = false
  }
}

async function removeTemplate(row) {
  try {
    await ElMessageBox.confirm(`确认删除模板「${row.templateName}」？`, '删除邮件模板', { type: 'warning' })
    await deleteEmailTemplate(row.id)
    ElMessage.success('模板已删除')
    await loadTemplates()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  }
}

function extractVariables(source) {
  const names = []
  const seen = new Set()
  const pattern = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g
  let match
  while ((match = pattern.exec(source || ''))) {
    if (!seen.has(match[1])) { seen.add(match[1]); names.push(match[1]) }
  }
  return names
}

function renderText(source, values, html = false) {
  return String(source || '').replace(/\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g, (_, key) => {
    const value = values[key] ?? ''
    return html ? escapeHtml(String(value)) : String(value)
  })
}

function escapeHtml(value) {
  return value.replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]))
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}
</script>

<style scoped>
.email-page { max-width: 1440px; margin: 0 auto; }
.page-header { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:24px; }
.page-header h2 { margin:0 0 6px; font-family:var(--zen-font-serif); letter-spacing:2px; }
.page-header p, .section-title p { margin:0; color:var(--zen-text-muted); font-size:13px; }
.email-card { padding:24px; margin-bottom:24px; border:1px solid var(--zen-border-light); border-radius:var(--zen-radius-lg); background:var(--zen-card); box-shadow:var(--zen-shadow-sm); }
.section-title { display:flex; align-items:flex-start; justify-content:flex-start; gap:14px; margin-bottom:22px; }
.template-actions { display:flex; align-items:center; gap:8px; white-space:nowrap; }
.template-actions :deep(.el-button) { margin-left:0!important; border-radius:7px!important; padding:7px 11px!important; min-height:30px; }
.section-title--between { align-items:center; justify-content:space-between; }
.section-title__left { display:flex; align-items:flex-start; gap:14px; }
.section-title__icon { display:flex; align-items:center; justify-content:center; width:40px; height:40px; border-radius:12px; background:var(--zen-accent-bg); font-size:20px; flex:none; }
.section-title h3 { margin:0 0 5px; font-size:17px; }
.smtp-form { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:0 18px; }
.smtp-form .el-form-item:nth-child(3) { grid-column:span 2; }
.smtp-form .el-form-item:nth-child(4) { grid-column:span 2; }
.switch-item :deep(.el-form-item__content) { min-height:32px; }
.switches { display:flex; align-items:center; gap:22px; height:32px; }
.action-row { display:flex; align-items:center; gap:12px; flex-wrap:wrap; padding-top:6px; }
.action-hint { color:var(--zen-text-muted); font-size:12px; }
.variable-list { display:flex; gap:5px; flex-wrap:wrap; }
.muted { color:var(--zen-text-muted); }
code { padding:2px 6px; border-radius:5px; background:var(--zen-bg-warm); color:var(--zen-accent-dark); font-size:12px; }
.editor-meta { display:grid; grid-template-columns:1fr 1fr; gap:18px; }
.contract-panel { padding:18px; margin-bottom:22px; border:1px solid var(--zen-border-light); border-radius:12px; background:var(--zen-bg); }
.contract-heading,.contract-actions { display:flex; gap:12px; align-items:center; flex-wrap:wrap; margin-bottom:12px; }
.contract-parameters { display:grid; gap:8px; margin:14px 0; }
.contract-parameter { display:flex; align-items:center; justify-content:space-between; gap:14px; padding:10px 12px; background:var(--zen-card); border-radius:8px; }
.contract-parameter>div { min-width:0; overflow-wrap:anywhere; }
.contract-parameter p { font-size:12px; margin:6px 0 0; color:var(--zen-text-secondary); }
.contract-parameter :deep(.el-button) { flex-shrink:0; }
.contract-actions span { font-size:12px; color:var(--zen-text-muted); }
.contract-error { color:var(--el-color-danger); }
.html-editor :deep(textarea) { font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace; line-height:1.55; }
.editor-foot { display:flex; justify-content:space-between; align-items:center; gap:16px; margin:-4px 0 16px; }
.detected-variables { display:flex; align-items:center; gap:6px; flex-wrap:wrap; color:var(--zen-text-muted); font-size:12px; }
.preview-toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; color:var(--zen-text-secondary); }
.preview-stage { min-height:460px; display:flex; justify-content:center; padding:14px; border:1px solid var(--zen-border-light); border-radius:12px; background:#f4f5f7; overflow:auto; }
.preview-stage--mobile { background:#e7e9ed; }
.preview-frame { width:100%; min-height:600px; border:0; border-radius:6px; background:#fff; box-shadow:var(--zen-shadow-sm); }
.preview-stage--mobile .preview-frame { width:375px; min-width:375px; min-height:667px; }
.dialog-tip { margin:0 0 18px; color:var(--zen-text-muted); font-size:13px; line-height:1.7; }
@media (max-width: 900px) {
  .smtp-form { grid-template-columns:1fr 1fr; }
  .smtp-form .el-form-item:nth-child(3), .smtp-form .el-form-item:nth-child(4) { grid-column:span 1; }
}
@media (max-width: 640px) {
  .email-card { padding:16px; }
  .page-header, .section-title--between { align-items:flex-start; flex-direction:column; }
  .smtp-form, .editor-meta { grid-template-columns:1fr; }
  .smtp-form .el-form-item:nth-child(3), .smtp-form .el-form-item:nth-child(4) { grid-column:auto; }
  .preview-stage { padding:8px; }
}
</style>
