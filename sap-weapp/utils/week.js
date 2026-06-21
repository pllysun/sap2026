/**
 * 周次解析与当前周计算（移植自安卓 WeekUtil.kt）。
 */

/** "2026-03-09" → Date(本地零点)；非法返回 null。 */
function parseDate(iso) {
  if (!iso) return null
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(String(iso).trim())
  if (!m) return null
  const d = new Date(+m[1], +m[2] - 1, +m[3])
  return isNaN(d.getTime()) ? null : d
}

/** Date → "YYYY-MM-DD"。 */
function format(date) {
  const y = date.getFullYear()
  const mo = String(date.getMonth() + 1).padStart(2, '0')
  const da = String(date.getDate()).padStart(2, '0')
  return `${y}-${mo}-${da}`
}

/** 解析教务周次串，如 "1-8,10-11(周)" / "1-16(单)" / "2-16(双)" → 升序周次数组。 */
function parseWeeks(raw) {
  if (!raw) return []
  const s = String(raw)
  const parity = s.indexOf('单') >= 0 ? 1 : s.indexOf('双') >= 0 ? 0 : -1
  const body = s.split('(')[0].split('（')[0].replace(/周/g, '').trim()
  const set = new Set()
  for (const part of body.split(/[,，]/)) {
    const p = part.trim()
    if (!p) continue
    const range = p.split(/[-~]/)
    if (range.length === 2) {
      const a = parseInt(range[0], 10)
      const b = parseInt(range[1], 10)
      if (!isNaN(a) && !isNaN(b)) {
        for (let w = a; w <= b; w++) if (parity === -1 || w % 2 === parity) set.add(w)
      }
    } else {
      const n = parseInt(p, 10)
      if (!isNaN(n)) set.add(n)
    }
  }
  return Array.from(set).sort((x, y) => x - y)
}

/** 根据开学日期算今天是第几周（1 起）；无开学日期返回 null。 */
function currentWeek(semesterStartIso, today) {
  const start = parseDate(semesterStartIso)
  if (!start) return null
  const t = today || new Date()
  const t0 = new Date(t.getFullYear(), t.getMonth(), t.getDate())
  const days = Math.floor((t0 - start) / 86400000)
  if (days < 0) return 1
  return Math.floor(days / 7) + 1
}

/** 某一周的周一到周日 Date 数组；无开学日期返回 null。 */
function datesOfWeek(semesterStartIso, week) {
  const start = parseDate(semesterStartIso)
  if (!start) return null
  const monday = new Date(start)
  monday.setDate(monday.getDate() + (week - 1) * 7)
  const out = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday)
    d.setDate(d.getDate() + i)
    out.push(d)
  }
  return out
}

/** 周次数组 → 紧凑标签，如 [1,2,3,5,6] → "1-3,5-6周"。 */
function formatWeeks(weeks) {
  if (!weeks || !weeks.length) return ''
  const s = Array.from(new Set(weeks)).sort((a, b) => a - b)
  const parts = []
  let start = s[0]
  let prev = s[0]
  for (let i = 1; i < s.length; i++) {
    if (s[i] === prev + 1) prev = s[i]
    else { parts.push(start === prev ? `${start}` : `${start}-${prev}`); start = s[i]; prev = s[i] }
  }
  parts.push(start === prev ? `${start}` : `${start}-${prev}`)
  return parts.join(',') + '周'
}

module.exports = { parseDate, format, parseWeeks, currentWeek, datesOfWeek, formatWeeks }
