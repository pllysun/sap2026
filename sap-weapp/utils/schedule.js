/**
 * 课表合并与网格布局（移植自安卓 ScheduleViewModel.buildDisplay + ScheduleGrid 布局）。
 * 把「教务课(按大节)」与「自建课(按节)」合并成统一 DisplayCourse，按周过滤，
 * 并计算每张课卡在网格中的 top/height(rpx) 与同格重叠时的 left%/width%(分栏)。
 */
const week = require('./week')
const periods = require('./periods')
const color = require('./color')

const LUNCH_GAP = 14   // 第4节后午休间隔(rpx)
const EVENING_GAP = 14 // 第8节后晚饭间隔(rpx)
const CARD_GAP = 4     // 课卡间留白(rpx)

/** 有效开学日期：手动覆盖优先，否则用教务下发的。 */
function effectiveStart(accountData, term) {
  const ov = accountData.settings && accountData.settings.startOverride && accountData.settings.startOverride[term]
  if (ov) return ov
  const td = accountData.termData[term]
  return (td && td.semesterStartDate) || null
}

/** 有效每日节数 8..16。 */
function dailyPeriods(settings) {
  const n = settings && settings.periodsPerDay
  return (n >= 8 && n <= 16) ? n : 10
}
/** 有效课格高度(rpx)：dp 40..88 → rpx(*2)，默认 56dp。 */
function rowHeightRpx(settings) {
  const dp = settings && settings.rowHeight
  const v = (dp >= 40 && dp <= 88) ? dp : 56
  return v * 2
}
/** 课程卡字号倍率 0.8..1.4。 */
function cardScale(settings) {
  const v = settings && settings.cardTextScale
  return ((v >= 80 && v <= 140) ? v : 100) / 100
}

/** 教务课(CourseDto) → 中间态。 */
function fromTermCourse(c) {
  const r = periods.nodesOfSection(c.sectionIndex || 1)
  return {
    name: c.name || '',
    teacher: c.teacher || '',
    location: c.room || '',
    day: c.day || 0,
    startNode: r.start,
    endNode: r.end,
    weeks: week.parseWeeks(c.weeks),
    weeksRaw: c.weeks || '',
    isCustom: false,
    customId: null,
    customColor: null,
  }
}

/** 自建课(CustomCourse) → 中间态。 */
function fromCustom(c) {
  return {
    name: c.name || '',
    teacher: c.teacher || '',
    location: c.location || '',
    day: c.day || 0,
    startNode: c.startNode,
    endNode: c.endNode,
    weeks: c.weeks || [],
    weeksRaw: week.formatWeeks(c.weeks || []),
    isCustom: true,
    customId: c.id,
    customColor: c.customColor || null,
    colorIndex: c.colorIndex || 0,
  }
}

/** 该课在指定周是否上课（weeks 空=每周）。 */
function isOnWeek(c, wk) { return !c.weeks || !c.weeks.length || c.weeks.indexOf(wk) >= 0 }

/**
 * 构建某周展示课程（含布局）。
 * @returns { cards: [...], remarks: [...] }
 */
