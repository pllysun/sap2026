<template>
  <div class="page message-board">
    <PageHeader title="留言板" label="COMMUNITY / 交流空间" description="一个问题、一点灵感，或一句想对伙伴说的话。" />

    <!-- Post -->
    <div v-if="!isGuest" class="card message-composer mb-4 anim-in">
      <h2><UiIcon name="edit" :size="18" /> 留下你的想法</h2>
      <div class="form-group">
        <textarea v-model="newContent" class="input textarea" aria-label="留言内容" placeholder="分享学习心得，或发起一个讨论…" rows="3"></textarea>
      </div>
      <div class="flex-between">
        <span v-if="postError" class="error-text">{{ postError }}</span>
        <span v-else></span>
        <button class="btn btn--primary btn--sm btn--pill" @click="postMessage" :disabled="posting || !newContent.trim()">
          <UiIcon name="send" :size="15" />{{ posting ? '发布中…' : '发布' }}
        </button>
      </div>
    </div>
    <div v-else class="card message-guest mb-4 anim-in">
      <UiIcon name="lock" :size="18" /> 加入协会后即可参与讨论 <router-link to="/join">申请加入 <UiIcon name="arrow-right" :size="14" /></router-link>
    </div>

    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <!-- Messages -->
    <div v-if="messages.length > 0" class="message-feed">
      <div class="message-feed__heading"><h2>最近的讨论</h2><span>{{ total }} 条留言</span></div>
      <div v-for="(msg, idx) in messages" :key="msg.id" class="card message-entry mb-3 anim-in" :style="{ animationDelay: (idx * 0.03) + 's' }">
        <div class="message__header">
          <UserAvatar :src="msg.avatar" :name="authorName(msg)" />
          <span class="message__name">{{ authorName(msg) }}</span>
          <span class="message__time">{{ formatTime(msg.createdAt) }}</span>
        </div>

        <div class="message__content">{{ msg.content }}</div>

        <div class="message__actions">
          <MessageLikeButton :item="msg" :target-type="0" @updated="Object.assign(msg, $event)" />
          <button v-if="!isGuest" class="reply-button" @click="toggleReplyForm(msg.id)" :aria-expanded="replyingTo === msg.id"><UiIcon name="message-square" :size="14" /> 回复 <span v-if="msg.replies?.length">{{ msg.replies.length }}</span></button>
        </div>

        <!-- Reply input -->
        <div v-if="replyingTo === msg.id" class="message-reply-input mt-2 flex gap-1">
          <input v-model="replyContent" class="input" aria-label="回复内容" placeholder="写下回复…" @keyup.enter="submitReply(msg.id)" style="flex:1;" />
          <button class="btn btn--primary btn--sm btn--pill" @click="submitReply(msg.id)" :disabled="replying || !replyContent.trim()">{{ replying ? '发送中…' : '发送' }}</button>
        </div>
        <p v-if="replyingTo === msg.id && replyError" class="error-text" role="alert">{{ replyError }}</p>

        <!-- Replies with collapse -->
        <div v-if="msg.replies && msg.replies.length > 0" class="reply-zone mt-2">
          <div v-for="(reply, rIdx) in visibleReplies(msg)" :key="reply.id" style="margin-bottom: 12px;">
            <div class="flex gap-1" style="align-items: center;">
              <UserAvatar :src="reply.avatar" :name="authorName(reply)" :size="24" />
              <span class="t-caption" style="font-weight:500;color:var(--ink-800);">{{ authorName(reply) }}</span>
              <span class="t-caption">{{ formatTime(reply.createdAt) }}</span>
            </div>
            <div class="t-body" style="margin: 2px 0 2px 28px; font-size: 0.875rem;">{{ reply.content }}</div>
            <div style="margin-left: 28px;">
              <MessageLikeButton :item="reply" :target-type="1" @updated="Object.assign(reply, $event)" />
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

    <p v-if="loadError" class="error-text" role="alert">{{ loadError }} <button @click="loadMessages">重新加载</button></p>
    <div v-if="!loading && !loadError && messages.length === 0" class="empty">
      <div class="empty-icon"><UiIcon name="message-square" :size="24" /></div>
      <div class="empty__text">还没有留言，来写下第一条吧</div>
    </div>
  </div>
</template>

<script setup>
import UiIcon from '@/components/UiIcon.vue'
import PageHeader from '@/components/PageHeader.vue'
import { ref, computed, reactive, onMounted } from 'vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/UserAvatar.vue'
import MessageLikeButton from '@/components/MessageLikeButton.vue'

