<template>
  <div ref="homeRoot" class="association-home">
    <section class="home-intro home-shell" aria-labelledby="home-title">
      <div class="intro-copy">
        <p class="intro-university"><span aria-hidden="true"></span>中南林业科技大学</p>
        <div class="intro-title"><h1 id="home-title"><span>软件</span><span>协会</span></h1><svg class="intro-title__mark" viewBox="0 0 72 72" fill="none" aria-hidden="true"><rect x="8" y="8" width="42" height="42" rx="12"/><rect x="24" y="24" width="40" height="40" rx="12"/><path d="m34 36-7 8 7 8m13-16 7 8-7 8"/></svg></div>
        <p class="intro-description">参加学习小组，练习算法题，查阅技术笔记与协会活动。</p>
        <nav class="intro-pathways" aria-label="开始学习">
          <router-link to="/oj" class="intro-pathway intro-pathway--practice">
            <span class="intro-pathway__mark"><UiIcon name="code" :size="18" /></span>
            <span class="intro-pathway__copy"><strong>算法题库</strong><span>从一道题开始练习</span></span>
            <UiIcon name="chevron-right" class="intro-pathway__arrow" :size="14" />
          </router-link>
          <router-link to="/study" class="intro-pathway">
            <span class="intro-pathway__mark"><UiIcon name="users" :size="18" /></span>
            <span class="intro-pathway__copy"><strong>学习小组</strong><span>和伙伴一起完成任务</span></span>
            <UiIcon name="chevron-right" class="intro-pathway__arrow" :size="14" />
          </router-link>
        </nav>
        <div class="intro-resource-links">
          <router-link to="/notes"><UiIcon name="book-open" :size="14" />查看学习笔记<UiIcon name="chevron-right" :size="12" /></router-link>
          <router-link v-if="isGuest && joinEnabled" to="/join"><UiIcon name="user-plus" :size="14" />加入软件协会<UiIcon name="chevron-right" :size="12" /></router-link>
          <router-link v-else to="/activities"><UiIcon name="calendar" :size="14" />查看协会活动<UiIcon name="chevron-right" :size="12" /></router-link>
        </div>
        <HomeAppDownload class="intro-app-download" />
        <div class="intro-updates" :aria-busy="pending.activities || pending.notes" aria-label="最近发布">
          <p class="intro-updates__heading"><UiIcon name="history" :size="13" />最近发布<span aria-hidden="true"></span></p>
          <div class="intro-updates__grid">
          <template v-for="item in introUpdates" :key="item.type">
            <div v-if="pending[item.kind === 'activity' ? 'activities' : 'notes']" class="intro-update-skeleton" aria-hidden="true"><i></i><span></span></div>
            <router-link v-else :to="item.to" class="intro-update" :class="`intro-update--${item.kind}`">
              <span class="intro-update__media" aria-hidden="true">
                <img v-if="item.image && !brokenImages[`intro-${item.id}`]" :src="item.image" alt="" @error="brokenImages[`intro-${item.id}`] = true">
                <svg v-else-if="item.kind === 'activity'" viewBox="0 0 24 24" fill="none"><path d="M5 6h14v14H5zM8 3v6m8-6v6M5 11h14m-10 4h2m2 0h2"/></svg>
                <svg v-else viewBox="0 0 24 24" fill="none"><path d="M5 4h11l3 3v13H5zM15 4v4h4M8 12h8m-8 4h5"/></svg>
              </span>
              <span class="intro-update__meta"><span class="intro-update__type">{{ item.type === '活动' ? '协会活动' : '学习笔记' }}</span><time v-if="item.date" :datetime="String(item.date).slice(0, 10)">{{ formatDate(item.date) }}</time></span>
              <span class="intro-update__title">{{ item.title }}</span>
              <span class="intro-update__icon"><UiIcon name="chevron-right" :size="14" /></span>
            </router-link>
          </template>
          </div>
        </div>
      </div>
      <div class="creative-stage" aria-label="切换语言，查看软件协会的 Hello World 示例">
        <div class="stage-halo" aria-hidden="true"></div><div class="stage-pixels" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
        <HeroCodeWindow />
        <div class="logo-stamp" aria-hidden="true"><img src="/logo.png" alt=""></div>
        <router-link to="/message-board" class="stage-note"><UiIcon name="message-square" :size="20" /><strong>留言板</strong></router-link>
      </div>
      <a class="intro-scroll" href="#association-life" aria-label="查看协会活动"><UiIcon name="layers" :size="18" /></a>
    </section>
    <div class="motto-ribbon"><div class="home-shell"><span class="ribbon-symbol" aria-hidden="true">✳</span><p>万维网连接五大洲，<br class="mobile-break">二进制写尽天下事</p><span class="ribbon-label">OUR MOTTO / 我们的口号</span></div></div>

    <HomeAlgorithmSection class="home-shell home-section reveal" />

    <section class="home-shell home-section reveal" aria-labelledby="explore-title">
      <div class="section-heading"><div><p class="eyebrow">01 / FIND YOUR WAY</p><h2 id="explore-title">不止写代码，<br>也一起拓宽可能。</h2></div><p>把学习落在实践里，把经验留给下一位。<br>这就是我们相聚的意义。</p></div>
      <div class="explore-grid">
        <router-link to="/study" class="explore-card explore-card--green"><span class="card-index">01</span><div class="explore-mark" aria-hidden="true">{<span>一起</span>}</div><div><h3>结伴学习</h3><p>加入学习小组，围绕任务交流、提交作品，在反馈中一步步进阶。</p></div><span class="explore-link">找到学习伙伴 <UiIcon name="users" :size="20" /></span></router-link>
        <router-link to="/notes" class="explore-card explore-card--cream"><span class="card-index">02</span><div class="paper-stack" aria-hidden="true"><i></i><i></i><i>笔记<br>也是路标。</i></div><div><h3>分享所学</h3><p>把踩过的坑、解决的方法和新想法写下来，让知识在交流中积累。</p></div><span class="explore-link">翻开软协笔记 <UiIcon name="book-open" :size="20" /></span></router-link>
        <router-link to="/activities" class="explore-card explore-card--blue"><span class="card-index">03</span><div class="connection-mark" aria-hidden="true"><i></i><i></i><i></i><b>＋</b></div><div><h3>走到一起</h3><p>在协会活动中认识新朋友，交流技术与日常，让线上连接延伸到校园。</p></div><span class="explore-link">看看协会活动 <UiIcon name="calendar" :size="20" /></span></router-link>
      </div>
    </section>

    <section id="association-life" class="life-section home-section reveal" aria-labelledby="life-title"><div class="home-shell">
      <div class="section-heading"><div><p class="eyebrow">02 / LIFE AT SAP</p><h2 id="life-title">发生在我们之间。</h2></div><router-link to="/activities" class="home-text-link">全部活动 <UiIcon name="calendar" /></router-link></div>
      <div v-if="pending.activities" class="home-placeholder" aria-live="polite">正在翻开协会相册…</div>
      <div v-else-if="errors.activities" class="home-placeholder">活动暂时未能加载 <button @click="loadResource('activities', true)">重新加载</button></div>
      <div v-else-if="activities.length" class="activity-editorial">
        <router-link v-for="(activity, index) in activities" :key="activity.id" to="/activities" class="activity-story" :class="{ 'activity-story--featured': index === 0 }">
          <div class="story-image"><img v-if="activity.images?.[0]?.imageUrl && !brokenImages[activity.id]" :src="activity.images[0].imageUrl" :alt="activity.title" loading="lazy" @error="brokenImages[activity.id] = true"><div v-else class="story-art" aria-hidden="true"><span>相聚的<br>每一刻。</span><UiIcon name="image" :size="54" /></div><span class="story-label">{{ activity.grade }} · 协会活动</span></div>
          <div class="story-copy"><time>{{ formatDate(activity.createdAt) }}</time><h3>{{ activity.title }}</h3><p v-if="activity.content">{{ activity.content }}</p><UiIcon name="calendar" class="story-icon" :size="20" /></div>
        </router-link>
      </div><div v-else class="home-placeholder">新的相聚正在酝酿，活动发布后会出现在这里。</div>
    </div></section>

    <section class="home-shell home-section reveal" data-load="study" aria-labelledby="learning-title">
      <div class="section-heading"><div><p class="eyebrow">03 / KEEP LEARNING</p><h2 id="learning-title">下一步，<br class="mobile-break">从这里开始。</h2></div><router-link to="/notes" class="home-text-link">笔记资料库 <UiIcon name="book-open" /></router-link></div>
      <div class="learning-layout">
        <div class="learning-guide"><span class="mini-label">学习方向 / 找到自己的起点</span>
          <div class="direction-tabs" role="tablist" aria-label="选择学习方向"><button v-for="(direction, i) in directions" :id="`direction-${i}`" :key="direction.title" role="tab" :aria-selected="activeDirection === i" aria-controls="direction-panel" :tabindex="activeDirection === i ? 0 : -1" :class="{ selected: activeDirection === i }" @click="activeDirection = i" @keydown="changeDirection($event, i)">{{ direction.label }}</button></div>
          <div id="direction-panel" role="tabpanel" :aria-labelledby="`direction-${activeDirection}`" tabindex="0"><Transition name="direction" mode="out-in"><div :key="activeDirection"><span class="direction-number">0{{ activeDirection + 1 }}</span><h3>{{ directions[activeDirection].title }}</h3><p>{{ directions[activeDirection].description }}</p><ol class="learning-steps"><li v-for="step in directions[activeDirection].steps" :key="step"><span></span>{{ step }}</li></ol></div></Transition></div>
          <router-link to="/study" class="home-text-link">在学习小组里实践 <UiIcon name="users" /></router-link>
        </div>
        <div class="notes-feed"><div class="feed-heading"><span class="mini-label">最近更新的笔记</span><span class="feed-dot"></span></div>
          <p v-if="pending.notes" class="home-placeholder">正在整理笔记…</p><p v-else-if="errors.notes" class="home-placeholder">笔记暂时未能加载 <button @click="loadResource('notes', true)">重试</button></p>
          <router-link v-for="(note, index) in notes" :key="note.id" :to="isGuest ? '/notes' : `/notes/${note.id}`" class="note-entry"><span class="note-entry__index">{{ String(index + 1).padStart(2, '0') }}</span><div><div class="note-entry__meta">{{ formatDate(note.createdAt) }} <span>· {{ note.readMinutes || 1 }} 分钟阅读</span></div><h3>{{ note.title }}</h3><p v-if="note.description">{{ note.description }}</p></div><UiIcon name="book-open" class="note-entry__icon" :size="22" /></router-link>
          <p v-if="!pending.notes && !errors.notes && !notes.length" class="home-placeholder">第一份知识积累，从一次分享开始。</p><p v-if="isGuest" class="member-hint">笔记目录开放浏览，正文供正式会员学习。</p>
          <router-link v-if="latestStudy" to="/study" class="study-spotlight"><span class="mini-label">{{ Number(latestStudy.status) === 1 ? '正在进行的学习活动' : '最近的学习活动' }}</span><h3>{{ latestStudy.title || `${latestStudy.grade}级学习活动` }}</h3><div><span>{{ latestStudy.grade }}级 · {{ Number(latestStudy.status) === 1 ? '和伙伴一起继续' : '看看大家的学习记录' }}</span><UiIcon name="users" :size="20" /></div></router-link>
        </div>
      </div>
    </section>

    <section class="community-section home-section reveal" data-load="stats calendar" :aria-busy="pending.stats || pending.calendar" aria-labelledby="community-title"><div class="home-shell">
      <div class="section-heading"><div><p class="eyebrow">04 / GROWING TOGETHER</p><h2 id="community-title">每一份参与，都有回响。</h2></div><p>数字背后，是一起学习和创造的人。<br>以下数据来自协会平台的真实记录。</p></div>
      <p v-if="errors.stats" class="data-notice" role="status">统计暂时不可用。<button @click="loadResource('stats', true)">重新加载</button></p>
      <div class="community-metrics"><div v-for="metric in metrics" :key="metric.key"><strong>{{ stats ? number(animatedStats[metric.key] ?? stats[metric.key]) : '—' }}</strong><span>{{ metric.label }}</span><small>{{ metric.note }}</small></div></div>
      <div class="community-charts">
        <div class="grade-chart"><div class="chart-heading"><h3>历届成员档案</h3><span>按届归档 · 同届去重</span></div><div v-if="stats?.memberArchiveByTerm?.length" class="grade-bars"><div v-for="grade in stats.memberArchiveByTerm" :key="grade.grade" class="grade-bar"><span>{{ grade.grade }}届</span><div><i :style="{ width: `${Math.max(2, Number(grade.count) / maxGradeCount * 100)}%` }"></i></div><b>{{ number(grade.count) }}</b></div></div><p v-else class="chart-empty">{{ pending.stats || !loaded.stats ? '正在读取成员档案…' : '暂无可展示的成员档案' }}</p><p class="chart-caption">同一成员可能被收录于多届；各届人数不能相加作为会员总数。</p></div>
        <div class="activity-heatmap"><div class="chart-heading"><h3>持续发生的连接</h3><span>近半年平台活跃记录</span></div><div v-if="loaded.calendar && !errors.calendar" class="heatmap-grid" role="img" aria-label="最近半年每日平台活跃记录热力图"><span v-for="day in heatmapDays" :key="day.date" :class="`heat-level-${day.level}`" :title="`${day.date} · ${number(day.count)} 次活动记录`"></span></div><p v-else class="chart-empty">{{ errors.calendar ? '活跃记录暂时不可用' : '正在读取活跃记录…' }} <button v-if="errors.calendar" @click="loadResource('calendar', true)">重试</button></p><div class="heatmap-legend"><span>{{ heatmapRange }}</span><div>少 <i v-for="level in 5" :key="level" :class="`heat-level-${level - 1}`"></i> 多</div></div><p class="chart-caption">这里记录平台的使用足迹，不代表在线人数。</p></div>
      </div>
    </div></section>

    <section v-if="!loaded.members || pending.members || errors.members || members.length" class="home-shell home-section members-section reveal" data-load="members" :aria-busy="pending.members" aria-labelledby="members-title"><div class="section-heading"><div><p class="eyebrow">05 / PEOPLE OF SAP</p><h2 id="members-title">同行的人，<br class="mobile-break">各有光芒。</h2></div><div v-if="members.length" class="member-navigation"><span>{{ memberPage + 1 }} / {{ memberPages }}</span><button aria-label="上一组优秀成员" :disabled="memberPage === 0" @click="memberPage--"><UiIcon name="chevron-left" /></button><button aria-label="下一组优秀成员" :disabled="memberPage + 1 >= memberPages" @click="memberPage++"><UiIcon name="chevron-right" /></button></div></div><div v-if="pending.members || !loaded.members" class="home-deferred-skeleton" aria-label="正在加载荣誉成员"><i v-for="i in 8" :key="i"></i></div><p v-else-if="errors.members" class="home-placeholder">荣誉成员暂时未能加载 <button @click="loadResource('members', true)">重试</button></p><div v-else class="people-grid"><article v-for="person in visibleMembers" :key="person.id" class="person-card"><span class="person-initial">{{ (person.name || '协')[0] }}</span><div><h3>{{ person.name || '软协成员' }}</h3><span>{{ person.grade }}级 <template v-if="person.destination">· {{ person.destination }}</template></span></div><p>{{ person.destinationDetail || person.bio || person.major || '在热爱的方向，继续探索。' }}</p></article></div></section>

    <section class="home-shell join-section reveal"><div class="join-panel"><div><p class="eyebrow">YOUR NEXT CHAPTER</p><h2>带上好奇心。<br>剩下的，一起探索。</h2></div><div class="join-panel__right"><p>刚接触编程，也可以从这里开始。<br>先了解我们的学习与活动，再找到自己的节奏。</p><router-link v-if="isGuest && joinEnabled" to="/join" class="home-button home-button--dark">了解入会 <UiIcon name="user-plus" /></router-link><router-link v-else to="/message-board" class="home-button home-button--dark">和大家打个招呼 <UiIcon name="message-square" /></router-link></div></div>
      <div class="home-faq"><details><summary>没有编程基础，可以参加吗？<span>＋</span></summary><p>可以先从基础语法和学习笔记开始，结合学习小组的任务练习。遇到问题时，把尝试过程写清楚，再和伙伴一起讨论。</p></details><details><summary>注册账号就代表加入协会吗？<span>＋</span></summary><p>注册账号与成为正式会员是两件事。入会开放时，可通过「了解入会」提交申请；完成入会审核后，账号会获得相应的会员权限。</p></details><details><summary>从哪里了解最新活动？<span>＋</span></summary><p>在「软协活动」查看协会记录与发布的内容，学习任务请前往「学习小组」。有疑问可以到留言板交流，或通过页脚的联系方式联系协会。</p></details></div>
    </section>
  </div>
