<template>
  <div class="page oj-workspace-page oj-studio" :class="{'oj-focus-mode':focusMode}">
    <div class="oj-workspace-topbar"><router-link :to="setId ? `/oj/sets/${setId}` : returnTo" class="oj-back"><UiIcon name="chevron-left" /> {{ setId ? setInfo?.name || '题单' : returnTo.startsWith('/home') ? '首页' : '算法题库' }}</router-link><span v-if="runtime?.available===false || runtime?.available && !Number(runtime.availableSlots)" class="oj-runtime-indicator" :class="{offline:runtime?.available===false,busy:runtime?.available && !Number(runtime.availableSlots)}"><i />{{ !runtime.available ? '运行服务暂不可用' : '运行资源忙碌' }}</span></div>
    <p v-if="error" class="oj-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="loading"><div class="loading__spinner" /></div>
    <template v-else-if="problem">
      <div class="oj-workspace-heading"><div><span class="oj-workspace-mark">&lt;/&gt;</span><div><span class="oj-eyebrow">{{ problem.sourcePlatform }} / {{ problem.sourceId || 'ALGORITHM' }}</span><h1>{{ problem.title }}</h1></div><span :class="['oj-difficulty', problem.difficulty.toLowerCase()]"><i />{{ levels[problem.difficulty] }}</span></div><button class="oj-focus-toggle" :aria-pressed="focusMode" @click="focusMode=!focusMode"><UiIcon :name="focusMode?'panel-top':'code'" :size="13" />{{ focusMode ? '返回双栏' : '专注写代码' }}</button></div>
      <div v-if="setId && setInfo" class="oj-set-workspace-strip"><strong>{{ setInfo.mode==='CONTEST'?'比赛':'练习' }} · {{ setInfo.name }}</strong><OjSelect :model-value="String(itemId)" :options="setNavigation" compact label="题单题目导航" @update:model-value="navigateSet" /><span v-if="setInfo.mode==='CONTEST'" class="oj-set-countdown">{{ setInfo.phase==='ENDED'?'已结束':'剩余 '+setCountdown }}</span><span v-if="!setCanSubmit">{{ setInfo.disqualified?'已取消参与资格':setInfo.phase==='ARCHIVED'?'已归档':!setInfo.joined?'未报名':'无法提交' }}</span><button class="oj-tool-button" @click="openDialog('ranking')"><UiIcon name="trophy" :size="15" />题单排名</button></div>
      <div class="oj-mobile-tabs"><button :class="{ active: panel==='statement' && statementView==='statement' }" @click="panel='statement';statementView='statement'">题目</button><button :class="{ active: panel==='statement' && statementView==='solution' }" @click="openSolution">题解</button><button :class="{ active: panel==='code' }" @click="panel='code'">代码与控制台</button></div>
      <div class="oj-workspace" :data-panel="panel">
        <section class="oj-statement" aria-label="题目与题解"><div class="oj-pane-heading"><nav class="oj-lesson-tabs" role="tablist" aria-label="题目内容"><button role="tab" :aria-selected="statementView==='statement'" :class="{active:statementView==='statement'}" @click="statementView='statement'"><UiIcon name="book-open" :size="14" />题目</button><button role="tab" :aria-selected="statementView==='solution'" :class="{active:statementView==='solution'}" @click="openSolution"><UiIcon name="code" :size="14" />题解</button></nav><nav v-show="statementView==='statement'" class="oj-statement-nav" aria-label="题目章节"><button @click="scrollSection('description')">描述</button><button @click="scrollSection('samples')">示例</button><button @click="scrollSection('source')">来源</button></nav></div><div v-show="statementView==='statement'" class="oj-statement-content">
          <div class="oj-tags"><span v-for="tag in problem.tags" :key="tag">{{ tag }}</span></div>
          <h2 id="oj-description">题目描述</h2><p class="oj-prose">{{ problem.description }}</p>
          <h2>输入格式</h2><p class="oj-prose">{{ problem.inputFormat }}</p>
          <h2>输出格式</h2><p class="oj-prose">{{ problem.outputFormat }}</p>
          <h2>数据范围</h2><p class="oj-prose">{{ problem.constraints }}</p>
          <div v-for="(sample, index) in problem.samples" :id="index===0?'oj-samples':undefined" :key="index" class="oj-sample"><div class="oj-sample-heading"><h2>示例 {{ index + 1 }}</h2><button class="oj-tool-button" @click="useSample(index)"><UiIcon name="terminal" :size="13" />带入测试</button></div><div class="oj-sample-io"><div><label>输入<button class="oj-copy-input" :aria-label="'复制示例 '+(index+1)+' 输入'" @click="copySample(sample.input,index)"><UiIcon :name="copiedSample===index?'check':'copy'" :size="13" />{{ copiedSample===index?'已复制':'复制' }}</button></label><pre>{{ sample.input }}</pre></div><div><label>输出</label><pre>{{ sample.expectedOutput }}</pre></div></div></div>
          <div id="oj-source" class="oj-source-note"><strong>题目来源</strong><p><a v-if="problem.sourceUrl" :href="problem.sourceUrl" target="_blank" rel="noopener noreferrer">{{ problem.sourcePlatform }} {{ problem.sourceId }} ↗</a><span v-else>{{ problem.sourcePlatform }}</span></p><p>{{ problem.sourceNote }}</p></div>
        </div><OjSolution v-if="solutionVisited" v-show="statementView==='solution'" :active="statementView==='solution'" :problem-id="problem.id" :set-id="setId" :item-id="itemId" :language="language" :mode="mode" :contest-ended="setInfo?.phase==='ENDED'" @use-code="useSolutionCode" /></section>
        <section class="oj-coding" aria-label="代码和控制台"><div class="oj-pane-heading"><strong><UiIcon name="code" :size="15" />代码工作台</strong></div>
          <div class="oj-workspace-shortcuts" aria-label="运行与提交记录"><button v-for="view in dialogViews" :key="view.value" @click="openDialog(view.value)"><UiIcon :name="view.icon" :size="13" />{{ view.label }}<i v-if="view.value==='result' && pending" class="oj-shortcut-pending" /></button></div>
          <div class="oj-editor-toolbar">
            <OjSelect compact v-model="language" :options="languageOptions" label="编程语言" :disabled="templateLoading" />
            <div class="oj-mode-switch" role="group" aria-label="做题模式"><button v-for="m in problem.modes" :key="m" :aria-pressed="mode === m" :class="{active:mode===m}" :disabled="templateLoading" @click="mode=m">{{ m === 'FUNCTION' ? '核心函数' : '完整程序' }}</button></div>
            <button class="oj-tool-button" :disabled="templateLoading" @click="restoreTemplate"><UiIcon name="refresh" :size="13" />恢复模板</button>
          </div>
          <div class="oj-mode-help"><details><summary><UiIcon name="info" :size="12" />{{ mode==='FUNCTION'?'函数模式说明':'输入输出说明' }}<UiIcon name="chevron-down" :size="12" /></summary><p>{{ mode === 'FUNCTION' ? '实现模板中的函数，平台负责读取输入、构造参数和输出结果。' : '提交完整程序，自行读取标准输入并将结果写入标准输出。' }}</p></details><span v-if="currentLanguage"><UiIcon name="clock" :size="12" />{{ currentLanguage.timeLimitMs }} ms <UiIcon name="cpu" :size="12" />{{ currentLanguage.memoryLimitMb }} MiB</span></div>
          <div class="oj-file-bar"><span><span class="oj-file-dot" /> {{ language==='java' && mode==='FUNCTION'?'Solution.java':filenames[language] }} <small>{{ mode === 'FUNCTION' ? '函数实现' : '完整程序' }}</small></span><button class="oj-tool-button" :disabled="formatting || templateLoading" @click="formatCurrent">{{ formatting ? '格式化中…' : '格式化代码' }}</button></div>
          <div v-if="templateLoading" class="oj-template-loading" role="status">正在整理代码模板…</div>
          <OjCodeEditor ref="editor" :key="language + ':' + mode" v-model="code" :language="language" :read-only="templateLoading" @format="formatCurrent" @run="send('run')" @submit="send('submit')" />
          <p v-if="draftError" class="oj-draft-error" role="alert">{{ draftError }}</p>
          <div class="oj-actions"><span><i class="oj-action-dot" />{{ code.split('\n').length }} 行 · {{ code.length }} 字符</span><button class="btn btn--secondary" :disabled="sending || pending || templateLoading || !code.trim() || !setCanSubmit" @click="send('run')" title="Ctrl / Cmd + Enter"><UiIcon name="play" :size="13" />{{ pending && currentJob?.kind === 'RUN' ? '运行中…' : '运行代码' }}</button><button class="btn btn--primary" :disabled="sending || pending || templateLoading || !code.trim() || !setCanSubmit" @click="send('submit')" title="Ctrl / Cmd + Shift + Enter"><UiIcon name="send" :size="13" />{{ pending && currentJob?.kind === 'SUBMIT' ? '判题中…' : '提交判题' }}</button></div>
          <section id="oj-test-input" class="oj-input-panel" aria-label="测试输入">
            <header><span><UiIcon name="terminal" :size="14" /><strong>测试输入</strong></span><label class="oj-input-toggle"><input v-model="custom" type="checkbox" /><span />自定义输入</label></header>
            <div class="oj-input-content">
              <textarea v-if="custom" v-model="input" class="oj-test-input" aria-label="自定义测试输入" maxlength="1048576" spellcheck="false" placeholder="按照题目输入格式填写测试数据" />
              <div v-else class="oj-input-samples"><button v-for="(sample,index) in problem.samples" :key="index" :class="{active:inputSample===index}" :aria-pressed="inputSample===index" @click="inputSample=index">示例 {{ index+1 }}</button><pre v-if="problem.samples[inputSample]">{{ problem.samples[inputSample].input }}</pre><p v-else class="oj-modal-hint">该题没有公开样例。</p></div>
              <div v-if="!custom && problem.samples[inputSample]" class="oj-input-sample-actions"><button class="oj-tool-button" @click="useSample(inputSample,false)"><UiIcon name="edit" :size="12" />编辑此输入</button></div><p class="oj-input-note">{{ custom ? '自定义输入用于运行代码；正式提交使用完整测试集。' : '运行代码会校验全部公开样例。' }}</p>
            </div>
          </section>
        </section>
      </div>
    </template>

    <OjWorkspaceDialog :view="dialogView" :open="Boolean(dialogView)" :title="dialogTitle" :icon="dialogIcon" :context="problem?.title || ''" @close="dialogView=''">
      <template #navigation><nav class="oj-dialog-navigation" aria-label="工作台记录"><button v-for="view in dialogViews" :key="view.value" :class="{active:dialogView===view.value}" :aria-pressed="dialogView===view.value" @click="openDialog(view.value)"><UiIcon :name="view.icon" :size="14" />{{ view.label }}<i v-if="view.value==='result' && pending" class="oj-shortcut-pending" /></button></nav></template>
      <OjRunResult v-show="dialogView==='result'" :job="currentJob" :pending="resultPending" :loading="jobLoading" :error="jobError" :retryable="Boolean(requestedJobId)" :performance="performance" :performance-loading="performanceLoading" :performance-error="performanceError" :limits="resultLimits" @resume="resumeEditing" @restore="restoreSubmission" @retry="showJob(requestedJobId,true,true)" @retry-performance="loadPerformance(true)">
        <template v-if="currentJob?.status==='AC' && currentJob.kind==='SUBMIT' && nextSetItem && setCanSubmit" #next><button class="oj-next-question oj-result-next" @click="navigateSet(nextSetItem.id)"><span><small>下一题 · {{ nextSetItem.label }}</small><strong>{{ nextSetItem.title }}</strong></span><UiIcon name="chevron-right" :size="14" /></button></template>
      </OjRunResult>
      <section v-show="dialogView==='history'" class="oj-history-view" aria-label="我的提交记录">
        <div class="oj-modal-section-heading"><div><h3>我的提交记录</h3><p>{{ setId ? '本题单 · 当前题目' : '当前题目' }} · 当前账号</p></div><button class="oj-modal-button" :disabled="historyLoading" @click="loadHistory(true)"><UiIcon name="refresh" :size="13" />刷新</button></div>
        <div v-if="historyLoading && !history.length" class="oj-modal-loading" role="status"><i />正在加载提交记录…</div>
        <div v-else-if="historyError" class="oj-modal-error" role="alert"><p>{{ historyError }}</p><button class="oj-modal-button" @click="loadHistory(true)">重试</button></div>
        <template v-else><div v-if="!history.length" class="oj-modal-empty"><UiIcon name="history" :size="22" /><h3>暂无提交记录</h3><p>运行与提交记录会保存在当前账号下。</p></div>
          <div v-else class="oj-history-table"><div class="oj-history-table-head"><span>提交</span><span>判题结果</span><span>通过用例</span><span>提交时间</span><span /></div><button v-for="job in history" :key="job.id" class="oj-history-table-row" @click="showJob(job.id)"><span class="oj-history-identity"><strong>{{ languageNames[job.language] }}<small>{{ modeNames[job.mode] }}</small></strong><small>{{ kindNames[job.kind] }} · #{{ job.id }}</small></span><span><OjStatusBadge :status="job.status" /></span><span class="oj-history-resources">{{ job.passedCases ?? '—' }} <small>/ {{ job.totalCases ?? '—' }}</small></span><span class="oj-history-date">{{ formatOjDate(job.acceptedAt || job.createdAt) }}</span><span class="oj-history-open">查看<UiIcon name="chevron-right" :size="14" /></span></button></div>
          <div class="oj-modal-pagination"><button class="oj-modal-button" :disabled="historyPage===1" @click="historyPage--;loadHistory()"><UiIcon name="chevron-left" :size="15" />上一页</button><span>第 {{ historyPage }} 页</span><button class="oj-modal-button" :disabled="history.length<50" @click="historyPage++;loadHistory()">下一页<UiIcon name="chevron-right" :size="15" /></button></div>
        </template>
      </section>
      <section v-show="dialogView==='ranking'" class="oj-ranking-view" aria-label="排行">
        <div class="oj-modal-section-heading"><div><h3>{{ setId ? '题单排名' : '本题排行' }}</h3><p>{{ setId ? setInfo?.mode==='CONTEST'?'按通过题数、ACM 总罚时排序':'按通过题数、完成时间排序' : '每人最快一次正式 AC · 用时升序，内存同分排序 · 前 100 名' }}</p></div><button class="oj-modal-button" :disabled="rankingLoading" @click="loadRanking(true)"><UiIcon name="refresh" :size="13" />刷新</button></div>
        <div v-if="!setId" class="oj-modal-ranking-filters"><OjSelect compact v-model="rankingLanguage" :options="languageOptions" label="排行语言" @change="loadRanking()" /><OjSelect compact v-model="rankingMode" :options="problem?.modes.map(m=>({value:m,label:modeNames[m]})) || []" label="排行模式" @change="loadRanking()" /></div>
        <div v-else class="oj-modal-set-summary"><span>{{ setInfo?.name }}</span><span>{{ rankTotals.total }} 道题 · {{ rankTotal }} 人</span></div>
        <div v-if="rankingLoading && !ranking.length" class="oj-modal-loading" role="status"><i />正在加载排行…</div>
        <div v-else-if="rankingError" class="oj-modal-error" role="alert"><p>{{ rankingError }}</p><button class="oj-modal-button" @click="loadRanking(true)">重试</button></div>
        <div v-else-if="!ranking.length" class="oj-modal-empty"><UiIcon name="trophy" :size="22" /><h3>暂无排名</h3><p>{{ setId ? '当前题单还没有符合条件的参与者。' : '该语言和模式还没有正式 AC 提交。' }}</p></div>
        <template v-else><div class="oj-modal-ranking-table"><div class="oj-modal-rank-head"><span>排名</span><span>用户</span><span>{{ setId?'通过题数':'执行用时' }}</span><span>{{ setId?(setInfo?.mode==='CONTEST'?'总罚时':'完成时间'):'内存消耗' }}</span></div><div v-for="(r,index) in ranking" :key="r.userId || r.studentId || index" class="oj-modal-rank-row" :class="{self:String(r.studentId)===String(user.user?.studentId)}"><span class="oj-rank-position" :class="{podium:(r.rank || index+1)<=3}">{{ setId ? r.rank || '—' : index+1 }}</span><div class="oj-modal-rank-user"><strong>{{ r.name || r.nickname }}<small v-if="r.nickname && r.nickname!==r.name">（{{ r.nickname }}）</small><span v-if="String(r.studentId)===String(user.user?.studentId)" class="oj-rank-me">我</span></strong><span>{{ r.studentId }}</span></div><strong class="oj-rank-metric">{{ setId ? r.acCount : r.timeMs }}<small>{{ setId ? '/ '+rankTotals.total : 'ms' }}</small></strong><span class="oj-rank-secondary">{{ setId ? setInfo?.mode==='CONTEST'?r.penalty+' 分钟':formatOjDate(r.completedAt) : (r.memoryBytes/1048576).toFixed(2)+' MiB' }}</span></div></div><div v-if="setId && rankTotal>50" class="oj-modal-pagination"><button class="oj-modal-button" :disabled="rankPage===1" @click="rankPage--;loadRanking()">上一页</button><span>{{ rankPage }} / {{ Math.ceil(rankTotal/50) }}</span><button class="oj-modal-button" :disabled="rankPage*50>=rankTotal" @click="rankPage++;loadRanking()">下一页</button></div></template>
      </section>
    </OjWorkspaceDialog>
  </div>
