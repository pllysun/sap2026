<template>
  <section class="zen-card explorer">
    <el-tabs v-model="period" @tab-change="reset">
      <el-tab-pane label="近期接口 · 90 天明细" name="recent" />
      <el-tab-pane label="历史归档 · 小时聚合" name="archive" />
    </el-tabs>
    <p class="hint">{{ period === 'archive' ? '永久保留按小时、接口、用户、IP、结果的聚合记录；次数、首末调用时间及耗时仍可查，不再保留每一次请求。历史数据的结果码为空表示旧版未记录。' : '支持 Web / App 合并查看，按接口或用户下钻。请求正文、密码、验证码和令牌不会写入此处。' }}</p>
    <div class="toolbar">
      <el-radio-group v-model="source" @change="reset"><el-radio-button value="">全部</el-radio-button><el-radio-button value="WEB">Web 接口</el-radio-button><el-radio-button value="APP">App 接口</el-radio-button></el-radio-group>
      <el-select v-model="dimension" style="width:140px" @change="reset"><el-option label="详细记录" value="detail"/><el-option label="接口维度" value="endpoint"/><el-option label="用户维度" value="user"/></el-select>
      <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" :disabled-date="disabledDate" @change="reset"/>
      <el-input v-model="user" placeholder="用户 ID（-1 为匿名）" clearable style="width:180px" @change="reset"/>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
    <el-table :data="rows" v-loading="loading" stripe empty-text="该范围暂无记录">
      <el-table-column v-if="dimension !== 'user'" label="分类 / 接口" min-width="260"><template #default="{ row }"><el-tag size="small" effect="plain">{{ row.source }}</el-tag> <code>{{ row.http_method }}</code><div class="endpoint">{{ row.endpoint }}</div><small>{{ row.description }}</small></template></el-table-column>
      <el-table-column v-if="dimension !== 'endpoint'" label="用户" min-width="130"><template #default="{ row }">{{ row.user_name || '匿名' }}<div class="hint">{{ row.user_id === -1 ? '未登录' : `ID ${row.user_id}` }}</div></template></el-table-column>
      <el-table-column prop="call_count" label="调用次数" width="100"/>
      <el-table-column v-if="dimension === 'endpoint'" prop="user_count" label="用户数" width="95"/>
      <el-table-column v-if="dimension === 'user'" prop="endpoint_count" label="接口数" width="95"/>
      <el-table-column label="结果" width="140"><template #default="{ row }"><template v-if="dimension === 'detail'">{{ row.result_code || '旧版未记录' }}</template><template v-else>{{ row.failure_count }} 次失败<div class="hint" v-if="row.unknown_count">{{ row.unknown_count }} 次未知</div></template></template></el-table-column>
      <el-table-column label="耗时" width="140"><template #default="{ row }">均 {{ Math.round(row.duration_sum / row.call_count) }} ms<div class="hint">最长 {{ row.duration_max }} ms</div></template></el-table-column>
      <el-table-column label="调用时间" min-width="185"><template #default="{ row }">{{ time(row.first_time) }}<div v-if="row.last_time !== row.first_time" class="hint">至 {{ time(row.last_time) }}</div></template></el-table-column>
      <el-table-column v-if="dimension === 'detail'" prop="ip" label="IP" min-width="135"/>
      <el-table-column v-else label="操作" width="95"><template #default="{ row }"><el-button text type="primary" @click="openDetail(row)">查看详情</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[20,50,100]" layout="total,sizes,prev,pager,next" @current-change="load" @size-change="reset"/>
    <el-dialog v-model="detailOpen" :title="detailTitle" width="min(1100px,94vw)" append-to-body modal-class="log-detail-modal" class="log-detail-dialog">
      <p class="hint">{{ period === 'archive' ? '历史小时汇总：每一行可能包含多次调用。' : '最近 90 天的实际调用记录。' }}</p>
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false"/>
      <el-table :data="detailRows" v-loading="detailLoading" max-height="55vh" stripe>
        <el-table-column label="用户" min-width="120"><template #default="{ row }">{{ row.user_name }}<div class="hint">ID {{ row.user_id }}</div></template></el-table-column>
        <el-table-column label="接口" min-width="250"><template #default="{ row }">{{ row.source }} · {{ row.http_method }}<div class="endpoint">{{ row.endpoint }}</div></template></el-table-column>
        <el-table-column prop="call_count" label="次数" width="75"/>
        <el-table-column label="结果" width="100"><template #default="{ row }">{{ row.result_code || '未记录' }}</template></el-table-column>
        <el-table-column prop="ip" label="IP" min-width="125"/>
        <el-table-column label="调用时间" min-width="180"><template #default="{ row }">{{ time(row.first_time) }}<div v-if="row.first_time !== row.last_time">至 {{ time(row.last_time) }}</div></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="detailPage" :page-size="20" :total="detailTotal" layout="total,prev,pager,next" @current-change="loadDetail"/>
    </el-dialog>
  </section>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
