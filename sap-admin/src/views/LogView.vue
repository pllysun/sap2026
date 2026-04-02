<template>
  <div class="log-page zen-fade-in">
    <div class="page-header">
      <h2>鉴 · 迹</h2>
      <p>系统操作日志记录与统计分析</p>
    </div>

    <!-- 统计区 -->
    <div class="stats-row">
      <div class="zen-card stat-card">
        <div class="stat-title">操作类型分布</div>
        <div ref="pieChart" class="chart-box"></div>
      </div>
      <div class="zen-card stat-card">
        <div class="stat-title">HTTP 方法统计</div>
        <div ref="barChart" class="chart-box"></div>
      </div>
      <div class="zen-card stat-card stat-card-wide">
        <div class="stat-title">近7天操作趋势</div>
        <div ref="lineChart" class="chart-box"></div>
      </div>
    </div>

    <!-- 日历热力图 -->
    <div class="zen-card" style="margin-bottom: 16px;">
      <div class="stat-title">🔥 操作日志活跃度（近一年）</div>
      <div ref="calendarChart" class="calendar-chart-box"></div>
    </div>

    <!-- 日志列表 -->
    <div class="zen-card">
      <div class="filter-bar">
        <el-select v-model="filterType" placeholder="操作类型" clearable style="width: 130px" @change="loadLogs">
          <el-option label="查询" value="查询" />
          <el-option label="新增" value="新增" />
          <el-option label="修改" value="修改" />
          <el-option label="删除" value="删除" />
        </el-select>
        <el-select v-model="filterMethod" placeholder="HTTP方法" clearable style="width: 130px" @change="loadLogs">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-tag effect="plain" style="margin-left: auto">
          共 {{ total }} 条记录
        </el-tag>
      </div>

      <el-table :data="logList" stripe style="width: 100%" size="small">
        <el-table-column prop="requestTime" label="时间" width="170" />
        <el-table-column prop="userName" label="操作者" width="100" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="description" label="操作描述" min-width="160" />
        <el-table-column prop="operationType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="opTypeTag(row.operationType)" size="small" effect="plain">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="httpMethod" label="方法" width="80">
          <template #default="{ row }">
            <el-tag :type="httpMethodTag(row.httpMethod)" size="small" effect="dark" round>{{ row.httpMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="200" show-overflow-tooltip />
        <el-table-column prop="duration" label="耗时" width="80">
          <template #default="{ row }">{{ row.duration }}ms</template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="sizes, prev, pager, next, jumper"
          @current-change="loadLogs"
          @size-change="loadLogs"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { getLogList, getLogStats } from '../api'
import request from '../utils/request'
import * as echarts from 'echarts'

const logList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterType = ref('')
const filterMethod = ref('')

const pieChart = ref(null)
const barChart = ref(null)
const lineChart = ref(null)
let pieInstance = null
let barInstance = null
let lineInstance = null

const calendarChart = ref(null)
let calendarInstance = null

const opTypeTag = (t) => {
  const map = { '查询': '', '新增': 'success', '修改': 'warning', '删除': 'danger' }
  return map[t] || 'info'
}

const httpMethodTag = (m) => {
  const map = { 'GET': 'info', 'POST': 'success', 'PUT': 'warning', 'DELETE': 'danger' }
  return map[m] || ''
}

const loadLogs = async () => {
  try {
    const res = await getLogList({
      current: currentPage.value,
      size: pageSize.value,
      operationType: filterType.value || undefined,
      httpMethod: filterMethod.value || undefined
    })
    logList.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) {}
}

const loadStats = async () => {
  try {
    const res = await getLogStats(7)
    const data = res.data || {}
    renderPie(data.byOperationType || {})
    renderBar(data.byHttpMethod || {})
    renderLine(data.dailyTrend || {})
  } catch (e) {}
}

const COLORS = ['#c9a96e', '#e8c87a', '#a68b5b', '#d4b978', '#8b7355', '#f0d890', '#b5976b']

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
      lineStyle: { color: '#c9a96e', width: 3 },
      itemStyle: { color: '#c9a96e' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[
        { offset: 0, color: 'rgba(201,169,110,0.3)' },
        { offset: 1, color: 'rgba(201,169,110,0.02)' }
      ]) },
      data: entries.map(e => e[1])
    }]
  })
}

const loadCalendarHeatmap = async () => {
  try {
    const res = await request.get('/api/log/calendar', { params: { days: 365 } })
    const data = res.data || []
    renderCalendar(data)
  } catch (e) {}
}

const renderCalendar = (data) => {
  if (!calendarChart.value) return
  calendarInstance = echarts.init(calendarChart.value)

  const today = new Date()
  const oneYearAgo = new Date(today)
  oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1)
  oneYearAgo.setDate(oneYearAgo.getDate() + 1)

  const rangeStart = oneYearAgo.getFullYear() + '-' +
    String(oneYearAgo.getMonth() + 1).padStart(2, '0') + '-' +
    String(oneYearAgo.getDate()).padStart(2, '0')
  const rangeEnd = today.getFullYear() + '-' +
    String(today.getMonth() + 1).padStart(2, '0') + '-' +
    String(today.getDate()).padStart(2, '0')

  calendarInstance.setOption({
    tooltip: {
      formatter: (params) => {
        return params.value[0] + '<br/>操作次数: ' + (params.value[1] || 0)
      }
    },
    visualMap: {
      min: 0,
      max: Math.max(...data.map(d => d[1]), 1),
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: {
        color: ['#ebedf0', '#f0d89066', '#e8c87a', '#c9a96e', '#8b7355']
      },
      textStyle: { color: '#7c7a72' }
    },
    calendar: {
      top: 30,
      left: 60,
      right: 30,
      bottom: 50,
      range: [rangeStart, rangeEnd],
      cellSize: ['auto', 15],
      splitLine: { show: false },
      itemStyle: {
        borderWidth: 3,
        borderColor: '#fff'
      },
      yearLabel: { show: false },
      monthLabel: { nameMap: 'en', color: '#7c7a72' },
      dayLabel: { firstDay: 1, nameMap: 'en', color: '#7c7a72' }
    },
    series: [{
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data: data
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
  loadLogs()
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


