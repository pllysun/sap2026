<template><div ref="host" class="oj-code-editor" /></template>
<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { EditorState, Compartment } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLineGutter, drawSelection } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { syntaxHighlighting, defaultHighlightStyle, bracketMatching, indentOnInput } from '@codemirror/language'
import { cpp } from '@codemirror/lang-cpp'
import { java } from '@codemirror/lang-java'
import { python } from '@codemirror/lang-python'
import { rust } from '@codemirror/lang-rust'
const props = defineProps({ modelValue: { type: String, default: '' }, language: { type: String, default: 'cpp' }, readOnly: Boolean, label: { type: String, default: '在线代码编辑器' } })
const emit = defineEmits(['update:modelValue'])
const host = ref(null), language = new Compartment(), editable = new Compartment()
let view
const grammar = key => ({ c: cpp, cpp, java, python, rust }[key] || cpp)()
onMounted(() => {
  view = new EditorView({ parent: host.value, state: EditorState.create({ doc: props.modelValue, extensions: [
    lineNumbers(), highlightActiveLineGutter(), drawSelection(), history(), bracketMatching(), indentOnInput(),
    EditorState.tabSize.of(4), keymap.of([indentWithTab, ...defaultKeymap, ...historyKeymap]),
    syntaxHighlighting(defaultHighlightStyle), language.of(grammar(props.language)),
    editable.of([EditorState.readOnly.of(props.readOnly),EditorView.editable.of(!props.readOnly)]), EditorView.contentAttributes.of({ 'aria-label': props.label, spellcheck: 'false' }),
    EditorView.updateListener.of(update => { if (update.docChanged) emit('update:modelValue', update.state.doc.toString()) }),
    EditorView.theme({ '&': { height: '100%', fontSize: '14px', background: '#fbfdff' },
      '.cm-scroller': { overflow: 'auto', fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', lineHeight: '1.8' },
      '.cm-content': { padding: '20px 0' }, '.cm-gutters': { background: '#f1f6fc', color: '#8695a9', border: 'none' },
      '&.cm-focused': { outline: 'none' }, '.cm-activeLineGutter': { background: '#e5effb' } })
  ] }) })
})
watch(() => props.modelValue, value => {
  if (view && value !== view.state.doc.toString()) view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
})
watch(() => props.language, value => { view?.dispatch({ effects: language.reconfigure(grammar(value)) }) })
watch(() => props.readOnly, value => view?.dispatch({effects:editable.reconfigure([EditorState.readOnly.of(value),EditorView.editable.of(!value)])}))
onBeforeUnmount(() => view?.destroy())
</script>
<style scoped>.oj-code-editor { height: 430px; min-height: 260px; overflow: hidden; resize: vertical; }</style>
