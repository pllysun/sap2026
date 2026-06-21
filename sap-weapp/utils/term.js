/**
 * 学期号工具 + 有数据学期扫描（移植自安卓 TermUtil.kt / TermScan）。
 * 学期格式 "YYYY-YYYY-D"（D=1 秋/上、2 春/下、3 短/夏）。
 */
const RE = /^(\d{4})-(\d{4})-(\d)$/

function isTerm(v) { return RE.test(String(v || '').trim()) }

function semester(term) {
  const m = RE.exec(String(term || '').trim())
  return m ? parseInt(m[3], 10) : 0
}

/** 下一学期：D=1→D=2(同学年)；否则进下一学年 D=1。 */
function next(term) {
  const m = RE.exec(String(term || '').trim())
  if (!m) return term
  const y1 = +m[1], y2 = +m[2], d = +m[3]
  return d === 1 ? `${y1}-${y2}-2` : `${y1 + 1}-${y2 + 1}-1`
}

/** 上一学期：D>1→D=1(同学年)；D=1→上一学年 D=2。 */
function prev(term) {
  const m = RE.exec(String(term || '').trim())
  if (!m) return term
  const y1 = +m[1], y2 = +m[2], d = +m[3]
  return d > 1 ? `${y1}-${y2}-1` : `${y1 - 1}-${y2 - 1}-2`
}

/** "2024-2025-2" → "2024-2025 第2学期"。 */
function label(term) {
  const m = RE.exec(String(term || '').trim())
  return m ? `${m[1]}-${m[2]} 第${m[3]}学期` : term
}

/** "2023-2024-1" → "23-1"。 */
function shortTerm(term) {
  const parts = String(term || '').split('-')
  if (parts.length >= 3 && parts[0].length === 4) return `${parts[0].slice(-2)}-${parts[2]}`
  return String(term || '').slice(-6)
}

const BACK_CAP = 8
const FWD_CAP = 4
const MAX_EMPTY_STREAK = 2

/**
 * 以 current 为锚扫描有数据学期。
 * hasData: async (term) => boolean（抓取并存储某学期、返回是否有课）。
 * 返回所有有课学期（去重，保持发现顺序）。
 */
async function scanAround(current, hasData, opts) {
  const o = opts || {}
  const backCap = o.backCap != null ? o.backCap : BACK_CAP
  const fwdCap = o.fwdCap != null ? o.fwdCap : FWD_CAP
  const maxEmptyStreak = o.maxEmptyStreak != null ? o.maxEmptyStreak : MAX_EMPTY_STREAK
  const withData = []
  const add = (t) => { if (withData.indexOf(t) < 0) withData.push(t) }

  if (await hasData(current)) add(current)
  // 向过去
  let streak = 0, t = prev(current), n = 0
  while (n < backCap && streak < maxEmptyStreak) {
    if (await hasData(t)) { add(t); streak = 0 } else streak += 1
    t = prev(t); n += 1
  }
  // 向将来
  streak = 0; t = next(current); n = 0
  while (n < fwdCap && streak < maxEmptyStreak) {
    if (await hasData(t)) { add(t); streak = 0 } else streak += 1
    t = next(t); n += 1
  }
  return withData
}

/** 默认选中学期。 */
function defaultTerm(current, withData, month) {
  if (!withData || !withData.length) return null
  if (withData.indexOf(current) < 0) return withData.slice().sort().reverse()[0]
  const nxt = next(current)
  if (semester(current) === 2 && month >= 8 && withData.indexOf(nxt) >= 0) return nxt
  return current
}

module.exports = {
  isTerm, semester, next, prev, label, shortTerm,
  scanAround, defaultTerm, BACK_CAP, FWD_CAP, MAX_EMPTY_STREAK,
}
