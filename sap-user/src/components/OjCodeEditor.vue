<template>
  <div class="oj-code-editor">
    <div ref="host" class="oj-code-editor-host" />
    <div v-if="!readOnly" class="oj-editor-status">
      <span v-if="assistanceError" class="oj-editor-assist-error" role="status">{{ assistanceError }}</span>
      <button type="button" class="oj-editor-shortcuts" aria-label="查看编辑器快捷键" title="补全：Ctrl + Space 或 Alt + /；查找：Ctrl / Cmd + F；格式化：Shift + Alt + F" @click="showShortcuts = !showShortcuts">快捷键</button>
      <span v-if="showShortcuts" class="oj-editor-shortcut-hint">补全 Ctrl + Space / Alt + / · 查找 Ctrl / Cmd + F · 格式化 Shift + Alt + F · 运行 Ctrl / Cmd + Enter · 提交 Ctrl / Cmd + Shift + Enter</span>
    </div>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { EditorState, Compartment, Prec } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLineGutter, drawSelection, highlightActiveLine, rectangularSelection } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab, isolateHistory } from '@codemirror/commands'
import { syntaxHighlighting, defaultHighlightStyle, bracketMatching, indentOnInput, indentUnit, foldGutter, foldKeymap } from '@codemirror/language'
import { closeBrackets, closeBracketsKeymap, acceptCompletion, nextSnippetField, prevSnippetField, startCompletion } from '@codemirror/autocomplete'
import { search, searchKeymap, highlightSelectionMatches } from '@codemirror/search'
import { createOjAssistant } from '@/utils/ojAssist'
const props = defineProps({ modelValue: { type: String, default: '' }, language: { type: String, default: 'cpp' }, readOnly: Boolean, label: { type: String, default: '在线代码编辑器' } })
const emit = defineEmits(['update:modelValue', 'format', 'run', 'submit'])
const host = ref(null), language = new Compartment(), editable = new Compartment(), assistance = new Compartment()
const assistanceError = ref(''), showShortcuts = ref(false)
const grammars = {
  c: () => import('@codemirror/lang-cpp').then(module => module.cpp()), cpp: () => import('@codemirror/lang-cpp').then(module => module.cpp()),
  java: () => import('@codemirror/lang-java').then(module => module.java()), python: () => import('@codemirror/lang-python').then(module => module.python()),
  rust: () => import('@codemirror/lang-rust').then(module => module.rust())
}
let view, assistant, generation = 0
defineExpose({focus:()=>view?.focus()})
async function configure() {
  const current = ++generation; assistant?.destroy(view); assistant = null
  if (!view) return
  view.dispatch({ effects: [
    assistance.reconfigure([]),
    editable.reconfigure([EditorState.readOnly.of(props.readOnly), EditorView.editable.of(!props.readOnly)])
  ] })
  const key = props.language
  let grammar = []
  try { grammar = await (grammars[key] || grammars.cpp)() } catch { /* Keep editing available when a syntax chunk fails to load. */ }
  if (!view || current !== generation) return
  view.dispatch({ effects: language.reconfigure(grammar) })
  if (!props.readOnly) {
    assistant = createOjAssistant(key, value => { if (current === generation) assistanceError.value = value === 'unavailable' ? '代码补全暂不可用' : '' })
    view.dispatch({ effects: assistance.reconfigure(assistant.extensions) })
  }
}
onMounted(() => {
  view = new EditorView({ parent: host.value, state: EditorState.create({ doc: props.modelValue, extensions: [
    lineNumbers(), highlightActiveLineGutter(), highlightActiveLine(), drawSelection(), rectangularSelection(), history(), bracketMatching(), indentOnInput(), closeBrackets(), foldGutter(),
    EditorState.tabSize.of(4), indentUnit.of('    '),
    EditorState.phrases.of({ Find: '查找', Replace: '替换', next: '下一个', previous: '上一个', all: '全选',
      'match case': '区分大小写', regexp: '正则表达式', 'by word': '完整单词', replace: '替换', 'replace all': '全部替换', close: '关闭',
      'Go to line': '跳转到行', go: '跳转', 'current match': '当前匹配', 'on line': '所在行', 'replaced match on line $': '已替换第 $ 行的匹配',
      'replaced $ matches': '已替换 $ 处匹配', 'Fold line': '折叠代码', 'Unfold line': '展开代码', unfold: '展开', 'folded code': '已折叠的代码',
      'Folded lines': '已折叠行', 'Unfolded lines': '已展开行', to: '至', Diagnostics: '语法提示', 'No diagnostics': '没有语法提示' }),
    Prec.highest(keymap.of([
      { key: 'Tab', run: editor => !editor.state.readOnly && (acceptCompletion(editor) || nextSnippetField(editor)), shift: editor => !editor.state.readOnly && prevSnippetField(editor) },
      { key: 'Mod-Enter', run: editor => { if (editor.state.readOnly) return false; emit('run'); return true } },
      { key: 'Mod-Shift-Enter', run: editor => { if (editor.state.readOnly) return false; emit('submit'); return true } },
      { key: 'Alt-/', run: editor => !editor.state.readOnly && startCompletion(editor) },
      { key: 'Shift-Alt-f', run: editor => { if (editor.state.readOnly) return false; emit('format'); return true } }
    ])),
    keymap.of([...closeBracketsKeymap, indentWithTab, ...defaultKeymap, ...historyKeymap, ...searchKeymap, ...foldKeymap]),
    search({ top: true }), highlightSelectionMatches(),
    syntaxHighlighting(defaultHighlightStyle), language.of([]), assistance.of([]),
    editable.of([EditorState.readOnly.of(props.readOnly),EditorView.editable.of(!props.readOnly)]), EditorView.contentAttributes.of({ 'aria-label': props.label, spellcheck: 'false' }),
    EditorView.updateListener.of(update => { if (update.docChanged) emit('update:modelValue', update.state.doc.toString()) }),
    EditorView.theme({ '&': { height: '100%', fontSize: '14px', background: '#fbfdff' },
      '.cm-scroller': { overflow: 'auto', fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', lineHeight: '1.8' },
      '.cm-content': { padding: '18px 0' }, '.cm-gutters': { background: '#f1f6fc', color: '#8695a9', border: 'none' },
      '&.cm-focused': { outline: 'none' }, '.cm-activeLineGutter': { background: '#e5effb' }, '.cm-activeLine': { background: '#edf5ff66' },
      '.cm-tooltip': { border: '1px solid #cfe0f3', borderRadius: '10px', background: '#fff', color: '#294e73', boxShadow: '0 8px 28px #264e731c', maxWidth: 'min(460px, calc(100vw - 24px))' },
      '.cm-tooltip-autocomplete > ul': { fontFamily: 'inherit', padding: '5px', maxHeight: '260px' },
      '.cm-tooltip-autocomplete > ul > li': { padding: '5px 8px', borderRadius: '5px', fontSize: '12px' },
      '.cm-tooltip-autocomplete > ul > li[aria-selected]': { background: '#e7f1ff', color: '#1f67b7' },
      '.cm-completionDetail': { color: '#8293a7', fontStyle: 'normal', marginLeft: '12px', fontSize: '11px' },
      '.cm-tooltip.cm-completionInfo': { padding: '0' },
      '.cm-panels': { background: '#f4f8fe', color: '#365978', borderColor: '#d9e6f6' },
      '.cm-search': { padding: '9px', lineHeight: '1.6' },
      '.cm-textfield': { background: '#fff', border: '1px solid #ccdef2', borderRadius: '6px', padding: '4px 7px', color: '#294e73', maxWidth: '180px' },
      '.cm-button': { backgroundImage: 'none', backgroundColor: '#fff', border: '1px solid #ccdef2', borderRadius: '6px', padding: '4px 8px', color: '#294e73', cursor: 'pointer' },
      '.cm-button:hover': { backgroundColor: '#eaf3ff' }, '.cm-foldGutter': { width: '15px' },
      '.cm-diagnostic': { fontFamily: 'inherit', fontSize: '12px', padding: '8px 12px' },
      '.cm-selectionMatch': { backgroundColor: '#d8e9fa' }
    })
  ] }) })
  configure()
})
watch(() => props.modelValue, value => {
  if (view && value !== view.state.doc.toString()) view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value }, annotations: isolateHistory.of('full') })
})
watch(() => [props.language, props.readOnly], configure)
onBeforeUnmount(() => { generation++; assistant?.destroy(view); view?.destroy(); view = null })
</script>
<style scoped>
.oj-code-editor { height: 430px; min-height: 260px; overflow: hidden; resize: vertical; display: flex; flex-direction: column; }
.oj-code-editor-host { flex: 1; min-height: 0; }
.oj-editor-status { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 6px 12px; font-size: 11px; color: #8195ac; border-top: 1px solid #e5edf8; background: #f8fbff; }
.oj-editor-assist-error { color: #a87537; }
.oj-editor-shortcuts { margin-left: auto; color: #6485a8; border: 0; background: transparent; cursor: pointer; font-size: inherit; padding: 2px 4px; border-radius: 4px; }
.oj-editor-shortcuts:hover { background: #e8f1fb; }
.oj-editor-shortcut-hint { flex-basis: 100%; line-height: 1.7; }
</style>
<style>
.oj-editor-description { padding: 10px 12px; max-width: 380px; font-family: var(--font-body, sans-serif); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.oj-editor-description strong { display: block; font-family: ui-monospace, monospace; font-size: 12px; color: #315d89; }
.oj-editor-description p { margin: 5px 0 0; color: #6c819b; }
.oj-editor-signature { padding: 8px 11px; max-width: 420px; font-family: ui-monospace, monospace; font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.oj-editor-signature strong { color: #2679cc; background: #edf5ff; border-radius: 3px; padding: 1px 3px; }
.oj-editor-completions .cm-completionIcon { color: #638db9; }
@media (max-width: 600px) { .oj-editor-completions .cm-completionDetail { display: none; } }
</style>
