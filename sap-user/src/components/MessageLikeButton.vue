<template>
  <span class="like-control">
    <button type="button" class="like-toggle" :class="{ 'is-liked': item.liked }" :disabled="pending" :aria-pressed="Boolean(item.liked)" :aria-label="item.liked ? '取消点赞' : '点赞'" @click="toggle">
      <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>
      <span>{{ pending ? '处理中…' : Number(item.likeCount) || 0 }}</span>
    </button><span v-if="error" class="like-error" role="alert">{{ error }}</span>
  </span>
</template>
<script setup>
import { ref } from 'vue'
import request from '@/utils/request'
const props = defineProps({ item: { type: Object, required: true }, targetType: { type: Number, required: true } })
const emit = defineEmits(['updated'])
const pending = ref(false), error = ref('')
async function toggle() {
  if (pending.value) return
  pending.value = true; error.value = ''
  try {
    const target = { targetType: props.targetType, targetId: props.item.id }
    const result = props.item.liked ? await request.delete('/api/message/like', { params: target }) : await request.post('/api/message/like', target)
    if (typeof result.data?.liked !== 'boolean' || !Number.isFinite(Number(result.data?.likeCount))) throw new Error('状态未能确认，请刷新后重试')
    emit('updated', { liked: result.data.liked, likeCount: Math.max(0, Number(result.data.likeCount)) })
  } catch (e) { error.value = e.message || '操作失败，请稍后重试' }
  finally { pending.value = false }
}
</script>
<style scoped>
.like-control{display:inline-flex;align-items:center;gap:9px;flex-wrap:wrap}.like-toggle{display:inline-flex;align-items:center;gap:6px;padding:7px 11px;min-height:34px;border:1px solid var(--border-blue);border-radius:8px;font-size:12px;color:var(--ink-500);background:#fff;transition:background .2s,color .2s}.like-toggle svg{fill:none;stroke:currentColor;stroke-width:1.7}.like-toggle:hover{background:var(--surface-blue)}.like-toggle.is-liked{color:#c84961;background:#fff0f3;border-color:#f5d6dd}.like-toggle.is-liked svg{fill:currentColor;animation:heart-pop .25s ease-out}.like-toggle:disabled{opacity:.6;cursor:wait}.like-toggle:focus-visible{outline:2px solid var(--primary);outline-offset:3px}.like-error{color:#b44243;font-size:11px}@keyframes heart-pop{50%{transform:scale(1.22)}}@media(prefers-reduced-motion:reduce){.like-toggle,.like-toggle.is-liked svg{animation:none;transition:none}}
</style>
