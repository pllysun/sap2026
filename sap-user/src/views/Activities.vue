<template>
  <div class="page">
    <PageHeader title="软协活动" label="OUR MOMENTS / 协会日常" description="一起探索、一起实践，把值得记住的瞬间留下。" />

    <div v-if="initialLoading" class="loading"><div class="loading__spinner"></div></div>

    <div v-else-if="activities.length === 0" class="empty">
      <div style="font-size:3rem; margin-bottom: var(--s3); opacity:0.4;">📅</div>
      <div class="empty__text">暂无活动记录</div>
    </div>

    <!-- Activities Grid -->
    <div v-else class="act-grid">
      <!-- Featured (first activity) -->
      <div
        v-if="activities.length > 0"
        class="act-featured anim-in"
        role="button" tabindex="0" :aria-label="'查看活动：' + activities[0].title"
        @keydown.enter.prevent="openDetail(activities[0])" @keydown.space.prevent="openDetail(activities[0])"
        @click="openDetail(activities[0])"
      >
        <div class="act-featured__img-wrap">
          <img
            v-if="activities[0].images && activities[0].images.length"
            :src="activities[0].images[0].imageUrl"
            alt=""
            class="act-featured__img"
          />
          <div v-else class="act-featured__img-placeholder">📸</div>
          <div class="act-featured__overlay">
            <span class="badge badge--gradient">{{ activities[0].grade }}</span>
          </div>
        </div>
        <div class="act-featured__body">
          <h2 class="act-featured__title">{{ activities[0].title }}</h2>
          <p class="act-featured__desc" v-if="activities[0].content">{{ activities[0].content }}</p>
          <p class="act-featured__desc act-featured__desc--empty" v-else>暂无描述，等待补充精彩记忆。</p>
          <div class="act-featured__meta">
            <span>📅 {{ formatTime(activities[0].createdAt) }}</span>
            <span v-if="activities[0].images && activities[0].images.length > 1">📷 {{ activities[0].images.length }} 张照片</span>
          </div>
        </div>
      </div>

      <!-- Rest in masonry-like cards -->
      <div class="act-cards">
        <div
          v-for="(act, idx) in activities.slice(1)" :key="act.id"
          class="act-card anim-in"
          role="button" tabindex="0" :aria-label="'查看活动：' + act.title"
          @keydown.enter.prevent="openDetail(act)" @keydown.space.prevent="openDetail(act)"
          :style="{ animationDelay: ((idx + 1) * 0.06) + 's' }"
          @click="openDetail(act)"
        >
          <div class="act-card__img-wrap">
            <img
              v-if="act.images && act.images.length"
              :src="act.images[0].imageUrl"
              alt=""
              class="act-card__img"
            />
            <div v-else class="act-card__img-placeholder">📸</div>
            <span class="act-card__badge">{{ act.grade }}</span>
            <div v-if="act.images && act.images.length > 1" class="act-card__count">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 15l5-5 4 4 4-4 5 5"/></svg>
              {{ act.images.length }}
            </div>
          </div>
          <div class="act-card__body">
            <h3 class="act-card__title">{{ act.title }}</h3>
            <p class="act-card__desc" v-if="act.content">{{ act.content }}</p>
            <div class="act-card__footer">
              <span class="t-caption">📅 {{ formatTime(act.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Load more -->
    <div class="load-more-area" ref="loadMoreRef">
      <div v-if="loadingMore" class="loading" style="padding: 24px 0;"><div class="loading__spinner"></div></div>
      <div v-else-if="noMore && activities.length > 0" class="t-caption" style="text-align: center; padding: 24px 0; color: var(--ink-400);">已加载全部活动 ✨</div>
    </div>

    <!-- Detail Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="detailAct" class="act-modal-overlay" @click.self="detailAct = null">
          <div ref="activityDialog" class="act-modal" role="dialog" aria-modal="true" aria-labelledby="activity-detail-title" tabindex="-1">
            <button class="act-modal__close" aria-label="关闭活动详情" @click="detailAct = null">✕</button>

            <!-- Image Carousel -->
            <div class="act-modal__carousel" v-if="detailAct.images && detailAct.images.length">
              <div class="act-modal__carousel-track" :style="{ transform: `translateX(-${carouselIdx * 100}%)` }">
                <div v-for="(img, i) in detailAct.images" :key="i" class="act-modal__slide">
                  <img :src="img.imageUrl" :alt="`${detailAct.title} · 照片 ${i + 1}`" role="button" tabindex="0" @click="previewImg = img.imageUrl" @keydown.enter.prevent="previewImg = img.imageUrl" />
                </div>
              </div>
              <template v-if="detailAct.images.length > 1">
                <button class="act-modal__nav act-modal__nav--prev" aria-label="上一张活动照片" @click="carouselIdx = Math.max(0, carouselIdx - 1)" :disabled="carouselIdx === 0"><UiIcon name="chevron-left" :size="22" /></button>
                <button class="act-modal__nav act-modal__nav--next" aria-label="下一张活动照片" @click="carouselIdx = Math.min(detailAct.images.length - 1, carouselIdx + 1)" :disabled="carouselIdx === detailAct.images.length - 1"><UiIcon name="chevron-right" :size="22" /></button>
                <div class="act-modal__dots">
                  <span v-for="(_, i) in detailAct.images" :key="i" class="act-modal__dot" :class="{ active: i === carouselIdx }" @click="carouselIdx = i"></span>
                </div>
              </template>
            </div>
            <div v-else class="act-modal__no-img">
              <span style="font-size: 3rem; opacity: 0.3;">📷</span>
              <span class="t-caption">暂无活动照片</span>
            </div>

            <!-- Detail Content -->
            <div class="act-modal__body">
              <div class="flex-between mb-2">
                <h2 id="activity-detail-title" class="act-modal__title">{{ detailAct.title }}</h2>
                <span class="badge badge--primary">{{ detailAct.grade }}</span>
              </div>
              <div class="act-modal__info">
                <span>📅 {{ formatTime(detailAct.createdAt) }}</span>
                <span v-if="detailAct.images && detailAct.images.length">📷 {{ detailAct.images.length }} 张照片</span>
              </div>
              <div class="act-modal__content" v-if="detailAct.content">{{ detailAct.content }}</div>
              <div class="act-modal__content act-modal__content--empty" v-else>暂无活动描述。</div>

              <!-- Thumbnail strip -->
              <div v-if="detailAct.images && detailAct.images.length > 1" class="act-modal__thumbs">
                <img
                  v-for="(img, i) in detailAct.images" :key="i"
                  :src="img.imageUrl" alt=""
                  class="act-modal__thumb"
                  :class="{ active: i === carouselIdx }"
                  @click="carouselIdx = i"
                />
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Full Image Preview -->
    <Teleport to="body">
      <div v-if="previewImg" ref="imageDialog" class="act-preview-overlay" role="dialog" aria-modal="true" aria-label="活动照片预览" tabindex="-1" @click.self="previewImg = null">
        <button class="preview-close" aria-label="关闭照片预览" @click="previewImg = null">×</button>
        <img :src="previewImg" class="act-preview-img" alt="活动照片大图" />
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import UiIcon from '@/components/UiIcon.vue'
import PageHeader from '@/components/PageHeader.vue'
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import request from '@/utils/request'
import { useDialog } from '@/utils/useDialog'

const activities = ref([])
const initialLoading = ref(true)
const loadingMore = ref(false)
const previewImg = ref(null)
const detailAct = ref(null)
const activityDialog = ref(null), imageDialog = ref(null)
useDialog(computed(() => !!detailAct.value), activityDialog, () => { detailAct.value = null })
useDialog(computed(() => !!previewImg.value), imageDialog, () => { previewImg.value = null })
const carouselIdx = ref(0)
const loadMoreRef = ref(null)

const currentPage = ref(1)
const pageSize = 9
const totalPages = ref(1)
const noMore = ref(false)

let observer = null

function openDetail(act) {
  detailAct.value = act
  carouselIdx.value = 0
}

async function loadPage() {
  if (loadingMore.value || noMore.value) return
  loadingMore.value = true
  try {
    const r = await request.get('/api/activity/page', { params: { current: currentPage.value, size: pageSize } })
    const data = r.data || {}
    const records = data.records || []
    activities.value.push(...records)
    totalPages.value = data.pages || 1
    if (currentPage.value >= totalPages.value || records.length === 0) {
      noMore.value = true
    } else {
      currentPage.value++
    }
  } catch {}
  finally { loadingMore.value = false }
}

function setupObserver() {
  if (!loadMoreRef.value) return
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && !loadingMore.value && !noMore.value) {
      loadPage()
    }
  }, { rootMargin: '200px' })
  observer.observe(loadMoreRef.value)
}

