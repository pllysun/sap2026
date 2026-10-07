<template>
  <section id="home-algorithms" ref="root" class="home-algorithms" aria-labelledby="home-algorithms-title">
    <header class="home-algorithm-heading">
      <div class="home-algorithm-heading-copy"><span class="home-algorithm-symbol"><UiIcon name="code" :size="22" /></span><div><h2 id="home-algorithms-title">算法题库</h2><p v-if="problemTotal !== null">{{ problemTotal }} 道公开题目<span>完整程序 / 核心函数</span></p></div></div>
      <div class="home-algorithm-heading-actions"><button type="button" :disabled="busy || !authenticated" aria-label="刷新首页算法题库" @click="loadAll"><UiIcon name="refresh" :size="14" />刷新</button><router-link to="/oj" class="home-algorithm-primary">进入题库<UiIcon name="chevron-right" :size="15" /></router-link></div>
    </header>
    <div class="home-algorithm-grid">
      <div class="home-algorithm-practice home-algorithm-card" :aria-busy="states.problems.status === 'loading'">
        <header class="home-algorithm-card-heading"><h3><UiIcon name="list" :size="16" />题目</h3><router-link to="/oj">全部题目<UiIcon name="chevron-right" :size="13" /></router-link></header>
        <div v-if="states.problems.status === 'error'" class="home-algorithm-empty" role="alert"><p>题目暂时未能加载</p><button type="button" @click="load('problems')">重新加载</button></div>
        <div v-else-if="!states.problems.data" class="home-algorithm-skeleton" role="status" aria-label="正在加载算法题目"><div v-for="n in 6" :key="n"><i /><span /><b /></div></div>
        <div v-else-if="!problems.length" class="home-algorithm-empty"><UiIcon name="code" :size="22" /><p>暂无公开题目</p></div>
        <div v-else class="home-algorithm-problems">
          <router-link v-for="problem in problems" :key="problem.id" :to="problemLink(problem.id)" class="home-algorithm-problem" :aria-label="'做题：' + problem.title">
            <OjProblemProgress :progress="problem.progress" /><span class="home-algorithm-problem-copy"><strong>{{ problem.title }}</strong><small>{{ (problem.tags || []).slice(0, 2).join(' · ') || problem.sourcePlatform }}</small></span><span class="home-algorithm-difficulty" :class="String(problem.difficulty).toLowerCase()">{{ levels[problem.difficulty] || '—' }}</span><UiIcon name="chevron-right" :size="14" />
          </router-link>
        </div>
        <div class="home-algorithm-set" :aria-busy="states.sets.status === 'loading'">
          <template v-if="practiceSet"><span class="home-algorithm-set-icon"><UiIcon name="layers" :size="19" /></span><div><span>练习题单</span><router-link :to="'/oj/sets/' + practiceSet.id"><strong>{{ practiceSet.name }}</strong></router-link><small>{{ practiceSet.total }} 道题<span v-if="practiceSet.joined"> · 已通过 {{ practiceSet.acCount }} / {{ practiceSet.total }}</span></small></div><router-link :to="'/oj/sets/' + practiceSet.id" class="home-algorithm-set-entry" :aria-label="'打开题单：' + practiceSet.name"><UiIcon name="chevron-right" :size="16" /></router-link></template>
          <template v-else><span class="home-algorithm-set-icon"><UiIcon name="layers" :size="19" /></span><div><strong>题单练习</strong><small>{{ states.sets.status === 'error' ? '题单暂时未能加载' : '按题单组合练习' }}</small></div><router-link to="/oj?tab=sets" class="home-algorithm-set-entry" aria-label="查看题单"><UiIcon name="chevron-right" :size="16" /></router-link></template>
        </div>
      </div>
      <div class="home-algorithm-sidebar">
        <section class="home-algorithm-card home-algorithm-ranking" aria-labelledby="home-algorithm-ranking-title" :aria-busy="states.ranking.status === 'loading'">
          <header class="home-algorithm-card-heading"><h3 id="home-algorithm-ranking-title"><UiIcon name="trophy" :size="16" />通过榜</h3><router-link to="/oj?tab=ranking">完整榜单<UiIcon name="chevron-right" :size="13" /></router-link></header>
          <div v-if="states.ranking.status === 'error'" class="home-algorithm-empty" role="alert"><p>排行榜暂时未能加载</p><button type="button" @click="load('ranking')">重新加载</button></div>
          <div v-else-if="!states.ranking.data" class="home-algorithm-skeleton compact" role="status" aria-label="正在加载通过榜"><div v-for="n in 3" :key="n"><i /><span /><b /></div></div>
          <div v-else-if="!ranking.length" class="home-algorithm-empty"><UiIcon name="trophy" :size="22" /><p>暂无上榜记录</p><router-link to="/oj">去做题</router-link></div>
          <ol v-else class="home-algorithm-ranks" aria-label="算法题库通过榜前三名">
            <li v-for="(person, index) in ranking" :key="person.studentId" :class="{ mine: isMe(person) }"><span class="home-algorithm-rank" :class="'place-' + (index + 1)">{{ index + 1 }}</span><div><strong>{{ person.name || person.nickname || '同学' }}<small v-if="person.name && person.nickname && person.name !== person.nickname">（{{ person.nickname }}）</small><span v-if="isMe(person)" class="home-algorithm-me">我</span></strong><span class="home-algorithm-account">{{ person.studentId }}</span></div><span class="home-algorithm-ac-count"><b>{{ person.acCount }}</b><small>/ {{ rankingTotal }}</small></span></li>
          </ol>
          <div v-if="myRank > 3" class="home-algorithm-my-rank"><span>我的排名 <b>{{ myRank }}</b></span><span>已通过 <strong>{{ myEntry.acCount }}</strong> / {{ rankingTotal }}</span></div>
          <p class="home-algorithm-ranking-note">按全部题目的独立通过数排序</p>
        </section>
        <section class="home-algorithm-card home-algorithm-history" aria-labelledby="home-algorithm-history-title" :aria-busy="states.history.status === 'loading'">
          <header class="home-algorithm-card-heading"><h3 id="home-algorithm-history-title"><UiIcon name="history" :size="16" />我的最近提交</h3><span>全部题目 · 最近 3 条</span></header>
          <div v-if="!user.token" class="home-algorithm-empty"><p>登录后查看个人记录</p><router-link to="/login">登录</router-link></div>
          <div v-else-if="states.history.status === 'error'" class="home-algorithm-empty" role="alert"><p>提交记录暂时未能加载</p><button type="button" @click="load('history')">重新加载</button></div>
          <div v-else-if="!states.history.data" class="home-algorithm-skeleton compact" role="status" aria-label="正在加载个人提交"><div v-for="n in 3" :key="n"><i /><span /><b /></div></div>
          <div v-else-if="!history.length" class="home-algorithm-empty"><UiIcon name="terminal" :size="22" /><p>还没有运行或提交记录</p><router-link :to="firstProblem ? problemLink(firstProblem.id) : '/oj'">开始做题<UiIcon name="chevron-right" :size="13" /></router-link></div>
          <div v-else class="home-algorithm-records"><router-link v-for="record in history" :key="record.id" :to="recordLink(record)" class="home-algorithm-record" :aria-label="'查看我的' + (kindNames[record.kind] || '提交') + '记录 ' + record.id"><div><strong>{{ problemTitles.get(String(record.problemId)) || ((languageNames[record.language] || record.language) + ' · ' + (modeNames[record.mode] || record.mode)) }}</strong><small>{{ kindNames[record.kind] }} · {{ shortDate(record.createdAt) }}</small></div><OjStatusBadge :status="record.status" :show-code="false" /><UiIcon name="chevron-right" :size="13" /></router-link></div>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import UiIcon from './UiIcon.vue'
