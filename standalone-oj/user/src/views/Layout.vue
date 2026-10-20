<template>
  <div class="user-layout oj-shell">
    <header class="oj-site-nav"><router-link to="/oj" class="oj-brand"><svg viewBox="0 0 32 32" aria-hidden="true"><rect x="1" y="1" width="30" height="30" rx="9" fill="#edf1fc"/><path d="m12 10-6 6 6 6m8-12 6 6-6 6m-3-14-2 16" fill="none" stroke="#546fa5" stroke-width="2" stroke-linecap="round"/></svg><span>{{siteName}}<small>算法学习与练习</small></span></router-link>
      <nav><router-link to="/oj" :class="{selected:route.path==='/oj'}">题库</router-link><router-link to="/oj/learning">学习与训练</router-link></nav>
      <div class="oj-account"><a v-if="store.roles.some(r=>Number(r)<3)" href="/admin/">管理端</a><router-link to="/profile">{{store.user?.nickname||'我的账号'}}</router-link><button @click="logout">退出</button></div>
    </header>
    <main class="layout-main oj-site-main"><router-view /></main>
  </div>
</template>
<script setup>
import {onMounted,ref} from 'vue'
import request from '@/utils/request'
import {useRoute,useRouter} from 'vue-router'
import {useUserStore} from '@/stores/user'
const route=useRoute(),router=useRouter(),store=useUserStore()
const siteName=ref('SAP OJ')
onMounted(()=>{store.fetchUserInfo().catch(()=>{});request.get('/api/public/config').then(r=>siteName.value=r.data.siteName).catch(()=>{})})
function logout(){store.logout();router.push('/login')}
</script>
<style scoped>
.oj-site-nav{height:66px;padding:0 max(24px,calc((100% - 1360px)/2));display:flex;align-items:center;gap:40px;background:#fff;border-bottom:1px solid #e1e6ef;position:sticky;top:0;z-index:40}.oj-brand{display:flex;align-items:center;gap:10px;color:#283d58;font-weight:700;text-decoration:none;white-space:nowrap}.oj-brand svg{width:36px;height:36px}.oj-brand small{display:block;font-size:10px;letter-spacing:1px;font-weight:400;color:#7f90a6;margin-top:3px}.oj-site-nav nav{display:flex;gap:24px}.oj-site-nav nav a,.oj-account a,.oj-account button{font-size:13px;color:#63748e;text-decoration:none;background:none;border:0;cursor:pointer;white-space:nowrap}.oj-site-nav nav a.router-link-active,.oj-site-nav nav a.selected{color:#3267b1}.oj-account{margin-left:auto;display:flex;gap:18px;align-items:center}.oj-site-main{max-width:1360px;margin:auto;padding:28px 24px 48px;min-height:calc(100dvh - 66px)}@media(max-width:760px){.oj-site-nav{height:60px;padding:0 14px;gap:16px}.oj-brand small,.oj-account>a{display:none}.oj-site-nav nav{gap:12px}.oj-account{gap:10px}.oj-site-main{padding:18px 12px 32px}.oj-brand{font-size:14px}.oj-brand svg{width:30px}}
</style>
