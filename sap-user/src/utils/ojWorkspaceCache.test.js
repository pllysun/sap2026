import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createWorkspaceCache } from './ojWorkspaceCache.js'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('concurrent panel opens share a request and reuse its completed value', async () => {
  const cache = createWorkspaceCache(), request = deferred()
  let calls = 0
  const loader = () => { calls++; return request.promise }
  const first = cache.load('history:account1:problem1:1', loader)
  const second = cache.load('history:account1:problem1:1', loader)
  request.resolve(['submission'])
  assert.deepEqual(await first, ['submission'])
  assert.deepEqual(await second, ['submission'])
  assert.deepEqual(await cache.load('history:account1:problem1:1', loader), ['submission'])
  assert.equal(calls, 1)
})

test('failed requests can be retried without storing an error', async () => {
  const cache = createWorkspaceCache()
  await assert.rejects(cache.load('ranking:cpp:STDIO', () => Promise.reject(new Error('offline'))), /offline/)
  assert.equal(await cache.load('ranking:cpp:STDIO', () => 2), 2)
})

test('manual refresh replaces a cached response and shares an ongoing refresh', async () => {
  const cache = createWorkspaceCache(), refresh = deferred()
  await cache.load('history:1', () => 1)
  const first = cache.load('history:1', () => refresh.promise, true)
  const second = cache.load('history:1', () => assert.fail('duplicate refresh'), true)
  const switchedPanel = cache.load('history:1', () => assert.fail('panel should await the refresh'))
  refresh.resolve(2)
  assert.equal(await first, 2)
  assert.equal(await second, 2)
  assert.equal(await switchedPanel, 2)
  assert.equal(await cache.load('history:1', () => assert.fail('unexpected request')), 2)
})

test('completion invalidates history without disturbing cached result details', async () => {
  const cache = createWorkspaceCache()
  cache.remember('history:account1:page1', ['old'])
  cache.remember('history:account1:page2', ['old'])
  cache.remember('job:account1:100', 'source')
  cache.invalidate('history:')
  assert.deepEqual(await cache.load('history:account1:page1', () => ['new']), ['new'])
  assert.equal(await cache.load('job:account1:100', () => assert.fail('details discarded')), 'source')
})

test('a response from before invalidation cannot overwrite the next request', async () => {
  const cache = createWorkspaceCache(), stale = deferred()
  const old = cache.load('history:account1', () => stale.promise)
  await Promise.resolve()
  cache.invalidate()
  assert.equal(await cache.load('history:account1', () => 'new'), 'new')
  stale.resolve('old')
  assert.equal(await old, 'old')
  assert.equal(await cache.load('history:account1', () => assert.fail('new response lost')), 'new')
})

test('the bounded cache retains recently visited filters and isolates workspaces', async () => {
  const cache = createWorkspaceCache(2), other = createWorkspaceCache()
  cache.remember('ranking:cpp', 'C++')
  cache.remember('ranking:java', 'Java')
  await cache.load('ranking:cpp', () => assert.fail('C++ should be cached'))
  cache.remember('ranking:rust', 'Rust')
  assert.equal(await cache.load('ranking:cpp', () => assert.fail('recent filter evicted')), 'C++')
  assert.equal(await cache.load('ranking:java', () => 'fresh Java'), 'fresh Java')
  assert.equal(await other.load('ranking:cpp', () => 'other account'), 'other account')
})
