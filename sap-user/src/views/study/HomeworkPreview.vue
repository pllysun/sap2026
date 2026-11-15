<template>
  <div class="page homework-preview-page">
    <div class="hw-preview__top">
      <button class="btn btn--ghost btn--sm btn--pill" @click="goBack">
        <UiIcon name="chevron-left" :size="16" /> 返回学习小组
      </button>
    </div>

    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <div v-else-if="error" class="empty" role="alert">
      <div class="empty-icon"><UiIcon name="warning" :size="24" /></div>
      <div class="empty__text">{{ error }}</div>
      <div class="hw-preview__error-actions">
        <button class="btn btn--primary btn--pill" @click="load">重试</button>
        <button class="btn btn--secondary btn--pill" @click="goBack">返回学习小组</button>
      </div>
    </div>

    <template v-else>
      <header class="hw-preview__header">
        <span class="page-kicker">HOMEWORK / 第 {{ week }} 周</span>
        <h1 class="hw-preview__title">{{ data.title || data.fileName || '作业预览' }}</h1>
        <p class="hw-preview__meta">
          <span v-if="data.fileName"><UiIcon name="file-plus" :size="14" />{{ data.fileName }}</span>
          <span v-if="data.updatedAt"><UiIcon name="clock" :size="14" />更新于 {{ formatTime(data.updatedAt) }}</span>
        </p>
        <div class="hw-preview__actions">
          <button v-if="data.fileUrl" class="btn btn--secondary btn--sm btn--pill" @click="download">
            <UiIcon name="file-save" :size="16" /> 下载原文件
          </button>
          <button class="btn btn--ghost btn--sm btn--pill" @click="load">
            <UiIcon name="refresh" :size="16" /> 刷新
          </button>
        </div>
      </header>

      <article class="card hw-preview__body">
        <MarkdownViewer
          v-if="data.previewable"
          :content="data.content"
          :format="data.format"
          :base-url="data.fileUrl"
        />
        <div v-else class="hw-preview__fallback">
          <div class="hw-preview__fallback-icon"><UiIcon name="warning" :size="24" /></div>
          <p>{{ data.reason || '该作业暂不支持在线预览，请下载查看' }}</p>
          <button v-if="data.fileUrl" class="btn btn--primary btn--pill" @click="download">
            <UiIcon name="file-save" :size="16" /> 下载文件
          </button>
        </div>
      </article>
    </template>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UiIcon from '@/components/UiIcon.vue'
import MarkdownViewer from '@/components/MarkdownViewer.vue'
import request from '@/utils/request'
import { openStoredFile } from '@/utils/downloadFile'

const route = useRoute()
const router = useRouter()

const activityId = ref(Number(route.params.activityId))
const week = ref(Number(route.params.week))
const data = ref({})
const loading = ref(true)
const error = ref('')
let requestId = 0

function goBack() {
  router.push('/study')
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function load() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const response = await request.get('/api/study/homework/preview', {
      params: { activityId: activityId.value, week: week.value }
    })
    if (current !== requestId) return
    data.value = response.data || {}
  } catch (e) {
    if (current !== requestId) return
    error.value = e?.message || '作业预览加载失败，请稍后重试'
  } finally {
    if (current === requestId) loading.value = false
  }
}

function download() {
  if (!data.value.fileUrl) return
  openStoredFile(data.value.fileUrl, data.value.fileName).catch(e => window.alert(e.message))
}

onMounted(load)
onBeforeUnmount(() => { requestId++ })
watch(
  () => [route.params.activityId, route.params.week],
  ([nextActivity, nextWeek]) => {
    activityId.value = Number(nextActivity)
    week.value = Number(nextWeek)
    load()
  }
)
</script>

<style scoped>
.homework-preview-page {
  max-width: 64rem;
}
.hw-preview__top {
  margin-bottom: var(--s3);
}
.hw-preview__header {
  padding: 1rem 0.5rem 2rem;
}
.hw-preview__title {
  font-size: clamp(1.625rem, 2.5vw, 2.25rem);
  line-height: 1.5;
  max-width: 30em;
  margin: 0.35rem 0 0;
  color: var(--ink-900);
}
.hw-preview__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1.25rem;
  margin-top: 0.85rem;
  color: var(--ink-400);
  font-size: 0.8125rem;
}
.hw-preview__meta > span {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
}
.hw-preview__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1.1rem;
}
.hw-preview__body {
  padding: 2.25rem 3rem;
  box-shadow: none;
  border-radius: 1rem;
  min-width: 0;
}
.hw-preview__fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.75rem;
  padding: 3rem 1.5rem;
  color: var(--ink-500);
}
.hw-preview__fallback-icon {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border-radius: 0.875rem;
  background: #f1f3fa;
  color: #596aaa;
}
.hw-preview__error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
}
@media (max-width: 768px) {
  .hw-preview__header {
    padding: 0.5rem 0 1.5rem;
  }
  .hw-preview__body {
    padding: 1.25rem;
  }
}
</style>
