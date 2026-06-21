/**
 * 设置页：课表显示设置（写入激活账号 AccountData.settings）+ 主题色 + 隐私/关于/退出。
 * 设置改完直接 store.saveAccountData，课表页 onShow 会重读重建，无需主动通知。
 */
const store = require('../../utils/store')
const term = require('../../utils/term')
const { STORAGE } = require('../../config')

const app = getApp()

// 预设主题色（对齐 App ThemeState.PRESETS）
const ACCENTS = [
  { color: '#2E9BEF', name: '天蓝' },
  { color: '#3B5BDB', name: '宝蓝' },
  { color: '#12B886', name: '青绿' },
  { color: '#20C997', name: '薄荷' },
  { color: '#FF922B', name: '活力橙' },
  { color: '#FF6B6B', name: '珊瑚红' },
  { color: '#E64980', name: '玫红' },
  { color: '#7C5CFC', name: '葡萄紫' },
  { color: '#9775FA', name: '薰衣草' },
  { color: '#2B2F38', name: '墨黑' },
]

// 各选择器的候选项工厂（label 用于展示，value 写回 settings）
function rangeOpts(arr, suffix) {
  return arr.map((v) => ({ label: suffix ? `${v}${suffix}` : String(v), value: v }))
}
// 「默认」项（value=0，对应 settings 的 0=默认）
const DEFAULT_OPT = { label: '默认', value: 0 }

const PICKERS = {
  periodsPerDay: {
    title: '一天总节数',
    options: rangeOpts([8, 9, 10, 11, 12, 13, 14, 15, 16], ' 节'),
  },
  rowHeight: {
    title: '课格高度',
    options: [DEFAULT_OPT].concat(rangeOpts([40, 48, 56, 64, 72, 80, 88])),
  },
  cardTextScale: {
    title: '课程卡字号',
    options: [DEFAULT_OPT].concat(rangeOpts([80, 90, 100, 110, 125, 140], ' %')),
  },
  totalWeeks: {
    title: '总周数',
    options: rangeOpts([16, 18, 20, 22, 24, 26], ' 周'),
  },
}

// 隐私与关于文本（用数组渲染，控制 JS 行数）。h=true 为小标题。
const PRIVACY = [
  { h: true, t: '一、我们收集什么' },
  { t: '为提供课表/成绩/考试/评教等服务，本应用会收集你的教务系统学号与登录密码。' },
  { h: true, t: '二、如何存储' },
  { t: '你的教务密码经 AES 加密后存储于本应用后端服务器，不以明文保存，仅用于向学校教务系统发起代抓取请求。' },
  { h: true, t: '三、用途' },
  { t: '收集的凭据仅用于代你登录学校教务系统，抓取课表、成绩、考试安排与评教信息并展示给你本人。' },
  { h: true, t: '四、第三方' },
  { t: '数据来源为你所在学校的教务系统（第三方）。除该教务系统外，我们不会将你的凭据提供给任何其他第三方。' },
  { h: true, t: '五、你的权利' },
  { t: '你可随时在「我的」中解绑教务账号，解绑后服务器会删除对应的加密凭据与缓存数据。' },
]

const ABOUT = [
  { k: '应用名', v: '软协课表' },
  { k: '版本', v: '1.0.0' },
  { k: '开发', v: '中南林业科技大学软件协会' },
]
const ABOUT_DESC = '软协课表致力于为同学提供简洁清爽的课表、成绩与考试查询体验。'

Page({
  data: {
    view: '',          // '' 主视图 / 'privacy' / 'about'
    accent: '#2E9BEF',
    accents: ACCENTS,
    hasAccount: false,
    account: '',
    settings: null,    // ad.settings 快照
    activeTerm: '',
    activeTermLabel: '', // 学期友好名（如 "2025-2026 第2学期"）
    startDate: '',     // 当前激活学期的有效开学日期（展示用）
    // 展示用文案
    rowHeightLabel: '默认',
    cardScaleLabel: '默认',
    privacy: PRIVACY,
    about: ABOUT,
    aboutDesc: ABOUT_DESC,
    // 通用选择器
    picker: null,      // { title, options:[{label,value}], field }
  },

  onLoad() {
    this.setData({ accent: app.globalData.accent || '#2E9BEF' })
  },

  onShow() {
    this.refresh()
  },

  /** 重新读取激活账号设置，刷新展示。 */
  refresh() {
    const account = store.getActiveAccount()
    if (!account) {
      this.setData({ hasAccount: false, account: '', settings: null })
      return
    }
    const ad = store.getAccountData(account)
    const s = ad.settings
    const t = ad.activeTerm || ''
    const startDate = (s.startOverride && s.startOverride[t])
      || (ad.termData[t] && ad.termData[t].semesterStartDate)
      || ''
    this.setData({
      hasAccount: true,
      account,
      settings: s,
      activeTerm: t,
      activeTermLabel: t ? term.label(t) : '',
      startDate: startDate || '',
      rowHeightLabel: s.rowHeight ? String(s.rowHeight) : '默认',
      cardScaleLabel: s.cardTextScale ? `${s.cardTextScale} %` : '默认',
    })
  },

  /** 读取-修改-保存 设置的通用方法。mut(settings) 内原地改字段。 */
  _mutate(mut) {
    const account = store.getActiveAccount()
    if (!account) return
    const ad = store.getAccountData(account)
    mut(ad.settings)
    store.saveAccountData(account, ad)
    this.refresh()
  },

  // ---- 开关 ----
  onToggleWeekend(e) {
    const v = e.detail.value
    this._mutate((s) => { s.showWeekend = v })
  },
  onToggleNonWeek(e) {
    const v = e.detail.value
    this._mutate((s) => { s.showNonWeek = v })
  },

  // ---- 通用选择器弹层 ----
  openPicker(e) {
    const field = e.currentTarget.dataset.field
    const def = PICKERS[field]
    if (!def) return
    this.setData({ picker: { title: def.title, options: def.options, field } })
  },
  closePicker() { this.setData({ picker: null }) },
  noop() {},

  /** 选中某项 → 按 field 写回 settings。 */
  pickOption(e) {
    const value = e.currentTarget.dataset.value
    const field = this.data.picker && this.data.picker.field
    if (!field) return
    this._mutate((s) => { s[field] = value })
    this.setData({ picker: null })
  },

  // ---- 开学日期 ----
  onPickStartDate(e) {
    const iso = e.detail.value
    const t = this.data.activeTerm
    if (!t) {
      wx.showToast({ title: '当前无激活学期', icon: 'none' })
      return
    }
    this._mutate((s) => {
      if (!s.startOverride) s.startOverride = {}
      s.startOverride[t] = iso
    })
  },

  // ---- 主题色 ----
  pickAccent(e) {
    const color = e.currentTarget.dataset.color
    if (!color) return
    wx.setStorageSync(STORAGE.THEME_ACCENT, color)
    app.globalData.accent = color
    this.setData({ accent: color })
    wx.showToast({ title: '已应用，部分页面重进生效', icon: 'none' })
  },

  // ---- 内嵌视图切换 ----
  showPrivacy() { this.setData({ view: 'privacy' }) },
  showAbout() { this.setData({ view: 'about' }) },
  backToMain() { this.setData({ view: '' }) },

  // ---- 退出登录 ----
  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      confirmColor: '#E2504A',
      success: (res) => {
        if (!res.confirm) return
        store.clearAuth()
        wx.reLaunch({ url: '/pages/login/login' })
      },
    })
  },
})
