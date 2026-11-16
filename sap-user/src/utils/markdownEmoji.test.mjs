import assert from 'node:assert/strict'
import test from 'node:test'
import { Marked } from 'marked'
import { createEmojiMarkdown } from '../../../shared/markdownEmoji.mjs'

function render(source) {
  const markdown = new Marked({
    breaks: true,
    gfm: true,
    ...createEmojiMarkdown()
  })
  return markdown.parse(source)
}

function renderWithoutEmoji(source) {
  return new Marked({ breaks: true, gfm: true }).parse(source)
}

test('渲染 GitHub emoji 短码', () => {
  assert.equal(render(':star:').trim(), '<p>⭐</p>')
  assert.equal(render(':round_pushpin:').trim(), '<p>📍</p>')
  assert.equal(render(':timer_clock:').trim(), '<p>⏲️</p>')
})

test('标题和加粗中的短码也会渲染', () => {
  assert.equal(render('# :star: 标题').trim(), '<h1>⭐ 标题</h1>')
  assert.equal(render('**:speech_balloon:**').trim(), '<p><strong>💬</strong></p>')
})

test('相邻短码和特殊名称可以渲染', () => {
  assert.equal(render(':star::star:').trim(), '<p>⭐⭐</p>')
  assert.equal(render(':+1:').trim(), '<p>👍</p>')
  assert.equal(render(':STAR:').trim(), '<p>⭐</p>')
})

test('未知短码保持原样', () => {
  assert.equal(render(':not_a_real_emoji_name:').trim(), '<p>:not_a_real_emoji_name:</p>')
})

test('行内代码和代码块中的短码不替换', () => {
  assert.equal(render('`:star:`').trim(), '<p><code>:star:</code></p>')
  assert.equal(render('```\n:star:\n```').trim(), '<pre><code>:star:\n</code></pre>')
})

test('原生 HTML code 元素中的短码不替换', () => {
  assert.equal(render('<code>:star:</code>').trim(), '<p><code>:star:</code></p>')
  assert.equal(
    render('<code class="lang">:star:</code> :star:').trim(),
    '<p><code class="lang">:star:</code> ⭐</p>'
  )
  assert.equal(
    render('[<code>:star:</code>](https://example.com)').trim(),
    '<p><a href="https://example.com"><code>:star:</code></a></p>'
  )
  // 属性里的短码不算内容，不能因此让 code 内的 Markdown 失效。
  assert.equal(
    render('<code title=":star:">**bold**</code>').trim(),
    '<p><code title=":star:"><strong>bold</strong></code></p>'
  )
  // 多行仍按 breaks: true 显示 <br>，只是不替换短码。
  assert.equal(
    render('<code>first\n:star:\nlast</code>').trim(),
    '<p><code>first<br>:star:<br>last</code></p>'
  )
  // 嵌套标签的跨行属性不能被改成 <br>。
  assert.equal(
    render('<code><span\nclass="x">:star:</span></code>').trim(),
    '<p><code><span\nclass="x">:star:</span></code></p>'
  )
  // 嵌套标签属性里的短码同样不算内容。
  assert.equal(
    render('<code><span title=":star:">**bold**</span></code>').trim(),
    '<p><code><span title=":star:"><strong>bold</strong></span></code></p>'
  )
  // 代码文本里的 < > 不能被当成 HTML 标签。
  assert.equal(
    render('<code>1 < 2\n3 > 1 :star:</code>').trim(),
    '<p><code>1 < 2<br>3 > 1 :star:</code></p>'
  )
  assert.equal(
    render('<code>1 < 2 :star: > 0</code>').trim(),
    '<p><code>1 < 2 :star: > 0</code></p>'
  )
})

test('块级 HTML code 不影响后面的正文', () => {
  assert.equal(
    render('<code>\n:star:\n</code>\n\n:star:').trim(),
    '<code>\n:star:\n</code><p>⭐</p>'
  )
})

test('块级 code 跨段落时仍保护短码', () => {
  assert.equal(
    render('<code>\n\n:star:\n\n</code>').trim(),
    '<code><p>:star:</p>\n</code>'
  )
  assert.equal(
    render('<code>\nfirst\n\n:star:\n\n</code>').trim(),
    '<code>\nfirst<p>:star:</p>\n</code>'
  )
})

test('表格单元格之间的 code 状态互不影响', () => {
  const html = render('| a | b |\n|---|---|\n| <code>:star: | :star: |')
  assert.match(html, /<td><code>:star:<\/td>/)
  assert.match(html, /<td>⭐<\/td>/)
})

test('未闭合的行内 code 不会吞掉后续段落', () => {
  assert.equal(
    render('<code>:star:\n\n:star:').trim(),
    '<p><code>:star:</p>\n<p>⭐</p>'
  )
  assert.equal(
    render('- <code>:star:\n- :star:').trim(),
    '<ul>\n<li><code>:star:</li>\n<li>⭐</li>\n</ul>'
  )
})

test('同一个 Marked 实例多次解析时 code 状态会重置', () => {
  const markdown = new Marked({
    breaks: true,
    gfm: true,
    ...createEmojiMarkdown()
  })
  assert.equal(markdown.parse('<code>:star:</code>').trim(), '<p><code>:star:</code></p>')
  assert.equal(markdown.parse(':star:').trim(), '<p>⭐</p>')
})

test('HTML 属性和链接地址中的短码不替换', () => {
  assert.equal(render('<a title=":star:">x</a>').trim(), '<p><a title=":star:">x</a></p>')
  assert.match(render('[x](https://example.com/:star:)'), /href="https:\/\/example\.com\/:star:"/)
})

test('裸 URL 的解析结果不受扩展影响', () => {
  const source = 'https://example.com/:star:'
  assert.equal(render(source), renderWithoutEmoji(source))
})

test('转义的短码不替换', () => {
  assert.equal(render('\\:star:').trim(), '<p>:star:</p>')
})

test('只替换链接文字，不改链接地址', () => {
  const html = render('[图标 :star:](https://example.com/:star:)')
  assert.match(html, /图标 ⭐/)
  assert.match(html, /href="https:\/\/example\.com\/:star:"/)
})

test('块级 HTML code 跨表格时保护表头和单元格，关闭后恢复替换', () => {
  const source = '<code>\n\n| :star: | x |\n|---|---|\n| :star: | :star: |\n\n</code>\n\n:star:'
  const html = render(source)
  assert.match(html, /<th>:star:<\/th>/)
  assert.match(html, /<td>:star:<\/td>/)
  assert.doesNotMatch(html, /<th>⭐<\/th>|<td>⭐<\/td>/)
  assert.match(html, /<\/code><p>⭐<\/p>/)
})

test('表格单元格内的嵌套 code 只保护本格，属性短码保持原样', () => {
  const source = '| a | b |\n|---|---|\n| <code title=":star:"><code>:star:</code> :star:</code> | :star: |'
  const html = render(source)
  assert.match(html, /<td><code title=":star:"><code>:star:<\/code> :star:<\/code><\/td>/)
  assert.match(html, /<td>⭐<\/td>/)
})
