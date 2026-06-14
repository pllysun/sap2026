<template>
  <div>
    <!-- Hero Section — Full Screen -->
    <section class="home-hero">
      <div class="home-hero__deco home-hero__deco--1"></div>
      <div class="home-hero__deco home-hero__deco--2"></div>
      <div class="home-hero__deco home-hero__deco--3"></div>

      <!-- 浮动技术标签 -->
      <div class="hero-float-tags">
        <span class="hero-float-tag" style="top:8%;left:6%;animation-delay:0s;">Vue.js</span>
        <span class="hero-float-tag" style="top:5%;right:10%;animation-delay:-2s;">Python</span>
        <span class="hero-float-tag" style="top:18%;left:3%;animation-delay:-4s;">Java</span>
        <span class="hero-float-tag" style="top:12%;right:4%;animation-delay:-1s;">C++</span>
        <span class="hero-float-tag" style="bottom:30%;left:5%;animation-delay:-3s;">Git</span>
        <span class="hero-float-tag" style="bottom:18%;right:6%;animation-delay:-5s;">Linux</span>
        <span class="hero-float-tag" style="top:32%;right:2%;animation-delay:-6s;">React</span>
        <span class="hero-float-tag" style="bottom:38%;left:2%;animation-delay:-7s;">SQL</span>
        <span class="hero-float-tag" style="top:3%;left:22%;animation-delay:-1.5s;">ChatGPT</span>
        <span class="hero-float-tag" style="top:15%;right:18%;animation-delay:-3.5s;">LLM</span>
        <span class="hero-float-tag" style="bottom:12%;left:15%;animation-delay:-4.5s;">TensorFlow</span>
        <span class="hero-float-tag" style="bottom:25%;right:15%;animation-delay:-0.5s;">Docker</span>
        <span class="hero-float-tag" style="top:28%;left:10%;animation-delay:-5.5s;">Spring Boot</span>
        <span class="hero-float-tag" style="bottom:8%;right:22%;animation-delay:-2.5s;">Node.js</span>
        <span class="hero-float-tag" style="bottom:5%;left:52%;animation-delay:-8s;">PyTorch</span>
        <span class="hero-float-tag" style="bottom:5%;left:38%;animation-delay:-8s;">TypeScript</span>
      </div>

      <!-- 大标题区域 — 中英文结合 -->
      <div class="hero-title-area anim-in">
        <h1 class="hero-title-text">
          <span class="hero-title-text__cn">
            <img src="/logo.png" class="hero-title-logo" alt="Logo" />中南林业科技大学
          </span>
          <span class="hero-title-text__accent">软件协会</span>
        </h1>
        <p class="hero-title-en">CSUFT · Software Association</p>
        <p class="hero-title-slogan">万维网连接五大洲，二进制写尽天下事</p>
      </div>

      <!-- 内容+向下探索包裹层：垂直居中 -->
      <div class="hero-body">
        <div class="home-hero__inner">
          <div class="anim-in hero-text-col" style="animation-delay: 0.15s;">
            <span class="section-label section-label--icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"/><path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"/><path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0"/><path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"/></svg>CSUFTSAP · 计算机学习入门</span>
            <h2 class="t-display mt-2" style="font-size: 1.8rem;">开启你的编程之旅，<br/>从零到一构建技术力量</h2>
            <p class="t-body mt-3" style="max-width: 480px; font-size: 0.95rem;">
              我们为每一位同学提供系统的编程学习路径——从 Markdown、C 语言基础到 Web 开发、AI 与就业分析，帮你找到属于自己的方向。
            </p>
            <div class="hero-btns hero-btns--desktop">
              <!-- 游客 + 入会开启 → 加入协会 -->
              <router-link v-if="isGuest && joinEnabled" to="/join" class="btn btn--primary btn--pill hero-btn"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>加入协会</router-link>
              <!-- 游客 + 入会关闭 → 开始学习（不可点） -->
              <span v-else-if="isGuest && !joinEnabled" class="btn btn--primary btn--pill btn--disabled hero-btn" style="cursor: not-allowed; opacity: 0.6;" title="请联系会长开放入口"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>开始学习</span>
              <!-- 正式成员 → 开始学习 -->
              <router-link v-if="!isGuest" to="/study" class="btn btn--primary btn--pill hero-btn"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>开始学习</router-link>
              <!-- 正式成员 + 入会开启 → 加入协会（提示已加入） -->
              <router-link v-if="!isGuest && joinEnabled" to="/join" class="btn btn--secondary btn--pill hero-btn"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>加入协会</router-link>
              <a href="#about-section" class="btn btn--secondary btn--pill hero-btn" @click.prevent="scrollToAbout"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>了解协会</a>
            </div>
          </div>
          <div class="card anim-in home-hero__code" style="animation-delay:0.25s;">
            <div style="display: flex; gap: 6px; margin-bottom: var(--s3);">
              <span style="width:12px;height:12px;border-radius:50%;background:#EF4444;"></span>
              <span style="width:12px;height:12px;border-radius:50%;background:#F59E0B;"></span>
              <span style="width:12px;height:12px;border-radius:50%;background:#22C55E;"></span>
            </div>
            <pre style="margin:0;color:var(--ink-500);line-height:1.8;"><span style="color:#8B5CF6;">#include</span> <span style="color:var(--teal);">&lt;iostream&gt;</span>
