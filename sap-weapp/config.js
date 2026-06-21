/**
 * 全局配置。
 *
 * BASE_URL：后端地址。生产域名 https://csuftsap.top（nginx 把 /api/ 反代到后端 8081）。
 * - 生产/真机/发布：用 https://csuftsap.top。必须在小程序后台「开发管理 → 服务器域名」把
 *   https://csuftsap.top 加入 request 合法域名 / uploadFile 合法域名 白名单（且域名需 ICP 备案）。
 * - 本地联调：改成 http://localhost:8081，并在开发者工具「详情 → 本地设置」勾选「不校验合法域名」。
 */
const BASE_URL = 'https://csuftsap.top'
// const BASE_URL = 'http://localhost:8081' // 本地联调时启用

module.exports = {
  BASE_URL,
  // 本地 storage key
  STORAGE: {
    TOKEN: 'sap_token',
    USER: 'sap_user',
    MEMBER: 'sap_member',        // { isMember, roles }
    ACTIVE_ACCOUNT: 'sap_active_account', // 当前选中的教务学号
    SCHEDULE_ROOT: 'sap_schedule_root',   // { accounts: { [account]: AccountData } }
    JW_CACHE: 'sap_jw_cache',    // { grades, exams, terms ...}
    THEME_ACCENT: 'sap_theme_accent',
    PERIODS: 'sap_periods',      // 自定义节次时间
  },
  // 角色码 <=3 即会员（成员/管理/会长/超管），4=游客
  MEMBER_ROLE_MAX: 3,
}
