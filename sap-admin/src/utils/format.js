// 通用格式化工具

/** 字节数 → 人类可读（B/KB/MB/GB/TB）。入参可能是 Long 序列化来的字符串，统一 Number()。 */
export function formatBytes(n) {
  n = Number(n) || 0
  if (n < 1024) return n + ' B'
  const units = ['KB', 'MB', 'GB', 'TB']
  let v = n / 1024
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return v.toFixed(2) + ' ' + units[i]
}

/** 千分位整数。 */
export function formatInt(n) {
  return (Number(n) || 0).toLocaleString('en-US')
}
