<template>
  <div class="oj-solution-reader">
    <div v-if="loading" class="solution-loading" role="status"><span>正在加载题解…</span><i v-for="i in 5" :key="i" /></div>
    <div v-else-if="error" class="solution-message" role="alert"><UiIcon name="info" :size="20" /><h3>题解加载失败</h3><p>{{ error }}</p><button @click="load(true)">重新加载</button></div>
    <div v-else-if="result?.state==='LOCKED'" class="solution-message solution-locked"><span class="solution-lock-mark"><UiIcon name="lock" :size="23" /></span><h3>比赛结束后开放题解</h3><p>题解与参考代码暂未开放。</p><span v-if="Number(result.unlocksAt)>0" class="solution-unlock-time"><UiIcon name="clock" :size="13" />{{ formatOjDate(result.unlocksAt) }}</span><button @click="load(true)">检查开放状态</button></div>
    <div v-else-if="result?.state==='MISSING'" class="solution-message"><UiIcon name="book-open" :size="22" /><h3>题解正在整理</h3><button @click="load(true)">刷新</button></div>
    <article v-else-if="document" class="solution-article">
      <header class="solution-introduction"><span><UiIcon name="book-open" :size="14" />题解</span><h2>{{ document.title }}</h2><p>{{ document.summary }}</p></header>
      <nav class="solution-outline" aria-label="题解目录"><button v-for="section in sections" :key="section.key" @click="scrollTo(section.key)">{{ section.title }}</button><button @click="scrollTo('code')">通关代码</button></nav>
      <section v-for="(section,index) in sections" :id="`solution-${section.key}`" :key="section.key" class="solution-section">
        <h2><span>{{ String(index+1).padStart(2,'0') }}</span>{{ section.title }}</h2>
        <div class="solution-prose" v-html="section.html" />
        <figure v-if="section.diagram" class="solution-figure"><button :aria-label="'放大图解：'+section.diagram.caption" @click="enlarge(section.diagram)"><div v-html="section.diagram.html" /><span><UiIcon name="search" :size="12" />放大图解</span></button><figcaption>{{ section.diagram.caption }}</figcaption></figure>
      </section>
      <section id="solution-code" class="solution-section solution-code-section"><h2><span><UiIcon name="code" :size="14" /></span>通关代码</h2><p class="solution-code-help">{{ selectedMode==='FUNCTION' ? '只提交函数实现，输入与输出由平台驱动处理。' : '完整程序包含输入、处理和输出，可直接提交。' }}</p>
        <div v-if="modes.length>1" class="solution-mode-switch" role="group" aria-label="题解做题模式"><button v-for="m in modes" :key="m" :aria-pressed="selectedMode===m" :class="{active:selectedMode===m}" @click="selectedMode=m">{{ modeNames[m] }}</button></div>
        <div class="solution-code-panel">
          <header class="solution-code-header"><div role="tablist" aria-label="题解代码语言"><button v-for="(lang,index) in languages" :id="`solution-tab-${lang}`" :key="lang" role="tab" :aria-selected="selectedLanguage===lang" :tabindex="selectedLanguage===lang?0:-1" aria-controls="solution-code-display" :class="{active:selectedLanguage===lang}" @click="selectedLanguage=lang" @keydown="moveLanguage($event,index)">{{ languageNames[lang] }}</button></div><button class="solution-copy" :aria-label="copied?'代码已复制':'复制题解代码'" @click="copyCode"><UiIcon :name="copied?'check':'copy'" :size="14" />{{ copied?'已复制':'复制' }}</button></header>
          <div id="solution-code-display" role="tabpanel" :aria-labelledby="`solution-tab-${selectedLanguage}`" tabindex="0" class="solution-code-scroll"><pre><code :class="`language-${selectedLanguage}`" v-html="highlightedCode" /></pre></div>
          <footer><span>{{ modeNames[selectedMode] }} · {{ code.split('\n').length }} 行</span><button @click="$emit('use-code',{language:selectedLanguage,mode:selectedMode,code})"><UiIcon name="edit" :size="13" />放入编辑器</button></footer>
        </div><p v-if="copyError" class="solution-copy-error" role="alert">{{ copyError }}</p>
      </section>
    </article>
    <Teleport to="body"><dialog ref="figureDialog" class="solution-figure-dialog" aria-labelledby="solution-figure-title" @click="closeOnBackdrop" @close="unlockFigure"><header><strong id="solution-figure-title">图解</strong><button aria-label="关闭图解" @click="figureDialog.close()"><UiIcon name="close" :size="17" /></button></header><div v-if="largeFigure" class="solution-enlarged" v-html="largeFigure.html" /><p>{{ largeFigure?.caption }}</p></dialog></Teleport>
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount, nextTick } from 'vue'
import request from '@/utils/request'
import UiIcon from './UiIcon.vue'
import { languageNames, modeNames, formatOjDate } from '@/utils/ojDictionaries'
import { renderSolutionMarkdown, renderSolutionSvg, highlightSolutionCode } from '@/utils/ojSolutionContent'
import './oj-solution.css'
const props=defineProps({ problemId:[String,Number],setId:[String,Number],itemId:[String,Number],active:Boolean,language:String,mode:String,contestEnded:Boolean })
defineEmits(['use-code'])
const result=ref(null),loading=ref(false),error=ref(''),copied=ref(false),copyError=ref(''),selectedLanguage=ref(props.language||'cpp'),selectedMode=ref(props.mode||'STDIO'),figureDialog=ref(null),largeFigure=ref(null)
const languages=['c','cpp','java','python','rust']
let generation=0,disposed=false,unlockTimer,copyTimer,controller,figureLocked=false,originalOverflow=''
const document=computed(()=>result.value?.state==='READY'?result.value.document:null)
const modes=computed(()=>Object.keys(document.value?.codes||{}))
const sections=computed(()=>document.value?.sections.map(s=>({...s,html:renderSolutionMarkdown(s.markdown),diagram:s.diagram?{caption:s.diagram.caption,html:renderSolutionSvg(s.diagram.svg)}:null}))||[])
const code=computed(()=>document.value?.codes[selectedMode.value]?.[selectedLanguage.value]||'')
const highlightedCode=computed(()=>highlightSolutionCode(code.value,selectedLanguage.value))
async function load(force=false){
  if(disposed||loading.value||!props.problemId||result.value&&!force)return
  const current=++generation;loading.value=true;error.value='';clearTimeout(unlockTimer);controller?.abort();controller=new AbortController()
  try{
    const path=props.setId?`/api/oj/sets/${props.setId}/items/${props.itemId}/solution`:`/api/oj/problems/${props.problemId}/solution`
    const {data}=await request.get(path,{signal:controller.signal})
    if(disposed||current!==generation)return
    result.value=data
    if(data.state==='READY'&&!modes.value.includes(selectedMode.value))selectedMode.value=modes.value[0]
    if(data.state==='LOCKED'&&Number(data.unlocksAt)>Number(data.serverNow)){
      const delay=Math.min(2147483000,Number(data.unlocksAt)-Number(data.serverNow)+350)
      unlockTimer=setTimeout(()=>{if(props.active)load(true);else result.value=null},delay)
    }
  }catch(e){if(!disposed&&current===generation&&e.code!=='ERR_CANCELED')error.value=e.message||'请稍后再试'}finally{if(current===generation)loading.value=false}
}
watch(()=>props.active,active=>{if(active)load()}, {immediate:true})
watch(()=>props.contestEnded,ended=>{if(ended&&props.active&&result.value?.state==='LOCKED')load(true)})
watch(()=>[props.language,props.mode],([language,mode])=>{if(languages.includes(language))selectedLanguage.value=language;if(modes.value.includes(mode))selectedMode.value=mode})
watch(()=>[selectedLanguage.value,selectedMode.value],()=>{copied.value=false;copyError.value='';clearTimeout(copyTimer);documentElement('solution-code-display')?.scrollTo(0,0)})
function documentElement(id){return window.document.getElementById(id)}
function scrollTo(key){documentElement('solution-'+key)?.scrollIntoView({block:'start',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'})}
function moveLanguage(event,index){let next;if(['ArrowRight','ArrowLeft','Home','End'].includes(event.key)){event.preventDefault();next=event.key==='Home'?0:event.key==='End'?languages.length-1:(index+(event.key==='ArrowRight'?1:-1)+languages.length)%languages.length;selectedLanguage.value=languages[next];nextTick(()=>documentElement('solution-tab-'+languages[next])?.focus())}}
async function copyCode(){try{await navigator.clipboard.writeText(code.value);copied.value=true;copyError.value='';clearTimeout(copyTimer);copyTimer=setTimeout(()=>copied.value=false,1800)}catch{copyError.value='复制失败，请在代码区域选中并复制。'}}
function enlarge(diagram){largeFigure.value=diagram;if(!figureLocked){originalOverflow=window.document.body.style.overflow;window.document.body.style.overflow='hidden';figureLocked=true}figureDialog.value.showModal()}
function unlockFigure(){if(figureLocked){window.document.body.style.overflow=originalOverflow;figureLocked=false}}
function closeOnBackdrop(event){if(event.target!==figureDialog.value)return;const rect=figureDialog.value.getBoundingClientRect();if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)figureDialog.value.close()}
onBeforeUnmount(()=>{disposed=true;++generation;controller?.abort();clearTimeout(unlockTimer);clearTimeout(copyTimer);figureDialog.value?.close();unlockFigure()})
</script>