function buildWeek(accountData, term, wk) {
  const settings = accountData.settings || {}
  const showNonWeek = !!settings.showNonWeek
  const td = accountData.termData[term] || { courses: [], remarks: [] }
  const custom = (accountData.custom && accountData.custom[term]) || []

  let list = []
  ;(td.courses || []).forEach((c) => list.push(fromTermCourse(c)))
  custom.forEach((c) => list.push(fromCustom(c)))

  // 标记本周/非本周，按设置过滤
  list = list.map((c) => Object.assign({}, c, { isThisWeek: isOnWeek(c, wk) }))
    .filter((c) => c.isThisWeek || showNonWeek)
    .filter((c) => c.day >= 1 && c.day <= 7 && c.startNode >= 1)

  // 解析颜色 + 周次标签
  list.forEach((c) => {
    const cc = c.customColor ? color.customCourseColor(toHex(c.customColor))
      : (c.isCustom ? color.paletteColor(c.colorIndex || 0) : color.courseColorOf(c.name))
    c.bg = cc.container
    c.fg = cc.onContainer
    c.weeksLabel = c.weeksRaw || week.formatWeeks(c.weeks)
  })

  // 网格布局（节 → top/height）
  const count = dailyPeriods(settings)
  const rh = rowHeightRpx(settings)
  const tops = nodeTops(count, rh)
  list.forEach((c) => {
    const s = Math.min(Math.max(c.startNode, 1), count)
    const e = Math.min(Math.max(c.endNode, s), count)
    c.top = tops[s - 1]
    c.height = (tops[e] != null ? tops[e] : (tops[e - 1] + rh)) - tops[s - 1] - CARD_GAP
  })

  // 同一天重叠 → 分栏（left%/width%）
  layoutLanes(list)

  // 去重：已被加进网格的同名自建课，不再在备注横幅里重复显示
  const placedNames = {}
  custom.forEach((c) => { if (c.name) placedNames[c.name] = true })
  const remarks = (td.remarks || [])
    .filter((r) => !placedNames[r.name])
    .map((r) => ({
      name: r.name || '', teacher: r.teacher || '', weeks: r.weeks || '', clazz: r.clazz || '',
    }))
  return { cards: list, remarks }
}

/** 各节顶端 top(rpx)，含午休/晚饭间隔。tops 长度 count+1（含末尾，便于算高度）。 */
function nodeTops(count, rh) {
  const tops = []
  let y = 0
  for (let node = 1; node <= count + 1; node++) {
    tops.push(y)
    y += rh
    if (node === periods.LUNCH_AFTER_NODE && node < count) y += LUNCH_GAP
    if (node === periods.EVENING_AFTER_NODE && node < count) y += EVENING_GAP
  }
  return tops
}

/** 网格总高度(rpx)。 */
function gridHeight(settings) {
  const count = dailyPeriods(settings)
  const tops = nodeTops(count, rowHeightRpx(settings))
  return tops[count]
}

/** 同一天重叠课卡分栏：把每天的卡按重叠簇分配车道，得 left%/width%。 */
function layoutLanes(cards) {
  for (let day = 1; day <= 7; day++) {
    const dayCards = cards.filter((c) => c.day === day).sort((a, b) => a.startNode - b.startNode || a.endNode - b.endNode)
    if (!dayCards.length) continue
    // 分簇（链式重叠）
    let clusterEnd = -1
    let cluster = []
    const flush = () => {
      if (!cluster.length) return
      assignLanes(cluster)
      cluster = []
    }
    dayCards.forEach((c) => {
      if (cluster.length && c.startNode > clusterEnd) flush()
      cluster.push(c)
      clusterEnd = Math.max(clusterEnd, c.endNode)
    })
    flush()
  }
}

function assignLanes(cluster) {
  const laneEnds = [] // 每条车道当前 endNode
  cluster.forEach((c) => {
    let lane = laneEnds.findIndex((end) => c.startNode > end)
    if (lane < 0) { lane = laneEnds.length; laneEnds.push(c.endNode) }
    else laneEnds[lane] = c.endNode
    c._lane = lane
  })
  const lanes = laneEnds.length
  cluster.forEach((c) => {
    c.widthPct = 100 / lanes
    c.leftPct = (c._lane / lanes) * 100
  })
}

/** 0xAARRGGBB(Long) → #RRGGBB。 */
function toHex(argb) {
  const v = Number(argb) & 0xffffff
  return '#' + v.toString(16).padStart(6, '0')
}

module.exports = {
  effectiveStart, dailyPeriods, rowHeightRpx, cardScale,
  buildWeek, nodeTops, gridHeight, isOnWeek,
  LUNCH_GAP, EVENING_GAP, CARD_GAP,
}
