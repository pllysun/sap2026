/**
 * 课程色卡（移植自安卓 Color.kt 的 CoursePalette）。
 * 12 色柔和卡片：{ container 底色, onContainer 文字色 }。
 */
const CoursePalette = [
  { container: '#C5DDF7', onContainer: '#2A6BB0' }, // 0 blue
  { container: '#F8CEBB', onContainer: '#C0552C' }, // 1 coral
  { container: '#CCD1F0', onContainer: '#45499C' }, // 2 indigo
  { container: '#D8D0F6', onContainer: '#5A4FBE' }, // 3 purple
  { container: '#F8DDA6', onContainer: '#B07A12' }, // 4 amber
  { container: '#F8CADC', onContainer: '#B53E6E' }, // 5 pink
  { container: '#C7D8EC', onContainer: '#3A6491' }, // 6 steel blue
  { container: '#F8D6AD', onContainer: '#B16A18' }, // 7 apricot
  { container: '#F8CCCC', onContainer: '#BD4B4B' }, // 8 rose
  { container: '#CFD5DF', onContainer: '#4C566B' }, // 9 slate
  { container: '#DDD3F4', onContainer: '#6A5BC0' }, // 10 lavender
  { container: '#EFE3A4', onContainer: '#9A7B10' }, // 11 gold
]

/** 名称 → 稳定色卡下标（同名课永远同色）。 */
function colorIndexOf(name) {
  const key = name || ''
  if (!key) return 0
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) & 0x7fffffff
  return h % CoursePalette.length
}

/** 取色卡：override 优先，否则按名称稳定取色。 */
function courseColorOf(name, override) {
  const idx = (override != null && override >= 0 && override < CoursePalette.length) ? override : colorIndexOf(name)
  return CoursePalette[idx]
}

function paletteColor(index) {
  const n = CoursePalette.length
  return CoursePalette[((index % n) + n) % n]
}

/** 由 #RRGGBB 计算相对亮度（0..1）。 */
function luminance(hex) {
  const m = /^#?([0-9a-fA-F]{6})$/.exec(hex)
  if (!m) return 0
  const v = parseInt(m[1], 16)
  const r = ((v >> 16) & 255) / 255, g = ((v >> 8) & 255) / 255, b = (v & 255) / 255
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** 由单个自定义颜色派生课卡色卡：选中色作底，按亮度配可读文字色。 */
function customCourseColor(hex) {
  const onC = luminance(hex) > 0.5 ? darken(hex, 0.32) : '#FFFFFF'
  return { container: hex, onContainer: onC }
}

function darken(hex, factor) {
  const m = /^#?([0-9a-fA-F]{6})$/.exec(hex)
  if (!m) return hex
  const v = parseInt(m[1], 16)
  const r = Math.round(((v >> 16) & 255) * factor)
  const g = Math.round(((v >> 8) & 255) * factor)
  const b = Math.round((v & 255) * factor)
  return '#' + [r, g, b].map((x) => x.toString(16).padStart(2, '0')).join('')
}

module.exports = { CoursePalette, colorIndexOf, courseColorOf, paletteColor, customCourseColor, luminance }