</template>
<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, defineAsyncComponent } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import OjCodeEditor from '@/components/OjCodeEditor.vue'
const OjSolution=defineAsyncComponent(()=>import('@/components/OjSolution.vue'))
import OjWorkspaceDialog from '@/components/OjWorkspaceDialog.vue'
import OjRunResult from '@/components/OjRunResult.vue'
import OjStatusBadge from '@/components/OjStatusBadge.vue'
import { languageNames,modeNames,kindNames,formatOjDate } from '@/utils/ojDictionaries'
import OjSelect from '@/components/OjSelect.vue'
import UiIcon from '@/components/UiIcon.vue'
import { formatOjCode } from '@/utils/ojFormatter'
import { createWorkspaceCache } from '@/utils/ojWorkspaceCache'
import {readExecutionEvents} from '@/utils/ojExecutionStream'
import './oj.css'
import './sets.css'
import './workspace-dialog.css'
import './studio.css'
import '@/components/oj-solution.css'
import {catalogReturn} from '@/utils/ojNavigation'
const route = useRoute(), router=useRouter(), user = useUserStore()
const statementView=ref(route.query.tab==='solution'?'solution':'statement'),solutionVisited=ref(route.query.tab==='solution')
function openSolution(){solutionVisited.value=true;statementView.value='solution';panel.value='statement';focusMode.value=false}
async function useSolutionCode(value){
  if(sending.value||pending.value||templateLoading.value){error.value='请等待当前运行或模板加载完成后再载入代码。';return}
  if(!problem.value.modes.includes(value.mode)||!problem.value.languages.some(l=>l.languageKey===value.language)){error.value='该语言或模式当前未启用，可复制代码后另行使用。';return}
  if(code.value.trim()&&code.value!==value.code&&!window.confirm('用题解代码替换当前编辑器内容？当前草稿将被覆盖。'))return
  saveDraft();++templateGeneration;ready=false;language.value=value.language;mode.value=value.mode;await nextTick();templateLoading.value=false;code.value=value.code;ready=true;saveDraft();panel.value='code';await nextTick();editor.value?.focus()
}
const returnTo=catalogReturn(route.query.from),editor=ref(null),copiedSample=ref(null)
let copyTimer
const nextSetItem=computed(()=>{const items=setInfo.value?.items.filter(i=>i.active)||[],index=items.findIndex(i=>String(i.id)===String(itemId));return items.slice(index+1).find(i=>i.progress?.state!=='AC') || null})
function scrollSection(section){document.getElementById('oj-'+section)?.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'start'})}
async function useSample(index,scroll=true){if(custom.value&&input.value&&input.value!==problem.value.samples[index].input&&!window.confirm('用此示例替换当前自定义测试输入？'))return;inputSample.value=index;input.value=problem.value.samples[index].input;custom.value=true;panel.value='code';await nextTick();if(scroll)scrollSection('test-input');document.querySelector('.oj-test-input')?.focus({preventScroll:true})}
async function copySample(text,index){try{await navigator.clipboard.writeText(text);copiedSample.value=index;clearTimeout(copyTimer);copyTimer=setTimeout(()=>copiedSample.value=null,1800)}catch{error.value='复制失败，请选中输入内容后复制。'}}
async function resumeEditing(){dialogView.value='';panel.value='code';await nextTick();editor.value?.focus()}

