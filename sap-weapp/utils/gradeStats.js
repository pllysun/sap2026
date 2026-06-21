/**
 * 成绩派生统计（移植自安卓 GradeStats.kt 的 computeGradeStats，纯函数）。
 */

const SCORE_BANDS = [
  { key: 'FAIL', label: '<60', min: 0, max: 60 },
  { key: 'D', label: '60-69', min: 60, max: 70 },
  { key: 'C', label: '70-79', min: 70, max: 80 },
  { key: 'B', label: '80-89', min: 80, max: 90 },
  { key: 'A', label: '90-100', min: 90, max: 100.1 },
]

function parseScore(score) {
  if (score == null) return null
  const v = parseFloat(String(score).trim())
  return isNaN(v) ? null : v
}

function isFailScore(score) {
  if (score == null) return false
  const v = parseScore(score)
  if (v != null) return v < 60
  const s = String(score)
  return s.indexOf('不及格') >= 0 || s.indexOf('不合格') >= 0
}

function num(s) {
  if (s == null) return 0
  const v = parseFloat(String(s).trim())
  return isNaN(v) ? 0 : v
}

function gp(g) {
  if (g.gradePoint == null) return null
  const v = parseFloat(String(g.gradePoint).trim())
  return isNaN(v) ? null : v
}

/** 由成绩列表计算完整分析模型。 */
function computeGradeStats(grades) {
  const empty = {
    totalCourses: 0, totalCredit: 0, earnedCredit: 0, gpa: 0, avgScore: 0,
    scoredCourses: 0, maxScore: null, minScore: null, maxCourse: null, minCourse: null,
    failCount: 0, excellentCount: 0, bands: [], terms: [], attrSlices: [],
    hasData: false, hasScored: false, passRate: 0, excellentRate: 0,
  }
  if (!grades || !grades.length) return empty

  const totalCredit = grades.reduce((s, g) => s + num(g.credit), 0)

  // 学分加权 GPA：仅取有正学分且有绩点。
  const gpaItems = []
  grades.forEach((g) => { const c = num(g.credit); const p = gp(g); if (c > 0 && p != null) gpaItems.push([c, p]) })
  const gpa = gpaItems.length
    ? gpaItems.reduce((s, x) => s + x[0] * x[1], 0) / gpaItems.reduce((s, x) => s + x[0], 0) : 0

  const earnedCredit = grades.filter((g) => !isFailScore(g.score)).reduce((s, g) => s + num(g.credit), 0)

  const scored = []
  grades.forEach((g) => { const v = parseScore(g.score); if (v != null) scored.push([g, v]) })

  let avgScore = 0
  if (scored.length) {
    const w = scored.filter((x) => num(x[0].credit) > 0)
    if (w.length) avgScore = w.reduce((s, x) => s + num(x[0].credit) * x[1], 0) / w.reduce((s, x) => s + num(x[0].credit), 0)
    else avgScore = scored.reduce((s, x) => s + x[1], 0) / scored.length
  }

  let maxEntry = null, minEntry = null
  scored.forEach((x) => {
    if (!maxEntry || x[1] > maxEntry[1]) maxEntry = x
    if (!minEntry || x[1] < minEntry[1]) minEntry = x
  })
  const failCount = grades.filter((g) => isFailScore(g.score)).length
  const excellentCount = scored.filter((x) => x[1] >= 90).length

  const bands = SCORE_BANDS.map((b) => ({ label: b.label, key: b.key, count: scored.filter((x) => x[1] >= b.min && x[1] < b.max).length }))

  // 按学期聚合（升序）。
  const termMap = {}
  grades.forEach((g) => { const k = (g.term && g.term.trim()) ? g.term : '其它'; (termMap[k] = termMap[k] || []).push(g) })
  const terms = Object.keys(termMap).sort().map((term) => {
    const list = termMap[term]
    const gpItems = []
    list.forEach((g) => { const c = num(g.credit); const v = gp(g); if (c > 0 && v != null) gpItems.push([c, v]) })
    const termGpa = gpItems.length ? gpItems.reduce((s, x) => s + x[0] * x[1], 0) / gpItems.reduce((s, x) => s + x[0], 0) : 0
    const sc = []
    list.forEach((g) => { const v = parseScore(g.score); if (v != null) sc.push([num(g.credit), v]) })
    let termAvg = 0
    if (sc.length) {
      const w = sc.filter((x) => x[0] > 0)
      if (w.length) termAvg = w.reduce((s, x) => s + x[0] * x[1], 0) / w.reduce((s, x) => s + x[0], 0)
      else termAvg = sc.reduce((s, x) => s + x[1], 0) / sc.length
    }
    return { term, gpa: termGpa, avgScore: termAvg, courses: list.length }
  })

  // 课程属性占比（按学分）。
  const attrMap = {}
  grades.forEach((g) => { const k = (g.courseAttr && g.courseAttr.trim()) ? g.courseAttr : '其它'; (attrMap[k] = attrMap[k] || []).push(g) })
  const attrSlices = Object.keys(attrMap).map((label) => ({
    label, credit: attrMap[label].reduce((s, g) => s + num(g.credit), 0), courses: attrMap[label].length,
  })).sort((a, b) => b.credit - a.credit)

  const scoredCourses = scored.length
  return {
    totalCourses: grades.length, totalCredit, earnedCredit, gpa, avgScore,
    scoredCourses, maxScore: maxEntry ? maxEntry[1] : null, minScore: minEntry ? minEntry[1] : null,
    maxCourse: maxEntry ? maxEntry[0].courseName : null, minCourse: minEntry ? minEntry[0].courseName : null,
    failCount, excellentCount, bands, terms, attrSlices,
    hasData: true, hasScored: scoredCourses > 0,
    passRate: scoredCourses > 0 ? (scoredCourses - failCount) * 100 / scoredCourses : 0,
    excellentRate: scoredCourses > 0 ? excellentCount * 100 / scoredCourses : 0,
  }
}

module.exports = { computeGradeStats, parseScore, isFailScore, SCORE_BANDS }
