<template>
  <el-dialog :model-value="open" @update:model-value="$emit('update:open',$event)" align-center width="min(1020px,95vw)" class="oj-solution-editor" :before-close="beforeClose" destroy-on-close>
    <template #header><div class="solution-editor-heading"><OjIcon name="library" :size="19" /><div><h3>题解管理</h3><p>{{ doc?.title || '正在加载' }} <span v-if="doc">· 题目版本 {{ doc.problemRevision }}</span></p></div></div></template>
    <div v-if="loading" class="solution-editor-loading">正在加载题解…</div>
    <p v-else-if="error" role="alert">{{ error }} <OjActionButton icon="refresh" @click="load">重试</OjActionButton></p>
    <template v-else-if="doc">
      <div class="solution-editor-toolbar"><el-radio-group v-model="view" size="small"><el-radio-button value="edit">编辑</el-radio-button><el-radio-button value="preview">预览</el-radio-button></el-radio-group><OjActionButton icon="upload" @click="fileInput.click()">导入题解 JSON</OjActionButton><OjActionButton icon="download" @click="exportDocument">导出</OjActionButton><input ref="fileInput" type="file" accept=".json,application/json" hidden @change="importDocument" /><span>比赛题解在结束后自动开放</span></div>
      <p class="solution-editor-note">统一八节结构；每种模式包含五语言代码。代码须与已验证参考实现保持相同逻辑，可以添加注释和调整排版。</p>
      <el-input v-if="view==='edit'" v-model="doc.summary" maxlength="300" show-word-limit placeholder="用一句话说明实际采用的算法" />
      <p v-else class="solution-editor-summary">{{ doc.summary }}</p>
      <el-tabs v-model="sectionKey" class="solution-editor-sections">
        <el-tab-pane v-for="section in doc.sections" :key="section.key" :name="section.key" :label="section.title">
          <template v-if="view==='edit'"><label class="solution-editor-label" :for="'solution-markdown-'+section.key">{{ section.title }} · Markdown 正文</label><el-input :id="'solution-markdown-'+section.key" v-model="section.markdown" type="textarea" :rows="12" maxlength="18000" />
            <el-collapse class="solution-editor-figure-options"><el-collapse-item title="可选 SVG 图解" name="diagram"><el-input :model-value="section.diagram?.caption || ''" @update:model-value="setDiagram(section,'caption',$event)" placeholder="图解说明" maxlength="500" /><el-input :model-value="section.diagram?.svg || ''" @update:model-value="setDiagram(section,'svg',$event)" type="textarea" :rows="5" maxlength="100000" placeholder="安全 SVG，需设置 viewBox；不允许脚本、外部资源或位图" /></el-collapse-item></el-collapse>
          </template>
          <div v-else class="solution-editor-preview"><div v-html="markdown(section.markdown)" /><figure v-if="section.diagram?.svg"><div v-html="svg(section.diagram.svg)" /><figcaption>{{ section.diagram.caption }}</figcaption></figure></div>
        </el-tab-pane>
      </el-tabs>
      <div class="solution-editor-code-heading"><h4><OjIcon name="code" :size="15" /> 通关代码</h4><el-radio-group v-if="modes.length>1" v-model="mode" size="small"><el-radio-button v-for="m in modes" :key="m" :value="m">{{ modeNames[m] }}</el-radio-button></el-radio-group><span v-else>{{ modeNames[mode] }}</span></div>
      <div class="solution-editor-code-panel"><div class="solution-editor-languages" role="tablist" aria-label="题解代码语言"><button v-for="(l,index) in languages" :id="'solution-admin-tab-'+l" :key="l" role="tab" :tabindex="language===l?0:-1" :aria-selected="language===l" aria-controls="solution-admin-code" :class="{active:language===l}" @click="language=l" @keydown="moveLanguage($event,index)">{{ languageNames[l] }}</button></div><div id="solution-admin-code" role="tabpanel" :aria-labelledby="'solution-admin-tab-'+language"><OjCodeEditor v-model="code" :language="language" :read-only="view==='preview'" label="题解通关代码" /></div></div>
    </template>
    <template #footer><span v-if="dirty" class="solution-editor-dirty">有未保存的修改</span><OjActionButton icon="close" @click="close">关闭</OjActionButton><OjActionButton icon="save" type="primary" :disabled="loading || !doc" :loading="saving" @click="save">保存题解</OjActionButton></template>
  </el-dialog>
