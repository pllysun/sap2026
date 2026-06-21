const app = getApp()
const api = require('../../utils/api')
const store = require('../../utils/store')
const sched = require('../../utils/schedule')
const week = require('../../utils/week')
const periods = require('../../utils/periods')
const term = require('../../utils/term')

const WEEK_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

Page({
  data: {
    accent: '#2E9BEF',
    ready: false,
    hasAccount: true,
    account: '',
    accounts: [],
    term: '',
    terms: [],
    selectedWeek: 1,
    currentWeek: null,
    totalWeeks: 20,
    days: [],
    nodeRows: [],
    totalHeight: 0,
    weeks: [],          // [{week, cards}] 供 swiper
    cardScale: 1,
    swiperHeight: 0,
    loading: false,
    error: '',
    // 弹层
    showWeekPicker: false,
    showTermPicker: false,
    showAccountPicker: false,
    detail: null,       // {course}
  },

  onLoad() {
    const win = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    const rpx2px = win.windowWidth / 750
    const headerPx = Math.round((96 + 104) * rpx2px) // 顶栏 + 星期行
    this.setData({
      accent: app.globalData.accent,
      swiperHeight: win.windowHeight - headerPx,
    })
  },

  onShow() {
    if (!app.hasToken()) { wx.reLaunch({ url: '/pages/login/login' }); return }
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 0 })
    this.init()
  },

  async init() {
    // 取已绑定教务学号
    let accounts = []
    try {
      const r = await api.jwAccounts()
      if (r.code === 200 && Array.isArray(r.data)) accounts = r.data.map((a) => a.account)
    } catch (e) { /* 离线则用本地 */ }
    if (!accounts.length) {
      const root = store.getRoot()
      accounts = Object.keys(root.accounts || {})
    }
    if (!accounts.length) { this.setData({ ready: true, hasAccount: false }); return }

    let active = store.getActiveAccount()
    if (!active || accounts.indexOf(active) < 0) { active = accounts[0]; store.setActiveAccount(active) }

    this.setData({ ready: true, hasAccount: true, account: active, accounts })

    // 决定学期：用本地缓存的 activeTerm，否则拉取默认课表
    const ad = store.getAccountData(active)
    if (ad.activeTerm && ad.termData[ad.activeTerm]) {
      this.rebuild(active, ad.activeTerm)
    } else {
      this.fetchSchedule(active, null, true)
    }
  },

  /** 从后端拉课表（term 为空=默认当前学期）。 */
  async fetchSchedule(account, termValue, setActive) {
    this.setData({ loading: true, error: '' })
    try {
      const r = await api.schedule(account, termValue)
      if (r.code !== 200 || !r.data) throw new Error(r.message || '获取课表失败')
      const data = r.data
      const t = data.term || termValue
      store.saveTermSchedule(account, t, data)
      if (setActive || !store.getAccountData(account).activeTerm) {
        const ad = store.getAccountData(account)
        ad.activeTerm = t
        store.saveAccountData(account, ad)
      }
      this.setData({ loading: false })
      this.rebuild(account, t)
    } catch (e) {
      this.setData({ loading: false, error: e.message || '获取课表失败' })
      wx.showToast({ title: e.message || '获取失败', icon: 'none' })
    }
  },

  /** 用本地数据重建网格。 */
  rebuild(account, t) {
    const ad = store.getAccountData(account)
    const td = ad.termData[t]
    if (!td) { this.fetchSchedule(account, t, true); return }
    const settings = ad.settings
    const count = sched.dailyPeriods(settings)
    const rh = sched.rowHeightRpx(settings)
    const tops = sched.nodeTops(count, rh)

    // 时间列
    const nodeRows = []
    for (let i = 1; i <= count; i++) {
      const p = periods.period(i) || { start: '', end: '' }
      nodeRows.push({ node: i, top: tops[i - 1], height: rh, start: p.start, end: p.end })
    }

    // 学期 / 周
    const startIso = sched.effectiveStart(ad, t)
    const cw = week.currentWeek(startIso)
    const totalWeeks = (settings.totalWeeks >= 1 && settings.totalWeeks <= 30) ? settings.totalWeeks : 20
    let selWeek = this.data.term === t ? this.data.selectedWeek : (cw || 1)
    if (selWeek > totalWeeks) selWeek = totalWeeks

    // 预构建所有周（按天分组，便于 WXML 渲染）
    const weeks = []
    let remarks = []
    for (let w = 1; w <= totalWeeks; w++) {
      const built = sched.buildWeek(ad, t, w)
      const byDay = {}
      built.cards.forEach((c, idx) => {
        c.idx = idx
        ;(byDay[c.day] = byDay[c.day] || []).push(c)
      })
      weeks.push({ week: w, cards: built.cards, byDay })
      if (w === selWeek) remarks = built.remarks
    }

    const terms = (ad.terms || []).map((x) => ({ value: x.value, label: x.label || term.label(x.value), current: x.current }))

    this.setData({
      account, term: t, terms,
      selectedWeek: selWeek, currentWeek: cw, totalWeeks,
      nodeRows, totalHeight: tops[count],
      weeks, remarks,
      cardScale: sched.cardScale(settings),
      showWeekend: !!settings.showWeekend,
    }, () => this.refreshDays())
  },

  /** 计算星期表头（含日期、今天高亮）。 */
  refreshDays() {
    const ad = store.getAccountData(this.data.account)
    const startIso = sched.effectiveStart(ad, this.data.term)
    const dates = week.datesOfWeek(startIso, this.data.selectedWeek)
    const showWeekend = !!(ad.settings && ad.settings.showWeekend)
    const n = showWeekend ? 7 : 5
    const today = new Date(); const todayKey = today.getFullYear() + '-' + today.getMonth() + '-' + today.getDate()
    const days = []
    for (let i = 0; i < n; i++) {
      const d = dates ? dates[i] : null
      days.push({
        day: i + 1,
        name: WEEK_NAMES[i],
        dateLabel: d ? (d.getMonth() + 1) + '/' + d.getDate() : '',
        isToday: d ? (d.getFullYear() + '-' + d.getMonth() + '-' + d.getDate()) === todayKey : false,
      })
    }
    this.setData({ days })
  },

  onSwiperChange(e) {
    const w = e.detail.current + 1
    if (w === this.data.selectedWeek) return
    const ad = store.getAccountData(this.data.account)
    const built = sched.buildWeek(ad, this.data.term, w)
    this.setData({ selectedWeek: w, remarks: built.remarks }, () => this.refreshDays())
  },

  // ---- 周选择 ----
  openWeekPicker() { this.setData({ showWeekPicker: true }) },
  closeWeekPicker() { this.setData({ showWeekPicker: false }) },
  pickWeek(e) {
    const w = +e.currentTarget.dataset.w
    this.setData({ selectedWeek: w, showWeekPicker: false }, () => this.refreshDays())
  },
  backToCurrentWeek() {
    if (this.data.currentWeek) this.setData({ selectedWeek: this.data.currentWeek, showWeekPicker: false }, () => this.refreshDays())
    else wx.showToast({ title: '未设置开学日期', icon: 'none' })
  },

  // ---- 学期选择 ----
  openTermPicker() { if (this.data.terms.length) this.setData({ showTermPicker: true }) },
  closeTermPicker() { this.setData({ showTermPicker: false }) },
  pickTerm(e) {
    const t = e.currentTarget.dataset.t
    this.setData({ showTermPicker: false })
    if (t === this.data.term) return
    const ad = store.getAccountData(this.data.account)
    ad.activeTerm = t
    store.saveAccountData(this.data.account, ad)
    if (ad.termData[t]) this.rebuild(this.data.account, t)
    else this.fetchSchedule(this.data.account, t, true)
  },

  // ---- 账号选择 ----
  openAccountPicker() { if (this.data.accounts.length > 1) this.setData({ showAccountPicker: true }) },
  closeAccountPicker() { this.setData({ showAccountPicker: false }) },
  pickAccount(e) {
    const a = e.currentTarget.dataset.a
    this.setData({ showAccountPicker: false })
    if (a === this.data.account) return
    store.setActiveAccount(a)
    this.init()
  },

  // ---- 课卡点击 ----
  tapCard(e) {
    const w = +e.currentTarget.dataset.w
    const i = +e.currentTarget.dataset.i
    const wk = this.data.weeks.find((x) => x.week === w)
    if (!wk) return
    const course = wk.cards[i]
    this.setData({ detail: course })
  },
  closeDetail() { this.setData({ detail: null }) },
  editDetail() {
    const c = this.data.detail
    if (!c || !c.isCustom) return
    this.setData({ detail: null })
    wx.navigateTo({ url: `/pages/editCourse/editCourse?account=${this.data.account}&term=${this.data.term}&id=${c.customId}` })
  },
  async deleteDetail() {
    const c = this.data.detail
    if (!c || !c.isCustom) return
    const res = await wx.showModal({ title: '删除课程', content: `确定删除「${c.name}」？`, confirmColor: '#E2504A' })
    if (!res.confirm) return
    const ad = store.getAccountData(this.data.account)
    const list = (ad.custom[this.data.term] || []).filter((x) => x.id !== c.customId)
    ad.custom[this.data.term] = list
    store.saveAccountData(this.data.account, ad)
    this.setData({ detail: null })
    this.rebuild(this.data.account, this.data.term)
  },

  // ---- 空格点击：新增课 ----
  tapEmpty(e) {
    const day = e.currentTarget.dataset.day
    const node = e.currentTarget.dataset.node
    wx.navigateTo({ url: `/pages/editCourse/editCourse?account=${this.data.account}&term=${this.data.term}&day=${day}&start=${node}` })
  },
  addCourse() {
    wx.navigateTo({ url: `/pages/editCourse/editCourse?account=${this.data.account}&term=${this.data.term}` })
  },

  /** 点备注课 → 跳编辑页反填课名/教师/周次，用户补星期+节次+地点后即成自建课。 */
  tapRemark(e) {
    const i = +e.currentTarget.dataset.i
    const r = this.data.remarks[i]
    if (!r) return
    const q = `account=${this.data.account}&term=${this.data.term}`
      + `&name=${encodeURIComponent(r.name || '')}`
      + `&teacher=${encodeURIComponent(r.teacher || '')}`
      + `&weeks=${encodeURIComponent(r.weeks || '')}`
    wx.navigateTo({ url: `/pages/editCourse/editCourse?${q}` })
  },

  noop() {},

  goBind() { wx.navigateTo({ url: '/pages/bind/bind' }) },
  goSettings() { wx.navigateTo({ url: '/pages/settings/settings?from=schedule' }) },

  async onSync() {
    if (this.data.loading) return
    await this.fetchSchedule(this.data.account, this.data.term, true)
    wx.showToast({ title: '已同步', icon: 'success' })
  },
})
