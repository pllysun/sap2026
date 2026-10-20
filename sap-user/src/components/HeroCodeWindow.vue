<template>
  <div ref="editor" class="hero-editor" :class="{ 'hero-editor--choosing': menuOpen, 'hero-editor--playing': playing }" @keydown.esc.stop="closeMenu(true)">
    <div class="hero-editor__bar">
      <span class="editor-dots" aria-hidden="true"><i></i><i></i><i></i></span>
      <span class="editor-project">Hello World</span>
      <button ref="trigger" class="language-trigger" aria-label="切换编程语言" aria-haspopup="menu" :aria-expanded="menuOpen" :aria-controls="menuId" @click="toggleMenu" @keydown.down.prevent="openMenu" @keydown.up.prevent="openMenu">
        <span class="language-mark" :style="languageAccent(language.id)" aria-hidden="true">{{ languageMark(language.id) }}</span>
        <strong>{{ language.label }}</strong>
        <svg class="language-chevron" viewBox="0 0 16 16" width="13" height="13" aria-hidden="true"><path d="m4 6 4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5" /></svg>
      </button>
    </div>
    <Teleport to="body">
      <Transition name="language-menu">
        <div v-if="menuOpen" :id="menuId" ref="menu" class="language-menu" :class="{ 'language-menu--above': menuAbove }" :style="menuPosition" role="menu" aria-label="选择编程语言" @keydown="navigateMenu">
          <div class="language-menu__heading" role="presentation"><span>示例语言</span><small>{{ heroLanguages.length }} 种语言</small></div>
          <div class="language-menu__grid" role="presentation">
            <button v-for="(item, index) in heroLanguages" :key="item.id" role="menuitemradio" :aria-checked="item.id === language.id" :aria-label="item.label" :tabindex="index === focusIndex ? 0 : -1" :class="{ selected: item.id === language.id }" @focus="focusIndex = index" @click="selectLanguage(item.id)">
              <span class="language-mark" :style="languageAccent(item.id)" aria-hidden="true">{{ languageMark(item.id) }}</span>
              <span class="language-option__copy"><strong>{{ item.label }}</strong><small>{{ item.file }}</small></span>
              <span v-if="item.id === language.id" class="language-option__selected" aria-hidden="true"></span>
            </button>
          </div>
        </div>
      </Transition>
    </Teleport>
    <div class="hero-editor__tabs">
      <span class="editor-tab"><svg viewBox="0 0 18 18" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.3" aria-hidden="true"><path d="M4 2h7l3 3v11H4zM10 2v4h4"/></svg><span class="editor-file">{{ language.file }}</span><i aria-hidden="true"></i></span>
      <span class="editor-context">示例代码</span>
    </div>
    <div class="hero-editor__code" :class="{ 'hero-editor__code--active': playing || played }" :style="{ '--output-line': outputLine }" :aria-label="`${language.label} 示例代码`" tabindex="0">
      <!-- Line numbers must never enter token matching: splitting their fixed-width
           spans made each digit/space claim an entire gutter after a language change. -->
      <div class="code-gutter" aria-hidden="true"><span v-for="index in lines.length" :key="index">{{ String(index).padStart(2, '0') }}</span></div>
      <ShikiMagicMovePrecompiled :key="reducedMotion ? 'still' : 'motion'" class="code-lines"
                                :steps="codeSteps" :step="languageIndex" :animate="motionReady && !reducedMotion"
                                :options="morphOptions" />
    </div>
    <div class="hero-editor__terminal">
      <div class="terminal-heading"><span><svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><path d="m3 5 4 4-4 4m6 1h7"/></svg>输出预览</span><button class="demo-trigger" aria-label="演示代码输出" :aria-busy="playing" :disabled="playing" title="展示示例输出" @click="playExample"><svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor" aria-hidden="true"><path d="m5 3 8 5-8 5z"/></svg>演示</button></div>
      <div class="terminal-output" :aria-busy="playing"><span class="terminal-prompt" aria-hidden="true">›</span><code>
        <span class="terminal-measure" aria-hidden="true">{{ HERO_GREETING }}<span class="terminal-caret terminal-caret--measure"></span></span>
        <span class="terminal-words" :class="{ 'terminal-words--animated': (playing || played) && !reducedMotion }" :data-effect="demoEffect"><span v-for="(character, index) in outputCharacters" :key="`${demoRun}-${index}`" class="terminal-letter">{{ character }}</span><span v-if="playing" class="terminal-caret" aria-hidden="true"></span></span>
      </code></div>
    </div>
    <span class="sr-only" aria-live="polite">当前语言：{{ language.label }}。{{ playing ? '正在演示输出' : played ? '演示完成：' + HERO_GREETING : '' }}</span>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, useId } from 'vue'
