// Dynamic imports keep each analysis worker limited to the selected language.
export async function loadLanguage(language) {
  let parser, catalog
  switch (language) {
    case 'c':
      [parser, catalog] = await Promise.all([import('@lezer/cpp'), import('./catalogs/c.js')]); break
    case 'cpp':
      [parser, catalog] = await Promise.all([import('@lezer/cpp'), import('./catalogs/cpp.js')]); break
    case 'java':
      [parser, catalog] = await Promise.all([import('@lezer/java'), import('./catalogs/java.js')]); break
    case 'python':
      [parser, catalog] = await Promise.all([import('@lezer/python'), import('./catalogs/python.js')]); break
    case 'rust':
      [parser, catalog] = await Promise.all([import('@lezer/rust'), import('./catalogs/rust.js')]); break
    default: throw new Error('Unsupported editor language')
  }
  return { parser: parser.parser, catalog: catalog.default }
}
