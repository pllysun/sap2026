<template>
  <section class="oj-sets" aria-label="题单">
    <form class="oj-set-toolbar" @submit.prevent="page=1;load()"><label class="oj-search"><UiIcon name="search" /><input v-model="keyword" aria-label="搜索题单" placeholder="搜索题单" /></label><OjSelect v-model="mode" :options="modeOptions" label="题单模式" @change="page=1;load()" /><button class="btn btn--primary" :disabled="loading">搜索</button></form>
    <p v-if="error" class="oj-error" role="alert">{{ error }} <button class="btn btn--sm" @click="load">重试</button></p>
    <div v-if="loading" class="oj-list-skeleton" aria-busy="true"><div v-for="i in 3" :key="i"><i /><span /><b /></div></div><div v-else-if="!rows.length" class="oj-empty"><h2>暂无题单</h2></div>
    <div v-else class="oj-set-list"><router-link v-for="s in rows" :key="s.id" :to="`/oj/sets/${s.id}`" class="oj-set-row"><span :class="['oj-set-symbol',{contest:s.mode==='CONTEST'}]"><UiIcon :name="s.mode==='CONTEST'?'trophy':'book-open'" :size="24" /></span><div class="oj-set-summary"><h2>{{ s.name }} <span class="oj-set-chip" :class="s.mode.toLowerCase()">{{ s.mode==='CONTEST'?'比赛':'练习' }}</span></h2><p v-if="s.description">{{ s.description }}</p><div class="oj-set-meta"><span>{{ s.total }} 道题</span><span>{{ s.participants }} 人参与</span><span v-if="s.mode==='CONTEST'">{{ date(s.startsAt) }} — {{ date(s.endsAt) }}</span><span v-if="s.accessType==='WHITELIST'">指定名单</span></div></div><div class="oj-set-progress"><strong>{{ s.acCount }}<small> / {{ s.total }}</small></strong><span :class="['oj-set-phase',s.phase.toLowerCase()]"><i />{{ phases[s.phase] }}</span></div><UiIcon name="chevron-right" /><div class="oj-set-line-progress" :aria-label="'已通过 '+s.acCount+' / '+s.total"><i :style="{width:(s.total?Math.min(100,100*s.acCount/s.total):0)+'%'}" /></div></router-link></div>
    <div v-if="total>20" class="oj-pagination"><button class="btn btn--secondary" :disabled="page===1" @click="page--;load()">上一页</button><span>{{ page }} / {{ Math.ceil(total/20) }}</span><button class="btn btn--secondary" :disabled="page*20>=total" @click="page++;load()">下一页</button></div>
  </section>
</template>
<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import UiIcon from '@/components/UiIcon.vue'
import OjSelect from '@/components/OjSelect.vue'
import './sets.css'
const rows=ref([]),page=ref(1),total=ref(0),keyword=ref(''),mode=ref(''),loading=ref(true),error=ref('')
const modeOptions=[{value:'',label:'全部模式'},{value:'PRACTICE',label:'练习模式'},{value:'CONTEST',label:'比赛模式'}]
const phases={PRACTICE:'练习中',UPCOMING:'未开始',RUNNING:'进行中',ENDED:'已结束',ARCHIVED:'已归档'}
const date=t=>t?new Date(Number(t)).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}):'未设置'
let generation=0,disposed=false
async function load(){const version=++generation;loading.value=true;error.value='';try{const{data}=await request.get('/api/oj/sets',{params:{keyword:keyword.value,mode:mode.value,page:page.value}});if(version===generation&&!disposed){rows.value=data.records;total.value=Number(data.total)}}catch(e){if(version===generation)error.value=e.message}finally{if(version===generation)loading.value=false}}
onMounted(load);onBeforeUnmount(()=>{disposed=true;generation++})
</script>
