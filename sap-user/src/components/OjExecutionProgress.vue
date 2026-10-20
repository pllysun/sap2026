<template>
  <section class="oj-execution" :class="{compact:!pending}" aria-label="执行过程">
    <header><strong>{{ pending?headline:'执行过程' }}</strong></header>
    <ol class="oj-execution-steps">
      <li v-for="step in steps" :key="step.key" :class="step.state"><span class="oj-execution-node"><UiIcon v-if="step.state==='done'" name="check" :size="12" /><UiIcon v-else-if="step.state==='failed'" name="warning" :size="12" /><i v-else-if="step.state==='active'" /><UiIcon v-else :name="step.icon" :size="12" /></span><div><strong>{{ step.label }}</strong><small>{{ step.detail }}</small></div></li>
    </ol>
    <div v-if="progress?.totalCases" class="oj-execution-cases">
      <div><span>{{ jobKind==='RUN'?'样例':'测试用例' }} <strong>{{ progress.completedCases }} / {{ progress.totalCases }}</strong></span><small>通过 {{ progress.passedCases }}</small></div>
      <div class="oj-execution-bar" role="progressbar" :aria-valuenow="progress.completedCases" :aria-valuemin="0" :aria-valuemax="progress.totalCases" aria-label="用例执行进度"><i :style="{width:Math.min(100,100*progress.completedCases/progress.totalCases)+'%'}" /></div>
      <div class="oj-execution-case-grid"><span v-for="item in visibleCases" :key="item.index" :class="item.state" :title="'用例 '+item.index+' · '+item.label"><UiIcon v-if="item.state==='done'" name="check" :size="10" /><UiIcon v-else-if="item.state==='failed'" name="warning" :size="10" /><i v-else-if="item.state==='active'" />{{ item.index }}</span><small v-if="progress.totalCases>32">共 {{ progress.totalCases }} 个</small></div>
    </div>
    <p v-if="pending && progress?.stage==='RETRYING'" class="oj-execution-note">节点连接中断，正在尝试其他运行资源。</p>
    <p v-else-if="pending" class="oj-execution-note">关闭弹窗后任务会继续运行。</p>
  </section>
