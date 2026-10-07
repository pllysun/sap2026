<template>
  <div class="page oj-list oj-studio">
    <PageHeader title="算法题库" label="" />
    <div class="oj-library-tabs" role="tablist" aria-label="题库视图">
      <button role="tab" :aria-selected="tab==='problems'" :class="{active:tab==='problems'}" @click="chooseTab('problems')"><UiIcon name="code" :size="16" />全部题目 <small v-if="libraryTotal!==null">{{ libraryTotal }}</small></button>
      <button role="tab" :aria-selected="tab==='sets'" :class="{active:tab==='sets'}" @click="chooseTab('sets')"><UiIcon name="layers" :size="16" />题单</button>
      <button role="tab" :aria-selected="tab==='ranking'" :class="{active:tab==='ranking'}" @click="chooseTab('ranking')"><UiIcon name="chart" :size="16" />提交记录</button>
    </div>
    <div v-show="tab==='problems'" class="oj-discovery">
      <aside class="oj-filter-rail" aria-label="题目筛选">
        <div class="oj-filter-title"><span><UiIcon name="filter" :size="16" />筛选题目</span><button :disabled="!hasFilters" @click="resetFilters">重置</button></div>
        <div class="oj-difficulty-picker"><span class="oj-field-label">难度</span><div role="group" aria-label="题目难度"><button v-for="d in difficultyOptions" :key="d.value" :class="[{active:difficulty===d.value},d.value.toLowerCase()]" :aria-pressed="difficulty===d.value" @click="difficulty=d.value;search()">{{ d.label }}</button></div></div>
        <label><span class="oj-field-label">算法类型</span><OjSelect v-model="tag" :options="tagOptions" label="算法类型" :disabled="filtersLoading" @change="scheduleSearch" /></label>
        <label><span class="oj-field-label">题目来源</span><OjSelect v-model="source" :options="sourceOptions" label="题目来源" :disabled="filtersLoading" @change="scheduleSearch" /></label>
        <label><span class="oj-field-label">编程模式</span><OjSelect v-model="mode" :options="modeOptions" label="做题模式" @change="scheduleSearch" /></label>
        <div class="oj-page-progress" v-if="loaded && rows.length"><div class="oj-page-progress-label"><span>本页通过</span><strong>{{ pageAccepted }}<small> / {{ rows.length }}</small></strong></div><div class="oj-progress-track"><i :style="{width:100*pageAccepted/rows.length+'%'}" /></div><div class="oj-progress-legend"><span><i class="ac" />已通过</span><span><i class="wrong" />答案错误</span><span><i class="issue" />运行异常</span></div></div>
      </aside>
      <section class="oj-catalog" aria-label="全部题目" :aria-busy="loading">
        <form class="oj-catalog-toolbar" @submit.prevent="search">
          <div class="oj-catalog-heading"><div><UiIcon name="list" :size="17" /><h2>题目</h2><span>{{ total }} 道</span></div><span v-if="loading&&loaded" class="oj-catalog-updating" role="status"><i />更新中</span><span v-else class="oj-catalog-page">{{ page }} / {{ Math.max(1,Math.ceil(total/20)) }} 页</span></div>
          <div class="oj-catalog-search-line"><label class="oj-search"><UiIcon name="search" :size="16" /><input v-model="keyword" placeholder="搜索题目、算法类型或来源" aria-label="搜索算法题" /><button v-if="keyword" type="button" aria-label="清除关键词" @click="keyword='';search()"><UiIcon name="close" :size="14" /></button></label><button class="btn btn--primary" type="submit">搜索</button></div>
          <div v-if="activeFilters.length" class="oj-active-filters" aria-label="已选筛选条件"><button v-for="f in activeFilters" :key="f.key" type="button" :aria-label="'移除筛选：'+f.label" @click="removeFilter(f.key)">{{ f.label }}<UiIcon name="close" :size="12" /></button><button class="oj-clear-filters" type="button" @click="resetFilters">清除全部</button></div>
        </form>
      <p v-if="filtersError" class="oj-error" role="alert">{{ filtersError }} <button class="btn btn--sm" @click="loadFilters(true)">重试筛选项</button></p>
      <p v-if="error" class="oj-error" role="alert">{{ error }} <button class="btn btn--sm" @click="load()">重试</button></p>
      <div v-if="!loaded&&loading" class="oj-catalog-skeleton" role="status" aria-label="正在加载题目"><div v-for="i in 6" :key="i"><i /><span /><b /></div></div>
      <div v-else-if="!rows.length" class="oj-catalog-empty"><UiIcon name="search" :size="30" /><h2>{{ hasFilters?'暂无匹配题目':'暂无题目' }}</h2><button v-if="hasFilters" class="btn btn--secondary" @click="resetFilters">清除筛选</button></div>
      <div v-else class="oj-problem-list oj-catalog-table" :class="{'is-updating':loading}">
        <div class="oj-catalog-head"><span>题目</span><span>难度</span><span>来源与模式</span><span /></div>
        <article v-for="(p,index) in rows" :key="p.id" class="oj-problem-row oj-catalog-row" :style="{'--row-delay':Math.min(index,7)*25+'ms'}">
          <div class="oj-catalog-title"><OjProblemProgress :progress="p.progress" /><div><h2><router-link :to="problemLink(p.id)">{{ p.title }}</router-link></h2><div class="oj-catalog-tags"><button v-for="t in p.tags" :key="t" :aria-label="'筛选算法：'+t" @click="tag=t;search()">{{ t }}</button></div></div></div>
          <span :class="['oj-difficulty',p.difficulty.toLowerCase()]"><i />{{ levels[p.difficulty] }}</span>
          <div class="oj-catalog-source"><strong>{{ p.sourcePlatform }}</strong><div><span v-for="m in p.modes" :key="m">{{ m==='FUNCTION'?'核心函数':'完整程序' }}</span></div></div>
          <router-link :to="problemLink(p.id)" class="oj-catalog-entry" :aria-label="'做题：'+p.title"><UiIcon name="code" :size="14" /><span>做题</span></router-link>
        </article>
      </div>
      <div v-if="total>20" class="oj-catalog-pagination"><span>第 {{ page }} 页 / 共 {{ Math.ceil(total/20) }} 页</span><div><button class="btn btn--secondary btn--sm" :disabled="page===1||loading" @click="changePage(-1)"><UiIcon name="chevron-left" :size="16" />上一页</button><button class="btn btn--secondary btn--sm" :disabled="page*20>=total||loading" @click="changePage(1)">下一页<UiIcon name="chevron-right" :size="16" /></button></div></div>
      </section>
    </div>
    <section v-show="tab==='ranking'" class="oj-ranking-panel oj-submission-board"><div class="oj-ranking-heading"><div><h2><UiIcon name="chart" :size="18" />提交记录</h2><p>按独立通过题数排序 · 前 100 名</p></div><button class="btn btn--secondary" @click="loadRanking" :disabled="rankingLoading"><UiIcon name="refresh" :size="14" />刷新记录</button></div><div class="oj-board-totals"><span>题库 <strong>{{ rankingTotals.total }}</strong> 道</span><span v-for="d in difficultyStats" :key="d.key" :class="d.key">{{ d.label }} {{ rankingTotals[d.key] }}</span></div><p v-if="rankingError" class="oj-error" role="alert">{{ rankingError }}</p><p v-if="rankingLoading" class="oj-console-hint" role="status">正在加载提交统计…</p><div v-else-if="!ranking.length" class="oj-empty"><h2>暂无提交记录</h2></div><TransitionGroup name="oj-board" tag="div"><div v-for="(r,i) in ranking" :key="r.studentId" class="oj-submission-summary"><span :class="['oj-rank',{top:i<3}]">{{ String(i+1).padStart(2,'0') }}</span><div class="oj-submission-details"><div class="oj-submission-top"><div class="oj-participant"><strong>{{ r.name || '未填写姓名' }} <small>（{{ r.nickname }}）</small></strong><span class="oj-student-id">学号 / 账号 {{ r.studentId }}</span></div><div class="oj-solved-total" :aria-label="'通过 '+r.acCount+' 题，共 '+rankingTotals.total+' 题'"><strong>{{ r.acCount }}</strong><span>/ {{ rankingTotals.total }}</span><small>已通过 / 总题数</small></div></div><div class="oj-difficulty-progress"><div v-for="d in difficultyStats" :key="d.key" :class="d.key"><span>{{ d.label }} <b>{{ r[d.key+'Ac'] }} / {{ rankingTotals[d.key] }}</b></span><div class="oj-progress-track"><i :style="{width:progress(r[d.key+'Ac'],rankingTotals[d.key])+'%'}" /></div></div></div></div></div></TransitionGroup></section>
    <ProblemSets v-if="setsVisited" v-show="tab==='sets'" :key="user.user?.id" />
  </div>
