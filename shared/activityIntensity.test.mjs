import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createActivityScale } from './activityIntensity.mjs'

test('typical days stay light and both twice-typical days and rare spikes are dark', () => {
  const values = [...Array(80).fill(100), ...Array(19).fill(200), 1000]
  const scale = createActivityScale(values)
  assert.deepEqual([0,100,200,1000].map(scale), [0,1,3,4])
  assert.deepEqual(values.slice(), values)
})
test('an isolated extreme spike cannot wash out ordinary active days', () => {
  const before = createActivityScale([...Array(80).fill(100), ...Array(19).fill(200), 1000])
  const after = createActivityScale([...Array(80).fill(100), ...Array(19).fill(200), 1e12])
  assert.deepEqual([100,200].map(before), [100,200].map(after))
})
test('zero days and future dates do not shift the active-day baseline', () => {
  assert.deepEqual([0,100,200,1000].map(createActivityScale([...Array(300).fill(0),100,100,100,200,1000])), [0,1,3,4])
})
test('equal values have equal colors and input is not sorted in place', () => {
  const values=[200,100,1000,100,100], original=[...values], scale=createActivityScale(values)
  assert.deepEqual(values,original)
  assert.equal(scale(100),scale('100'))
})
test('empty, single-day, constant and malformed data produce valid levels', () => {
  assert.equal(createActivityScale([])(0),0)
  assert.equal(createActivityScale([500])(500),1)
  assert.equal(createActivityScale([100,100,100])(100),1)
  const scale=createActivityScale([NaN,Infinity,-1,0,null,'100'])
  assert.equal(scale(100),1)
  for(const value of [NaN,Infinity,-1,null,undefined,'bad'])assert.equal(scale(value),0)
})
test('intensity is monotonic and safe for large counts', () => {
  const scale=createActivityScale([100,100,100,200,1000])
  const levels=[1,20,100,120,150,200,1000,Number.MAX_VALUE].map(scale)
  assert.deepEqual(levels,[...levels].sort((a,b)=>a-b))
  assert(levels.every(level=>Number.isInteger(level)&&level>=1&&level<=4))
})
