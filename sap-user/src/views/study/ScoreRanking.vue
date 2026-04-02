<template>
  <div class="page">
    <div class="page-header"><h1 class="page-title">成绩统计</h1></div>

    <div class="filter-bar mb-4 anim-in">
      <div class="form-group" style="margin-bottom:0;min-width:110px;">
        <select v-model="selectedGrade" class="select" @change="loadActivities"><option v-for="y in years" :key="y" :value="y">{{ y }}年</option></select>
      </div>
      <div class="form-group" style="margin-bottom:0;min-width:160px;">
        <select v-model="selectedActivityId" class="select" @change="loadRanking">
          <option v-for="a in activityList" :key="a.id" :value="a.id">第{{ a.seqNum }}次 {{ a.title || '' }}</option>
        </select>
      </div>
      <button class="btn btn--primary btn--sm btn--pill" @click="loadRanking">🔍 查询</button>
    </div>

    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <div v-else-if="records.length > 0" class="card anim-in">
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>排名</th><th>学号</th><th>姓名</th><th>总分</th></tr></thead>
          <tbody>
            <tr v-for="r in records" :key="r.userId">
              <td><span v-if="r.rank <= 3" class="badge badge--gradient">{{ r.rank }}</span><span v-else>{{ r.rank }}</span></td>
              <td>{{ r.studentId }}</td><td>{{ r.userName }}</td><td style="font-weight:700;">{{ r.totalScore }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination" v-if="total > pageSize">
        <button class="pagination__btn" :disabled="currentPage<=1" @click="currentPage--;loadRanking()">‹</button>
        <span class="t-caption">{{ currentPage }} / {{ Math.ceil(total/pageSize) }}</span>
        <button class="pagination__btn" :disabled="currentPage>=Math.ceil(total/pageSize)" @click="currentPage++;loadRanking()">›</button>
      </div>
    </div>
    <div v-else class="empty"><div class="empty__text">暂无排名数据</div></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
const years = ref([]); const selectedGrade = ref(''); const activityList = ref([]); const selectedActivityId = ref(null)
const records = ref([]); const total = ref(0); const currentPage = ref(1); const pageSize = 20; const loading = ref(false)

onMounted(async () => { try { const r = await request.get('/api/activity/years'); years.value = r.data||[]; if(years.value.length){selectedGrade.value=years.value[years.value.length-1];await loadActivities()} } catch{} })

async function loadActivities() { if(!selectedGrade.value)return; try{const r=await request.get('/api/study/activity/list',{params:{grade:selectedGrade.value}});activityList.value=r.data||[];if(activityList.value.length){selectedActivityId.value=activityList.value[0].id;currentPage.value=1;await loadRanking()}else{records.value=[];total.value=0}}catch{} }

async function loadRanking() { if(!selectedActivityId.value)return; loading.value=true; try{const r=await request.get('/api/study/ranking',{params:{activityId:selectedActivityId.value,current:currentPage.value,size:pageSize}});records.value=r.data.records||[];total.value=r.data.total||0}catch{}finally{loading.value=false} }
</script>