const setId=route.params.setId, itemId=route.params.itemId, setInfo=ref(null),setClock=ref(Date.now());let setOffset=0,setTimer
const setApi=setId?`/api/oj/sets/${setId}`:null
const setCanSubmit=computed(()=>!setId || Boolean(setInfo.value?.canSubmit) && (setInfo.value.mode!=='CONTEST'||setClock.value<Number(setInfo.value.endsAt)))
const setCountdown=computed(()=>{const t=Math.max(0,Math.floor((Number(setInfo.value?.endsAt)-setClock.value)/1000));return `${String(Math.floor(t/3600)).padStart(2,'0')}:${String(Math.floor(t/60)%60).padStart(2,'0')}:${String(t%60).padStart(2,'0')}`})
const setNavigation=computed(()=>setInfo.value?.items.filter(i=>i.active).map(i=>({value:String(i.id),label:`${i.label}. ${i.title}`}))||[])
function navigateSet(id){saveDraft();router.push(`/oj/sets/${setId}/items/${id}`)}
async function loadSet(){if(!setId)return;try{const{data}=await request.get(setApi);if(!disposed){setInfo.value=data;setOffset=Number(data.serverNow)-Date.now();setClock.value=Date.now()+setOffset}}catch(e){if(!disposed)error.value=e.message}}
const levels = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
const problem = ref(null), loading = ref(true), error = ref(''), language = ref('cpp'), mode = ref('STDIO'), code = ref(''), draftError = ref('')
const focusMode=ref(false),runtime=ref(null)
let runtimeTimer
async function loadRuntime(){if(setId)loadSet();try{const {data}=await request.get('/api/oj/runtime');if(!disposed)runtime.value=data}catch{if(!disposed)runtime.value={available:false,availableSlots:0}}}
const panel = ref('statement'), dialogView = ref(''), custom = ref(false), input = ref(''), inputSample=ref(0), sending = ref(false), pending = ref(false), currentJob = ref(null), history = ref([])
const dialogViews=computed(()=>[{value:'result',label:'运行结果',icon:'terminal'},{value:'history',label:'提交记录',icon:'history'},{value:'ranking',label:setId?'题单排名':'本题排行',icon:'trophy'}])
const dialogTitle=computed(()=>dialogViews.value.find(v=>v.value===dialogView.value)?.label || '')
const dialogIcon=computed(()=>dialogViews.value.find(v=>v.value===dialogView.value)?.icon || 'terminal')
function openDialog(view){dialogView.value=view;primeRecords()}
const recordsCache=createWorkspaceCache()
const recordKey=(kind,suffix='')=>`${kind}:${user.user?.id}:${setId || 'library'}:${itemId || problem.value?.id}:${suffix}`
async function primeRecords(){
  if(!problem.value || !user.user?.id)return
  const selected=jobGeneration
  const historyReady=loadHistory()
  loadRanking()
  await historyReady
  if(!disposed && selected===jobGeneration && !linkedSubmissionId() && !currentJob.value && !jobLoading.value && !jobError.value && history.value[0])await showJob(history.value[0].id,false)
}
const currentLanguage = computed(() => problem.value?.languages.find(l => l.languageKey === language.value))
const templateLoading=ref(false), formatting=ref(false)
const filenames={c:'main.c',cpp:'main.cpp',java:'Main.java',python:'solution.py',rust:'solution.rs'}
const languageOptions=computed(()=>problem.value?.languages.map(l=>({value:l.languageKey,label:l.label,detail:l.version,badge:{c:'C',cpp:'C++',java:'Java',python:'Py',rust:'Rs'}[l.languageKey]}))||[])
const resultPending=computed(()=>['QUEUED','RUNNING'].includes(currentJob.value?.status))
const progressConnection=ref('connecting')
const resultLimits=computed(()=>problem.value?.languages.find(l=>l.languageKey===currentJob.value?.language))
const performance=ref(null),performanceLoading=ref(false),performanceError=ref(''),ranking=ref([]),rankingLoading=ref(false),rankingError=ref(''),historyPage=ref(1),historyLoading=ref(false),historyError=ref(''),jobLoading=ref(false),jobError=ref(''),requestedJobId=ref(null)
const rankingLanguage=ref('cpp'),rankingMode=ref('STDIO'),rankPage=ref(1),rankTotal=ref(0),rankTotals=ref({total:0})
let performanceGeneration=0,rankingGeneration=0
watch(()=>[currentJob.value?.id,currentJob.value?.status],()=>loadPerformance())
async function loadPerformance(force=false){
  const generation=++performanceGeneration;performance.value=null;performanceError.value='';performanceLoading.value=false
  const {id,status}=currentJob.value || {};if(!id||status!=='AC')return
  performanceLoading.value=true
  try{const data=await recordsCache.load(recordKey('performance',id),async()=> (await request.get(`/api/oj/submissions/${id}/performance`)).data,force)
    if(generation===performanceGeneration&&!disposed)performance.value=data
  }catch(e){if(generation===performanceGeneration&&!disposed)performanceError.value=e.message}
  finally{if(generation===performanceGeneration)performanceLoading.value=false}
}
let displayedRankingKey=''
async function loadRanking(force=false){
  if(!problem.value)return
  const generation=++rankingGeneration,params=setId?{page:rankPage.value}:{language:rankingLanguage.value,mode:rankingMode.value}
  const path=setId?setApi+'/ranking':`/api/oj/problems/${problem.value.id}/ranking`,key=recordKey('ranking',setId?params.page:`${params.language}:${params.mode}`)
  if(key!==displayedRankingKey)ranking.value=[]
  displayedRankingKey=key;rankingLoading.value=true;rankingError.value=''
  try{const data=await recordsCache.load(key,async()=> (await request.get(path,{params})).data,force)
    if(generation===rankingGeneration&&!disposed){ranking.value=data.records||[];rankTotal.value=Number(data.total||data.participants||0);rankTotals.value=data.totals||{total:0}}
  }catch(e){if(generation===rankingGeneration&&!disposed)rankingError.value=e.message}
  finally{if(generation===rankingGeneration)rankingLoading.value=false}
}
let jobGeneration=0,historyGeneration=0,jobController,pollController,pollGeneration=0,runningJobId,eventController,eventTimer,runningProgress,pollFailures=0,pollRequest=0
function selectJob(){if(jobController){jobController.abort();recordsCache.invalidate(recordKey('job',requestedJobId.value))}jobController=null;return ++jobGeneration}
function activeJob(generation){return !disposed && generation===jobGeneration}
let timer, draftTimer, disposed = false, ready = false, templateGeneration = 0
const formattedTemplates=new Map()
function draftKey(lang = language.value, selected = mode.value) { return setId ? `sap-oj-set-draft:${user.user.id}:${setId}:${itemId}:${lang}:${selected}` : `sap-oj-draft:${user.user.id}:${problem.value.id}:${lang}:${selected}` }
function saveDraft(lang, selected) { if (!ready || templateLoading.value || !problem.value || !user.user?.id) return; try { localStorage.setItem(draftKey(lang, selected), code.value); draftError.value = '' } catch { draftError.value = '本机存储已满，请及时备份代码' } }
async function template(lang=language.value, selected=mode.value) {
  const key=`${lang}:${selected}`
  if(!formattedTemplates.has(key)) formattedTemplates.set(key,formatOjCode(problem.value.templates[lang]?.[selected] || '',lang).catch(e=>{formattedTemplates.delete(key);throw e}))
  return formattedTemplates.get(key)
}
async function loadDraft() {
  const generation=++templateGeneration, key=draftKey();templateLoading.value=true
  try { let saved;try{saved=localStorage.getItem(key)}catch{};const raw=problem.value.templates[language.value]?.[mode.value] || '';const next=saved == null || saved.trim()===raw.trim()?await template():saved;if(!disposed && generation===templateGeneration)code.value=next }
  catch(e){if(!disposed && generation===templateGeneration){code.value=problem.value.templates[language.value]?.[mode.value] || '';error.value=e.message}}
  finally{if(generation===templateGeneration)templateLoading.value=false}
}
watch(code, () => { if(templateLoading.value)return;clearTimeout(draftTimer); draftTimer = setTimeout(() => saveDraft(), 400) })
watch([language, mode], async (next, previous) => { if (!ready) return; clearTimeout(draftTimer); saveDraft(...previous); await loadDraft() })
async function restoreTemplate() {
  if (!window.confirm('恢复当前语言和模式的初始模板？当前草稿会被覆盖。')) return
  templateLoading.value=true
  try{code.value=await template()}catch(e){error.value=e.message}finally{templateLoading.value=false;saveDraft()}
}
async function formatCurrent(){if(formatting.value||templateLoading.value)return;const original=code.value,lang=language.value;formatting.value=true;try{const formatted=await formatOjCode(original,lang);if(code.value===original && language.value===lang){code.value=formatted;saveDraft()}}catch(e){error.value=e.message}finally{formatting.value=false}}
let displayedHistoryKey=''
async function loadHistory(force=false) {
  if(!problem.value)return
  const generation=++historyGeneration,page=historyPage.value,key=recordKey('history',page)
  if(key!==displayedHistoryKey)history.value=[]
  displayedHistoryKey=key;historyLoading.value=true;historyError.value=''
  try {const data=await recordsCache.load(key,async()=> (await request.get(setId ? setApi+'/submissions' : '/api/oj/submissions', {params:setId ? {mine:true,itemId,page} : {problemId:problem.value.id,page}})).data,force)
    if(!disposed && generation===historyGeneration)history.value=(setId?data.records:data)||[]
  }catch(e){if(!disposed && generation===historyGeneration)historyError.value=e.message}
  finally{if(generation===historyGeneration)historyLoading.value=false}
}
async function showJob(id,reveal=true,force=false) {
  const generation=selectJob();requestedJobId.value=id;if(reveal)dialogView.value='result'
  jobLoading.value=true;jobError.value='';currentJob.value=null
  const controller=new AbortController();jobController=controller
  try {const data=await recordsCache.load(recordKey('job',id),async()=> (await request.get(`/api/oj/submissions/${id}`,{signal:controller.signal})).data,force)
    if(!activeJob(generation))return
    if(String(data.problemId)!==String(problem.value.id))throw new Error('该提交记录不属于当前题目')
    currentJob.value=data;if(['QUEUED','RUNNING'].includes(data.status))followJob(data)
  }catch(e){if(activeJob(generation) && e.code!=='ERR_CANCELED')jobError.value=e.message}
  finally{if(activeJob(generation))jobLoading.value=false;if(jobController===controller)jobController=null}
}
async function restoreSubmission() {
  const job = currentJob.value
  if (!problem.value.modes.includes(job.mode) || !problem.value.languages.some(l => l.languageKey === job.language)) { error.value = '该次提交的语言或模式已停用'; return }
  saveDraft(); ++templateGeneration;ready=false; language.value = job.language; mode.value = job.mode; await nextTick();templateLoading.value=false;code.value = job.code;ready=true;saveDraft();dialogView.value='';panel.value='code';await nextTick();editor.value?.focus()
}
function stopEvents(){eventController?.abort();eventController=null;clearTimeout(eventTimer)}
function followJob(job){if(runningJobId===job.id)return;clearTimeout(timer);pollController?.abort();stopEvents();runningJobId=job.id;runningProgress=job.progress;pollFailures=0;pending.value=true;progressConnection.value='connecting';const generation=++pollGeneration;streamJob(job.id,generation);poll(job.id,generation)}
async function streamJob(id,generation,attempt=0){
  if(disposed || generation!==pollGeneration)return
  const controller=new AbortController();eventController=controller
  try {
    const terminal=await readExecutionEvents(`/api/oj/submissions/${id}/events`,{token:localStorage.getItem('sap_token'),signal:controller.signal,
      onOpen:()=>{if(generation===pollGeneration)progressConnection.value='live'},
      onProgress:progress=>{
        if(disposed || generation!==pollGeneration || String(progress.jobId)!==String(id))return
        if(runningProgress?.streamId===progress.streamId && Number(runningProgress.sequence)>=Number(progress.sequence))return
        runningProgress=progress
        if(String(currentJob.value?.id)===String(id))currentJob.value={...currentJob.value,progress,passedCases:progress.passedCases,totalCases:progress.totalCases,...(progress.stage==='FINISHED'?{}:{status:progress.status})}
      }})
    if(disposed || generation!==pollGeneration)return
    if(terminal){clearTimeout(timer);poll(id,generation);return}
    throw new Error('进度连接已断开')
  }catch(e){
    if(disposed || generation!==pollGeneration || controller.signal.aborted)return
    progressConnection.value=attempt<2?'reconnecting':'fallback'
    if(attempt<2)eventTimer=setTimeout(()=>streamJob(id,generation,attempt+1),500*2**attempt)
    clearTimeout(timer);poll(id,generation)
  }finally{if(eventController===controller)eventController=null}
}
async function poll(id,generation) {
  if(disposed || generation!==pollGeneration)return
  const revision=++pollRequest;pollController?.abort();clearTimeout(timer)
  pollController=new AbortController()
  try {const {data}=await request.get(`/api/oj/submissions/${id}`,{signal:pollController.signal});if(disposed || generation!==pollGeneration || revision!==pollRequest)return
    pollFailures=0
    if(['QUEUED','RUNNING'].includes(data.status) && runningProgress && (runningProgress.streamId!==data.progress?.streamId || Number(runningProgress.sequence)>Number(data.progress?.sequence || 0)))data.progress=runningProgress
    recordsCache.remember(recordKey('job',id),data)
    if(currentJob.value?.id===id)currentJob.value=data
    if(['QUEUED','RUNNING'].includes(data.status))timer=setTimeout(()=>poll(id,generation),progressConnection.value==='live'?12000:1200)
    else {++pollGeneration;stopEvents();pending.value=false;runningJobId=null;recordsCache.invalidate('history:');loadHistory();if(data.kind==='SUBMIT'){recordsCache.invalidate('ranking:');loadRanking();if(data.status==='AC'){recordsCache.invalidate('performance:');loadPerformance()}}loadRuntime()}
  }catch(e){if(!disposed && generation===pollGeneration && revision===pollRequest && e.code!=='ERR_CANCELED'){
    if(++pollFailures<3){progressConnection.value='fallback';timer=setTimeout(()=>poll(id,generation),1500);return}
    ++pollGeneration;stopEvents();if(currentJob.value?.id===id)jobError.value=e.message;pending.value=false;runningJobId=null
  }}
}
let retryIntent
async function send(kind) {
  if(sending.value||pending.value||templateLoading.value||!setCanSubmit.value||!code.value.trim())return
  const generation=selectJob();sending.value=true
  saveDraft();error.value='';jobError.value='';jobLoading.value=false;requestedJobId.value=null;pending.value=true;dialogView.value='result';currentJob.value={language:language.value,mode:mode.value,kind:kind==='run'?'RUN':'SUBMIT',status:'QUEUED',passedCases:0,totalCases:0}
  try {const payload={problemId:problem.value.id,language:language.value,mode:mode.value,code:code.value};if(kind==='run' && custom.value)payload.input=input.value
    if(setId){const fingerprint=JSON.stringify({...payload,kind});if(retryIntent?.fingerprint!==fingerprint)retryIntent={fingerprint,key:crypto.randomUUID()};payload.requestKey=retryIntent.key}
    const {data}=await request.post(setId?`${setApi}/items/${itemId}/${kind}`:`/api/oj/${kind}`,payload);retryIntent=null
    if(disposed)return
    if(activeJob(generation)){currentJob.value=data;requestedJobId.value=data.id}
    followJob(data)
  }catch(e){if(!disposed){pending.value=false;if(activeJob(generation)){jobError.value=e.message;currentJob.value=null}else error.value=e.message;loadRuntime()}}
  finally{sending.value=false}
}
function linkedSubmissionId() {
  const value=route.query.submission
  return typeof value==='string' && /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value))?Number(value):null
}
function openLinkedSubmission() {
  const id=linkedSubmissionId()
  if(id)showJob(id)
}
watch(()=>route.query.submission,()=>{if(!loading.value && problem.value)openLinkedSubmission()})
onMounted(async () => {
  if(setId){await loadSet();if(disposed)return;setTimer=setInterval(()=>{setClock.value=Date.now()+setOffset},1000)}
  loadRuntime();runtimeTimer=setInterval(()=>{if(!document.hidden)loadRuntime()},10000)
  try { if (!user.user) await user.fetchUserInfo(); if(disposed)return; const response=await request.get(setId?`${setApi}/items/${itemId}`:`/api/oj/problems/${route.params.id}`);if(disposed)return;problem.value=response.data
    language.value = problem.value.languages.some(l => l.languageKey === 'cpp') ? 'cpp' : problem.value.languages[0].languageKey
    mode.value = problem.value.defaultMode;rankingLanguage.value=language.value;rankingMode.value=mode.value
    primeRecords();await nextTick();await loadDraft();ready=true
  } catch (e) { error.value = e.message } finally { loading.value = false }
  if(!disposed && problem.value)openLinkedSubmission()
})
watch(()=>user.user?.id,(id,previous)=>{if(previous==null || id===previous)return;solutionVisited.value=false;statementView.value='statement';recordsCache.invalidate();selectJob();++pollGeneration;stopEvents();runningProgress=null;pollController?.abort();clearTimeout(timer);++historyGeneration;++rankingGeneration;++performanceGeneration;historyPage.value=1;history.value=[];ranking.value=[];currentJob.value=null;requestedJobId.value=null;performance.value=null;jobError.value='';historyError.value='';rankingError.value='';performanceError.value='';pending.value=false;runningJobId=null;jobLoading.value=false;historyLoading.value=false;rankingLoading.value=false;performanceLoading.value=false;dialogView.value='';ready=false;code.value='';if(id && problem.value)loadDraft().then(()=>{if(!disposed)ready=true});if(setId){setInfo.value=null;loadSet()}})
onBeforeUnmount(() => { clearTimeout(copyTimer);recordsCache.invalidate();saveDraft(); disposed = true;selectJob();++pollGeneration;stopEvents();runningProgress=null;pollController?.abort();++historyGeneration;++performanceGeneration;++rankingGeneration; clearTimeout(timer); clearTimeout(draftTimer);clearInterval(runtimeTimer);clearInterval(setTimer) })
</script>
