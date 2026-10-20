<template>
  <section class="zen-card user-management">
    <div class="user-toolbar">
      <div><h3>平台用户</h3><p>查看全部账号、最高职级与实际权限，点击用户可查看关联资料。</p></div>
      <div class="user-toolbar-actions"><el-button :disabled="!hasFilters" @click="resetFilters">重置筛选</el-button><el-button :loading="loading || optionsLoading" @click="refresh">刷新</el-button></div>
    </div>
    <div class="user-filters" aria-label="平台用户筛选">
      <label class="filter-search"><span>姓名 / 学号 / QQ</span><el-input v-model="keyword" clearable placeholder="搜索姓名、学号、QQ 号或昵称" maxlength="50" @input="search" @keyup.enter="searchNow"><template #prefix><el-icon><Search /></el-icon></template></el-input></label>
      <label><span>届别</span><el-select v-model="filters.grade" clearable filterable placeholder="全部届别" :loading="optionsLoading" @change="searchNow"><el-option v-for="grade in options.grades" :key="grade" :label="`${grade}届`" :value="grade" /></el-select></label>
      <label><span>最高职级</span><el-select v-model="filters.position" clearable filterable placeholder="全部职级" :loading="optionsLoading" @change="searchNow"><el-option v-for="position in options.positions" :key="position.value" :label="position.label" :value="position.value" /></el-select></label>
      <label><span>权限等级</span><el-select v-model="filters.roleCode" clearable placeholder="全部权限等级" @change="permissionChanged"><el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" /></el-select></label>
      <label><span>权限匹配方式</span><el-select v-model="filters.roleMatch" :disabled="filters.roleCode == null || filters.roleCode === -1" @change="searchNow"><el-option label="最高权限等于所选等级" value="highest" /><el-option label="包含所选权限（多角色）" value="contains" /></el-select></label>
      <label><span>性别</span><el-select v-model="filters.gender" clearable placeholder="全部性别" @change="searchNow"><el-option label="男" :value="1" /><el-option label="女" :value="0" /><el-option label="未填写" :value="-1" /></el-select></label>
      <label><span>账号状态</span><el-select v-model="filters.status" clearable placeholder="全部状态" @change="searchNow"><el-option label="正常" :value="1" /><el-option label="已禁用" :value="0" /></el-select></label>
    </div>
    <p class="user-filter-hint">职级按权限优先，同级取最近一届；选择届别后查看该届任职或注册用户。权限等级按账号当前实际授权筛选。</p>
    <el-alert v-if="optionsError" :title="optionsError" type="warning" :closable="false" show-icon><el-button link type="primary" @click="loadOptions">重试加载筛选项</el-button></el-alert>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table :data="rows" v-loading="loading" row-key="id" stripe empty-text="没有符合条件的用户" @row-click="row=>emit('open-profile',row.id)" class="users-table">
      <el-table-column label="用户" min-width="160"><template #default="{row}"><button class="user-name" type="button" @click.stop="emit('open-profile',row.id)"><span class="user-avatar">{{ row.name?.slice(0,1) || '?' }}</span><span><b>{{ row.name }}</b><small>{{ row.nickname !== row.name ? row.nickname : `ID ${row.id}` }}</small></span></button></template></el-table-column>
      <el-table-column prop="studentId" label="学号 / 账号" min-width="130" />
      <el-table-column label="最高职级" min-width="180"><template #default="{row}"><el-tooltip :content="row.positionSource==='term'?'按有效任职档案选取，历史任职可在详情查看':'按账号权限展示，暂无对应的任职档案'" placement="top"><el-tag :type="roleType(row.positionRoleCode)" effect="light">{{ row.positionLabel }}</el-tag></el-tooltip></template></el-table-column>
      <el-table-column label="当前最高权限" min-width="145"><template #default="{row}"><el-tag :type="roleType(row.highestRoleCode)" size="small" effect="plain">{{ row.highestRoleCode == null ? '未分配权限' : `${row.highestRoleCode}级 · ${row.highestRoleName}` }}</el-tag></template></el-table-column>
      <el-table-column prop="qq" label="QQ 号" min-width="135" />
      <el-table-column label="性别" width="80"><template #default="{row}">{{ Number(row.gender)===1?'男':row.gender!=null&&Number(row.gender)===0?'女':'未填写' }}</template></el-table-column>
      <el-table-column prop="grade" label="注册届别" width="100" />
      <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="Number(row.status)===1?'success':'danger'" size="small" effect="plain">{{ Number(row.status)===1?'正常':'已禁用' }}</el-tag></template></el-table-column>
      <el-table-column label="注册时间" min-width="165"><template #default="{row}">{{ date(row.createdAt) }}</template></el-table-column>
      <el-table-column label="关联资料" width="105"><template #default="{row}"><el-button link type="primary" @click.stop="emit('open-profile',row.id)">查看详情</el-button></template></el-table-column>
    </el-table>
    <div class="user-pagination"><span>{{ hasFilters ? '筛选结果' : '全部用户' }} · {{ total }} 个账号</span><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20,50,100]" :total="total" layout="sizes,prev,pager,next" @current-change="load" @size-change="searchNow" /></div>
  </section>
