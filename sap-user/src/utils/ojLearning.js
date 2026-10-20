import { SKILL_NODES, LAYER_NAMES } from '../content/learning/skills.js'
import { SKILL_TRACKS } from '../content/learning/tracks.js'

export { SKILL_TRACKS, LAYER_NAMES }
export const tiers = [
  { id:'untouched', label:'未开始' }, { id:'newbie', label:'初步接触' },
  { id:'learning', label:'持续练习' }, { id:'proficient', label:'训练充分' }, { id:'master', label:'进阶积累' }
]
// Local tags describe simpler concepts than some upstream categories. Do not
// count a matrix traversal as linear algebra or a tree traversal as tree DP.
const overrides = {
  math_basic:['基础数学','等差数列'], enum:['枚举','暴力'], prefix:['差分数组'],
  dp:['最长递增子序列'], dp_knapsack:['01背包'], dp_interval:['区间动态规划'],
  heap:['最小堆'], stl_usage:['哈希'],
}
const narrow = { sim_impl:['大模拟','设计','工程实现','数据流'], matrix_linear:['线性代数','矩阵快速幂','高斯消元'], dp_tree:['树形 DP','树形动态规划'], segtree:['线段树','扫描线','可持久化线段树'] }
export const nodes = [
  {id:'io_basics',name:'输入输出与条件判断',layer:0,tags:['输入输出','条件判断','入门'],dependsOn:[],intro:'先学会读取数据、输出答案和判断条件。留意空格、换行、数据类型与边界，完整程序的每一步都从这里开始。',steps:['画出程序的三步：读入数据 → 计算 → 输出答案。','用 if / else 处理不同情况，注意等号和边界。','用示例、零和负数检查输出是否完全符合题目要求。']},
  {id:'loop_basics',name:'循环与逐步模拟',layer:0,tags:['模拟','循环','递推'],dependsOn:['io_basics'],intro:'循环就是把同一个步骤重复执行。明确从哪里开始、什么时候停止，以及每次怎样更新变量，避免少做一步或无限循环。',steps:['先手算三次重复操作，写下每次变量的值。','用 for 或 while 表达开始条件、停止条件和更新方式。','检查只执行一次、没有执行和到最后一次时的结果。']},
  {id:'array_basics',name:'数组与下标',layer:0,tags:['数组','二维数组','矩阵'],dependsOn:['io_basics','loop_basics'],intro:'数组把一组同类型的数据放在一起，下标帮助我们找到其中的一个。先练习遍历、求和和寻找最大值，再理解二维数组的行与列。',steps:['把数据画成一排格子，在每个格子上标出从 0 开始的下标。','用循环访问每个格子，注意最后一个下标是长度减一。','二维数组要分别检查行和列，不要把两种下标混在一起。']},
  {id:'string_basics',name:'基础字符串处理',layer:0,tags:['字符串'],dependsOn:['io_basics','loop_basics'],intro:'把字符串看作字符序列，练习遍历、计数、比较与切分。这些基础训练与进阶的 KMP、字符串哈希分别统计。',steps:['先确认读入的是一个单词还是一整行，空格是否需要保留。','逐个访问字符，把需要计数或比较的信息记录下来。','检查空字符串、重复字符和大小写不同的情况。']},
  {id:'complexity_basics',name:'时间与空间复杂度',layer:0,tags:['复杂度分析'],dependsOn:['loop_basics','array_basics'],intro:'复杂度描述数据变多时工作量怎样增长。先数循环的次数：扫一遍 n 个数是 O(n)，两层各扫 n 次通常是 O(n²)。空间复杂度则关注额外保存了多少数据。',steps:['给数据规模起一个名字，例如数组长度 n。','数一数最坏情况下会执行多少次操作，忽略固定常数。','把工作量与题目的数据范围比较，再决定是否需要优化。']},
  {id:'list_basics',name:'链表入门',layer:2,tags:['链表','双向链表'],dependsOn:['array_basics'],intro:'链表用连接把一个个节点串起来。理解当前节点、下一个节点和空连接，练习遍历、插入、删除与反转。',steps:['画出节点和值，并用箭头连接下一节点。','改动连接前先保存后继，避免把剩余节点弄丢。','检查空链表、只有一个节点和修改头节点的情况。']},
  {id:'tree_basics',name:'二叉树入门',layer:2,tags:['二叉树','树'],dependsOn:['recursion'],intro:'二叉树中每个节点最多有两个孩子。用小树理解先访问谁，再学习递归遍历与按层遍历。',steps:['画出根节点、左孩子和右孩子，空位置也要标出来。','明确遍历顺序，用递归处理一棵更小的子树。','检查空树、只有根节点和只有一边孩子的情况。']},
  ...SKILL_NODES.map(n=>({...n,tags:[...(narrow[n.id]||n.tags),...(overrides[n.id]||[])]})),
]
export const solved = p => p?.progress?.state==='AC'
const norm = s => String(s).trim().toLowerCase()
export function matchesNode(problem,node) {
  const tags=new Set(node.tags.map(norm))
  return (problem.tags||[]).some(t=>tags.has(norm(t)))
}
export function classify(stats) {
  if(!stats.attempted)return tiers[0]
  if(stats.ac<3)return tiers[1]
  if(stats.ac<8||stats.accuracy<.45)return tiers[2]
  if(stats.ac>=15&&stats.accuracy>=.7&&stats.hard>=1||stats.ac>=25&&stats.accuracy>=.6)return tiers[4]
  return tiers[3]
}
export function skillReport(problems=[]) {
  const report=nodes.map(node=>{
    const questions=problems.filter(p=>matchesNode(p,node))
    const attempted=questions.filter(p=>Number(p.practice?.attempts)>0||solved(p)).length
    const ac=questions.filter(solved).length
    const attempts=questions.reduce((s,p)=>s+Number(p.practice?.attempts||0),0)
    const acAttempts=questions.reduce((s,p)=>s+Number(p.practice?.acAttempts||0),0)
    const stats={attempted,ac,attempts,accuracy:attempts?Math.min(1,acAttempts/attempts):0,hard:questions.filter(p=>solved(p)&&p.difficulty==='HARD').length}
    return {...node,questions,stats,tier:classify(stats)}
  })
  const byId=new Map(report.map(n=>[n.id,n]))
  // The library is intentionally small. One completed prerequisite gives a
  // suggested next step; all nodes remain explorable, never hard locked.
  return report.map(n=>({...n,depsReady:n.dependsOn.every(id=>byId.get(id)?.stats.ac>0)}))
}
export function recommend(report,trackId='all') {
  const focus=SKILL_TRACKS.find(t=>t.id===trackId)?.focus
  return report.filter(n=>n.questions.some(p=>!solved(p))&&n.depsReady&&(!focus||focus.includes(n.id)))
    .sort((a,b)=>(focus?focus.indexOf(a.id)-focus.indexOf(b.id):a.layer-b.layer)||a.stats.ac-b.stats.ac).slice(0,6)
}
export function filterSkillReport(report,{trackId='all',scope='all',query='',selectedId=''}={}) {
  const focus=scope==='direction'?SKILL_TRACKS.find(t=>t.id===trackId)?.focus:null
  const terms=norm(query).split(/\s+/).filter(Boolean),byId=new Map(report.map(n=>[n.id,n]))
  const ids=new Set(report.filter(n=>(!focus||focus.includes(n.id))&&terms.every(term=>norm([n.name,n.intro,...n.tags].join(' ')).includes(term))).map(n=>n.id))
  // Keep prerequisites in the graph, including when a search only matches an
  // advanced topic. A linked topic stays visible unless an explicit search is active.
  if(!terms.length&&byId.has(selectedId))ids.add(selectedId)
  const pending=[...ids]
  while(pending.length)for(const dep of byId.get(pending.pop())?.dependsOn||[])if(byId.has(dep)&&!ids.has(dep)){ids.add(dep);pending.push(dep)}
  return report.filter(n=>ids.has(n.id))
}
export function addDays(date,days) {
  if(!/^\d{4}-\d{2}-\d{2}$/.test(date||''))return ''
  const d=new Date(date+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+days)
  return Number.isFinite(d.getTime())?d.toISOString().slice(0,10):''
}
export const weekday = date => new Date(date+'T12:00:00Z').getUTCDay()
export function planSchedule(plan,problems,today) {
  if(!plan)return []
  const rest=new Set(plan.restDays||[]),target=Number(plan.dailyTarget),byId=new Map(problems.map(p=>[String(p.id),p]))
  if(rest.size>=7||target<1||target>10||!addDays(plan.startedOn,0)||!addDays(today,0))return []
  let day=plan.startedOn,slot=0,carry=today
  while(rest.has(weekday(carry)))carry=addDays(carry,1)
  return plan.problemIds.map(id=>{
    while(rest.has(weekday(day)))day=addDays(day,1)
    const problem=byId.get(String(id)),assignedOn=day
    if(++slot>=target){day=addDays(day,1);slot=0}
    return {problem,assignedOn,date:problem&&!solved(problem)&&assignedOn<today?carry:assignedOn,done:solved(problem)}
  }).filter(t=>t.problem)
}
export function reviewQueue(problems,state,today) {
  return problems.filter(p=>solved(p)&&p.practice?.firstAcceptedAt).map(p=>{
    const review=state.reviews?.[String(p.id)]||{stage:1,due:addDays(String(p.practice.firstAcceptedAt).slice(0,10),1)}
    return {...p,review,overdue:review.due<today,dueNow:review.due<=today}
  }).sort((a,b)=>a.review.due.localeCompare(b.review.due))
}