onMounted(async () => {
  await loadPage()
  initialLoading.value = false
  await nextTick()
  setupObserver()
})

onUnmounted(() => {
  if (observer) observer.disconnect()
})

function formatTime(t) { return t ? new Date(t).toLocaleDateString('zh-CN') : '' }
</script>

<style scoped>
/* ---- Featured Card ---- */
.act-featured {
  margin-bottom: var(--s6);
  border-radius: var(--r-xl);
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--ink-100);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  min-height: 320px;
  transition: box-shadow 0.3s var(--ease), transform 0.3s var(--ease);
}
.act-featured:hover {
  box-shadow: var(--shadow-xl);
  transform: translateY(-3px);
}
.act-featured__img-wrap {
  position: relative;
  overflow: hidden;
}
.act-featured__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s var(--ease);
}
.act-featured:hover .act-featured__img {
  transform: scale(1.05);
}
.act-featured__img-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 4rem;
  background: var(--gradient-soft);
  opacity: 0.5;
}
.act-featured__overlay {
  position: absolute;
  top: var(--s4);
  left: var(--s4);
}
.act-featured__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--s7) var(--s6);
}
.act-featured__title {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--ink-900);
  margin-bottom: var(--s3);
  line-height: 1.3;
  letter-spacing: -0.02em;
}
.act-featured__desc {
  font-size: 0.9375rem;
  color: var(--ink-600);
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: var(--s5);
}
.act-featured__desc--empty {
  color: var(--ink-400);
  font-style: italic;
}
.act-featured__meta {
  display: flex;
  align-items: center;
  gap: var(--s4);
  font-size: 0.8125rem;
  color: var(--ink-400);
}

