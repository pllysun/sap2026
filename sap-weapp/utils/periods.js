/**
 * 节次时间表（移植自安卓 Periods.kt，WakeUp 风格按「节」排）。
 * 教务「大节」sectionIndex(1 起) 映射为节区间 [2N-1, 2N]。
 */
const { STORAGE } = require('../config')

const DEFAULT = [
  { node: 1, start: '08:00', end: '08:45' },
  { node: 2, start: '08:55', end: '09:40' },
  { node: 3, start: '10:00', end: '10:45' },
  { node: 4, start: '10:55', end: '11:40' },
  { node: 5, start: '14:00', end: '14:45' },
  { node: 6, start: '14:55', end: '15:40' },
  { node: 7, start: '16:00', end: '16:45' },
  { node: 8, start: '16:55', end: '17:40' },
  { node: 9, start: '19:00', end: '19:45' },
  { node: 10, start: '19:55', end: '20:40' },
]

const FULL_TEMPLATE = DEFAULT.concat([
  { node: 11, start: '20:50', end: '21:35' },
  { node: 12, start: '21:45', end: '22:30' },
  { node: 13, start: '22:40', end: '23:25' },
  { node: 14, start: '23:35', end: '00:20' },
  { node: 15, start: '00:30', end: '01:15' },
  { node: 16, start: '01:25', end: '02:10' },
])

const MAX_NODES = 16
const LUNCH_AFTER_NODE = 4
const EVENING_AFTER_NODE = 8

let current = DEFAULT.slice()

function load() {
  try {
    const s = wx.getStorageSync(STORAGE.PERIODS)
    const parsed = parse(s)
    current = parsed || DEFAULT.slice()
  } catch (e) {
    current = DEFAULT.slice()
  }
}

function save(list) {
  const normalized = (list && list.length ? list : DEFAULT).map((p, i) => ({ node: i + 1, start: p.start, end: p.end }))
  current = normalized
  try {
    wx.setStorageSync(STORAGE.PERIODS, normalized.map((p) => `${p.start}-${p.end}`).join(','))
  } catch (e) {}
}

function resetDefault() {
  current = DEFAULT.slice()
  try { wx.removeStorageSync(STORAGE.PERIODS) } catch (e) {}
}

function parse(s) {
  if (!s) return null
  try {
    const arr = s.split(',').map((seg, i) => {
      const [a, b] = seg.split('-')
      return { node: i + 1, start: a.trim(), end: b.trim() }
    })
    return arr.length ? arr : null
  } catch (e) {
    return null
  }
}

/** 某节时间：优先当前生效表，缺失回退默认模板。 */
function period(node) {
  return current.find((p) => p.node === node) || FULL_TEMPLATE.find((p) => p.node === node) || null
}

function count() { return current.length }

/** 取 count 节的时间表（自定义优先，超出用默认补齐）。 */
function tableFor(c) {
  const n = Math.min(Math.max(c, 1), MAX_NODES)
  const out = []
  for (let node = 1; node <= n; node++) out.push(period(node))
  return out
}

function defaultTableFor(c) {
  const n = Math.min(Math.max(c, 1), MAX_NODES)
  return FULL_TEMPLATE.slice(0, n)
}

/** 教务大节 sectionIndex(1 起) → {start,end} 节号区间。 */
function nodesOfSection(sectionIndex) {
  const s = Math.max(sectionIndex, 1)
  return { start: 2 * s - 1, end: 2 * s }
}

module.exports = {
  DEFAULT, FULL_TEMPLATE, MAX_NODES, LUNCH_AFTER_NODE, EVENING_AFTER_NODE,
  load, save, resetDefault, period, count, tableFor, defaultTableFor, nodesOfSection,
  get current() { return current },
}
