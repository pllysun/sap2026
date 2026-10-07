<template>
  <div ref="root" class="oj-select" :class="{'oj-select--compact':compact}" @keydown.esc="escape" @keydown.down.prevent="move(1)" @keydown.up.prevent="move(-1)" @keydown.home.prevent="focus(0)" @keydown.end.prevent="focus(options.length-1)">
    <button ref="trigger" type="button" class="oj-select-trigger" :disabled="disabled" :aria-label="label" aria-haspopup="listbox" :aria-expanded="open" :aria-controls="listId" @click="toggle">
      <span v-if="selected?.badge" class="oj-language-badge">{{ selected.badge }}</span><span class="oj-select-value"><strong>{{ selected?.label || '请选择' }}</strong><small v-if="selected?.detail">{{ selected.detail }}</small></span><svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m6 9 6 6 6-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
    </button>
    <div v-if="open" :id="listId" class="oj-select-menu" :style="menuStyle" role="listbox" :aria-label="label">
      <button v-for="(option,index) in options" :key="option.value" type="button" role="option" :aria-selected="option.value===modelValue" :class="{selected:option.value===modelValue}" @click="choose(option.value)" @keydown.enter.prevent="choose(option.value)" @keydown.space.prevent="choose(option.value)" @focus="cursor=index"><span v-if="option.badge" class="oj-language-badge">{{ option.badge }}</span><span class="oj-select-value"><strong>{{ option.label }}</strong><small v-if="option.detail">{{ option.detail }}</small></span><span v-if="option.value===modelValue" class="oj-select-check" aria-hidden="true">✓</span></button>
    </div>
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, useId } from 'vue'
const props=defineProps({modelValue:String,options:{type:Array,default:()=>[]},label:String,disabled:Boolean,compact:Boolean}),emit=defineEmits(['update:modelValue','change'])
const root=ref(null),trigger=ref(null),open=ref(false),cursor=ref(0),listId=useId(),selected=computed(()=>props.options.find(o=>o.value===props.modelValue)),menuStyle=ref({})
function fitMenu(){const rect=trigger.value?.getBoundingClientRect();if(!rect)return;const bounds=root.value?.closest('dialog[open]')?.querySelector('.oj-dialog-body')?.getBoundingClientRect();const below=(bounds?.bottom ?? window.innerHeight)-rect.bottom-16,above=rect.top-(bounds?.top ?? 0)-16,up=below<180&&above>below;menuStyle.value={top:up?'auto':'calc(100% + 7px)',bottom:up?'calc(100% + 7px)':'auto',maxHeight:Math.min(340,Math.max(72,up?above:below))+'px'}}
function escape(event){if(open.value){event.preventDefault();event.stopPropagation();close()}}
function close(){open.value=false;trigger.value?.focus()}
async function focus(index){if(props.disabled)return;fitMenu();open.value=true;cursor.value=Math.max(0,Math.min(props.options.length-1,index));await nextTick();root.value?.querySelectorAll('[role=option]')[cursor.value]?.focus()}
function toggle(){if(open.value)close();else focus(Math.max(0,props.options.findIndex(o=>o.value===props.modelValue)))}
function move(direction){focus(open.value?cursor.value+direction:Math.max(0,props.options.findIndex(o=>o.value===props.modelValue)))}
function choose(value){emit('update:modelValue',value);emit('change',value);close()}
function outside(e){if(!root.value?.contains(e.target))open.value=false}
function focusOutside(e){if(!root.value?.contains(e.target))open.value=false}
onMounted(()=>{document.addEventListener('pointerdown',outside);document.addEventListener('focusin',focusOutside);window.addEventListener('resize',fitMenu)})
onBeforeUnmount(()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('focusin',focusOutside);window.removeEventListener('resize',fitMenu)})
</script>
<style scoped>
.oj-select{position:relative;min-width:0}.oj-select-trigger{width:100%;min-height:42px;display:flex;gap:10px;align-items:center;text-align:left;border:1px solid #dce6f2;background:#fff;border-radius:10px;padding:8px 12px;color:#365373;cursor:pointer;font:inherit}.oj-select-trigger:hover{border-color:#8ab4e5;background:#fafcff}.oj-select-trigger:focus-visible,.oj-select-menu button:focus-visible{outline:2px solid #3483db;outline-offset:2px}.oj-select-trigger:disabled{opacity:.6;cursor:wait}.oj-select-trigger>svg{margin-left:auto;flex:none}.oj-select-value{display:flex;flex-direction:column;gap:2px;min-width:0}.oj-select-value strong{font-size:12px;font-weight:600}.oj-select-value small{color:#8697ad;font-size:10px}.oj-language-badge{font:600 11px ui-monospace,monospace;background:#eef4fd;color:#2d70bc;min-width:30px;padding:7px 4px;border-radius:7px;text-align:center;flex:none}.oj-select-menu{position:absolute;z-index:40;left:0;top:calc(100% + 7px);width:max-content;min-width:100%;max-width:min(320px,calc(100vw - 48px));max-height:min(340px,60dvh);overflow-y:auto;overscroll-behavior:contain;padding:6px;border:1px solid #dce6f2;border-radius:12px;background:#fff;box-shadow:0 12px 40px #2a4c7824}.oj-select-menu button{display:flex;gap:10px;align-items:center;border:0;border-radius:8px;background:none;width:100%;padding:10px;text-align:left;color:#365373;cursor:pointer}.oj-select-menu button:hover,.oj-select-menu button:focus{background:#f4f7fc}.oj-select-menu button.selected{background:#edf5ff}.oj-select-check{color:#2d79cf;margin-left:auto;padding-left:12px}

.oj-select--compact .oj-select-trigger{min-height:34px;padding:5px 8px;gap:7px;border-radius:7px}
.oj-select--compact .oj-select-trigger .oj-select-value{gap:1px;line-height:1.3}
.oj-select--compact .oj-select-trigger .oj-language-badge{min-width:23px;padding:5px 3px;border-radius:5px;font-size:9px}
.oj-select--compact .oj-select-trigger strong{font-size:11px}
.oj-select--compact .oj-select-trigger small{font-size:9px}
.oj-select--compact .oj-select-trigger>svg{width:13px;height:13px}
</style>
