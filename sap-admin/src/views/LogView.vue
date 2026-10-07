<template>
  <div class="log-page zen-fade-in">
    <div class="page-header">
      <h2>日志管理</h2>
      <p>系统操作日志记录与统计分析</p>
    </div>

    <!-- 统计区 -->
    <div class="stats-row">
      <div class="zen-card stat-card">
        <div class="stat-title">操作类型分布</div>
        <div v-show="statsStatus === 'loaded' && hasPie" ref="pieChart" class="chart-box"></div>
        <div v-if="statsStatus !== 'loaded' || !hasPie" class="log-chart-empty">
          {{ chartPlaceholderText(hasPie) }}
        </div>
      </div>
      <div class="zen-card stat-card">
        <div class="stat-title">HTTP 方法统计</div>
        <div v-show="statsStatus === 'loaded' && hasBar" ref="barChart" class="chart-box"></div>
        <div v-if="statsStatus !== 'loaded' || !hasBar" class="log-chart-empty">
          {{ chartPlaceholderText(hasBar) }}
        </div>
      </div>
      <div class="zen-card stat-card stat-card-wide">
        <div class="stat-title">近7天操作趋势</div>
        <div v-show="statsStatus === 'loaded' && hasLine" ref="lineChart" class="chart-box"></div>
        <div v-if="statsStatus !== 'loaded' || !hasLine" class="log-chart-empty">
          {{ chartPlaceholderText(hasLine) }}
        </div>
      </div>
    </div>

    <!-- 日历热力图 -->
    <div class="zen-card" style="margin-bottom: 16px;">
      <div class="stat-title">操作日志活跃度 · 永久保留
        <el-date-picker v-model="heatmapYear" type="year" value-format="YYYY" :clearable="false" style="width:120px;margin-left:16px" @change="loadCalendarHeatmap" />
      </div>
      <div ref="calendarChart" class="calendar-chart-box"></div>
    </div>

    <LogExplorer />
  </div>
</template>

<script setup>
import { createActivityScale } from '../../../shared/activityIntensity.mjs'
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { getLogStats } from '../api'
import request from '../utils/request'
import * as echarts from 'echarts'
import LogExplorer from './LogExplorer.vue'
const heatmapYear = ref(String(new Date().getFullYear()))


const pieChart = ref(null)
const barChart = ref(null)
const lineChart = ref(null)
let pieInstance = null
let barInstance = null
let lineInstance = null

// 统计区状态：loading / loaded / error
const statsStatus = ref('loading')
const hasPie = ref(false)
const hasBar = ref(false)
const hasLine = ref(false)

const chartPlaceholderText = (hasData) => {
  if (statsStatus.value === 'loading') return '加载中…'
  if (statsStatus.value === 'error') return '统计加载失败'
  return hasData ? '' : '暂无数据'
}

const calendarChart = ref(null)
let calendarInstance = null


const loadStats = async () => {
  statsStatus.value = 'loading'
  try {
    const res = await getLogStats(7)
    const data = res.data || {}
    const pieData = data.byOperationType || {}
    const barData = data.byHttpMethod || {}
    const lineData = data.dailyTrend || {}
    hasPie.value = Object.keys(pieData).length > 0
    hasBar.value = Object.keys(barData).length > 0
    hasLine.value = Object.keys(lineData).length > 0
    statsStatus.value = 'loaded'
    // 等容器 v-show 显示后再渲染，否则 echarts 容器尺寸为 0
    await nextTick()
    if (hasPie.value) renderPie(pieData)
    if (hasBar.value) renderBar(barData)
    if (hasLine.value) renderLine(lineData)
  } catch (e) {
    statsStatus.value = 'error'
  }
}

const COLORS = ['#3B82F6', '#14B8A6', '#8B5CF6', '#22C55E', '#F59E0B', '#EF4444', '#0EA5E9']

const renderPie = (data) => {
  if (!pieChart.value) return
  pieInstance = echarts.init(pieChart.value)
  pieInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    color: COLORS,
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '55%'],
      label: { fontSize: 12 },
      data: Object.entries(data).map(([name, value]) => ({ name, value }))
    }]
  })
}

const renderBar = (data) => {
  if (!barChart.value) return
  barInstance = echarts.init(barChart.value)
  const entries = Object.entries(data)
  barInstance.setOption({
    tooltip: { trigger: 'axis' },
    color: COLORS,
    grid: { top: 20, bottom: 30, left: 50, right: 20 },
    xAxis: { type: 'category', data: entries.map(e => e[0]) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', barWidth: 36, itemStyle: { borderRadius: [6,6,0,0] },
      data: entries.map((e, i) => ({ value: e[1], itemStyle: { color: COLORS[i % COLORS.length] } }))
    }]
  })
}

const renderLine = (data) => {
  if (!lineChart.value) return
  lineInstance = echarts.init(lineChart.value)
  const entries = Object.entries(data)
  lineInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 20, bottom: 30, left: 50, right: 20 },
    xAxis: { type: 'category', data: entries.map(e => e[0].slice(5)), boundaryGap: false },
    yAxis: { type: 'value' },
    series: [{
      type: 'line', smooth: true, symbol: 'circle', symbolSize: 8,
      lineStyle: { color: '#3B82F6', width: 3 },
      itemStyle: { color: '#3B82F6' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[
        { offset: 0, color: 'rgba(59,130,246,0.25)' },
        { offset: 1, color: 'rgba(59,130,246,0.02)' }
      ]) },
      data: entries.map(e => e[1])
    }]
  })
}

const loadCalendarHeatmap = async () => {
  try {
    const res = await request.get('/api/log/calendar-year', { params: { year: heatmapYear.value } })
    const data = res.data || []
    renderCalendar(data)
  } catch (e) {}
}

const renderCalendar = (data) => {
  if (!calendarChart.value) return
  calendarInstance ||= echarts.init(calendarChart.value)
  const intensity = createActivityScale(data.map(day => day[1]))

  calendarInstance.setOption({
    tooltip: {
      formatter: (params) => {
        return params.value[0] + '<br/>操作次数: ' + (params.value[1] || 0)
      }
    },
    visualMap: {
      min: 0,
      max: 4,
      dimension: 2,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      text: ['多', '少'],
      inRange: {
        color: ['#eef0f7', '#c8dbfc', '#9dbffb', '#6ba1f8', '#3B82F6']
      },
      textStyle: { color: '#9298b0' }
    },
    calendar: {
      top: 30,
      left: 60,
      right: 30,
      bottom: 50,
      range: heatmapYear.value,
      cellSize: ['auto', 15],
      splitLine: { show: false },
      itemStyle: {
        borderWidth: 3,
        borderColor: '#fff'
      },
      yearLabel: { show: false },
      monthLabel: { nameMap: 'en', color: '#9298b0' },
      dayLabel: { firstDay: 1, nameMap: 'en', color: '#9298b0' }
    },
    series: [{
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data: data.map(day => [day[0], day[1], intensity(day[1])])
    }]
  })
}

const handleResize = () => {
  pieInstance?.resize()
  barInstance?.resize()
  lineInstance?.resize()
  calendarInstance?.resize()
}

onMounted(async () => {
  await nextTick()
  loadStats()
  loadCalendarHeatmap()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  pieInstance?.dispose()
  barInstance?.dispose()
  lineInstance?.dispose()
  calendarInstance?.dispose()
})
</script>

<style scoped>
.log-page .stat-card { display: block; }
.log-page .chart-box { width: 100%; }
.log-chart-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--zen-text-muted, #999);
  font-size: 13px;
}
</style>