const userStore = useUserStore()
const isGuest = computed(() => {
  const roles = userStore.roles || []
  return !roles.some(r => [0, 1, 2, 3].includes(Number(r)))
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
const replying = ref(false)
const replyError = ref('')
const loadError = ref('')
let loadGeneration = 0
const authorName = item => item.userName?.trim() || '软协同学'
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
  const generation = ++loadGeneration
  loading.value = true
  loadError.value = ''
  try {
    const r = await request.get('/api/message/list', { params: { current: currentPage.value, size: pageSize.value } })
    if (generation !== loadGeneration) return
    messages.value = r.data.records || []; total.value = r.data.total || 0
  } catch (e) { if (generation === loadGeneration) loadError.value = e.message || '留言加载失败' }
  finally { if (generation === loadGeneration) loading.value = false }
}

async function postMessage() {
  if (posting.value || !newContent.value.trim()) return
  posting.value = true; postError.value = ''
  try { await request.post('/api/message', { content: newContent.value }); newContent.value = ''; currentPage.value = 1; await loadMessages() }
  catch (e) { postError.value = e.message || '发布失败' }
  finally { posting.value = false }
}

function toggleReplyForm(id) {
  if (replying.value) return
  replyingTo.value = replyingTo.value === id ? null : id
  replyContent.value = ''
  replyError.value = ''
}

async function submitReply(msgId) {
  if (replying.value || !replyContent.value.trim()) return
  replying.value = true; replyError.value = ''
  try { await request.post(`/api/message/${msgId}/reply`, { content: replyContent.value }); replyingTo.value = null; replyContent.value = ''; await loadMessages() }
  catch (e) { replyError.value = e.message || '回复失败，请稍后重试' }
  finally { replying.value = false }
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

<style scoped>
.message-board{max-width:1120px}.message-board .page-header{text-align:left;margin-bottom:30px}.message-board .page-title{color:#202e3b;font-size:2rem}.message-board .page-title:after{display:none}.message-board .card{box-shadow:none;border:1px solid #dee4ea;border-radius:14px}.message__header{gap:12px}.message__name{color:#26323d}.message__content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;padding:6px 0}.message__actions{gap:15px;align-items:center}.reply-button{color:#536a82;font-size:0.75rem;padding:8px 11px;border-radius:8px;min-height:34px}.reply-button:hover{background:#e9eef3}.reply-zone{background:#f3f6f9;border-left:2px solid #bbcbda;border-radius:0 9px 9px 0;padding:15px}.reply-zone .t-body{white-space:pre-wrap;overflow-wrap:anywhere}.reply-button:focus-visible{outline:2px solid #39638d;outline-offset:2px}
</style>

<style scoped>
.page.message-board { max-width: 66rem; }
.message-board .message-composer { border-color: #cbd4eb; border-top: 3px solid #7b8ac4; padding: 1.5rem; }
.message-composer h2 { display: flex; align-items: center; gap: .5rem; font-size: 1rem; margin-bottom: 1rem; }
.message-composer textarea { background: #f8f9fc; min-height: 7rem; resize: vertical; }
.message-guest { display: flex; align-items: center; gap: .625rem; color: var(--ink-600); flex-wrap: wrap; font-size: .875rem; }
.message-guest a { display: flex; align-items: center; gap: .5rem; color: var(--primary); font-weight: 600; margin-left: auto; }
.message-feed__heading { display: flex; align-items: center; justify-content: space-between; margin: 2rem 0 1rem; }
.message-feed__heading h2 { font-size: 1.125rem; }.message-feed__heading span { font-size: .8125rem; color: var(--ink-500); }
.message-board .message-entry { padding: 1.5rem; }
.message__name { font-size: .9375rem; }
.message__content { font-size: 1rem; margin: .75rem 0; }
.reply-button { display: inline-flex; gap: .375rem; align-items: center; font-size: .8125rem; }
.message-reply-input { padding-left: 2.75rem; }.message-reply-input .input { min-width: 0; }
.reply-zone { background: #f4f5f9; border-color: #c8d0e5; margin-left: 2.75rem; }
@media(max-width: 600px) { .message-board .message-entry { padding: 1.125rem; }.message__header { flex-wrap: wrap; row-gap: .25rem; }.message__time { font-size: .75rem; }.message__content { font-size: .9375rem; }.reply-zone { margin-left: 0; }.message-reply-input { padding-left: 0; }.message-guest a { margin-left: 0; } }
</style>
