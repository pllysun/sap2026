<template><div class="login-page"><el-card class="login-card" shadow="never"><div class="brand">SAP OJ</div><h1>登录管理端</h1><p>题目、题解、题单与判题资源管理</p><el-form label-position="top" @submit.prevent="login"><el-form-item label="账号"><el-input v-model.trim="account" autocomplete="username" maxlength="32" /></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" autocomplete="current-password" show-password maxlength="72" /></el-form-item><el-button type="primary" native-type="submit" :loading="busy" style="width:100%">登录</el-button></el-form><a href="/oj">返回用户端</a></el-card></div></template>
<script setup>
import {ref} from 'vue'
import {useRouter,useRoute} from 'vue-router'
import request from '@/utils/request'
import {safeRedirect} from '@/utils/authNavigation'
const router=useRouter(),route=useRoute(),account=ref(''),password=ref(''),busy=ref(false)
async function login(){if(busy.value)return;busy.value=true;try{const r=await request.post('/api/auth/admin/login',{studentId:account.value,password:password.value});localStorage.setItem('sap-token',r.data.token);localStorage.setItem('sap-user',JSON.stringify(r.data.user));router.push(safeRedirect(route.query.redirect))}catch{}finally{busy.value=false}}
</script>
<style scoped>.login-page{min-height:100dvh;background:#f5f7fb;display:grid;place-items:center;padding:24px}.login-card{width:min(420px,100%);border-radius:18px;padding:18px}.brand{font-weight:700;letter-spacing:2px;color:#546fa5;font-size:14px}.login-card h1{font-size:25px;color:#283d58;margin:24px 0 12px}.login-card p{color:#8290a5;font-size:13px;margin-bottom:26px}.login-card a{display:block;color:#6d84a8;font-size:12px;text-decoration:none;margin-top:20px}</style>