import OjProblemProgress from './OjProblemProgress.vue'
import OjStatusBadge from './OjStatusBadge.vue'
import { kindNames, languageNames, modeNames, formatOjDate } from '@/utils/ojDictionaries'

const user = useUserStore(), root = ref(null), activated = ref(false)
const authenticated = computed(() => Boolean(user.token && user.user?.id))
const states = reactive(Object.fromEntries(['problems', 'ranking', 'history', 'sets'].map(key => [key, { status: 'idle', data: null }])))
const paths = { problems: '/api/oj/problems?page=1&size=6', ranking: '/api/oj/leaderboard', history: '/api/oj/submissions?page=1', sets: '/api/oj/sets?mode=PRACTICE&page=1' }
const levels = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
const busy = computed(() => Object.values(states).some(state => state.status === 'loading'))
const problems = computed(() => states.problems.data?.records || [])
const problemTotal = computed(() => states.problems.data ? Number(states.problems.data.total) : null)
const problemTitles = computed(() => new Map(problems.value.map(problem => [String(problem.id), problem.title])))
const firstProblem = computed(() => problems.value.find(problem => problem.progress?.state !== 'AC') || problems.value[0])
const ranking = computed(() => (states.ranking.data?.records || []).slice(0, 3))
const rankingTotal = computed(() => Number(states.ranking.data?.totals.total || 0))
const isMe = person => Boolean(user.user?.studentId) && String(person.studentId) === String(user.user.studentId)
const myRank = computed(() => (states.ranking.data?.records || []).findIndex(isMe) + 1)
const myEntry = computed(() => myRank.value ? states.ranking.data.records[myRank.value - 1] : null)
const history = computed(() => (states.history.data || []).filter(record => ['RUN', 'SUBMIT'].includes(record.kind)).slice(0, 3))
const practiceSet = computed(() => (states.sets.data?.records || []).find(set => set.mode === 'PRACTICE' && set.status === 'PUBLISHED'))
const problemLink = id => ({ path: '/oj/' + id, query: { from: '/home#home-algorithms' } })
const recordLink = record => ({ ...problemLink(record.problemId), query: { from: '/home#home-algorithms', submission: record.id } })
const shortDate = value => { const date = formatOjDate(value); return /^\d{4}-/.test(date) ? date.slice(5, 16).replace('-', '.') : date }
let observer, generation = 0, disposed = false
const controllers = new Map()
async function load(key) {
  if (!authenticated.value || disposed) return
  controllers.get(key)?.abort()
  const controller = new AbortController(), version = generation
  controllers.set(key, controller); states[key].status = 'loading'
  try {
    const { data } = await request.get(paths[key], { signal: controller.signal })
    if (key === 'history' ? !Array.isArray(data) : !Array.isArray(data?.records)) throw new Error('Invalid algorithm response')
    if (key === 'problems' && !Number.isFinite(Number(data.total))) throw new Error('Invalid problem count')
    if (key === 'ranking' && !Number.isFinite(Number(data.totals?.total))) throw new Error('Invalid leaderboard count')
    if (!disposed && generation === version && !controller.signal.aborted) { states[key].data = data; states[key].status = 'ready' }
  } catch (error) {
    if (!disposed && generation === version && !controller.signal.aborted) states[key].status = 'error'
  } finally { if (controllers.get(key) === controller) controllers.delete(key) }
}
function loadAll() { return Promise.allSettled(Object.keys(paths).map(load)) }
watch(() => [user.user?.id, user.token], () => {
  generation++; controllers.forEach(controller => controller.abort()); controllers.clear()
  Object.values(states).forEach(state => { state.status = 'idle'; state.data = null })
  if (activated.value && authenticated.value) loadAll()
})
onMounted(() => {
  const activate = () => { activated.value = true; observer?.disconnect(); if (authenticated.value) loadAll() }
  if (!window.IntersectionObserver) { activate(); return }
  observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) activate() }, { rootMargin: '200px 0px' })
  observer.observe(root.value)
})
onBeforeUnmount(() => { disposed = true; generation++; observer?.disconnect(); controllers.forEach(controller => controller.abort()); controllers.clear() })
</script>

