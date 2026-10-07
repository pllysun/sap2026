let worker, serial = 0
const pending = new Map()
export function formatOjCode(code, language) {
  if (!code?.trim()) return Promise.resolve(code || '')
  if (!worker) {
    worker = new Worker(new URL('./ojFormatter.worker.js', import.meta.url), { type: 'module' })
    worker.onmessage = ({ data }) => { const task = pending.get(data.id); if (!task) return; pending.delete(data.id); clearTimeout(task.timeout); data.error ? task.reject(new Error(data.error)) : task.resolve(data.result) }
    worker.onerror = () => { for (const task of pending.values()) { clearTimeout(task.timeout); task.reject(new Error('格式化工具加载失败，请重试')) } pending.clear(); worker.terminate(); worker = null }
  }
  return new Promise((resolve, reject) => {
    const id = ++serial
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error('格式化工具加载超时，请重试')) }, 60000)
    pending.set(id, { resolve, reject, timeout }); worker.postMessage({ id, code, language })
  })
}
export async function formatOjPack(pack) {
  for (const [language, profile] of Object.entries(pack.profiles)) {
    for (const field of ['starterStdio', 'starterFunction', 'functionDriver']) profile[field] = await formatOjCode(profile[field], language)
    for (const mode of ['STDIO', 'FUNCTION']) pack.references[language][mode] = await formatOjCode(pack.references[language][mode], language)
  }
  return pack
}
