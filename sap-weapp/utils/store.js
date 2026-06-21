/**
 * 本地状态层：会员态 / 用户 / 当前教务学号 / 课表缓存(多账号多学期) / 成绩考试缓存。
 * 基于 wx.getStorageSync。注意小程序单 key 上限 1MB、总 10MB，故课表按账号+学期分缓存。
 */
const { STORAGE, MEMBER_ROLE_MAX } = require('../config')

function read(key, def) {
  try { const v = wx.getStorageSync(key); return v == null || v === '' ? def : v } catch (e) { return def }
}
function write(key, val) { try { wx.setStorageSync(key, val) } catch (e) {} }

// ---- 会员态 ----
function memberFromRoles(roles) {
  const arr = roles || []
  const isMember = arr.some((r) => r <= MEMBER_ROLE_MAX)
  return { isMember, roles: arr }
}
function setMember(roles) { write(STORAGE.MEMBER, memberFromRoles(roles)) }
function getMember() { return read(STORAGE.MEMBER, { isMember: false, roles: [] }) }

// ---- 用户 ----
function setUser(u) { write(STORAGE.USER, u || null) }
function getUser() { return read(STORAGE.USER, null) }

// ---- token ----
function getToken() { return read(STORAGE.TOKEN, '') }
function setToken(t) { write(STORAGE.TOKEN, t || '') }
function clearAuth() {
  try {
    wx.removeStorageSync(STORAGE.TOKEN)
    wx.removeStorageSync(STORAGE.MEMBER)
    wx.removeStorageSync(STORAGE.USER)
  } catch (e) {}
}

// ---- 当前教务学号 ----
function getActiveAccount() { return read(STORAGE.ACTIVE_ACCOUNT, '') }
function setActiveAccount(a) { write(STORAGE.ACTIVE_ACCOUNT, a || '') }

// ---- 课表根（多账号） ----
function defaultSettings() {
  return {
    totalWeeks: 20,
    showWeekend: false,
    showNonWeek: false,
    weekStartSunday: false,
    periodsPerDay: 10,
    showNowLine: false,
    rowHeight: 0,
    cardTextScale: 0,
    startOverride: {}, // { [term]: iso } 手动开学日期覆盖
  }
}
function defaultAccountData() {
  return { activeTerm: '', terms: [], scanned: false, termData: {}, settings: defaultSettings(), custom: {} }
}

function getRoot() { return read(STORAGE.SCHEDULE_ROOT, { accounts: {} }) }
function setRoot(root) { write(STORAGE.SCHEDULE_ROOT, root) }

function getAccountData(account) {
  const root = getRoot()
  const d = root.accounts[account]
  if (!d) return defaultAccountData()
  // 兼容补齐
  if (!d.settings) d.settings = defaultSettings()
  if (!d.settings.startOverride) d.settings.startOverride = {}
  if (!d.termData) d.termData = {}
  if (!d.custom) d.custom = {}
  if (!d.terms) d.terms = []
  return d
}

function saveAccountData(account, data) {
  const root = getRoot()
  root.accounts[account] = data
  setRoot(root)
}

/** 写入某学期教务课表（来自后端 ScheduleData）。 */
function saveTermSchedule(account, term, scheduleData) {
  const d = getAccountData(account)
  d.termData[term] = {
    courses: scheduleData.courses || [],
    remarks: scheduleData.remarks || [],
    weekdays: scheduleData.weekdays || [],
    semesterStartDate: scheduleData.semesterStartDate || null,
  }
  if (scheduleData.terms && scheduleData.terms.length) d.terms = scheduleData.terms
  saveAccountData(account, d)
}

// ---- 成绩/考试/学期 缓存（防风控：进页面读缓存，刷新靠手动同步） ----
function getJwCache() { return read(STORAGE.JW_CACHE, {}) }
function setJwCache(c) { write(STORAGE.JW_CACHE, c) }

function readCache(kind, key) {
  const c = getJwCache()
  return (c[kind] && c[kind][key]) || null
}
function writeCache(kind, key, items) {
  const c = getJwCache()
  if (!c[kind]) c[kind] = {}
  c[kind][key] = { items, syncedAt: Date.now() }
  setJwCache(c)
}

module.exports = {
  read, write,
  setMember, getMember, memberFromRoles,
  setUser, getUser,
  getToken, setToken, clearAuth,
  getActiveAccount, setActiveAccount,
  defaultSettings, defaultAccountData,
  getRoot, setRoot, getAccountData, saveAccountData, saveTermSchedule,
  readCache, writeCache,
}
