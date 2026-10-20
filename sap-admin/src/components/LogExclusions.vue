<template>
  <section class="zen-card log-exclusions">
    <div class="exclusion-heading"><div><h3>接口日志过滤</h3><p>过滤的接口照常运行，调用不写入日志与请求统计；已有记录也会退出展示和统计。移除规则后恢复记录。</p></div><el-button v-if="canEdit" :loading="saving" :disabled="!dirty" type="primary" @click="save">保存规则</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading" class="rules-list">
      <div v-for="(rule,index) in rules" :key="`${rule.path}-${rule.method}`" class="rule-chip"><code>{{ rule.method==='*'?'全部方法':rule.method }}</code><span>{{ rule.path }}</span><el-button v-if="canEdit" link type="danger" :disabled="saving" @click="rules.splice(index,1)" :aria-label="`移除 ${rule.path} 的过滤`"><el-icon><Close /></el-icon></el-button></div>
      <span v-if="!rules.length&&!loading" class="rule-empty">尚未设置过滤规则</span>
    </div>
    <form v-if="canEdit" class="rule-form" @submit.prevent="add">
      <el-select v-model="method" aria-label="过滤请求方法" style="width:125px"><el-option v-for="m in methods" :key="m" :label="m==='*'?'全部方法':m" :value="m" /></el-select>
      <el-input v-model="path" maxlength="191" placeholder="/api/example/{id}/heartbeat" aria-label="过滤接口路径" :disabled="saving" />
      <el-button native-type="submit" :disabled="!path.trim()||saving||rules.length>=50">添加规则</el-button>
      <el-button v-if="dirty" text :disabled="saving" @click="reset">撤销修改</el-button>
    </form>
    <p class="rule-note">{{ rules.length }} / 50 条 · {id} 代表一个路径段，支持指定请求方法。{{ canEdit?'保存后立即生效，重启后保留。':'当前权限可查看，由超级管理员或会长维护。' }}</p>
  </section>
</template>
<script setup>
import { ref,computed,onMounted } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
const emit=defineEmits(['saved'])
const rules=ref([]),saved=ref([]),path=ref(''),method=ref('*'),canEdit=ref(false),loading=ref(true),saving=ref(false),error=ref('')
const methods=['*','GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS']
const dirty=computed(()=>JSON.stringify(rules.value)!==JSON.stringify(saved.value))
const copy=items=>items.map(item=>({...item}))
function reset(){rules.value=copy(saved.value);error.value=''}
function add(){const next={path:path.value.trim(),method:method.value};if(!/^\/api\/(?:[A-Za-z0-9_.-]+|\{[A-Za-z][A-Za-z0-9_]*})(?:\/(?:[A-Za-z0-9_.-]+|\{[A-Za-z][A-Za-z0-9_]*}))*$/.test(next.path)||next.path.includes('..')){ElMessage.warning('请输入完整接口路径，动态路径段使用 {id}');return}if(rules.value.some(r=>r.path===next.path&&r.method===next.method)){ElMessage.warning('该规则已经存在');return}rules.value.push(next);path.value=''}
async function save(){saving.value=true;error.value='';try{const {data}=await request.put('/api/log/exclusions',rules.value);rules.value=copy(data);saved.value=copy(data);emit('saved');ElMessage.success('过滤规则已生效')}catch(e){error.value=e.message||'保存失败'}finally{saving.value=false}}
onMounted(async()=>{try{const {data}=await request.get('/api/log/exclusions');rules.value=copy(data.rules);saved.value=copy(data.rules);canEdit.value=data.canEdit}catch(e){error.value=e.message||'规则加载失败'}finally{loading.value=false}})
</script>
<style scoped>
.log-exclusions{margin-bottom:20px}.exclusion-heading{display:flex;align-items:center;justify-content:space-between;gap:20px}.exclusion-heading h3{font-size:15px;margin:0 0 8px}.exclusion-heading p,.rule-note,.rule-empty{font-size:12px;color:var(--zen-text-muted);line-height:1.7;margin:0}.rules-list{display:flex;flex-wrap:wrap;gap:10px;min-height:42px;margin:18px 0}.rule-chip{display:flex;align-items:center;gap:10px;padding:7px 10px;border:1px solid var(--zen-border-light);border-radius:8px;background:var(--zen-bg);font-size:12px;overflow-wrap:anywhere}.rule-chip code{color:var(--el-color-primary);font-size:10px;white-space:nowrap}.rule-form{display:flex;align-items:center;gap:10px;max-width:850px;margin-bottom:12px}.rule-form>.el-input{flex:1}.rule-note{font-size:11px}@media(max-width:700px){.exclusion-heading{align-items:flex-start}.rule-form{flex-wrap:wrap}.rule-form .el-input{flex-basis:calc(100% - 145px)}.rule-chip{max-width:100%}.rule-chip span{overflow-wrap:anywhere}}
</style>
