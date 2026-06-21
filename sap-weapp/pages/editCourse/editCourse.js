// 编辑 / 新增自建课程页
// 自建课存储于 store.getAccountData(account).custom[term]（数组）。
const store = require('../../utils/store')
const color = require('../../utils/color')
const week = require('../../utils/week')

const DAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

Page({
  data: {
    account: '',
    term: '',
    id: '',            // 有值=编辑模式
    // 表单字段
    name: '',
    location: '',
    teacher: '',
    day: 0,            // 1-7，0=未选
    startNode: 0,
    endNode: 0,
    weeksSel: {},      // { [week:number]: true } 已选周次集合
    colorIndex: 0,
    // 渲染辅助
    palette: color.CoursePalette,
    dayNames: DAY_NAMES,
    totalWeeks: 20,
    maxNode: 10,
    nodeList: [],      // [1..nodeMax]
    weekList: [],      // [1..totalWeeks]
  },

  onLoad(options) {
    const account = options.account || ''
    const term = options.term || ''
    const id = options.id || ''
    const ad = store.getAccountData(account)
    const totalWeeks = (ad.settings && ad.settings.totalWeeks) || 20
    const maxNode = (ad.settings && ad.settings.periodsPerDay) || 10

    // 默认值（新增模式预填）
    let name = ''
    let location = ''
    let teacher = ''
    let day = parseInt(options.day, 10) || 0
    let startNode = parseInt(options.start, 10) || 0
    let endNode = startNode
    let weeksSel = {}
    let colorIndex = 0

    if (id) {
      // 编辑模式：从已有自建课预填
      const list = ad.custom[term] || []
      const cur = list.find((x) => x.id === id)
      if (cur) {
        name = cur.name || ''
        location = cur.location || ''
        teacher = cur.teacher || ''
        day = cur.day || 0
        startNode = cur.startNode || 0
        endNode = cur.endNode || 0
        colorIndex = cur.colorIndex || 0
        ;(cur.weeks || []).forEach((w) => { weeksSel[w] = true })
      }
    } else {
      // 新增模式：从备注课反填课名/教师/周次（用户再补星期+节次+地点）
      if (options.name) name = decodeURIComponent(options.name)
      if (options.teacher) teacher = decodeURIComponent(options.teacher)
      if (options.weeks) {
        week.parseWeeks(decodeURIComponent(options.weeks)).forEach((w) => { weeksSel[w] = true })
      }
    }

    // chip 上限：取设置节数与当前结束节的较大值，保证已有课能正常显示
    const nodeMax = Math.max(maxNode, endNode)
    const nodeList = []
    for (let i = 1; i <= nodeMax; i++) nodeList.push(i)
    const weekList = []
    for (let i = 1; i <= totalWeeks; i++) weekList.push(i)

    wx.setNavigationBarTitle({ title: id ? '编辑课程' : '新增课程' })

    this.setData({
      account, term, id,
      name, location, teacher, day, startNode, endNode, weeksSel, colorIndex,
      totalWeeks, maxNode, nodeList, weekList,
    })
  },

  // ---- 文本输入 ----
  onName(e) { this.setData({ name: e.detail.value }) },
  onLocation(e) { this.setData({ location: e.detail.value }) },
  onTeacher(e) { this.setData({ teacher: e.detail.value }) },

  // ---- 星期单选 ----
  pickDay(e) { this.setData({ day: +e.currentTarget.dataset.day }) },

  // ---- 开始 / 结束节单选 ----
  pickStart(e) {
    const start = +e.currentTarget.dataset.node
    // 结束节须 ≥ 开始节，否则自动对齐
    const endNode = this.data.endNode < start ? start : this.data.endNode
    this.setData({ startNode: start, endNode })
  },
  pickEnd(e) {
    const end = +e.currentTarget.dataset.node
    // 结束节不得小于开始节
    if (this.data.startNode && end < this.data.startNode) {
      wx.showToast({ title: '结束节不能早于开始节', icon: 'none' })
      return
    }
    this.setData({ endNode: end })
  },

  // ---- 周次：快捷集合 ----
  // 生成数组 [a..b]（限制在 totalWeeks 内）
  range(a, b) {
    const out = []
    const hi = Math.min(b, this.data.totalWeeks)
    for (let i = a; i <= hi; i++) out.push(i)
    return out
  },
  setWeeks(arr) {
    const sel = {}
    arr.forEach((w) => { sel[w] = true })
    this.setData({ weeksSel: sel })
  },
  weeksFirstHalf() { this.setWeeks(this.range(1, Math.min(8, this.data.totalWeeks))) },
  weeksSecondHalf() { this.setWeeks(this.range(10, Math.min(17, this.data.totalWeeks))) },
  weeksOdd() { this.setWeeks(this.range(1, this.data.totalWeeks).filter((w) => w % 2 === 1)) },
  weeksEven() { this.setWeeks(this.range(1, this.data.totalWeeks).filter((w) => w % 2 === 0)) },
  weeksAll() { this.setWeeks(this.range(1, this.data.totalWeeks)) },

  // ---- 周次：单格 toggle ----
  toggleWeek(e) {
    const w = +e.currentTarget.dataset.week
    const sel = Object.assign({}, this.data.weeksSel)
    if (sel[w]) delete sel[w]
    else sel[w] = true
    this.setData({ weeksSel: sel })
  },

  // ---- 颜色单选 ----
  pickColor(e) { this.setData({ colorIndex: +e.currentTarget.dataset.idx }) },

  // ---- 收集已选周次（升序数组） ----
  collectWeeks() {
    return Object.keys(this.data.weeksSel)
      .filter((k) => this.data.weeksSel[k])
      .map((k) => +k)
      .sort((a, b) => a - b)
  },

  // ---- 保存 ----
  onSave() {
    const d = this.data
    const name = (d.name || '').trim()
    if (!name) return wx.showToast({ title: '请填写课程名', icon: 'none' })
    if (!d.day) return wx.showToast({ title: '请选择星期', icon: 'none' })
    if (!d.startNode || !d.endNode) return wx.showToast({ title: '请选择起止节', icon: 'none' })
    const weeks = this.collectWeeks()
    if (!weeks.length) return wx.showToast({ title: '请选择上课周数', icon: 'none' })

    const course = {
      id: d.id || ('c' + Date.now()),
      name,
      teacher: (d.teacher || '').trim(),
      location: (d.location || '').trim(),
      day: d.day,
      startNode: d.startNode,
      endNode: d.endNode,
      weeks,
      colorIndex: d.colorIndex,
      customColor: null,
    }

    const ad = store.getAccountData(d.account)
    const list = (ad.custom[d.term] || []).filter((x) => x.id !== course.id)
    list.push(course)
    ad.custom[d.term] = list
    store.saveAccountData(d.account, ad)

    wx.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => wx.navigateBack(), 350)
  },

  // ---- 删除（仅编辑模式） ----
  onDelete() {
    const d = this.data
    if (!d.id) return
    wx.showModal({
      title: '删除课程',
      content: '确定删除「' + (d.name || '该课程') + '」吗？',
      confirmColor: '#E2504A',
      success: (res) => {
        if (!res.confirm) return
        const ad = store.getAccountData(d.account)
        ad.custom[d.term] = (ad.custom[d.term] || []).filter((x) => x.id !== d.id)
        store.saveAccountData(d.account, ad)
        wx.showToast({ title: '已删除', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 300)
      },
    })
  },
})
