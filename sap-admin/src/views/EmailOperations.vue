<template>
  <section class="mail-ops">
    <div class="ops-title"><div><h3>代码事件绑定</h3><p>模板参数必须与代码事件完全一致，不可缺少或多出。先解绑，才能删除对应模板。</p></div><el-button :loading="loading" @click="loadBindings">刷新绑定</el-button></div>
    <div class="hook-grid">
      <div v-for="hook in hooks" :key="hook.eventKey" class="hook-card">
        <div class="hook-heading"><strong>{{ hook.title }}</strong><el-tag size="small" :type="hook.compatible === false ? 'danger' : hook.templateId ? 'success' : 'info'">{{ hook.compatible === false ? '参数不匹配' : hook.templateId ? '已绑定' : '未绑定' }}</el-tag></div>
        <code>{{ hook.eventKey }}</code>
        <div class="hook-variables"><el-tag v-for="key in hook.variables" :key="key" size="small" effect="plain">{{ key }}</el-tag></div>
        <el-select v-model="hook.selectedId" placeholder="选择适配该事件的模板" clearable style="width:100%"><el-option v-for="t in compatible(hook)" :key="t.id" :value="String(t.id)" :label="t.templateName + (t.enabled ? '' : '（停用）')" /></el-select>
        <p v-if="hook.compatible === false" class="hint">原绑定模板参数不匹配，已停止生成该事件邮件，请重新选择模板或解绑。</p>
        <p v-if="hook.eventKey === 'PASSWORD_CODE'" class="hint">用户在忘记密码页面点击发送后，向注册 QQ 邮箱发送验证码。有效期 15 分钟，过期不发；同账号/邮箱每 3 分钟一次、24 小时最多 3 次。</p>
        <p v-if="hook.eventKey === 'APP_REGISTRATION_CODE'" class="hint">手机端注册时向本次 QQ 邮箱发送验证码；有效期 15 分钟，每 3 分钟可重发，同账号/邮箱 24 小时最多 5 次。Web 注册流程保持不变。</p>
        <el-input v-if="hook.eventKey === 'ACCOUNT_REGISTERED'" v-model="hook.introduction" type="textarea" :rows="3" placeholder="协会介绍（固定参数，可修改）" style="margin-top:12px" />
        <p v-if="selectedDisabled(hook)" class="hint">模板已停用：绑定后仍不会生成自动邮件，需在模板编辑中启用。</p>
        <div class="ops-actions"><el-button type="primary" plain :loading="saving === hook.eventKey" @click="saveBinding(hook)">保存绑定</el-button><el-button :disabled="!hook.templateId" @click="unbind(hook)">解绑</el-button></div>
      </div>
    </div>
  </section>
  <section class="mail-ops">
    <div class="ops-title"><div><h3>邮件发送中心</h3><p>单条发送，完成后间隔至少 65 秒。空队列休眠，每天 08:00 自动检查。</p></div><div class="ops-actions"><el-button @click="refresh">刷新</el-button><el-button type="primary" plain :loading="activating" @click="activate">唤醒队列</el-button></div></div>
    <div class="queue-summary"><div><b>{{ status.queue || 0 }}</b><span>等待处理 / 发送中</span></div><div><b>{{ status.failed || 0 }}</b><span>待处理失败邮件</span></div><div><b>{{ status.enabled ? '已开启' : '已暂停' }}</b><span>全局发送开关</span></div></div>
    <p class="hint">SMTP 接受不代表最终送达。“结果未知”的邮件请先核实收件情况，避免重复发送。</p>
    <el-tabs v-model="kind" @tab-change="changeTab"><el-tab-pane label="发送队列" name="queue" /><el-tab-pane label="失败邮件" name="failed" /><el-tab-pane label="发送日志" name="logs" /></el-tabs>
    <p v-if="kind === 'logs'" class="hint">每封邮件仅显示最新状态。展开左侧箭头查看历史过程；历史“开始发送”不代表仍在发送。</p>
    <div v-if="kind === 'failed'" class="ops-actions batch"><span>已选 {{ selected.length }} 条</span><el-button type="primary" plain :disabled="!selected.length" :loading="acting" @click="batch('retry')">批量重试</el-button><el-button :disabled="!selected.length" @click="batch('ignore')">忽略</el-button><el-button type="danger" plain :disabled="!selected.length" @click="batch('delete')">永久删除</el-button></div>
    <el-table :key="kind" :row-key="rowKey" :expand-row-keys="expanded.map(rowKey)" :data="rows" v-loading="listLoading" stripe @expand-change="expandHistory" @selection-change="selected = $event" empty-text="暂无记录">
      <el-table-column v-if="kind === 'logs'" type="expand" width="48"><template #default="{row}">
        <div class="mail-history" v-loading="history[row.id]?.loading">
          <section class="business-context">
            <strong>业务事件：{{ row.context?.eventTitle || row.event_key || '系统事件' }}</strong>
            <p>{{ row.context?.reason || '历史记录未保存业务详情' }}</p>
            <p v-if="row.context?.recipient">涉及用户：{{ person(row.context.recipient) }}</p>
            <p v-if="row.context?.initiator">业务触发人：{{ person(row.context.initiator) }}</p>
            <p v-if="row.subject">邮件主题：{{ row.subject }}</p>
            <dl v-if="row.context?.details"><template v-for="(value,key) in row.context.details" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl>
            <p class="hint">{{ row.context?.source === 'legacy' ? '历史日志未保存快照；上方用户资料为当前关联资料，不代表当时资料，未记录的详情无法还原。' : '业务信息为触发时快照；摘要不包含业务验证码或邮件 HTML 正文。' }}</p>
          </section>
          <strong>历史过程（最新在前）</strong>
          <el-timeline><el-timeline-item v-for="event in history[row.id]?.records || []" :key="event.id" :timestamp="date(event.created_at)">
            <b>{{ eventLabel(event.status) }}</b><span class="history-detail">{{ event.detail }}</span>
            <p class="hint">本步执行人：{{ event.context?.operator ? person(event.context.operator) : event.actor_id ? `用户 ID ${event.actor_id}（历史资料未记录）` : '系统' }}</p>
          </el-timeline-item></el-timeline>
          <el-pagination v-if="(history[row.id]?.total || 0)>20" :current-page="history[row.id]?.page || 1" :page-size="20" :total="history[row.id]?.total || 0" layout="prev, pager, next" @current-change="p=>loadHistory(row,p)" />
        </div>
      </template></el-table-column>
      <el-table-column v-if="kind === 'failed'" type="selection" width="48" />
      <el-table-column label="业务事件 / 涉及用户" min-width="340"><template #default="{row}">
        <div class="subject"><strong>{{ row.context?.eventTitle || row.event_key || '系统事件' }}</strong></div>
        <div v-if="row.context?.recipient" class="event-person">{{ person(row.context.recipient) }}</div>
        <div v-if="row.context?.initiator" class="hint">触发人：{{ person(row.context.initiator) }}</div>
        <div class="hint">收件邮箱：{{ row.recipient || '无（未生成邮件或配置操作）' }}</div>
        <div v-if="row.subject" class="hint">{{ row.subject }}</div>
        <div v-if="row.context?.source === 'legacy'" class="hint">历史记录 · 资料按当前关联显示</div>
      </template></el-table-column>
      <el-table-column label="触发原因 / 业务内容" min-width="260"><template #default="{row}">
        <div class="subject">{{ row.context?.reason || '历史记录未保存业务详情' }}</div>
        <div v-if="row.context?.details?.['问题标题']" class="hint">问题 #{{ row.context.details['问题编号'] }}：{{ row.context.details['问题标题'] }}</div>
        <div v-if="row.context?.details?.['任务']" class="hint">{{ row.context.details['任务'] }} · 第 {{ row.context.details['周期'] }} 周期</div>
        <div v-if="row.context?.details && kind === 'logs'" class="hint">展开左侧箭头查看业务详情与处理历史</div>
      </template></el-table-column>
      <el-table-column :label="kind === 'logs' ? '最新状态' : '状态'" width="138"><template #default="{row}"><el-tag :type="tagType(row.state || row.status)" effect="plain">{{ label(row.state || row.status) }}</el-tag></template></el-table-column>
      <el-table-column v-if="kind !== 'queue'" label="说明" min-width="240"><template #default="{row}">{{ row.reason || row.detail || '—' }}</template></el-table-column>
      <el-table-column v-if="kind === 'logs'" label="本步执行人" min-width="170"><template #default="{row}">{{ row.context?.operator ? person(row.context.operator) : row.actor_id ? `用户 ID ${row.actor_id}` : '系统' }}</template></el-table-column>
      <el-table-column label="记录时间" width="175"><template #default="{row}">{{ date(row.failed_at || row.created_at) }}</template></el-table-column>
      <el-table-column v-if="kind === 'failed'" label="操作" width="240" fixed="right"><template #default="{row}"><div class="row-actions"><el-button size="small" @click="detail(row)">查看内容</el-button><el-button size="small" type="primary" plain :disabled="['PASSWORD_CODE','APP_REGISTRATION_CODE'].includes(row.event_key) || row.status === 'EXPIRED'" @click="batch('retry',[row])">重试</el-button><el-dropdown trigger="click" @command="a => batch(a,[row])"><el-button size="small">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="ignore">忽略</el-dropdown-item><el-dropdown-item command="delete">永久删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="loadRows" style="margin-top:18px;justify-content:flex-end" />
  </section>
  <el-dialog v-model="detailVisible" title="失败邮件内容快照" width="850px" append-to-body modal-class="email-modal" class="email-dialog" destroy-on-close>
    <p class="hint">{{ detailData.recipient }} · {{ detailData.subject }}</p><p class="hint">{{ detailData.reason }}</p>
    <iframe :srcdoc="detailData.html" sandbox="" referrerpolicy="no-referrer" title="失败邮件预览" style="width:100%;height:550px;border:1px solid #e2e7ef" />
    <template #footer><el-button @click="detailVisible=false">关闭</el-button></template>
  </el-dialog>
