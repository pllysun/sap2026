<template><div class="oj-admin-shell"><header><router-link to="/oj" class="brand">SAP OJ <small>管理控制台</small></router-link><nav><router-link to="/oj">题库与判题</router-link><router-link v-if="roles.includes(0)" to="/users">账号</router-link><router-link v-if="roles.includes(0)" to="/settings">系统设置</router-link><router-link to="/storage">题包归档</router-link><router-link to="/audit">操作审计</router-link></nav><a href="/oj">用户端</a><span>{{name}}</span><button @click="logout">退出</button></header><main><router-view /></main></div></template>
<script setup>
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import request,{clearAuth} from '@/utils/request'
const router=useRouter(),name=ref(''),roles=ref([])
onMounted(async()=>{try{const r=await request.get('/api/auth/info');name.value=r.data.user.nickname;roles.value=r.data.roles.map(Number);if(!roles.value.some(n=>n<3))logout()}catch{logout()}})
async function logout(){await request.post('/api/auth/logout').catch(()=>{});clearAuth();router.push('/login')}
</script>
<style scoped>.oj-admin-shell{min-height:100dvh;background:#f5f7fb}.oj-admin-shell header{display:flex;align-items:center;gap:24px;min-height:64px;background:#fff;border-bottom:1px solid #e3e8f0;padding:0 max(24px,calc((100% - 1480px)/2));font-size:13px}.brand{font-size:18px;font-weight:700;color:#273d5a;white-space:nowrap}.brand small{font-size:11px;font-weight:400;color:#8797ad;margin-left:10px}.oj-admin-shell nav{display:flex;gap:22px;flex:1}.oj-admin-shell a{text-decoration:none;color:#657894}.oj-admin-shell a.router-link-active{color:#3974b5}.oj-admin-shell button{border:0;background:transparent;color:#657894;cursor:pointer}.oj-admin-shell main{max-width:1480px;margin:auto;padding:26px 24px}@media(max-width:760px){.oj-admin-shell header{padding:14px;gap:12px;flex-wrap:wrap}.brand small{display:none}.oj-admin-shell nav{order:2;flex-basis:100%;padding-top:8px}.oj-admin-shell main{padding:18px 12px}}
</style>
