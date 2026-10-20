<template><section class="audit-view"><header><div><h1>操作审计</h1><p>记录操作、账号和结果，日志不保存密码、令牌、代码或测试数据。</p></div><el-button @click="load">刷新</el-button></header><el-table :data="rows" v-loading="busy"><el-table-column prop="createdAt" label="时间" width="210"/><el-table-column prop="userId" label="账号 ID" width="110"/><el-table-column prop="action" label="操作" min-width="210"/><el-table-column prop="method" label="方法" width="90"/><el-table-column prop="path" label="路径" min-width="250"/><el-table-column label="结果" width="90"><template #default="{row}"><el-tag :type="row.success?'success':'danger'">{{row.success?'成功':'失败'}}</el-tag></template></el-table-column></el-table><el-pagination v-model:current-page="page" :page-size="30" :total="total" layout="prev,pager,next" @current-change="load"/></section></template>
<script setup>
import {ref,onMounted} from 'vue'
import request from '@/utils/request'
const rows=ref([]),total=ref(0),page=ref(1),busy=ref(false)
async function load(){busy.value=true;try{const r=await request.get('/api/admin/audit',{params:{page:page.value}});rows.value=r.data.records;total.value=Number(r.data.total)}finally{busy.value=false}}
onMounted(load)
</script>
<style scoped>.audit-view{background:white;border:1px solid #e3e8f0;border-radius:16px;padding:24px}.audit-view header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.audit-view h1{font-size:25px;color:#283d58;margin:0 0 10px}.audit-view p{font-size:13px;color:#8193aa}.audit-view .el-pagination{margin-top:18px}</style>
