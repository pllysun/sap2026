// Real language formatters run off the UI thread; code never leaves the browser.
const engines = new Map()
const clangStyle=JSON.stringify({ BasedOnStyle:'Google', IndentWidth:4, ColumnLimit:100, SortIncludes:'Never', AllowShortFunctionsOnASingleLine:'None', AllowShortBlocksOnASingleLine:'Never' })
async function engine(language) {
  const key=['c','cpp','java'].includes(language)?'clang':language
  if(!engines.has(key)) engines.set(key,(async()=>{
    if(key==='clang'){const m=await import('@wasm-fmt/clang-format/vite');await m.default();return m}
    if(key==='python'){const m=await import('@wasm-fmt/ruff_fmt/vite');await m.default();return m}
    if(key==='rust'){
      const [m,{default:url}]=await Promise.all([import('@scalar/rust-fmt'),import('@scalar/rust-fmt/wasm?url')])
      const response=await fetch(url,{method:'HEAD'})
      if(!response.ok)throw new Error('Rust 格式化资源加载失败，请重试')
      // Some static servers decode .br over HTTP; rustfmt must not decode it twice.
      const encoding=/(?:^|,)\s*br\b/i.test(response.headers.get('Content-Encoding')||'')?'none':undefined
      await m.init({url,encoding});return m
    }
    throw new Error('不支持的语言')
  })().catch(e=>{engines.delete(key);throw e}))
  return engines.get(key)
}
async function format(code,language){
  const m=await engine(language)
  if(language==='rust')return m.format(code,{edition:'2024',styleEdition:'2024',maxWidth:100})
  if(language==='python')return m.format(code,'main.py',{indent_width:4,line_width:100})
  return m.format(code,language==='java'?'Main.java':language==='c'?'main.c':'main.cpp',clangStyle)
}
let queue = Promise.resolve()
self.onmessage = ({ data: { id, code, language } }) => {
  queue = queue.then(async () => {
    try {
      if (!code.trim()) return self.postMessage({ id, result: code })
      const marker = '__USER_CODE__', hasMarker = code.includes(marker)
      const comment = language === 'python' ? '# SAP_OJ_USER_CODE_PLACEHOLDER' : '// SAP_OJ_USER_CODE_PLACEHOLDER'
      const input = hasMarker ? code.replace(marker, comment) : code
      let result = await format(input, language)
      if (hasMarker) {
        result = result.replace(comment, marker)
        if ((result.match(/__USER_CODE__/g) || []).length !== 1) throw new Error('调用驱动标记无效')
      }
      self.postMessage({ id, result })
    } catch (e) { self.postMessage({ id, error: e.message || '代码格式化失败' }) }
  })
}
