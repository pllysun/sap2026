const scalar = value => typeof value === 'string' ? value : ''

export function readCatalogQuery(query = {}) {
  return {
    keyword: scalar(query.keyword).slice(0, 200),
    difficulty: ['EASY', 'MEDIUM', 'HARD'].includes(query.difficulty) ? query.difficulty : '',
    tag: scalar(query.tag), source: scalar(query.source),
    mode: ['STDIO', 'FUNCTION'].includes(query.mode) ? query.mode : '',
    page: Math.max(1, Math.min(100000, Math.floor(Number(query.page)) || 1))
  }
}

export function catalogQuery(state, tab = 'problems') {
  return Object.fromEntries(Object.entries({ ...readCatalogQuery(state), tab }).filter(([key, value]) => value && !(key === 'page' && value === 1)))
}

// Accept only known entry pages; never trust an arbitrary return URL.
export function catalogReturn(value) {
  if (value === '/home#home-algorithms') return value
  if (typeof value !== 'string' || !/^\/oj(?:\?|$)/.test(value)) return '/oj'
  const url = new URL(value, 'https://local.invalid')
  const query = Object.fromEntries(url.searchParams)
  const params = new URLSearchParams(catalogQuery(readCatalogQuery(query), ['sets', 'ranking'].includes(query.tab) ? query.tab : 'problems'))
  return '/oj?' + params.toString()
}
