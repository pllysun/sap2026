<template>
  <section class="zen-card registration-settings" aria-labelledby="registration-settings-title">
    <div class="registration-settings__header">
      <div>
        <h3 id="registration-settings-title">注册防护</h3>
        <p>配置验证码、注册额度与请求频率。保存后自动生效，无需重启服务。</p>
      </div>
      <el-tag v-if="config" :type="changed ? 'warning' : 'success'" effect="plain">
        {{ changed ? '有未保存的修改' : '已与数据库同步' }}
      </el-tag>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="registration-settings__error" />
    <div v-if="loading && !config" role="status" class="registration-settings__empty">正在加载注册防护配置…</div>
    <el-button v-if="!config && !loading" @click="load">重新加载配置</el-button>

    <el-form v-if="config" label-position="top" :disabled="saving || loading" @submit.prevent="save">
      <div class="registration-settings__groups">
        <section v-for="group in groups" :key="group.key" class="registration-settings__group">
          <div class="registration-settings__group-heading">
            <h4>{{ group.title }}</h4>
            <el-switch v-model="config[group.key].enabled" :aria-label="group.title + '开关'" />
          </div>
          <p class="registration-settings__hint">{{ group.description }}</p>
          <div class="registration-settings__fields">
            <el-form-item v-for="field in group.fields" :key="field.key" :label="field.label" :for="'registration-' + field.key">
              <div class="registration-settings__number">
                <el-input-number :id="'registration-' + field.key" v-model="config[group.key][field.key]"
                  :min="field.min" :max="field.max" :precision="0" :step="1" controls-position="right"
                  :disabled="!config[group.key].enabled && !field.shared" :aria-label="field.label" />
                <span>{{ field.unit }}</span>
              </div>
              <small v-if="field.hint">{{ field.hint }}</small>
            </el-form-item>
          </div>
        </section>
      </div>

      <el-collapse class="registration-settings__advanced">
        <el-collapse-item title="可信代理配置" name="proxies">
          <el-form-item label="可信反向代理 IP" for="registration-trusted-proxies">
            <el-input id="registration-trusted-proxies" v-model="config.trustedProxies" type="textarea" :rows="3"
              :maxlength="500" show-word-limit placeholder="每行一个 IP，或使用逗号分隔；留空表示不信任代理头" />
          </el-form-item>
          <p class="registration-settings__hint">填写实际连接后端的代理 IP。只支持确切的 IPv4 / IPv6 地址，代理需覆盖真实 IP 请求头。一体化镜像通常使用 127.0.0.1 和 ::1。</p>
        </el-collapse-item>
      </el-collapse>

      <div class="registration-settings__notice">
        <p>注册额度统计通过验证码后的提交，失败提交也占额度；保存配置会保留已有计数。校园网共用出口 IP 时，请根据招新规模调整 IP 额度。</p>
        <p>验证码开关只影响注册。图片参数和获取频率同时适用于密码找回；其他实例最多 5 秒同步配置。</p>
      </div>
      <div class="registration-settings__actions">
        <el-button type="primary" native-type="submit" :loading="saving" :disabled="!changed || loading">保存注册防护配置</el-button>
        <el-button :disabled="!changed" @click="undo">撤销修改</el-button>
        <el-button @click="useDefaults">填入推荐值</el-button>
        <el-button :loading="loading" @click="load">重新加载</el-button>
      </div>
    </el-form>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRegistrationProtectionConfig, updateRegistrationProtectionConfig } from '../api'

const config = ref(null)
const original = ref(null)
const defaults = ref(null)
const revision = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const copy = value => JSON.parse(JSON.stringify(value))
const changed = computed(() => config.value && JSON.stringify(config.value) !== JSON.stringify(original.value))