/* ---- Cards Grid ---- */
.act-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--s5);
}

.act-card {
  background: var(--bg-card);
  border: 1px solid var(--ink-100);
  border-radius: var(--r-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: box-shadow 0.3s var(--ease), transform 0.3s var(--ease);
  display: flex;
  flex-direction: column;
}
.act-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
}

.act-card__img-wrap {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: var(--ink-50);
}
.act-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease);
}
.act-card:hover .act-card__img {
  transform: scale(1.06);
}
.act-card__img-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.5rem;
  background: var(--gradient-soft);
  opacity: 0.4;
}
.act-card__badge {
  position: absolute;
  top: var(--s3);
  right: var(--s3);
  padding: 3px 10px;
  font-size: 0.7rem;
  font-weight: 700;
  border-radius: var(--r-pill);
  background: rgba(255,255,255,0.9);
  color: var(--primary);
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.act-card__count {
  position: absolute;
  bottom: var(--s3);
  right: var(--s3);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 0.75rem;
  font-weight: 600;
  color: #fff;
  background: rgba(0,0,0,0.5);
  backdrop-filter: blur(8px);
  border-radius: var(--r-pill);
}

.act-card__body {
  padding: var(--s4) var(--s4) var(--s3);
  flex: 1;
  display: flex;
  flex-direction: column;
}
.act-card__title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--ink-900);
  margin-bottom: var(--s2);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.act-card__desc {
  font-size: 0.8125rem;
  color: var(--ink-500);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: auto;
}
.act-card__footer {
  margin-top: var(--s3);
  padding-top: var(--s3);
  border-top: 1px solid var(--ink-100);
}

/* ---- Detail Modal ---- */
.act-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.55);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--s5);
}
.act-modal {
  background: var(--bg-card);
  border-radius: var(--r-xl);
  width: 100%;
  max-width: 720px;
  max-height: 88vh;
  overflow-y: auto;
  box-shadow: 0 24px 80px rgba(0,0,0,0.2);
  position: relative;
  animation: modalSlideIn 0.35s var(--ease) both;
}
@keyframes modalSlideIn {
  from { opacity: 0; transform: translateY(24px) scale(0.96); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
.act-modal__close {
  position: absolute;
  top: var(--s3);
  right: var(--s3);
  z-index: 10;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(8px);
  border: 1px solid var(--ink-200);
  font-size: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--dur);
  color: var(--ink-600);
  box-shadow: var(--shadow-sm);
}
.act-modal__close:hover {
  background: var(--white);
  color: var(--ink-900);
  box-shadow: var(--shadow-md);
}

/* Carousel */
.act-modal__carousel {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  background: var(--ink-900);
}
.act-modal__carousel-track {
  display: flex;
  height: 100%;
  transition: transform 0.4s var(--ease);
}
.act-modal__slide {
  min-width: 100%;
  height: 100%;
}
.act-modal__slide img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  cursor: zoom-in;
}
.act-modal__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(8px);
  border: none;
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--ink-700);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.act-modal__nav:hover { background: #fff; box-shadow: 0 4px 16px rgba(0,0,0,0.2); }
.act-modal__nav:disabled { opacity: 0.3; cursor: not-allowed; }
.act-modal__nav--prev { left: var(--s3); }
.act-modal__nav--next { right: var(--s3); }

.act-modal__dots {
  position: absolute;
  bottom: var(--s3);
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 6px;
}
.act-modal__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255,255,255,0.4);
  cursor: pointer;
  transition: all 0.2s;
}
.act-modal__dot.active {
  background: #fff;
  width: 20px;
  border-radius: 4px;
}

