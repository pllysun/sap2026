<template><section class="settings-view" v-loading="busy"><h1>系统设置</h1><p>业务设置保存到数据库，修改后即时生效，重启后保留。</p><el-form label-position="top" :model="form"><el-form-item label="站点名称"><el-input v-model="form.siteName" maxlength="60"/></el-form-item><el-form-item label="允许公开注册"><el-switch v-model="form.registrationEnabled"/><small>关闭后由管理员创建账号</small></el-form-item><el-form-item label="等待队列容量"><el-input-number v-model="form.queueCapacity" :min="1" :max="1000"/></el-form-item><el-form-item label="每人每日正式提交上限"><el-input-number v-model="form.dailySubmissionLimit" :min="0" :max="100000"/><small>0 表示不限</small></el-form-item><el-form-item label="整个判题组最大时长（毫秒）"><el-input-number v-model="form.maxJudgeGroupMs" :min="10000" :max="1800000" :step="10000"/></el-form-item><el-button type="primary" :loading="saving" @click="save">保存设置</el-button></el-form><p>语言与资源限制、判题节点、题目和题单在“题库与判题”中配置。MySQL、Redis、RustFS 地址与凭据在部署配置文件中管理。</p></section></template>
<script setup>
import {ref,onMounted} from 'vue'
import {ElMessage} from 'element-plus'
import request from '@/utils/request'
const form=ref({siteName:'SAP OJ',registrationEnabled:false,queueCapacity:50,dailySubmissionLimit:0,maxJudgeGroupMs:600000}),busy=ref(false),saving=ref(false)
onMounted(async()=>{busy.value=true;try{const r=await request.get('/api/admin/settings');form.value={...r.data,maxJudgeGroupMs:Number(r.data.maxJudgeGroupMs)}}finally{busy.value=false}})
async function save(){saving.value=true;try{await request.put('/api/admin/settings',form.value);ElMessage.success('已保存')}finally{saving.value=false}}
</script>
<style scoped>.settings-view{max-width:780px;background:#fff;border:1px solid #e3e8f0;border-radius:16px;padding:28px}.settings-view h1{font-size:25px;color:#283d58}.settings-view p{font-size:13px;line-height:1.8;color:#8193aa}.settings-view small{color:#8193aa;margin-left:14px}.settings-view .el-form{max-width:560px;margin:26px 0}</style>
