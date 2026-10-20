// Color scale only: activity counts remain unchanged in API data and tooltips.
// Nonzero-day median resists isolated load-test spikes. Compress the upper tail
// so twice the typical activity is already dark, without a single peak washing
// out every other day. Both frontends use the same four intensity levels.
export function createActivityScale(values) {
  const counts = values.map(Number).filter(value => Number.isFinite(value) && value > 0).sort((a, b) => a - b)
  const middle = Math.floor(counts.length / 2)
  const baseline = counts.length ? counts.length % 2 ? counts[middle] : counts[middle - 1] / 2 + counts[middle] / 2 : 1
  return value => {
    const count = Number(value)
    if (!Number.isFinite(count) || count <= 0) return 0
    if (count <= baseline) return 1
    const logRatio = Math.log2(count) - Math.log2(baseline)
    return Math.min(4, Math.max(1, Math.round(1 + 3 * (1 - Math.exp(-logRatio)))))
  }
}
