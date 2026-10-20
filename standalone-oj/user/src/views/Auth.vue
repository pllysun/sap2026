<template><main class="oj-auth-page"><form class="oj-auth-card" @submit.prevent="submit">
  <router-link class="oj-auth-brand" to="/oj">{{siteName}}</router-link><h1>{{register?'创建练习账号':'欢迎回来'}}</h1><p>登录后继续你的学习与练习。</p>
  <label>账号<input v-model.trim="form.studentId" autocomplete="username" maxlength="32" required placeholder="3–32 位字母、数字或下划线" /></label>
  <label v-if="register">昵称<input v-model.trim="form.nickname" autocomplete="nickname" maxlength="50" required /></label>
  <label>密码<input v-model="form.password" :autocomplete="register?'new-password':'current-password'" type="password" :minlength="register?12:1" maxlength="72" required :placeholder="register?'至少 12 个字符':''" /></label>
  <p v-if="error" class="oj-auth-error" role="alert">{{error}}</p><button class="oj-auth-submit" :disabled="busy||register&&!registrationEnabled">{{busy?'处理中…':register?'注册账号':'登录'}}</button>
  <p v-if="register&&!registrationEnabled">当前未开放注册，请联系管理员创建账号。</p><router-link v-if="!register&&registrationEnabled" to="/register">注册账号</router-link><router-link v-if="register" to="/login">已有账号，返回登录</router-link><a class="oj-auth-admin" href="/admin/">管理入口</a>
</form></main></template>
<script setup>
import {computed,ref,onMounted} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import request from '@/utils/request'
import {useUserStore} from '@/stores/user'
import {safeRedirect} from '@/utils/authNavigation'
import '@/assets/styles/auth.css'
const route=useRoute(),router=useRouter(),store=useUserStore(),register=computed(()=>route.path==='/register')
const form=ref({studentId:'',password:'',nickname:''}),error=ref(''),busy=ref(false),registrationEnabled=ref(false),siteName=ref('SAP OJ')
onMounted(async()=>{try{const config=(await request.get('/api/public/config')).data;registrationEnabled.value=config.registrationEnabled;siteName.value=config.siteName}catch{}})
async function submit(){if(busy.value)return;error.value='';busy.value=true;try{if(register.value){await store.register(form.value);form.value.password='';await router.push('/login')}else{await store.login(form.value.studentId,form.value.password);await router.push(safeRedirect(route.query.redirect))}}catch(e){error.value=e.message||'操作失败，请重试'}finally{busy.value=false}}
</script>
