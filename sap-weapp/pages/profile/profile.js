// 我的页：用户信息卡 + 教务账号管理 + 设置入口
const api = require('../../utils/api')
const store = require('../../utils/store')

// 取昵称/姓名的首字作为头像占位
function firstChar(s) {
  const t = (s || '').trim()
  return t ? t.charAt(0).toUpperCase() : '?'
}

// 把后端 identities 拼成 "2025 · 宣传部部长" 这种行（多个用 / 连）
function fmtIdentities(list) {
  if (!list || !list.length) return ''
  return list
    .map((it) => {
      const parts = []
      if (it.grade != null && it.grade !== '') parts.push(it.grade)
      if (it.positionName) parts.push(it.positionName)
      return parts.join(' · ')
    })
    .filter((s) => s)
    .join(' / ')
}

// 时间戳 -> "上次同步 06-19 14:30"
function fmtSync(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ''
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  return `上次同步 ${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

Page({
  data: {
    // 用户信息
    avatar: '',
    nick: '',
    nickInitial: '?',
    studentId: '',
    isMember: false,
    memberLabel: '游客',
    identityLine: '',
    // 教务账号
    accounts: [],          // [{ account, syncLabel }]
    activeAccount: '',
    accountsLoaded: false,
  },

  onLoad() {
    this.applyLocal()
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 2 })
    // 每次进页面：先用本地态渲染，再后台刷新（active 可能在别页改变）
    this.applyLocal()
    this.refreshMe()
    this.loadAccounts()
  },

  // 用本地缓存填充用户卡
  applyLocal() {
    const u = store.getUser() || {}
    const m = store.getMember() || { isMember: false }
    this.setUserData(u, m, this.data.identityLine)
  },

  // 统一写入用户卡相关字段
  setUserData(u, m, identityLine) {
    const nick = u.nickname || u.name || ''
    this.setData({
      avatar: u.avatar || '',
      nick: nick || '未命名',
      nickInitial: firstChar(nick),
      studentId: u.studentId || '',
      isMember: !!m.isMember,
      memberLabel: m.isMember ? '协会会员' : '游客',
      identityLine: identityLine || '',
    })
  },

  // 调 api.me() 刷新用户态
  refreshMe() {
    api
      .me()
      .then((res) => {
        if (!res || res.code !== 200 || !res.data) return
        const d = res.data
        if (d.user) store.setUser(d.user)
        if (d.roles) store.setMember(d.roles)
        const m = store.getMember()
        this.setUserData(d.user || store.getUser() || {}, m, fmtIdentities(d.identities))
      })
      .catch(() => {}) // 静默失败，保留本地态
  },

  // 拉教务账号列表
  loadAccounts() {
    api
      .jwAccounts()
      .then((res) => {
        const list = (res && res.code === 200 && Array.isArray(res.data)) ? res.data : []
        const accounts = list.map((it) => ({
          account: it.account,
          syncLabel: fmtSync(it.lastSyncAt),
        }))
        // 校正激活态：若当前激活账号已不在列表里，退回第一个
        let active = store.getActiveAccount()
        if (active && !accounts.some((a) => a.account === active)) {
          active = accounts.length ? accounts[0].account : ''
          store.setActiveAccount(active)
        }
        this.setData({ accounts, activeAccount: active, accountsLoaded: true })
      })
      .catch(() => { this.setData({ accountsLoaded: true }) })
  },

  // 点行 -> 切换激活账号
  tapAccount(e) {
    const acc = e.currentTarget.dataset.acc
    if (!acc || acc === this.data.activeAccount) return
    store.setActiveAccount(acc)
    this.setData({ activeAccount: acc })
    wx.showToast({ title: '已切换', icon: 'success' })
  },

  // 解绑某账号（行尾按钮，catchtap 阻止冒泡到切换）
  unbindAccount(e) {
    const acc = e.currentTarget.dataset.acc
    if (!acc) return
    wx.showModal({
      title: '解绑教务账号',
      content: `确定解绑 ${acc} ？解绑后需重新绑定才能同步。`,
      confirmColor: '#E2504A',
      success: (r) => {
        if (!r.confirm) return
        wx.showLoading({ title: '解绑中', mask: true })
        api
          .jwUnbind(acc)
          .then((res) => {
            wx.hideLoading()
            if (!res || res.code !== 200) {
              wx.showToast({ title: (res && res.message) || '解绑失败', icon: 'none' })
              return
            }
            // 若解绑的是当前激活账号，激活态交给剩余第一个
            if (acc === store.getActiveAccount()) {
              const rest = this.data.accounts.filter((a) => a.account !== acc)
              store.setActiveAccount(rest.length ? rest[0].account : '')
            }
            wx.showToast({ title: '已解绑', icon: 'success' })
            this.loadAccounts()
          })
          .catch((err) => {
            wx.hideLoading()
            wx.showToast({ title: (err && err.message) || '解绑失败', icon: 'none' })
          })
      },
    })
  },

  // 绑定新教务账号
  goBind() {
    wx.navigateTo({ url: '/pages/bind/bind' })
  },

  // 设置页
  goSettings() {
    wx.navigateTo({ url: '/pages/settings/settings' })
  },
})
