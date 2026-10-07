<template>
  <span class="oj-problem-progress" :class="[label?'oj-row-number':'oj-progress-symbol',`oj-progress-${state.toLowerCase()}`]" :data-progress="state" :title="description" :aria-label="label?`${label} · ${description}`:description">
    <span v-if="label">{{ label }}</span>
    <svg v-if="label?['AC','WRONG','ISSUE'].includes(state):state!=='PENDING'" :class="label?'oj-progress-mark':'oj-progress-glyph'" viewBox="0 0 24 24" aria-hidden="true">
      <path v-if="state==='AC'" d="m6 12 4 4 8-8" />
      <path v-else-if="state==='WRONG'" d="m8 8 8 8M16 8l-8 8" />
      <path v-else-if="state==='ISSUE'" d="M12 6v7m0 4h.01" />
      <path v-else-if="!label" d="M8 12h8" />
    </svg>
    <i v-if="state==='PENDING'" :class="label?'oj-progress-dot':'oj-progress-spinner'" aria-hidden="true" />
  </span>
</template>
<script setup>
import {computed} from 'vue'
import {ojStatuses} from '@/utils/ojDictionaries'
const props=defineProps({label:{type:String,default:''},progress:{type:Object,default:()=>({})}})
const state=computed(()=>['AC','WRONG','ISSUE','PENDING'].includes(props.progress?.state)?props.progress.state:'NONE')
const description=computed(()=>state.value==='AC'?'已通过':state.value==='WRONG'?'最近提交：答案错误':state.value==='ISSUE'?`最近提交：${ojStatuses[props.progress?.lastVerdict]?.label||'运行异常'}`:state.value==='PENDING'?'判题中':'尚未提交')
</script>
<style scoped>
.oj-problem-progress{position:relative;font-weight:600;transition:color .2s,background .2s,border-color .2s;box-sizing:border-box;flex:none}
.oj-progress-symbol{display:grid;place-items:center;width:36px;height:36px;border:1px solid #dce6f0;border-radius:50%;color:#a1b1c2;background:#f8fafc}
.oj-progress-glyph{width:22px;height:22px}.oj-progress-glyph path{fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.oj-problem-progress.oj-progress-ac{color:#20815e;background:#eaf8ef;border-color:#b9e2cb}
.oj-problem-progress.oj-progress-wrong{color:#c54855;background:#fff0f2;border-color:#f1c5cb}
.oj-problem-progress.oj-progress-issue{color:#a87212;background:#fff8e6;border-color:#ebd59b}
.oj-problem-progress.oj-progress-pending{color:#327abc;background:#edf6ff;border-color:#c5ddf4}
.oj-progress-mark{position:absolute;right:-4px;bottom:-4px;width:15px;height:15px;background:currentColor;border:2px solid #fff;border-radius:50%;box-sizing:content-box}
.oj-progress-mark:empty{display:none}.oj-progress-mark path{fill:none;stroke:#fff;stroke-width:2.3;stroke-linecap:round;stroke-linejoin:round}
.oj-progress-dot{position:absolute;right:-2px;bottom:-2px;width:8px;height:8px;background:currentColor;border:2px solid #fff;border-radius:50%;animation:oj-progress-pulse 1.4s ease-in-out infinite}
.oj-progress-spinner{width:18px;height:18px;border:2px solid #c5ddf4;border-top-color:currentColor;border-radius:50%;animation:oj-progress-spin .8s linear infinite}
@keyframes oj-progress-pulse{50%{opacity:.45;transform:scale(.8)}}
@keyframes oj-progress-spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.oj-progress-dot,.oj-progress-spinner{animation:none}.oj-problem-progress{transition:none}}
</style>
