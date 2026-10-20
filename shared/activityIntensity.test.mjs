import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createActivityScale } from './activityIntensity.mjs'

test('sustained growth is distributed across intermediate tiers with at most 5% peaks', () => {
  const values = Array.from({ length: 182 }, (_, i) => 50 + i * 17)
  const scale = createActivityScale(values), tiers = [0, 0, 0, 0, 0]
  values.forEach(value => tiers[scale(value)]++)
  assert(tiers[4] > 0 && tiers[4] <= Math.floor(values.length * .05))
  assert(tiers.slice(1, 4).every(count => count >= 30 && count <= 80), JSON.stringify(tiers))
})
test('ties at the top stay together rather than filling the darkest tier', () => {
  const values = [...Array(100).fill(20), ...Array(40).fill(500), ...Array(40).fill(5000)]
  const scale = createActivityScale(values)
  assert.equal(scale(20), 1)
  assert.equal(scale(5000), 3)
  assert.equal(values.filter(value => scale(value) === 4).length, 0)
})
test('only rare peaks use level 4; one extreme spike leaves ordinary colors stable', () => {
  const values = [...Array(80).fill(100), ...Array(19).fill(200), 1000]
  const before = createActivityScale(values)
  const after = createActivityScale([...values.slice(0, -1), 1e12])
  assert.deepEqual([0, 100, 200, 1000].map(before), [0, 1, 3, 4])
  assert.deepEqual([100, 200].map(before), [100, 200].map(after))
})
test('both rank and numeric distance contribute to intermediate tiers', () => {
  const compact = Array.from({ length: 100 }, (_, i) => i + 1)
  const stretched = compact.map(value => value < 20 ? value : value * 1000)
  assert(createActivityScale(stretched)(stretched[21]) > createActivityScale(compact)(compact[21]))
})
test('zero days do not shift the active-day scale', () => {
  const values = Array.from({ length: 100 }, (_, i) => i + 1)
  const a = createActivityScale(values), b = createActivityScale([...Array(300).fill(0), ...values])
  assert.deepEqual([0, 10, 40, 70, 100].map(a), [0, 10, 40, 70, 100].map(b))
})
test('equal values share colors and the input is not sorted in place', () => {
  const values = [200, 100, 1000, 100, 100], original = [...values], scale = createActivityScale(values)
  assert.deepEqual(values, original)
  assert.equal(scale(100), scale('100'))
})
test('empty, sparse, constant and malformed data never invent peaks', () => {
  assert.equal(createActivityScale([])(0), 0)
  assert.equal(createActivityScale([])(1), 1)
  assert.equal(createActivityScale([500])(500), 1)
  assert.equal(createActivityScale(Array(100).fill(100))(100), 1)
  const sparse = Array.from({ length: 19 }, (_, i) => i + 1)
  assert(sparse.every(value => createActivityScale(sparse)(value) < 4))
  const scale = createActivityScale([NaN, Infinity, -1, 0, null, '100'])
  assert.equal(scale(100), 1)
  for (const value of [NaN, Infinity, -1, null, undefined, 'bad']) assert.equal(scale(value), 0)
})
test('scale is monotonic and respects the peak budget, including large counts', () => {
  for (let size = 20; size <= 365; size += 7) {
    const values = Array.from({ length: size }, (_, i) => Math.floor((i + 1) ** 1.8 / 4) + 1)
    const scale = createActivityScale(values), levels = values.map(scale)
    assert.deepEqual(levels, [...levels].sort((a, b) => a - b))
    assert(levels.filter(level => level === 4).length <= Math.floor(size * .05))
    assert(levels.every(level => Number.isInteger(level) && level >= 1 && level <= 4))
  }
  assert([1, 1e100, Number.MAX_VALUE].map(createActivityScale([1, 1e100, Number.MAX_VALUE])).every(Number.isInteger))
})