.act-modal__no-img {
  aspect-ratio: 16 / 9;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--s2);
  background: var(--gradient-soft);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
}

.act-modal__body {
  padding: var(--s5) var(--s6);
}
.act-modal__title {
  font-size: 1.35rem;
  font-weight: 800;
  color: var(--ink-900);
  line-height: 1.3;
}
.act-modal__info {
  display: flex;
  align-items: center;
  gap: var(--s4);
  font-size: 0.8125rem;
  color: var(--ink-400);
  margin-bottom: var(--s4);
}
.act-modal__content {
  font-size: 0.9375rem;
  color: var(--ink-600);
  line-height: 1.8;
  white-space: pre-wrap;
}
.act-modal__content--empty {
  color: var(--ink-400);
  font-style: italic;
}

.act-modal__thumbs {
  display: flex;
  gap: var(--s2);
  margin-top: var(--s5);
  overflow-x: auto;
  padding-bottom: var(--s2);
}
.act-modal__thumb {
  width: 64px;
  height: 48px;
  object-fit: cover;
  border-radius: var(--r-sm);
  cursor: pointer;
  opacity: 0.5;
  border: 2px solid transparent;
  transition: all 0.2s;
  flex-shrink: 0;
}
.act-modal__thumb.active {
  opacity: 1;
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(59,130,246,0.2);
}
.act-modal__thumb:hover {
  opacity: 0.85;
}

/* ---- Full Preview ---- */
.act-preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  cursor: zoom-out;
  animation: fadeIn 0.2s var(--ease);
}
.act-preview-img {
  max-width: 92%;
  max-height: 90vh;
  border-radius: 8px;
  box-shadow: 0 20px 80px rgba(0,0,0,0.4);
}

/* ---- Load More ---- */
.load-more-area {
  min-height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---- Modal Transitions ---- */
.modal-enter-active { animation: modalSlideIn 0.35s var(--ease); }
.modal-leave-active { animation: modalSlideIn 0.25s var(--ease) reverse; }

/* ---- Responsive ---- */
@media (max-width: 1024px) {
  .act-cards { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .act-featured {
    grid-template-columns: 1fr;
    min-height: auto;
  }
  .act-featured__img-wrap {
    aspect-ratio: 16 / 9;
  }
  .act-featured__body {
    padding: var(--s5) var(--s4);
  }
  .act-featured__title {
    font-size: 1.2rem;
  }
  .act-cards { grid-template-columns: repeat(2, 1fr); gap: var(--s3); }
  .act-modal {
    max-width: 100%;
    max-height: 95vh;
    border-radius: var(--r-lg);
    margin: var(--s3);
  }
  .act-modal__body { padding: var(--s4); }
}
@media (max-width: 480px) {
  .act-cards { grid-template-columns: 1fr; }
  .act-card__img-wrap { aspect-ratio: 16 / 9; }
}
</style>

<style scoped>
.act-modal-overlay { padding: 24px; }.act-modal { max-height: calc(100dvh - 48px); overscroll-behavior: contain; border: 1px solid var(--border-blue); border-radius: 18px; }.act-modal__title, .act-modal__content { overflow-wrap: anywhere; }.act-modal__title { padding-right: 22px; }.preview-close { position: absolute; right: max(18px, env(safe-area-inset-right)); top: max(18px, env(safe-area-inset-top)); width: 44px; height: 44px; font-size: 28px; border-radius: 50%; color: #fff; background: #ffffff26; z-index: 2; }.act-preview-img { object-fit: contain; max-height: calc(100dvh - 110px); }
@media (max-width: 600px) { .act-modal-overlay { padding: 12px; }.act-modal { margin: 0; max-height: calc(100dvh - 24px); }.act-modal__body { padding: 20px; }.act-modal__title { font-size: 20px; }.act-modal__carousel { height: auto; aspect-ratio: 4/3; }.act-modal__slide img { height: 100%; object-fit: contain; }.act-modal__info { flex-wrap: wrap; }.act-modal__thumbs { padding-bottom: 6px; } }
</style>