// 这里只定义输入范围与展示文案，实际参数及推荐值全部来自后端数据库配置接口。
const groups = [
  { key: 'captcha', title: '注册验证码', description: '开启后根据免验证额度要求图形验证；验证码绑定来源 IP 且只能使用一次。', fields: [
    { key: 'freeLimit', label: '同 IP 免验证次数', min: 0, max: 1000, unit: '次', hint: '0 表示每次注册都需验证' },
    { key: 'freeWindowHours', label: '免验证计数窗口', min: 1, max: 168, unit: '小时' },
    { key: 'length', label: '验证码位数', min: 4, max: 6, unit: '位', shared: true },
    { key: 'ttlSeconds', label: '验证码有效期', min: 30, max: 600, unit: '秒', shared: true },
    { key: 'minSolveSeconds', label: '最短作答时间', min: 0, max: 30, unit: '秒', hint: '过快提交会被拒绝', shared: true },
  ] },
  { key: 'quotas', title: '注册额度与并发', description: '限制通过验证后的批量提交；各窗口从首次提交起算。', fields: [
    { key: 'cooldownSeconds', label: '同 IP 注册间隔', min: 1, max: 3600, unit: '秒' },
    { key: 'ipHourlyLimit', label: '同 IP 每小时上限', min: 1, max: 10000, unit: '次' },
    { key: 'ipDailyLimit', label: '同 IP 24 小时上限', min: 1, max: 50000, unit: '次' },
    { key: 'qqDailyLimit', label: '同 QQ 24 小时上限', min: 1, max: 100, unit: '次' },
    { key: 'globalMinuteLimit', label: '全站每分钟上限', min: 1, max: 10000, unit: '次' },
    { key: 'maxConcurrent', label: '每实例总并发上限', min: 1, max: 128, unit: '个' },
    { key: 'maxConcurrentPerIp', label: '同 IP 并发上限', min: 1, max: 128, unit: '个' },
  ] },
  { key: 'requests', title: '请求频率保护', description: '注册请求在验证前就计数。瞬时额度耗尽后按持续速率恢复。', fields: [
    { key: 'registerCapacity', label: '同 IP 注册请求瞬时上限', min: 1, max: 1000, unit: '次' },
    { key: 'registerPerMinute', label: '同 IP 注册请求持续速率', min: 1, max: 1000, unit: '次/分' },
    { key: 'captchaCapacity', label: '同 IP 获取图片瞬时上限', min: 1, max: 1000, unit: '次' },
    { key: 'captchaPerMinute', label: '同 IP 获取图片持续速率', min: 1, max: 1000, unit: '次/分' },
    { key: 'captchaGlobalCapacity', label: '全站获取图片瞬时上限', min: 1, max: 5000, unit: '次' },
    { key: 'captchaGlobalPerMinute', label: '全站获取图片持续速率', min: 1, max: 5000, unit: '次/分' },
  ] },
]

function accept(data) {
  if (!data?.revision || !data.config?.captcha || !data.config?.quotas || !data.config?.requests || !data.defaults) {
    throw new Error('注册防护配置不完整，请重新加载')
  }
  config.value = copy(data.config)
  original.value = copy(data.config)
  defaults.value = copy(data.defaults)
  revision.value = data.revision
}

async function load() {
  if (loading.value || saving.value) return
  loading.value = true
  error.value = ''
  try { accept((await getRegistrationProtectionConfig()).data) }
  catch (e) { error.value = e.message || '配置加载失败，请稍后重试' }
  finally { loading.value = false }
}

function validate() {
  for (const group of groups) {
    for (const field of group.fields) {
      const value = config.value[group.key][field.key]
      if (!Number.isInteger(value) || value < field.min || value > field.max) {
        return `${field.label}需为 ${field.min}–${field.max} 之间的整数`
      }
    }
  }
  if (config.value.captcha.minSolveSeconds >= config.value.captcha.ttlSeconds) return '最短作答时间必须小于验证码有效期'
  if (config.value.quotas.maxConcurrentPerIp > config.value.quotas.maxConcurrent) return '同 IP 并发上限不能超过实例总并发上限'
  return ''
}

async function save() {
  if (saving.value || loading.value || !changed.value) return
  error.value = validate()
  if (error.value) return
  saving.value = true
  try {
    accept((await updateRegistrationProtectionConfig({ revision: revision.value, config: copy(config.value) })).data)
    ElMessage.success('注册防护配置已保存并生效')
  } catch (e) { error.value = e.message || '保存失败，请稍后重试' }
  finally { saving.value = false }
}

function undo() { config.value = copy(original.value); error.value = '' }
function useDefaults() { config.value = copy(defaults.value); error.value = '' }
onMounted(load)
</script>

<style scoped>
.registration-settings { margin-bottom: 24px; }
.registration-settings__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.registration-settings__header h3 { font-size: 18px; margin: 0 0 6px; }
.registration-settings__header p, .registration-settings__hint, .registration-settings__notice { color: var(--zen-text-muted, #777); font-size: 12px; line-height: 1.7; margin: 0; }
.registration-settings__groups { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.registration-settings__group { padding: 18px; border: 1px solid var(--zen-border, #e8e8e8); border-radius: 12px; min-width: 0; }
.registration-settings__group-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.registration-settings__group-heading h4 { margin: 0; font-size: 15px; }
.registration-settings__hint { margin: 8px 0 16px; }
.registration-settings__fields { display: grid; gap: 4px; }
.registration-settings__number { display: flex; align-items: center; gap: 8px; width: 100%; }
.registration-settings__number .el-input-number { width: 100%; flex: 1; min-width: 0; }
.registration-settings__number span { flex: 0 0 40px; font-size: 12px; color: var(--zen-text-muted, #777); }
.registration-settings__fields small { color: var(--zen-text-muted, #777); line-height: 1.5; margin-top: 4px; }
.registration-settings__advanced { margin-top: 20px; }
.registration-settings__notice { margin-top: 16px; }
.registration-settings__notice p { margin: 6px 0; }
.registration-settings__actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.registration-settings__actions .el-button { margin-left: 0; }
.registration-settings__error { margin-bottom: 16px; }
.registration-settings__empty { padding: 24px 0; color: var(--zen-text-muted, #777); }
@media (max-width: 1200px) { .registration-settings__groups { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .registration-settings__groups { grid-template-columns: 1fr; } .registration-settings__header { flex-direction: column; } }
</style>
