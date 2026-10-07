const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const {chromium} = require('/Users/pllysun/Library/Caches/ms-playwright-go/1.57.0/package');
const session = JSON.parse(fs.readFileSync('/Users/pllysun/Library/Caches/sap-oj-test-session.json'));
const production = process.env.SAP_OJ_PRODUCTION === 'true';
const base = process.env.SAP_BASE_URL || (production ? session.base : 'http://127.0.0.1:18081');
const directory = path.join(__dirname, 'problem-packs');
const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'manifest.json')));
const fixture = manifest.items.map((item, index) => {
  const pack = JSON.parse(fs.readFileSync(path.join(directory, item.status === 'PILOT_OWNED_BY_SEPARATE_AGENT' ? item.slug + '/pack.json' : item.file)));
  return {...Object.fromEntries(['slug','title','difficulty','tags','modes','sourcePlatform'].map(key => [key, pack[key]])), id: String(index+1000)};
});
const forbidden = ['ALGORITHM LAB','ALGORITHM LIBRARY','让思路，成为代码','从一道经典题开始','让每一道好题','每一次通过，都值得记录','练习 · 提交 · 进步','01 理解题意','EVERY SOLUTION COUNTS'];
async function api(route) {
  const response = await fetch(session.base + route, {headers: {'sap-token': session.token}, signal: AbortSignal.timeout(30000)});
  const result = await response.json(); assert.equal(result.code, 200, route + ': ' + result.message); return result.data;
}
async function main() {
  let all = fixture;
  if (production) {
    all = []; let page = 1, data;
    do {data = await api('/api/oj/problems?size=50&page=' + page++); all.push(...data.records);} while (all.length < data.total);
    const options = await api('/api/oj/filters');
    assert.deepEqual(new Set(options.sources), new Set(all.map(row => row.sourcePlatform)));
    assert.deepEqual(new Set(options.tags), new Set(all.flatMap(row => row.tags)));
    for (const query of [{source:'原创',mode:'STDIO'}, {source:'LeetCode',tag:'数组',mode:'FUNCTION'}, {difficulty:'EASY',tag:'字符串'}, {source:'不存在的来源'}]) {
      const expected = all.filter(row => (!query.source || row.sourcePlatform === query.source) && (!query.tag || row.tags.includes(query.tag)) && (!query.mode || row.modes.includes(query.mode)) && (!query.difficulty || row.difficulty === query.difficulty));
      const actual = await api('/api/oj/problems?' + new URLSearchParams({...query,size:'5',page:'2'}));
      assert.equal(actual.total, expected.length);
      assert.deepEqual(actual.records.map(row => row.id), expected.slice(5,10).map(row => row.id));
    }
  }
  const browser = await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}}), errors=[];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(token => {localStorage.setItem('sap_token', token);localStorage.setItem('sap-token', token);}, session.token);
    if (!production) {
      await page.route('**/api/oj/filters', route => route.fulfill({json:{code:200,data:{tags:[...new Set(all.flatMap(row=>row.tags))].sort(),sources:[...new Set(all.map(row=>row.sourcePlatform))].sort()}}}));
      await page.route(/\/api\/oj\/problems(?:\?|$)/, route => {
        const query = new URL(route.request().url()).searchParams;
        const rows = all.filter(row => (!query.get('keyword') || (row.title+' '+row.tags+' '+row.sourcePlatform).toLowerCase().includes(query.get('keyword').toLowerCase())) && (!query.get('difficulty') || row.difficulty===query.get('difficulty')) && (!query.get('tag') || row.tags.includes(query.get('tag'))) && (!query.get('source') || row.sourcePlatform===query.get('source')) && (!query.get('mode') || row.modes.includes(query.get('mode'))));
        const current=Number(query.get('page')||1);return route.fulfill({json:{code:200,data:{records:rows.slice((current-1)*20,current*20),total:rows.length,page:current,size:20}}});
      });
    }
    await page.goto(base+'/oj'); await page.locator('.oj-problem-row').first().waitFor();
    assert.equal((await page.locator('.page-header').innerText()).trim(), '算法题库');
    const waitRows = async count => page.waitForFunction(expected => !document.querySelector('.oj-list-skeleton') && document.querySelectorAll('.oj-problem-row').length === expected, Math.min(20,count));
    const choose = async(label, option) => {await page.getByRole('button',{name:label,exact:true}).click(); await page.getByRole('option',{name:option,exact:true}).click();};
    await page.getByRole('button',{name:'下一页',exact:true}).click(); await page.locator('.oj-pagination').getByText('2 /',{exact:false}).waitFor();
    await choose('题目来源','原创');
    const originals = all.filter(row=>row.sourcePlatform==='原创');await waitRows(originals.length);
    assert.deepEqual(await page.locator('.oj-row-title h2').allTextContents(), originals.slice(0,20).map(row=>row.title));
    await choose('做题模式','核心函数');await waitRows(originals.filter(row=>row.modes.includes('FUNCTION')).length);
    await page.getByRole('button',{name:'重置',exact:true}).click();await waitRows(all.length);
    await choose('算法类型','数组'); await choose('题目来源','LeetCode'); await choose('做题模式','核心函数');
    const filtered = all.filter(row=>row.tags.includes('数组') && row.sourcePlatform==='LeetCode' && row.modes.includes('FUNCTION')); await waitRows(filtered.length);
    assert.deepEqual(await page.locator('.oj-row-title h2').allTextContents(),filtered.slice(0,20).map(row=>row.title));
    await page.getByRole('button',{name:'重置',exact:true}).click();await waitRows(all.length);
    await page.getByRole('textbox',{name:'搜索算法题'}).fill('不存在的题目-no-match');await page.getByRole('button',{name:'搜索',exact:true}).click();await page.getByRole('heading',{name:'暂无匹配题目'}).waitFor();
    await page.getByRole('button',{name:'重置',exact:true}).click();await waitRows(all.length);
    for (const width of [390,768,1024,1440,1920]) {
      await page.setViewportSize({width,height:1000});
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'page overflow at '+width);
      await page.getByRole('button',{name:'算法类型',exact:true}).click();
      assert(await page.getByRole('listbox',{name:'算法类型'}).isVisible());await page.keyboard.press('Escape');
      if ([390,1440].includes(width)) await page.screenshot({path:'/Users/pllysun/Library/Caches/sap-oj-ui/clean-library-'+(production?'production':'candidate')+'-'+width+'.png',fullPage:true});
    }
    for (const text of forbidden) assert(!(await page.locator('body').innerText()).includes(text),text);
    assert(!(await page.locator('.oj-problem-list').innerText()).includes('↗'));
    await page.getByRole('tab',{name:'提交记录',exact:true}).click();await page.getByRole('heading',{name:'提交记录',exact:true}).waitFor();
    for (const text of forbidden) assert(!(await page.locator('body').innerText()).includes(text),text);
    await page.goto(base+'/admin/oj');await page.getByRole('heading',{name:'算法题库',exact:true}).waitFor();
    assert.equal((await page.locator('.oj-admin-hero').innerText()).trim(),'算法题库');
    for (const text of forbidden) assert(!(await page.locator('body').innerText()).includes(text),text);
    assert.deepEqual(errors,[]);
    const report={passed:true,production,problemCount:all.length,checks:['plain shared header card','difficulty/algorithm/source/mode filters','cross-filter intersection','pagination resets on filtering','reset and empty result','submission board without slogans','admin header without slogans','normal problem entry','five responsive widths'],runtimeErrors:errors.length};
    fs.writeFileSync(path.join(__dirname,'library-filters-'+(production?'production':'candidate')+'.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
  } finally {await browser.close();}
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
