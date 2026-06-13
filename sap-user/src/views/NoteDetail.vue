<template>
  <div class="page note-detail-page">
    <!-- 加载中 -->
    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <!-- 内容 -->
    <template v-else-if="note">
      <div class="note-detail__header anim-in">
        <button class="note-detail__back" @click="$router.push('/notes')">
          ← 返回列表
        </button>
        <h1 class="note-detail__title">{{ note.title }}</h1>
        <div class="note-detail__meta">
          <span>✍ {{ note.authorName }}</span>
          <span>📅 {{ formatDate(note.createdAt) }}</span>
          <span>👁 {{ note.viewCount }} 次浏览</span>
          <span>⬇ {{ note.downloadCount }} 次下载</span>
        </div>
      </div>

      <!-- Markdown 渲染区 -->
      <article class="note-detail__body card anim-in" style="animation-delay: 0.1s;">
        <div class="markdown-body" v-html="renderedHtml"></div>
      </article>
    </template>

    <!-- 错误 -->
    <div v-else class="empty">
      <div style="font-size:3rem; margin-bottom: var(--s3); opacity:0.4;">😥</div>
      <div class="empty__text">{{ errorMsg || '笔记不存在或无权访问' }}</div>
      <button class="btn btn--primary btn--pill" style="margin-top: var(--s4);" @click="$router.push('/notes')">返回列表</button>
    </div>

    <!-- 悬浮操作菜单 -->
    <Teleport to="body">
      <div class="fab-menu" :class="{ 'fab-menu--open': fabOpen }">
        <!-- 展开时的操作按钮 -->
        <Transition name="fab-item">
          <div v-if="fabOpen" class="fab-items">
            <button class="fab-item" @click="scrollToTop" title="返回顶部">
              <span class="fab-item__icon">⬆</span>
              <span class="fab-item__label">顶部</span>
            </button>
            <button class="fab-item" @click="handleDownload" title="下载PDF">
              <span class="fab-item__icon">📥</span>
              <span class="fab-item__label">下载</span>
            </button>
            <button class="fab-item" @click="$router.push('/notes')" title="返回列表">
              <span class="fab-item__icon">📋</span>
              <span class="fab-item__label">列表</span>
            </button>
          </div>
        </Transition>
        <!-- 主按钮 -->
        <button class="fab-main" @click="fabOpen = !fabOpen" :class="{ 'fab-main--active': fabOpen }">
          <span class="fab-main__icon">{{ fabOpen ? '✕' : '☰' }}</span>
        </button>
      </div>
    </Teleport>

    <!-- 打印专用隐藏区域 -->
    <div id="print-area" class="print-only" v-if="note">
      <h1 style="text-align:center; margin-bottom: 8px;">{{ note.title }}</h1>
      <p style="text-align:center; color:#888; margin-bottom: 24px; font-size: 12px;">
        {{ note.authorName }} · {{ formatDate(note.createdAt) }}
      </p>
      <div class="markdown-body" v-html="renderedHtml"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// 配置 marked 使用 highlight.js
marked.setOptions({
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(code, { language: lang }).value } catch {}
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true
})

const route = useRoute()
const router = useRouter()
const note = ref(null)
const loading = ref(true)
const errorMsg = ref('')
const fabOpen = ref(false)

const renderedHtml = computed(() => {
  if (!note.value?.content) return ''
  try { return DOMPurify.sanitize(marked(note.value.content)) } catch { return '<p>Markdown 解析失败</p>' }
})

const formatDate = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

const loadNote = async () => {
  const id = route.params.id
  loading.value = true
  try {
    const res = await request.get(`/api/note/${id}`)
    note.value = res.data
  } catch (e) {
    errorMsg.value = e?.message || '加载失败'
    note.value = null
  }
  loading.value = false
}

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
  fabOpen.value = false
}

const handleDownload = async () => {
  fabOpen.value = false
  // 通过后端接口下载 PDF
  const token = localStorage.getItem('sap_token') || ''
  const url = `/api/note/${route.params.id}/pdf?sap-token=${encodeURIComponent(token)}`
  window.open(url, '_blank')
}

onMounted(() => {
  loadNote()
})
</script>

<style scoped>
.note-detail-page {
  max-width: 860px;
  margin: 0 auto;
}

/* 头部 */
.note-detail__header {
  margin-bottom: var(--s5);
}
.note-detail__back {
  background: none;
  border: 1px solid var(--ink-200);
  padding: 6px 16px;
  border-radius: var(--r-pill);
  font-size: 0.8rem;
  color: var(--ink-500);
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: var(--s4);
}
.note-detail__back:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: rgba(59,130,246,0.04);
}
.note-detail__title {
  font-size: 2rem;
  font-weight: 800;
  color: var(--ink-900);
  line-height: 1.3;
  letter-spacing: -0.02em;
  margin-bottom: var(--s3);
}
.note-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s4);
  font-size: 0.8125rem;
  color: var(--ink-400);
}

/* Markdown 内容区 */
.note-detail__body {
  padding: var(--s6) var(--s7);
  line-height: 1.85;
  font-size: 0.9375rem;
  color: var(--ink-700);
}

