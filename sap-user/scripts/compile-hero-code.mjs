import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createHighlighter } from 'shiki'
import { codeToKeyedTokens } from '@shikijs/magic-move/core'
import { heroLanguages } from '../src/components/heroLanguages.js'

// Syntax grammars run only here. The browser receives a small set of prepared tokens.
const languages = { js: 'javascript', ts: 'typescript', shell: 'shellscript' }
const theme = {
  name: 'sap-hero-light', type: 'light',
  colors: { 'editor.background': '#ffffff', 'editor.foreground': '#34425e' },
  tokenColors: [
    { scope: ['keyword', 'storage', 'meta.preprocessor'], settings: { foreground: '#405ac3' } },
    { scope: ['string'], settings: { foreground: '#177b76' } },
    { scope: ['constant.numeric', 'constant.language'], settings: { foreground: '#a15f30' } },
    { scope: ['comment'], settings: { foreground: '#748094' } },
    { scope: ['entity.name.function', 'support.function'], settings: { foreground: '#8b4b9c' } },
    { scope: ['entity.name.type', 'support.type'], settings: { foreground: '#286c96' } },
  ],
}
const highlighter = await createHighlighter({
  themes: [theme], langs: heroLanguages.map(item => languages[item.id] || item.id),
})
try {
  const steps = heroLanguages.map(item => codeToKeyedTokens(highlighter, item.code,
    { lang: languages[item.id] || item.id, theme: theme.name }, false))
  const directory = new URL('../src/generated/', import.meta.url)
  await mkdir(directory, { recursive: true })
  await writeFile(new URL('hero-code-tokens.json', directory), JSON.stringify(steps) + '\n')
  console.log(`Prepared ${steps.length} homepage code examples: ${fileURLToPath(directory)}`)
} finally { highlighter.dispose() }