</template>
<script setup>
import {ref,computed,onMounted,onBeforeUnmount,watch,defineAsyncComponent} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import UiIcon from '@/components/UiIcon.vue'
import OjSelect from '@/components/OjSelect.vue'
import OjProblemProgress from '@/components/OjProblemProgress.vue'
import request from '@/utils/request'
import {useUserStore} from '@/stores/user'
import {readCatalogQuery,catalogQuery} from '@/utils/ojNavigation'
import './oj.css'
import './catalog.css'
import './studio.css'
const ProblemSets=defineAsyncComponent(()=>import('./ProblemSets.vue'))
const difficultyOptions=[{value:'',label:'全部难度'},{value:'EASY',label:'简单'},{value:'MEDIUM',label:'中等'},{value:'HARD',label:'困难'}]
const levels={EASY:'简单',MEDIUM:'中等',HARD:'困难'}
const route=useRoute(),router=useRouter(),tabValue=value=>['sets','ranking'].includes(value)?value:'problems'
const user=useUserStore(),initial=readCatalogQuery(route.query),setsVisited=ref(route.query.tab==='sets')
const tab=ref(tabValue(route.query.tab)),tag=ref(initial.tag),source=ref(initial.source),mode=ref(initial.mode),filters=ref({tags:[],sources:[]}),filtersLoading=ref(false),filtersError=ref('')
const tagOptions=computed(()=>[{value:'',label:'全部算法类型'},...filters.value.tags.map(value=>({value,label:value}))])
const sourceOptions=computed(()=>[{value:'',label:'全部来源'},...filters.value.sources.map(value=>({value,label:value}))])
const modeOptions=[{value:'',label:'全部模式'},{value:'STDIO',label:'完整程序'},{value:'FUNCTION',label:'核心函数'}]
const rows=ref([]),keyword=ref(initial.keyword),difficulty=ref(initial.difficulty),page=ref(initial.page),total=ref(0),libraryTotal=ref(null),loaded=ref(false),loading=ref(false),error=ref(''),ranking=ref([]),rankingError=ref(''),rankingLoading=ref(false),rankingTotals=ref({total:0,easy:0,medium:0,hard:0})
const hasFilters=computed(()=>Boolean(keyword.value||difficulty.value||tag.value||source.value||mode.value))
const difficultyStats=[{key:'easy',label:'简单'},{key:'medium',label:'中等'},{key:'hard',label:'困难'}]
function progress(passed,total){return Number(total)>0?Math.min(100,100*Number(passed)/Number(total)):0}
let generation=0,disposed=false,progressTimer,searchTimer,listRequest,filterRequest,rankRequest,statusRequest,filtersAt=0
const cancelled=e=>e.code==='ERR_CANCELED'||e.name==='CanceledError'
const state=()=>({keyword:keyword.value,difficulty:difficulty.value,tag:tag.value,source:source.value,mode:mode.value,page:page.value})
const pageAccepted=computed(()=>rows.value.filter(p=>p.progress?.state==='AC').length)
const activeFilters=computed(()=>{const applied=readCatalogQuery(route.query);return [{key:'keyword',label:applied.keyword},{key:'difficulty',label:levels[applied.difficulty]},{key:'tag',label:applied.tag},{key:'source',label:applied.source},{key:'mode',label:applied.mode==='FUNCTION'?'核心函数':applied.mode==='STDIO'?'完整程序':''}].filter(f=>f.label)})
function removeFilter(key){({keyword,difficulty,tag,source,mode})[key].value='';search()}
function problemLink(id){return {path:'/oj/'+id,query:{from:router.resolve({path:'/oj',query:catalogQuery(readCatalogQuery(route.query))}).href}}}
function chooseTab(value){router.push({query:catalogQuery(state(),value)})}
function persist(){const query=catalogQuery(state(),tab.value);if(JSON.stringify(query)===JSON.stringify(catalogQuery(readCatalogQuery(route.query),tabValue(route.query.tab))))load();else router.push({query})}
function activate(){
  clearTimeout(searchTimer);listRequest?.abort();rankRequest?.abort();filterRequest?.abort();statusRequest?.abort();generation++
  if(tab.value==='sets')setsVisited.value=true
  if(tab.value==='problems'){load();loadFilters()}else if(tab.value==='ranking')loadRanking()
}
watch(()=>route.query,query=>{const next=readCatalogQuery(query);for(const [key,field] of Object.entries({keyword,difficulty,tag,source,mode,page}))field.value=next[key];tab.value=tabValue(query.tab);activate()})
watch(()=>user.user?.id,(id,previous)=>{if(previous==null||id===previous)return;rows.value=[];ranking.value=[];loaded.value=false;activate()})
function search(){clearTimeout(searchTimer);page.value=1;persist()}
function scheduleSearch(){clearTimeout(searchTimer);listRequest?.abort();statusRequest?.abort();generation++;searchTimer=setTimeout(search,180)}
function resetFilters(){keyword.value='';difficulty.value='';tag.value='';source.value='';mode.value='';search()}
function changePage(delta){page.value+=delta;persist()}
async function loadFilters(force=false){
  if(!force&&Date.now()-filtersAt<30000)return
  filterRequest?.abort();const controller=new AbortController();filterRequest=controller;filtersLoading.value=true;filtersError.value=''
  try{const{data}=await request.get('/api/oj/filters',{signal:controller.signal});if(!disposed&&!controller.signal.aborted){filters.value=data;filtersAt=Date.now()}}
  catch(e){if(!disposed&&!cancelled(e))filtersError.value=e.message}finally{if(filterRequest===controller)filtersLoading.value=false}
}
async function load(){
  listRequest?.abort();statusRequest?.abort();const controller=new AbortController();listRequest=controller;const version=++generation
  loading.value=true;error.value=''
  try{const{data}=await request.get('/api/oj/problems',{signal:controller.signal,params:{keyword:keyword.value,difficulty:difficulty.value,tag:tag.value,source:source.value,mode:mode.value,page:page.value}})
    if(version===generation&&!disposed&&!controller.signal.aborted){rows.value=data.records;total.value=Number(data.total);if(!hasFilters.value)libraryTotal.value=total.value;loaded.value=true}}
  catch(e){if(version===generation&&!disposed&&!cancelled(e))error.value=e.message}finally{if(version===generation)loading.value=false}
}
async function loadRanking(){
  rankRequest?.abort();const controller=new AbortController();rankRequest=controller;rankingLoading.value=true;rankingError.value=''
  try{const{data}=await request.get('/api/oj/leaderboard',{signal:controller.signal});if(!disposed&&!controller.signal.aborted){ranking.value=data.records;rankingTotals.value=data.totals;libraryTotal.value=Number(data.totals.total)}}
  catch(e){if(!disposed&&!cancelled(e))rankingError.value=e.message}finally{if(rankRequest===controller)rankingLoading.value=false}
}
async function updateProgress(){
  if(document.hidden||tab.value!=='problems'||loading.value||!rows.value.some(p=>p.progress?.pending)||statusRequest)return
  const version=generation,controller=new AbortController();statusRequest=controller
  try{const{data}=await request.get('/api/oj/progress',{signal:controller.signal,params:{ids:rows.value.map(p=>p.id).join(',')}})
    if(version===generation&&!disposed&&!controller.signal.aborted){const status=new Map(data.records.map(p=>[p.id,p.progress]));rows.value=rows.value.map(p=>({...p,progress:status.get(p.id)||p.progress}))}}
  catch(e){if(!cancelled(e)){/* A transient status failure is retried on the next interval. */}}finally{if(statusRequest===controller)statusRequest=null}
}
onMounted(()=>{activate();progressTimer=setInterval(updateProgress,10000)})
onBeforeUnmount(()=>{disposed=true;generation++;clearInterval(progressTimer);clearTimeout(searchTimer);for(const request of [listRequest,filterRequest,rankRequest,statusRequest])request?.abort()})
</script>
