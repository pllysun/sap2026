<template>
  <Teleport to="body">
    <dialog ref="element" class="oj-workspace-dialog oj-studio-modal" aria-labelledby="oj-workspace-dialog-title"
      @cancel.prevent="emit('close')" @click="backdropClick" @keydown.tab="trapFocus">
      <header class="oj-dialog-header">
        <div class="oj-dialog-heading">
          <span class="oj-dialog-symbol"><UiIcon :name="icon" :size="16" /></span>
          <div><h2 id="oj-workspace-dialog-title">{{ title }}</h2><p>{{ context }}</p></div>
        </div>
        <button class="oj-dialog-close" aria-label="关闭弹窗" @click="emit('close')"><UiIcon name="close" :size="16" /></button>
      </header>
      <slot name="navigation" />
      <div ref="body" class="oj-dialog-body"><slot /></div>
    </dialog>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import UiIcon from './UiIcon.vue'

const props = defineProps({ open: Boolean, title: String, context: String, view: String, icon: { type: String, default: 'terminal' } })
const emit = defineEmits(['close'])
const element = ref(null)
const body = ref(null), scrollPositions = new Map()
let originalOverflow, locked = false

watch(() => props.view, async (view, previous) => {
  if (previous && body.value) scrollPositions.set(previous, body.value.scrollTop)
  await nextTick()
  if (body.value && props.view === view) body.value.scrollTop = scrollPositions.get(view) || 0
})

function unlock() {
  if (locked) { document.body.style.overflow = originalOverflow; locked = false }
}
watch(() => props.open, open => {
  if (open && !element.value.open) {
    originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    locked = true
    element.value.showModal()
  } else if (!open) { element.value?.close(); unlock() }
}, { flush: 'post' })
function backdropClick(event) {
  if (event.target !== element.value) return
  const box = element.value.getBoundingClientRect()
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) emit('close')
}
function trapFocus(event) {
  const items=[...element.value.querySelectorAll('a[href],button:not(:disabled),input:not(:disabled),textarea:not(:disabled),[tabindex="0"]')].filter(item=>item.getClientRects().length)
  const first=items[0],last=items.at(-1)
  if(!first){event.preventDefault();element.value.focus();return}
  if(event.shiftKey && document.activeElement===first){event.preventDefault();last.focus()}
  else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first.focus()}
}
onBeforeUnmount(() => { element.value?.close(); unlock() })
</script>

<style scoped>
.oj-workspace-dialog{width:min(980px,calc(100vw - 48px));height:min(740px,calc(100dvh - 48px));max-width:none;max-height:none;box-sizing:border-box;margin:auto;padding:0;border:1px solid #d9e5f3;border-radius:14px;box-shadow:0 24px 90px #163d6533;color:#29455f;background:#fff;overflow:hidden;font-family:inherit}
.oj-workspace-dialog[open]{display:flex;flex-direction:column;animation:oj-dialog-enter .2s ease-out}
.oj-workspace-dialog::backdrop{background:#15365555;backdrop-filter:blur(5px)}
.oj-dialog-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:15px 22px;background:linear-gradient(120deg,#f0f7ff,#fff);border-bottom:1px solid #e6edf5;flex:none}
.oj-dialog-heading{display:flex;align-items:center;gap:10px;min-width:0}.oj-dialog-heading>div{min-width:0}.oj-dialog-symbol{display:grid;place-items:center;width:30px;height:30px;flex:none;border-radius:8px;background:#e7f1fd;color:#397cc1;border:1px solid #dceafa}
.oj-dialog-heading h2{font-size:15px;line-height:1.4;margin:0;color:#294c70}.oj-dialog-heading p{font-size:11px;line-height:1.5;color:#7c93ac;margin:3px 0 0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.oj-dialog-close{display:grid;place-items:center;width:28px;height:28px;flex:none;border:1px solid #e2eaf4;border-radius:7px;background:#fff;color:#7f94ab;cursor:pointer}.oj-dialog-close:hover{background:#edf5fd;color:#367dbb}.oj-dialog-close:focus-visible{outline:2px solid #6ca8e3;outline-offset:3px}
.oj-dialog-body{padding:18px 22px;min-height:0;flex:1;overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable;scrollbar-width:thin;scrollbar-color:#c9d9ea transparent}
@keyframes oj-dialog-enter{from{opacity:0;transform:translateY(12px) scale(.985)}to{opacity:1;transform:none}}
@media(max-width:600px){.oj-workspace-dialog{width:calc(100vw - 20px);height:calc(100dvh - 28px);border-radius:12px}.oj-dialog-header{padding:12px 14px;gap:10px}.oj-dialog-heading h2{font-size:14px}.oj-dialog-body{padding:15px 14px}}
@media(prefers-reduced-motion:reduce){.oj-workspace-dialog[open]{animation:none}}
</style>
