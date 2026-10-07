<template>
  <div class="oj-run-result">
    <div v-if="loading" class="oj-modal-loading" role="status"><i />正在加载运行记录…</div>
    <div v-else-if="error" class="oj-modal-error" role="alert"><UiIcon name="warning" /><p>{{ error }}</p><button v-if="retryable" class="oj-modal-button" @click="emit('retry')">重新加载</button></div>
    <div v-else-if="!job" class="oj-modal-empty"><UiIcon name="terminal" :size="22" /><h3>暂无运行结果</h3><p>运行代码后在这里查看结果。</p></div>
    <template v-else>
      <header class="oj-result-banner oj-verdict" :class="[tone,`oj-verdict--${verdict.tone}`]" aria-live="polite">
        <span class="oj-verdict-seal" aria-hidden="true"><UiIcon :name="verdict.icon || 'info'" :size="17" /><small>{{ verdictCode }}</small></span>
        <div class="oj-verdict-main"><h3 class="oj-verdict-title">{{ verdictTitle }}</h3><p class="oj-verdict-description">{{ verdict.help }}</p><div class="oj-verdict-context"><span><UiIcon :name="job.kind==='RUN'?'play':'send'" :size="11" />{{ kindNames[job.kind] }}</span><span>{{ languageNames[job.language] }}</span><span>{{ modeNames[job.mode] }}</span></div></div>
        <div v-if="job.id" class="oj-verdict-reference"><span><UiIcon name="file-save" :size="12" />#{{ job.id }}</span><time>{{ formatOjDate(job.acceptedAt || job.createdAt) }}</time></div>
      </header>
      <OjExecutionProgress v-if="pending" :progress="job.progress" :pending="pending" :status="job.status" :job-kind="job.kind" />
      <template v-else>
        <div v-if="tab!=='performance'" class="oj-run-metrics">
          <div><span><UiIcon name="clipboard-check" :size="13" />通过用例</span><strong>{{ job.passedCases ?? '—' }} <small>/ {{ job.totalCases ?? '—' }}</small></strong></div>
          <div><span><UiIcon name="clock" :size="13" />执行用时</span><strong>{{ job.result?.timeMs ?? '—' }} <small>ms</small></strong></div>
          <div><span><UiIcon name="cpu" :size="13" />内存使用</span><strong>{{ job.result?.memoryBytes == null ? '—' : (job.result.memoryBytes/1048576).toFixed(2) }} <small>MiB</small></strong></div>
        </div>
        <div class="oj-result-view-tabs" role="tablist" aria-label="运行结果详情">
          <button v-for="item in tabs" :key="item.value" role="tab" :aria-selected="tab===item.value" :class="{active:tab===item.value}" @click="tab=item.value"><UiIcon :name="item.icon" :size="13" />{{ item.label }}</button>
          <button v-if="job.code" class="oj-modal-button oj-restore-source" @click="emit('restore')"><UiIcon name="file-save" :size="13" />恢复到编辑器</button>
        </div>
        <div v-if="tab==='cases'" class="oj-result-details">
          <details v-if="job.progress?.timings?.COMPILING" class="oj-result-execution"><summary>执行过程<UiIcon name="chevron-down" :size="12" /></summary><OjExecutionProgress :progress="job.progress" :status="job.status" :job-kind="job.kind" /></details>
          <p v-if="detailMessage" class="oj-result-message">{{ detailMessage }}</p>
          <p v-if="job.result?.outputTruncated" class="oj-result-message" role="status">输出较多，部分内容已截断展示；所有用例仍使用完整输出判题。</p>
          <div v-if="job.result?.compilerOutput" class="oj-compiler-output"><h3>编译信息</h3><pre>{{ job.result.compilerOutput }}</pre></div>
          <div v-if="cases.length" class="oj-case-layout">
            <div class="oj-result-case-list" aria-label="测试用例结果"><button v-for="(test,index) in cases" :key="index" :class="{active:caseIndex===index}" :aria-pressed="caseIndex===index" @click="caseIndex=index"><span>{{ test.name || '用例 '+(index+1) }}</span><OjStatusBadge :status="test.verdict" :show-code="false" /></button></div>
            <div v-if="selectedCase" class="oj-result-case-detail"><header><h3>{{ selectedCase.name || '用例 '+(caseIndex+1) }}</h3><OjStatusBadge :status="selectedCase.verdict" /></header><div class="oj-output-pair"><div><label>实际输出<span v-if="selectedCase.outputTruncated">（展示已截断）</span></label><pre>{{ selectedCase.output || (selectedCase.outputTruncated ? '（已省略）' : '（无输出）') }}</pre></div><div v-if="selectedCase.expected !== undefined"><label>期望输出<span v-if="selectedCase.expectedTruncated">（展示已截断）</span></label><pre>{{ selectedCase.expected || (selectedCase.expectedTruncated ? '（已省略）' : '（无输出）') }}</pre></div></div><div v-if="selectedCase.stderr || selectedCase.stderrTruncated"><label>错误输出<span v-if="selectedCase.stderrTruncated">（展示已截断）</span></label><pre>{{ selectedCase.stderr || '（已省略）' }}</pre></div></div>
          </div>
          <section v-else-if="!job.result?.compilerOutput" class="oj-test-receipt" :class="`oj-verdict--${verdict.tone}`" aria-label="测试集结果">
            <header><span><UiIcon name="clipboard-check" :size="14" />测试集结果</span><small v-if="job.kind==='SUBMIT'"><UiIcon name="lock" :size="11" />隐藏用例</small></header>
            <div class="oj-test-receipt-content"><span class="oj-test-receipt-seal"><UiIcon :name="job.status==='AC'?'shield-check':'info'" :size="19" /></span><div><strong>{{ job.status==='AC'?'全部测试用例通过':hasCaseTotals?'测试集未全部通过':'暂无测试用例结果' }}</strong><p>{{ job.kind==='SUBMIT'?'隐藏测试的输入与输出不公开。':'该记录没有可展示的用例输出。' }}</p></div><span v-if="hasCaseTotals" class="oj-test-receipt-count">{{ job.passedCases }}<small>/ {{ job.totalCases }}</small></span></div>
            <div v-if="hasCaseTotals" class="oj-test-coverage" role="progressbar" aria-label="测试用例通过比例" :aria-valuenow="completion" aria-valuemin="0" aria-valuemax="100"><span v-for="n in 20" :key="n"><i :style="{width:Math.max(0,Math.min(100,completion*20-(n-1)*100))+'%'}" /></span></div>
          </section>
        </div>
        <div v-else-if="tab==='performance'">
          <div v-if="performanceLoading" class="oj-modal-loading" role="status"><i />正在加载性能数据…</div>
          <div v-else-if="performanceError" class="oj-modal-error" role="alert"><p>{{ performanceError }}</p><button class="oj-modal-button" @click="emit('retry-performance')">重试</button></div>
          <OjPerformance v-else-if="performance" :data="performance" :limits="limits" />
          <div v-else class="oj-modal-empty"><UiIcon name="chart" :size="22" /><h3>暂无性能数据</h3><p>通过后可以查看时间和空间表现。</p></div>
        </div>
        <div v-else class="oj-result-source"><p class="oj-modal-hint">{{ codeFormatting?'正在格式化展示…':'本次提交的代码' }} <span v-if="codeFormatError">· {{ codeFormatError }}</span></p><OjCodeEditor v-if="job.code" :model-value="formattedCode" :language="job.language" read-only label="本次提交的代码" /><p v-else class="oj-modal-hint">该记录没有代码。</p></div>
        <footer class="oj-result-actions"><p v-if="job.kind==='RUN'" class="oj-result-run-note">本次运行不计入正式通过数</p><div class="oj-result-action-buttons"><button class="oj-modal-button" @click="emit('resume')"><UiIcon name="edit" :size="13" />继续编辑</button><slot name="next" /></div></footer>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import OjCodeEditor from './OjCodeEditor.vue'
