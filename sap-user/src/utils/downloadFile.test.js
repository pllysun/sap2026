import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import * as user from './downloadFile.js'
import * as admin from '../../../sap-admin/src/utils/downloadFile.js'

const origin = 'https://sap.example'
const stored = 'https://files.example/uploads/uuid.md'
const originalName = '[1-1]中文 Markdown 学习.md'
const originalBytes = new TextEncoder().encode('# 中文标题\n\n**原始内容**\n```cpp\nprintf("你好");\n```\n')
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex')

function browser(t, respond) {
  const previous = Object.fromEntries(['window', 'document', 'localStorage', 'fetch'].map(k => [k, globalThis[k]]))
  t.after(() => {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete globalThis[key]
      else globalThis[key] = value
    }
  })
  const state = { requests: [], popups: 0, clicks: [], saved: null, navigated: null, closed: false }
  globalThis.window = {
    location: { origin },
    setTimeout() {},
    open() {
      state.popups++
      return { opener: {}, location: { replace(url) { state.navigated = url } }, close() { state.closed = true } }
    }
  }
  globalThis.localStorage = { getItem(key) { return key === 'sap_token' ? 'user-session' : 'admin-session' } }
  globalThis.document = {
    body: { appendChild() {} },
    createElement() { return { click() { state.clicks.push({ href: this.href, download: this.download }) }, remove() {} } }
  }
  t.mock.method(URL, 'createObjectURL', blob => { state.saved = blob; return 'blob:download-audit' })
  globalThis.fetch = async (url, options) => {
    state.requests.push({ url: String(url), options })
    if (respond) return respond(url, options, state.requests.length)
    return state.requests.length === 1
      ? Response.json({ code: 200, data: { url: stored } })
      : new Response(originalBytes, { headers: { 'Content-Type': 'text/markdown' } })
  }
  return state
}

for (const [side, api, token] of [['user', user, 'user-session'], ['admin', admin, 'admin-session']]) {
  test(`${side}: Markdown downloads retain Chinese filename and exact UTF-8 bytes`, async t => {
    const state = browser(t)
    await api.openStoredFile(stored, originalName)
    assert.equal(state.popups, 0)
    assert.equal(state.clicks[0].download, originalName)
    assert.equal(sha256(new Uint8Array(await state.saved.arrayBuffer())), sha256(originalBytes))
    assert.equal(state.requests.length, 2)
    const proxy = new URL(state.requests[1].url)
    assert.equal(proxy.origin, origin)
    assert.equal(proxy.pathname, '/api/file/download')
    assert.equal(proxy.searchParams.get('url'), stored)
    assert.equal(proxy.searchParams.get('name'), originalName)
    for (const { url, options } of state.requests) {
      assert.equal(new URL(url, origin).origin, origin)
      assert.equal(options.headers['sap-token'], token)
      assert.equal(options.credentials, 'omit')
      assert.equal(options.redirect, 'error')
      assert.ok(!url.includes(token))
    }
  })

  test(`${side}: legacy encoded names and uppercase extensions also download`, async t => {
    const legacy = 'https://files.example/old/' + encodeURIComponent('中文课件.MD') + '?version=1'
    const state = browser(t, (_, __, index) => index === 1
      ? Response.json({ code: 200, data: { url: legacy } })
      : new Response(originalBytes))
    await api.openStoredFile(legacy)
    assert.equal(state.clicks[0].download, '中文课件.MD')
    assert.equal(new URL(state.requests[1].url).searchParams.get('url'), legacy)
    assert.equal(state.popups, 0)
  })

  test(`${side}: original Markdown filename works even when object key has no extension`, async t => {
    const state = browser(t)
    await api.openStoredFile('https://files.example/uuid', originalName)
    assert.equal(state.clicks[0].download, originalName)
    assert.equal(state.requests.length, 2)
  })

  test(`${side}: expired authentication produces an error, never a fake Markdown file`, async t => {
    const state = browser(t, (_, __, index) => index === 1
      ? Response.json({ code: 200, data: { url: stored } })
      : Response.json({ code: 401, message: '请重新登录' }))
    await assert.rejects(api.openStoredFile(stored, originalName), /请重新登录/)
    assert.equal(state.clicks.length, 0)
    assert.equal(state.saved, null)
  })

  test(`${side}: file permission rejection does not start a download`, async t => {
    const state = browser(t, () => Response.json({ code: 403, message: '下载地址不属于本站对象存储' }))
    await assert.rejects(api.openStoredFile(stored, originalName), /不属于本站/)
    assert.equal(state.requests.length, 1)
    assert.equal(state.clicks.length, 0)
  })

  test(`${side}: non-Markdown files retain object-storage navigation`, async t => {
    const pdf = 'https://files.example/course.pdf'
    const state = browser(t, () => Response.json({ code: 200, data: { url: pdf } }))
    await api.openStoredFile(pdf, '课程.pdf')
    assert.equal(state.popups, 1)
    assert.equal(state.navigated, pdf)
    assert.equal(state.requests.length, 1)
    assert.equal(state.clicks.length, 0)
  })

  test(`${side}: unsafe file addresses are rejected before network access`, async t => {
    const state = browser(t)
    for (const url of ['javascript:alert(1)', 'http://files.example/a.md', 'https://name:password@files.example/a.md']) {
      await assert.rejects(api.openStoredFile(url, 'a.md'), /下载地址无效/)
    }
    assert.equal(state.requests.length, 0)
    assert.equal(state.popups, 0)
  })

  test(`${side}: authenticated byte downloads cannot send the token to an external origin`, async t => {
    const state = browser(t)
    await assert.rejects(api.downloadFile('https://files.example/a.md', 'a.md'), /下载地址无效/)
    assert.equal(state.requests.length, 0)
  })
}
