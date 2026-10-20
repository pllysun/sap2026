export function safeRedirect(value,fallback='/oj'){
  return typeof value==='string'&&/^\/(?:oj(?:[/?#]|$)|profile(?:[?#]|$)|users(?:[?#]|$)|audit(?:[?#]|$))/.test(value)&&!value.includes('\\')?value:fallback
}
