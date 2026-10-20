import {test} from 'node:test'
import assert from 'node:assert/strict'
import {nodes,SKILL_TRACKS,skillReport,solved,recommend,filterSkillReport,planSchedule,reviewQueue,addDays} from './ojLearning.js'
const p=(id,tags,state='NONE',attempts=0,ac=0)=>({id,title:'P'+id,tags,difficulty:'EASY',progress:{state},practice:{attempts,acAttempts:ac}})

test('knowledge graph is acyclic, track references resolve, and baseline tags do not claim advanced knowledge',()=>{
  const ids=new Set(nodes.map(n=>n.id)),visited=new Set(),stack=new Set()
  function visit(id){assert.ok(!stack.has(id),'cycle: '+id);if(visited.has(id))return;stack.add(id);for(const dep of nodes.find(n=>n.id===id).dependsOn){assert.ok(ids.has(dep));visit(dep)}stack.delete(id);visited.add(id)}
  nodes.forEach(n=>visit(n.id));SKILL_TRACKS.forEach(t=>[...t.focus,...t.required].forEach(id=>assert.ok(ids.has(id))))
  const report=skillReport([p(1,['矩阵'],'AC',1,1),p(2,['二叉树'],'AC',1,1),p(3,['字符串'],'AC',1,1)])
  assert.equal(report.find(n=>n.id==='matrix_linear').stats.ac,0)
  assert.equal(report.find(n=>n.id==='dp_tree').stats.ac,0)
  assert.equal(report.find(n=>n.id==='strings').stats.ac,0)
  assert.equal(report.find(n=>n.id==='string_basics').stats.ac,1)
})
test('actual topic practice counts distinct accepted problems and excludes unavailable topics from recommendations',()=>{
  const report=skillReport([p(1,['基础数学'],'AC',3,1),p(2,['基础数学']),p(3,['前缀和'],'WRONG',2)])
  const basic=report.find(n=>n.id==='math_basic');assert.equal(basic.stats.ac,1);assert.equal(basic.stats.attempts,3)
  assert.equal(report.find(n=>n.id==='prefix').depsReady,true)
  assert.ok(recommend(report).every(n=>n.questions.some(p=>!solved(p))))
  assert.equal(skillReport([]).every(n=>n.tier.id==='untouched'),true)
})
test('full learning map remains visible in every direction and scoped searches preserve prerequisite context',()=>{
  const report=skillReport([p(1,['数组'],'AC',1,1)]),snapshot=JSON.stringify(report)
  for(const track of SKILL_TRACKS)assert.deepEqual(filterSkillReport(report,{trackId:track.id}).map(n=>n.id),report.map(n=>n.id))
  const focused=filterSkillReport(report,{trackId:'starter',scope:'direction'})
  assert(focused.length<report.length);assert(focused.some(n=>n.id==='string_basics'));assert(!focused.some(n=>n.id==='flow_adv'))
  const searched=filterSkillReport(report,{query:'线段树'})
  assert(searched.some(n=>n.id==='segtree'))
  const ids=new Set(searched.map(n=>n.id))
  for(const node of searched)assert(node.dependsOn.every(id=>ids.has(id)))
  assert.deepEqual(filterSkillReport(report,{query:'不存在的知识点',selectedId:'array_basics'}),[])
  assert(filterSkillReport(report,{trackId:'starter',scope:'direction',selectedId:'flow_adv'}).some(n=>n.id==='flow_adv'))
  assert.equal(JSON.stringify(report),snapshot)
})
test('plans retain every task, skip rest days, carry overdue work, and complete only on formal AC',()=>{
  const problems=[p(1,[],'AC'),p(2,[],'WRONG'),p(3,[]),p(4,[]),p(5,[])]
  const plan={startedOn:'2026-10-02',dailyTarget:2,restDays:[0,6],problemIds:[1,2,3,4,5]}
  const tasks=planSchedule(plan,problems,'2026-10-04')
  assert.equal(tasks.length,5);assert.equal(tasks[0].done,true);assert.equal(tasks[0].date,'2026-10-02')
  assert.equal(tasks[1].date,'2026-10-05');assert.equal(tasks[1].assignedOn,'2026-10-02')
  assert.equal(tasks[2].date,'2026-10-05');assert.equal(tasks[4].date,'2026-10-06')
  assert.equal(planSchedule({...plan,restDays:[0,1,2,3,4,5,6]},problems,'2026-10-04').length,0)
  assert.equal(planSchedule(plan,problems.slice(0,4),'2026-10-04').length,4)
})
test('review queue uses server dates, covers overdue days, and does not count a sample run as completion',()=>{
  const a={...p(1,[],'AC'),practice:{firstAcceptedAt:'2026-09-30T23:58:00'}},b=p(2,[],'WRONG')
  assert.equal(addDays('2026-12-31',1),'2027-01-01');assert.equal(addDays('2028-02-28',1),'2028-02-29')
  const queue=reviewQueue([a,b],{reviews:{}},'2026-10-02');assert.equal(queue.length,1);assert.equal(queue[0].review.due,'2026-10-01');assert.equal(queue[0].overdue,true)
  assert.equal(reviewQueue([a],{reviews:{1:{stage:3,due:'2026-10-09'}}},'2026-10-02')[0].dueNow,false)
})
