<template><div class="oj-prose oj-markdown" v-html="html" /></template>
<script setup>
import { computed } from 'vue'
import { renderProblemMarkdown } from '@/utils/ojProblemContent'
const props = defineProps({ text: { type: String, default: '' } })
const html = computed(() => renderProblemMarkdown(props.text))
</script>
<style scoped>
.oj-markdown{white-space:normal;overflow-wrap:anywhere;min-width:0;line-height:1.85}
.oj-markdown :deep(p){margin:0 0 .85em}.oj-markdown :deep(> :last-child){margin-bottom:0}
.oj-markdown :deep(h1),.oj-markdown :deep(h2),.oj-markdown :deep(h3),.oj-markdown :deep(h4),.oj-markdown :deep(h5),.oj-markdown :deep(h6){line-height:1.55;color:var(--ink-800,#294967);margin:1.15em 0 .5em}
.oj-markdown :deep(h1){font-size:1.15em}.oj-markdown :deep(h2){font-size:1.1em}.oj-markdown :deep(h3),.oj-markdown :deep(h4),.oj-markdown :deep(h5),.oj-markdown :deep(h6){font-size:1em}
.oj-markdown :deep(ul),.oj-markdown :deep(ol){padding-left:1.6em;margin:.7em 0}.oj-markdown :deep(li+li){margin-top:.25em}
.oj-markdown :deep(blockquote){margin:.9em 0;padding:.6em 1em;border-left:3px solid #b5c8e1;background:#f5f8fc;color:#536a84}
.oj-markdown :deep(a){color:#366eaf;text-decoration:underline;text-underline-offset:3px}
.oj-markdown :deep(code){font:.92em/1.65 ui-monospace,SFMono-Regular,Consolas,monospace;background:#eef3fa;border-radius:4px;padding:.1em .35em;white-space:break-spaces}
.oj-markdown :deep(pre){margin:.85em 0;padding:1em;border:1px solid #dfe7f1;border-radius:8px;background:#f7f9fd;max-width:100%;overflow:auto}
.oj-markdown :deep(pre code){display:block;padding:0;background:none;white-space:pre;overflow-wrap:normal}
.oj-markdown :deep(.oj-markdown-table){width:100%;overflow:auto;margin:.85em 0}
.oj-markdown :deep(table){width:100%;border-collapse:collapse;font-size:.95em}
.oj-markdown :deep(th),.oj-markdown :deep(td){border:1px solid #dfe7f1;padding:.55em .8em;min-width:5em;text-align:left}
.oj-markdown :deep(th){background:#f0f4fa;color:#365274;font-weight:650}
.oj-markdown :deep(img){display:block;max-width:100%;height:auto;margin:.8em 0;border-radius:7px}
.oj-markdown :deep(hr){border:0;border-top:1px solid #e0e7f1;margin:1em 0}
.oj-markdown :deep(.hljs-keyword),.oj-markdown :deep(.hljs-selector-tag){color:#81509b}.oj-markdown :deep(.hljs-string){color:#377566}.oj-markdown :deep(.hljs-type),.oj-markdown :deep(.hljs-built_in){color:#336f94}.oj-markdown :deep(.hljs-number),.oj-markdown :deep(.hljs-literal){color:#99632d}.oj-markdown :deep(.hljs-comment){color:#728078}.oj-markdown :deep(.hljs-title){color:#3e6eac}.oj-markdown :deep(.hljs-meta){color:#81699e}
</style>
