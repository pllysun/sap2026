// GitHub 风格 emoji 短码（`:star:` → ⭐）的 marked 扩展。
// 短码表来自 gemoji 8.1.0，见 emojiShortcodes.LICENSE.txt。
import emojiShortcodes from './emojiShortcodes.json' with { type: 'json' }

const shortcodePattern = /^:([a-z0-9_+-]+):/i
const codeOpenPattern = /^<code(?=[\s>])/i
const codeClosePattern = /^<\/code\s*>/i

/**
 * marked 默认只实现了 GFM 的语法，不包含 GitHub 的 emoji 短码。
 * 返回 Marked 的扩展/钩子配置，直接展开到 new Marked({...}) 即可。
 *
 * 原生 HTML 的 <code> 不是 Markdown 代码，但 marked 会把里面的文本当普通
 * inline token 解析。这里沿 token 树记录 <code> 深度，把代码内部的 emoji
 * token 还原成原短码；不解析 HTML 字符串，因此文本里的 `<`、标签属性、
 * 嵌套标签和跨行内容都不会误判。
 */
export function createEmojiMarkdown(emojis = emojiShortcodes) {
  return {
    extensions: [
      {
        name: 'emoji',
        level: 'inline',
        start(source) {
          return source.indexOf(':')
        },
        tokenizer(source) {
          const match = shortcodePattern.exec(source)
          if (!match) return
          const name = match[1].toLowerCase()
          if (!Object.hasOwn(emojis, name)) return
          return { type: 'emoji', raw: match[0], name }
        },
        renderer(token) {
          return emojis[token.name]
        }
      }
    ],
    hooks: {
      processAllTokens(tokens) {
        protectCodeTokens(tokens)
        return tokens
      }
    }
  }

  function protectCodeTokens(tokens, initialBlockDepth = 0) {
    if (!Array.isArray(tokens)) return
    let inlineDepth = 0
    let blockDepth = initialBlockDepth

    const walk = (token) => {
      // HTML 的 <code> 不应跨 Markdown 块生效；每个段落/标题/列表项重新计数，
      // 防止未闭合的 <code> 把后面整篇文档的短码都吞掉。
      if (token.type === 'space' || token.type === 'paragraph' || token.type === 'heading' || token.type === 'list_item') {
        inlineDepth = 0
      }
      if (token.type === 'html') {
        if (token.block) {
          // 块级 <code> 可以跨段落；一个块里可能同时有开始标签和首行内容。
          if (/^<\/?code(?=[\s>])/i.test(token.raw)) {
            for (const match of token.raw.matchAll(/<code(?=[\s>])(?:[^>"']|"[^"]*"|'[^']*')*>|<\/code\s*>/gi)) {
              if (match[0][1] === '/') blockDepth = Math.max(0, blockDepth - 1)
              else blockDepth++
            }
          }
        } else if (codeOpenPattern.test(token.raw)) {
          inlineDepth++
        } else if (codeClosePattern.test(token.raw)) {
          inlineDepth = Math.max(0, inlineDepth - 1)
        }
        return
      }
      if (token.type === 'emoji' && (inlineDepth > 0 || blockDepth > 0)) {
        token.type = 'text'
        token.text = token.raw
        return
      }
      if (token.type === 'table') {
        // 每个单元格独立计数，避免一格未闭合的 <code> 影响后面的单元格。
        for (const cell of token.header || []) {
          inlineDepth = 0
          protectCodeTokens(cell.tokens, blockDepth)
        }
        for (const row of token.rows || []) {
          for (const cell of row) {
            inlineDepth = 0
            protectCodeTokens(cell.tokens, blockDepth)
          }
        }
        inlineDepth = 0
        return
      }
      if (token.type === 'list') {
        for (const item of token.items || []) walk(item)
        inlineDepth = 0
        return
      }
      if (Array.isArray(token.tokens)) {
        for (const child of token.tokens) walk(child)
      }
    }

    for (const token of tokens) walk(token)
  }
}