import { ShikiMagicMovePrecompiled } from '@shikijs/magic-move/vue'
import '@shikijs/magic-move/style.css'
import codeSteps from '../generated/hero-code-tokens.json'
import { heroLanguages } from './heroLanguages'
import { HERO_GREETING, createDemoEffectPicker } from '../utils/heroDemo'
const selected = ref('c')
const language = computed(() => heroLanguages.find(item => item.id === selected.value))
const languageIndex = computed(() => heroLanguages.findIndex(item => item.id === selected.value))
const lines = computed(() => language.value.code.split('\n'))
const outputLine = computed(() => lines.value.findIndex(line => /printf|cout|println|print\(|console\.log|WriteLine/.test(line)))
const editor = ref(null), trigger = ref(null), menu = ref(null), menuOpen = ref(false), menuAbove = ref(false), focusIndex = ref(0)
const menuId = `hero-languages-${useId()}`, menuPosition = ref({})
const displayedOutput = ref(HERO_GREETING), playing = ref(false), played = ref(false)
const outputCharacters = computed(() => Array.from(displayedOutput.value))
const demoEffect = ref('blur'), demoRun = ref(0)
const nextDemoEffect = createDemoEffectPicker()
const reducedMotion = ref(false), motionReady = ref(false)
// Keep the established code morph timing and movement unchanged.
const morphOptions = { duration: 600, delayMove: .3, delayLeave: .1, delayEnter: .7,
  easing: 'ease', animateContainer: false, containerStyle: false, splitTokens: true }
const languageStyles = {
  c: ['C', '#3d62ad', '#eaf0fc'], cpp: ['C++', '#3b5bb4', '#eceffd'],
  java: ['J', '#a46336', '#fbefe4'], python: ['Py', '#8b6d23', '#f9f2d9'],
  js: ['JS', '#896318', '#fcf1cf'], ts: ['TS', '#286fa3', '#e7f2fb'],
  rust: ['Rs', '#9c593c', '#f8ece4'], kotlin: ['Kt', '#8057b2', '#f1eafa'],
  shell: ['sh', '#3e7d70', '#e7f4ef'], lua: ['Lua', '#5364a3', '#eef0fa'],
  csharp: ['C#', '#7960a4', '#f1ecf8'],
}
function languageMark(id) { return languageStyles[id][0] }
function languageAccent(id) { const [, color, background] = languageStyles[id]; return { color, background } }
let motionPreference, demoTimer
function updateMotionPreference() {
  reducedMotion.value = motionPreference.matches
  if (reducedMotion.value && playing.value) { clearTimeout(demoTimer); displayedOutput.value = HERO_GREETING; playing.value = false; played.value = true }
}
function resetDemo() { clearTimeout(demoTimer); playing.value = false; played.value = false; displayedOutput.value = HERO_GREETING }
function playExample() {
  if (playing.value) return
  resetDemo()
  if (reducedMotion.value) { played.value = true; return }
  demoEffect.value = nextDemoEffect()
  demoRun.value++
  playing.value = true
  displayedOutput.value = ''
  const characters = Array.from(HERO_GREETING)
  let length = 0
  const tick = () => {
    const character = characters[length++]
    displayedOutput.value = characters.slice(0, length).join('')
    if (length < characters.length) demoTimer = setTimeout(tick, /[，！]/.test(character) ? 130 : 45 + Math.random() * 24)
    else { playing.value = false; played.value = true }
  }
  // A fixed, local preview only. Never evaluate snippets or upload code.
  demoTimer = setTimeout(tick, 140)
}
async function focusOption() { await nextTick(); menu.value?.querySelectorAll('[role="menuitemradio"]')[focusIndex.value]?.focus() }
function openMenu() {
  if (menuOpen.value) return
  const bounds = trigger.value?.getBoundingClientRect()
  if (!bounds) return
  const unit = parseFloat(getComputedStyle(document.documentElement).fontSize)
  const edge = 12, width = Math.min(22 * unit, window.innerWidth - edge * 2)
  const below = window.innerHeight - bounds.bottom - edge - 8, above = bounds.top - edge - 8
  menuAbove.value = below < 26.25 * unit && above > below
  menuPosition.value = { width: `${width}px`, left: `${Math.max(edge, Math.min(bounds.right - width, window.innerWidth - width - edge))}px`,
    maxHeight: `${Math.max(100, menuAbove.value ? above : below)}px`,
    ...(menuAbove.value ? { bottom: `${window.innerHeight - bounds.top + 8}px` } : { top: `${bounds.bottom + 8}px` }) }
  focusIndex.value = languageIndex.value
  menuOpen.value = true
  focusOption()
}
function closeMenu(restoreFocus = false) { menuOpen.value = false; if (restoreFocus) trigger.value?.focus({ preventScroll: true }) }
function toggleMenu() { if (menuOpen.value) closeMenu(true); else openMenu() }
function selectLanguage(id) { resetDemo(); selected.value = id; closeMenu(true) }
function navigateMenu(event) {
  if (event.key === 'Escape') { event.preventDefault(); closeMenu(true); return }
  if (event.key === 'Tab') { closeMenu(true); return }
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const index = focusIndex.value, last = heroLanguages.length - 1
  if (event.key === 'Home') focusIndex.value = 0
  else if (event.key === 'End') focusIndex.value = last
  else if (event.key === 'ArrowDown') focusIndex.value = Math.min(index + 2, last)
  else if (event.key === 'ArrowUp') focusIndex.value = Math.max(index - 2, 0)
  else focusIndex.value = (index + (event.key === 'ArrowRight' ? 1 : -1) + heroLanguages.length) % heroLanguages.length
  focusOption()
}
function outside(event) { if (menuOpen.value && !trigger.value?.contains(event.target) && !menu.value?.contains(event.target)) closeMenu() }
function viewportChanged(event) {
  if (!menuOpen.value) return
  if (event.type === 'scroll' && event.target instanceof Node && menu.value?.contains(event.target)) return
  closeMenu()
}
onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  updateMotionPreference(); motionPreference.addEventListener('change', updateMotionPreference)
  motionReady.value = true
  document.addEventListener('pointerdown', outside); document.addEventListener('focusin', outside)
  window.addEventListener('resize', viewportChanged); window.addEventListener('scroll', viewportChanged, true)
})
onUnmounted(() => {
  clearTimeout(demoTimer); motionPreference?.removeEventListener('change', updateMotionPreference)
  document.removeEventListener('pointerdown', outside); document.removeEventListener('focusin', outside)
  window.removeEventListener('resize', viewportChanged); window.removeEventListener('scroll', viewportChanged, true)
})
</script>

