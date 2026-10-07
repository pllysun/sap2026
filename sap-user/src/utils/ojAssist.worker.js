import { createAnalyzer } from './ojAssist/analyzer.js'
import { loadLanguage } from './ojAssist/languages.js'
let analyzer, selectedLanguage, boot
async function initialize(language) {
  if (selectedLanguage !== language || !boot) {
    selectedLanguage = language
    boot = loadLanguage(language).then(({ parser, catalog }) => { analyzer = createAnalyzer(parser, catalog, language); return analyzer })
  }
  return boot
}
// One worker is created per editable editor and recreated on language changes.
self.onmessage = async ({ data }) => {
  const { id, language, kind, code = '', position = 0, explicit = false } = data
  if (typeof code !== 'string' || code.length > 65536 || !Number.isInteger(position) || position < 0 || position > code.length) {
    self.postMessage({ id, error: '代码超出分析范围' }); return
  }
  try {
    const engine = await initialize(language)
    const result = kind === 'init' ? true : kind === 'complete' ? engine.complete(code, position, explicit)
      : kind === 'signature' ? engine.signature(code, position) : kind === 'hover' ? engine.hover(code, position)
      : kind === 'diagnostics' ? engine.diagnostics(code) : null
    self.postMessage({ id, result })
  } catch { self.postMessage({ id, error: '本地分析暂不可用' }) }
}