</template>
<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
const props=defineProps({templates:{type:Array,default:()=>[]}})
const emit=defineEmits(['bindings-change','hooks-loaded'])
const hooks=ref([]),loading=ref(false),saving=ref(''),status=ref({}),kind=ref('queue'),rows=ref([]),page=ref(1),total=ref(0),listLoading=ref(false),selected=ref([]),acting=ref(false),activating=ref(false),detailVisible=ref(false),detailData=ref({})
const history=ref({}),expanded=ref([])
let timer,generation=0,alive=true
async function loadBindings(){loading.value=true;try{const {data}=await request.get('/api/email/bindings');hooks.value=data.map(h=>({...h,selectedId:h.templateId==null||h.compatible===false?'':String(h.templateId),introduction:h.defaults?.associationIntro||''}));emit('bindings-change',hooks.value.filter(h=>h.templateId).map(h=>String(h.templateId)));emit('hooks-loaded',data)}finally{loading.value=false}}
function compatible(h){const expected=new Set(h.variables);return props.templates.filter(t=>{const actual=new Set(t.variables||[]);return actual.size===expected.size&&[...actual].every(v=>expected.has(v))})}
function selectedDisabled(h){return props.templates.some(t=>String(t.id)===h.selectedId&&!t.enabled)}
async function saveBinding(h){saving.value=h.eventKey;try{await request.put('/api/email/bindings/'+h.eventKey,{templateId:h.selectedId||null,defaults:h.eventKey==='ACCOUNT_REGISTERED'?{associationIntro:h.introduction}:{}});ElMessage.success('绑定已保存');await loadBindings()}finally{saving.value=''}}
async function unbind(h){try{await ElMessageBox.confirm('解绑后不再生成此事件邮件，已排队邮件不受影响。','解绑模板',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'});h.selectedId='';await saveBinding(h)}catch(e){if(e!=='cancel'&&e!=='close')throw e}}
const rowKey=row=>String(row.message_id||('event-'+row.id))
async function loadRows(){const g=++generation;listLoading.value=true;try{const {data}=await request.get('/api/email/delivery/'+(kind.value==='logs'?'messages':kind.value),{params:{page:page.value,size:20}});if(g!==generation)return;const keys=new Set(expanded.value.map(rowKey));rows.value=data.records;total.value=data.total;selected.value=[];expanded.value=rows.value.filter(r=>keys.has(rowKey(r)));history.value=Object.fromEntries(rows.value.filter(r=>history.value[r.id]).map(r=>[r.id,history.value[r.id]]));for(const row of expanded.value)if(!history.value[row.id])loadHistory(row)}finally{if(g===generation)listLoading.value=false}}
async function loadHistory(row,p=1){const g=generation;history.value[row.id]={...(history.value[row.id]||{}),loading:true};try{const {data}=await request.get('/api/email/delivery/logs/'+row.id+'/history',{params:{page:p,size:20}});if(g!==generation||!alive)return;history.value[row.id]={...data,page:p,loading:false}}finally{if(history.value[row.id])history.value[row.id].loading=false}}
function expandHistory(row,items){expanded.value=items;if(items.some(i=>i.id===row.id))loadHistory(row)}
async function refresh(){const {data}=await request.get('/api/email/delivery/status');status.value=data;await loadRows()}
function changeTab(){page.value=1;selected.value=[];expanded.value=[];history.value={};loadRows()}
async function activate(){activating.value=true;try{await request.post('/api/email/delivery/activate');ElMessage.success('已唤醒；暂停状态需先开启全局发送开关');await refresh()}finally{activating.value=false}}
async function detail(row){const {data}=await request.get('/api/email/failed/'+row.id);detailData.value=data;detailVisible.value=true}
async function batch(action,items=selected.value){const ids=items.map(r=>r.id);if(!ids.length||acting.value)return;try{await ElMessageBox.confirm(action==='delete'?'永久删除失败记录及正文，不可恢复；操作日志保留。':action==='retry'?'重新加入队列。结果未知的邮件可能重复，请先核实。':'标记为已忽略，保留记录且不自动重试。','确认操作',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'});acting.value=true;await request.post('/api/email/failed/actions/'+action,{ids});ElMessage.success('操作完成');await refresh()}catch(e){if(e!=='cancel'&&e!=='close')throw e}finally{acting.value=false}}
const labels={PENDING:'等待发送',SENDING:'发送中',SUCCESS:'SMTP 已接受',FAILED:'发送失败',EXPIRED:'已过期',UNKNOWN:'结果未知',IGNORED:'已忽略',QUEUED:'已入队',SKIPPED:'未生成邮件',BOUND:'已绑定',UNBOUND:'已解绑',RETRY:'重新入队',IGNORE:'已忽略',DELETE:'已删除',PASSWORD_RESET:'密码已重置'}
const label=s=>s==='QUEUED'||s==='RETRY'?'等待发送':labels[s]||s
const eventLabel=s=>s==='SENDING'?'开始发送':labels[s]||s
const tagType=s=>s==='SUCCESS'?'success':['FAILED','EXPIRED','UNKNOWN'].includes(s)?'danger':['SENDING','PENDING','QUEUED'].includes(s)?'warning':'info'
const date=v=>v?new Date(Number(v)).toLocaleString('zh-CN',{hour12:false}):'—'
const person=p=>`${p.name || '姓名未记录'} · ${p.account || '账号未记录'}${p.id ? `（ID ${p.id}）` : ''}`
onMounted(async()=>{await Promise.all([loadBindings(),refresh()]);if(alive)timer=setInterval(()=>{if(!document.hidden&&!acting.value&&selected.value.length===0)refresh().catch(()=>{})},15000)})
onUnmounted(()=>{alive=false;clearInterval(timer);generation++})
</script>
<style scoped>
.business-context{margin-bottom:24px;padding:16px;background:var(--zen-card);border:1px solid var(--zen-border-light);border-radius:10px;overflow-wrap:anywhere}.business-context p{margin:8px 0;line-height:1.7}.business-context dl{display:grid;grid-template-columns:110px 1fr;gap:8px;font-size:13px}.business-context dt{color:var(--zen-text-secondary)}.business-context dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.event-person{font-size:13px;margin-top:6px}
.mail-history{padding:20px 28px;background:var(--zen-bg)}.mail-history .el-timeline{margin:20px 0 0;padding-left:10px}.history-detail{margin-left:12px;color:var(--zen-text-secondary);overflow-wrap:anywhere}
.mail-ops{padding:24px;margin-bottom:24px;border:1px solid #e6eaf2;border-radius:16px;background:var(--zen-card)}.ops-title{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}.ops-title h3{margin:0 0 6px;font-size:17px}.ops-title p,.hint{font-size:12px;line-height:1.7;color:var(--zen-text-secondary);margin:6px 0}.hook-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.hook-card{padding:20px;border:1px solid #e7ecf4;background:var(--zen-bg);border-radius:12px}.hook-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px}.hook-variables{display:flex;gap:5px;flex-wrap:wrap;margin:14px 0}code{font-size:11px;color:var(--zen-text-muted)}.ops-actions,.row-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.hook-card .ops-actions{margin-top:16px}.row-actions{flex-wrap:nowrap}.ops-actions :deep(.el-button),.row-actions :deep(.el-button){margin-left:0!important;border-radius:8px!important}.queue-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:16px}.queue-summary>div{padding:18px;background:var(--zen-bg);border-radius:12px}.queue-summary b{display:block;font-size:24px;margin-bottom:6px}.queue-summary span{font-size:12px;color:var(--zen-text-secondary)}.batch{margin:0 0 16px}.batch>span{font-size:13px;color:var(--zen-text-muted);margin-right:8px}.subject{font-size:14px;line-height:1.6;overflow-wrap:anywhere}@media(max-width:850px){.hook-grid{grid-template-columns:1fr}.ops-title{align-items:flex-start;flex-direction:column}.queue-summary b{font-size:19px}}
</style>
