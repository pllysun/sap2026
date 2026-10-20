import DOMPurify from 'dompurify'
import { Marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import './ojSolutionContent'

const escape = text => text.replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]))
const parser = new Marked({
  gfm: true, breaks: true,
  renderer: {
    code({ text, lang = '' }) {
      const language = lang.trim().split(/\s+/)[0].toLowerCase()
      const known = language && hljs.getLanguage(language)
      const code = known ? hljs.highlight(text, { language, ignoreIllegals: true }).value : escape(text)
      return `<pre><code${known ? ` class="hljs language-${escape(language)}"` : ''}>${code}\n</code></pre>`
    },
  },
})

// Problem fields can contain Markdown or safe HTML. All output crosses the same sanitizer.
export function renderProblemMarkdown(text = '') {
  const fragment = DOMPurify.sanitize(parser.parse(String(text ?? '')), {
    RETURN_DOM_FRAGMENT: true,
    ALLOWED_TAGS: ['p','br','ul','ol','li','strong','em','del','s','code','pre','span','table','thead','tbody','tr','th','td','blockquote','h1','h2','h3','h4','h5','h6','hr','a','img','sup','sub'],
    ALLOWED_ATTR: ['href','src','alt','title','class','colspan','rowspan'],
    FORBID_TAGS: ['style','script','iframe','object','embed','form','input','svg','math'],
  })
  for (const el of fragment.querySelectorAll('[class]')) {
    const classes = ['SPAN','CODE'].includes(el.tagName) ? el.className.split(/\s+/).filter(c => /^(hljs(?:-[\w-]+)?|language-[\w+-]+)$/.test(c)) : []
    if (classes.length) el.className = classes.join(' ')
    else el.removeAttribute('class')
  }
  for (const link of fragment.querySelectorAll('a[href]')) {
    link.target = '_blank'; link.rel = 'noopener noreferrer'
  }
  for (const image of fragment.querySelectorAll('img')) {
    let valid = false
    try { valid = ['http:','https:'].includes(new URL(image.getAttribute('src') || '', window.location.href).protocol) } catch {}
    if (!valid || !image.getAttribute('src')) { image.remove(); continue }
    image.loading = 'lazy'; image.decoding = 'async'; image.referrerPolicy = 'no-referrer'
  }
  for (const table of fragment.querySelectorAll('table')) {
    const wrap = document.createElement('div'); wrap.className = 'oj-markdown-table'
    table.replaceWith(wrap); wrap.append(table)
  }
  const result = document.createElement('div'); result.append(fragment)
  return result.innerHTML
}