const period=ref('recent'), source=ref(''), dimension=ref('endpoint'), dates=ref(null), user=ref('')
const page=ref(1),size=ref(20),total=ref(0),rows=ref([]),loading=ref(false),error=ref('')
const detailOpen=ref(false),detailTitle=ref(''),detailRows=ref([]),detailPage=ref(1),detailTotal=ref(0),detailLoading=ref(false),detailError=ref('')
let selection={}, revision=0, detailRevision=0
const time=value => value ? String(value).replace('T',' ').slice(0,19) : '—'
const disabledDate=date => period.value === 'recent' && date.getTime() < Date.now()-91*86400000
const params=()=>({archive:period.value==='archive',source:source.value||undefined,userId:user.value||undefined,start:dates.value?.[0],end:dates.value?.[1]})
async function load(){
  const rev=++revision; loading.value=true; error.value=''
  try {
    if(user.value && !/^-?\d+$/.test(user.value)) throw new Error('请输入数字用户 ID')
    const res=await request.get('/api/log/explore',{params:{...params(),dimension:dimension.value,current:page.value,size:size.value}})
    if(rev===revision){rows.value=res.data?.records||[];total.value=Number(res.data?.total||0)}
  } catch(e){if(rev===revision){error.value=e.message||'日志加载失败';rows.value=[];total.value=0}}
  finally{if(rev===revision)loading.value=false}
}
function reset(){ page.value=1;detailOpen.value=false;load() }
function openDetail(row){
  selection={...params(),...(dimension.value==='user'?{userId:row.user_id}:{endpoint:row.endpoint,source:row.source,method:row.http_method})}
  detailTitle.value=dimension.value==='user'?`${row.user_name || '匿名'} · 接口调用`:`${row.http_method} ${row.endpoint}`
  detailPage.value=1;detailOpen.value=true;loadDetail()
}
async function loadDetail(){
  const rev=++detailRevision;detailLoading.value=true;detailError.value=''
  try{const res=await request.get('/api/log/explore',{params:{...selection,dimension:'detail',current:detailPage.value,size:20}});if(rev===detailRevision){detailRows.value=res.data?.records||[];detailTotal.value=Number(res.data?.total||0)}}
  catch(e){if(rev===detailRevision){detailError.value=e.message||'详情加载失败';detailRows.value=[]}}
  finally{if(rev===detailRevision)detailLoading.value=false}
}
onMounted(load)
</script>
<style scoped>
.explorer{margin-top:20px}.toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:18px 0}.hint{color:var(--el-text-color-secondary);font-size:12px;line-height:1.7}.endpoint{overflow-wrap:anywhere;line-height:1.6;margin:5px 0}small{color:var(--el-text-color-secondary)}.el-pagination{margin-top:20px;justify-content:flex-end;flex-wrap:wrap}.el-alert{margin:12px 0}
</style>
<style>
html body .log-detail-modal .el-overlay-dialog{display:flex;align-items:center;padding:16px;overflow:hidden!important}
html body .el-dialog.log-detail-dialog{display:flex;flex-direction:column;margin:auto!important;max-width:100%;max-height:calc(100dvh - 32px)!important;overflow:hidden!important}
html body .el-dialog.log-detail-dialog .el-dialog__body{min-height:0;overflow:auto!important;flex:1}
</style>
