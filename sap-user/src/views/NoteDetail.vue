<template>
  <div class="page note-detail-page">
    <!-- 加载中 -->
    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <!-- 内容 -->
    <template v-else-if="note">
      <div class="note-detail__header anim-in">
        <button class="note-detail__back" @click="$router.push('/notes')">
          <UiIcon name="list" :size="16" /> 返回列表
        </button>
        <h1 class="note-detail__title">{{ note.title }}</h1>
        <div class="note-detail__meta">
          <span><UiIcon name="edit" :size="18" /> {{ note.authorName }}</span>
          <span><UiIcon name="calendar" :size="18" /> {{ formatDate(note.createdAt) }}</span>
          <span><UiIcon name="eye" :size="18" /> {{ note.viewCount }} 次浏览</span>
          <span class="note-detail__download-count"><UiIcon name="file-save" :size="15" /> {{ note.downloadCount }} 次下载</span>
        </div>
      </div>

      <!-- Markdown 渲染区 -->
      <article class="note-detail__body card anim-in" style="animation-delay: 0.1s;">
        <MarkdownViewer :content="note.content" />
      </article>
    </template>

    <!-- 错误 -->
    <div v-else class="empty">
      <div class="empty-icon"><UiIcon name="warning" :size="24" /></div>
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
              <span class="fab-item__icon"><UiIcon name="panel-top" /></span>
              <span class="fab-item__label">顶部</span>
            </button>
            <button class="fab-item" @click="handleDownload" title="下载PDF">
              <span class="fab-item__icon"><UiIcon name="file-save" /></span>
              <span class="fab-item__label">下载</span>
            </button>
            <button class="fab-item" @click="$router.push('/notes')" title="返回列表">
              <span class="fab-item__icon"><UiIcon name="list" /></span>
              <span class="fab-item__label">列表</span>
            </button>
          </div>
        </Transition>
        <!-- 主按钮 -->
        <button class="fab-main" aria-label="笔记操作菜单" :aria-expanded="fabOpen" @click="fabOpen = !fabOpen" :class="{ 'fab-main--active': fabOpen }">
          <UiIcon :name="fabOpen ? 'close' : 'list'" :size="21" />
        </button>
      </div>
    </Teleport>

    <!-- 打印专用隐藏区域 -->
    <div id="print-area" class="print-only" v-if="note">
      <h1 style="text-align:center; margin-bottom: 8px;">{{ note.title }}</h1>
      <p style="text-align:center; color:#888; margin-bottom: 24px; font-size: 12px;">
        {{ note.authorName }} · {{ formatDate(note.createdAt) }}
      </p>
      <MarkdownViewer :content="note.content" />
    </div>
  </div>
</template>

<script setup>
import UiIcon from '@/components/UiIcon.vue'
import MarkdownViewer from '@/components/MarkdownViewer.vue'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { downloadFile } from '@/utils/downloadFile'

const route = useRoute()
const router = useRouter()
const note = ref(null)
const loading = ref(true)
const errorMsg = ref('')
const fabOpen = ref(false)

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
  try { await downloadFile(`/api/note/${route.params.id}/pdf`, `${note.value?.title || '笔记'}.pdf`) }
  catch (e) { errorMsg.value = e.message; window.alert(e.message) }
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
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
.note-detail__download-count {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

/* Markdown 内容区 */
.note-detail__body {
  padding: var(--s6) var(--s7);
  line-height: 1.85;
  font-size: 0.9375rem;
  color: var(--ink-700);
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
  background: var(--primary);
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
  background: var(--primary-dark);
  transform: none;
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
  display: inline-flex;
  align-items: center;
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
}
</style>

<style scoped>
.note-detail__body { min-width: 0; overflow-wrap: anywhere; }
@media (max-width: 480px) { .note-detail__body { padding: 20px 16px; }.fab-menu { right: 18px; bottom: calc(18px + env(safe-area-inset-bottom)); } }
</style>

<style scoped>
.page.note-detail-page { max-width: 64rem; }
.note-detail-page .note-detail__header { padding: 1rem .5rem 2rem; }
.note-detail-page .note-detail__title { font-size: clamp(1.625rem, 2.5vw, 2.25rem); line-height: 1.5; max-width: 30em; }
.note-detail__meta > span { display: inline-flex; gap: .375rem; align-items: center; }
.note-detail__meta :deep(svg) { width: .875rem; height: .875rem; }
.note-detail-page .note-detail__body { padding: 2.25rem 3rem; font-size: 1rem; box-shadow: none; border-radius: 1rem; }
@media(max-width: 600px) { .note-detail-page .note-detail__body { padding: 1.25rem; font-size: .9375rem; }.note-detail__meta { gap: .75rem 1rem; }.fab-menu { right: 1rem; bottom: 1rem; } }
</style>
