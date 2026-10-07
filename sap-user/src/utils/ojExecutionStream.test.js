import test from 'node:test'
import assert from 'node:assert/strict'
import {readExecutionEvents,consumeExecutionEvents} from './ojExecutionStream.js'
const progress=(sequence,stage='COMPILING',streamId='instance-a')=>({streamId,sequence,jobId:7,stage,status:'RUNNING',totalCases:2,completedCases:0,passedCases:0})
const frame=value=>`id: ${value.streamId}:${value.sequence}\nevent: progress\ndata: ${JSON.stringify(value)}\n\n`
const stream=chunks=>new ReadableStream({start(controller){for(const chunk of chunks)controller.enqueue(chunk);controller.close()}})
const bytes=text=>new TextEncoder().encode(text)

test('fragmented UTF-8, CRLF, comments, and multiline SSE frames decode correctly',async()=>{
  const value={...progress(1,'FINISHED'),message:'完成🙂'},text=(': heartbeat\n\n'+frame(value)).replaceAll('\n','\r\n'),all=bytes(text),received=[]
  assert.equal(await consumeExecutionEvents(stream([...all].map(b=>new Uint8Array([b]))),{onProgress:p=>received.push(p)}),true)
  assert.deepEqual(received,[value])
})
test('duplicate and stale events are ignored while a restarted server can reset its sequence',async()=>{
  const values=[progress(3),progress(3),progress(2),progress(1,'COMPILED','instance-b'),progress(2,'FINISHED','instance-b')],received=[]
  await consumeExecutionEvents(stream([bytes(values.map(frame).join(''))]),{onProgress:p=>received.push(p)})
  assert.deepEqual(received.map(p=>[p.streamId,p.sequence]),[['instance-a',3],['instance-b',1],['instance-b',2]])
})
test('production long-as-string counters normalize safely and still reject overflow',async()=>{
  const received=[],values=[progress('2'),progress('1'),{...progress('3','FINISHED'),jobId:'7',updatedAt:'1790980096992',timings:{COMPILING:'1790980096469'}}]
  assert.equal(await consumeExecutionEvents(stream([bytes(values.map(frame).join(''))]),{onProgress:p=>received.push(p)}),true)
  assert.deepEqual(received.map(p=>p.sequence),[2,3]);assert.equal(received[1].jobId,'7')
  for(const sequence of ['9007199254740993','1.5','-1','bad'])await assert.rejects(consumeExecutionEvents(stream([bytes(frame(progress(sequence)))])),/无效/)
})
test('fetch sends the token only in the request header and accepts a terminal snapshot',async()=>{
  let call,opened=false
  const terminal=await readExecutionEvents('/api/oj/submissions/7/events',{token:'test-token',onOpen:()=>opened=true,onProgress:()=>{},fetchImpl:async(url,options)=>{call={url,options};return new Response(frame(progress(1,'FINISHED')),{headers:{'content-type':'text/event-stream'}})}})
  assert.equal(terminal,true);assert.equal(opened,true);assert.equal(call.url.includes('test-token'),false);assert.equal(call.options.headers['sap-token'],'test-token');assert.equal(call.options.cache,'no-store')
})
test('JSON denials and missing authentication reject without opening a stream',async()=>{
  await assert.rejects(readExecutionEvents('/events',{token:'t',fetchImpl:async()=>new Response(JSON.stringify({code:404,message:'提交记录不存在'}),{headers:{'content-type':'application/json'}})}),/提交记录不存在/)
  await assert.rejects(readExecutionEvents('/events',{}),/请先登录/)
})
test('idle connections time out and cancel their body reader',async()=>{
  let cancelled=false
  await assert.rejects(readExecutionEvents('/events',{token:'t',timeoutMs:20,fetchImpl:async()=>new Response(new ReadableStream({cancel(){cancelled=true}}),{headers:{'content-type':'text/event-stream'}})}),/进度连接超时/)
  assert.equal(cancelled,true)
})
test('account or route cancellation releases the reader and oversized or malformed events fail closed',async()=>{
  const controller=new AbortController();let cancelled=false
  const waiting=consumeExecutionEvents(new ReadableStream({cancel(){cancelled=true}}),{signal:controller.signal})
  controller.abort();await assert.rejects(waiting);assert.equal(cancelled,true)
  await assert.rejects(consumeExecutionEvents(stream([bytes('data: '+'x'.repeat(65536))])),/过大/)
  await assert.rejects(consumeExecutionEvents(stream([bytes('event: progress\ndata: {}\n\n')])),/无效/)
})
