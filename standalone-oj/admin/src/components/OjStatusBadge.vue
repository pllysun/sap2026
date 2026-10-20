<template><span :class="['oj-status-badge',`oj-status--${item.tone}`]" :title="item.help" :aria-label="`${item.label} ${status}`"><i aria-hidden="true" /><span>{{ item.label }}</span><small v-if="showCode">{{ status }}</small></span></template>
<script setup>
import { computed } from 'vue'
import { ojStatuses } from '@/utils/ojDictionaries'
const props=defineProps({status:{type:String,default:''},showCode:{type:Boolean,default:true}})
const item=computed(()=>ojStatuses[props.status]||{label:'未知状态',tone:'neutral',help:props.status})
</script>
<style scoped>
.oj-status-badge{display:inline-flex;align-items:center;gap:6px;border:1px solid transparent;padding:5px 9px;border-radius:7px;white-space:nowrap;font-family:inherit;font-size:11px;line-height:1.35;font-weight:500;vertical-align:middle}.oj-status-badge>i{width:5px;height:5px;border-radius:50%;background:currentColor;flex:none}.oj-status-badge>small{font:500 9px ui-monospace,monospace;opacity:.55;margin-left:2px}.oj-status--success{color:#329472;background:#edf8f2;border-color:#e2f1e8}.oj-status--danger{color:#c16a6a;background:#fff1f1;border-color:#f9e4e4}.oj-status--warning{color:#b58a44;background:#fff8e9;border-color:#f6ebd3}.oj-status--neutral{color:#8a9bb1;background:#f3f6fa;border-color:#eaf0f6}.oj-status--waiting{color:#8699b5;background:#f0f4fa;border-color:#e5edf6}.oj-status--working{color:#4989cc;background:#ecf5ff;border-color:#dceafa}.oj-status--working>i{width:9px;height:9px;border:1.5px solid #b7d5f3;border-top-color:#4989cc;background:none;animation:oj-status-spin 1s linear infinite}.oj-status--waiting>i{animation:oj-status-pulse 1.7s ease-in-out infinite}@keyframes oj-status-spin{to{transform:rotate(360deg)}}@keyframes oj-status-pulse{50%{opacity:.3;box-shadow:0 0 0 3px #8699b512}}@media(prefers-reduced-motion:reduce){.oj-status-badge>i{animation:none!important}}
</style>
