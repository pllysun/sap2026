import { nextTick, onUnmounted, watch } from 'vue'

const openDialogs = []
let originalOverflow = ''

// Shared by teleported dialogs, including nested image previews on touch screens.
export function useDialog(visible, element, close) {
  const identity = Symbol('dialog')
  let returnFocus = null
  function release() {
    const index = openDialogs.indexOf(identity)
    if (index < 0) return
    openDialogs.splice(index, 1)
    if (!openDialogs.length) document.body.style.overflow = originalOverflow
    document.removeEventListener('keydown', onKeydown)
    if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
  }
  function onKeydown(event) {
    if (openDialogs.at(-1) !== identity) return
    if (event.key === 'Escape') { event.preventDefault(); close(); return }
    if (event.key !== 'Tab') return
    const items = [...(element.value?.querySelectorAll('a[href],button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]') || [])].filter(el => el.getClientRects().length)
    const first = items[0], last = items.at(-1)
    if (!first) { event.preventDefault(); element.value?.focus(); return }
    if (event.shiftKey && (document.activeElement === first || !element.value?.contains(document.activeElement))) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && (document.activeElement === last || !element.value?.contains(document.activeElement))) { event.preventDefault(); first.focus() }
  }
  watch(visible, async open => {
    if (!open) { release(); return }
    returnFocus = document.activeElement
    if (!openDialogs.length) { originalOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden' }
    openDialogs.push(identity)
    document.addEventListener('keydown', onKeydown)
    await nextTick()
    if (visible.value && openDialogs.at(-1) === identity) (element.value?.querySelector('button') || element.value)?.focus({ preventScroll: true })
  }, { flush: 'post' })
  onUnmounted(release)
}
