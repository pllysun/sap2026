<template>
  <button :type="nativeType" class="oj-action" :class="[`oj-action--${type}`,{'oj-action--soft':link,'oj-action--small':size==='small','oj-action--circle':circle}]" :disabled="disabled||loading" :aria-busy="loading||undefined">
    <span v-if="loading" class="oj-action-spinner" aria-hidden="true" /><OjIcon v-else-if="icon" :name="icon" :size="size==='small'?15:17" />
    <span v-if="$slots.default" class="oj-action-label"><slot /></span>
  </button>
</template>
<script setup>
import OjIcon from './OjIcon.vue'
defineProps({icon:String,type:{type:String,default:'default'},size:String,link:Boolean,circle:Boolean,loading:Boolean,disabled:Boolean,nativeType:{type:String,default:'button'}})
</script>
<style scoped>
.oj-action{--oj-accent:#47799f;--oj-border:#d6e5f2;--oj-surface:#f8fbff;display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:36px;max-width:100%;padding:8px 13px;border:1px solid var(--oj-border);border-radius:9px;background:var(--oj-surface);color:var(--oj-accent);font-family:inherit;font-size:12px;font-weight:500;line-height:1.35;white-space:nowrap;cursor:pointer;box-shadow:0 1px 2px #25496b05;transition:background .18s,border-color .18s,box-shadow .18s,transform .18s;box-sizing:border-box;vertical-align:middle}
.oj-action:hover:not(:disabled){background:#edf5fc;border-color:#aecde8;box-shadow:0 3px 10px #315b8010;transform:translateY(-1px)}
.oj-action:active:not(:disabled){transform:translateY(0);box-shadow:none}
.oj-action:focus-visible{outline:3px solid #79ade54d;outline-offset:2px}
.oj-action:disabled{opacity:.48;cursor:not-allowed;box-shadow:none}
.oj-action--primary{--oj-accent:#fff;--oj-border:#3986c4;--oj-surface:linear-gradient(135deg,#438fd0,#3077b6);box-shadow:0 3px 9px #327eb925}
.oj-action--primary:hover:not(:disabled){background:linear-gradient(135deg,#3584c7,#256da9);border-color:#2776b5;box-shadow:0 4px 12px #327eb935}
.oj-action--success{--oj-accent:#28846a;--oj-border:#bfe1d2;--oj-surface:#eff9f4}
.oj-action--success:hover:not(:disabled){background:#e3f4eb;border-color:#92cbb5}
.oj-action--danger{--oj-accent:#b95465;--oj-border:#ecccd3;--oj-surface:#fff5f6}
.oj-action--danger:hover:not(:disabled){background:#ffe9ed;border-color:#dca4b1}
.oj-action--soft{min-height:30px;padding:6px 9px;border-radius:7px;box-shadow:none;font-size:11px;gap:5px}
.oj-action--primary.oj-action--soft{--oj-accent:#357bb4;--oj-border:#d7e8f7;--oj-surface:#eff6fd;box-shadow:none}
.oj-action--primary.oj-action--soft:hover:not(:disabled){background:#e2effb;border-color:#aacfee;box-shadow:none}
.oj-action--small{min-height:30px;padding:6px 9px;font-size:11px}
.oj-action--circle{width:32px;height:32px;min-height:32px;padding:0;flex:none;border-radius:8px}
.oj-action-label{display:inline-flex;align-items:center;gap:5px;min-width:0}
.oj-action-spinner{width:13px;height:13px;flex:none;border:1.7px solid currentColor;border-right-color:transparent;border-radius:50%;animation:oj-action-spin .7s linear infinite}
@keyframes oj-action-spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.oj-action{transition:none}.oj-action:hover:not(:disabled){transform:none}.oj-action-spinner{animation-duration:1.5s}}
</style>
