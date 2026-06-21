// 登录页：协会会员账号密码登录
const api = require('../../utils/api')
const store = require('../../utils/store')

Page({
  data: {
    studentId: '',   // 学号
    password: '',     // 密码
    loading: false,   // 登录中
  },

  // 学号输入
  onStudentId(e) {
    this.setData({ studentId: e.detail.value.trim() })
  },

  // 密码输入
  onPassword(e) {
    this.setData({ password: e.detail.value })
  },

  // 提交登录
  async onLogin() {
    const { studentId, password, loading } = this.data
    if (loading || !studentId || !password) return
    this.setData({ loading: true })
    try {
      const r = await api.appLogin(studentId, password)
      if (r.code === 200) {
        // 成功：写入本地状态后进入课表
        store.setToken(r.data.token)
        store.setUser(r.data.user)
        store.setMember(r.data.roles)
        wx.reLaunch({ url: '/pages/schedule/schedule' })
      } else {
        wx.showToast({ title: r.message || '登录失败', icon: 'none' })
      }
    } catch (e) {
      wx.showToast({ title: e.message || '网络异常', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