</template>

<script setup>
import { createActivityScale } from '../../../shared/activityIntensity.mjs'
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import HeroCodeWindow from '@/components/HeroCodeWindow.vue'
import UiIcon from '@/components/UiIcon.vue'
import HomeAlgorithmSection from '@/components/HomeAlgorithmSection.vue'
import HomeAppDownload from '@/components/HomeAppDownload.vue'
const userStore = useUserStore()
const isGuest = computed(() => !userStore.roles.some(role => [0, 1, 2, 3].includes(Number(role))))
const homeRoot = ref(null), stats = ref(null), activities = ref([]), notes = ref([]), members = ref([]), calendar = ref([]), studyActivities = ref([])
const introUpdates = computed(() => {
  const activity = activities.value[0], note = notes.value[0]
  return [
    { type: '活动', kind: 'activity', id: activity?.id, image: activity?.images?.[0]?.imageUrl, title: activity?.title || (errors.activities ? '查看协会活动' : '暂无活动发布'), date: activity?.createdAt, to: '/activities' },
    { type: '笔记', kind: 'note', title: note?.title || (errors.notes ? '查看学习笔记' : '暂无笔记发布'), date: note?.createdAt, to: note && !isGuest.value ? `/notes/${note.id}` : '/notes' },
  ]
})
const latestStudy = computed(() => {
  const sorted = [...studyActivities.value].sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')) || Number(b.id) - Number(a.id))
  return sorted.find(item => Number(item.status) === 1) || sorted[0]
})
const joinEnabled = ref(false), activeDirection = ref(0), memberPage = ref(0)
const pending = reactive({ activities: true, notes: true }), loaded = reactive({})
const errors = reactive({}), brokenImages = reactive({}), animatedStats = reactive({})
const memberPageSize = 8
const memberPages = computed(() => Math.ceil(members.value.length / memberPageSize))
const visibleMembers = computed(() => members.value.slice(memberPage.value * memberPageSize, (memberPage.value + 1) * memberPageSize))
const metrics = [
  { key: 'memberCount', label: '会员账号', note: '含往届，按会员及以上权限去重' },
  { key: 'registeredCount', label: '平台注册用户', note: '包含游客，不等于会员人数' },
  { key: 'activityCount', label: '协会活动', note: '已记录的协会活动' },
  { key: 'noteCount', label: '共享学习笔记', note: '来自协会的知识积累' },
]
const directions = [
  { label: '编程起步', title: '先写出第一个小程序。', description: '不急着掌握所有技术，从理解问题、写出代码、验证结果开始。', steps: ['搭建开发环境，熟悉编辑器', '学习 C 语言基础与程序调试', '用 Markdown 记录过程与收获'] },
  { label: '算法思维', title: '让解决问题更有章法。', description: '从具体问题出发，理解数据如何组织、程序如何高效运行。', steps: ['掌握数组、链表与基础数据结构', '练习查找、排序与递归', '复盘解题思路，而不只是答案'] },
  { label: '应用实践', title: '把想法做成能用的作品。', description: '从一个小需求开始，在实际开发中连接界面、数据与协作。', steps: ['尝试 Web 界面或移动端应用', '了解接口、数据库与版本管理', '完成一个项目，演示并收集反馈'] },
]
function changeDirection(event, index) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  activeDirection.value = event.key === 'Home' ? 0 : event.key === 'End' ? 2 : (index + (event.key === 'ArrowRight' ? 1 : 2)) % 3
  document.getElementById(`direction-${activeDirection.value}`)?.focus()
}
const number = value => new Intl.NumberFormat('zh-CN').format(Number(value) || 0)
const formatDate = value => value ? String(value).slice(0, 10).replaceAll('-', '.') : '协会记录'
const maxGradeCount = computed(() => Math.max(1, ...(stats.value?.memberArchiveByTerm || []).map(row => Number(row.count))))
function dateKey(date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
const heatmapDays = computed(() => {
  const map = new Map(calendar.value.map(row => [String(row[0]).slice(0, 10), Number(row[1]) || 0]))
  const start = new Date(); start.setDate(start.getDate() - 181)
  const days = Array.from({ length: 182 }, (_, i) => { const day = new Date(start); day.setDate(day.getDate() + i); const date = dateKey(day); return { date, count: map.get(date) || 0 } })
  const intensity = createActivityScale(days.map(day => day.count))
  return days.map(day => ({ ...day, level: intensity(day.count) }))
})
const heatmapRange = computed(() => `${heatmapDays.value[0].date.slice(5).replace('-', '.')} — ${heatmapDays.value.at(-1).date.slice(5).replace('-', '.')}`)
let observer, dataObserver, countFrame, disposed = false
const generations = {}
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches
function animateStats() {
  cancelAnimationFrame(countFrame)
  const start = performance.now()
  const frame = now => { const progress = reducedMotion() ? 1 : Math.min(1, (now - start) / 1000); metrics.forEach(metric => animatedStats[metric.key] = Math.round(Number(stats.value[metric.key] || 0) * (1 - (1 - progress) ** 3))); if (progress < 1) countFrame = requestAnimationFrame(frame) }
  countFrame = requestAnimationFrame(frame)
}
function observeSections() {
  observer?.disconnect()
  if (reducedMotion() || !window.IntersectionObserver) return
  observer = new IntersectionObserver(entries => entries.forEach(entry => { if (entry.isIntersecting) { entry.target.classList.add('is-visible'); observer.unobserve(entry.target) } }), { threshold: 0.08 })
  homeRoot.value?.querySelectorAll('.reveal').forEach(section => { if (!section.classList.contains('is-visible')) { section.classList.add('reveal-ready'); observer.observe(section) } })
}
const resources = {
  stats: ['/api/home/overview', data => { stats.value = data; animateStats() }],
  activities: ['/api/activity/page?current=1&size=3', data => activities.value = data.records || []],
  notes: ['/api/note/list?current=1&size=3', data => notes.value = data.records || []],
  study: ['/api/study/activity/list', data => studyActivities.value = data || []],
  members: ['/api/outstanding-member/all', data => { members.value = data || []; memberPage.value = 0 }],
  calendar: ['/api/log/public/calendar', data => calendar.value = data || []],
  join: ['/api/join/status', data => joinEnabled.value = data === true],
}
async function loadResource(key, retry = false) {
  if (disposed || (pending[key] && generations[key]) || (loaded[key] && !retry)) return
  const generation = (generations[key] || 0) + 1
  generations[key] = generation; pending[key] = true; errors[key] = false
  try {
    const [url, apply] = resources[key]
    const result = await request.get(url)
    if (!disposed && generations[key] === generation) { apply(result.data); loaded[key] = true }
  } catch { if (!disposed && generations[key] === generation) { errors[key] = true; loaded[key] = true } }
  finally { if (!disposed && generations[key] === generation) { pending[key] = false; await nextTick(); observeSections() } }
}
function observeData() {
  const sections = homeRoot.value?.querySelectorAll('[data-load]') || []
  const load = section => section.dataset.load.split(' ').forEach(key => loadResource(key))
  if (!window.IntersectionObserver) { sections.forEach(load); return }
  dataObserver = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) { load(entry.target); dataObserver.unobserve(entry.target) }
  }), { rootMargin: '240px 0px', threshold: 0 })
  sections.forEach(section => dataObserver.observe(section))
}
onMounted(() => {
  observeSections(); observeData()
  // First-screen cards resolve independently; lower-page data waits until its section approaches the viewport.
  ;['activities', 'notes', 'join'].forEach(key => loadResource(key))
})
onUnmounted(() => { disposed = true; observer?.disconnect(); dataObserver?.disconnect(); cancelAnimationFrame(countFrame) })
</script>

<style scoped src="../assets/styles/home.css"></style>
