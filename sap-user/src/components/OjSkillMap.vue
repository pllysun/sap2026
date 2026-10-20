<template>
  <div ref="root" class="skill-map">
    <svg v-if="edges.length" class="skill-connections" :width="bounds.width" :height="bounds.height" aria-hidden="true"><path v-for="edge in edges" :key="edge.id" :d="edge.d" :class="{active:edge.active}" /></svg>
    <section v-for="group in groups" :key="group.layer" class="skill-layer">
      <header><span>{{ String(group.layer+1).padStart(2,'0') }}</span><h3>{{ group.name }}</h3><small>{{ group.nodes.length }} 个知识点</small></header>
      <div class="skill-layer-grid"><button v-for="node in group.nodes" :key="node.id" :data-node="node.id" :class="['skill-node',node.tier.id,{selected:node.id===selected,related:relatedIds.has(node.id),focus:focus.includes(node.id),recommended:recommendations.includes(node.id),uncovered:!node.questions.length,'has-progress':node.stats.ac>0}]" :aria-label="`${node.name}，${node.tier.label}，${node.stats.ac} 道已通过${recommendations.includes(node.id)?'，建议下一步':''}${focus.includes(node.id)?'，当前方向重点':''}`" :aria-pressed="node.id===selected" @click="$emit('select',node.id)" @mouseenter="hover=node.id" @mouseleave="hover=''" @focus="hover=node.id" @blur="hover=''">
        <span class="skill-node-name">{{ node.name }}<UiIcon v-if="recommendations.includes(node.id)" name="star" :size="13" /><UiIcon v-else-if="!node.depsReady&&node.stats.ac===0" name="link" :size="12" /></span><span class="skill-node-meta"><span>{{ node.tier.label }}</span><b>{{ node.stats.ac }}<small> AC</small></b></span><span class="skill-node-track"><i :style="{width:(node.questions.length?100*node.stats.ac/node.questions.length:0)+'%'}" /></span>
      </button></div>
    </section>
  </div>
</template>
<script setup>
import {ref,computed,watch,nextTick,onMounted,onBeforeUnmount} from 'vue'
import {LAYER_NAMES} from '@/utils/ojLearning'
import UiIcon from './UiIcon.vue'
const props=defineProps({report:{type:Array,default:()=>[]},selected:String,focus:{type:Array,default:()=>[]},recommendations:{type:Array,default:()=>[]},relations:Boolean})
defineEmits(['select'])
const groups=computed(()=>LAYER_NAMES.map((name,layer)=>({name,layer,nodes:props.report.filter(n=>n.layer===layer)})).filter(group=>group.nodes.length))
const root=ref(null),hover=ref(''),edges=ref([]),bounds=ref({width:0,height:0});let observer,frame
const relatedIds=computed(()=>{
  const active=hover.value||props.selected,ids=new Set()
  for(const node of props.report)for(const dep of node.dependsOn){
    if(node.id===active)ids.add(dep)
    if(dep===active)ids.add(node.id)
  }
  return ids
})
function draw(){
  if(!root.value)return
  const parent=root.value.getBoundingClientRect(),rects=new Map([...root.value.querySelectorAll('[data-node]')].map(el=>[el.dataset.node,el.getBoundingClientRect()]))
  bounds.value={width:root.value.offsetWidth,height:root.value.offsetHeight}
  const active=hover.value||props.selected,rows=[]
  for(const node of props.report)for(const dep of node.dependsOn){
    const related=node.id===active||dep===active
    if(!props.relations&&!related)continue
    const from=rects.get(dep),to=rects.get(node.id);if(!from||!to)continue
    const x1=from.left-parent.left+from.width/2,y1=from.bottom-parent.top,x2=to.left-parent.left+to.width/2,y2=to.top-parent.top
    const mid=(y1+y2)/2;rows.push({id:dep+'-'+node.id,active:related,d:`M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`})
  }
  // Draw active relationships last so they remain visible above the rest of the graph.
  edges.value=rows.sort((a,b)=>Number(a.active)-Number(b.active))
}
function schedule(){cancelAnimationFrame(frame);frame=requestAnimationFrame(draw)}
watch(()=>[props.report,props.selected,props.relations,hover.value],()=>nextTick(schedule))
onMounted(()=>{observer=new ResizeObserver(schedule);observer.observe(root.value);schedule()})
onBeforeUnmount(()=>{observer?.disconnect();cancelAnimationFrame(frame)})
</script>
