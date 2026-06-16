<template>
  <div class="cos-panel">
    <div class="panel-grid">
      <div class="chart-card">
        <div class="chart-title">用户流量 Top（上传 / 下载）</div>
        <div v-show="rows.length" ref="barEl" class="chart-box"></div>
        <div v-if="!rows.length" class="empty-hint">{{ loading ? '加载中…' : '暂无数据' }}</div>
      </div>
      <div class="chart-card">
        <div class="chart-title">流量趋势</div>
        <div ref="trendEl" class="chart-box"></div>
      </div>
    </div>

    <div class="table-title">按用户明细</div>
    <el-table :data="rows" stripe size="small" :default-sort="{ prop: 'totalBytes', order: 'descending' }">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="userName" label="用户" min-width="120" />
      <el-table-column label="上传量" min-width="110" sortable :sort-by="r => r.uploadBytes">
        <template #default="{ row }">{{ fmt(row.uploadBytes) }}</template>
      </el-table-column>
      <el-table-column label="下载量" min-width="110" sortable :sort-by="r => r.downloadBytes">
        <template #default="{ row }">{{ fmt(row.downloadBytes) }}</template>
      </el-table-column>
      <el-table-column label="上传次数" min-width="90" align="right">
        <template #default="{ row }">{{ row.uploadCount }}</template>
      </el-table-column>
      <el-table-column label="下载次数" min-width="90" align="right">
        <template #default="{ row }">{{ row.downloadCount }}</template>
      </el-table-column>
      <el-table-column label="合计" min-width="110" prop="totalBytes" sortable>
        <template #default="{ row }">{{ fmt(row.totalBytes) }}</template>
      </el-table-column>
      <template #empty><span style="color: var(--zen-text-muted)">暂无数据</span></template>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { getCosByUser, getCosTrend } from '../../../api'
import { formatBytes } from '../../../utils/format'

const props = defineProps({ days: { type: Number, default: 7 } })

const rows = ref([])
const loading = ref(false)
const barEl = ref(null)
const trendEl = ref(null)
let barInst = null
let trendInst = null

const fmt = (n) => formatBytes(n)

const load = async () => {
  loading.value = true
  try {
    const [u, t] = await Promise.all([getCosByUser(props.days), getCosTrend(props.days)])
    rows.value = (u.data || []).map(r => {
      const up = Number(r.uploadBytes || 0)
      const down = Number(r.downloadBytes || 0)
      return {
        userId: Number(r.userId || 0),
        userName: r.userName || '匿名',
        uploadBytes: up,
        downloadBytes: down,
        uploadCount: Number(r.uploadCount || 0),
        downloadCount: Number(r.downloadCount || 0),
        totalBytes: up + down
      }
    })
    trendData.value = t.data || { dates: [], upload: [], download: [] }
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
  await nextTick()
  renderBar()
  renderTrend()
}

const trendData = ref({ dates: [], upload: [], download: [] })

const bytesAxis = { type: 'value', axisLabel: { formatter: (v) => formatBytes(v), color: '#9298b0', fontSize: 11 }, splitLine: { lineStyle: { color: '#eef0f7' } } }

const renderBar = () => {
  if (!barEl.value || !rows.value.length) return
  if (!barInst) barInst = echarts.init(barEl.value)
  const top = rows.value.slice(0, 15)
  barInst.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (v) => formatBytes(v) },
    legend: { data: ['上传', '下载'], top: 0, textStyle: { color: '#555a72' } },
    grid: { top: 36, right: 16, bottom: 60, left: 64 },
    xAxis: { type: 'category', data: top.map(r => r.userName), axisLabel: { color: '#9298b0', fontSize: 11, interval: 0, rotate: 35 }, axisLine: { lineStyle: { color: '#dfe2ee' } } },
    yAxis: bytesAxis,
    series: [
      { name: '上传', type: 'bar', stack: 't', data: top.map(r => r.uploadBytes), itemStyle: { color: '#14B8A6', borderRadius: [0, 0, 0, 0] } },
      { name: '下载', type: 'bar', stack: 't', data: top.map(r => r.downloadBytes), itemStyle: { color: '#3B82F6', borderRadius: [4, 4, 0, 0] } }
    ]
  })
}

const renderTrend = () => {
  if (!trendEl.value) return
  if (!trendInst) trendInst = echarts.init(trendEl.value)
  const d = trendData.value
  trendInst.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (v) => formatBytes(v) },
    legend: { data: ['上传', '下载'], top: 0, textStyle: { color: '#555a72' } },
    grid: { top: 36, right: 16, bottom: 30, left: 64 },
    xAxis: { type: 'category', boundaryGap: false, data: (d.dates || []).map(x => String(x).slice(5)), axisLabel: { color: '#9298b0', fontSize: 11 }, axisLine: { lineStyle: { color: '#dfe2ee' } } },
    yAxis: bytesAxis,
    series: [
      { name: '上传', type: 'line', smooth: true, symbol: 'circle', data: (d.upload || []).map(Number), lineStyle: { color: '#14B8A6', width: 2 }, itemStyle: { color: '#14B8A6' }, areaStyle: { color: 'rgba(20,184,166,0.12)' } },
      { name: '下载', type: 'line', smooth: true, symbol: 'circle', data: (d.download || []).map(Number), lineStyle: { color: '#3B82F6', width: 2 }, itemStyle: { color: '#3B82F6' }, areaStyle: { color: 'rgba(59,130,246,0.12)' } }
    ]
  })
}

const onResize = () => { barInst?.resize(); trendInst?.resize() }

watch(() => props.days, load)
onMounted(async () => { await load(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  barInst?.dispose()
  trendInst?.dispose()
})
</script>

<style scoped>
.panel-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin: 8px 0 20px;
}
.chart-card {
  border: 1px solid var(--zen-border-light);
  border-radius: var(--zen-radius-md);
  padding: 14px 16px;
  position: relative;
}
.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--zen-text-secondary);
  margin-bottom: 8px;
}
.chart-box {
  height: 280px;
  width: 100%;
}
.empty-hint {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--zen-text-muted);
  font-size: 13px;
}
.table-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--zen-text);
  margin: 4px 0 12px;
}
@media (max-width: 900px) {
  .panel-grid { grid-template-columns: 1fr; }
}
</style>