import OjPerformance from './OjPerformance.vue'
import OjStatusBadge from './OjStatusBadge.vue'
import OjExecutionProgress from './OjExecutionProgress.vue'
import UiIcon from './UiIcon.vue'
import { ojStatuses,languageNames,modeNames,kindNames,formatOjDate } from '@/utils/ojDictionaries'
import { formatOjCode } from '@/utils/ojFormatter'

const props=defineProps({ job:Object, pending:Boolean, loading:Boolean, error:String, retryable:Boolean, performance:Object, performanceLoading:Boolean, performanceError:String, limits:Object })
const emit=defineEmits(['restore','retry','retry-performance','resume'])
const tab=ref('cases'),caseIndex=ref(0),formattedCode=ref(''),codeFormatting=ref(false),codeFormatError=ref('')
let codeGeneration=0
const cases=computed(()=>props.job?.result?.cases || [])
const selectedCase=computed(()=>cases.value[caseIndex.value])
const hasCaseTotals=computed(()=>Number(props.job?.totalCases)>0 && props.job?.passedCases!=null && Number.isFinite(Number(props.job.passedCases)))
const completion=computed(()=>hasCaseTotals.value?Math.max(0,Math.min(100,100*Number(props.job.passedCases)/Number(props.job.totalCases))):0)
const tone=computed(()=>props.job?.status==='AC'?'success':props.pending?'waiting':'failed')
const verdict=computed(()=>ojStatuses[props.job?.status] || {label:'未知状态',tone:'neutral',icon:'info',help:'暂无判题状态说明'})
const verdictCode=computed(()=>({SYSTEM_ERROR:'ERROR',VALIDATION_FAILED:'INVALID'})[props.job?.status] || props.job?.status)
const verdictTitle=computed(()=>props.job?.status==='AC'?props.job.kind==='RUN'?'运行通过':props.job.kind==='VALIDATE'?'验证通过':'提交通过':verdict.value.label)
const detailMessage=computed(()=>{
  const message=props.job?.result?.message
  return typeof message==='string' && !['通过',verdict.value.label].includes(message.trim())?message:''
})
const tabs=computed(()=>[{value:'cases',label:'测试详情',icon:'clipboard-check'},...(props.job?.status==='AC'?[{value:'performance',label:'性能表现',icon:'chart'}]:[]),...(props.job?.code?[{value:'code',label:'提交代码',icon:'code'}]:[])])
watch(()=>props.job?.id,()=>{tab.value='cases';caseIndex.value=0;formattedCode.value=props.job?.code || '';codeGeneration++;codeFormatting.value=false;codeFormatError.value=''})
watch([tab,()=>props.job?.code],async()=>{
  if(tab.value!=='code'||!props.job?.code)return
  const generation=++codeGeneration;formattedCode.value=props.job.code;codeFormatting.value=true;codeFormatError.value=''
  try{const source=await formatOjCode(props.job.code,props.job.language);if(generation===codeGeneration)formattedCode.value=source}
  catch{if(generation===codeGeneration)codeFormatError.value='保留原始代码展示'}
  finally{if(generation===codeGeneration)codeFormatting.value=false}
})
onBeforeUnmount(()=>codeGeneration++)
</script>
<style scoped>
.oj-result-execution{border:1px solid #e5edf6;border-radius:8px;padding:0 13px;margin-bottom:15px}.oj-result-execution>summary{display:flex;align-items:center;justify-content:space-between;cursor:pointer;list-style:none;color:#7f99b1;font-size:11px;padding:10px 0}.oj-result-execution>summary::-webkit-details-marker{display:none}.oj-result-execution[open]>summary>.ui-icon{transform:rotate(180deg)}
.oj-verdict--success{--outcome:#258b72;--outcome-soft:#f3f6fb;--outcome-line:#d9e4f2;--outcome-deep:#293f5b}
.oj-verdict--warning{--outcome:#b17a34;--outcome-soft:#fff8ec;--outcome-line:#eddfc6;--outcome-deep:#96622b}
.oj-verdict--danger{--outcome:#b85a69;--outcome-soft:#fff2f4;--outcome-line:#efd5db;--outcome-deep:#a14b5a}
.oj-verdict--waiting,.oj-verdict--working,.oj-verdict--neutral{--outcome:#427db8;--outcome-soft:#f0f6ff;--outcome-line:#d4e3f4;--outcome-deep:#315f97}
.oj-run-result .oj-result-banner.oj-verdict{position:relative;isolation:isolate;display:grid;grid-template-columns:42px minmax(0,1fr) auto;align-items:center;gap:14px;padding:19px 20px;margin:0 0 16px;overflow:hidden;border:1px solid var(--outcome-line);border-radius:11px;background:linear-gradient(108deg,var(--outcome-soft),#fff 78%);box-shadow:0 3px 12px #183f6110,inset 0 1px #fff;animation:oj-verdict-arrive .28s ease both}
.oj-verdict::before{content:'';position:absolute;left:0;top:15px;bottom:15px;width:3px;border-radius:0 3px 3px 0;background:var(--outcome)}
.oj-verdict::after{content:'';position:absolute;z-index:-1;right:-12px;top:-30px;width:150px;height:150px;opacity:.35;background:radial-gradient(circle,var(--outcome-line) 1px,transparent 1.6px);background-size:9px 9px;mask-image:linear-gradient(90deg,transparent,#000)}
.oj-verdict-seal{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;width:42px;height:45px;border:1px solid var(--outcome-line);border-radius:10px;background:linear-gradient(155deg,#fff,var(--outcome-soft));color:var(--outcome);box-shadow:0 2px 5px #234d4310,inset 0 1px #fff}
.oj-verdict-seal>small{font:650 8px ui-monospace,SFMono-Regular,Consolas,monospace;letter-spacing:.55px}
.oj-verdict--success .oj-verdict-seal :deep(path){stroke-dasharray:26;stroke-dashoffset:26;animation:oj-verdict-check .4s .12s ease forwards}
.oj-verdict--success .oj-verdict-seal{background:linear-gradient(150deg,#fff,#f5f8fd);box-shadow:0 2px 5px #2543680e,inset 0 1px #fff}
.oj-verdict--working .oj-verdict-seal>.ui-icon{animation:oj-verdict-spin 1.5s linear infinite}
.oj-verdict-title{display:flex;align-items:center;gap:9px;color:var(--outcome-deep);font-size:15px;font-weight:650;line-height:1.6;margin:0}
.oj-run-result .oj-verdict .oj-verdict-description{font-size:11px;line-height:1.65;color:#688398;margin:3px 0 8px}
.oj-verdict-context{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:10px;color:#6e889e}
.oj-verdict-context>span{display:inline-flex;align-items:center;gap:4px}
.oj-verdict-context>span+span::before{content:'';height:8px;width:1px;background:var(--outcome-line);margin-right:2px}
.oj-verdict-reference{display:flex;flex-direction:column;align-items:flex-end;gap:8px;min-width:0;color:#728da5}
.oj-verdict-reference>span{display:inline-flex;align-items:center;gap:5px;border:1px solid #dce6f088;border-radius:5px;padding:3px 6px;background:#ffffffa6;font:10px ui-monospace,SFMono-Regular,Consolas,monospace}
.oj-verdict-reference>time{font-size:10px;font-variant-numeric:tabular-nums}
.oj-test-receipt{border:1px solid var(--outcome-line);border-radius:10px;overflow:hidden;background:#fff;box-shadow:0 2px 9px #244c7310;animation:oj-verdict-arrive .3s .07s ease both}
.oj-test-receipt>header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 14px;background:linear-gradient(110deg,var(--outcome-soft),#fff);border-bottom:1px solid var(--outcome-line)}
.oj-test-receipt>header>span{display:flex;align-items:center;gap:6px;font-size:11px;color:var(--outcome-deep);font-weight:550}
.oj-test-receipt>header>small{display:inline-flex;align-items:center;gap:4px;font-size:9px;color:#7890a5}
.oj-test-receipt-content{display:flex;align-items:center;gap:11px;padding:19px 16px 14px}
.oj-test-receipt-seal{display:grid;place-items:center;width:34px;height:34px;flex:none;border-radius:10px;background:var(--outcome-soft);border:1px solid var(--outcome-line);color:var(--outcome)}
.oj-test-receipt-content>div{min-width:0}
.oj-test-receipt-content strong{font-size:12px;font-weight:550;line-height:1.65;color:#3b617b}
.oj-test-receipt-content p{font-size:10px;line-height:1.8;color:#7d93a7;margin:4px 0 0}
.oj-test-receipt-count{display:flex;align-items:baseline;gap:5px;margin-left:auto;flex:none;color:var(--outcome-deep);font:500 22px ui-monospace,SFMono-Regular,Consolas,monospace;letter-spacing:-.6px}
.oj-test-receipt-count>small{font:10px ui-monospace,SFMono-Regular,Consolas,monospace;color:#8098a9;letter-spacing:0}
.oj-test-coverage{display:flex;gap:4px;margin:0 16px 16px}
.oj-test-coverage>span{height:6px;flex:1;overflow:hidden;border-radius:2px;background:#edf2f7}
.oj-test-coverage i{display:block;height:100%;border-radius:2px;background:linear-gradient(90deg,var(--outcome),var(--outcome-deep));transform-origin:left;animation:oj-coverage-fill .45s .12s ease both}
.oj-verdict--success .oj-test-coverage i{background:linear-gradient(90deg,#299783,#27796f)}
.oj-result-actions{display:flex;align-items:center;justify-content:flex-end;gap:14px;padding:15px 16px;margin-top:20px;border:1px solid #d9e6f5;border-radius:10px;background:linear-gradient(115deg,#f2f7ff,#fff);box-shadow:inset 0 1px #fff}
.oj-result-run-note{margin:0 auto 0 0;color:#7e96af;font-size:11px;line-height:1.8}
.oj-result-action-buttons{display:flex;align-items:center;justify-content:flex-end;gap:9px;min-width:0}
.oj-result-actions .oj-modal-button{flex:none}
.oj-result-actions :deep(.oj-result-next){display:flex;align-items:center;gap:8px;min-width:0;max-width:260px;margin:0;padding:8px 10px;border:1px solid #c7daef;border-radius:7px;background:linear-gradient(115deg,#e8f2ff,#f6faff);color:#376fae;cursor:pointer;text-align:left;font-family:inherit;box-shadow:inset 0 1px #fff;transition:background .18s,box-shadow .18s}
.oj-result-actions :deep(.oj-result-next:hover){background:#e4f0ff;box-shadow:0 3px 10px #2b64a318}
.oj-result-actions :deep(.oj-result-next>span){min-width:0;display:flex;flex-direction:column;gap:3px;color:inherit}
.oj-result-actions :deep(.oj-result-next small){font-size:9px;color:#6b8fb6}
.oj-result-actions :deep(.oj-result-next strong){font-size:11px;font-weight:550;line-height:1.5;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:200px}
.oj-result-actions :deep(.oj-result-next>.ui-icon){transition:transform .18s}
.oj-result-actions :deep(.oj-result-next:hover>.ui-icon){transform:translateX(2px)}
@keyframes oj-verdict-arrive{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:none}}
@keyframes oj-verdict-check{to{stroke-dashoffset:0}}
@keyframes oj-verdict-spin{to{transform:rotate(360deg)}}
@keyframes oj-coverage-fill{from{transform:scaleX(0)}to{transform:scaleX(1)}}
@media(max-width:600px){.oj-run-result .oj-result-banner.oj-verdict{grid-template-columns:36px minmax(0,1fr);gap:10px;padding:14px}.oj-verdict-seal{width:34px;height:40px;border-radius:8px}.oj-verdict-title{font-size:14px;gap:7px}.oj-verdict-reference{grid-column:2;flex-direction:row;align-items:center;justify-content:flex-start;gap:8px}.oj-verdict-reference>time{font-size:9px}.oj-run-result .oj-verdict .oj-verdict-description{font-size:10px;margin-bottom:6px}.oj-verdict-context{font-size:9px;gap:5px}.oj-test-receipt-content{padding:15px 12px 13px;gap:8px}.oj-test-receipt-content strong{font-size:11px}.oj-test-receipt-content p{font-size:9px}.oj-test-receipt-count{font-size:19px;gap:3px}.oj-test-receipt-count>small{font-size:9px}.oj-test-receipt-seal{width:27px;height:30px;border-radius:7px}.oj-test-coverage{margin-inline:12px;gap:3px}.oj-result-actions{flex-wrap:wrap;gap:12px;padding:12px}.oj-result-action-buttons{flex:1;justify-content:flex-end}.oj-result-actions :deep(.oj-result-next){max-width:210px;padding:7px 9px}.oj-result-actions :deep(.oj-result-next strong){max-width:160px}}
@media(max-width:400px){.oj-result-action-buttons{flex-basis:100%;justify-content:space-between}.oj-test-receipt-count{font-size:17px}.oj-test-receipt-seal{display:none}}
@media(prefers-reduced-motion:reduce){.oj-run-result *,.oj-run-result :deep(*){animation:none!important;transition:none!important}.oj-verdict--success .oj-verdict-seal :deep(path){stroke-dashoffset:0}}
</style>
