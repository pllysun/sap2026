<template>
  <div ref="editor" class="hero-editor" :class="{ 'hero-editor--choosing': menuOpen, 'hero-editor--playing': playing }" @keydown.esc.stop="closeMenu(true)">
    <div class="hero-editor__bar">
      <span class="editor-dots" aria-hidden="true"><i></i><i></i><i></i></span>
      <span class="editor-project">Hello World</span>
      <div class="language-picker">
        <button ref="trigger" class="language-trigger" aria-label="切换编程语言" aria-haspopup="menu" :aria-expanded="menuOpen" aria-controls="hero-language-menu" @click="toggleMenu" @keydown.down.prevent="openMenu" @keydown.up.prevent="openMenu">
          <span aria-hidden="true">&lt;/&gt;</span><strong>{{ language.label }}</strong><svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true"><path d="m4 6 4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5" /></svg>
        </button>
        <Transition name="language-menu">
          <div v-if="menuOpen" id="hero-language-menu" class="language-menu" :class="{ 'language-menu--above': menuAbove }" role="menu" aria-label="选择编程语言" @keydown="navigateMenu">
            <div class="language-menu__caption" role="presentation">选择示例语言</div>
            <div class="language-menu__grid" role="presentation">
              <button v-for="(item, index) in heroLanguages" :key="item.id" role="menuitemradio" :aria-checked="item.id === language.id" :tabindex="index === focusIndex ? 0 : -1" :class="{ selected: item.id === language.id }" @click="selectLanguage(item.id)">{{ item.label }}<span v-if="item.id === language.id" aria-hidden="true">✓</span></button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
    <div class="hero-editor__tabs">
      <span class="editor-tab"><svg viewBox="0 0 18 18" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.3" aria-hidden="true"><path d="M4 2h7l3 3v11H4zM10 2v4h4"/></svg><span class="editor-file">{{ language.file }}</span><i aria-hidden="true"></i></span>
      <span class="editor-context">示例代码</span>
    </div>
    <div class="hero-editor__code" :aria-label="`${language.label} 示例代码`" tabindex="0">
      <Transition name="code-swap" mode="out-in">
        <div :key="language.id" class="code-lines">
          <div v-for="(line, index) in lines" :key="index" class="code-line" :class="{ 'code-line--active': (playing || played) && index === outputLine }"><span class="line-number" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span><code><span v-for="(token, tokenIndex) in codeTokens(line)" :key="tokenIndex" :class="`syntax-${token.kind}`">{{ token.text }}</span><span v-if="index === lines.length - 1" class="editor-caret" aria-hidden="true"></span></code></div>
        </div>
      </Transition>
    </div>
    <div class="hero-editor__terminal">
      <div class="terminal-heading"><span><svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><path d="m3 5 4 4-4 4m6 1h7"/></svg>输出预览</span><button class="demo-trigger" aria-label="演示代码输出" :disabled="playing" title="本地展示示例输出，不执行或上传代码" @click="playExample"><svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor" aria-hidden="true"><path d="m5 3 8 5-8 5z"/></svg>{{ playing ? '演示中' : played ? '再演示' : '演示' }}</button></div>
      <div class="terminal-output" :aria-busy="playing"><span class="terminal-prompt" aria-hidden="true">›</span><code>{{ displayedOutput || '\u00a0' }}</code><span v-if="playing" class="terminal-caret" aria-hidden="true"></span><svg v-if="played && !playing" class="terminal-done" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="m4 10 4 4 8-8"/></svg></div>
    </div>
    <span class="sr-only" aria-live="polite">当前语言：{{ language.label }}。{{ playing ? '正在演示输出' : played ? '演示完成：' + sampleOutput : '' }}</span>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { heroLanguages, codeTokens } from './heroLanguages'
