/** 通过请求头鉴权下载，不将会话令牌写入地址或浏览器历史。 */
export async function downloadFile(path, filename) {
  const target = new URL(path, window.location.origin)
  if (target.origin !== window.location.origin || !target.pathname.startsWith('/api/')) throw new Error('下载地址无效')
  const response = await fetch(target, { credentials: 'omit', redirect: 'error', headers: { 'sap-token': localStorage.getItem('sap-token') || '' } })
  if (!response.ok) throw new Error('下载失败，请稍后重试')
  if (response.headers.get('content-type')?.includes('application/json')) {
    const result = await response.json()
    throw new Error(result.message || '下载失败，请重新登录后重试')
  }
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url; link.download = filename.replace(/[\\/:*?"<>|]/g, '_')
  document.body.appendChild(link); link.click(); link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

/** Markdown 按原始字节保存，避免浏览器把缺少 charset 的直链作为网页解码。 */
export async function openStoredFile(url, filename) {
  const source = new URL(url)
  if (source.protocol !== 'https:' || source.username || source.password) throw new Error('下载地址无效')
  let name = filename
  if (!name) {
    const last = source.pathname.split('/').pop()
    try { name = decodeURIComponent(last) } catch { name = last }
  }
  name = name || '附件'
  const markdown = /\.(?:md|markdown)$/i.test(name) || /\.(?:md|markdown)$/i.test(source.pathname)
  const popup = markdown ? null : window.open('about:blank', '_blank')
  if (!markdown && !popup) throw new Error('请允许本站打开下载窗口后重试')
  if (popup) popup.opener = null
  try {
    const response = await fetch('/api/file/download-link', {
      method: 'POST', credentials: 'omit', redirect: 'error',
      headers: { 'Content-Type': 'application/json', 'sap-token': localStorage.getItem('sap-token') || '' },
      body: JSON.stringify({ url })
    })
    const result = await response.json()
    if (!response.ok || result.code !== 200) throw new Error(result.message || '获取下载地址失败')
    const target = new URL(result.data.url)
    if (target.protocol !== 'https:' || target.username || target.password) throw new Error('下载地址无效')
    if (markdown) {
      const query = new URLSearchParams({ url: target.href, name })
      await downloadFile(`/api/file/download?${query}`, name)
    } else popup.location.replace(target.href)
  } catch (e) { popup?.close(); throw e }
}