<style scoped>
.hero-editor { position: relative; color: #34425e; border: 1px solid #cdd7ee; border-radius: 18px; background: #fff; box-shadow: 0 2px 4px #3e4c8108, 0 25px 60px -18px #4b609732; isolation: isolate; }
.hero-editor::before { content: ''; position: absolute; z-index: -1; inset: 12px -13px -12px 13px; border: 1px solid #d4daf1; border-radius: 18px; background: #e9eefbcc; transform: rotate(2deg); pointer-events: none; }
.hero-editor--choosing { z-index: 4; }
.hero-editor__bar { display: flex; align-items: center; gap: .8125rem; min-height: 3.25rem; padding: .5rem 1.0625rem; border-bottom: 1px solid #dce2f1; border-radius: 18px 18px 0 0; background: #eef1fc; }
.editor-dots { display: flex; gap: 5px; flex-shrink: 0; }.editor-dots i { width: 6px; height: 6px; border-radius: 50%; background: #a8b5df; }.editor-dots i:nth-child(2) { background: #c9b7dc; }.editor-dots i:last-child { background: #e0bb9c; }
.editor-project { color: #62718f; font: .75rem ui-monospace, monospace; letter-spacing: .3px; margin-right: auto; }
.language-trigger { display: flex; gap: .5rem; align-items: center; min-height: 2.125rem; padding: .25rem .5625rem .25rem .3125rem; border: 1px solid #d1d9ee; border-radius: 9px; background: #fff; color: #3f517e; font-size: .75rem; cursor: pointer; transition: border-color .2s, box-shadow .2s, transform .2s cubic-bezier(.34,1.3,.64,1); }
.language-trigger:hover { border-color: #a2b3df; box-shadow: 0 2px 8px #4b609718; }.language-trigger:active { transform: scale(.97); }.language-trigger strong { font-weight: 600; }.language-chevron { margin-left: 3px; transition: transform .2s; }.language-trigger[aria-expanded="true"] .language-chevron { transform: rotate(180deg); }
.language-mark { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; width: 1.6875rem; height: 1.5625rem; border-radius: .375rem; font: 700 .625rem/1 ui-monospace, monospace; letter-spacing: -.4px; }
.language-menu { position: fixed; z-index: 1001; overflow-y: auto; overscroll-behavior: contain; padding: .75rem; background: #fdfdff; border: 1px solid #d7ddef; box-shadow: 0 0 0 3px #ffffffb3, 0 12px 36px #27365d24; border-radius: 15px; color: #34425e; transform-origin: top right; scrollbar-width: thin; }
.language-menu--above { transform-origin: bottom right; }
.language-menu__heading { display: flex; justify-content: space-between; align-items: center; padding: .1875rem .3125rem .75rem; font-size: .8125rem; font-weight: 600; }.language-menu__heading small { color: #748094; font-size: .6875rem; font-weight: 400; }
.language-menu__grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: .3125rem; }
.language-menu__grid button { display: flex; align-items: center; gap: .5625rem; min-width: 0; min-height: 3.125rem; text-align: left; padding: .5rem; border: 1px solid transparent; border-radius: 9px; position: relative; cursor: pointer; background: transparent; transition: background .15s, border-color .15s, transform .15s cubic-bezier(.34,1.3,.64,1); }
.language-menu__grid button:hover { background: #f0f2fa; }.language-menu__grid button:active { transform: scale(.97); }.language-menu__grid button.selected { border-color: #c5cdef; background: #edf0fd; }
.language-option__copy { display: flex; flex-direction: column; gap: 4px; min-width: 0; }.language-option__copy strong { font-size: .75rem; font-weight: 600; color: #394e7d; }.language-option__copy small { font: .625rem/1.2 ui-monospace,monospace; color: #748094; }
.language-option__selected { width: 5px; height: 5px; background: #6477c5; border-radius: 50%; margin-left: auto; flex-shrink: 0; }
.hero-editor__tabs { display: flex; align-items: stretch; justify-content: space-between; min-height: 2.5625rem; padding: 0 1.0625rem; border-bottom: 1px solid #e7eaf3; background: #f7f8fd; }
.editor-tab { position: relative; display: inline-flex; gap: 9px; align-items: center; padding: 0 14px; margin-left: -1px; background: #fff; border-inline: 1px solid #e7eaf3; color: #6373b2; }
.editor-tab::before { content: ''; position: absolute; height: 2px; background: #6979c7; inset: 0 0 auto; }.editor-tab i { width: 5px; height: 5px; margin-left: 9px; border-radius: 50%; background: #91a3dd; }
.editor-file { font: .75rem ui-monospace, monospace; color: #475a82; }.editor-context { align-self: center; font-size: .75rem; color: #7b8599; }
.hero-editor__code { --hero-line-height: 1.625rem; --hero-code-inset: 1rem; display: grid; grid-template-columns: 2ch minmax(max-content,1fr); align-items: start; column-gap: 1.75ch; padding: var(--hero-code-inset) 1.25rem var(--hero-code-inset) 1rem; overflow: auto; background: #fff; font: .8125rem/var(--hero-line-height) ui-monospace, SFMono-Regular, Consolas, monospace; scrollbar-width: thin; scrollbar-color: #c9d2e8 transparent; }
.code-gutter { position: sticky; left: -1rem; display: grid; grid-auto-rows: var(--hero-line-height); align-content: start; color: #929db1; background: #fff; user-select: none; text-align: right; font-size: .75em; line-height: var(--hero-line-height); z-index: 2; }
.code-lines { min-height: 18em; margin: 0; padding: 0; font: inherit; font-variant-ligatures: none; letter-spacing: 0; tab-size: 4; }
.hero-editor__code--active { background-image: linear-gradient(#f1f3fc,#f1f3fc); background-size: 100% var(--hero-line-height); background-repeat: no-repeat; background-position: 0 calc(var(--hero-code-inset) + var(--output-line) * var(--hero-line-height)); }
.hero-editor__terminal { padding: .75rem 1.1875rem 1rem; border-top: 1px solid #dce9e7; border-radius: 0 0 18px 18px; background: #f0f7f6; }
.terminal-heading { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-bottom: .5625rem; }
.terminal-heading > span { display: flex; align-items: center; gap: 6px; color: #637b7b; font-size: .75rem; }
.demo-trigger { min-height: 1.875rem; display: inline-flex; align-items: center; gap: 5px; border: 1px solid #cbdedb; border-radius: 7px; padding: .25rem .5625rem; background: #fff; color: #427b76; font-size: .75rem; cursor: pointer; transition: box-shadow .2s, transform .2s cubic-bezier(.34,1.3,.64,1); }.demo-trigger:hover { box-shadow: 0 2px 7px #427b7618; }.demo-trigger:active { transform: scale(.96); }.demo-trigger:disabled { opacity: .7; cursor: default; }
.terminal-output { display: flex; align-items: flex-start; gap: .625rem; color: #326d69; }.terminal-output code { display: grid; flex: 1; min-width: 0; font: .8125rem/1.8 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .1px; overflow-wrap: anywhere; }
.terminal-measure, .terminal-words { grid-area: 1/1; white-space: pre-wrap; }.terminal-measure { visibility: hidden; pointer-events: none; }.terminal-letter { display: inline-block; }
.terminal-prompt { color: #7a9995; font: 1.125rem/1.3 ui-monospace, monospace; }.terminal-caret { display: inline-block; width: 2px; height: 1em; background: #659f98; vertical-align: -.12em; margin-left: 3px; box-shadow: 0 0 6px #659f9850; animation: caret-blink .85s step-end infinite; }.terminal-caret--measure { animation: none; }
/* Adapted from Open Claude UI: soft grapheme reveal and restrained overshoot.
   All five effects affect only new glyphs, so existing output never shifts. */
.terminal-words--animated[data-effect="blur"] .terminal-letter { animation: output-blur .32s ease-out both; }
.terminal-words--animated[data-effect="spring"] .terminal-letter { animation: output-spring .3s cubic-bezier(.34,1.3,.64,1) both; }
.terminal-words--animated[data-effect="ink"] .terminal-letter { animation: output-ink .28s ease-out both; }
.terminal-words--animated[data-effect="scan"] .terminal-letter { animation: output-scan .28s cubic-bezier(.16,1,.3,1) both; }
.terminal-words--animated[data-effect="prism"] .terminal-letter { animation: output-prism .32s ease-out both; }
@keyframes output-blur { from { opacity: 0; filter: blur(4px); } to { opacity: 1; filter: blur(0); } }
@keyframes output-spring { from { opacity: 0; transform: translateY(.35em) scale(.82); } to { opacity: 1; transform: none; } }
@keyframes output-ink { from { opacity: .2; clip-path: inset(0 100% 0 0); filter: brightness(1.5); } to { opacity: 1; clip-path: inset(0); filter: none; } }
@keyframes output-scan { from { opacity: 0; transform: translateY(-.25em); text-shadow: 0 6px 5px #739cc8; } to { opacity: 1; transform: none; text-shadow: 0 0 transparent; } }
@keyframes output-prism { from { opacity: .1; text-shadow: -3px 0 #839ee0, 3px 0 #c39bd9; filter: blur(1px); } to { opacity: 1; text-shadow: 0 0 transparent; filter: none; } }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.language-menu-enter-active { transition: opacity .18s, transform .24s cubic-bezier(.34,1.3,.64,1); }.language-menu-leave-active { transition: opacity .12s, transform .12s ease-out; }.language-menu-enter-from, .language-menu-leave-to { opacity: 0; transform: translateY(5px) scale(.96); }
button:focus-visible, .hero-editor__code:focus-visible { outline: 2px solid #798bd6; outline-offset: 3px; }
@keyframes caret-blink { 50% { opacity: 0; } }
@media (max-width: 1100px) and (min-width: 901px) { .hero-editor__code { --hero-line-height: 1.5rem; font-size: .75rem; } }
@media (max-width: 480px) {
  .hero-editor__bar { padding: 7px 12px; gap: 9px; min-height: 48px; }.editor-project { font-size: .75rem; }.editor-dots { gap: 3px; }.editor-dots i { width: 5px; height: 5px; }
  .language-trigger { gap: 5px; min-height: 34px; padding-right: 7px; }.language-mark { width: 24px; height: 24px; }
  .hero-editor__tabs { min-height: 37px; padding-inline: 12px; }.editor-tab { padding-inline: 10px; }
  .hero-editor__code { --hero-code-inset: 13px; --hero-line-height: 1.5rem; font-size: .75rem; padding-inline: 10px 14px; column-gap: 1.25ch; }.code-gutter { left: -10px; }
  .hero-editor__terminal { padding: 9px 13px 13px; }.terminal-output { gap: 7px; }.terminal-output code { font-size: .75rem; }.terminal-heading { margin-bottom: 6px; }.demo-trigger { min-height: 34px; }
}
@media (prefers-reduced-motion: reduce) { *, *::before, .code-lines :deep(*) { animation: none !important; transition: none !important; } }
</style>
