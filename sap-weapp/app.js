const periods = require('./utils/periods')
const store = require('./utils/store')
const { STORAGE } = require('./config')

App({
  globalData: {
    accent: '#2E9BEF', // 主题强调色（默认天蓝，对齐 App ThemeState.DEFAULT），可在设置里改
  },

  onLaunch() {
    // 载入自定义节次时间
    periods.load()
    // 载入主题色
    try {
      const a = wx.getStorageSync(STORAGE.THEME_ACCENT)
      if (a) this.globalData.accent = a
    } catch (e) {}
  },

  /** 是否已登录（有 token）。 */
  hasToken() {
    return !!store.getToken()
  },
})
