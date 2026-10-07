import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readCatalogQuery, catalogQuery, catalogReturn } from './ojNavigation.js'

test('filters and pagination survive a catalogue / workspace round trip', () => {
  const filters = { keyword: '两数', difficulty: 'EASY', tag: '数组', source: 'LeetCode', mode: 'FUNCTION', page: 2 }
  const path = '/oj?' + new URLSearchParams(catalogQuery(filters)).toString()
  assert.deepEqual(readCatalogQuery(Object.fromEntries(new URL(catalogReturn(path), 'https://local.invalid').searchParams)), filters)
})

test('untrusted return paths never leave known entry pages or preserve unexpected parameters', () => {
  for (const path of ['https://elsewhere.invalid', '//elsewhere.invalid', '/oj/123', '/oj\\evil', ['bad'], undefined]) assert.equal(catalogReturn(path), '/oj')
  assert.equal(catalogReturn('/oj?tab=ranking&token=private&from=elsewhere'), '/oj?tab=ranking')
  assert.equal(catalogReturn('/home#home-algorithms'), '/home#home-algorithms')
  for (const path of ['/home?token=private', '/home#other', '/home#home-algorithms?redirect=https://elsewhere.invalid']) assert.equal(catalogReturn(path), '/oj')
})

test('malformed query arrays, invalid modes, and page numbers produce usable filters', () => {
  const value = readCatalogQuery({ keyword: ['a', 'b'], difficulty: 'HACK', mode: 'BAD', page: -8 })
  assert.deepEqual(value, { keyword: '', difficulty: '', tag: '', source: '', mode: '', page: 1 })
  assert.equal(readCatalogQuery({ page: 'NaN' }).page, 1)
  assert.equal(readCatalogQuery({ page: '3.5' }).page, 3)
})
