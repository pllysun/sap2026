<template>
  <div class="page">
    <div class="page-header"><h1 class="page-title">我的成绩</h1></div>

    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <div v-else-if="activities.length === 0" class="empty">
      <div class="empty__text">还没有成绩记录</div>
      <p class="t-caption mt-2">请选择年份和活动次数查看成绩</p>
    </div>

    <template v-else>
      <!-- Filter Bar -->
      <div class="filter-bar mb-4 anim-in">
        <div class="form-group" style="margin-bottom:0; min-width:120px;">
          <select v-model="selectedIdx" class="select" @change="onSelect">
            <option v-for="(a, i) in activities" :key="i" :value="i">
              {{ a.title || `第${a.seqNum}次` }} ({{ a.grade }})
            </option>
          </select>
        </div>
        <button class="btn btn--primary btn--sm btn--pill" @click="onSelect">🔄 刷新</button>
      </div>

      <!-- Current Activity Card -->
      <div v-if="current" class="card mb-4 anim-in">
        <div class="flex-between mb-3">
          <h3 class="t-heading">{{ current.title || `第${current.seqNum}次学习活动` }}</h3>
          <div class="flex gap-1">
            <span class="badge badge--primary">总分 {{ current.totalScore }}</span>
            <span class="badge badge--gradient">第 {{ current.rank }} 名</span>
          </div>
        </div>

        <div class="t-heading mb-3" style="display:flex;align-items:center;gap:6px;">⏱ 成绩时间线</div>

        <div v-if="current.scores.length > 0">
          <div v-for="(s, idx) in current.scores" :key="idx"
            class="card card--gradient mb-2 anim-in"
            :style="{ animationDelay: (idx * 0.05) + 's', padding: 'var(--s4)' }">
            <div class="flex-between">
              <span class="t-heading">第 {{ s.week }} 周</span>
              <span class="badge" :class="s.score >= 7 ? 'badge--success' : s.score >= 4 ? 'badge--warning' : 'badge--error'">{{ s.score }} 分</span>
            </div>
            <p class="t-body mt-1" v-if="s.comment">{{ s.comment }}</p>
            <p class="t-caption mt-1">评分人：{{ s.leaderName }}</p>
          </div>
        </div>
        <div v-else class="empty" style="padding: var(--s6);"><div class="empty__text">暂无成绩记录</div></div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(true)
const activities = ref([])
const selectedIdx = ref(0)
const current = computed(() => activities.value[selectedIdx.value] || null)

onMounted(async () => {
  try { const r = await request.get('/api/study/my-scores'); activities.value = r.data.activities || [] } catch {}
  finally { loading.value = false }
})

function onSelect() { /* reactive via selectedIdx */ }
</script>
