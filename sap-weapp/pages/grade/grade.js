/**
 * 成绩页：分段切换（成绩 / 考试 / 评教）。
 * 防风控策略：进页面 / 切 tab 只读本地缓存；仅「无任何缓存」时首次自动同步一次；
 * 刷新统一靠用户点同步条的「同步」按钮。
 */
const api = require('../../utils/api')
const store = require('../../utils/store')
const term = require('../../utils/term')
const color = require('../../utils/color')
const { computeGradeStats } = require('../../utils/gradeStats')

// ---- 内联小工具（按规范不外拆，避免与其它代理冲突） ----
function pad2(n) { return n < 10 ? '0' + n : '' + n }
/** 时间戳 → "MM-dd HH:mm"。 */
function fmtTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}
/** 数字格式化（保留 n 位，空值占位 '-'）。 */
function fx(v, n) {
  if (v == null || v === '' || isNaN(v)) return '-'
  return Number(v).toFixed(n == null ? 1 : n)
}

Page({
  data: {
    tab: 0,                 // 0 成绩 / 1 考试 / 2 评教
    account: '',
    ready: false,           // 已完成首次账号判定
    hasAccount: false,

    // ---- 成绩 ----
    gradeSync: { at: '', state: 'idle', err: '' }, // state: idle/syncing/error
    subTab: 0,              // 成绩内二级：0 列表 / 1 分析
    stats: null,            // computeGradeStats 结果（已格式化部分）
    bandRows: [],           // 分数段（含宽度百分比）
    termGroups: [],         // 按学期分组的成绩列表

    // ---- 考试 ----
    examSync: { at: '', state: 'idle', err: '' },
    terms: [],              // TermDto[]
    examTerm: '',           // 当前选中学期
    examTermLabel: '',
    exams: [],
    showTermSheet: false,

    // ---- 评教 ----
    evalSync: { at: '', state: 'idle', err: '' },
    evalTerm: '',           // 当前评教学期（可空，后端给默认）
    evalTerms: [],          // 可切学期 string[]
    evalTermLabel: '全部',
    tasks: [],
    hasUnEvaluated: false,
    showEvalTermSheet: false,
    evalResults: [],        // 一键评教结果弹层
    showEvalResult: false,
    autoLoading: false,
  },

  onLoad() { this.boot() },

  // 账号可能在别的页被切换，onShow 重读并加载缓存
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 1 })
    const acc = store.getActiveAccount()
    if (acc !== this.data.account) { this.boot() }
  },

  /** 初始化：判定账号 + 加载当前 tab 缓存。 */
  boot() {
    const account = store.getActiveAccount()
    const hasAccount = !!account
    this.setData({ account, hasAccount, ready: true })
    if (!hasAccount) return
    this.loadTab(this.data.tab)
  },

  /** 切换分段。 */
  switchTab(e) {
    const tab = Number(e.currentTarget.dataset.tab)
    if (tab === this.data.tab) return
    this.setData({ tab })
    if (this.data.hasAccount) this.loadTab(tab)
  },

  /** 成绩内二级分段：列表 / 分析。 */
  switchSub(e) {
    const sub = Number(e.currentTarget.dataset.sub)
    if (sub !== this.data.subTab) this.setData({ subTab: sub })
  },

  /** 按 tab 读缓存（无缓存则首次自动同步一次）。 */
  loadTab(tab) {
    if (tab === 0) this.loadGrades()
    else if (tab === 1) this.loadTermsThenExams()
    else this.loadEval()
  },

  // ========================= 成绩 tab =========================
  loadGrades() {
    const cache = store.readCache('grades', this.data.account)
    if (cache && cache.items) {
      this.renderGrades(cache.items, cache.syncedAt)
    } else {
      this.syncGrades() // 无缓存首次自动同步
    }
  },

  renderGrades(items, syncedAt) {
    const s = computeGradeStats(items || [])
    // 概览数据格式化
    const view = {
      gpa: fx(s.gpa, 2),
      avgScore: fx(s.avgScore, 1),
      totalCourses: s.totalCourses,
      totalCredit: fx(s.totalCredit, 1),
      earnedCredit: fx(s.earnedCredit, 1),
      maxScore: s.maxScore == null ? '-' : fx(s.maxScore, 0),
      minScore: s.minScore == null ? '-' : fx(s.minScore, 0),
      excellentRate: fx(s.excellentRate, 0),
      passRate: fx(s.passRate, 0),
      failCount: s.failCount,
      hasData: s.hasData,
    }
    // 分数段：宽度按 count/max 比例
    const maxBand = s.bands.reduce((m, b) => Math.max(m, b.count), 0) || 1
    const bandRows = s.bands.map((b) => ({
      label: b.label,
      key: b.key,
      count: b.count,
      pct: Math.round((b.count / maxBand) * 100),
      danger: b.key === 'FAIL',
    }))
    // 成绩列表按学期分组（降序：最近学期在前），每门课带挂科标记
    const map = {}
    ;(items || []).forEach((g) => {
      const k = (g.term && g.term.trim()) ? g.term : '其它'
      ;(map[k] = map[k] || []).push(g)
    })
    const termGroups = Object.keys(map).sort().reverse().map((t) => ({
      term: t,
      label: term.isTerm(t) ? term.label(t) : t,
      courses: map[t].map((g) => ({
        name: g.courseName,
        // 子行：学分 X · 绩点 Y · 属性（对齐 App GradeRow）
        sub: [
          g.credit ? '学分 ' + g.credit : null,
          (g.gradePoint && String(g.gradePoint).trim()) ? '绩点 ' + g.gradePoint : null,
          g.courseAttr || null,
        ].filter(Boolean).join('  ·  '),
        score: g.score == null || g.score === '' ? '-' : String(g.score),
        fail: isFail(g.score),
      })),
    }))
    this.setData({
      stats: view,
      bandRows,
      termGroups,
      'gradeSync.at': fmtTime(syncedAt),
      'gradeSync.state': 'idle',
      'gradeSync.err': '',
    })
  },

  syncGrades() {
    if (this.data.gradeSync.state === 'syncing') return
    this.setData({ 'gradeSync.state': 'syncing', 'gradeSync.err': '' })
    api.grades(this.data.account).then((res) => {
      if (res.code === 200) {
        const items = res.data || []
        store.writeCache('grades', this.data.account, items)
        this.renderGrades(items, Date.now())
        wx.showToast({ title: '已同步', icon: 'success' })
      } else {
        this.fail('gradeSync', res.message)
      }
    }).catch((e) => this.fail('gradeSync', e && e.message))
  },

  // ========================= 考试 tab =========================
  // 先有学期再拉考试
  loadTermsThenExams() {
    const tc = store.readCache('terms', this.data.account)
    if (tc && tc.items && tc.items.length) {
      this.applyTerms(tc.items)
      this.loadExams()
    } else {
      this.syncTerms(true) // 无学期缓存：拉学期后自动拉考试
    }
  },

  applyTerms(terms) {
    let cur = this.data.examTerm
    if (!cur) {
      const def = terms.find((t) => t.current) || terms[0]
      cur = def ? def.value : ''
    }
    const found = terms.find((t) => t.value === cur)
    this.setData({ terms, examTerm: cur, examTermLabel: found ? found.label : cur })
  },

  syncTerms(thenExams) {
    api.terms(this.data.account).then((res) => {
      if (res.code === 200) {
        const items = res.data || []
        store.writeCache('terms', this.data.account, items)
        this.applyTerms(items)
        if (thenExams) this.loadExams()
      } else if (thenExams) {
        this.fail('examSync', res.message)
      }
    }).catch((e) => { if (thenExams) this.fail('examSync', e && e.message) })
  },

  loadExams() {
    if (!this.data.examTerm) { this.setData({ exams: [] }); return }
    const key = this.data.account + '|' + this.data.examTerm
    const cache = store.readCache('exams', key)
    if (cache && cache.items) {
      this.renderExams(cache.items, cache.syncedAt)
    } else {
      this.syncExams()
    }
  },

  renderExams(items, syncedAt) {
    // 每场考试按课程名取稳定色点（对齐 App ExamCard）
    const exams = (items || []).map((e) => Object.assign({}, e, {
      dot: color.courseColorOf(e.courseName).container,
    }))
    this.setData({
      exams,
      'examSync.at': fmtTime(syncedAt),
      'examSync.state': 'idle',
      'examSync.err': '',
    })
  },

  syncExams() {
    if (this.data.examSync.state === 'syncing') return
    if (!this.data.examTerm) return
    this.setData({ 'examSync.state': 'syncing', 'examSync.err': '' })
    const key = this.data.account + '|' + this.data.examTerm
    api.exams(this.data.account, this.data.examTerm).then((res) => {
      if (res.code === 200) {
        const items = res.data || []
        store.writeCache('exams', key, items)
        this.renderExams(items, Date.now())
        wx.showToast({ title: '已同步', icon: 'success' })
      } else {
        this.fail('examSync', res.message)
      }
    }).catch((e) => this.fail('examSync', e && e.message))
  },

  openTermSheet() { this.setData({ showTermSheet: true }) },
  closeTermSheet() { this.setData({ showTermSheet: false }) },
  pickExamTerm(e) {
    const v = e.currentTarget.dataset.t
    const found = this.data.terms.find((t) => t.value === v)
    this.setData({ examTerm: v, examTermLabel: found ? found.label : v, showTermSheet: false })
    this.loadExams() // 切学期只读缓存
  },

  // ========================= 评教 tab =========================
  loadEval() {
    const key = this.data.account + '|' + (this.data.evalTerm || '')
    const cache = store.readCache('eval', key)
    if (cache && cache.items) {
      this.renderEval(cache.items, cache.syncedAt)
    } else {
      this.syncEval()
    }
  },

  renderEval(dto, syncedAt) {
    const d = dto || {}
    const tasks = (d.tasks || []).map((t) => ({
      teacherNo: t.teacherNo,
      teacher: t.teacher,
      college: t.college,
      typeName: t.typeName,
      evaluated: t.evaluated,
      score: t.score,
      statusText: t.evaluated ? (t.score != null && t.score !== '' ? String(t.score) : '已评') : '未评',
    }))
    const hasUn = tasks.some((t) => t.evaluated === false)
    this.setData({
      tasks,
      hasUnEvaluated: hasUn,
      evalTerm: d.term || this.data.evalTerm || '',
      evalTerms: d.terms || [],
      evalTermLabel: d.term ? (term.isTerm(d.term) ? term.label(d.term) : d.term) : '全部',
      'evalSync.at': fmtTime(syncedAt),
      'evalSync.state': 'idle',
      'evalSync.err': '',
    })
  },

  syncEval() {
    if (this.data.evalSync.state === 'syncing') return
    this.setData({ 'evalSync.state': 'syncing', 'evalSync.err': '' })
    const t = this.data.evalTerm || null
    api.evalList(this.data.account, t).then((res) => {
      if (res.code === 200) {
        const dto = res.data || {}
        const key = this.data.account + '|' + (dto.term || t || '')
        store.writeCache('eval', key, dto)
        this.renderEval(dto, Date.now())
        wx.showToast({ title: '已同步', icon: 'success' })
      } else {
        this.fail('evalSync', res.message)
      }
    }).catch((e) => this.fail('evalSync', e && e.message))
  },

  openEvalTermSheet() { this.setData({ showEvalTermSheet: true }) },
  closeEvalTermSheet() { this.setData({ showEvalTermSheet: false }) },
  pickEvalTerm(e) {
    const v = e.currentTarget.dataset.t || ''
    this.setData({ evalTerm: v, showEvalTermSheet: false })
    this.loadEval() // 切学期只读缓存
  },

  /** 一键自动评教。 */
  autoEval() {
    if (this.data.autoLoading) return
    wx.showModal({
      title: '一键自动评教',
      content: '将对所有未评教任务自动提交评价，确定继续？',
      success: (r) => { if (r.confirm) this.doAutoEval() },
    })
  },

  doAutoEval() {
    this.setData({ autoLoading: true })
    wx.showLoading({ title: '评教中…', mask: true })
    api.evalAuto({ account: this.data.account, term: this.data.evalTerm || null, comment: '' }).then((res) => {
      wx.hideLoading()
      this.setData({ autoLoading: false })
      if (res.code === 200) {
        const results = (res.data || []).map((x) => ({
          teacher: x.teacher,
          typeName: x.typeName,
          success: x.success,
          message: x.message || (x.success ? '成功' : '失败'),
          score: x.score,
        }))
        this.setData({ evalResults: results, showEvalResult: true })
        this.syncEval() // 重新同步评教列表
      } else {
        wx.showToast({ title: res.message || '评教失败', icon: 'none' })
      }
    }).catch((e) => {
      wx.hideLoading()
      this.setData({ autoLoading: false })
      wx.showToast({ title: (e && e.message) || '评教失败', icon: 'none' })
    })
  },

  closeEvalResult() { this.setData({ showEvalResult: false }) },

  // ========================= 公共 =========================
  // 同步条「同步」按钮统一入口
  onSync() {
    const tab = this.data.tab
    if (tab === 0) this.syncGrades()
    else if (tab === 1) { this.syncTerms(false); this.syncExams() }
    else this.syncEval()
  },

  fail(field, msg) {
    this.setData({ [field + '.state']: 'error', [field + '.err']: msg || '同步失败' })
    wx.showToast({ title: msg || '同步失败', icon: 'none' })
  },

  noop() {},
})

// 借用 gradeStats 的挂科判定（避免重复 require 大模块逻辑，内联轻量版）
function isFail(score) {
  if (score == null || score === '') return false
  const v = parseFloat(String(score).trim())
  if (!isNaN(v)) return v < 60
  const s = String(score)
  return s.indexOf('不及格') >= 0 || s.indexOf('不合格') >= 0
}
