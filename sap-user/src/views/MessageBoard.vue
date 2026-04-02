<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">留言板</h1>
      <p class="page-desc">分享你的想法，与协会成员交流互动</p>
    </div>

    <!-- Post -->
    <div v-if="!isGuest" class="card mb-4 anim-in">
      <div class="form-group">
        <textarea v-model="newContent" class="input textarea" placeholder="写下你的想法…" rows="3"></textarea>
      </div>
      <div class="flex-between">
        <span v-if="postError" class="error-text">{{ postError }}</span>
        <span v-else></span>
        <button class="btn btn--primary btn--sm btn--pill" @click="postMessage" :disabled="posting || !newContent.trim()">
          {{ posting ? '发布中…' : '📝 发布' }}
        </button>
      </div>
    </div>
    <div v-else class="card mb-4 anim-in" style="text-align:center;padding:20px;color:#999;">
      🔒 游客无法发布留言，请先加入协会
    </div>

    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <!-- Messages -->
    <div v-if="messages.length > 0">
      <div v-for="(msg, idx) in messages" :key="msg.id" class="card mb-3 anim-in" :style="{ animationDelay: (idx * 0.03) + 's' }">
        <div class="message__header">
          <img :src="msg.avatar || '/default-avatar.png'" class="message__avatar" />
          <span class="message__name">{{ msg.userName }}</span>
          <span class="message__time">{{ formatTime(msg.createdAt) }}</span>
        </div>

        <div class="message__content">{{ msg.content }}</div>

        <div class="message__actions">
          <span class="like-btn" :class="{ liked: msg.liked }" @click="toggleLike(msg, 0)">
            {{ msg.liked ? '♥' : '♡' }} {{ msg.likeCount || 0 }}
          </span>
          <span class="like-btn" @click="toggleReplyForm(msg.id)">💬 回复</span>
        </div>

        <!-- Reply input -->
        <div v-if="replyingTo === msg.id" class="mt-2 flex gap-1" style="padding-left: 36px;">
          <input v-model="replyContent" class="input" placeholder="写下回复…" @keyup.enter="submitReply(msg.id)" style="flex:1;" />
          <button class="btn btn--primary btn--sm btn--pill" @click="submitReply(msg.id)" :disabled="!replyContent.trim()">发送</button>
        </div>

        <!-- Replies with collapse -->
        <div v-if="msg.replies && msg.replies.length > 0" class="reply-zone mt-2">
          <div v-for="(reply, rIdx) in visibleReplies(msg)" :key="reply.id" style="margin-bottom: 12px;">
            <div class="flex gap-1" style="align-items: center;">
              <img :src="reply.avatar || '/default-avatar.png'" style="width:20px;height:20px;border-radius:50%;" />
              <span class="t-caption" style="font-weight:500;color:var(--text-primary);">{{ reply.userName }}</span>
              <span class="t-caption">{{ formatTime(reply.createdAt) }}</span>
            </div>
            <div class="t-body" style="margin: 2px 0 2px 28px; font-size: 0.875rem;">{{ reply.content }}</div>
            <div style="margin-left: 28px;">
              <span class="like-btn" :class="{ liked: reply.liked }" @click="toggleLike(reply, 1)" style="font-size:0.75rem;">
                {{ reply.liked ? '♥' : '♡' }} {{ reply.likeCount || 0 }}
              </span>
            </div>
          </div>
          <!-- Collapse / Expand button -->
          <button
            v-if="msg.replies.length > 3"
            class="btn btn--ghost btn--sm"
            style="width: 100%; margin-top: 4px; font-size: 0.8rem;"
            @click="toggleExpandReplies(msg.id)"
          >
            {{ expandedReplies[msg.id]
              ? '收起回复 ▲'
              : `展开剩余 ${msg.replies.length - 3} 条回复 ▼` }}
          </button>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div class="flex-between mb-4 anim-in" v-if="total > 0" style="flex-wrap: wrap; gap: var(--s3);">
      <div class="flex gap-1" style="align-items: center;">
        <span class="t-caption">每页</span>
        <select v-model.number="pageSize" class="select" style="width: auto; min-width: 70px; padding: 4px 8px; font-size: 0.8rem;" @change="currentPage = 1; loadMessages()">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
        <span class="t-caption">条 · 共 {{ total }} 条留言</span>
      </div>
      <div class="pagination" v-if="total > pageSize">
        <button class="pagination__btn" :disabled="currentPage <= 1" @click="currentPage--; loadMessages()">‹</button>
        <button v-for="p in displayPages" :key="p"
          class="pagination__btn" :class="{ active: p === currentPage }"
          @click="currentPage = p; loadMessages()">{{ p }}</button>
        <button class="pagination__btn" :disabled="currentPage >= totalPages" @click="currentPage++; loadMessages()">›</button>
      </div>
    </div>

    <div v-if="!loading && messages.length === 0" class="empty">
      <div style="font-size:3rem; margin-bottom: var(--s3); opacity:0.4;">💬</div>
      <div class="empty__text">还没有留言，来写下第一条吧</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isGuest = computed(() => {
  const roles = userStore.roles || []
  return roles.length === 0 || (roles.includes(4) && !roles.some(r => r <= 3))
})

const messages = ref([])
const loading = ref(true)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const newContent = ref('')
const posting = ref(false)
const postError = ref('')
const replyingTo = ref(null)
const replyContent = ref('')
const expandedReplies = reactive({})

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
const displayPages = computed(() => {
  const pages = []
  for (let i = Math.max(1, currentPage.value - 2); i <= Math.min(totalPages.value, currentPage.value + 2); i++) pages.push(i)
  return pages
})

function visibleReplies(msg) {
  if (!msg.replies) return []
  if (msg.replies.length <= 3 || expandedReplies[msg.id]) return msg.replies
  return msg.replies.slice(0, 3)
}

function toggleExpandReplies(msgId) {
  expandedReplies[msgId] = !expandedReplies[msgId]
}

onMounted(() => loadMessages())

async function loadMessages() {
  loading.value = true
  try {
    const r = await request.get('/api/message/list', { params: { current: currentPage.value, size: pageSize.value } })
    messages.value = r.data.records || []; total.value = r.data.total || 0
  } catch {} finally { loading.value = false }
}

async function postMessage() {
  if (!newContent.value.trim()) return
  posting.value = true; postError.value = ''
  try { await request.post('/api/message', { content: newContent.value }); newContent.value = ''; currentPage.value = 1; await loadMessages() }
  catch (e) { postError.value = e.message || '发布失败' }
  finally { posting.value = false }
}

function toggleReplyForm(id) {
  replyingTo.value = replyingTo.value === id ? null : id
  replyContent.value = ''
}

async function submitReply(msgId) {
  if (!replyContent.value.trim()) return
  try { await request.post(`/api/message/${msgId}/reply`, { content: replyContent.value }); replyingTo.value = null; replyContent.value = ''; await loadMessages() } catch {}
}

async function toggleLike(item, targetType) {
  try {
    if (item.liked) { await request.delete('/api/message/like', { params: { targetType, targetId: item.id } }); item.liked = false; item.likeCount = Math.max(0, (item.likeCount || 0) - 1) }
    else { await request.post('/api/message/like', { targetType, targetId: item.id }); item.liked = true; item.likeCount = (item.likeCount || 0) + 1 }
  } catch {}
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t), now = new Date(), diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前'
  return d.toLocaleDateString('zh-CN')
}
</script>
