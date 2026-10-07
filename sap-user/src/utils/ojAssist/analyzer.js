import { fn, normalizeType } from './catalog.js'

const identifiers = new Set(['Identifier', 'Definition', 'VariableName', 'BoundIdentifier', 'FieldIdentifier'])
const declarationTypes = /^(?:PrimitiveType|TypeName|ScopedTypeName|GenericType|ArrayType|TemplateType|TypeIdentifier|StructSpecifier|SizedTypeSpecifier|TypeDescriptor)$/
const functionNodes = { c: 'FunctionDefinition', cpp: 'FunctionDefinition', java: 'MethodDeclaration', python: 'FunctionDefinition', rust: 'FunctionItem' }
const children = node => { const result = []; for (let child = node.firstChild; child; child = child.nextSibling) result.push(child); return result }
const child = (node, name) => children(node).find(item => item.name === name)
function firstIdentifier(node) {
  if (identifiers.has(node.name)) return node
  for (const item of children(node)) {
    if (declarationTypes.test(item.name) || /TypeArg|Argument|ParameterList|ParamList/.test(item.name)) continue
    const found = firstIdentifier(item); if (found) return found
  }
  return null
}
function genericArguments(raw) {
  const opening = raw?.search(/[<[]/)
  if (opening < 0 || opening == null) return []
  let closing = raw.length, balance = 1
  for (let i = opening + 1; i < raw.length; i++) {
    if ('<['.includes(raw[i])) balance++
    else if ('>]'.includes(raw[i]) && --balance === 0) { closing = i; break }
  }
  const content = raw.slice(opening + 1, closing), result = []; let depth = 0, start = 0
  for (let i = 0; i < content.length; i++) {
    if ('<['.includes(content[i])) depth++
    else if ('>]'.includes(content[i])) depth--
    else if (content[i] === ',' && depth === 0) { result.push(content.slice(start, i).trim()); start = i + 1 }
  }
  result.push(content.slice(start).trim())
  return result
}
function instantiate(raw, owner) {
  const args = genericArguments(owner)
  if (raw === '$element') return args[0] || ''
  if (raw === '$value') return args[1] || ''
  return (raw || '').replace(/\bT\b/g, args[0] || 'T').replace(/\bV\b/g, args[1] || 'V')
}
function scopeContains(scope, position) { return scope.from <= position && (position < scope.to || scope.open && position === scope.to) }
function splitChain(raw) {
  const parts = []; let depth = 0, start = 0
  for (let i = 0; i < raw.length; i++) {
    if ('([<'.includes(raw[i])) depth++
    else if (')]>'.includes(raw[i]) && depth > 0) depth--
    else if (!depth && (raw[i] === '.' || raw.slice(i, i + 2) === '::' || raw.slice(i, i + 2) === '->')) {
      parts.push(raw.slice(start, i)); i += raw[i] === '.' ? 0 : 1; start = i + 1
    }
  }
  parts.push(raw.slice(start))
  return parts.filter(Boolean)
}
function receiverBefore(code, end) {
  let start = end, depth = 0
  while (start > 0) {
    const c = code[start - 1]
    if (')]>'.includes(c)) depth++
    else if ('([<'.includes(c)) { if (!depth) break; depth-- }
    else if (!depth && /[\s=;,{}+*/!?&|]/.test(c)) break
    start--
  }
  return code.slice(start, end).trim()
}
function wordAt(code, position) {
  const left = code.slice(0, position).match(/[\p{L}_][\p{L}\p{N}_]*!?$/u)
  const right = code.slice(position).match(/^[\p{L}\p{N}_]*!?/u)
  if (!left && !right?.[0]) return null
  const from = position - (left?.[0].length || 0), to = position + (right?.[0].length || 0)
  return { from, to, name: code.slice(from, to) }
}

// Lightweight single-file analysis. It never evaluates, compiles, or executes source.
export function createAnalyzer(parser, catalog, language) {
  let cached
  function build(code) {
    if (cached?.code === code) return cached
    const tree = parser.parse(code)
    const root = { from: 0, to: code.length, depth: 0, open: true, owner: '', parent: null }
    const index = { code, tree, root, symbols: [], members: new Map(), ignored: [], parameterRanges: [], errors: [] }
    const text = node => code.slice(node.from, node.to)
    function makeScope(node, parent, owner = parent.owner) {
      const lastLine = code.slice(code.lastIndexOf('\n') + 1)
      const linePrefix = code.slice(code.lastIndexOf('\n', node.from - 1) + 1, node.from)
      const open = language === 'python'
        ? node.to === code.length && (lastLine.match(/^[ \t]*/)[0].length > linePrefix.match(/^[ \t]*/)[0].length)
        : !/\}\s*;?\s*$/.test(text(node))
      return { from: node.from, to: node.to, depth: parent.depth + 1, open, parent, owner }
    }
    function add(nameNode, scope, valueType = '', extra = {}) {
      if (!nameNode) return
      const label = text(nameNode)
      if (!/^[\p{L}_][\p{L}\p{N}_]*$/u.test(label) || label === '__USER_CODE__') return
      const symbol = { label, type: 'variable', valueType, from: nameNode.from, scope, ...extra }
      index.symbols.push(symbol)
      if (symbol.member && scope.owner) {
        if (!index.members.has(scope.owner)) index.members.set(scope.owner, [])
        index.members.get(scope.owner).push(symbol)
      }
      return symbol
    }
    function infer(raw, scope, position = scope.from) {
      raw = (raw || '').trim()
      if (!raw) return ''
      if (language === 'python') {
        if (/^(?:[rubf]*["'])/i.test(raw)) return 'str'
        if (raw.startsWith('[')) return 'list'
        if (raw.startsWith('{')) return raw.includes(':') || raw === '{}' ? 'dict' : 'set'
        if (raw.startsWith('(') && raw.includes(',')) return 'tuple'
      }
      if (language === 'java' && /^"/.test(raw)) return 'String'
      if (language === 'cpp' && /^"/.test(raw)) return 'const char *'
      if (language === 'rust' && /^"/.test(raw)) return 'str'
      if (language === 'rust' && /^vec!\s*\[/.test(raw)) return 'Vec'
      if (/^(?:true|false|True|False)$/.test(raw)) return language === 'java' ? 'boolean' : 'bool'
      if (/^[+-]?\d+(?:\.\d+)?(?:[uif]\d+|[lLfF])?$/.test(raw)) return raw.includes('.') ? 'float' : language === 'rust' ? 'i32' : 'int'
      if (/(?:\.|\)\s*::)/.test(raw)) {
        const resolved = resolve(index, raw.replace(/^new\s+/, ''), position)
        if (resolved.raw) return resolved.raw
      }
      const constructor = raw.match(/^(?:new\s+)?([\p{L}_][\p{L}\p{N}_:.]*(?:\s*<[^;()]*>)?)\s*(?:\(|\{|::(?:new|from|with_capacity))/u)
      if (constructor) {
        const name = constructor[1].replace(/::(?:new|from|with_capacity)$/, '').replace(/::$/, '')
        const normalized = normalizeType(name, language)
        if (catalog.types[normalized] || index.members.has(normalized)) return name
        const global = catalog.globals.find(item => item.label === name)
        if (global?.returnType) return global.returnType
      }
      const alias = raw.match(/^([\p{L}_][\p{L}\p{N}_]*)/u)?.[1]
      if (alias) {
        const prior = index.symbols.filter(item => item.label === alias && item.from < scope.to && item.scope.from <= scope.from && item.scope.to >= scope.to).at(-1)
        if (prior?.valueType) return prior.valueType
        const global = catalog.globals.find(item => item.label === alias)
        if (/^\s*[\w]+\s*\(/.test(raw) && global?.returnType) return global.returnType
      }
      return ''
    }
    function addParameters(parameters, scope) {
      if (!parameters) return []
      index.parameterRanges.push({ from: parameters.from, to: parameters.to })
      const result = [], items = children(parameters)
      if (language === 'python') {
        for (let i = 0; i < items.length; i++) {
          if (items[i].name !== 'VariableName') continue
          const name = items[i], annotation = items[i + 1]?.name === 'TypeDef' ? text(items[i + 1]).replace(/^:\s*/, '') : ''
          add(name, scope, text(name) === 'self' ? scope.owner : annotation, { hoisted: true })
          if (text(name) !== 'self' && text(name) !== 'cls') result.push(text(name) + (annotation ? ': ' + annotation : ''))
        }
      } else {
        for (const item of items) {
          if (!['ParameterDeclaration', 'FormalParameter', 'Parameter', 'SelfParameter'].includes(item.name)) continue
          const name = firstIdentifier(item) || children(item).find(n => n.name === 'self')
          if (!name) continue
          const raw = text(item)
          let valueType = language === 'rust' ? raw.slice(raw.indexOf(':') + 1).trim() : code.slice(item.from, name.from).replace(/\bfinal\s+/g, '').trim()
          if (language === 'rust' && !raw.includes(':')) valueType = scope.owner
          add(name, scope, valueType, { hoisted: true })
          if (text(name) !== 'self') result.push(raw)
        }
      }
      return result
    }
    function declaration(node, scope, member = false) {
      const kids = children(node)
      if (language === 'java') {
        const valueType = text(kids.find(n => declarationTypes.test(n.name) || n.name === 'var') || { from: node.from, to: node.from })
        for (const item of kids.filter(n => n.name === 'VariableDeclarator')) {
          const name = child(item, 'Definition'), expression = children(item).find((n, i, items) => i > 0 && items[i - 1].name === 'AssignOp')
          add(name, scope, valueType === 'var' ? infer(expression && text(expression), scope, node.from) : valueType, { member, hoisted: member })
        }
      } else if (language === 'rust') {
        const equal = kids.findIndex(n => n.name === '='), names = equal >= 0 ? kids.slice(0, equal) : kids
        const name = names.find(n => n.name === 'BoundIdentifier' || n.name === 'FieldIdentifier'), colon = names.findIndex(n => n.name === ':')
        const valueType = colon >= 0 ? code.slice(names[colon].to, equal < 0 ? node.to : kids[equal].from).replace(/;\s*$/, '').trim() : infer(equal >= 0 ? text(kids[equal + 1] || { from: 0, to: 0 }) : '', scope, node.from)
        add(name, scope, valueType, { member, hoisted: member })
      } else if (language === 'python') {
        const equal = kids.findIndex(n => n.name === 'AssignOp'), annotation = kids.find(n => n.name === 'TypeDef')
        const valueType = annotation ? text(annotation).replace(/^:\s*/, '') : infer(equal >= 0 ? code.slice(kids[equal].to, node.to) : '', scope, node.from)
        const left = kids[0]
        if (left?.name === 'MemberExpression' && /^self\./.test(text(left)) && scope.owner) {
          const name = children(left).filter(n => n.name === 'PropertyName' || n.name === 'VariableName').at(-1)
          if (name && text(name) !== 'self') add(name, scope, valueType, { member: true, hoisted: true, memberOnly: true })
        } else if (left) {
          const names = left.name === 'VariableName' ? [left] : children(left).filter(n => n.name === 'VariableName')
          for (const name of names) add(name, scope, valueType, { member, memberOnly: member, hoisted: member })
        }
      } else {
        const declarators = kids.filter(n => identifiers.has(n.name) || /^(?:Init|Array|Pointer|Reference|Function)Declarator$/.test(n.name))
        for (const item of declarators) {
          const name = firstIdentifier(item); if (!name) continue
          const valueType = code.slice(node.from, declarators[0].from).replace(/\b(?:static|extern|register|typedef)\s+/g, '').trim()
          const equal = text(item).indexOf('='), raw = equal < 0 ? '' : text(item).slice(equal + 1)
          if (item.name === 'FunctionDeclarator') {
            const parameters = child(item, 'ParameterList'), params = parameters ? children(parameters).filter(n => n.name === 'ParameterDeclaration').map(text) : []
            add(name, scope, '', { ...fn(text(name), params, valueType, '当前文件中的函数。'), member, hoisted: true })
          } else {
            const array = /\[/.test(code.slice(name.to, item.to)) ? '[]' : ''
            add(name, scope, valueType === 'auto' ? infer(raw, scope, node.from) : valueType + array, { member, hoisted: member })
          }
        }
      }
    }
    function visit(node, scope) {
      if (/Comment|^(?:String|StringLiteral|CharLiteral|CharacterLiteral|RawString|FormatString|SystemLibString)$/.test(node.name)) {
        const open = /LineComment/.test(node.name) || /BlockComment/.test(node.name) ? !/\*\/$/.test(text(node))
          : node.name === 'SystemLibString' ? !/>$/.test(text(node)) : !/["']\s*$/.test(text(node))
        index.ignored.push({ from: node.from, to: node.to, open: node.to === code.length && open })
        return
      }
      if (node.type.isError && index.errors.length < 20) index.errors.push({ from: node.from, to: node.to })
      const kids = children(node)
      const classBody = kids.find(n => ['ClassBody', 'InterfaceBody', 'EnumBody', 'FieldDeclarationList', 'DeclarationList'].includes(n.name))
      const isClass = ['ClassDeclaration', 'InterfaceDeclaration', 'EnumDeclaration', 'StructItem', 'EnumItem', 'TraitItem', 'ImplItem'].includes(node.name)
        || ['ClassSpecifier', 'StructSpecifier'].includes(node.name) && classBody
        || language === 'python' && node.name === 'ClassDefinition'
      if (isClass) {
        const name = kids.find(n => ['Definition', 'VariableName', 'TypeIdentifier', 'GenericType'].includes(n.name))
        const owner = name ? normalizeType(text(name), language) : ''
        if (owner) {
          if (!index.members.has(owner)) index.members.set(owner, [])
          if (node.name !== 'ImplItem') add(name, scope, owner, { type: 'class', hoisted: true, info: '当前文件中的类型。' })
        }
        const inner = makeScope(node, scope, owner)
        for (const item of kids) visit(item, inner)
        return
      }
      // An unfinished C/C++ statement can make the parser classify a function
      // body as a braced initializer. Keep its parameters available while typing.
      const initializer = (language === 'c' || language === 'cpp') && ['Declaration', 'FieldDeclaration'].includes(node.name)
        ? child(node, 'InitDeclarator') || node : null
      const recoveredDeclarator = initializer && child(initializer, 'FunctionDeclarator')
      const recoveredBody = initializer && child(initializer, 'InitializerList')
      if (node.name === functionNodes[language] || recoveredDeclarator && recoveredBody) {
        const declarator = child(node, 'FunctionDeclarator') || recoveredDeclarator
        const name = language === 'cpp' || language === 'c' ? declarator && firstIdentifier(declarator)
          : kids.find(n => ['Definition', 'VariableName', 'BoundIdentifier'].includes(n.name))
        const parameters = declarator ? child(declarator, 'ParameterList') : kids.find(n => ['FormalParameters', 'ParamList'].includes(n.name))
        const inner = makeScope(node, scope)
        const params = addParameters(parameters, inner)
        const returnNode = kids.find(n => declarationTypes.test(n.name) || n.name === 'TypeDef')
        let returnType = returnNode ? text(returnNode).replace(/^->\s*/, '') : ''
        if (language === 'rust') { const arrow = kids.findIndex(n => n.name === '->'); returnType = arrow >= 0 ? text(kids[arrow + 1]) : '()' }
        const member = Boolean(scope.owner) && !scope.parent?.owner
        const info = name ? fn(text(name), params, returnType, '当前文件中的' + (member ? '方法。' : '函数。')) : {}
        add(name, scope, '', { ...info, member, memberOnly: member && language === 'python', hoisted: true })
        if (recoveredBody) visit(recoveredBody, inner)
        else for (const item of kids) if (item !== parameters && item !== declarator) visit(item, inner)
        return
      }
      const isBlock = language === 'python' ? false : ['Block', 'CompoundStatement', 'ForStatement', 'ForExpression', 'ForRangeLoop', 'EnhancedForStatement', 'LambdaExpression'].includes(node.name)
      if (isBlock) scope = makeScope(node, scope)
      if (language === 'java' && node.name === 'EnhancedForStatement') {
        const spec = child(node, 'ForSpec'), name = spec && child(spec, 'Definition')
        const valueType = spec && children(spec).find(n => declarationTypes.test(n.name))
        add(name, scope, valueType ? text(valueType) : '')
      }
      if (language === 'cpp' && node.name === 'ForRangeLoop') {
        const declaration = kids.find(n => identifiers.has(n.name) || /(?:Reference|Pointer)Declarator/.test(n.name))
        const name = declaration && firstIdentifier(declaration), opening = kids.find(n => n.name === '(')
        const iterable = name && kids.find(n => n.from > name.to && ![')', 'CompoundStatement'].includes(n.name))
        const declared = name && code.slice(opening?.to || node.from, name.from).trim()
        const owner = iterable && resolve(index, text(iterable), node.from).raw
        add(name, scope, /\bauto\b/.test(declared || '') ? instantiate('$element', owner) : declared || '')
      }
      if (language === 'rust' && node.name === 'ForExpression') {
        const name = kids.find(n => n.name === 'BoundIdentifier'), into = kids.findIndex(n => n.name === 'in')
        const iterable = into < 0 ? null : kids[into + 1]
        const owner = iterable && resolve(index, text(iterable).replace(/^&(?:mut\s+)?\s*/, ''), node.from).raw
        add(name, scope, instantiate('$element', owner))
      }
      if (language === 'java' && ['LocalVariableDeclaration', 'FieldDeclaration'].includes(node.name)
        || (language === 'cpp' || language === 'c') && ['Declaration', 'FieldDeclaration'].includes(node.name)
        || language === 'rust' && ['LetDeclaration', 'FieldDeclaration'].includes(node.name)
        || language === 'python' && node.name === 'AssignStatement') {
        declaration(node, scope, node.name === 'FieldDeclaration' || language === 'python' && scope.owner && scope.parent === root)
      }
      if (language === 'python' && node.name === 'ImportStatement') {
        const raw = text(node), imported = raw.match(/^from\s+([\w.]+)\s+import\s+(.+)/)
        const entries = (imported ? imported[2] : raw.replace(/^import\s+/, '')).split(',')
        for (const entry of entries) {
          const match = entry.trim().match(/^([\w.]+)(?:\s+as\s+(\w+))?$/); if (!match) continue
          const original = match[1], label = match[2] || original.split('.')[0], offset = raw.indexOf(label)
          const hint = imported ? catalog.types[imported[1]]?.find(item => item.label === original) : null
          const valueType = hint?.valueType || original
          add({ from: node.from + offset, to: node.from + offset + label.length }, scope, valueType, { ...(hint || {}), label, type: hint?.type || (catalog.types[valueType] ? imported ? 'class' : 'namespace' : 'variable') })
        }
      }
      if (language === 'python' && node.name === 'ForStatement') {
        const into = kids.findIndex(n => n.name === 'in'), iterable = into < 0 ? null : kids[into + 1]
        const sourceType = iterable ? infer(text(iterable), scope, node.from) : ''
        for (const item of kids.slice(0, into)) if (item.name === 'VariableName') add(item, scope, instantiate('$element', sourceType))
      }
      for (const item of kids) visit(item, scope)
    }
    visit(tree.topNode, root)
    cached = index
    return index
  }
  function visible(index, position) {
    const result = new Map()
    for (const symbol of index.symbols.filter(item => !item.memberOnly && scopeContains(item.scope, position) && (item.hoisted || item.from <= position))
      .sort((a, b) => b.scope.depth - a.scope.depth || b.from - a.from)) {
      if (!result.has(symbol.label)) result.set(symbol.label, symbol)
    }
    return result
  }
  function members(index, raw, staticOnly = false) {
    const name = normalizeType(raw, language)
    const options = index.members.get(name) || catalog.types[name] || []
    return options.filter(item => !staticOnly || item.static || item.type === 'class' || item.type === 'namespace')
      .map(item => {
        const valueType = instantiate(item.valueType, raw), returnType = instantiate(item.returnType, raw)
        const parameters = item.parameters?.map(parameter => instantiate(parameter, raw))
        const signature = parameters ? item.label + '(' + parameters.join(', ') + ')' + (returnType ? ' → ' + returnType : '') : item.signature
        return { ...item, valueType, returnType, parameters, signature }
      })
  }
  function resolve(index, expression, position) {
    const locals = visible(index, position), parts = splitChain(expression.trim())
    let raw = '', staticOnly = false
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i], match = part.match(/^([\p{L}_][\p{L}\p{N}_]*!?)/u)
      if (!match) return { raw: '', staticOnly: false }
      const name = match[1], called = /[([]/.test(part.slice(name.length).replace(/^<[^>]*>/, ''))
      let hint
      if (!i) {
        hint = locals.get(name) || catalog.globals.find(item => item.label === name)
        if (name === 'this' || name === 'self' || name === 'Self') {
          const context = index.symbols.filter(item => item.scope.owner && scopeContains(item.scope, position)).sort((a, b) => b.scope.depth - a.scope.depth)[0]
          raw = context?.scope.owner || ''; staticOnly = name === 'Self'; continue
        }
        if (!hint && (catalog.types[name] || index.members.has(name))) hint = { valueType: name, type: 'class' }
      } else hint = members(index, raw, staticOnly).find(item => item.label === name)
      if (!hint) return { raw: '', staticOnly: false }
      raw = called ? hint.returnType || hint.valueType || '' : hint.valueType || hint.returnType || ''
      staticOnly = !called && ['class', 'namespace'].includes(hint.type)
      // Indexing a declared array/container selects its element, not the container.
      const arraySuffix = part.slice(name.length)
      if (/\[[^\]]*\]/.test(arraySuffix) && !/^\s*\(/.test(arraySuffix)) {
        raw = /\[\]$/.test(raw) ? raw.slice(0, -2) : instantiate('$element', raw); staticOnly = false
      }
    }
    return { raw, staticOnly }
  }
  function suppressed(index, position) {
    return index.ignored.some(range => range.from < position && (position < range.to || range.open && position === range.to))
  }
  function optionsAt(index, position, explicit = false) {
    if (suppressed(index, position)) return null
    const prefix = index.code.slice(0, position).match(/[\p{L}_][\p{L}\p{N}_]*!?$/u)?.[0] || ''
    const from = position - prefix.length, before = index.code.slice(0, from)
    const operator = before.match(/(::|->|\.)\s*$/)
    let options
    if (operator) {
      const end = from - operator[0].length, expression = receiverBefore(index.code, end)
      const owner = resolve(index, expression, position)
      options = owner.raw ? members(index, owner.raw, owner.staticOnly) : []
    } else {
      if (!prefix && !explicit) return null
      options = [
        ...visible(index, position).values(),
        ...catalog.globals,
        ...Object.entries(catalog.snippets).map(([label, item]) => ({ label, type: 'keyword', snippet: item.body, detail: item.detail, info: item.detail })),
        ...catalog.keywords.map(label => ({ label, type: 'keyword', info: '语言关键词。' }))
      ]
    }
    const unique = new Map()
    for (const item of options) {
      if (!unique.has(item.label) && (!prefix || item.label.toLocaleLowerCase().includes(prefix.toLocaleLowerCase()))) unique.set(item.label, item)
    }
    return { from, to: position, options: [...unique.values()].slice(0, 150).map(({ label, type, valueType, returnType, signature, parameters, info, snippet, detail, macroDelimiter }) =>
      ({ label, type, detail: detail || signature || valueType || '', info: info || '', valueType, returnType, signature, parameters, snippet, macroDelimiter })) }
  }
  function signature(index, position) {
    if (suppressed(index, position) || index.parameterRanges.some(range => range.from < position && position < range.to)) return null
    let depth = 0, opening = -1
    for (let i = position - 1; i >= Math.max(0, position - 4096); i--) {
      if (index.ignored.some(range => range.from <= i && i < range.to)) continue
      const c = index.code[i]
      if (')]}' .includes(c)) depth++
      else if ('([{' .includes(c)) {
        if (!depth) { if (c === '(') opening = i; break }
        depth--
      }
    }
    if (opening < 0) return null
    const expression = receiverBefore(index.code, opening), chain = splitChain(expression)
    const label = chain.pop()?.match(/([\p{L}_][\p{L}\p{N}_]*!?)$/u)?.[0]
    if (!label) return null
    const owner = chain.length ? resolve(index, chain.join(language === 'rust' || expression.includes('::') ? '::' : '.'), position) : null
    const hint = owner ? members(index, owner.raw, owner.staticOnly).find(item => item.label === label)
      : visible(index, position).get(label) || catalog.globals.find(item => item.label === label)
    if (!hint?.parameters) return null
    let activeParameter = 0; depth = 0
    for (let i = opening + 1; i < position; i++) {
      if (index.ignored.some(range => range.from <= i && i < range.to)) continue
      const c = index.code[i]
      if ('([{'.includes(c)) depth++
      else if (')]}'.includes(c)) depth--
      else if (c === ',' && !depth) activeParameter++
    }
    return { from: opening, label: hint.label, parameters: hint.parameters, activeParameter, returnType: hint.returnType, info: hint.info }
  }
  return {
    complete(code, position, explicit = false) { return optionsAt(build(code), position, explicit) },
    signature(code, position) { return signature(build(code), position) },
    hover(code, position) {
      const index = build(code), word = wordAt(code, position)
      if (!word || suppressed(index, position)) return null
      const options = optionsAt(index, word.to, true)?.options || [], hint = options.find(item => item.label === word.name)
      return hint ? { ...word, signature: hint.signature || hint.detail, info: hint.info } : null
    },
    diagnostics(code) {
      const index = build(code), seen = new Set()
      return index.errors.filter(item => !seen.has(item.from) && seen.add(item.from)).slice(0, 12)
        .map(item => ({ from: Math.min(item.from, Math.max(0, code.length - 1)), to: Math.min(code.length, Math.max(item.to, item.from + 1)), severity: 'warning', message: '这里的语法可能不完整，请检查括号、分隔符或语句。' }))
    }
  }
}
