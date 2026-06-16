<template>
  <div class="api-panel">
    <div class="panel-grid">
      <div class="chart-card">
        <div class="chart-title">接口请求 Top（全部用户合计）</div>
        <div v-show="top.length" ref="barEl" class="chart-box"></div>
        <div v-if="!top.length" class="empty-hint">{{ loading ? '加载中…' : '暂无数据' }}</div>
      </div>
      <div class="chart-card">
        <div class="chart-title">请求量趋势</div>
        <div ref="trendEl" class="chart-box"></div>
      </div>
    </div>

    <!-- 下钻 -->
    <div class="drill-bar">
      <el-radio-group v-model="drillMode" size="small" @change="onDrillModeChange">
        <el-radio-button value="user">按用户看接口</el-radio-button>
        <el-radio-button value="endpoint">按接口看用户</el-radio-button>
      </el-radio-group>

      <el-select v-if="drillMode === 'user'" v-model="selUser" filterable placeholder="选择用户" size="small" style="width: 220px" @change="loadDrill">
        <el-option v-for="u in users" :key="u.userId" :label="u.userName" :value="u.userId" />
      </el-select>
      <el-select v-else v-model="selEndpoint" filterable placeholder="选择接口" size="small" style="width: 320px" @change="loadDrill">
        <el-option v-for="ep in endpoints" :key="ep" :label="ep" :value="ep" />
      </el-select>
    </div>

    <el-table v-if="drillMode === 'user'" :data="drillRows" stripe size="small">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="endpoint" label="接口" min-width="240" show-overflow-tooltip />
      <el-table-column prop="httpMethod" label="方法" width="90">
        <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.httpMethod }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="cnt" label="请求数" width="110" align="right" sortable />
      <template #empty><span style="color: var(--zen-text-muted)">{{ selUser ? '该用户暂无记录' : '请选择用户' }}</span></template>
    </el-table>

    <el-table v-else :data="drillRows" stripe size="small">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="userName" label="用户" min-width="160" />
      <el-table-column prop="cnt" label="请求数" width="110" align="right" sortable />
      <template #empty><span style="color: var(--zen-text-muted)">{{ selEndpoint ? '该接口暂无记录' : '请选择接口' }}</span></template>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { getApiTop, getApiTrend, getApiByUser, getApiByEndpoint, getStatsUsers } from '../../../api'

const props = defineProps({ days: { type: Number, default: 7 } })

const top = ref([])
const trendData = ref({ dates: [], counts: [] })
const users = ref([])
const loading = ref(false)

const drillMode = ref('user')
const selUser = ref(null)
const selEndpoint = ref(null)
const drillRows = ref([])

const endpoints = computed(() => [...new Set(top.value.map(r => r.endpoint))])

const barEl = ref(null)
const trendEl = ref(null)
let barInst = null
let trendInst = null

const load = async () => {
  loading.value = true
  try {
    const [t, tr, u] = await Promise.all([
      getApiTop({ days: props.days, limit: 20 }),
      getApiTrend(props.days),
      getStatsUsers()
    ])
    top.value = (t.data || []).map(r => ({
      endpoint: r.endpoint,
      httpMethod: r.httpMethod,
      cnt: Number(r.cnt || 0)
    }))
    trendData.value = tr.data || { dates: [], counts: [] }
    users.value = (u.data || []).map(x => ({ userId: Number(x.userId || 0), userName: x.userName || '匿名' }))
  } catch (e) {
    top.value = []
  } finally {
    loading.value = false
  }
  await nextTick()
  renderBar()
  renderTrend()
}

const renderBar = () => {
  if (!barEl.value || !top.value.length) return
  if (!barInst) barInst = echarts.init(barEl.value)
  const items = top.value.slice(0, 15).slice().reverse()
  barInst.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 10, right: 24, bottom: 24, left: 200 },
    xAxis: { type: 'value', axisLabel: { color: '#9298b0', fontSize: 11 }, splitLine: { lineStyle: { color: '#eef0f7' } } },
    yAxis: {
      type: 'category',
      data: items.map(r => `${r.httpMethod} ${r.endpoint}`),
      axisLabel: { color: '#555a72', fontSize: 11, width: 190, overflow: 'truncate' },
      axisLine: { lineStyle: { color: '#dfe2ee' } }
    },
    series: [{
      type: 'bar',
      data: items.map(r => r.cnt),
      barWidth: '60%',
      itemStyle: {
        borderRadius: [0, 4, 4, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#14B8A6' }, { offset: 1, color: '#3B82F6' }])
      }
    }]
  })
}

const renderTrend = () => {
  if (!trendEl.value) return
  if (!trendInst) trendInst = echarts.init(trendEl.value)
  const d = trendData.value
  trendInst.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 16, right: 16, bottom: 30, left: 50 },
    xAxis: { type: 'category', boundaryGap: false, data: (d.dates || []).map(x => String(x).slice(5)), axisLabel: { color: '#9298b0', fontSize: 11 }, axisLine: { lineStyle: { color: '#dfe2ee' } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#9298b0', fontSize: 11 }, splitLine: { lineStyle: { color: '#eef0f7' } } },
    series: [{
      type: 'line', smooth: true, symbol: 'circle', symbolSize: 7,
      data: (d.counts || []).map(Number),
      lineStyle: { color: '#3B82F6', width: 3 }, itemStyle: { color: '#3B82F6' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(59,130,246,0.25)' }, { offset: 1, color: 'rgba(59,130,246,0.02)' }]) }
    }]
  })
}

const onDrillModeChange = () => {
  drillRows.value = []
  loadDrill()
}

const loadDrill = async () => {
  try {
    if (drillMode.value === 'user') {
      if (!selUser.value) { drillRows.value = []; return }
      const res = await getApiByUser({ userId: selUser.value, days: props.days })
      drillRows.value = (res.data || []).map(r => ({ endpoint: r.endpoint, httpMethod: r.httpMethod, cnt: Number(r.cnt || 0) }))
    } else {
      if (!selEndpoint.value) { drillRows.value = []; return }
      const res = await getApiByEndpoint({ endpoint: selEndpoint.value, days: props.days })
      drillRows.value = (res.data || []).map(r => ({ userName: r.userName || '匿名', cnt: Number(r.cnt || 0) }))
    }
  } catch (e) {
    drillRows.value = []
  }
}

const onResize = () => { barInst?.resize(); trendInst?.resize() }

watch(() => props.days, async () => { await load(); await loadDrill() })
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
  grid-template-columns: 1.4fr 1fr;
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
  height: 320px;
  width: 100%;
}
.empty-hint {
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--zen-text-muted);
  font-size: 13px;
}
.drill-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 4px 0 14px;
  flex-wrap: wrap;
}
@media (max-width: 900px) {
  .panel-grid { grid-template-columns: 1fr; }
}
</style>