</template>
<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import request from '@/utils/request'
import { languageNames, modeNames } from '@/utils/ojDictionaries'
import OjIcon from './OjIcon.vue'
import OjActionButton from './OjActionButton.vue'
import OjCodeEditor from './OjCodeEditor.vue'
const props=defineProps({open:Boolean,problemId:[String,Number]})
const emit=defineEmits(['update:open'])
const keys=['understand','approach','walkthrough','steps','correctness','complexity','pitfalls','review'],titles=['读懂题目','解法思路','样例推演','实现步骤','为什么正确','复杂度','边界与常见错误','复盘练习'],languages=['c','cpp','java','python','rust']
const doc=ref(null),loading=ref(false),saving=ref(false),error=ref(''),view=ref('edit'),sectionKey=ref('understand'),mode=ref('STDIO'),language=ref('cpp'),fileInput=ref(null),baseline=ref('')
let generation=0,disposed=false,controller
const modes=computed(()=>Object.keys(doc.value?.codes||{})),dirty=computed(()=>doc.value&&JSON.stringify(doc.value)!==baseline.value)
const code=computed({get:()=>doc.value?.codes[mode.value]?.[language.value]||'',set:value=>{if(doc.value?.codes[mode.value])doc.value.codes[mode.value][language.value]=value}})
watch(()=>[props.open,props.problemId],([open])=>{if(open)load();else{++generation;controller?.abort();loading.value=false}}, {immediate:true})
async function load(){
  const current=++generation;loading.value=true;error.value='';doc.value=null;controller?.abort();controller=new AbortController()
  try{
    const [editorial,problem]=await Promise.all([request.get(`/api/admin/oj/problems/${props.problemId}/solution`,{signal:controller.signal}),request.get(`/api/admin/oj/problems/${props.problemId}`,{signal:controller.signal})])
    if(disposed||current!==generation)return
    if(editorial.data.state==='READY')doc.value=editorial.data.document
    else{const p=problem.data.pack;doc.value={schemaVersion:1,problemSlug:p.slug,problemRevision:problem.data.revision,title:p.title,summary:'',sections:keys.map((key,i)=>({key,title:titles[i],markdown:''})),codes:Object.fromEntries(p.modes.map(m=>[m,Object.fromEntries(languages.map(l=>[l,p.references[l][m]]))]))}}
    baseline.value=JSON.stringify(doc.value);mode.value=modes.value[0];view.value='edit';sectionKey.value='understand'
  }catch(e){if(current===generation&&e.code!=='ERR_CANCELED')error.value=e.message||'加载失败'}finally{if(current===generation)loading.value=false}
}
function setDiagram(section,key,value){section.diagram||={caption:'',svg:''};section.diagram[key]=value;if(!section.diagram.caption&&!section.diagram.svg)delete section.diagram}
async function importDocument(event){const file=event.target.files[0];event.target.value='';if(!file)return;try{if(file.size>1500000)throw new Error('题解文件过大');const value=JSON.parse(await file.text());if(!props.open||!doc.value)return;if(value.problemSlug!==doc.value.problemSlug||String(value.problemRevision)!==String(doc.value.problemRevision)||value.title!==doc.value.title)throw new Error('题解文件的题目或版本不匹配');if(value.schemaVersion!==1||value.sections?.length!==8||value.sections.some((s,i)=>s.key!==keys[i]||s.title!==titles[i]||typeof s.markdown!=='string'))throw new Error('题解必须采用统一八节结构');if(!value.codes||JSON.stringify(Object.keys(value.codes).sort())!==JSON.stringify(modes.value.slice().sort())||Object.values(value.codes).some(group=>JSON.stringify(Object.keys(group).sort())!==JSON.stringify(languages.slice().sort())||Object.values(group).some(c=>typeof c!=='string')))throw new Error('每种模式须包含完整五语言代码');if(dirty.value&&!window.confirm('导入会替换尚未保存的题解，是否继续？'))return;doc.value=value;mode.value=modes.value[0];ElMessage.success('题解已载入，保存后生效')}catch(e){ElMessage.error(e.message)}}
function exportDocument(){const blob=new Blob([JSON.stringify(doc.value,null,2)+'\n'],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=doc.value.problemSlug+'-solution.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
async function save(){saving.value=true;try{const snapshot=JSON.stringify(doc.value);await request.put(`/api/admin/oj/problems/${props.problemId}/solution`,JSON.parse(snapshot));baseline.value=snapshot;ElMessage.success('题解已保存')}catch(e){ElMessage.error(e.message)}finally{saving.value=false}}
function beforeClose(done){if(saving.value)return;if(dirty.value&&!window.confirm('有尚未保存的题解修改，是否关闭？'))return;done()}
function close(){beforeClose(()=>emit('update:open',false))}
function markdown(text){return DOMPurify.sanitize(marked.parse(text||'',{gfm:true}),{ALLOWED_TAGS:['p','br','ul','ol','li','strong','em','code','pre','table','thead','tbody','tr','th','td','blockquote','h3','h4','h5','hr'],ALLOWED_ATTR:[]})}
function svg(text){return DOMPurify.sanitize(text,{ALLOWED_TAGS:['svg','g','rect','circle','ellipse','line','polyline','polygon','path','text','tspan','defs','marker','title','desc'],ALLOWED_ATTR:['viewBox','width','height','x','y','x1','y1','x2','y2','cx','cy','r','rx','ry','points','d','fill','stroke','stroke-width','stroke-linecap','stroke-linejoin','stroke-dasharray','opacity','fill-opacity','stroke-opacity','font-size','font-family','font-weight','text-anchor','dominant-baseline','transform','id','xmlns','role','aria-label','dx','dy'],FORBID_TAGS:['style','script','foreignObject','image','use','a','animate','set']}).replace(/\s(?:fill|stroke)="[^"]*(?:url\(|javascript:|data:)[^"]*"/gi,'')}
function moveLanguage(event,index){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();language.value=languages[event.key==='Home'?0:event.key==='End'?4:(index+(event.key==='ArrowRight'?1:-1)+5)%5];nextTick(()=>document.getElementById('solution-admin-tab-'+language.value)?.focus())}
onBeforeUnmount(()=>{disposed=true;++generation;controller?.abort()})
</script>
<style>
html body .el-dialog.oj-solution-editor{box-sizing:border-box;max-height:92dvh!important;overflow:hidden!important}
html body .el-dialog.oj-solution-editor .el-dialog__header,html body .el-dialog.oj-solution-editor .el-dialog__footer{flex:0 0 auto}
html body .el-dialog.oj-solution-editor .el-dialog__body{flex:1 1 0;min-height:0;overflow-y:auto!important;overflow-x:hidden!important;padding:14px 0 16px!important}
.oj-solution-editor{display:flex;flex-direction:column;height:min(850px,92dvh);padding:22px;border-radius:14px}.oj-solution-editor .el-dialog__body{flex:1;min-height:0;overflow:auto}.oj-solution-editor .el-dialog__footer{display:flex;align-items:center;justify-content:flex-end;gap:10px}.solution-editor-heading{display:flex;align-items:center;gap:12px;color:#315272}.solution-editor-heading h3{margin:0;font-size:16px}.solution-editor-heading p{margin:7px 0 0;font-size:12px;color:#7890a9}.solution-editor-toolbar{display:flex;align-items:center;gap:9px;flex-wrap:wrap}.solution-editor-toolbar>span{margin-left:auto;font-size:11px;color:#758ba5}.solution-editor-note{font-size:12px;line-height:1.8;color:#698098;background:#f2f6fc;padding:10px 14px;border-radius:8px}.solution-editor-label{display:block;margin:3px 0 10px;color:#526c8b;font-size:12px}.solution-editor-sections{margin-top:18px}.solution-editor-sections .el-tabs__item{font-size:12px}.solution-editor-figure-options{margin-top:10px}.solution-editor-figure-options .el-textarea{margin-top:10px}.solution-editor-code-heading{display:flex;align-items:center;gap:15px;margin-top:20px}.solution-editor-code-heading h4{margin:0;font-size:13px;color:#3c5a7a;display:flex;align-items:center;gap:7px}.solution-editor-code-heading>span{font-size:11px;color:#7e95b0}.solution-editor-code-panel{margin-top:12px;border:1px solid #e0e9f5;border-radius:9px;overflow:hidden}.solution-editor-languages{display:flex;gap:4px;padding:7px;background:#eef4fc}.solution-editor-languages button{padding:7px 14px;border:0;border-radius:6px;background:transparent;font-size:12px;color:#66819f;cursor:pointer}.solution-editor-languages button.active{background:#fff;color:#286cc1}.solution-editor-languages button:focus-visible{outline:2px solid #568bcb}.solution-editor-code-panel .oj-code-editor{height:320px;resize:none}.solution-editor-preview{min-height:250px;font-size:14px;line-height:1.9;color:#3c526c;overflow-wrap:anywhere}.solution-editor-preview table{border-collapse:collapse;display:block;overflow:auto}.solution-editor-preview td,.solution-editor-preview th{border:1px solid #dde6f3;padding:6px 10px}.solution-editor-preview pre{overflow:auto;background:#f2f6fc;padding:12px}.solution-editor-preview figure{margin:18px 0}.solution-editor-preview svg{width:100%;height:auto;max-height:400px}.solution-editor-preview figcaption{font-size:12px;color:#7d90a9}.solution-editor-loading{padding:30px;color:#7288a5}.solution-editor-dirty{margin-right:auto;font-size:12px;color:#947446}.solution-editor-summary{font-size:14px;line-height:1.8;color:#496181}@media(max-width:650px){.oj-solution-editor{padding:14px}.solution-editor-toolbar>span{width:100%;margin:0}.solution-editor-languages button{padding:7px 11px}}
</style>
