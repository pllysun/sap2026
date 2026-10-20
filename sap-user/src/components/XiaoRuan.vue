<template>
  <div ref="host" class="xiaoruan" :style="{'--mascot-size':size/16+'rem'}" aria-hidden="true">
    <img v-if="visible && !hidden && !failed" :src="source" width="192" height="192" alt="" decoding="async" loading="lazy" @error="fallback" />
  </div>
</template>
<script setup>
import {ref,computed,onMounted,onBeforeUnmount} from 'vue'
const props=defineProps({mood:{type:String,default:'idea'},size:{type:Number,default:80}})
const stills={cheer:new URL('../assets/xiaoruan/cheer.webp',import.meta.url).href,approve:new URL('../assets/xiaoruan/approve.webp',import.meta.url).href,idea:new URL('../assets/xiaoruan/idea.webp',import.meta.url).href,question:new URL('../assets/xiaoruan/question.webp',import.meta.url).href,coding:new URL('../assets/xiaoruan/coding.webp',import.meta.url).href,celebrate:new URL('../assets/xiaoruan/celebrate.webp',import.meta.url).href}
const animations={cheer:new URL('../assets/xiaoruan/cheer-motion.webp',import.meta.url).href,approve:new URL('../assets/xiaoruan/approve-motion.webp',import.meta.url).href,idea:new URL('../assets/xiaoruan/idea-motion.webp',import.meta.url).href,question:new URL('../assets/xiaoruan/question-motion.webp',import.meta.url).href,coding:new URL('../assets/xiaoruan/coding-motion.webp',import.meta.url).href,celebrate:new URL('../assets/xiaoruan/celebrate-motion.webp',import.meta.url).href}
const host=ref(null),visible=ref(false),hidden=ref(false),failed=ref(false),reduce=ref(false),motionFailed=ref(false)
const source=computed(()=>(reduce.value||motionFailed.value?stills:animations)[props.mood]||stills.idea)
let observer,media,connection
function preferences(){reduce.value=Boolean(media?.matches||connection?.saveData)}
function visibility(){hidden.value=document.hidden}
function fallback(){if(!reduce.value&&!motionFailed.value)motionFailed.value=true;else failed.value=true}
onMounted(()=>{
  media=matchMedia('(prefers-reduced-motion: reduce)');connection=navigator.connection;preferences();visibility()
  media.addEventListener('change',preferences);connection?.addEventListener?.('change',preferences);document.addEventListener('visibilitychange',visibility)
  observer=new IntersectionObserver(([entry])=>{visible.value=entry.isIntersecting},{rootMargin:'0px'});observer.observe(host.value)
})
onBeforeUnmount(()=>{observer?.disconnect();media?.removeEventListener('change',preferences);connection?.removeEventListener?.('change',preferences);document.removeEventListener('visibilitychange',visibility)})
</script>
<style scoped>
.xiaoruan{position:relative;width:var(--mascot-size);height:var(--mascot-size);flex:none;pointer-events:none}.xiaoruan img{width:100%;height:100%;object-fit:contain;animation:xiaoruan-enter .4s ease both}@keyframes xiaoruan-enter{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:none}}@media(prefers-reduced-motion:reduce){.xiaoruan img{animation:none}}
</style>
