import { readFileSync, statSync } from 'node:fs'
import { resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

/** Check the URLs actually emitted into HTML, rather than guessing asset paths. */
export function verifyAdminBuild(directory, base = '/admin/') {
  const root = resolve(directory)
  const origin = 'https://build.invalid'
  const entryUrl = new URL(base.endsWith('/') ? base : `${base}/`, origin)
  const html = readFileSync(resolve(root, 'index.html'), 'utf8')
  const references = [...html.matchAll(/<(script|link)\b[^>]*?\b(?:src|href)=["']([^"']+)["'][^>]*>/g)]
  if (!references.some(([, tag, url]) => tag === 'script' && /\.js(?:\?|$)/.test(url))) {
    throw new Error('管理端构建缺少脚本入口')
  }
  for (const [, , reference] of references) {
    const url = new URL(reference, entryUrl)
    if (url.origin !== entryUrl.origin || !url.pathname.startsWith(entryUrl.pathname)) {
      throw new Error(`管理端资源未使用构建前缀 ${entryUrl.pathname}: ${reference}`)
    }
    const file = resolve(root, decodeURIComponent(url.pathname.slice(entryUrl.pathname.length)))
    if (!file.startsWith(root + sep) || !statSync(file).isFile()) {
      throw new Error(`管理端构建资源不存在: ${reference}`)
    }
  }
  return { base: entryUrl.pathname, references: references.length }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = verifyAdminBuild(
    process.argv[2] || fileURLToPath(new URL('../dist/', import.meta.url)),
    process.env.VITE_BASE_URL || '/admin/',
  )
  console.log(`管理端构建资源检查通过：${result.base}，${result.references} 个入口资源`)
}
