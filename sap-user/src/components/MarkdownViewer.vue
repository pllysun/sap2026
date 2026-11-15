<template>
  <pre v-if="isPlain" class="md-plain">{{ content }}</pre>
  <div v-else class="markdown-body" v-html="html" />
</template>

<script setup>
import { computed } from 'vue'
import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

const props = defineProps({
  content: { type: String, default: '' },
  format: { type: String, default: 'markdown' },
  /** Markdown 文件地址；用于把相对图片/链接解析到文件所在目录 */
  baseUrl: { type: String, default: '' }
})

const isPlain = computed(() => props.format === 'plain')

// 与笔记详情保持同一套渲染参数，避免前台/后台/作业三处换行行为不一致。
const markdown = new Marked({
  renderer: {
    code({ text, lang }) {
      const language = lang?.trim().split(/\s+/)[0]
      const highlighted = language && hljs.getLanguage(language)
        ? hljs.highlight(text, { language }).value
        : hljs.highlightAuto(text).value
      return `<pre><code class="hljs">${highlighted}</code></pre>`
    }
  },
  breaks: true,
  gfm: true
})

const html = computed(() => {
  if (!props.content) return ''
  let clean
  try {
    clean = DOMPurify.sanitize(markdown.parse(props.content), {
      FORBID_TAGS: ['style', 'script', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'select', 'textarea', 'link', 'meta'],
      FORBID_ATTR: ['style', 'srcset']
    })
  } catch {
    return '<p>Markdown 解析失败</p>'
  }
  return rewriteUrls(clean)
})

/** 解析相对地址；只允许 http/https 链接，图片额外要求 https。 */
function resolveUrl(value) {
  if (!value) return ''
  if (/^(mailto:|tel:)/i.test(value)) return value
  try {
    const url = new URL(value, props.baseUrl || window.location.href)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : ''
  } catch {
    return ''
  }
}

/** 给外链补 target/rel，给图片补懒加载，并去掉不合法的图片地址。 */
function rewriteUrls(input) {
  if (!input || typeof DOMParser === 'undefined') return input
  const doc = new DOMParser().parseFromString(input, 'text/html')

  doc.querySelectorAll('a[href]').forEach(link => {
    const href = link.getAttribute('href') || ''
    if (!href || href.startsWith('#')) return
    const resolved = resolveUrl(href)
    if (!resolved) {
      link.removeAttribute('href')
      return
    }
    link.setAttribute('href', resolved)
    link.setAttribute('target', '_blank')
    link.setAttribute('rel', 'noopener noreferrer')
  })

  doc.querySelectorAll('img[src]').forEach(img => {
    const resolved = resolveUrl(img.getAttribute('src') || '')
    if (!resolved || !resolved.startsWith('https://')) {
      img.remove()
      return
    }
    img.setAttribute('src', resolved)
    img.setAttribute('loading', 'lazy')
    img.setAttribute('decoding', 'async')
    img.setAttribute('referrerpolicy', 'no-referrer')
  })

  return doc.body.innerHTML
}
</script>

<style scoped>
.md-plain {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: var(--font, inherit);
  font-size: 0.9375rem;
  line-height: 1.85;
  color: var(--ink-700);
}

.markdown-body {
  line-height: 1.85;
  font-size: 0.9375rem;
  color: var(--ink-700);
  overflow-wrap: anywhere;
  min-width: 0;
}
.markdown-body :deep(h1) {
  font-size: 1.8em;
  margin: 1em 0 0.4em;
  padding-bottom: 0.3em;
  border-bottom: 2px solid var(--ink-100);
  color: var(--ink-900);
  font-weight: 800;
  line-height: 1.4;
}
.markdown-body :deep(h2) {
  font-size: 1.5em;
  margin: 1.4em 0 0.4em;
  padding-bottom: 0.2em;
  border-bottom: 1px solid var(--ink-100);
  color: var(--ink-800);
  font-weight: 700;
  line-height: 1.45;
}
.markdown-body :deep(h3) {
  font-size: 1.25em;
  margin: 1.2em 0 0.35em;
  color: var(--ink-800);
  font-weight: 700;
}
.markdown-body :deep(h4) {
  font-size: 1.1em;
  margin: 1em 0 0.3em;
  color: var(--ink-700);
  font-weight: 600;
}
.markdown-body :deep(p) {
  margin: 0.65em 0;
}
.markdown-body :deep(code) {
  background: rgba(59, 130, 246, 0.06);
  color: #b2555b;
  padding: 2px 7px;
  border-radius: 5px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.88em;
}
.markdown-body :deep(pre) {
  background: #f5f7fc;
  color: #33465f;
  padding: 20px;
  border-radius: 12px;
  overflow-x: auto;
  margin: 16px 0;
  border: 1px solid #dce2ee;
  line-height: 1.6;
  max-width: 100%;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
  font-size: 0.875rem;
  line-height: 1.8;
}
.markdown-body :deep(blockquote) {
  border-left: 4px solid var(--primary, #3b82f6);
  padding: 12px 20px;
  margin: 16px 0;
  background: #f2f4fa;
  color: var(--ink-600);
  border-radius: 0 10px 10px 0;
  font-style: normal;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  display: block;
  max-width: 100%;
  overflow-x: auto;
  margin: 16px 0;
  border-radius: 8px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--ink-100);
  padding: 10px 14px;
  text-align: left;
  min-width: 6em;
}
.markdown-body :deep(th) {
  background: linear-gradient(180deg, var(--ink-50), rgba(0, 0, 0, 0.02));
  font-weight: 700;
  color: var(--ink-700);
}
.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 10px;
  margin: 8px 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 10px 0;
}
.markdown-body :deep(ul) {
  list-style: disc;
}
.markdown-body :deep(ol) {
  list-style: decimal;
}
.markdown-body :deep(li) {
  margin: 5px 0;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 2px solid var(--ink-100);
  margin: 24px 0;
}
.markdown-body :deep(a) {
  color: var(--primary, #3b82f6);
  text-decoration: none;
  border-bottom: 1px dashed rgba(59, 130, 246, 0.3);
}
.markdown-body :deep(a:hover) {
  border-bottom-style: solid;
  border-bottom-color: var(--primary);
}
.markdown-body :deep(strong) {
  font-weight: 700;
  color: var(--ink-800);
}
.markdown-body :deep(em) {
  font-style: italic;
  color: var(--ink-600);
}

@media (max-width: 768px) {
  .markdown-body :deep(pre) {
    padding: 14px;
    border-radius: 8px;
    font-size: 0.8rem;
  }
  .markdown-body :deep(h1) {
    font-size: 1.5em;
  }
  .markdown-body :deep(h2) {
    font-size: 1.3em;
  }
}
@media (max-width: 480px) {
  .markdown-body :deep(th),
  .markdown-body :deep(td) {
    padding: 8px 10px;
    min-width: 5em;
  }
}
</style>
