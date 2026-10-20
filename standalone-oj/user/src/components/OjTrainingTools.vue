<template>
  <section class="learning-tools">
    <div class="learning-section-heading"><div><h2><UiIcon name="code" :size="18" />训练工具箱</h2><p>输入输出骨架、标准库用法、复杂度与常见陷阱。</p></div><XiaoRuan mood="coding" :size="64" /></div>
    <div class="learning-tool-types"><button :class="{active:section==='stl'}" @click="section='stl'">C++ STL 速查 <small>{{ STL_ENTRIES.length }}</small></button><button :class="{active:section==='io'}" @click="section='io'">五语言输入输出</button></div>
    <template v-if="section==='stl'">
      <div class="learning-tool-filters"><label class="learning-search"><UiIcon name="search" :size="16" /><input v-model="search" placeholder="搜索容器、算法或用法" aria-label="搜索 STL" /></label><select v-model="category" aria-label="STL 分类"><option value="">全部分类</option><option v-for="c in STL_CATEGORIES" :key="c.key" :value="c.key">{{ c.label }}</option></select></div>
      <div class="learning-tool-layout"><nav aria-label="STL 条目"><button v-for="entry in entries" :key="entry.id" :class="{active:entry.id===chosen?.id}" @click="selected=entry.id"><strong>{{ entry.name }}</strong><span>{{ entry.summary }}</span></button><p v-if="!entries.length">暂无匹配，试试其他关键词。</p></nav><article v-if="chosen" class="learning-tool-detail"><header><span>{{ STL_CATEGORIES.find(c=>c.key===chosen.category)?.label }}</span><h3>{{ chosen.name }}</h3><p>{{ chosen.summary }}</p></header><div class="learning-code"><div class="learning-code-tabs"><span>C++</span><button @click="copy(chosen.code)"><UiIcon name="copy" :size="13" />{{ copied?'已复制':'复制' }}</button></div><pre tabindex="0"><code v-html="highlightSolutionCode(chosen.code,'cpp')" /></pre></div><dl><template v-if="chosen.complexity"><dt>复杂度</dt><dd>{{ chosen.complexity }}</dd></template><template v-if="chosen.pitfalls"><dt>常见陷阱</dt><dd>{{ chosen.pitfalls }}</dd></template></dl><p class="learning-footnote">代码为用法片段，需要相应头文件与上下文；不是独立题目的答案。</p><a v-if="chosen.url" :href="chosen.url" target="_blank" rel="noopener noreferrer" class="learning-resource-link">查阅 cppreference<UiIcon name="link" :size="12" /></a></article></div>
    </template>
    <article v-else class="learning-io"><h3>完整程序的起点</h3><p>以读取两个整数并输出和为例。请根据题目修改，题目的五语言完整题解仍在做题页面中。</p><div class="learning-code"><div class="learning-code-tabs" role="tablist" aria-label="输入输出模板语言"><button v-for="(name,key) in templateLanguages" :key="key" role="tab" :aria-selected="language===key" :class="{active:language===key}" @click="language=key">{{ name }}</button><button class="learning-code-copy" @click="copy(ioTemplates[language])"><UiIcon name="copy" :size="13" />{{ copied?'已复制':'复制' }}</button></div><pre tabindex="0"><code v-html="highlightSolutionCode(ioTemplates[language],language)" /></pre></div></article>
    <p v-if="copyError" class="learning-error" role="alert">{{ copyError }}</p>
  </section>
</template>
<script setup>
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import {STL_ENTRIES,STL_CATEGORIES} from '@/content/learning/stl'
import {ioTemplates} from '@/content/learning/templates'
import {languageNames} from '@/utils/ojDictionaries'
import {highlightSolutionCode} from '@/utils/ojSolutionContent'
import UiIcon from './UiIcon.vue'
import XiaoRuan from './XiaoRuan.vue'
const templateLanguages=Object.fromEntries(Object.keys(ioTemplates).map(key=>[key,languageNames[key]]))
const section=ref('stl'),search=ref(''),category=ref(''),selected=ref('vector'),language=ref('cpp'),copied=ref(false),copyError=ref('');let timer
const entries=computed(()=>STL_ENTRIES.filter(e=>(!category.value||e.category===category.value)&&[e.name,e.summary,e.code,e.complexity,e.pitfalls].join(' ').toLowerCase().includes(search.value.trim().toLowerCase())))
const chosen=computed(()=>entries.value.find(e=>e.id===selected.value)||entries.value[0])
watch(()=>[language.value,chosen.value?.id],()=>{copied.value=false;copyError.value='';clearTimeout(timer)})
async function copy(code){try{await navigator.clipboard.writeText(code);copied.value=true;copyError.value='';clearTimeout(timer);timer=setTimeout(()=>copied.value=false,1600)}catch{copyError.value='复制失败，请选中代码后复制。'}}
onBeforeUnmount(()=>clearTimeout(timer))
</script>
