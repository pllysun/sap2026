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

    <!-- 统一明细：用户 × 接口 × 方法 × 请求数，直接搜索/排序，无需先选用户 -->
    <div class="detail-bar">
      <span class="detail-title">请求明细</span>
      <span class="detail-count" v-if="detail.length">共 {{ filteredDetail.length }} / {{ detail.length }} 条</span>
      <el-input v-model="search" placeholder="搜索用户 / 接口 / 方法" clearable size="small" class="detail-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <el-table :data="filteredDetail" stripe size="small" max-height="440"
      :default-sort="{ prop: 'cnt', order: 'descending' }">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="userName" label="用户" min-width="130" sortable show-overflow-tooltip />
      <el-table-column prop="endpoint" label="接口" min-width="250" sortable show-overflow-tooltip />
      <el-table-column prop="httpMethod" label="方法" width="92" sortable>
        <template #default="{ row }">
          <el-tag size="small" effect="light" :type="methodTag(row.httpMethod)">{{ row.httpMethod }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cnt" label="请求数" width="110" align="right" sortable />
      <template #empty>
        <span style="color: var(--zen-text-muted)">{{ loading ? '加载中…' : (search ? '没有匹配的记录' : '暂无数据') }}</span>
      </template>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getApiTop, getApiTrend, getApiDetail } from '../../../api'

const props = defineProps({ days: { type: Number, default: 7 } })

const top = ref([])
const trendData = ref({ dates: [], counts: [] })
const detail = ref([])
const search = ref('')
const loading = ref(false)

// HTTP 方法着色：GET 蓝 / POST 绿 / PUT 橙 / DELETE 红
const methodTag = (m) => ({ GET: 'info', POST: 'success', PUT: 'warning', DELETE: 'danger', PATCH: 'primary' }[m] || '')

const filteredDetail = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return detail.value
  return detail.value.filter(r =>
    (r.userName || '').toLowerCase().includes(q) ||
    (r.endpoint || '').toLowerCase().includes(q) ||
    (r.httpMethod || '').toLowerCase().includes(q)
  )
})

const barEl = ref(null)
const trendEl = ref(null)
let barInst = null
let trendInst = null

const load = async () => {
  loading.value = true
  try {
    const [t, tr, dt] = await Promise.all([
      getApiTop({ days: props.days, limit: 20 }),
      getApiTrend(props.days),
      getApiDetail({ days: props.days, limit: 1000 })
    ])
    top.value = (t.data || []).map(r => ({
      endpoint: r.endpoint,
      httpMethod: r.httpMethod,
      cnt: Number(r.cnt || 0)
    }))
    trendData.value = tr.data || { dates: [], counts: [] }
    detail.value = (dt.data || []).map(r => ({
      userId: Number(r.userId || 0),
      userName: r.userName || '匿名',
      endpoint: r.endpoint,
      httpMethod: r.httpMethod,
      cnt: Number(r.cnt || 0)
    }))
  } catch (e) {
    top.value = []
    detail.value = []
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
.detail-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 12px;
}
.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--zen-text);
}
.detail-count {
  font-size: 12px;
  color: var(--zen-text-muted);
}
.detail-search {
  width: 260px;
  margin-left: auto;
}
@media (max-width: 900px) {
  .panel-grid { grid-template-columns: 1fr; }
}
</style>
