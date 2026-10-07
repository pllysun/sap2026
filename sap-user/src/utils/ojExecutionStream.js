/** Authenticated SSE over fetch: tokens never appear in URLs or event payloads. */
export async function readExecutionEvents(url,{token,signal,onOpen,onProgress,fetchImpl=fetch,timeoutMs=25000}) {
  if(!token)throw new Error('请先登录')
  const controller=new AbortController()
  const abort=()=>controller.abort(signal?.reason)
  if(signal?.aborted)abort()
  signal?.addEventListener('abort',abort,{once:true})
  let timer
  const touch=()=>{clearTimeout(timer);timer=setTimeout(()=>controller.abort(new Error('进度连接超时')),timeoutMs)}
  touch()
  try {
    const response=await fetchImpl(url,{headers:{'sap-token':token,Accept:'text/event-stream'},signal:controller.signal,cache:'no-store',credentials:'same-origin'})
    if(!response.ok || !response.headers.get('content-type')?.includes('text/event-stream')) {
      let message='实时进度暂不可用'
      try {message=(await response.json()).message || message}catch{}
      throw new Error(message)
    }
    onOpen?.()
    return await consumeExecutionEvents(response.body,{signal:controller.signal,onProgress,onChunk:touch})
  } finally {clearTimeout(timer);signal?.removeEventListener('abort',abort);controller.abort()}
}

export async function consumeExecutionEvents(body,{signal,onProgress,onChunk}={}) {
  if(!body)throw new Error('浏览器不支持进度连接')
  const reader=body.getReader(),decoder=new TextDecoder()
  let line='',event='',data=[],streamId='',sequence=0,terminal=false,frameSize=0
  const dispatch=()=>{
    if(event==='progress' && data.length) {
      const value=JSON.parse(data.join('\n'))
      // Production Fastjson writes Java long fields as strings to preserve IDs.
      // Sequence values are bounded counters, so normalize only safe decimal integers.
      if(typeof value.sequence==='string' && /^\d+$/.test(value.sequence))value.sequence=Number(value.sequence)
      if(typeof value.streamId!=='string' || !Number.isSafeInteger(value.sequence) || value.sequence<=0 || !value.jobId || typeof value.stage!=='string')throw new Error('进度数据无效')
      if(value.streamId!==streamId){streamId=value.streamId;sequence=0}
      if(value.sequence>sequence) {sequence=value.sequence;onProgress?.(value);terminal=value.stage==='FINISHED'}
    }
    data=[];event='';frameSize=0
  }
  const parseLine=raw=>{
    const value=raw.endsWith('\r')?raw.slice(0,-1):raw
    if(!value){dispatch();return}
    if(value.startsWith(':'))return
    const colon=value.indexOf(':'),field=colon<0?value:value.slice(0,colon)
    let content=colon<0?'':value.slice(colon+1);if(content.startsWith(' '))content=content.slice(1)
    if(field==='event')event=content
    if(field==='data')data.push(content)
  }
  const cancel=()=>reader.cancel().catch(()=>{})
  signal?.addEventListener('abort',cancel,{once:true})
  try {
    while(!signal?.aborted && !terminal) {
      const {value,done}=await reader.read()
      if(done)break
      onChunk?.()
      const text=decoder.decode(value,{stream:true})
      for(const character of text) {
        if(++frameSize>65536)throw new Error('进度数据过大')
        if(character==='\n'){parseLine(line);line='';if(terminal)break}
        else line+=character
      }
    }
    if(signal?.aborted)throw signal.reason || new DOMException('Aborted','AbortError')
    return terminal
  } finally {signal?.removeEventListener('abort',cancel);await reader.cancel().catch(()=>{});reader.releaseLock()}
}