</template>
<script setup>
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import UiIcon from './UiIcon.vue'
const props=defineProps({progress:Object,pending:Boolean,status:String,jobKind:String})
const now=ref(Date.now());let timer
watch(()=>props.pending,value=>{clearInterval(timer);if(value)timer=setInterval(()=>now.value=Date.now(),1000)},{immediate:true})
onBeforeUnmount(()=>clearInterval(timer))
const headline=computed(()=>({QUEUED:'等待运行资源',DISPATCHED:'准备运行',COMPILING:'正在编译',COMPILED:'编译完成',RUNNING_CASE:`正在执行第 ${props.progress?.caseIndex || 1} 个用例`,CASE_FINISHED:'正在检验用例',COMPILE_FAILED:'编译失败',RETRYING:'正在切换运行资源',FINISHED:'正在获取完整结果'})[props.progress?.stage] || (props.status==='QUEUED'?'等待运行资源':'正在同步执行进度'))
const steps=computed(()=>{
  const p=props.progress,t=p?.timings||{},terminal=p?.stage==='FINISHED',failed=p?.status!=='AC' && terminal
  const elapsed=(start,end)=>start?`${Math.max(0,((end || now.value)-start)/1000).toFixed(1)} s`:''
  return [
    {key:'queue',label:'排队',icon:'clock',state:t.DISPATCHED?'done':props.pending?'active':'waiting',detail:t.DISPATCHED?elapsed(t.QUEUED,t.DISPATCHED):props.pending?'等待分配资源':'—'},
    {key:'compile',label:'编译',icon:'code',state:t.COMPILE_FAILED?'failed':t.COMPILED?'done':t.COMPILING && !terminal?'active':'waiting',detail:t.COMPILE_FAILED?'编译失败':t.COMPILED?elapsed(t.COMPILING,t.COMPILED):t.COMPILING?elapsed(t.COMPILING,terminal?p.updatedAt:null):'等待编译'},
    {key:'cases',label:props.jobKind==='RUN'?'运行样例':'执行测试',icon:'terminal',state:terminal && t.COMPILED?(failed?'failed':'done'):t.COMPILED?'active':'waiting',detail:p?.totalCases && t.COMPILED?`${p.completedCases} / ${p.totalCases}`:'等待执行'},
    {key:'result',label:'结果',icon:'clipboard-check',state:terminal?(failed?'failed':'done'):'waiting',detail:terminal?(p?.status==='AC'?'通过':'已结束'):'等待完成'}
  ]
})
const visibleCases=computed(()=>{
  const p=props.progress;if(!p?.totalCases)return []
  const results=new Map(p.cases?.map(c=>[c.index,c.verdict])||[])
  const indices=p.totalCases<=32?Array.from({length:p.totalCases},(_,i)=>i+1):Array.from({length:32},(_,i)=>Math.max(1,Math.min(p.totalCases-31,p.caseIndex-23))+i)
  return indices.map(index=>{const verdict=results.get(index),state=verdict?(verdict==='AC'?'done':'failed'):props.pending && p.stage==='RUNNING_CASE' && p.caseIndex===index?'active':'waiting';return {index,state,label:verdict || (state==='active'?'运行中':'尚未执行')}})
})
</script>
<style scoped>
.oj-execution{padding:.75rem 0;color:#5b758c}.oj-execution>header{display:flex;align-items:center;justify-content:space-between;gap:.75rem;margin-bottom:.875rem}.oj-execution>header>strong{font-size:0.8125rem;font-weight:600;color:#315d82}.oj-execution-steps{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));padding:0;margin:0;list-style:none;gap:.625rem}.oj-execution-steps>li{display:flex;align-items:flex-start;gap:.5rem;position:relative;min-width:0}.oj-execution-node{display:grid;place-items:center;flex:none;width:1.5rem;height:1.5rem;border:1px solid #e2eaf3;border-radius:6px;background:#f7faff;color:#677581;z-index:1}.oj-execution-steps>li:not(:last-child):after{content:'';position:absolute;top:.75rem;left:2rem;right:.5rem;height:1px;background:#e6eef6;z-index:0}.oj-execution-steps>li>div{background:#fff;position:relative;z-index:1;padding-right:8px;min-width:0}.oj-execution-steps strong{display:block;font-size:.75rem;color:#617483;font-weight:500;line-height:1.5rem}.oj-execution-steps small{display:block;font:.6875rem/1.5 ui-monospace,monospace;color:#697581;min-height:1rem;margin-top:.125rem;overflow-wrap:anywhere}.oj-execution-steps .done .oj-execution-node{border-color:#c8ecdf;background:#edf9f3;color:#208160}.oj-execution-steps .done strong{color:#3e8067}.oj-execution-steps .active .oj-execution-node{border-color:#9fc9ee;background:#eff7ff;box-shadow:0 0 0 3px #3887cf0a}.oj-execution-steps .active strong{color:#3279b5}.oj-execution-steps .failed .oj-execution-node{border-color:#f3d3cc;background:#fff4f0;color:#a16152}.oj-execution-steps .failed strong{color:#a56152}.oj-execution-node>i,.oj-execution-case-grid>span>i{display:block;width:11px;height:11px;border:1.5px solid #bed9f1;border-top-color:#3986c8;border-radius:50%;animation:oj-exec-spin .8s linear infinite}.oj-execution-cases{padding:.625rem .75rem;margin-top:.75rem;background:#f8fbff;border:1px solid #e6eff7;border-radius:8px}.oj-execution-cases>div:first-child{display:flex;justify-content:space-between;gap:12px;font-size:0.75rem}.oj-execution-cases strong{font:0.75rem ui-monospace,monospace;margin-left:8px;color:#47769f}.oj-execution-cases small{font-size:0.75rem;color:#657786}.oj-execution-bar{height:3px;background:#e5eef7;margin-top:.5rem;border-radius:4px;overflow:hidden}.oj-execution-bar>i{display:block;height:100%;background:linear-gradient(90deg,#89c1eb,#3ca486);border-radius:4px;transition:width .3s ease}.oj-execution-case-grid{display:flex;flex-wrap:wrap;gap:.375rem;margin-top:.5rem;align-items:center}.oj-execution-case-grid>span{display:inline-flex;align-items:center;justify-content:center;gap:3px;min-width:1.5rem;padding:.1875rem .375rem;border:1px solid #e3ecf5;border-radius:4px;font:.6875rem/1.25 ui-monospace,monospace;color:#697581;background:#fff;transition:background .2s,color .2s}.oj-execution-case-grid>span.done{border-color:#ccebdc;background:#effaf4;color:#298364}.oj-execution-case-grid>span.failed{border-color:#f2d6ce;background:#fff3ef;color:#a6614d}.oj-execution-case-grid>span.active{border-color:#a9cef0;background:#edf6ff;color:#3277af}.oj-execution-case-grid>span>i{width:9px;height:9px}.oj-execution-case-grid>small{margin-left:5px}.oj-execution-note{font-size:.75rem;line-height:1.5;color:#65737f;margin:.75rem 0 0}.compact{padding:.375rem 0 .625rem}.compact>header{display:none}
.compact .oj-execution-cases{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:.375rem .625rem;padding:.5rem .625rem;margin-top:.625rem}.compact .oj-execution-cases>div:first-child{grid-column:1 / -1}.compact .oj-execution-bar{margin-top:0}.compact .oj-execution-case-grid{margin-top:0;max-width:22rem}.compact .oj-execution-case-grid>small{display:none}
@keyframes oj-exec-spin{to{transform:rotate(360deg)}}
@media(max-width:500px){.oj-execution-steps{gap:6px}.oj-execution-steps>li{flex-direction:column;gap:2px}.oj-execution-steps>li:not(:last-child):after{left:32px;right:0}.oj-execution-steps>li>div{padding:0;background:none}.oj-execution-steps small{font-size:0.75rem;white-space:normal}.oj-execution>header>strong{font-size:0.75rem}.oj-execution-cases{padding:12px}}
@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
</style>