<style scoped>
.home-algorithms{scroll-margin-top:90px;color:var(--home-ink,#193653)}
.home-algorithm-heading,.home-algorithm-heading-copy,.home-algorithm-heading-actions{display:flex;align-items:center;gap:18px}.home-algorithm-heading{justify-content:space-between;margin-bottom:25px;gap:20px}.home-algorithm-heading h2{margin:0;font-size:31px;letter-spacing:-.7px;font-weight:650}.home-algorithm-heading p{display:flex;gap:16px;margin:8px 0 0;color:#6d87a1;font-size:12px}.home-algorithm-heading p>span{color:#8a9eb2}.home-algorithm-symbol{width:50px;height:50px;display:grid;place-items:center;border:1px solid #cde1f4;border-radius:15px;background:linear-gradient(135deg,#e8f3ff,#f6faff);color:#397fbc;box-shadow:5px 5px 0 #edf5fd;transform:rotate(-5deg)}.home-algorithm-symbol>.ui-icon{transform:rotate(5deg)}
.home-algorithm-heading-actions{gap:12px;flex:none}.home-algorithm-heading-actions button{display:flex;align-items:center;gap:6px;padding:8px 10px;border:0;background:transparent;color:#718ca7;font-size:11px;cursor:pointer}.home-algorithm-heading-actions button:disabled{opacity:.5}.home-algorithm-primary{display:flex;align-items:center;gap:13px;padding:11px 15px;border:1px solid #2774c5;border-radius:9px;background:#2878cd;color:#fff;font-size:12px;box-shadow:0 4px 13px #2675c41c;transition:background .2s,transform .2s}.home-algorithm-primary:hover{background:#2469b7;transform:translateY(-1px)}
.home-algorithm-grid{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(0,1fr);gap:22px;align-items:start}.home-algorithm-card{min-width:0;border:1px solid #d8e6f4;border-radius:16px;overflow:hidden;background:#fff;box-shadow:0 6px 26px #2d669009}.home-algorithm-card-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:18px 23px;background:linear-gradient(115deg,#f4f9ff,#fff);border-bottom:1px solid #e3edf7}.home-algorithm-card-heading h3{display:flex;align-items:center;gap:9px;margin:0;color:#315978;font-size:14px;font-weight:600}.home-algorithm-card-heading h3>.ui-icon{color:#6f95bb}.home-algorithm-card-heading>a{display:flex;align-items:center;gap:5px;font-size:11px;color:#6d8daa}.home-algorithm-card-heading>a:hover{color:#286eb0}.home-algorithm-card-heading>span{font-size:10px;color:#94a7b9}
.home-algorithm-problem{display:flex;align-items:center;gap:15px;min-height:73px;padding:15px 23px;border-bottom:1px solid #eaf0f7;transition:background .2s}.home-algorithm-problem:last-child{border-bottom:0}.home-algorithm-problem:hover{background:#f5faff}.home-algorithm-problem-copy{min-width:0;flex:1}.home-algorithm-problem-copy strong{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px;color:#315572;font-weight:550}.home-algorithm-problem-copy small{display:block;margin-top:5px;font-size:10px;color:#92a5b7}.home-algorithm-problem>.ui-icon{color:#8cabca;transition:transform .2s}.home-algorithm-problem:hover>.ui-icon{transform:translateX(3px)}.home-algorithm-problem :deep(.oj-progress-symbol){width:28px;height:28px}.home-algorithm-problem :deep(.oj-progress-glyph){width:17px;height:17px}.home-algorithm-problem :deep(.oj-progress-ac){background:#f2f6fb;border-color:#dae5f1;color:#258b72}.home-algorithm-problem :deep(.oj-progress-spinner){width:13px;height:13px}.home-algorithm-difficulty{flex:none;padding:4px 8px;font-size:10px;border-radius:5px;color:#7d92a7;background:#f1f5fa}.home-algorithm-difficulty.easy{color:#258b72;background:#f2f7f6}.home-algorithm-difficulty.medium{color:#a38243;background:#faf6ed}.home-algorithm-difficulty.hard{color:#b66570;background:#fcf1f4}
.home-algorithm-set{display:flex;align-items:center;gap:14px;margin:8px 16px 16px;padding:18px;border:1px solid #dce9f7;border-radius:10px;background:linear-gradient(110deg,#eef6ff,#f9fcff);min-height:99px}.home-algorithm-set-icon{display:grid;place-items:center;flex:none;width:36px;height:40px;color:#5d8dba;background:#ffffff9c;border:1px solid #d3e5f6;border-radius:9px}.home-algorithm-set>div{flex:1;min-width:0}.home-algorithm-set>div>span{display:block;margin-bottom:5px;font-size:10px;color:#7595b4}.home-algorithm-set strong{font-size:14px;font-weight:550;color:#325b80;display:block;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.home-algorithm-set small{display:block;margin-top:6px;font-size:10px;color:#7e99b4;line-height:1.6}.home-algorithm-set-entry{display:grid;place-items:center;flex:none;width:30px;height:30px;border:1px solid #cfdff1;background:#ffffffbf;border-radius:8px;color:#7196ba;transition:background .2s}.home-algorithm-set-entry:hover{background:#fff}
.home-algorithm-sidebar{display:grid;gap:18px}.home-algorithm-ranks{list-style:none;padding:0;margin:0}.home-algorithm-ranks>li{display:flex;align-items:center;gap:13px;padding:16px 23px;border-bottom:1px solid #eaf0f7;min-height:71px}.home-algorithm-ranks>li.mine{background:#f6faff}.home-algorithm-ranks>li>div{flex:1;min-width:0}.home-algorithm-ranks strong{display:flex;align-items:center;gap:3px;font-size:12px;font-weight:550;color:#365b7d;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.home-algorithm-ranks strong>small{font-size:10px;font-weight:400;color:#7b97b1;overflow:hidden;text-overflow:ellipsis}.home-algorithm-rank{display:grid;place-items:center;flex:none;width:26px;height:29px;border:1px solid #dee7f0;border-radius:7px;background:#f2f6fb;color:#8097ae;font:12px ui-monospace,monospace}.home-algorithm-rank.place-1{border-color:#e8dfc9;color:#9d7b3b;background:#fcf8ef}.home-algorithm-rank.place-2{border-color:#d2e3f5;color:#638bac;background:#f0f6fd}.home-algorithm-rank.place-3{border-color:#e0deee;color:#8b86ad;background:#f6f5fc}.home-algorithm-account{display:block;margin-top:5px;font-size:10px;color:#98aabd;letter-spacing:.3px}.home-algorithm-me{flex:none;padding:1px 4px;background:#e7f0ff;color:#6b93c1;border-radius:3px;margin-left:5px;font-size:9px;font-weight:400}.home-algorithm-ac-count{display:flex;align-items:baseline;gap:5px;flex:none}.home-algorithm-ac-count b{font-size:20px;font-weight:500;color:#3d76aa;font-variant-numeric:tabular-nums}.home-algorithm-ac-count small{font-size:10px;color:#a0b2c5}.home-algorithm-my-rank{display:flex;justify-content:space-between;gap:12px;padding:12px 23px;background:#f7faff;font-size:11px;color:#7895b0;border-bottom:1px solid #e6eef7}.home-algorithm-my-rank b,.home-algorithm-my-rank strong{font-weight:550;color:#5180a9}.home-algorithm-ranking-note{margin:0;padding:12px 23px;color:#91a5b8;font-size:10px;line-height:1.5}
.home-algorithm-record{display:flex;align-items:center;gap:12px;min-height:72px;padding:15px 23px;border-bottom:1px solid #eaf0f7;transition:background .2s}.home-algorithm-record:last-child{border-bottom:0}.home-algorithm-record:hover{background:#f5faff}.home-algorithm-record>div{flex:1;min-width:0}.home-algorithm-record strong{display:block;color:#486b8c;font-size:12px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.home-algorithm-record small{display:block;font-size:10px;color:#91a5b9;margin-top:7px}.home-algorithm-record>.ui-icon{color:#92acca;flex:none}.home-algorithm-record :deep(.oj-status-badge){font-size:10px;padding:4px 6px;gap:5px}.home-algorithm-record :deep(.oj-status-emblem){width:15px;height:15px;border-radius:4px}.home-algorithm-record :deep(.oj-status-emblem svg){width:10px;height:10px}
.home-algorithm-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;min-height:205px;padding:30px 22px;text-align:center;color:#8ba2b9}.home-algorithm-empty p{font-size:12px;margin:0;line-height:1.8}.home-algorithm-empty button,.home-algorithm-empty>a{display:inline-flex;align-items:center;gap:5px;padding:7px 11px;border:1px solid #d7e5f4;border-radius:6px;background:#f6faff;color:#5b89b4;font-size:11px;cursor:pointer}.home-algorithm-skeleton{padding:0 23px}.home-algorithm-skeleton>div{display:flex;align-items:center;gap:15px;height:73px;border-bottom:1px solid #edf2f8}.home-algorithm-skeleton i{width:28px;height:28px;background:#edf3fa;border-radius:9px;flex:none}.home-algorithm-skeleton span{height:10px;max-width:180px;flex:1;background:#edf3fa;border-radius:4px}.home-algorithm-skeleton b{width:35px;height:18px;margin-left:auto;background:#edf3fa;border-radius:4px}.home-algorithm-skeleton>div:last-child{border:0}
.home-algorithms :is(a,button):focus-visible{outline:2px solid #4388c5;outline-offset:3px}
@media(max-width:1000px){.home-algorithm-grid{grid-template-columns:minmax(0,1.15fr) minmax(0,1fr);gap:16px}.home-algorithm-card-heading,.home-algorithm-problem,.home-algorithm-ranks>li,.home-algorithm-record{padding-inline:16px}.home-algorithm-problem{gap:10px}.home-algorithm-record{gap:7px}.home-algorithm-heading p>span{display:none}}
@media(max-width:760px){.home-algorithm-heading{align-items:flex-start;gap:12px;margin-bottom:20px}.home-algorithm-heading-copy{gap:12px}.home-algorithm-heading h2{font-size:24px}.home-algorithm-heading p{font-size:10px;margin-top:6px}.home-algorithm-symbol{width:39px;height:39px;border-radius:11px}.home-algorithm-symbol>.ui-icon{width:19px;height:19px}.home-algorithm-heading-actions{gap:5px}.home-algorithm-heading-actions button{width:30px;height:34px;overflow:hidden;font-size:0;gap:0;padding:7px}.home-algorithm-primary{padding:9px 10px;font-size:11px;gap:5px}.home-algorithm-grid{grid-template-columns:minmax(0,1fr);gap:16px}.home-algorithm-sidebar{grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.home-algorithm-heading-actions button>.ui-icon{width:14px;height:14px}.home-algorithm-card-heading h3{font-size:12px;gap:6px}.home-algorithm-card-heading{padding-block:15px;gap:8px}.home-algorithm-problem{min-height:69px}.home-algorithm-problem-copy strong{font-size:13px}.home-algorithm-record :deep(.oj-status-badge){font-size:9px}.home-algorithm-ranks strong>small{display:none}.home-algorithm-card-heading>span{display:none}.home-algorithm-record>.ui-icon{display:none}.home-algorithm-rank{width:22px}.home-algorithm-ac-count b{font-size:17px}.home-algorithm-ranks>li{gap:8px}.home-algorithm-my-rank{padding:12px 16px;flex-wrap:wrap}.home-algorithm-ranking-note{padding-inline:16px}}
@media(max-width:560px){.home-algorithm-sidebar{grid-template-columns:minmax(0,1fr)}.home-algorithm-ranks strong>small{display:inline}.home-algorithm-card-heading>span{display:block}.home-algorithm-card{border-radius:12px}.home-algorithm-record>.ui-icon{display:block}.home-algorithm-heading-actions{align-self:center}.home-algorithm-set{padding:15px;margin-inline:12px;gap:11px}.home-algorithm-heading-copy{gap:10px}}
@media(prefers-reduced-motion:reduce){.home-algorithms *,.home-algorithms :deep(*){animation:none!important;transition:none!important}}
</style>
