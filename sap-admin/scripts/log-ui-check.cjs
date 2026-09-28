// 全部接口由本地 fixture 提供：不读取线上日志，不写入真实用户数据。
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright')
const assert=require('node:assert/strict')
const base='http://127.0.0.1:3191'
;(async()=>{
  const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true})
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1100}})
    const errors=[], calls=[]
    page.on('pageerror',e=>errors.push(e.message))
    await page.addInitScript(()=>localStorage.setItem('sap-token','local-log-ui-test'))
    let fail=false
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url())
      if(url.origin!==base)return route.abort()
      if(!url.pathname.startsWith('/api/'))return route.continue()
      let data={}
      if(url.pathname==='/api/auth/info')data={user:{name:'日志测试管理员'},roles:[0]}
      if(url.pathname==='/api/log/stats')data={byOperationType:{查询:300},byHttpMethod:{GET:300},dailyTrend:{'2026-09-11':300}}
      if(url.pathname==='/api/log/calendar-year')data=[['2026-09-11',300]]
      if(url.pathname==='/api/log/explore'){
        const query=Object.fromEntries(url.searchParams);calls.push(query)
        if(fail)return route.fulfill({json:{code:503,message:'测试加载失败'}})
        data={total:45,records:[{source:query.source||'APP',endpoint:'/api/app/feedback/{id}',http_method:'GET',user_id:11,user_name:'测试用户',call_count:3,user_count:2,endpoint_count:2,failure_count:1,result_code:200,duration_sum:90,duration_max:50,first_time:'2026-09-11T09:00:00',last_time:'2026-09-11T09:10:00',ip:'127.0.0.1',description:'查看反馈'}]}
      }
      return route.fulfill({json:{code:200,data}})
    })
    await page.goto(base+'/log')
    const panel=page.locator('.explorer')
    await panel.getByRole('button',{name:'查看详情'}).click()
    const dialog=page.getByRole('dialog')
    await dialog.locator('.el-table__body').getByText('测试用户').first().waitFor()
    assert.equal(calls.at(-1).dimension,'detail')
    assert.equal(calls.at(-1).endpoint,'/api/app/feedback/{id}')
    await page.waitForFunction(()=>!document.querySelector('[class*="enter-active"]'))
    const box=await page.locator('.el-dialog.log-detail-dialog').boundingBox()
    await page.screenshot({path:'/Users/pllysun/Library/Caches/sap-course-fix/log-dialog.png'})
    console.log('dialog bounds',box)
    assert(box.y>=0 && box.y+box.height<=1100,'弹窗必须位于视口内')
    await dialog.locator('.btn-next').click()
    await page.waitForFunction(()=>document.querySelector('.el-dialog .el-pager .is-active')?.textContent==='2')
    assert.equal(calls.at(-1).current,'2')
    await dialog.getByRole('button',{name:'Close this dialog'}).click()
    await Promise.all([page.waitForResponse(r=>r.url().includes('/api/log/explore')&&r.url().includes('source=WEB')),panel.getByText('Web 接口',{exact:true}).click()])
    assert.equal(calls.at(-1).source,'WEB')
    await Promise.all([page.waitForResponse(r=>r.url().includes('archive=true')),panel.getByRole('tab',{name:'历史归档 · 小时聚合'}).click()])
    assert.equal(calls.at(-1).archive,'true')
    await panel.locator('.toolbar .el-select').click()
    await Promise.all([page.waitForResponse(r=>r.url().includes('dimension=user')),page.getByRole('option',{name:'用户维度'}).click()])
    await panel.getByRole('button',{name:'查看详情'}).click()
    await dialog.locator('.el-table__body').getByText('测试用户').first().waitFor()
    assert.equal(calls.at(-1).userId,'11')
    await dialog.getByRole('button',{name:'Close this dialog'}).click()
    await page.locator('.el-overlay:visible').waitFor({state:'hidden'})
    await panel.screenshot({path:'/Users/pllysun/Library/Caches/sap-course-fix/log-explorer.png'})
    fail=true
    await panel.getByRole('button',{name:'刷新',exact:true}).click()
    await panel.getByText('测试加载失败',{exact:true}).waitFor()
    assert.equal(await panel.locator('.el-table__body tr').count(),0)
    assert.deepEqual(errors,[])
    console.log('PASS: 分类、接口/用户下钻、独立分页、历史聚合、视口弹窗、错误状态')
  }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
