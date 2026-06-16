<template>
  <div class="analytics-page zen-fade-in">
    <div class="page-header">
      <h2>流量统计</h2>
      <p>COS 流量与接口请求统计 · 按用户聚合（大概统计）</p>
    </div>

    <!-- 统计周期 -->
    <div class="analytics-toolbar">
      <span class="toolbar-label">统计周期</span>
      <el-radio-group v-model="days" size="small" @change="loadOverview">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 概览卡片 -->
    <div class="overview-row">
      <div class="ov-card">
        <div class="ov-icon up">↑</div>
        <div class="ov-body"><div class="ov-val">{{ formatBytes(overview.uploadBytes) }}</div><div class="ov-label">上传总量</div></div>
      </div>
      <div class="ov-card">
        <div class="ov-icon down">↓</div>
        <div class="ov-body"><div class="ov-val">{{ formatBytes(overview.downloadBytes) }}</div><div class="ov-label">下载总量</div></div>
      </div>
      <div class="ov-card">
        <div class="ov-icon req">≡</div>
        <div class="ov-body"><div class="ov-val">{{ formatInt(overview.totalRequests) }}</div><div class="ov-label">接口请求数</div></div>
      </div>
      <div class="ov-card">
        <div class="ov-icon usr">@</div>
        <div class="ov-body"><div class="ov-val">{{ formatInt(overview.activeUsers) }}</div><div class="ov-label">活跃用户</div></div>
      </div>
    </div>

    <!-- 模块化多 Tab：新增统计 = 加一项 modules + 一个 Panel 组件 -->
    <div class="zen-card analytics-tabs-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane v-for="m in modules" :key="m.key" :label="m.label" :name="m.key" lazy>
          <component :is="m.component" :days="days" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, markRaw, onMounted } from 'vue'
import { getStatsOverview } from '../../api'
import { formatBytes, formatInt } from '../../utils/format'
import CosTrafficPanel from './panels/CosTrafficPanel.vue'
import ApiRequestPanel from './panels/ApiRequestPanel.vue'

const days = ref(7)
const activeTab = ref('cos')
const overview = reactive({ uploadBytes: 0, downloadBytes: 0, totalRequests: 0, activeUsers: 0 })

// 可扩展模块注册表
const modules = [
  { key: 'cos', label: 'COS 流量', component: markRaw(CosTrafficPanel) },
  { key: 'api', label: '接口请求', component: markRaw(ApiRequestPanel) }
]

const loadOverview = async () => {
  try {
    const res = await getStatsOverview(days.value)
    const d = res.data || {}
    overview.uploadBytes = Number(d.uploadBytes || 0)
    overview.downloadBytes = Number(d.downloadBytes || 0)
    overview.totalRequests = Number(d.totalRequests || 0)
    overview.activeUsers = Number(d.activeUsers || 0)
  } catch (e) {}
}

onMounted(loadOverview)
</script>

<style scoped>
.analytics-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.toolbar-label {
  font-size: 13px;
  color: var(--zen-text-secondary);
}
.overview-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.ov-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--zen-card);
  border: 1px solid var(--zen-border-light);
  border-radius: var(--zen-radius-lg);
  box-shadow: var(--zen-shadow-sm);
  padding: 18px 20px;
}
.ov-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--zen-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.ov-icon.up { background: linear-gradient(135deg, #14B8A6, #3B82F6); }
.ov-icon.down { background: linear-gradient(135deg, #3B82F6, #8B5CF6); }
.ov-icon.req { background: linear-gradient(135deg, #8B5CF6, #6366F1); }
.ov-icon.usr { background: linear-gradient(135deg, #F59E0B, #EF4444); }
.ov-val {
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--zen-text);
  line-height: 1.2;
}
.ov-label {
  font-size: 12px;
  color: var(--zen-text-muted);
  margin-top: 2px;
}
.analytics-tabs-card {
  padding: 8px 20px 20px;
}
@media (max-width: 900px) {
  .overview-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
