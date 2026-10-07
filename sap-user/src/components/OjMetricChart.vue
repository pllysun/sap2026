<template>
  <div class="oj-distribution">
    <div class="oj-distribution-legend"><span><i /> AC 提交</span><span class="current"><i /> 当前区间</span></div>
    <svg v-if="bins.length" viewBox="0 0 440 210" class="oj-distribution-svg" role="group" :aria-label="label + '的 AC 提交分布'">
      <text x="46" y="16" class="oj-chart-label">提交次数</text>
      <g v-for="tick in maximum>1?[0, maximum / 2, maximum]:[0,1]" :key="tick">
        <line x1="46" x2="422" :y1="162 - tick / maximum * 126" :y2="162 - tick / maximum * 126" class="oj-chart-grid" />
        <text x="35" :y="166 - tick / maximum * 126" text-anchor="end" class="oj-chart-label">{{ tick }}</text>
      </g>
      <g v-for="(bin, index) in bins" :key="index" tabindex="0" role="img" :aria-label="describe(bin)" :class="['oj-chart-bin',{current:bin.current,selected:active===index}]"
        @pointerenter="active=index" @pointerleave="active=null" @focus="active=index" @blur="active=null">
        <rect :x="46 + index * width" y="32" :width="width" height="130" fill="transparent" />
        <rect :x="46 + index * width + 5" :y="162 - bin.count / maximum * 126" :width="Math.max(1,width - 10)" :height="bin.count / maximum * 126" rx="4" class="oj-chart-bar" />
        <title>{{ describe(bin) }}</title>
      </g>
      <line :x1="markerX" :x2="markerX" y1="26" y2="162" class="oj-chart-marker" />
      <circle :cx="markerX" cy="26" r="4" class="oj-chart-marker-dot" />
      <text x="46" y="185" class="oj-chart-label">{{ axis(bins[0].from) }}</text>
      <text x="422" y="185" text-anchor="end" class="oj-chart-label">{{ axis(bins.at(-1).to) }}</text>
      <text x="234" y="204" text-anchor="middle" class="oj-chart-label">{{ label }}（{{ unit }}）</text>
    </svg>
    <div v-else class="oj-chart-no-data">暂无分布数据</div>
    <p class="oj-chart-readout" aria-live="polite">{{ activeBin ? describe(activeBin) : '悬停或聚焦区间查看提交数量' }}</p>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
const props = defineProps({ metric: Object, kind: String, label: String })
const active = ref(null)
const bins = computed(() => (props.metric?.bins || []).filter(b => Number.isFinite(Number(b.from)) && Number.isFinite(Number(b.to)) && Number(b.to) > Number(b.from))
  .map(b => ({ from:Number(b.from), to:Number(b.to), count:Number.isFinite(Number(b.count))?Math.max(0,Number(b.count)):0, current:Boolean(b.current) })))
const maximum = computed(() => { const max=Math.max(1,...bins.value.map(b=>b.count));return max<=1?1:Math.ceil(max/2)*2 })
const width = computed(() => 376 / (bins.value.length || 1))
const activeBin = computed(() => active.value == null ? null : bins.value[active.value])
const markerX = computed(() => {
  const from=bins.value[0]?.from||0,to=bins.value.at(-1)?.to||1,value=Number(props.metric?.value)||0
  return 46 + 376 * Math.max(0,Math.min(1,(value-from)/(to-from)))
})
const divisor = computed(() => props.kind !== 'memory' ? 1 : bins.value.at(-1)?.to - bins.value[0]?.from < 1024 ? 1 : bins.value.at(-1)?.to < 1048576 ? 1024 : 1048576)
const unit = computed(() => props.kind === 'memory' ? ({1:'B',1024:'KiB',1048576:'MiB'}[divisor.value]) : 'ms')
function axis(value) { return (value / divisor.value).toLocaleString('zh-CN',{maximumFractionDigits:2}) }
function describe(bin) { return `${axis(bin.from)}–${axis(bin.to)} ${unit.value} · ${bin.count} 次提交${bin.current ? ' · 当前所在区间' : ''}` }
watch(() => props.metric, () => { active.value=null })
</script>
<style scoped>
.oj-distribution-legend{display:flex;gap:16px;justify-content:flex-end;margin:0 0 10px;font-size:10px;color:#8ba1b4}.oj-distribution-legend>span{display:flex;align-items:center;gap:6px}.oj-distribution-legend i{width:7px;height:7px;border-radius:2px;background:#bfd6e9}.oj-distribution-legend .current i{background:var(--metric-color,#3985cc)}.oj-distribution-svg{display:block;width:100%;overflow:visible}.oj-chart-grid{stroke:#e4ebf2;stroke-width:1}.oj-chart-label{fill:#8ca0b2;font:10px sans-serif}.oj-chart-bar{fill:#c5d9e9;transition:fill .15s}.oj-chart-bin.current .oj-chart-bar,.oj-chart-bin.selected .oj-chart-bar{fill:var(--metric-color,#3985cc)}.oj-chart-bin{outline:none;cursor:help}.oj-chart-bin:focus-visible>.oj-chart-bar{stroke:#284d70;stroke-width:2}.oj-chart-marker{stroke:var(--metric-color,#3985cc);stroke-width:1.3;stroke-dasharray:3 4;opacity:.6;pointer-events:none}.oj-chart-marker-dot{fill:var(--metric-color,#3985cc);stroke:#fff;stroke-width:2;pointer-events:none}.oj-chart-readout{font-size:10px;line-height:1.6;color:#7e96ad;margin:6px 0 0;min-height:32px}.oj-chart-no-data{min-height:160px;display:grid;place-items:center;font-size:12px;color:#91a5b9}
@media(prefers-reduced-motion:reduce){.oj-chart-bar{transition:none}}
</style>
