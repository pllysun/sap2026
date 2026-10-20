// Color scale only: activity counts remain unchanged in API data and tooltips.
// Rank contributes 80%, robust log magnitude 20%. The 95th percentile caps the
// magnitude term so isolated spikes cannot wash out other days. Level 4 is for
// at most 5% of ACTIVE days; use the lower rank of ties, keeping equal counts the
// same color even if that leaves level 4 empty. Zeros/future days are excluded.
// Fewer than 20 active days cannot establish a rare peak. Shared by both clients.
export function createActivityScale(values) {
  const counts = values.map(Number).filter(value => Number.isFinite(value) && value > 0).sort((a, b) => a - b)
  const size = counts.length, minimum = counts[0]
  const logMinimum = Math.log1p(minimum)
  const logRange = Math.log1p(counts[Math.ceil(size * .95) - 1]) - logMinimum
  return value => {
    const count = Number(value)
    if (!Number.isFinite(count) || count <= 0) return 0
    if (!size || count <= minimum) return 1
    let left = 0, right = size
    while (left < right) {
      const middle = (left + right) >>> 1
      if (counts[middle] < count) left = middle + 1
      else right = middle
    }
    // Integer comparison avoids rounding at the 95% boundary.
    if (size >= 20 && left * 100 >= size * 95) return 4
    const rank = left / size
    const magnitude = logRange > 0 ? Math.max(0, Math.min(1, (Math.log1p(count) - logMinimum) / logRange)) : 1
    const weighted = .8 * rank + .2 * magnitude
    return weighted >= 2 / 3 ? 3 : weighted >= 1 / 3 ? 2 : 1
  }
}
