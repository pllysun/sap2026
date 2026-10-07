#!/usr/bin/env python3
"""Check that representative hidden sets reject realistic incorrect algorithms.
This is supplementary coverage evidence; not a proof of completeness.
"""
import bisect,collections,json,pathlib,math
ROOT=pathlib.Path(__file__).resolve().parent.parent

def ts(s):return list(map(int,s.split()))
def array(s):return ts(s)[1:]
def s(s):return s.strip().replace('-','')
def near_sqrt(data):return round(math.sqrt(int(data)))
def local_bst(data):
    t=ts(data);nodes=list(zip(t[1::3],t[2::3],t[3::3]))
    return int(all((l<0 or nodes[l][0]<v) and (r<0 or nodes[r][0]>v) for v,l,r in nodes))
BAD={
'leetcode-9':('忽略负号的回文检查',lambda data:int(str(abs(int(data)))==str(abs(int(data)))[::-1])),
'leetcode-3':('统计全串不同字符，而非连续子串',lambda data:len(set(s(data)))),
'leetcode-11':('只比较相邻柱子',lambda data:max(min(a,b) for a,b in zip(array(data),array(data)[1:]))),
'leetcode-32':('只统计可配对括号总数，忽略连续与顺序',lambda data:min(s(data).count('('),s(data).count(')'))*2),
'leetcode-41':('取最大值加一',lambda data:max([0]+array(data))+1),
'leetcode-42':('按全局最高柱计算水量',lambda data:sum(max(array(data))-x for x in array(data))),
'leetcode-69':('四舍五入平方根',near_sqrt),
'leetcode-70':('错误初始状态：ways(1)=0',lambda data:0 if int(data)==1 else 1),
'leetcode-84':('把所有柱子面积相加',lambda data:sum(array(data))),
'leetcode-35':('目标缺失时错误返回-1',lambda data:ts(data)[2:].index(ts(data)[1]) if ts(data)[1] in ts(data)[2:] else -1),
'leetcode-33':('直接对旋转数组做普通二分',lambda data:(lambda p:p if p<len(ts(data)[2:]) and ts(data)[2+p]==ts(data)[1] else -1)(bisect.bisect_left(ts(data)[2:],ts(data)[1]))),
'leetcode-55':('只检查第一步是否非零',lambda data:int(len(array(data))==1 or array(data)[0]>0)),
'leetcode-62':('错误使用网格面积作为路径数',lambda data:math.prod(ts(data))),
'leetcode-98':('只比较直接子结点，未检查祖先约束',local_bst),
'leetcode-239':('只输出整个数组最大值',lambda data:max(ts(data)[2:])),
'leetcode-312':('每次只计算初始邻居，未更新',lambda data:(lambda a:sum((a[i-1] if i else 1)*a[i]*(a[i+1] if i+1<len(a) else 1) for i in range(len(a))))(array(data))),
}
report=[]
for slug,(label,wrong) in BAD.items():
    pack=json.loads((ROOT/(slug+'.json')).read_text());killed=[]
    for case in pack['cases']:
        actual=str(wrong(case['input'])).split()
        if actual!=case['expectedOutput'].split():killed.append(case['name'])
    report.append({'slug':slug,'incorrectAlgorithm':label,'detected':bool(killed),'rejectingCases':killed})
    assert killed,(slug,label)
(ROOT/'corpus/test-quality.json').write_text(json.dumps({'kind':'MUTATION_COVERAGE','notes':'Representative wrong algorithms only; not proof of complete coverage or source constraints equivalence.','checked':len(report),'detected':sum(x['detected'] for x in report),'results':report},ensure_ascii=False,indent=2)+'\n')
print(f'{len(report)}/{len(report)} representative incorrect algorithms rejected')
