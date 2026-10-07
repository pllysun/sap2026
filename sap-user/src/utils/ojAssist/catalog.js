// Public language and standard-library hints. This data contains no problem answers.
export function fn(label, parameters = [], returnType = '', info = '', extra = {}) {
  return { label, type: 'function', parameters, returnType, signature: label + '(' + parameters.join(', ') + ')' + (returnType ? ' → ' + returnType : ''), info, ...extra }
}
export function value(label, valueType = '', info = '', extra = {}) {
  return { label, type: 'variable', valueType, info, ...extra }
}
export function type(label, info = '') { return { label, type: 'class', valueType: label, info } }
export function profile(keywords, snippets, globals, types) {
  return { keywords: keywords.split(/\s+/).filter(Boolean), snippets, globals, types }
}
export function normalizeType(raw, language) {
  if (!raw) return ''
  let result = raw.trim().replace(/\b(?:const|volatile|struct|class|mut|dyn)\s+/g, '').replace(/&\s*'(?:\w+)\s*/g, '').replace(/^[&*\s]+|[&*\s]+$/g, '')
  if (language === 'java' && /\[\]\s*$/.test(result)) return 'array'
  if ((language === 'c' || language === 'cpp') && /\[\]\s*$/.test(result)) return 'array'
  result = result.replace(/::\s*</g, '<').replace(/<.*|\[.*|\?.*/s, '').trim()
  return result.split(/::|\./).at(-1) || ''
}
