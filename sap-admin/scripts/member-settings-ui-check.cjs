// 本地 fixture 回归，不读取真实成员、不调用线上写接口。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const base = process.env.UI_BASE || 'http://127.0.0.1:3191'
const output = process.env.UI_OUTPUT || '/tmp/sap-member-settings-ui'
const names = ['林一', '陈二', '张三', '李四', '王五', '周六', '赵七', '吴八', '郑九', '许十', '沈同学', '罗同学', '杨同学', '刘同学', '孙同学', '胡同学', '郑同学']
const terms = Array.from({ length: 55 }, (_, i) => ({ id: i + 1, userId: i + 1, userName: names[i % names.length], studentId: String(20260001 + i), grade: '2026', positionName: i === 0 ? '会长' : i === 1 ? '团支书' : '软件部部长' }))
const sections = [['terms','任职档案',15],['joins','入会申请',1],['study','学习小队',2],['feedback','课表反馈',0],['notes','发布笔记',1]]
const settings = { current_grade: '2026', membership_fee: '30', footer_copyright: '中南林业科技大学软件协会', footer_address: '学生活动中心', footer_qq: '123456', footer_email: 'fixture@example.com', join_qq_group_name: '2026 新生群' }
const defaults = {
  captcha: { enabled:true,freeLimit:0,freeWindowHours:24,length:6,ttlSeconds:180,minSolveSeconds:1 },
  quotas: { enabled:true,cooldownSeconds:10,ipHourlyLimit:20,ipDailyLimit:50,qqDailyLimit:3,globalMinuteLimit:30,maxConcurrent:4,maxConcurrentPerIp:1 },
  requests: { enabled:true,registerCapacity:20,registerPerMinute:20,captchaCapacity:10,captchaPerMinute:10,captchaGlobalCapacity:60,captchaGlobalPerMinute:60 }, trustedProxies:'127.0.0.1',
}
;(async () => {
  fs.mkdirSync(output, { recursive: true })
  const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1050 } })
    const errors = [], writes = [], relationReads = []
    const screenshot = name => page.screenshot({ path: output + '/' + name + '.png', animations: 'disabled' })
    page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(() => localStorage.setItem('sap-token','member-settings-fixture'))
    await page.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url())
      if (url.origin !== base) return route.abort()
      if (!url.pathname.startsWith('/api/')) return route.continue()
      let data = {}
      if (url.pathname === '/api/auth/info') data = { user: { name:'测试管理员' }, roles:[0] }
      if (url.pathname === '/api/term/grades') data = ['2026','2025']
      if (url.pathname === '/api/term/list') { const size = +url.searchParams.get('size'), current = +url.searchParams.get('current'); data = { records: terms.slice((current-1)*size,current*size), total:terms.length } }
      if (url.pathname === '/api/user/list') data = { records: [], total:0 }
      if (url.pathname === '/api/user/members') data = []
      if (url.pathname === '/api/position/list') data = [{id:1,positionName:'会长',isSystem:1,maxCount:1,roleCode:1,sortOrder:1},{id:2,positionName:'软件部部长',isSystem:0,maxCount:2,roleCode:2,sortOrder:2}]
      if (url.pathname.includes('outstanding')) data = [{ id:1,name:'优秀同学',grade:'2025',gender:'女',major:'软件工程',destination:'就业',destinationDetail:'软件研发',bio:'热爱技术与分享。' }]
      if (/\/api\/user\/\d+\/profile$/.test(url.pathname)) {
        const id = +url.pathname.split('/')[3]
        data = { user:{id,name:terms[id-1]?.userName || '测试成员',studentId:String(20260000+id),qq:'100001',grade:'2026',gender:1,status:1,createdAt:'2026-09-01T09:00:00'},roles:[2,3],joinedAt:'2026-09-03T09:00:00',firstArchivedAt:'2026-09-20T09:00:00',sections:sections.map(([key,label,total])=>({key,label,total})) }
      }
      if (url.pathname.endsWith('/profile/relations')) {
        const section = url.searchParams.get('section'), current = +url.searchParams.get('current')
        relationReads.push({section,current})
        data = { total:sections.find(s=>s[0]===section)?.[2] || 0,records:section==='terms' ? Array.from({length:current===1?12:3},(_,i)=>({id:(current-1)*12+i,grade:'2026',positionName:`任职记录 ${(current-1)*12+i+1}`,createdAt:'2026-09-20T09:00:00'})) : section==='study' ? [{id:1,activityName:'秋季算法学习小队',grade:'2026',week:1,leaderName:'负责人同学',score:9,comment:'认真完成',status:1,createdAt:'2026-09-20T09:00:00'}] : [] }
      }
      if (url.pathname === '/api/setting/value') data = settings[url.searchParams.get('key')]
      if (url.pathname === '/api/setting/public') data = settings
      if (url.pathname === '/api/setting/cos-config') data = { bucketName:'fixture-bucket',region:'cos.ap-nanjing',cdnDomain:'https://example.com',secretId:'已配置',secretKey:'已配置' }
      if (url.pathname === '/api/setting/registration-protection') data = { revision:'fixture',config:defaults,defaults }
      if (request.method() === 'PUT' && url.pathname === '/api/setting') { writes.push(request.postDataJSON()); data = '已保存' }
      return route.fulfill({json:{code:200,data}})
    })
    await page.goto(base+'/member')
    await page.getByRole('button',{name:'查看林一的成员详情',exact:true}).first().waitFor()
    assert.equal(await page.locator('.term-card').count(),24)
    assert.equal(await page.locator('.term-grid').evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length),4)
    const pager = page.locator('.term-pagination')
    await pager.getByText('第 1–24 条 / 共 55 条',{exact:true}).waitFor()
    const colors = await pager.locator('.el-pager li.is-active').evaluate(el=>({fg:getComputedStyle(el).color,bg:getComputedStyle(el).backgroundColor}))
    assert.notEqual(colors.fg,colors.bg); assert.equal(colors.fg,'rgb(255, 255, 255)')
    assert((await pager.innerText()).includes('条/页'))
    await pager.locator('button.btn-next').click()
    await pager.getByText('第 25–48 条 / 共 55 条',{exact:true}).waitFor()
    await pager.locator('button.btn-prev').click()
    await pager.getByText('第 1–24 条 / 共 55 条',{exact:true}).waitFor()
    await page.locator('.term-sid').getByText('20260001',{exact:true}).waitFor()
    await page.waitForFunction(() => document.querySelector('.term-card .el-tag')?.getBoundingClientRect().width > 20)
    await screenshot('members')
    assert.equal(await page.locator('.term-card .el-tag').first().textContent(),'会长')
    await page.getByRole('button',{name:'查看林一的成员详情',exact:true}).first().click()
    const dialog = page.getByRole('dialog',{name:'成员详情'})
    await dialog.getByText('任职记录 1',{exact:true}).waitFor()
    await dialog.locator('.profile-pagination button.btn-next').click()
    await dialog.getByText('任职记录 13',{exact:true}).waitFor()
    assert(relationReads.some(r=>r.section==='terms'&&r.current===2))
    await dialog.locator('.profile-nav').getByRole('button',{name:/学习小队/}).click()
    await dialog.getByText('秋季算法学习小队',{exact:true}).waitFor()
    await page.waitForFunction(()=>!document.querySelector('[class*="enter-active"]'))
    const bounds = await dialog.boundingBox()
    assert(bounds.y>=0 && bounds.y+bounds.height<=1051)
    await screenshot('member-detail')
    await dialog.locator('.profile-nav').getByRole('button',{name:/课表反馈/}).click()
    await dialog.getByText('暂无关联记录',{exact:true}).waitFor()
    await page.setViewportSize({width:390,height:844})
    await screenshot('member-detail-mobile')
    assert((await dialog.boundingBox()).width<=390)
    await dialog.getByRole('button',{name:'关闭',exact:true}).last().click()
    await page.setViewportSize({width:1600,height:1050})
    await page.getByRole('tab',{name:'优秀成员',exact:true}).click()
    await page.getByRole('button',{name:'查看优秀同学的优秀成员档案',exact:true}).click()
    await dialog.getByText(/未关联注册账号/).waitFor()
    await dialog.getByRole('button',{name:'关闭',exact:true}).last().click()

    await page.goto(base+'/settings')
    const nav = page.getByRole('navigation',{name:'设置分类'})
    await nav.waitFor()
    assert.equal(await page.locator('.settings-layout > .settings-col > .zen-card:visible').count(),2)
    await screenshot('settings-general')
    await page.getByPlaceholder('群名称，如 2026软件协会新生群').fill('入会群草稿')
    await nav.getByRole('button',{name:/网站展示/}).click()
    await page.getByPlaceholder('如 中南林业科技大学软件协会').fill('页脚草稿')
    await screenshot('settings-appearance')
    await nav.getByRole('button',{name:/基础配置/}).click()
    assert.equal(await page.getByPlaceholder('群名称，如 2026软件协会新生群').inputValue(),'入会群草稿')
    await page.getByRole('button',{name:'保存入会配置',exact:true}).click()
    await page.getByText('入会配置保存成功',{exact:true}).waitFor()
    assert(writes.length>0 && writes.every(body=>body.settingKey.startsWith('join_')))
    await nav.getByRole('button',{name:/网站展示/}).click()
    assert.equal(await page.getByPlaceholder('如 中南林业科技大学软件协会').inputValue(),'页脚草稿')
    await nav.getByRole('button',{name:/身份管理/}).click()
    await screenshot('settings-identity')
    await nav.getByRole('button',{name:/文件存储/}).click()
    assert.equal(await page.locator('.settings-layout > .settings-col > .zen-card:visible').count(),1)
    await nav.getByRole('button',{name:/账号与安全/}).click()
    await page.locator('.registration-settings').waitFor()
    await screenshot('settings-security')
    await page.setViewportSize({width:390,height:844})
    await nav.getByRole('button',{name:/基础配置/}).click()
    await screenshot('settings-mobile')
    assert(await page.locator('.settings-content').evaluate(el=>el.scrollWidth<=el.clientWidth+1),'设置内容不应横向溢出')
    assert.deepEqual(errors,[])
    console.log(JSON.stringify({status:'passed',checks:['compact_cards','chinese_pagination','page_contrast','profile_relations','profile_pagination','unlinked_archive','settings_categories','draft_preservation','scoped_save','responsive'],screenshots:output}))
  } catch(error) {
    const page = browser.contexts()[0]?.pages()[0]
    if(page) await page.screenshot({path:output+'/failure.png',fullPage:true})
    throw error
  } finally { await browser.close() }
})().catch(error=>{console.error(error);process.exitCode=1})
