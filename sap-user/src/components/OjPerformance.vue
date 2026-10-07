<template>
  <section class="oj-performance-view" aria-label="时间和空间表现">
    <div class="oj-performance-context"><span><UiIcon name="chart" :size="13" />{{ data.comparable ? '正式 AC 提交分布' : '本次运行表现' }}</span><small v-if="data.comparable">{{ data.sampleCount }} 次可比较的 AC 提交</small></div>
    <div class="oj-performance-cards">
      <article v-for="metric in metrics" :key="metric.key" class="oj-performance-card" :class="metric.key">
        <header><span class="oj-metric-symbol"><UiIcon :name="metric.icon" :size="14" /></span><h3>{{ metric.label }}</h3></header>
        <div class="oj-performance-value">{{ format(data[metric.key]?.value, metric.key) }}<small>{{ metric.unit }}</small></div>
        <p class="oj-performance-comparison" v-if="data.comparable && data.sampleCount > 1">优于 <strong>{{ data[metric.key]?.beatsPercent ?? '—' }}%</strong> 的可比较提交</p>
        <p class="oj-performance-comparison" v-else>{{ data.comparable ? '当前仅有 1 次提交，暂不比较' : '单个测试用例的峰值' }}</p>
        <OjMetricChart v-if="data.comparable" :metric="data[metric.key]" :kind="metric.key" :label="metric.label" />
        <div v-else class="oj-resource-meter">
          <div class="oj-resource-meter-label"><span>{{ metric.key === 'time' ? '时间限制' : '内存限制' }}</span><strong>{{ limit(metric.key) ? format(limit(metric.key), metric.key) + ' ' + metric.unit : '—' }}</strong></div>
          <div class="oj-resource-track" :class="{unavailable:!limit(metric.key)}"><i :style="{width:usage(metric.key)+'%'}" /></div>
          <p>{{ limit(metric.key) ? '使用 ' + usage(metric.key).toFixed(1) + '% 的当前配置限额' : '该记录没有可用的限制信息' }}</p>
        </div>
      </article>
    </div>
    <p class="oj-performance-footnote">{{ data.note }}</p>
  </section>
</template>
<script setup>
import UiIcon from './UiIcon.vue'
import OjMetricChart from './OjMetricChart.vue'
const props = defineProps({ data: { type: Object, required: true }, limits: Object })
const metrics = [{ key: 'time', label: '执行用时', icon: 'clock', unit: 'ms' }, { key: 'memory', label: '内存消耗', icon: 'cpu', unit: 'MiB' }]
function format(value, key) {
  if (value == null || !Number.isFinite(Number(value))) return '—'
  return key === 'memory' ? (Number(value) / 1048576).toFixed(2) : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function limit(key) { return key === 'time' ? Number(props.limits?.timeLimitMs) || 0 : (Number(props.limits?.memoryLimitMb) || 0) * 1048576 }
function usage(key) { return Math.min(100, Math.max(0, 100 * (Number(props.data[key]?.value) || 0) / (limit(key) || 1))) }
</script>
<style scoped>
.oj-performance-context{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px}.oj-performance-context>span{display:flex;align-items:center;gap:8px;font-size:13px;font-weight:600;color:#496b8b}.oj-performance-context>small{font-size:11px;color:#8ba0b5}
.oj-performance-cards{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.oj-performance-card{--metric-color:#3985cc;--metric-tint:#eaf4ff;min-width:0;border:1px solid #e0eaf5;border-radius:14px;background:linear-gradient(145deg,#fff,#f8fbff);padding:16px 17px 14px}.oj-performance-card.memory{--metric-color:#5a8f84;--metric-tint:#edf7f3;background:linear-gradient(145deg,#fff,#f8fcfa)}
.oj-performance-card header{display:flex;align-items:center;gap:10px}.oj-metric-symbol{display:grid;place-items:center;width:27px;height:27px;background:var(--metric-tint);color:var(--metric-color);border-radius:10px}.oj-performance-card h3{font-size:13px;font-weight:500;margin:0;color:#5f7993}.oj-performance-value{font:600 29px/1.3 ui-monospace,SFMono-Regular,Consolas,monospace;color:var(--metric-color);margin:20px 0 8px;letter-spacing:-1px}.oj-performance-value small{font:12px sans-serif;color:#8e9fb0;margin-left:8px;letter-spacing:0}.oj-performance-comparison{font-size:11px;color:#8b9fb3;margin:0 0 18px;line-height:1.7}.oj-performance-comparison strong{color:var(--metric-color);font-weight:600}
.oj-resource-meter{padding:20px 0 12px;margin-top:14px;border-top:1px solid #e5edf4}.oj-resource-meter-label{display:flex;align-items:center;justify-content:space-between;gap:10px;font-size:11px;color:#7f94a8}.oj-resource-meter-label strong{font:11px ui-monospace,monospace;font-weight:400;color:#718aa2}.oj-resource-track{height:9px;margin-top:15px;border-radius:10px;background:#eaf0f5;overflow:hidden}.oj-resource-track i{display:block;height:100%;background:var(--metric-color);border-radius:10px;transition:width .5s}.oj-resource-track.unavailable i{display:none}.oj-resource-meter p{font-size:11px;line-height:1.6;margin:12px 0 0;color:#8ba0b4}.oj-performance-footnote{font-size:11px;line-height:1.8;color:#8c9eb1;margin:15px 0 0}
@media(max-width:650px){.oj-performance-cards{grid-template-columns:1fr;gap:14px}.oj-performance-card{padding:19px 17px 14px}.oj-performance-value{font-size:26px}.oj-performance-context{align-items:flex-start;flex-direction:column;gap:6px}}
@media(prefers-reduced-motion:reduce){.oj-resource-track i{transition:none}}
</style>
