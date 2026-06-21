// 教务绑定页：把学校教务账号密码交给后端，后端代抓课表。
// 绑定可能分阶段：表单 → (验证码) → (短信MFA) → 成功。
const api = require('../../utils/api')
const store = require('../../utils/store')

Page({
  data: {
    stage: 'form',          // 'form' | 'captcha' | 'mfa'
    account: '',            // 学号
    password: '',           // 教务密码
    challengeId: '',        // 验证码/短信阶段的会话标识
    captchaSrc: '',         // 验证码图片（已拼好 data: 前缀）
    captchaCode: '',        // 用户输入的验证码
    phone: '',              // 掩码手机号
    mfaCode: '',            // 短信验证码
    loading: false,         // 防重复提交
  },

  // ---- 输入绑定 ----
  onAccount(e) { this.setData({ account: e.detail.value.trim() }) },
  onPassword(e) { this.setData({ password: e.detail.value }) },
  onCaptcha(e) { this.setData({ captchaCode: e.detail.value.trim() }) },
  onMfa(e) { this.setData({ mfaCode: e.detail.value.trim() }) },

  /**
   * 统一处理后端绑定返回。各阶段提交后都走这里。
   * @param {{code,message,data}} r
   */
  handle(r) {
    if (!r || r.code !== 200) {
      wx.showToast({ title: (r && r.message) || '绑定失败', icon: 'none' })
      return
    }
    const d = r.data || {}

    // 需要图形验证码
    if (d.needCaptcha === true) {
      const img = d.captchaImage || ''
      const src = img.indexOf('data:') === 0 ? img : 'data:image/jpeg;base64,' + img
      this.setData({
        stage: 'captcha',
        challengeId: d.challengeId || this.data.challengeId,
        captchaSrc: src,
        captchaCode: '',
      })
      return
    }

    // 需要短信验证
    if (d.needMfa === true) {
      this.setData({
        stage: 'mfa',
        challengeId: d.challengeId || this.data.challengeId,
        phone: d.phone || '',
        mfaCode: '',
      })
      return
    }

    // 都不需要 → 绑定成功
    store.setActiveAccount(this.data.account)
    wx.showToast({ title: '绑定成功', icon: 'success' })
    setTimeout(() => wx.navigateBack(), 600)
  },

  // ---- 表单态：初次绑定 ----
  async onBind() {
    const { account, password, loading } = this.data
    if (loading) return
    if (!account) { wx.showToast({ title: '请输入学号', icon: 'none' }); return }
    if (!password) { wx.showToast({ title: '请输入教务密码', icon: 'none' }); return }
    this.setData({ loading: true })
    try {
      const r = await api.jwBind(account, password)
      this.handle(r)
    } catch (e) {
      wx.showToast({ title: '网络异常，请重试', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // ---- 验证码态：提交验证码 ----
  async onSubmitCaptcha() {
    const { challengeId, captchaCode, loading } = this.data
    if (loading) return
    if (!captchaCode) { wx.showToast({ title: '请输入验证码', icon: 'none' }); return }
    this.setData({ loading: true })
    try {
      const r = await api.jwBindCaptcha(challengeId, captchaCode)
      this.handle(r)
    } catch (e) {
      wx.showToast({ title: '网络异常，请重试', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // ---- 短信态：提交短信码 ----
  async onSubmitMfa() {
    const { challengeId, mfaCode, loading } = this.data
    if (loading) return
    if (!mfaCode) { wx.showToast({ title: '请输入短信验证码', icon: 'none' }); return }
    this.setData({ loading: true })
    try {
      const r = await api.jwBindMfa(challengeId, mfaCode)
      this.handle(r)
    } catch (e) {
      wx.showToast({ title: '网络异常，请重试', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // ---- 短信态：重新发送 ----
  async onResendMfa() {
    const { challengeId, loading } = this.data
    if (loading) return
    this.setData({ loading: true })
    try {
      const r = await api.jwBindMfaResend(challengeId, '')
      if (r && r.code === 200) {
        wx.showToast({ title: '已重新发送', icon: 'none' })
      } else {
        wx.showToast({ title: (r && r.message) || '发送失败', icon: 'none' })
      }
    } catch (e) {
      wx.showToast({ title: '网络异常，请重试', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // 返回表单（验证码/短信态点「返回」时用）
  backToForm() {
    this.setData({ stage: 'form', captchaCode: '', mfaCode: '' })
  },
})
