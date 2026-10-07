<template>
  <span :class="['oj-status-badge',`oj-status--${item.tone}`]" :title="item.help" :aria-label="`${item.label} ${status}`">
    <span class="oj-status-emblem" aria-hidden="true"><UiIcon :name="item.icon || 'info'" :size="11" /></span>
    <span class="oj-status-label">{{ item.label }}</span>
    <small v-if="showCode" class="oj-status-code" aria-hidden="true">{{ status }}</small>
  </span>
</template>
<script setup>
import { computed } from 'vue'
import UiIcon from './UiIcon.vue'
import { ojStatuses } from '@/utils/ojDictionaries'
const props=defineProps({status:{type:String,default:''},showCode:{type:Boolean,default:true}})
const item=computed(()=>ojStatuses[props.status]||{label:'未知状态',tone:'neutral',help:props.status})
</script>
<style scoped>
.oj-status-badge{--status-ink:#667c98;--status-wash:#f0f4f9;--status-line:#dfe7f1;display:inline-flex;align-items:center;gap:6px;padding:4px 7px 4px 4px;border:1px solid var(--status-line);border-radius:6px;white-space:nowrap;color:var(--status-ink);background:linear-gradient(110deg,var(--status-wash),#fff);box-shadow:inset 0 1px #ffffffb3;font-family:inherit;font-size:11px;line-height:1.4;font-weight:550;vertical-align:middle}
.oj-status-emblem{display:grid;place-items:center;width:17px;height:17px;flex:none;border-radius:4px;color:#fff;background:var(--status-ink);box-shadow:0 1px 2px #19395212}
.oj-status-label{color:var(--status-ink)}
.oj-status-badge>.oj-status-code{font:600 9px ui-monospace,SFMono-Regular,Consolas,monospace;color:var(--status-ink);opacity:.8;letter-spacing:.2px;margin:0 0 0 1px;padding-left:6px;border-left:1px solid var(--status-line)}
.oj-status--success{--status-ink:#24836e;--status-wash:#f3f6fb;--status-line:#d9e4f2}
.oj-status--danger{--status-ink:#b95765;--status-wash:#fff0f2;--status-line:#f0d7dd}
.oj-status--warning{--status-ink:#ac7731;--status-wash:#fff7e9;--status-line:#f0e1c5}
.oj-status--waiting{--status-ink:#7186a3;--status-wash:#eff3f9;--status-line:#dfe7f2}
.oj-status--working{--status-ink:#357ac4;--status-wash:#edf5ff;--status-line:#d3e3f7}
.oj-status--working .ui-icon{animation:oj-status-spin 1.6s linear infinite}
.oj-status--waiting .oj-status-emblem{animation:oj-status-breathe 1.8s ease-in-out infinite}
@keyframes oj-status-spin{to{transform:rotate(360deg)}}
@keyframes oj-status-breathe{50%{opacity:.55}}
@media(prefers-reduced-motion:reduce){.oj-status-badge *{animation:none!important}}
</style>
