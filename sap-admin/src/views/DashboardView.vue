<template>
  <div class="dashboard zen-fade-in">
    <div class="page-header">
      <h2>静 · 观</h2>
      <p>总览社团运行脉络</p>
    </div>

    <!-- 年份切换 -->
    <div class="scope-switcher">
      <span class="scope-label">{{ isAll ? '全部数据' : currentGrade + ' 届数据' }}</span>
      <el-switch v-model="isAll" active-text="全部" inactive-text="本届" @change="onScopeChange" />
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card" v-for="item in statCards" :key="item.label">
        <div class="stat-icon-wrap" :style="{ background: item.bgColor }">
          <el-icon :size="22" :color="item.color"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-number">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </div>

    <!-- 历届成员统计图 -->
    <div class="zen-card chart-card">
      <h3 class="section-title">历届成员</h3>
      <div v-show="gradeStatsLoaded" ref="chartRef" class="grade-chart"></div>
      <div v-if="!gradeStatsLoaded && gradeStatsLoading" class="chart-placeholder">
        <span style="opacity: 0.3; font-size: 32px">📊</span>
        <p style="color: var(--zen-text-muted); margin-top: 8px; font-size: 13px">加载中...</p>
      </div>
      <el-empty v-if="!gradeStatsLoading && !gradeStatsLoaded" description="暂无历届数据" :image-size="80" />
    </div>

    <!-- 财务概览 + 快捷操作 -->
    <div class="section-grid">
      <div class="zen-card">
        <h3 class="section-title">财务概览</h3>
        <div class="finance-summary">
          <div class="fin-item">
            <span class="fin-label">总收入</span>
            <span class="fin-value income">¥{{ financeStats.totalIncome || '0.00' }}</span>
          </div>
          <div class="fin-item">
            <span class="fin-label">总支出</span>
            <span class="fin-value expense">¥{{ financeStats.totalExpense || '0.00' }}</span>
          </div>
          <div class="fin-divider"></div>
          <div class="fin-item">
            <span class="fin-label">
              结余
              <el-tooltip v-if="isAll" content="累计口径（含历届）：为所有年级收支直接相加，非单届结算" placement="top">
                <span style="color: var(--zen-text-muted); font-size: 12px; cursor: help;">（累计口径）</span>
              </el-tooltip>
            </span>
            <span class="fin-value balance">¥{{ financeStats.balance || '0.00' }}</span>
          </div>
        </div>
      </div>

      <div class="zen-card">
        <h3 class="section-title">快捷操作</h3>
        <div class="quick-actions">
          <router-link to="/member" class="action-btn">
            <el-icon><User /></el-icon>
            <span>成员管理</span>
          </router-link>
          <router-link to="/activity" class="action-btn">
            <el-icon><Calendar /></el-icon>
            <span>活动管理</span>
          </router-link>
          <router-link to="/finance" class="action-btn">
            <el-icon><Wallet /></el-icon>
            <span>财务管理</span>
          </router-link>
          <router-link to="/study" class="action-btn">
            <el-icon><Reading /></el-icon>
            <span>学习小组</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getDashboardStats, getGradeStats } from '../api'
import * as echarts from 'echarts'

const isAll = ref(false)
const currentGrade = ref('')

const statCards = ref([
  { label: '社团成员', value: '0', icon: 'User', color: '#8b7355', bgColor: 'rgba(139,115,85,0.08)' },
  { label: '社团活动', value: '0', icon: 'Calendar', color: '#5c8a5e', bgColor: 'rgba(92,138,94,0.08)' },
  { label: '学习活动', value: '0', icon: 'Reading', color: '#6b8fa3', bgColor: 'rgba(107,143,163,0.08)' },
  { label: '财务账单', value: '0', icon: 'Wallet', color: '#c49b5c', bgColor: 'rgba(196,155,92,0.08)' }
])

const financeStats = reactive({
  totalIncome: '0.00',
  totalExpense: '0.00',
  balance: '0.00'
})

const chartRef = ref(null)
const gradeStatsLoaded = ref(false)
const gradeStatsLoading = ref(true)
let chartInstance = null

const loadStats = async (grade) => {
  try {
    const res = await getDashboardStats(grade || undefined)
    const d = res.data
    if (d.currentGrade && d.currentGrade !== 'all') currentGrade.value = String(d.currentGrade)
    statCards.value[0].value = String(d.userCount ?? 0)
    statCards.value[1].value = String(d.activityCount ?? 0)
    statCards.value[2].value = String(d.studyActivityCount ?? 0)
    if (d.financeStats) {
      statCards.value[3].value = String(d.financeStats.count ?? 0)
      financeStats.totalIncome = String(d.financeStats.totalIncome ?? '0.00')
      financeStats.totalExpense = String(d.financeStats.totalExpense ?? '0.00')
      financeStats.balance = String(d.financeStats.balance ?? '0.00')
    }
  } catch (e) {}
}

const onScopeChange = () => {
  loadStats(isAll.value ? 'all' : undefined)
}

const initChart = (data) => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  const grades = data.map(d => d.grade + '届')
  const counts = data.map(d => d.count)
  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e8e6e3', borderWidth: 1,
      textStyle: { color: '#222', fontSize: 13 },
      formatter: (params) => {
        const p = params[0]
        return `<div style="font-weight:600;margin-bottom:4px">${p.name}</div>
                <span style="color:#8b7355">● </span>成员 <b>${p.value}</b> 人`
      }
    },
    grid: { top: 20, right: 20, bottom: 30, left: 50 },
    xAxis: {
      type: 'category', data: grades,
      axisLine: { lineStyle: { color: '#e8e6e3' } },
      axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 12, fontFamily: 'Noto Sans SC, sans-serif' }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      splitLine: { lineStyle: { color: '#f5f3f0', type: 'dashed' } },
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 12 }
    },
    series: [{
      type: 'bar', data: counts, barWidth: '45%',
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#a89279' }, { offset: 1, color: '#8b7355' }
        ])
      },
      emphasis: {
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#c5b5a2' }, { offset: 1, color: '#8b7355' }
          ])
        }
      },
      label: { show: true, position: 'top', color: '#8b7355', fontSize: 13, fontWeight: 600, formatter: '{c}人' }
    }],
    animationDuration: 800, animationEasing: 'cubicOut'
  })
}

const handleResize = () => chartInstance?.resize()

onMounted(async () => {
  loadStats()
  try {
    const res = await getGradeStats()
    const data = res.data || []
    if (data.length > 0) {
      gradeStatsLoaded.value = true
      await nextTick()
      initChart(data)
      window.addEventListener('resize', handleResize)
    }
  } catch (e) {
  } finally {
    // 无论成功失败都结束 loading，空数据时显示 el-empty 而非永远「加载中」
    gradeStatsLoading.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>


