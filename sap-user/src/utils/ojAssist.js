import { autocompletion, snippetCompletion, closeCompletion } from '@codemirror/autocomplete'
import { linter } from '@codemirror/lint'
import { StateEffect, StateField } from '@codemirror/state'
import { syntaxTree } from '@codemirror/language'
import { ViewPlugin, hoverTooltip, showTooltip } from '@codemirror/view'

const catalogLoaders = {
  c: () => import('./ojAssist/catalogs/c.js'), cpp: () => import('./ojAssist/catalogs/cpp.js'),
  java: () => import('./ojAssist/catalogs/java.js'), python: () => import('./ojAssist/catalogs/python.js'),
  rust: () => import('./ojAssist/catalogs/rust.js')
}
function description(title, info) {
  const dom = document.createElement('div'); dom.className = 'oj-editor-description'
  if (title) { const heading = document.createElement('strong'); heading.textContent = title; dom.append(heading) }
  if (info) { const text = document.createElement('p'); text.textContent = info; dom.append(text) }
  return dom
}
function completion(item, after) {
  const option = { label: item.label, type: item.type, detail: item.detail, info: () => description(item.signature || item.valueType || item.label, item.info) }
  if (item.snippet) return snippetCompletion(item.snippet, option)
  if (item.type === 'function' && !/^\s*[(\[]/.test(after)) {
    const delimiter = item.macroDelimiter || '(', closing = delimiter === '[' ? ']' : ')'
    // The cursor starts inside the call; signature help supplies the arguments.
    return snippetCompletion(item.label + delimiter + (item.parameters?.length ? '${}' : '') + closing, option)
  }
  return option
}
export function createOjAssistant(language, onStatus = () => {}) {
  let worker, destroyed = false, failed = false, sequence = 0, fallback, loadingFallback
  const pending = new Map()
  function finishAll() { for (const item of pending.values()) { clearTimeout(item.timer); item.resolve(null) } pending.clear() }
  function degrade() {
    if (destroyed || failed) return
    failed = true; worker?.terminate(); worker = null; finishAll()
    loadingFallback = catalogLoaders[language]().then(module => { fallback = module.default; if (!destroyed) onStatus('basic') })
      .catch(() => { if (!destroyed) onStatus('unavailable') })
  }
  onStatus('loading')
  try {
    worker = new Worker(new URL('./ojAssist.worker.js', import.meta.url), { type: 'module', name: 'oj-code-assist' })
    worker.onmessage = ({ data }) => {
      const item = pending.get(data.id); if (!item) return
      clearTimeout(item.timer); pending.delete(data.id); item.resolve(data.error ? null : data.result)
    }
    worker.onerror = event => { event.preventDefault(); degrade() }
    worker.onmessageerror = degrade
  } catch { degrade() }
  function request(kind, code, position, explicit = false) {
    if (destroyed || !worker || code.length > 65536) return { promise: Promise.resolve(null), cancel() {} }
    const id = ++sequence
    let resolve
    const promise = new Promise(done => { resolve = done })
    const cancel = () => { const item = pending.get(id); if (item) { clearTimeout(item.timer); pending.delete(id); item.resolve(null) } }
    const timer = setTimeout(() => { cancel(); if (kind === 'init') degrade() }, kind === 'init' ? 15000 : 2500)
    pending.set(id, { resolve, timer })
    try { worker.postMessage({ id, kind, language, code, position, explicit }) } catch { degrade() }
    return { promise, cancel }
  }
  if (worker) request('init', '', 0).promise.then(result => { if (!destroyed && !failed) result ? onStatus('ready') : degrade() })
  async function query(kind, code, position, explicit = false) {
    if (!failed) return request(kind, code, position, explicit).promise
    await loadingFallback
    if (!fallback || destroyed || kind !== 'complete') return null
    const prefix = code.slice(0, position).match(/[\p{L}_][\p{L}\p{N}_]*!?$/u)?.[0] || '', from = position - prefix.length
    if (!prefix && !explicit || /(?:\.|::|->)\s*$/.test(code.slice(0, from))) return null
    const options = [...fallback.globals, ...Object.entries(fallback.snippets).map(([label, item]) => ({ label, snippet: item.body, type: 'keyword', detail: item.detail })),
      ...fallback.keywords.map(label => ({ label, type: 'keyword' }))]
      .filter(item => !prefix || item.label.toLowerCase().includes(prefix.toLowerCase())).slice(0, 150)
    return { from, to: position, options }
  }
  const source = async context => {
    if (context.state.readOnly || context.view?.composing) return null
    if (failed) {
      for (let node = syntaxTree(context.state).resolveInner(context.pos, -1); node; node = node.parent) {
        if (/Comment|String|CharLiteral|CharacterLiteral/.test(node.name)) return null
      }
    }
    const document = context.state.doc, position = context.pos, code = document.toString()
    const job = failed ? { promise: query('complete', code, position, context.explicit), cancel() {} } : request('complete', code, position, context.explicit)
    context.addEventListener('abort', job.cancel, { onDocChange: true })
    const result = await job.promise
    if (!result || context.aborted || destroyed || context.view && (context.view.state.doc !== document || context.view.state.selection.main.head !== position)) return null
    return { from: result.from, to: result.to, validFor: /^[\p{L}\p{N}_!]*$/u, options: result.options.map(item => completion(item, code.slice(result.to))) }
  }
  const signatureEffect = StateEffect.define()
  const signatureState = StateField.define({
    create: () => null,
    update(value, transaction) {
      if (transaction.docChanged || transaction.selection) value = null
      for (const effect of transaction.effects) if (effect.is(signatureEffect)) value = effect.value
      return value
    },
    provide: field => showTooltip.from(field)
  })
  const signatures = ViewPlugin.fromClass(class {
    constructor(view) { this.view = view; this.version = 0; this.schedule() }
    update(update) { if (update.docChanged || update.selectionSet || update.focusChanged) this.schedule() }
    schedule() {
      clearTimeout(this.timer); const generation = ++this.version
      if (!this.view.hasFocus || this.view.composing || this.view.state.readOnly) return
      this.timer = setTimeout(async () => {
        const document = this.view.state.doc, position = this.view.state.selection.main.head
        const result = await query('signature', document.toString(), position)
        if (generation !== this.version || destroyed || this.view.state.doc !== document || this.view.state.selection.main.head !== position || !this.view.hasFocus) return
        this.view.dispatch({ effects: signatureEffect.of(result ? {
          pos: position, above: true, strictSide: false, create: () => {
            const dom = documentOwner(result); return { dom }
          }
        } : null) })
      }, 180)
    }
    destroy() { this.version++; clearTimeout(this.timer) }
  })
  function documentOwner(result) {
    const dom = document.createElement('div'); dom.className = 'oj-editor-signature'
    const title = document.createElement('div'); title.append(document.createTextNode(result.label + '('))
    result.parameters.forEach((parameter, i) => {
      if (i) title.append(document.createTextNode(', '))
      const part = document.createElement(i === result.activeParameter ? 'strong' : 'span'); part.textContent = parameter; title.append(part)
    })
    title.append(document.createTextNode(')' + (result.returnType ? ' → ' + result.returnType : ''))); dom.append(title)
    return dom
  }
  return {
    extensions: [
      autocompletion({ override: [source], activateOnTypingDelay: 160, maxRenderedOptions: 12, tooltipClass: () => 'oj-editor-completions', icons: true }),
      signatureState, signatures,
      hoverTooltip(async (view, position) => {
        if (view.composing) return null
        const document = view.state.doc, result = await query('hover', document.toString(), position)
        return result && !destroyed && view.state.doc === document ? { pos: result.from, end: result.to, above: true, create: () => ({ dom: description(result.signature || result.name, result.info) }) } : null
      }, { hoverTime: 400 }),
      linter(async view => {
        if (view.composing || view.state.readOnly) return []
        const document = view.state.doc, result = await query('diagnostics', document.toString(), 0)
        return !destroyed && view.state.doc === document ? result || [] : []
      }, { delay: 700 })
    ],
    destroy(view) { destroyed = true; if (view) closeCompletion(view); worker?.terminate(); worker = null; finishAll(); fallback = null }
  }
}
