<template><section class="oj-profile-card"><h1>我的账号</h1><p>{{store.user?.studentId}}</p><form @submit.prevent="save"><label>昵称<input v-model.trim="nickname" maxlength="50" required /></label><button :disabled="busy">保存昵称</button></form><form @submit.prevent="password"><h2>修改密码</h2><label>当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" required /></label><label>新密码<input v-model="newPassword" type="password" autocomplete="new-password" minlength="12" maxlength="72" required /></label><button :disabled="busy">修改并重新登录</button></form><p role="status">{{message}}</p></section></template>
<script setup>
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {useUserStore} from '@/stores/user'
import request from '@/utils/request'
const store=useUserStore(),router=useRouter(),nickname=ref(''),currentPassword=ref(''),newPassword=ref(''),message=ref(''),busy=ref(false)
onMounted(async()=>{await store.fetchUserInfo();nickname.value=store.user.nickname})
async function save(){busy.value=true;try{await store.updateProfile({nickname:nickname.value});await store.fetchUserInfo();message.value='昵称已保存'}catch(e){message.value=e.message}finally{busy.value=false}}
async function password(){busy.value=true;try{await request.put('/api/auth/password',{currentPassword:currentPassword.value,password:newPassword.value});store.logout();router.push('/login')}catch(e){message.value=e.message}finally{busy.value=false}}
</script>
<style scoped>.oj-profile-card{max-width:560px;background:white;border:1px solid #e1e6ef;border-radius:18px;padding:28px;margin:25px auto}.oj-profile-card h1{font-size:24px}.oj-profile-card h2{font-size:18px;margin-top:24px}.oj-profile-card label{display:block;margin:18px 0;color:#63748e}.oj-profile-card input{display:block;width:100%;border:1px solid #dce2ed;border-radius:8px;padding:10px;margin-top:8px}.oj-profile-card button{border:0;background:#3267b1;color:#fff;padding:10px 18px;border-radius:8px;cursor:pointer}</style>
