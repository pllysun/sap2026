<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">软协笔记</h1>
      <p class="page-desc">知识汇聚，笔记沉淀，让学习的痕迹成为前行的力量</p>
    </div>

    <div v-if="initialLoading" class="loading"><div class="loading__spinner"></div></div>

    <div v-else-if="notes.length === 0" class="empty">
      <div style="font-size:3rem; margin-bottom: var(--s3); opacity:0.4;">📝</div>
      <div class="empty__text">暂无笔记</div>
    </div>

    <!-- 搜索栏 -->
    <div v-if="!initialLoading && notes.length > 0 || keyword" class="note-search">
      <div class="note-search__input-wrap">
        <span class="note-search__icon">🔍</span>
        <input v-model="keyword" class="note-search__input" placeholder="搜索笔记标题…"
          @keyup.enter="doSearch" />
        <button v-if="keyword" class="note-search__clear" @click="keyword = ''; doSearch()">✕</button>
      </div>
    </div>

    <!-- 笔记卡片列表 -->
    <div class="note-grid" v-if="notes.length">
      <div
        v-for="(note, idx) in notes" :key="note.id"
        class="note-card anim-in"
        :class="{ 'note-card--guest': isGuest }"
        :style="{ animationDelay: (idx * 0.05) + 's' }"
        @click="goDetail(note)"
      >
        <div class="note-card__header">
          <div class="note-card__icon">📄</div>
          <div class="note-card__meta">
            <span class="note-card__author">{{ note.authorName }}</span>
            <span class="note-card__date">{{ formatDate(note.createdAt) }}</span>
          </div>
        </div>
        <h3 class="note-card__title">{{ note.title }}</h3>
        <p class="note-card__desc">{{ note.description || '暂无简介' }}</p>
        <div class="note-card__footer">
          <span class="note-card__stat">📝 {{ formatWordCount(note.wordCount) }}</span>
          <span class="note-card__stat">⏱ {{ note.readMinutes }} 分钟</span>
          <span class="note-card__stat">👁 {{ note.viewCount }}</span>
          <span class="note-card__stat">⬇ {{ note.downloadCount }}</span>
        </div>

        <!-- 游客遮罩 -->
        <div v-if="isGuest" class="note-card__guest-mask">
          <span class="note-card__guest-text">🔒 成为正式成员后可查看</span>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="note-paging" v-if="totalPages > 1">
      <button class="pagination__btn" :disabled="page <= 1" @click="page--; loadNotes()">‹</button>
      <button
        v-for="p in displayPages" :key="p"
        class="pagination__btn" :class="{ active: p === page }"
        @click="page = p; loadNotes()"
      >{{ p }}</button>
      <button class="pagination__btn" :disabled="page >= totalPages" @click="page++; loadNotes()">›</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const notes = ref([])
const page = ref(1)
const total = ref(0)
const pageSize = 12
const keyword = ref('')
const initialLoading = ref(true)

const isGuest = computed(() => {
  const roles = userStore.roles || []
  return roles.length === 0 || (roles.includes(4) && !roles.some(r => r <= 3))
})

const totalPages = computed(() => Math.ceil(total.value / pageSize))
const displayPages = computed(() => {
  const pages = []
  for (let i = Math.max(1, page.value - 2); i <= Math.min(totalPages.value, page.value + 2); i++) pages.push(i)
  return pages
})

const loadNotes = async () => {
  try {
    const params = { current: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    const res = await request.get('/api/note/list', { params })
    notes.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {}
}

const doSearch = () => {
  page.value = 1
  loadNotes()
}

const goDetail = (note) => {
  if (isGuest.value) return
  router.push(`/notes/${note.id}`)
}


const formatDate = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

const formatWordCount = (count) => {
  if (!count) return '0 字'
  if (count >= 10000) return (count / 10000).toFixed(1) + ' 万字'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k 字'
  return count + ' 字'
}

onMounted(async () => {
  await loadNotes()
  initialLoading.value = false
})
</script>

<style scoped>
/* 搜索栏 */
.note-search {
  margin-bottom: var(--s5);
  display: flex;
  justify-content: center;
}
.note-search__input-wrap {
  position: relative;
  width: 100%;
  max-width: 480px;
}
.note-search__icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 1rem;
  pointer-events: none;
}
.note-search__input {
  width: 100%;
  padding: 12px 40px 12px 42px;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-pill);
  font-size: 0.9rem;
  background: var(--bg-card);
  color: var(--ink-800);
  transition: all 0.2s;
  outline: none;
}
.note-search__input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(59,130,246,0.1);
}
.note-search__clear {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  font-size: 0.9rem;
  color: var(--ink-400);
  cursor: pointer;
  padding: 4px;
}

/* 笔记卡片网格 */
.note-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--s5);
}

.note-card {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--ink-100);
  border-radius: var(--r-lg);
  padding: var(--s6);
  cursor: pointer;
  transition: all 0.3s var(--ease);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 260px;
}
.note-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
  border-color: var(--primary-light, var(--ink-200));
}

.note-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--s3);
}
.note-card__icon {
  font-size: 1.6rem;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(20,184,166,0.1), rgba(59,130,246,0.1));
  border-radius: 10px;
  flex-shrink: 0;
}
.note-card__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 0.75rem;
  color: var(--ink-400);
}
.note-card__author {
  font-weight: 600;
  color: var(--ink-600);
}

.note-card__title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--ink-900);
  margin-bottom: var(--s3);
  line-height: 1.4;
}

.note-card__desc {
  font-size: 0.85rem;
  color: var(--ink-500);
  line-height: 1.7;
  flex: 1;
  margin-bottom: var(--s3);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-card__footer {
  display: flex;
  gap: var(--s4);
  padding-top: var(--s3);
  border-top: 1px solid var(--ink-100);
}
.note-card__stat {
  font-size: 0.75rem;
  color: var(--ink-400);
  display: flex;
  align-items: center;
  gap: 4px;
}

.note-card--guest {
  cursor: not-allowed;
}
.note-card--guest:hover {
  transform: none;
}

/* 游客遮罩 */
.note-card__guest-mask {
  position: absolute;
  inset: 0;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-lg);
  opacity: 0;
  transition: opacity 0.25s;
}
.note-card--guest:hover .note-card__guest-mask {
  opacity: 1;
}
.note-card__guest-text {
  background: var(--bg-card);
  padding: 10px 20px;
  border-radius: var(--r-pill);
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--ink-600);
  box-shadow: var(--shadow-md);
}

/* 分页 */
.note-paging {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-top: var(--s6);
}

/* 响应式 */
@media (max-width: 768px) {
  .note-grid { grid-template-columns: 1fr; gap: var(--s3); }
  .note-card { padding: var(--s4); min-height: 200px; }
}
</style>