/* Markdown 样式 */
.markdown-body :deep(h1) {
  font-size: 1.8em;
  margin: 1em 0 0.4em;
  padding-bottom: 0.3em;
  border-bottom: 2px solid var(--ink-100);
  color: var(--ink-900);
  font-weight: 800;
}
.markdown-body :deep(h2) {
  font-size: 1.5em;
  margin: 0.8em 0 0.3em;
  padding-bottom: 0.2em;
  border-bottom: 1px solid var(--ink-100);
  color: var(--ink-800);
  font-weight: 700;
}
.markdown-body :deep(h3) {
  font-size: 1.25em;
  margin: 0.7em 0 0.3em;
  color: var(--ink-800);
  font-weight: 700;
}
.markdown-body :deep(h4) {
  font-size: 1.1em;
  margin: 0.6em 0 0.3em;
  color: var(--ink-700);
  font-weight: 600;
}
.markdown-body :deep(p) {
  margin: 0.6em 0;
}
.markdown-body :deep(code) {
  background: rgba(59,130,246,0.06);
  color: #e06c75;
  padding: 2px 7px;
  border-radius: 5px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.88em;
}
.markdown-body :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 20px;
  border-radius: 12px;
  overflow-x: auto;
  margin: 16px 0;
  box-shadow: inset 0 2px 8px rgba(0,0,0,0.2);
  line-height: 1.6;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
  font-size: 0.85em;
}
.markdown-body :deep(blockquote) {
  border-left: 4px solid var(--primary, #3b82f6);
  padding: 12px 20px;
  margin: 16px 0;
  background: linear-gradient(135deg, rgba(59,130,246,0.04), rgba(20,184,166,0.04));
  color: var(--ink-600);
  border-radius: 0 10px 10px 0;
  font-style: italic;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
  border-radius: 8px;
  overflow: hidden;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--ink-100);
  padding: 10px 14px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: linear-gradient(180deg, var(--ink-50), rgba(0,0,0,0.02));
  font-weight: 700;
  color: var(--ink-700);
}
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 10px;
  margin: 8px 0;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 10px 0;
}
.markdown-body :deep(li) {
  margin: 5px 0;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 2px solid var(--ink-100);
  margin: 24px 0;
}
.markdown-body :deep(a) {
  color: var(--primary, #3b82f6);
  text-decoration: none;
  border-bottom: 1px dashed rgba(59,130,246,0.3);
  transition: all 0.2s;
}
.markdown-body :deep(a:hover) {
  border-bottom-style: solid;
  border-bottom-color: var(--primary);
}
.markdown-body :deep(strong) {
  font-weight: 700;
  color: var(--ink-800);
}
.markdown-body :deep(em) {
  font-style: italic;
  color: var(--ink-600);
}

/* ========== 悬浮操作菜单 ========== */
.fab-menu {
  position: fixed;
  bottom: 28px;
  right: 28px;
  z-index: 900;
  display: flex;
  flex-direction: column-reverse;
  align-items: center;
  gap: 10px;
}

.fab-main {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #14B8A6, #3B82F6);
  color: #fff;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(59,130,246,0.35);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
}
.fab-main:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 28px rgba(59,130,246,0.45);
}
.fab-main--active {
  background: linear-gradient(135deg, #ef4444, #f97316);
  transform: rotate(90deg);
}
.fab-main__icon {
  transition: transform 0.3s;
  line-height: 1;
}

.fab-items {
  display: flex;
  flex-direction: column-reverse;
  gap: 8px;
  animation: fabSlideIn 0.25s ease-out;
}
@keyframes fabSlideIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.fab-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 8px 10px;
  background: var(--bg-card, #fff);
  border: 1px solid var(--ink-100);
  border-radius: var(--r-pill);
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
  white-space: nowrap;
  font-size: 0.85rem;
  color: var(--ink-700);
}
.fab-item:hover {
  background: var(--ink-50);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
  transform: translateX(-4px);
}
.fab-item__icon {
  font-size: 1.1rem;
}
.fab-item__label {
  font-weight: 600;
}

/* fade transition for fab items */
.fab-item-enter-active { animation: fabSlideIn 0.25s ease-out; }
.fab-item-leave-active { animation: fabSlideIn 0.15s ease-in reverse; }

/* ========== 打印 ========== */
.print-only {
  display: none;
}
@media print {
  .print-only {
    display: block !important;
  }
  .note-detail__header,
  .note-detail__body,
  .fab-menu,
  .nav,
  .footer { display: none !important; }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .note-detail-page {
    padding: 0 var(--s3);
  }
  .note-detail__title {
    font-size: 1.5rem;
  }
  .note-detail__body {
    padding: var(--s4) var(--s4);
  }
  .note-detail__meta {
    gap: var(--s3);
    font-size: 0.75rem;
  }
  .fab-menu {
    bottom: 20px;
    right: 16px;
  }
  .fab-main {
    width: 48px;
    height: 48px;
    font-size: 1.1rem;
  }
  .fab-item {
    font-size: 0.8rem;
    padding: 7px 14px 7px 9px;
  }

  .markdown-body :deep(pre) {
    padding: 14px;
    border-radius: 8px;
    font-size: 0.8rem;
  }
  .markdown-body :deep(h1) { font-size: 1.5em; }
  .markdown-body :deep(h2) { font-size: 1.3em; }
}
</style>