</template>
<script setup>
import { ref,reactive,computed,onMounted,onBeforeUnmount } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getUserDirectory,getUserDirectoryFilters } from '../api'
const emit=defineEmits(['open-profile'])
const rows=ref([]),keyword=ref(''),page=ref(1),size=ref(20),total=ref(0),loading=ref(false),error=ref('')
const filters=reactive({grade:undefined,position:undefined,roleCode:undefined,roleMatch:'highest',gender:undefined,status:undefined})
const options=ref({grades:[],positions:[]}),optionsLoading=ref(false),optionsError=ref('')
const roleOptions=[{value:0,label:'0级 · 超级管理员'},{value:1,label:'1级 · 会长'},{value:2,label:'2级 · 管理员'},{value:3,label:'3级 · 成员'},{value:4,label:'4级 · 游客'},{value:-1,label:'未分配权限'}]
const hasFilters=computed(()=>!!keyword.value.trim() || ['grade','position','roleCode','gender','status'].some(key=>filters[key]!==undefined&&filters[key]!==null&&filters[key]!==''))
const roleType=value=>value==null?'info':({0:'danger',1:'warning',2:'primary',3:'success',4:'info'}[Number(value)]||'info')
let revision=0,optionsRevision=0,timer
const date=value=>value?String(value).replace('T',' ').slice(0,19):'未记录'
async function load(){const rev=++revision;loading.value=true;error.value='';try{const params={current:page.value,size:size.value,keyword:keyword.value.trim()||undefined};for(const [key,value] of Object.entries(filters))if(value!==undefined&&value!==null&&value!=='')params[key]=value;const {data}=await getUserDirectory(params);if(rev!==revision)return;rows.value=data.records||[];total.value=Number(data.total||0)}catch(e){if(rev===revision)error.value=e.message||'用户加载失败'}finally{if(rev===revision)loading.value=false}}
async function loadOptions(){const rev=++optionsRevision;optionsLoading.value=true;optionsError.value='';try{const {data}=await getUserDirectoryFilters();if(rev===optionsRevision)options.value=data}catch(e){if(rev===optionsRevision)optionsError.value=e.message||'筛选项加载失败'}finally{if(rev===optionsRevision)optionsLoading.value=false}}
function search(){clearTimeout(timer);revision++;timer=setTimeout(searchNow,300)}
function searchNow(){clearTimeout(timer);page.value=1;load()}
function permissionChanged(){if(filters.roleCode==null||filters.roleCode==='')filters.roleMatch='highest';searchNow()}
function resetFilters(){keyword.value='';Object.assign(filters,{grade:undefined,position:undefined,roleCode:undefined,roleMatch:'highest',gender:undefined,status:undefined});searchNow()}
function refresh(){clearTimeout(timer);load();loadOptions()}
onMounted(refresh)
onBeforeUnmount(()=>{revision++;optionsRevision++;clearTimeout(timer)})
</script>
<style scoped>
.user-toolbar{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:20px}.user-toolbar h3{font-size:16px;margin:0 0 6px}.user-toolbar p,.user-pagination>span{font-size:12px;color:var(--zen-text-muted);margin:0}.user-toolbar-actions{display:flex;flex-shrink:0}.user-filters{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px 16px;padding:16px;background:var(--zen-bg-warm);border:1px solid var(--zen-border-light,#eef1f7);border-radius:12px}.user-filters label{display:flex;flex-direction:column;min-width:0;gap:7px}.user-filters label>span{font-size:12px;color:var(--zen-text-secondary)}.user-filters .el-select{width:100%}.filter-search{grid-column:span 2}.user-filter-hint{font-size:12px;color:var(--zen-text-muted);line-height:1.7;margin:12px 0 18px}.users-table{cursor:pointer}.user-name{display:flex;gap:10px;align-items:center;text-align:left;padding:4px 0;background:none;border:0;cursor:pointer;color:inherit;font:inherit}.user-name b{font-weight:600}.user-name small{display:block;color:var(--zen-text-muted);margin-top:4px;font-size:11px}.user-avatar{width:34px;height:34px;border-radius:10px;background:var(--el-color-primary-light-9);display:grid;place-items:center;color:var(--el-color-primary);flex:none}.user-pagination{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-top:20px}.user-name:focus-visible{outline:2px solid var(--el-color-primary);border-radius:6px}@media(max-width:760px){.user-toolbar{flex-wrap:wrap}.user-toolbar>div:first-child{width:100%}.user-filters{grid-template-columns:repeat(2,minmax(0,1fr));padding:12px;gap:12px}.user-toolbar-actions{margin-left:auto}}@media(max-width:440px){.user-filters{grid-template-columns:minmax(0,1fr)}.filter-search{grid-column:auto}}
</style>