<span style="color:#8B5CF6;">using namespace</span> std;

<span style="color:var(--primary);">int</span> <span style="color:#F59E0B;">main</span>() {
  cout &lt;&lt; <span style="color:var(--teal);">"Hello, Future Developer!"</span>
       &lt;&lt; endl;
  <span style="color:#6e7591;">// 你的编程之旅从这里开始</span>
  <span style="color:#8B5CF6;">return</span> <span style="color:var(--primary);">0</span>;
}</pre>
            <p class="t-caption mt-3 text-center">写下你的第一行代码，成为未来的开发者</p>
          </div>
          <!-- 手机端按钮放在代码卡片下方 -->
          <div class="hero-btns hero-btns--mobile anim-in" style="animation-delay: 0.3s;">
            <router-link v-if="isGuest && joinEnabled" to="/join" class="btn btn--primary btn--pill hero-btn"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>加入协会</router-link>
            <span v-else-if="isGuest && !joinEnabled" class="btn btn--primary btn--pill btn--disabled hero-btn" style="cursor: not-allowed; opacity: 0.6;"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>开始学习</span>
            <router-link v-if="!isGuest" to="/study" class="btn btn--primary btn--pill hero-btn"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>开始学习</router-link>
            <router-link v-if="!isGuest && joinEnabled" to="/join" class="btn btn--secondary btn--pill hero-btn"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>加入协会</router-link>
            <a href="#about-section" class="btn btn--secondary btn--pill hero-btn" @click.prevent="scrollToAbout"><svg style="width:15px;height:15px;vertical-align:-2px;margin-right:5px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>了解协会</a>
          </div>
        </div>

        <!-- 向下滚动引导 -->
        <div class="hero-scroll-hint anim-in" style="animation-delay: 0.4s;" @click="scrollToAbout">
          <span>向下探索</span>
          <div class="hero-scroll-arrow"></div>
        </div>
      </div>
    </section>

    <!-- Stats + Line Chart Section -->
    <section id="about-section" class="home-section">
      <div class="page">
        <span class="section-label section-label--icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>数据洞察</span>
        <h2 class="section-title">数读软协，见证成长</h2>
        <p class="section-desc">用数据记录我们的足迹，每一位成员都是协会历史的创造者。</p>

        <div v-if="stats" class="grid grid--3 mb-4">
          <div v-for="(s, idx) in statCards" :key="idx" class="card card--interactive anim-in" :style="{ animationDelay: (idx * 0.06) + 's' }">
            <div class="stat">
              <div class="stat__value">{{ s.value }}</div>
              <div class="stat__label">{{ s.label }}</div>
            </div>
          </div>
        </div>
        <div v-else class="loading"><div class="loading__spinner"></div></div>

        <!-- 历届成员折线图 -->
        <div v-if="gradeStats.length > 0" class="card anim-in" style="padding: var(--s5);">
          <h3 class="t-heading mb-3" style="display:flex;align-items:center;gap:6px;"><svg style="width:18px;height:18px;color:var(--primary)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/></svg>历届正式成员人数</h3>
          <div class="chart-container">
            <canvas ref="lineChartCanvas"></canvas>
          </div>
        </div>

        <!-- 操作日志日历热力图 -->
        <div class="card anim-in" style="padding: var(--s5); margin-top: var(--s4);">
          <h3 class="t-heading mb-3" style="display:flex;align-items:center;gap:6px;"><svg style="width:18px;height:18px;color:var(--teal)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>在线成员活跃度</h3>
          <div class="heatmap-container">
            <canvas ref="heatmapCanvas"></canvas>
          </div>
          <div class="heatmap-legend">
            <span class="heatmap-legend__label">Less</span>
            <span class="heatmap-legend__box" style="background: #ebedf0;"></span>
            <span class="heatmap-legend__box" style="background: #9be9a8;"></span>
            <span class="heatmap-legend__box" style="background: #40c463;"></span>
            <span class="heatmap-legend__box" style="background: #30a14e;"></span>
            <span class="heatmap-legend__box" style="background: #216e39;"></span>
            <span class="heatmap-legend__label">More</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Learning Path Section -->
    <section class="home-section">
      <div class="page">
        <span class="section-label section-label--icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="6" cy="19" r="3"/><path d="M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15"/><circle cx="18" cy="5" r="3"/></svg>
          推荐学习路径
        </span>
        <h2 class="section-title">计算机入门学习路线</h2>
        <p class="section-desc">循序渐进，从基础到实战，找到最适合你的学习方向。</p>

        <div class="home-paths">
          <div v-for="(path, idx) in learningPaths" :key="idx"
            class="card card--interactive anim-in home-path-card"
            :style="{ animationDelay: (idx * 0.08) + 's', '--accent': path.accent }">
            <span class="home-path-card__step">STEP {{ idx + 1 }}</span>
            <!-- 图标为内置静态 SVG，无用户输入，v-html 安全 -->
            <div class="home-path-card__icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" v-html="path.svg"></svg>
            </div>
            <h3 class="home-path-card__title">{{ path.title }}</h3>
            <p class="home-path-card__desc">{{ path.desc }}</p>
            <div class="home-path-card__tags">
              <span v-for="tag in path.tags" :key="tag" class="home-path-tag">{{ tag }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Quick Start Tips -->
    <section class="home-section">
      <div class="page">
        <span class="section-label section-label--icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5A4.61 4.61 0 0 1 8.91 14"/></svg>新手必看</span>
        <h2 class="section-title">编程入门指南</h2>
        <p class="section-desc">来自学长学姐的建议，帮你少走弯路。</p>

        <div class="grid grid--2">
          <div v-for="(tip, idx) in beginnerTips" :key="idx"
            class="card card--interactive anim-in"
            :style="{ animationDelay: (idx * 0.06) + 's' }">
            <div class="flex gap-2" style="align-items: flex-start;">
              <div class="home-tip-icon" :style="{ background: tip.bg, color: tip.accent }"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="tip.svg"></svg></div>
              <div>
                <h3 class="t-heading" style="margin-bottom: 4px;">{{ tip.title }}</h3>
                <p class="t-body">{{ tip.desc }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Outstanding Members -->
    <section class="home-section">
      <div class="page">
        <span class="section-label section-label--icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>榜样力量</span>
        <h2 class="section-title">优秀学长学姐</h2>
        <p class="section-desc">他们曾在软件协会分享、学习、成长，如今在更大的舞台发光发热。</p>

        <div v-if="membersLoading" class="loading"><div class="loading__spinner"></div></div>
        <div v-else-if="members.length === 0" class="empty"><div class="empty__text">暂无优秀成员数据</div></div>

        <div class="grid grid--4" v-if="pagedMembers.length > 0">
          <div
            v-for="(m, idx) in pagedMembers" :key="m.id"
            class="card card--interactive member-card anim-in"
            :style="{ animationDelay: (idx * 0.04) + 's' }"
          >
            <div class="member-card__name">
              {{ m.name }}
              <span class="badge" :class="destinationBadge(m.destination)">{{ m.destination || '—' }}</span>
            </div>
            <div class="member-card__info">{{ m.grade }}级 · {{ m.major || '未填写' }}</div>
            <div class="member-card__info" v-if="m.destinationDetail" style="display:flex;align-items:center;gap:4px;"><svg style="width:13px;height:13px;flex-shrink:0;opacity:.7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>{{ m.destinationDetail }}</div>
          </div>
        </div>

        <div class="pagination" v-if="totalPages > 1">
          <button class="pagination__btn" :disabled="currentPage <= 1" @click="currentPage--">‹</button>
          <button v-for="p in displayPages" :key="p" class="pagination__btn" :class="{ active: p === currentPage }" @click="currentPage = p">{{ p }}</button>
          <button class="pagination__btn" :disabled="currentPage >= totalPages" @click="currentPage++">›</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const joinEnabled = ref(false)
const isGuest = computed(() => {
  const roles = userStore.roles || []
  return roles.length === 0 || (roles.includes(4) && !roles.some(r => r <= 3))
})

const heatmapCanvas = ref(null)
const calendarData = ref([])

const stats = ref(null)
const members = ref([])
const membersLoading = ref(true)
const currentPage = ref(1)
const pageSize = 8
const gradeStats = ref([])
const lineChartCanvas = ref(null)

const statCards = computed(() => {
  if (!stats.value) return []
  return [
    { value: stats.value.userCount || 0, label: '协会成员（正式）' },
    { value: stats.value.activityCount || 0, label: '活动次数' },
    { value: stats.value.studyActivityCount || 0, label: '学习活动' }
  ]
})

// icon 为内置线性 SVG 路径（Lucide 风格），accent 为该卡片的主题强调色
const learningPaths = [
  {
    accent: '#3B82F6', title: 'Markdown 语法',
    desc: 'AI 时代的必备技能，学会用 Markdown 编写规范文档，提高沟通效率。',
    tags: ['文档编写', 'AI 提问', '排版技能'],
    svg: '<path d="M14 3v4a1 1 0 0 0 1 1h4"/><path d="M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2Z"/><path d="M9 13h6"/><path d="M9 17h6"/>'
  },
  {
    accent: '#6366F1', title: 'C 语言基础',
    desc: '编程的第一步，掌握变量、循环、函数等核心概念，为后续学习打下坚实基础。',
    tags: ['入门必学', '语法基础', '指针'],
    svg: '<path d="m16 18 6-6-6-6"/><path d="m8 6-6 6 6 6"/>'
  },
  {
    accent: '#8B5CF6', title: '数据结构与算法',
    desc: '学会用合适的数据结构解决问题，培养算法思维，提升编程核心竞争力。',
    tags: ['链表', '树与图', '排序算法'],
    svg: '<line x1="6" y1="3" x2="6" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/>'
  },
  {
    accent: '#14B8A6', title: 'Web 开发入门',
    desc: '从 HTML/CSS/JavaScript 开始，学习构建网页应用，了解前后端协作模式。',
    tags: ['HTML/CSS', 'JavaScript', 'Vue/React'],
    svg: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18Z"/>'
  },
  {
    accent: '#0EA5E9', title: 'AI 人工智能',
    desc: '了解机器学习与深度学习基础，学习用 Python 构建智能应用与数据分析。',
    tags: ['Python', '机器学习', '深度学习'],
    svg: '<rect x="5" y="5" width="14" height="14" rx="2"/><rect x="9" y="9" width="6" height="6" rx="1"/><path d="M9 2v2"/><path d="M15 2v2"/><path d="M9 20v2"/><path d="M15 20v2"/><path d="M2 9h2"/><path d="M2 15h2"/><path d="M20 9h2"/><path d="M20 15h2"/>'
  },
  {
    accent: '#F43F5E', title: '就业/考研分析',
    desc: '规划你的未来发展路径，了解计算机行业就业趋势与考研/保研备考策略。',
    tags: ['就业方向', '考研攻略', '保研规划'],
    svg: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>'
  }
]

const beginnerTips = [
  { accent: '#3B82F6', bg: 'rgba(59,130,246,0.1)', title: '坚持每天写代码', desc: '编程是一项实践技能，每天写至少 30 分钟代码，比一周集中学一天效果好得多。', svg: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>' },
  { accent: '#8B5CF6', bg: 'rgba(139,92,246,0.1)', title: '不要怕看错误信息', desc: '报错是程序员最好的老师。学会阅读和理解编译器/运行时给出的错误提示。', svg: '<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/>' },
  { accent: '#14B8A6', bg: 'rgba(20,184,166,0.1)', title: '加入学习小组', desc: '和志同道合的同学组队学习，互相督促、讨论问题，学习效率翻倍。', svg: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>' },
  { accent: '#F59E0B', bg: 'rgba(245,158,11,0.1)', title: '善用搜索和文档', desc: '遇到问题先 Google，学会阅读官方文档。这是每个开发者的必备技能。', svg: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>' }
]

const totalPages = computed(() => Math.ceil(members.value.length / pageSize))
const pagedMembers = computed(() => { const s = (currentPage.value - 1) * pageSize; return members.value.slice(s, s + pageSize) })
const displayPages = computed(() => { const pages = []; for (let i = Math.max(1, currentPage.value - 2); i <= Math.min(totalPages.value, currentPage.value + 2); i++) pages.push(i); return pages })

function destinationBadge(d) {
  if (!d) return 'badge--default'
  if (d.includes('就业')) return 'badge--teal'
  if (d.includes('保研')) return 'badge--success'
  if (d.includes('考研')) return 'badge--purple'
  return 'badge--primary'
}

function scrollToAbout() {
  const el = document.getElementById('about-section')
  if (el) el.scrollIntoView({ behavior: 'smooth' })
}

// 绘制折线图
function drawLineChart() {
  if (!lineChartCanvas.value || gradeStats.value.length === 0) return
  const canvas = lineChartCanvas.value
  const ctx = canvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1

  // Set canvas size
  const rect = canvas.parentElement.getBoundingClientRect()
  canvas.width = rect.width * dpr
  canvas.height = 280 * dpr
  canvas.style.width = rect.width + 'px'
  canvas.style.height = '280px'
  ctx.scale(dpr, dpr)

  const w = rect.width, h = 280
  const data = gradeStats.value
  const labels = data.map(d => d.grade + '级')
  const values = data.map(d => Number(d.count))
  const maxVal = Math.max(...values, 1)

  const padL = 60, padR = 30, padT = 30, padB = 50
  const plotW = w - padL - padR
  const plotH = h - padT - padB

  ctx.clearRect(0, 0, w, h)

  // Grid lines
  const gridLines = 5
  ctx.strokeStyle = '#eef0f7'
  ctx.lineWidth = 1
  ctx.font = '12px Inter, Noto Sans SC, sans-serif'
  ctx.fillStyle = '#9298b0'
  ctx.textAlign = 'right'
  for (let i = 0; i <= gridLines; i++) {
    const y = padT + plotH - (plotH / gridLines) * i
    const val = Math.round((maxVal / gridLines) * i)
    ctx.beginPath(); ctx.moveTo(padL, y); ctx.lineTo(w - padR, y); ctx.stroke()
    ctx.fillText(val.toString(), padL - 8, y + 4)
  }

  if (values.length === 0) return
  const stepX = values.length > 1 ? plotW / (values.length - 1) : plotW

  // Gradient fill
  const gradient = ctx.createLinearGradient(0, padT, 0, h - padB)
  gradient.addColorStop(0, 'rgba(20,184,166,0.25)')
  gradient.addColorStop(1, 'rgba(20,184,166,0.02)')
  ctx.beginPath()
  ctx.moveTo(padL, padT + plotH)
  for (let i = 0; i < values.length; i++) {
    const x = padL + stepX * i
    const y = padT + plotH - (values[i] / maxVal) * plotH
    ctx.lineTo(x, y)
  }
  ctx.lineTo(padL + stepX * (values.length - 1), padT + plotH)
  ctx.closePath()
  ctx.fillStyle = gradient
  ctx.fill()

  // Line
  ctx.beginPath()
  ctx.strokeStyle = '#14B8A6'
  ctx.lineWidth = 2.5
  ctx.lineJoin = 'round'
  for (let i = 0; i < values.length; i++) {
    const x = padL + stepX * i
    const y = padT + plotH - (values[i] / maxVal) * plotH
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
  }
  ctx.stroke()

  // Points + labels
  ctx.textAlign = 'center'
  for (let i = 0; i < values.length; i++) {
    const x = padL + stepX * i
    const y = padT + plotH - (values[i] / maxVal) * plotH

    // Point
    ctx.beginPath(); ctx.arc(x, y, 5, 0, Math.PI * 2)
    ctx.fillStyle = '#fff'; ctx.fill()
    ctx.strokeStyle = '#14B8A6'; ctx.lineWidth = 2.5; ctx.stroke()

    // Value label
    ctx.fillStyle = '#1a1d2e'
    ctx.font = 'bold 13px Inter, Noto Sans SC, sans-serif'
    ctx.fillText(values[i].toString(), x, y - 12)

    // X label
    ctx.fillStyle = '#6e7591'
    ctx.font = '12px Inter, Noto Sans SC, sans-serif'
    ctx.fillText(labels[i], x, h - padB + 20)
  }
}

watch(gradeStats, () => { nextTick(() => drawLineChart()) })

// 数据并行预加载——进入页面前就加载完成
const loadData = async () => {
  const [statsRes, gradeRes, memberRes] = await Promise.allSettled([
    request.get('/api/dashboard/stats', { params: { grade: 'all' } }),
    request.get('/api/dashboard/grade-stats'),
    request.get('/api/outstanding-member/all')
  ])
  if (statsRes.status === 'fulfilled') stats.value = statsRes.value.data
  if (gradeRes.status === 'fulfilled') gradeStats.value = gradeRes.value.data || []
  if (memberRes.status === 'fulfilled') members.value = memberRes.value.data || []
  membersLoading.value = false
}
loadData() // 立即执行，不等 onMounted

// 加载热力图数据
const loadCalendar = async () => {
  try {
    const res = await request.get('/api/log/public/calendar')
    calendarData.value = res.data || []
    nextTick(() => drawHeatmap())
  } catch (e) {}
}
loadCalendar()

// 加载入会状态
request.get('/api/join/status').then(res => { joinEnabled.value = res.data === true }).catch(() => {})
userStore.fetchUserInfo().catch(() => {})

// GitHub 风格日历热力图
function drawHeatmap() {
  if (!heatmapCanvas.value || calendarData.value.length === 0) return
  const canvas = heatmapCanvas.value
  const ctx = canvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1

  const cellSize = 13
  const cellGap = 3
  const cellTotal = cellSize + cellGap
  const labelPadLeft = 36
  const labelPadTop = 22
  const weeksToShow = 53

  const canvasW = labelPadLeft + weeksToShow * cellTotal + 10
  const canvasH = labelPadTop + 7 * cellTotal + 20

  canvas.width = canvasW * dpr
  canvas.height = canvasH * dpr
  canvas.style.width = canvasW + 'px'
  canvas.style.height = canvasH + 'px'
  ctx.scale(dpr, dpr)
  ctx.clearRect(0, 0, canvasW, canvasH)

  // Build date→count map
  const dateMap = {}
  let maxCount = 0
  for (const [date, count] of calendarData.value) {
    dateMap[date] = count
    if (count > maxCount) maxCount = count
  }

  // Color levels
  const getColor = (count) => {
    if (count === 0) return '#ebedf0'
    const ratio = count / Math.max(maxCount, 1)
    if (ratio <= 0.25) return '#9be9a8'
    if (ratio <= 0.5) return '#40c463'
    if (ratio <= 0.75) return '#30a14e'
    return '#216e39'
  }

  // Calculate start date (go back to the first Sunday/Monday of ~53 weeks ago)
  const today = new Date()
  const endDate = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const startDate = new Date(endDate)
  startDate.setDate(startDate.getDate() - (weeksToShow * 7 - 1) - startDate.getDay())

  // Month labels
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
  ctx.fillStyle = '#6e7591'
  ctx.font = '11px Inter, Noto Sans SC, sans-serif'
  ctx.textAlign = 'center'

  let lastMonth = -1
  const d = new Date(startDate)
  for (let week = 0; week < weeksToShow; week++) {
    const testDate = new Date(d)
    testDate.setDate(testDate.getDate() + week * 7)
    if (testDate.getMonth() !== lastMonth) {
      lastMonth = testDate.getMonth()
      ctx.fillText(months[lastMonth], labelPadLeft + week * cellTotal + cellSize / 2, 14)
    }
  }

  // Day labels
  const dayLabels = ['', 'Mon', '', 'Wed', '', 'Fri', '']
  ctx.fillStyle = '#6e7591'
  ctx.font = '10px Inter, Noto Sans SC, sans-serif'
  ctx.textAlign = 'right'
  for (let i = 0; i < 7; i++) {
    if (dayLabels[i]) {
      ctx.fillText(dayLabels[i], labelPadLeft - 6, labelPadTop + i * cellTotal + cellSize - 1)
    }
  }

  // Draw cells
  const current = new Date(startDate)
  for (let week = 0; week < weeksToShow; week++) {
    for (let day = 0; day < 7; day++) {
      const dateStr = current.getFullYear() + '-' +
        String(current.getMonth() + 1).padStart(2, '0') + '-' +
        String(current.getDate()).padStart(2, '0')

      if (current <= endDate) {
        const count = dateMap[dateStr] || 0
        const x = labelPadLeft + week * cellTotal
        const y = labelPadTop + day * cellTotal

        ctx.fillStyle = getColor(count)
        ctx.beginPath()
        // Rounded rect
        const r = 2
        ctx.moveTo(x + r, y)
        ctx.lineTo(x + cellSize - r, y)
        ctx.quadraticCurveTo(x + cellSize, y, x + cellSize, y + r)
        ctx.lineTo(x + cellSize, y + cellSize - r)
        ctx.quadraticCurveTo(x + cellSize, y + cellSize, x + cellSize - r, y + cellSize)
        ctx.lineTo(x + r, y + cellSize)
        ctx.quadraticCurveTo(x, y + cellSize, x, y + cellSize - r)
        ctx.lineTo(x, y + r)
        ctx.quadraticCurveTo(x, y, x + r, y)
        ctx.fill()
      }
      current.setDate(current.getDate() + 1)
    }
  }
}

// Hero 全屏滚动吸附
let isSnapping = false
const wheelHandler = (e) => {
  if (isSnapping) { e.preventDefault(); return }
  const heroEl = document.querySelector('.home-hero')
  const aboutEl = document.getElementById('about-section')
  if (!heroEl || !aboutEl) return

  const heroRect = heroEl.getBoundingClientRect()
  const aboutRect = aboutEl.getBoundingClientRect()

  // 在 Hero 区域内，向下滚动 → 跳到 about-section
  if (e.deltaY > 0 && heroRect.bottom > window.innerHeight * 0.5) {
    e.preventDefault()
    isSnapping = true
    aboutEl.scrollIntoView({ behavior: 'smooth' })
    setTimeout(() => { isSnapping = false }, 800)
    return
  }

  // 在 about-section 顶部附近，向上滚动 → 跳回 Hero
  if (e.deltaY < 0 && aboutRect.top >= -10 && aboutRect.top <= 80) {
    e.preventDefault()
    isSnapping = true
    window.scrollTo({ top: 0, behavior: 'smooth' })
    setTimeout(() => { isSnapping = false }, 800)
    return
  }
}

onMounted(() => {
  window.addEventListener('wheel', wheelHandler, { passive: false })
})

onUnmounted(() => {
  window.removeEventListener('wheel', wheelHandler)
})
</script>

<style scoped>
/* Full-screen hero — vertical layout: title on top, content below */
.home-hero {
  min-height: 100vh;
  margin-top: -72px;
  padding: 5vh var(--s6) 0;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  background: var(--gradient-soft);
}

/* 内容+向下探索包裹层：占据剩余空间，内容偏上 */
.hero-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding-top: 10vh;
  width: 100%;
  gap: var(--s5);
}

/* 浮动技术标签 */
.hero-float-tags { position: absolute; inset: 0; pointer-events: none; z-index: 0; }
.hero-float-tag {
  position: absolute;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--ink-300);
  background: rgba(255,255,255,0.5);
  padding: 4px 14px;
  border-radius: var(--r-pill);
  border: 1px solid var(--ink-100);
  animation: tagFloat 8s ease-in-out infinite;
  backdrop-filter: blur(4px);
}
@keyframes tagFloat {
  0%, 100% { transform: translateY(0px) rotate(0deg); opacity: 0.5; }
  25% { transform: translateY(-12px) rotate(2deg); opacity: 0.8; }
  50% { transform: translateY(-6px) rotate(-1deg); opacity: 0.6; }
  75% { transform: translateY(-14px) rotate(1deg); opacity: 0.7; }
}

/* 大标题区域 — 中英文结合 */
.hero-title-area {
  text-align: center;
  position: relative;
  z-index: 1;
}
.hero-title-logo {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  object-fit: cover;
  vertical-align: middle;
  margin-right: 12px;
  margin-top: -6px;
  box-shadow: 0 6px 24px rgba(20,184,166,0.25), 0 0 40px rgba(59,130,246,0.1);
  animation: logoGlow 4s ease-in-out infinite;
}
@keyframes logoGlow {
  0%, 100% { box-shadow: 0 6px 24px rgba(20,184,166,0.25), 0 0 40px rgba(59,130,246,0.1); }
  50% { box-shadow: 0 8px 36px rgba(20,184,166,0.35), 0 0 60px rgba(139,92,246,0.15); }
}
.hero-title-text {
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.hero-title-text__cn {
  font-size: 3.6rem;
  font-weight: 800;
  color: var(--ink-800);
  line-height: 1.15;
  letter-spacing: 0.02em;
  display: inline-flex;
  align-items: center;
}
.hero-title-text__accent {
  background: linear-gradient(135deg, #14B8A6 0%, #3B82F6 50%, #8B5CF6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-size: 4.2rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  line-height: 1.15;
}
.hero-title-en {
  margin-top: var(--s3);
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--ink-400);
  letter-spacing: 0.25em;
  text-transform: uppercase;
}
.hero-title-slogan {
  margin-top: var(--s2);
  font-size: 0.95rem;
  color: var(--ink-300);
  font-weight: 400;
  letter-spacing: 0.12em;
  font-style: italic;
}

.home-hero__inner {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--s7);
  align-items: center;
  position: relative;
  z-index: 1;
}
.home-hero__code {
  padding: var(--s5);
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.85rem;
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-lg);
}

/* 向下滚动引导 */
.hero-scroll-hint {
  position: relative;
  z-index: 1;
  cursor: pointer;
  text-align: center;
  color: var(--ink-400);
  font-size: 0.8rem;
  font-weight: 500;
  letter-spacing: 0.1em;
  transition: color 0.2s;
  margin-top: auto;
  padding-bottom: var(--s4);
}
.hero-scroll-hint:hover { color: var(--primary); }
.hero-scroll-arrow {
  width: 20px; height: 20px;
  margin: 6px auto 0;
  border-right: 2px solid var(--ink-300);
  border-bottom: 2px solid var(--ink-300);
  transform: rotate(45deg);
  animation: scrollBounce 2s ease-in-out infinite;
}
@keyframes scrollBounce {
  0%, 100% { transform: rotate(45deg) translate(0, 0); opacity: 0.5; }
  50% { transform: rotate(45deg) translate(4px, 4px); opacity: 1; }
}

.home-hero__deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  animation: heroFloat 10s ease-in-out infinite;
}
.home-hero__deco--1 { width: 300px; height: 300px; top: -80px; right: 10%; background: radial-gradient(circle, rgba(20,184,166,0.1) 0%, transparent 70%); }
.home-hero__deco--2 { width: 200px; height: 200px; bottom: -40px; left: 5%; background: radial-gradient(circle, rgba(139,92,246,0.08) 0%, transparent 70%); animation-delay: -5s; }
.home-hero__deco--3 { width: 150px; height: 150px; top: 30%; left: 40%; background: radial-gradient(circle, rgba(59,130,246,0.06) 0%, transparent 70%); animation-delay: -3s; }
@keyframes heroFloat { 0%, 100% { transform: translateY(0) scale(1); } 50% { transform: translateY(-18px) scale(1.04); } }

/* Sections */
.home-section {
  padding: var(--s8) var(--s6);
}

/* Chart */
.chart-container {
  width: 100%;
  position: relative;
}

/* 热力图 */
.heatmap-container {
  width: 100%;
  overflow-x: auto;
}
.heatmap-legend {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
  margin-top: 8px;
}
.heatmap-legend__label {
  font-size: 11px;
  color: #6e7591;
}
.heatmap-legend__box {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

/* Learning path cards — 6 items: 3 per row */
.home-paths {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--s5);
}
.home-path-card {
  --accent: var(--primary);
  position: relative;
  padding: var(--s5);
  text-align: left;
  overflow: hidden;
}
/* 顶部强调线：悬停时从左展开 */
.home-path-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), color-mix(in srgb, var(--accent) 35%, transparent));
  transform: scaleX(0);
  transform-origin: left;
  transition: transform var(--dur) var(--ease);
}
.home-path-card:hover::before { transform: scaleX(1); }
.home-path-card:hover {
  border-color: color-mix(in srgb, var(--accent) 28%, var(--ink-100));
}
.home-path-card__step {
  position: absolute;
  top: var(--s4);
  right: var(--s4);
  font-size: 0.625rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  padding: 3px 10px;
  border-radius: var(--r-pill);
}
.home-path-card__icon {
  width: 54px;
  height: 54px;
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  margin-bottom: var(--s4);
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.home-path-card__icon svg { width: 27px; height: 27px; }
.home-path-card:hover .home-path-card__icon {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--accent) 28%, transparent);
}
.home-path-card__title {
  font-size: 1.0625rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--ink-900);
  margin-bottom: var(--s2);
}
.home-path-card__desc {
  font-size: 0.875rem;
  line-height: 1.7;
  color: var(--ink-500);
  margin-bottom: var(--s4);
}
.home-path-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.home-path-tag {
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 9%, transparent);
  padding: 4px 10px;
  border-radius: var(--r-pill);
  transition: background var(--dur) var(--ease);
}
.home-path-card:hover .home-path-tag {
  background: color-mix(in srgb, var(--accent) 15%, transparent);
}
/* 区块标签内联图标 */
.section-label--icon {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.section-label--icon svg {
  width: 14px;
  height: 14px;
}

.home-tip-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.home-tip-icon svg {
  width: 24px;
  height: 24px;
}

/* Hero buttons */
.hero-btns {
  display: flex;
  gap: var(--s3);
  margin-top: var(--s4);
  flex-wrap: wrap;
}
/* Desktop: show inline buttons, hide mobile copy */
.hero-btns--mobile { display: none; }
.hero-btn {
  height: 44px;
  padding: 0 24px;
  font-size: 0.9rem;
}

@media (max-width: 768px) {
  .home-hero {
    min-height: 100vh;
    min-height: 100dvh;
    padding: var(--s5) var(--s4) var(--s4);
    margin-top: -56px;
  }
  .hero-body {
    padding-top: 0;
    gap: var(--s3);
    justify-content: center;
  }
  .home-hero__inner {
    grid-template-columns: 1fr;
    gap: var(--s4);
  }
  .home-hero__code {
    font-size: 0.75rem;
    padding: var(--s4);
    border-radius: var(--r-lg);
  }
  .home-hero__code pre {
    font-size: 0.72rem !important;
    line-height: 1.6 !important;
  }

  .hero-title-text__cn {
    font-size: 1.6rem;
  }
  .hero-title-text__accent {
    font-size: 2rem;
  }
  .hero-title-logo {
    width: 36px;
    height: 36px;
    border-radius: 8px;
    margin-right: 8px;
  }
  .hero-title-en {
    font-size: 0.8rem;
    letter-spacing: 0.15em;
  }
  .hero-title-slogan {
    font-size: 0.8rem;
  }

  /* Hide floating tech tags on mobile to avoid overlap */
  .hero-float-tags {
    display: none;
  }
  /* Hide section label in hero on mobile */
  .hero-text-col .section-label {
    display: none;
  }

  /* Mobile: hide desktop buttons, show mobile buttons below code card */
  .hero-btns--desktop { display: none; }
  .hero-btns--mobile {
    display: flex;
    flex-direction: column;
    gap: var(--s2);
    margin-top: var(--s3);
  }
  .hero-btns--mobile .hero-btn {
    width: 100%;
    justify-content: center;
    height: 42px;
    padding: 0 18px;
    font-size: 0.85rem;
  }

  .home-paths { grid-template-columns: 1fr 1fr; gap: var(--s3); }
  .home-path-card {
    padding: var(--s4);
  }
  .home-path-card__icon {
    width: 46px;
    height: 46px;
    border-radius: 13px;
    margin-bottom: var(--s3);
  }
  .home-path-card__icon svg { width: 23px; height: 23px; }
  .home-path-card__step {
    font-size: 0.6rem;
    padding: 2px 6px;
    top: var(--s2);
    right: var(--s2);
  }

  .home-section {
    padding: var(--s6) var(--s4);
  }

  .home-tip-icon {
    width: 40px;
    height: 40px;
    font-size: 1.1rem;
  }

  .home-hero__deco--1 { width: 150px; height: 150px; }
  .home-hero__deco--2 { width: 100px; height: 100px; }
  .home-hero__deco--3 { width: 80px; height: 80px; }
}

@media (max-width: 480px) {
  .home-hero {
    padding: var(--s3) var(--s3) var(--s3);
  }
  .hero-title-text__cn {
    font-size: 1.25rem;
  }
  .hero-title-text__accent {
    font-size: 1.55rem;
  }
  .hero-title-logo {
    width: 28px;
    height: 28px;
    margin-right: 6px;
  }
  .hero-title-en { font-size: 0.7rem; }
  .hero-title-slogan { font-size: 0.7rem; }

  .home-paths { grid-template-columns: 1fr; }

  .home-section {
    padding: var(--s5) var(--s3);
  }
}
</style>