const selected = ref('c')
const language = computed(() => heroLanguages.find(item => item.id === selected.value))
const lines = computed(() => language.value.code.split('\n'))
const outputLine = computed(() => lines.value.findIndex(line => /printf|cout|println|print\(|console\.log|WriteLine/.test(line)))
const editor = ref(null), trigger = ref(null), menuOpen = ref(false), menuAbove = ref(false), focusIndex = ref(0)
const sampleOutput = 'Hello, 软件协会!'
const displayedOutput = ref(sampleOutput), playing = ref(false), played = ref(false)
let demoTimer
function resetDemo() { clearTimeout(demoTimer); playing.value = false; played.value = false; displayedOutput.value = sampleOutput }
function playExample() {
  if (playing.value) return
  resetDemo()
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { played.value = true; return }
  playing.value = true
  displayedOutput.value = ''
  const characters = Array.from(sampleOutput)
  let length = 0
  const tick = () => {
    displayedOutput.value = characters.slice(0, ++length).join('')
    if (length < characters.length) demoTimer = setTimeout(tick, 45)
    else { playing.value = false; played.value = true }
  }
  // This only animates a fixed preview. No eval, network request, or code execution.
  demoTimer = setTimeout(tick, 160)
}
async function focusOption() { await nextTick(); editor.value?.querySelectorAll('[role="menuitemradio"]')[focusIndex.value]?.focus() }
function openMenu() {
  const bounds = trigger.value?.getBoundingClientRect()
  menuAbove.value = !!bounds && window.innerHeight - bounds.bottom < 260 && bounds.top > 260
  menuOpen.value = true
  focusIndex.value = heroLanguages.findIndex(item => item.id === selected.value)
  focusOption()
}
function closeMenu(restoreFocus = false) { menuOpen.value = false; if (restoreFocus) trigger.value?.focus() }
function toggleMenu() { if (menuOpen.value) closeMenu(true); else openMenu() }
function selectLanguage(id) { resetDemo(); selected.value = id; closeMenu(true) }
function navigateMenu(event) {
  if (event.key === 'Tab') { closeMenu(true); return }
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const delta = ['ArrowLeft', 'ArrowUp'].includes(event.key) ? -1 : 1
  focusIndex.value = event.key === 'Home' ? 0 : event.key === 'End' ? heroLanguages.length - 1 : (focusIndex.value + delta + heroLanguages.length) % heroLanguages.length
  focusOption()
}
function outsideClick(event) { if (menuOpen.value && !editor.value?.contains(event.target)) closeMenu() }
function outsideFocus(event) { if (menuOpen.value && !editor.value?.contains(event.target)) closeMenu() }
onMounted(() => { document.addEventListener('pointerdown', outsideClick); document.addEventListener('focusin', outsideFocus) })
onUnmounted(() => { clearTimeout(demoTimer); document.removeEventListener('pointerdown', outsideClick); document.removeEventListener('focusin', outsideFocus) })
</script>

<style scoped>
.hero-editor { position: relative; color: #315578; border: 1px solid #c8dff6; border-radius: 18px; background: #fffffffa; box-shadow: 0 2px 3px #4176a608, 0 25px 60px -18px #387dbb38; isolation: isolate; }
.hero-editor::before { content: ''; position: absolute; z-index: -1; inset: 12px -13px -12px 13px; border: 1px solid #bad8f380; border-radius: 18px; background: #dcedfc80; transform: rotate(2deg); pointer-events: none; }
.hero-editor--choosing { z-index: 4; }
.hero-editor__bar { display: flex; align-items: center; gap: 13px; min-height: 52px; padding: 8px 17px; border-bottom: 1px solid #dceaf8; border-radius: 18px 18px 0 0; background: linear-gradient(110deg, #eff7ff, #f8fcff); }
.editor-dots { display: flex; gap: 5px; flex-shrink: 0; }.editor-dots i { width: 6px; height: 6px; border: 1px solid #a4c8eb; border-radius: 50%; background: #c1ddf7; }.editor-dots i:nth-child(2) { background: #e0effd; }.editor-dots i:last-child { background: #f7fbff; }
.editor-project { color: #6c8dab; font: 10px ui-monospace, monospace; letter-spacing: .3px; margin-right: auto; }
.language-picker { position: relative; }
.language-trigger { display: flex; gap: 9px; align-items: center; min-height: 34px; padding: 6px 9px; border: 1px solid #c6ddf3; border-radius: 7px; background: #ffffffc9; color: #35658f; font-size: 11px; cursor: pointer; transition: background .2s, border-color .2s; }
.language-trigger:hover { border-color: #87b4e1; background: #fff; }.language-trigger > span { color: #85a9ca; }.language-trigger strong { font-weight: 600; }
.language-menu { position: absolute; top: calc(100% + 10px); right: 0; width: 290px; max-width: calc(100vw - 60px); max-height: calc(100dvh - 32px); overflow-y: auto; padding: 14px; background: #fff; border: 1px solid #c9dff3; box-shadow: 0 16px 45px #24558229; border-radius: 13px; z-index: 5; }
.language-menu--above { top: auto; bottom: calc(100% + 10px); }
.language-menu__caption { font-size: 11px; color: #7190aa; margin: 0 3px 12px; }.language-menu__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 5px; }.language-menu__grid button { min-height: 40px; text-align: left; padding: 8px; border-radius: 7px; color: #456988; font-size: 11px; position: relative; cursor: pointer; }.language-menu__grid button:hover, .language-menu__grid button:focus-visible { background: #f0f7ff; }.language-menu__grid button.selected { color: #226fc2; background: #e7f3ff; font-weight: 600; }.language-menu__grid button > span { margin-left: 4px; font-size: 10px; }
.hero-editor__tabs { display: flex; align-items: stretch; justify-content: space-between; min-height: 41px; padding: 0 17px; border-bottom: 1px solid #e7f0f9; background: #f8fbfe; }
.editor-tab { position: relative; display: inline-flex; gap: 9px; align-items: center; padding: 0 14px; margin-left: -1px; background: #fff; border-inline: 1px solid #e6eff8; color: #4989c3; }
.editor-tab::before { content: ''; position: absolute; height: 2px; background: #5a9edc; inset: 0 0 auto; }.editor-tab i { width: 5px; height: 5px; margin-left: 9px; border-radius: 50%; background: #6ba5d8; }
.editor-file { font: 11px ui-monospace, monospace; color: #375b7c; }.editor-context { align-self: center; font-size: 9px; color: #90a7ba; }
.hero-editor__code { padding: 16px 0; overflow: auto; background: #fff; scrollbar-width: thin; scrollbar-color: #c1d9ee transparent; }
.code-lines { min-height: 18em; font: 13px/2 ui-monospace, SFMono-Regular, Consolas, monospace; }
.code-line { display: flex; min-height: 2em; padding-inline: 14px 20px; border-left: 2px solid transparent; transition: background .2s, border-color .2s; }
.code-line code { font: inherit; white-space: pre; }.code-line--active { background: #edf6ff; border-left-color: #5c9ddc; }
.line-number { width: 3.6em; flex-shrink: 0; color: #9aafc2; font-size: .75em; padding-top: .33em; user-select: none; }
.syntax-keyword, .syntax-directive { color: #2d6ec4; }.syntax-string { color: #2b8598; }.syntax-comment { color: #88a0b5; }.syntax-number { color: #8472b4; }
.editor-caret, .terminal-caret { display: inline-block; width: .4em; height: 1em; background: #6fa7dc; vertical-align: -.12em; margin-left: 3px; animation: caret-blink 1.2s step-end infinite; }
.hero-editor__terminal { padding: 12px 19px 16px; border-top: 1px solid #dceafa; border-radius: 0 0 18px 18px; background: linear-gradient(120deg, #f4f9ff, #eaf4fd); }
.terminal-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 9px; }
.terminal-heading > span { display: flex; align-items: center; gap: 6px; color: #718da6; font-size: 10px; }
.demo-trigger { min-height: 30px; display: inline-flex; align-items: center; gap: 5px; border: 1px solid #c7dff4; border-radius: 6px; padding: 4px 9px; background: #ffffffa8; color: #427ba9; font-size: 10px; cursor: pointer; transition: background .2s; }.demo-trigger:hover { background: #fff; }.demo-trigger:disabled { opacity: .65; cursor: default; }
.terminal-output { display: flex; align-items: center; gap: 10px; min-height: 27px; color: #225581; }.terminal-output code { font: 13px ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .1px; }.terminal-prompt { color: #75a0c6; font: 18px ui-monospace, monospace; }.terminal-done { color: #5d93bb; margin-left: auto; }.terminal-caret { width: 5px; height: 14px; margin-left: -6px; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.code-swap-enter-active, .code-swap-leave-active, .language-menu-enter-active, .language-menu-leave-active { transition: opacity .14s, transform .14s; }.code-swap-enter-from { opacity: 0; transform: translateY(5px); }.code-swap-leave-to { opacity: 0; transform: translateY(-5px); }.language-menu-enter-from, .language-menu-leave-to { opacity: 0; transform: translateY(-4px); }
button:focus-visible, .hero-editor__code:focus-visible { outline: 2px solid #4d92d1; outline-offset: 3px; }
@keyframes caret-blink { 50% { opacity: 0; } }
@media (max-width: 1100px) and (min-width: 901px) { .code-lines { font-size: 11px; }.line-number { width: 2.8em; } }
@media (max-width: 480px) {
  .hero-editor__bar { padding: 7px 12px; gap: 9px; min-height: 48px; }.editor-project { font-size: 9px; }.editor-dots { gap: 3px; }.editor-dots i { width: 5px; height: 5px; }
  .language-trigger { gap: 5px; font-size: 10px; min-height: 34px; padding: 6px 7px; }.language-trigger > span { display: none; }
  .hero-editor__tabs { min-height: 37px; padding-inline: 12px; }.editor-tab { padding-inline: 10px; }.editor-file { font-size: 10px; }.editor-context { font-size: 8px; }
  .hero-editor__code { padding-block: 13px; }.code-lines { font-size: 11px; }.code-line { padding-inline: 8px 14px; }.line-number { width: 2.6em; }
  .hero-editor__terminal { padding: 9px 13px 13px; }.terminal-output code { font-size: 11px; }.terminal-heading { margin-bottom: 6px; }.demo-trigger { min-height: 34px; }
  .language-menu { right: -2px; width: 270px; padding: 10px; max-width: calc(100vw - 64px); }
}
@media (prefers-reduced-motion: reduce) { *, *::before { animation: none !important; transition: none !important; } }
</style>
