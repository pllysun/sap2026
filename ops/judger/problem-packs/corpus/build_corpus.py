#!/usr/bin/env python3
"""Reproducible classic OJ corpus. No external hidden tests are used.
Restricted Python algorithm AST -> readable C/C++/Java/Python/Rust references.
Independent high-level oracles generate expected results; references are checked separately.
"""
import ast, json, random, itertools, functools, math, pathlib, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parent
SEED=20261001
rng=random.Random(SEED)
ITEMS=[]
ARRAY_INPUT='n=read()\nfor i in range(n):\n    a[i]=read()\n'
STRING_INPUT='n=readstr(a)\n'

def add(pid,title,slug,diff,tags,desc,fmt,constraints,code,cases,oracle,platform='LeetCode',function=None,out='输出答案；多元素结果按题目规定顺序以空格分隔。'):
    url=('https://leetcode.com/problems/'+slug+'/' if platform=='LeetCode' else 'https://www.luogu.com.cn/problem/'+pid if platform=='洛谷' else '')
    packslug=('leetcode-'+pid if platform=='LeetCode' else 'luogu-'+pid.lower() if platform=='洛谷' else 'original-'+slug)
    ITEMS.append(dict(slug=packslug,title=title,difficulty=diff,tags=tags,description=desc,inputFormat=fmt,outputFormat=out,constraints=constraints,sourcePlatform=platform,sourceUrl=url,sourceId=pid,sourceNote='自写教学改编题面；算法任务参考来源链接，输入输出和数据范围以本题为准。测试均为自行生成，未获取外站隐藏官方测试。' if platform!='原创' else '软件协会原创',_code=code,_cases=cases,_oracle=oracle,_function=function))

def arr_input(a,*extra): return f'{len(a)}'+(' '+' '.join(map(str,extra)) if extra else '')+'\n'+' '.join(map(str,a))+'\n'
def arrays(values,lo=-10,hi=10): return list(values)+[[rng.randint(lo,hi) for _ in range(rng.randint(1,30))] for _ in range(16)]
def strings(vals,alphabet='abc'): return vals+[''.join(rng.choice(alphabet) for _ in range(rng.randint(1,30))) for _ in range(16)]
def toks(data): return list(map(int,data.split()))
def emit_tokens(vals): return ' '.join(map(str,vals))+'\n'
def simple_cases(xs,encoding=lambda x:str(x)+'\n'): return [encoding(x) for x in xs]
def arr_cases(xs): return [arr_input(x) for x in xs]

# EASY: 12 LeetCode, 1 Luogu, 2 original. Pilot adds five EASY.
add('9','回文数','palindrome-number','EASY',['数学'], '判断非负整数的十进制表示是否左右对称；负数不是回文。','一个整数 x。','-100000000 <= x <= 100000000。', '''x=read()
y=x
r=0
while y>0:
    r=r*10+y%10
    y=y//10
ok=0
if x>=0 and x==r:
    ok=1
emit(ok)
''',simple_cases([-1,0,1,10,11,121,1221,100000000]+[rng.randint(-10000,10000) for _ in range(16)]),lambda s:int(s.strip()==s.strip()[::-1]))
add('14','最长公共前缀','longest-common-prefix','EASY',['字符串'], '求一组小写字符串共有的最长前缀。空字符串用单独的 - 表示；无公共前缀时也输出 -。','第一行 m，接着 m 个字符串。','1 <= m <= 20；每个字符串长度 0..100。', '''m=read()
n=readstr(a)
for j in range(1,m):
    k=readstr(b)
    n=mn(n,k)
    i=0
    while i<n and a[i]==b[i]:
        i=i+1
    n=i
if n==0:
    emitstr(a,0)
else:
    emitstr(a,n)
''', ['3\nflower flow flight\n','2\ndog racecar\n','2\n- a\n','1\n-\n']+[str(len(ss))+'\n'+' '.join(ss)+'\n' for ss in [[rng.choice(['abc','ab','abcd','a','xyz']) for _ in range(rng.randint(1,10))] for z in range(16)]],lambda s: (lambda xs: (next((''.join(xs[0][:i]) for i in range(len(xs[0]),-1,-1) if all(x.startswith(xs[0][:i]) for x in xs)),'') or '-')+'\n')([x if x!='-' else '' for x in s.split()[1:]]))
add('21','合并两个有序链表','merge-two-sorted-lists','EASY',['链表','双指针'],'将两个非降序链表合并成一个非降序链表。本模式用值序列表示链表，允许空链表。','n m，随后 n 个和 m 个整数。','0 <= n,m <= 100；值在 -1000..1000。', '''n=read()
m=read()
for i in range(n):
    a[i]=read()
for i in range(m):
    b[i]=read()
i=0
j=0
while i<n or j<m:
    if j==m or (i<n and a[i]<=b[j]):
        emit(a[i])
        i=i+1
    else:
        emit(b[j])
        j=j+1
''',[f'{len(a)} {len(b)}\n'+emit_tokens(a)+emit_tokens(b) for a,b in [([],[]),([],[1]),([1,2,4],[1,3,4])]+[(sorted([rng.randint(-10,10) for _ in range(rng.randint(0,30))]),sorted([rng.randint(-10,10) for _ in range(rng.randint(0,30))])) for _ in range(16)]],lambda s:sorted(toks(s)[2:]))
add('26','删除有序数组中的重复项','remove-duplicates-from-sorted-array','EASY',['数组','双指针'],'保留非降序数组中每个值的第一次出现。输出不同元素数量，再输出去重后的数组。','n，随后 n 个非降序整数。','0 <= n <= 200；元素 -1000..1000。',ARRAY_INPUT+'''k=0
for i in range(n):
    if i==0 or a[i]!=a[i-1]:
        b[k]=a[i]
        k=k+1
emit(k)
for i in range(k):
    emit(b[i])
''',arr_cases([sorted(x) for x in arrays([[],[1],[1,1],[1,1,2,3,3]])]),lambda s:(lambda x:[len(x)]+x)(sorted(set(toks(s)[1:]))))
add('27','移除元素','remove-element','EASY',['数组','双指针'],'移除数组中等于 val 的元素。本题要求保持其他元素相对顺序；输出剩余数量及数组。','n val，随后 n 个整数。','0 <= n <= 200；元素和 val 为 -1000..1000。', '''n=read()
v=read()
k=0
for i in range(n):
    x=read()
    if x!=v:
        a[k]=x
        k=k+1
emit(k)
for i in range(k):
    emit(a[i])
''',[arr_input(x,v) for x,v in [([],0),([1,1],1),([3,2,2,3],3)]+[(x,rng.randint(-3,3)) for x in arrays([],-3,3)]],lambda s:(lambda x:[len(x)]+x)([v for v in toks(s)[2:] if v!=toks(s)[1]]))
add('28','找出首次出现的位置','find-the-index-of-the-first-occurrence-in-a-string','EASY',['字符串'],'输出 needle 在 haystack 中首次出现的零基位置；不存在输出 -1。空字符串使用 - 表示。','两个小写字符串 haystack needle。','两串长度 0..200。', '''n=readstr(a)
m=readstr(b)
ans=-1
for i in range(n-m+1):
    ok=1
    for j in range(m):
        if a[i+j]!=b[j]:
            ok=0
    if ok==1 and ans==-1:
        ans=i
emit(ans)
''',[a+' '+b+'\n' for a,b in [('sadbutsad','sad'),('abc','d'),('-','-'),('abc','-')]+[(rng.choice(['abcabc','aaa','abcd','a']),rng.choice(['a','ab','bca','d','-'])) for _ in range(16)]],lambda s:(lambda a,b:a.find(b))(*[x if x!='-' else '' for x in s.split()]))
add('35','搜索插入位置','search-insert-position','EASY',['二分查找'],'在严格递增数组中找到目标值的位置；若不存在，输出插入后仍保持有序的位置。','n target，随后 n 个严格递增整数。','0 <= n <= 200；值和 target 为 -1000..1000。', '''n=read()
t=read()
for i in range(n):
    a[i]=read()
l=0
r=n
while l<r:
    m=(l+r)//2
    if a[m]<t:
        l=m+1
    else:
        r=m
emit(l)
''',[arr_input(a,t) for a,t in [([],0),([1,3,5,6],5),([1,3,5,6],2)]+[(sorted(set(rng.randint(-20,20) for _ in range(30))),rng.randint(-25,25)) for _ in range(16)]],lambda s:sum(v<toks(s)[1] for v in toks(s)[2:]))
add('58','最后一个单词的长度','length-of-last-word','EASY',['字符串'],'句子由英文字母和空格组成，输出最后一个单词的长度。输入采用词序列表示，自动忽略首尾空白。','第一行单词数 n，随后 n 个非空英文单词。','1 <= n <= 50；每个单词长度 1..100。','''n=read()
k=0
for i in range(n):
    k=readstr(a)
emit(k)
''',['2\nHello World\n','1\nx\n']+[str(len(x))+'\n'+' '.join(x)+'\n' for x in [[rng.choice(['a','hello','algorithm','test']) for _ in range(rng.randint(1,20))] for z in range(16)]],lambda s:len(s.split()[-1]))
add('66','加一','plus-one','EASY',['数组','数学'],'非负整数用十进制数字数组表示，求它加一后的数字数组。输入除数字 0 本身外没有前导零。','n，随后 n 个 0..9 的数字，最高位在前。','1 <= n <= 200。', ARRAY_INPUT+'''carry=1
for i in range(n-1,-1,-1):
    x=a[i]+carry
    a[i]=x%10
    carry=x//10
if carry==1:
    emit(1)
for i in range(n):
    emit(a[i])
''',arr_cases([[0],[9],[9,9],[1,2,3],[9]*200]+[[rng.randint(1,9)]+[rng.randint(0,9) for _ in range(rng.randint(0,50))] for z in range(16)]),lambda s:list(map(int,str(int(''.join(map(str,toks(s)[1:])))+1))))
add('69','整数平方根','sqrtx','EASY',['二分查找','数学'],'求非负整数平方根向下取整的结果。','一个整数 x。','0 <= x <= 100000000。', '''x=read()
l=0
r=10001
while l+1<r:
    m=(l+r)//2
    if m*m<=x:
        l=m
    else:
        r=m
emit(l)
''',simple_cases([0,1,2,4,8,100000000]+[rng.randint(0,100000000) for _ in range(16)]),lambda s:math.isqrt(int(s)),function=('scalar','mySqrt','my_sqrt','x'))
add('70','爬楼梯','climbing-stairs','EASY',['动态规划'],'有 n 阶台阶，每次上 1 阶或 2 阶，求不同走法数量。','一个整数 n。','1 <= n <= 40。', '''n=read()
x=1
y=1
for i in range(n):
    z=x+y
    x=y
    y=z
emit(x)
''',simple_cases([1,2,3,40]+[rng.randint(1,40) for _ in range(16)]),lambda s:(lambda n:sum(math.comb(n-k,k) for k in range(n//2+1)))(int(s)),function=('scalar','climbStairs','climb_stairs','n'))
add('88','合并两个有序数组','merge-sorted-array','EASY',['数组','双指针'],'将两组非降序整数合并，输出非降序结果。完整程序输入仅包含有效元素，省略容量占位。','n m，随后 n 个和 m 个整数。','0 <= n,m <= 100；元素 -1000..1000。',ITEMS[2]['_code'],ITEMS[2]['_cases'],ITEMS[2]['_oracle'])
# MEDIUM LeetCode 15.
add('3','无重复字符的最长子串','longest-substring-without-repeating-characters','MEDIUM',['字符串','滑动窗口'],'给定小写字符串，求不含重复字符的最长连续子串长度。空串用 - 表示。','一个小写字符串。','长度 0..200。', STRING_INPUT+'''ans=0
l=0
for r in range(n):
    x=a[r]
    if b[x]>l:
        l=b[x]
    b[x]=r+1
    ans=mx(ans,r-l+1)
emit(ans)
''',simple_cases(strings(['-','a','aaaa','abcabcbb','abcdefghijklmnopqrstuvwxyz'])) ,lambda s:max([0]+[j-i for i in range(len(s.strip().replace('-',''))) for j in range(i+1,len(s.strip().replace('-',''))+1) if len(set(s.strip()[i:j]))==j-i]),function=('string','lengthOfLongestSubstring','length_of_longest_substring','s'))
add('11','盛最多水的容器','container-with-most-water','MEDIUM',['数组','双指针'],'数组值表示单位间隔竖线的高度，选择两条竖线，使其形成容器的面积最大。','n，随后 n 个非负高度。','2 <= n <= 200；高度 0..10000。', ARRAY_INPUT+'''l=0
r=n-1
ans=0
while l<r:
    ans=mx(ans,(r-l)*mn(a[l],a[r]))
    if a[l]<a[r]:
        l=l+1
    else:
        r=r-1
emit(ans)
''',arr_cases(arrays([[0,0],[1,1],[1,8,6,2,5,4,8,3,7],[10000]*200],0,100)),lambda s:(lambda a:max((j-i)*min(a[i],a[j]) for i in range(len(a)) for j in range(i+1,len(a))))(toks(s)[1:]),function=('array','maxArea','max_area','height'))
add('15','三数之和','3sum','MEDIUM',['数组','排序','双指针'],'找出和为零的不同三元组，每组按值递增，组间按字典序排列。输出组数，再依次输出三元组。','n，随后 n 个整数。','0 <= n <= 50；值 -1000..1000。',ARRAY_INPUT+'''for i in range(n):
    for j in range(i+1,n):
        if a[j]<a[i]:
            x=a[i]
            a[i]=a[j]
            a[j]=x
cnt=0
for i in range(n):
    if i==0 or a[i]!=a[i-1]:
        for j in range(i+1,n):
            if j==i+1 or a[j]!=a[j-1]:
                for k in range(j+1,n):
                    if (k==j+1 or a[k]!=a[k-1]) and a[i]+a[j]+a[k]==0:
                        b[cnt*3]=a[i]
                        b[cnt*3+1]=a[j]
                        b[cnt*3+2]=a[k]
                        cnt=cnt+1
emit(cnt)
for i in range(cnt*3):
    emit(b[i])
''',arr_cases(arrays([[],[0,0,0],[-1,0,1,2,-1,-4]],-5,5)),lambda s:(lambda ts:[len(ts)]+list(itertools.chain.from_iterable(ts)))(sorted(set(tuple(sorted(t)) for t in itertools.combinations(toks(s)[1:],3) if sum(t)==0))))
add('17','电话号码的字母组合','letter-combinations-of-a-phone-number','MEDIUM',['回溯','字符串'],'将 2..9 数字映射到电话键盘字母（2=abc,3=def,4=ghi,5=jkl,6=mno,7=pqrs,8=tuv,9=wxyz），按字典序列出全部组合。空输入用 - 表示，输出组合数量，再输出各字符串。','一串 2..9；空串输入 -。','长度 0..4。',STRING_INPUT+'''total=1
if n==0:
    total=0
for i in range(n):
    x=a[i]-50
    b[i]=3
    if x==5 or x==7:
        b[i]=4
    d[i]=97+x*3
    if x>=6:
        d[i]=d[i]+1
for i in range(n):
    total=total*b[i]
emit(total)
for k in range(total):
    x=k
    for i in range(n-1,-1,-1):
        e[i]=d[i]+x%b[i]
        x=x//b[i]
    emitstr(e,n)
''',simple_cases(['-','2','7','23','79','9999']+[''.join(rng.choice('23456789') for z in range(rng.randint(1,4))) for _ in range(16)]),lambda s:(lambda xs: str(len(xs))+'\n'+'\n'.join(xs)+'\n')([] if s.strip()=='-' else [''.join(t) for t in itertools.product(*[{'2':'abc','3':'def','4':'ghi','5':'jkl','6':'mno','7':'pqrs','8':'tuv','9':'wxyz'}[x] for x in s.strip()])]))
add('19','删除链表的倒数第 N 个结点','remove-nth-node-from-end-of-list','MEDIUM',['链表','双指针'],'删除链表倒数第 k 个结点，输出剩余结点的值序列。链表以数组表示。','n k，随后 n 个整数。','1 <= k <= n <= 200；结点值 -1000..1000。','''n=read()
k=read()
for i in range(n):
    x=read()
    if i!=n-k:
        emit(x)
''',[arr_input(a,k) for a,k in [([1],1),([1,2,3,4,5],2)]+[(a,rng.randint(1,len(a))) for a in arrays([],0,10)]],lambda s:toks(s)[2:2+toks(s)[0]-toks(s)[1]]+toks(s)[3+toks(s)[0]-toks(s)[1]:])
add('33','搜索旋转排序数组','search-in-rotated-sorted-array','MEDIUM',['二分查找'],'严格递增数组经过一次旋转，求 target 的零基下标，不存在输出 -1。','n target，随后 n 个互不相同整数。','0 <= n <= 200；元素和 target 为 -1000..1000。','''n=read()
t=read()
for i in range(n):
    a[i]=read()
l=0
r=n-1
ans=-1
while l<=r:
    m=(l+r)//2
    if a[m]==t:
        ans=m
        l=r+1
    else:
        if a[l]<=a[m]:
            if a[l]<=t and t<a[m]:
                r=m-1
            else:
                l=m+1
        else:
            if a[m]<t and t<=a[r]:
                l=m+1
            else:
                r=m-1
emit(ans)
''',[arr_input(a,t) for a,t in [([],1),([4,5,6,7,0,1,2],0),([1],0)]+[(a[k:]+a[:k],rng.randint(-30,30)) for a,k in [(sorted(set(rng.randint(-20,20) for z in range(30))),rng.randint(0,10)) for _ in range(16)]]],lambda s:toks(s)[2:].index(toks(s)[1]) if toks(s)[1] in toks(s)[2:] else -1)
add('34','在排序数组中查找首尾位置','find-first-and-last-position-of-element-in-sorted-array','MEDIUM',['二分查找'],'在非降序数组中找出 target 首次和末次出现的下标，不存在输出 -1 -1。','n target，随后 n 个整数。','0 <= n <= 200；值和 target 为 -1000..1000。','''n=read()
t=read()
for i in range(n):
    a[i]=read()
left=-1
right=-1
l=0
r=n
while l<r:
    m=(l+r)//2
    if a[m]<t:
        l=m+1
    else:
        r=m
if l<n and a[l]==t:
    left=l
l=0
r=n
while l<r:
    m=(l+r)//2
    if a[m]<=t:
        l=m+1
    else:
        r=m
if left!=-1:
    right=l-1
emit(left)
emit(right)
''',[arr_input(a,t) for a,t in [([],0),([1,1,1],1),([5,7,7,8,8,10],8)]+[(sorted(x),rng.randint(-4,4)) for x in arrays([],-4,4)]],lambda s:(lambda ids:[ids[0],ids[-1]] if ids else [-1,-1])([i for i,x in enumerate(toks(s)[2:]) if x==toks(s)[1]]))
add('46','全排列','permutations','MEDIUM',['回溯'],'列出互不相同整数的所有排列，按字典序排列。先输出排列数量，再输出每个排列。','n，随后 n 个互不相同整数。','1 <= n <= 7；元素 -100..100。',ARRAY_INPUT+'''for i in range(n):
    for j in range(i+1,n):
        if a[j]<a[i]:
            x=a[i]
            a[i]=a[j]
            a[j]=x
total=1
for i in range(1,n+1):
    total=total*i
emit(total)
for z in range(total):
    for i in range(n):
        emit(a[i])
    k=n-2
    while k>=0 and a[k]>=a[k+1]:
        k=k-1
    if k>=0:
        j=n-1
        while a[j]<=a[k]:
            j=j-1
        x=a[k]
        a[k]=a[j]
        a[j]=x
        l=k+1
        r=n-1
        while l<r:
            x=a[l]
            a[l]=a[r]
            a[r]=x
            l=l+1
            r=r-1
''',arr_cases([[1],[1,2,3],list(range(7))]+[rng.sample(list(range(-10,11)),rng.randint(1,6)) for _ in range(10)]),lambda s:(lambda ps:[len(ps)]+list(itertools.chain.from_iterable(ps)))(sorted(itertools.permutations(toks(s)[1:]))))
add('48','旋转图像','rotate-image','MEDIUM',['矩阵'],'将 n×n 矩阵顺时针旋转 90 度，按行输出旋转后的元素。','n，随后 n 行，每行 n 个整数。','1 <= n <= 20；元素 -1000..1000。','''n=read()
for i in range(n*n):
    a[i]=read()
for i in range(n):
    for j in range(n):
        emit(a[(n-1-j)*n+i])
''',[str(n)+'\n'+emit_tokens([rng.randint(-10,10) for _ in range(n*n)]) for n in [1,2,3,20]+[rng.randint(1,10) for _ in range(16)]],lambda s:(lambda n,a:[a[(n-1-j)*n+i] for i in range(n) for j in range(n)])(toks(s)[0],toks(s)[1:]))
add('54','螺旋矩阵','spiral-matrix','MEDIUM',['矩阵'],'从左上角出发，按顺时针螺旋顺序输出矩阵的所有元素。','r c，随后 r 行，每行 c 个整数。','1 <= r,c <= 20；元素 -1000..1000。','''rows=read()
cols=read()
for i in range(rows*cols):
    a[i]=read()
top=0
bottom=rows-1
left=0
right=cols-1
while top<=bottom and left<=right:
    for j in range(left,right+1):
        emit(a[top*cols+j])
    top=top+1
    for i in range(top,bottom+1):
        emit(a[i*cols+right])
    right=right-1
    if top<=bottom:
        for j in range(right,left-1,-1):
            emit(a[bottom*cols+j])
        bottom=bottom-1
    if left<=right:
        for i in range(bottom,top-1,-1):
            emit(a[i*cols+left])
        left=left+1
''',[f'{r} {c}\n'+emit_tokens(list(range(r*c))) for r,c in [(1,1),(1,20),(20,1),(3,4),(20,20)]+[(rng.randint(1,10),rng.randint(1,10)) for _ in range(16)]],lambda s: spiral_oracle(toks(s)))
def spiral_oracle(ts):
    r,c=ts[:2]; grid=[ts[2+i*c:2+(i+1)*c] for i in range(r)]; ans=[]
    while grid:
        ans+=grid.pop(0)
        grid=[list(x) for x in zip(*grid)][::-1]
    return ans
add('55','跳跃游戏','jump-game','MEDIUM',['贪心'],'从数组下标 0 出发，a[i] 表示最多向右跳多少步，判断能否到达最后一个位置。能输出 1，否则 0。','n，随后 n 个非负整数。','1 <= n <= 200；a[i] 0..200。',ARRAY_INPUT+'''far=0
for i in range(n):
    if i<=far:
        far=mx(far,i+a[i])
ok=0
if far>=n-1:
    ok=1
emit(ok)
''',arr_cases(arrays([[0],[2,3,1,1,4],[3,2,1,0,4],[0]*200],0,5)),lambda s:jump_oracle(toks(s)[1:]))
def jump_oracle(a):
    reach={0}
    for i in range(len(a)):
        if i in reach: reach.update(range(i+1,min(len(a),i+a[i]+1)))
    return int(len(a)-1 in reach)
add('56','合并区间','merge-intervals','MEDIUM',['排序','区间'],'合并闭区间中有交集的区间（端点相等也合并）。输出区间数量，再输出按左端点排序的区间。','n，随后 n 行，每行 left right。','0 <= n <= 100；-1000 <= left <= right <= 1000。','''n=read()
for i in range(n):
    a[i]=read()
    b[i]=read()
for i in range(n):
    for j in range(i+1,n):
        if a[j]<a[i]:
            x=a[i]
            a[i]=a[j]
            a[j]=x
            x=b[i]
            b[i]=b[j]
            b[j]=x
k=0
for i in range(n):
    if k==0 or a[i]>e[k-1]:
        d[k]=a[i]
        e[k]=b[i]
        k=k+1
    else:
        e[k-1]=mx(e[k-1],b[i])
emit(k)
for i in range(k):
    emit(d[i])
    emit(e[i])
''',[str(len(ps))+'\n'+''.join(f'{a} {b}\n' for a,b in ps) for ps in [[],[(1,3),(2,6),(8,10),(15,18)],[(1,1),(1,2)]]]+[str(len(ps))+'\n'+''.join(f'{a} {b}\n' for a,b in ps) for ps in [[sorted([rng.randint(-10,10),rng.randint(-10,10)]) for j in range(rng.randint(1,30))] for _ in range(16)]],lambda s:interval_oracle(toks(s)))
def interval_oracle(ts):
    ps=sorted(zip(ts[1::2],ts[2::2])); ans=[]
    for l,r in ps:
        if ans and l<=ans[-1][1]: ans[-1]=(ans[-1][0],max(r,ans[-1][1]))
        else: ans.append((l,r))
    return [len(ans)]+list(itertools.chain.from_iterable(ans))
add('62','不同路径','unique-paths','MEDIUM',['动态规划','组合数学'],'从 r×c 网格左上角到右下角，每次只向右或向下走一步，求路径数量。','r c。','1 <= r,c <= 15；答案在 32 位有符号整数内。','''rows=read()
cols=read()
for j in range(cols):
    a[j]=1
for i in range(1,rows):
    for j in range(1,cols):
        a[j]=a[j]+a[j-1]
emit(a[cols-1])
''',[f'{r} {c}\n' for r,c in [(1,1),(1,15),(15,1),(15,15),(3,7)]+[(rng.randint(1,15),rng.randint(1,15)) for _ in range(16)]],lambda s:math.comb(sum(toks(s))-2,toks(s)[0]-1))
add('64','最小路径和','minimum-path-sum','MEDIUM',['动态规划','矩阵'],'非负权值网格中，从左上角到右下角，只能向右或向下，求路径上所有格子的最小权值和。','r c，随后 r 行，每行 c 个权值。','1 <= r,c <= 20；权值 0..1000。','''rows=read()
cols=read()
for i in range(rows):
    for j in range(cols):
        x=read()
        if i==0 and j==0:
            a[j]=x
        else:
            if i==0:
                a[j]=a[j-1]+x
            else:
                if j==0:
                    a[j]=a[j]+x
                else:
                    a[j]=mn(a[j],a[j-1])+x
emit(a[cols-1])
''',[f'{r} {c}\n'+emit_tokens([rng.randint(0,1000) for _ in range(r*c)]) for r,c in [(1,1),(1,20),(20,1),(20,20)]+[(rng.randint(1,6),rng.randint(1,6)) for _ in range(16)]],lambda s:grid_oracle(toks(s)))
def grid_oracle(ts):
    r,c=ts[:2]; a=ts[2:]
    @functools.lru_cache(None)
    def f(i,j):
        if i==r-1 and j==c-1: return a[i*c+j]
        return a[i*c+j]+min([f(i+1,j)] if j==c-1 else [f(i,j+1)] if i==r-1 else [f(i+1,j),f(i,j+1)])
    return f(0,0)
add('75','颜色分类','sort-colors','MEDIUM',['数组','双指针'],'将仅含 0、1、2 的数组按非降序排列，输出排序后的数组。','n，随后 n 个整数。','0 <= n <= 200；a[i] 属于 {0,1,2}。',ARRAY_INPUT+'''for i in range(n):
    b[a[i]]=b[a[i]]+1
for x in range(3):
    for i in range(b[x]):
        emit(x)
''',arr_cases(arrays([[],[0],[2,0,2,1,1,0],[2]*200],0,2)),lambda s:sorted(toks(s)[1:]))
# HARD LeetCode 8.
add('4','寻找两个有序数组的中位数','median-of-two-sorted-arrays','HARD',['二分查找'],'求两个非降序数组合并后的中位数。为避免浮点歧义，本题输出中位数的两倍（整数）。','n m，随后 n 个和 m 个整数。','0 <= n,m <= 200；n+m >= 1；值 -1000..1000。','''n=read()
m=read()
for i in range(n):
    a[i]=read()
for i in range(m):
    b[i]=read()
l=0
r=n
found=0
while l<=r and found==0:
    i=(l+r)//2
    j=(n+m+1)//2-i
    if j<0:
        r=i-1
    else:
        if j>m:
            l=i+1
        else:
            x=-1000000000
            y=-1000000000
            p=1000000000
            q=1000000000
            if i>0:
                x=a[i-1]
            if j>0:
                y=b[j-1]
            if i<n:
                p=a[i]
            if j<m:
                q=b[j]
            if x<=q and y<=p:
                ans=mx(x,y)*2
                if (n+m)%2==0:
                    ans=mx(x,y)+mn(p,q)
                emit(ans)
                found=1
            else:
                if x>q:
                    r=i-1
                else:
                    l=i+1
''',[f'{len(a)} {len(b)}\n'+emit_tokens(a)+emit_tokens(b) for a,b in [([],[1]),([1,3],[2]),([1,2],[3,4]),([0],[0]),(list(range(200)),list(range(-200,0)))]]+[f'{len(a)} {len(b)}\n'+emit_tokens(a)+emit_tokens(b) for a,b in [(sorted(rng.randint(-100,100) for i in range(rng.randint(0,30))),sorted(rng.randint(-100,100) for i in range(rng.randint(1,30)))) for z in range(16)]],lambda s:(lambda a:2*a[len(a)//2] if len(a)%2 else a[len(a)//2-1]+a[len(a)//2])(sorted(toks(s)[2:])))
add('32','最长有效括号','longest-valid-parentheses','HARD',['字符串','栈','动态规划'],'字符串仅由左右圆括号组成，求最长连续有效括号子串的长度。空串用 - 表示。','一个括号字符串。','长度 0..200。',STRING_INPUT+'''top=0
b[0]=-1
ans=0
for i in range(n):
    if a[i]==40:
        top=top+1
        b[top]=i
    else:
        top=top-1
        if top<0:
            top=0
            b[0]=i
        else:
            ans=mx(ans,i-b[top])
emit(ans)
''',simple_cases(strings(['-','(',')','()','(()',')()())','('*100+')'*100],'()')),lambda s:parens_oracle(s.strip().replace('-','')),function=('string','longestValidParentheses','longest_valid_parentheses','s'))
def parens_oracle(s):
    ans=0
    for i in range(len(s)):
        bal=0
        for j in range(i,len(s)):
            bal+=1 if s[j]=='(' else -1
            if bal<0: break
            if bal==0: ans=max(ans,j-i+1)
    return ans
add('41','缺失的第一个正数','first-missing-positive','HARD',['数组','哈希'],'给定未排序整数数组，求其中没有出现的最小正整数。','n，随后 n 个整数。','0 <= n <= 200；值 -1000000..1000000。',ARRAY_INPUT+'''for i in range(n):
    if a[i]>0 and a[i]<=n:
        b[a[i]]=1
ans=1
while ans<=n and b[ans]==1:
    ans=ans+1
emit(ans)
''',arr_cases(arrays([[],[1],[3,4,-1,1],[7,8,9,11,12],list(range(1,201))],-20,20)),lambda s:next(i for i in itertools.count(1) if i not in set(toks(s)[1:])),function=('array','firstMissingPositive','first_missing_positive','nums'))
add('42','接雨水','trapping-rain-water','HARD',['数组','双指针'],'单位宽度柱子的高度给定，求下雨后可积存的总水量。','n，随后 n 个非负高度。','1 <= n <= 200；高度 0..10000。',ARRAY_INPUT+'''l=0
r=n-1
lm=0
rm=0
ans=0
while l<=r:
    if lm<=rm:
        lm=mx(lm,a[l])
        ans=ans+lm-a[l]
        l=l+1
    else:
        rm=mx(rm,a[r])
        ans=ans+rm-a[r]
        r=r-1
emit(ans)
''',arr_cases(arrays([[0],[0,1,0,2,1,0,1,3,2,1,2,1],[10000]+[0]*198+[10000]],0,100)),lambda s:(lambda a:sum(min(max(a[:i+1]),max(a[i:]))-a[i] for i in range(len(a))))(toks(s)[1:]),function=('array','trap','trap','height'))
add('76','最小覆盖子串','minimum-window-substring','HARD',['字符串','滑动窗口'],'找出 s 中包含 t 全部字符及出现次数的最短连续子串；同长度取起点最小的。无解输出 -。','两个小写字符串 s t；空串使用 -。','s,t 长度 0..200。','''n=readstr(a)
m=readstr(b)
for i in range(m):
    d[b[i]]=d[b[i]]+1
left=0
need=m
best=n+1
start=0
for right in range(n):
    x=a[right]
    if e[x]<d[x]:
        need=need-1
    e[x]=e[x]+1
    while need==0 and left<=right:
        if right-left+1<best:
            best=right-left+1
            start=left
        x=a[left]
        e[x]=e[x]-1
        if e[x]<d[x]:
            need=need+1
        left=left+1
if m==0 or best==n+1:
    emitstr(b,0)
else:
    for i in range(best):
        b[i]=a[start+i]
    emitstr(b,best)
''',[a+' '+b+'\n' for a,b in [('adobecodebanc','abc'),('a','aa'),('-','a'),('abc','-'),('aaaa','aa')]+[(''.join(rng.choice('abc') for z in range(rng.randint(1,20))),''.join(rng.choice('abc') for z in range(rng.randint(1,8)))) for _ in range(16)]],lambda s:window_oracle(s))
def window_oracle(s):
    import collections
    a,b=[x if x!='-' else '' for x in s.split()]; need=collections.Counter(b)
    if not b:return '-\n'
    poss=[a[i:j] for i in range(len(a)) for j in range(i+1,len(a)+1) if not need-collections.Counter(a[i:j])]
    return (min(poss,key=len) if poss else '-')+'\n'
add('84','柱状图中最大的矩形','largest-rectangle-in-histogram','HARD',['数组','单调栈'],'柱子宽度为 1，求柱状图内可容纳矩形的最大面积。','n，随后 n 个非负高度。','1 <= n <= 200；高度 0..10000。',ARRAY_INPUT+'''top=0
ans=0
for i in range(n+1):
    h=0
    if i<n:
        h=a[i]
    while top>0 and a[b[top-1]]>h:
        top=top-1
        x=a[b[top]]
        left=-1
        if top>0:
            left=b[top-1]
        ans=mx(ans,x*(i-left-1))
    b[top]=i
    top=top+1
emit(ans)
''',arr_cases(arrays([[0],[2,1,5,6,2,3],[10000]*200,list(range(200))],0,100)),lambda s:(lambda a:max(min(a[i:j])*(j-i) for i in range(len(a)) for j in range(i+1,len(a)+1)))(toks(s)[1:]),function=('array','largestRectangleArea','largest_rectangle_area','heights'))
add('239','滑动窗口最大值','sliding-window-maximum','HARD',['数组','单调队列'],'对于长度 k 的每个连续窗口，依次输出其中最大值。','n k，随后 n 个整数。','1 <= k <= n <= 200；元素 -10000..10000。','''n=read()
k=read()
for i in range(n):
    a[i]=read()
head=0
tail=0
for i in range(n):
    while head<tail and b[head]<=i-k:
        head=head+1
    while head<tail and a[b[tail-1]]<=a[i]:
        tail=tail-1
    b[tail]=i
    tail=tail+1
    if i>=k-1:
        emit(a[b[head]])
''',[arr_input(a,k) for a,k in [([1],1),([1,3,-1,-3,5,3,6,7],3),([-1,-2,-3],3),(list(range(200)),200)]+[(a,rng.randint(1,len(a))) for a in arrays([])]],lambda s:(lambda a,k:[max(a[i:i+k]) for i in range(len(a)-k+1)])(toks(s)[2:],toks(s)[1]))
add('312','戳气球','burst-balloons','HARD',['区间动态规划'],'依次戳破全部气球。每次得分为该气球与当前左右相邻气球数值的乘积，缺失邻居视为 1。求最大总得分。','n，随后 n 个非负数值。','1 <= n <= 60；数值 0..100。','''n=read()
a[0]=1
a[n+1]=1
for i in range(1,n+1):
    a[i]=read()
width=n+2
for length in range(2,n+2):
    for left in range(0,n+2-length):
        right=left+length
        best=0
        for k in range(left+1,right):
            best=mx(best,d[left*width+k]+d[k*width+right]+a[left]*a[k]*a[right])
        d[left*width+right]=best
emit(d[n+1])
''',arr_cases([[0],[1],[3,1,5,8],[100]*60]+[[rng.randint(0,6) for i in range(rng.randint(1,7))] for _ in range(16)]),lambda s: balloons_oracle(toks(s)[1:]))
def balloons_oracle(a):
    if len(a)>8: # independent recursive interval formulation for maximum-size stress case
        @functools.lru_cache(None)
        def f(t):
            if len(t)<3:return 0
            return max(f(t[:k+1])+f(t[k:])+t[0]*t[k]*t[-1] for k in range(1,len(t)-1))
        return f(tuple([1]+a+[1]))
    @functools.lru_cache(None)
    def f(t):
        return max([0]+[(t[i-1] if i else 1)*t[i]*(t[i+1] if i+1<len(t) else 1)+f(t[:i]+t[i+1:]) for i in range(len(t))])
    return f(tuple(a))
# Four supplementary Luogu problems: one EASY + three MEDIUM.
add('P1428','小鱼比可爱','p1428','EASY',['数组'],'按顺序给出每条小鱼的数值，对每一条鱼统计在它之前且数值严格小于它的小鱼数量。','n，随后 n 个整数。','1 <= n <= 100；数值 0..1000。',ARRAY_INPUT+'''for i in range(n):
    cnt=0
    for j in range(i):
        if a[j]<a[i]:
            cnt=cnt+1
    emit(cnt)
''',arr_cases(arrays([[1],[1,1,1],[1,2,3,4],list(range(100))],0,100)),lambda s:(lambda a:[sum(y<x for y in a[:i]) for i,x in enumerate(a)])(toks(s)[1:]),platform='洛谷')
add('P1048','采药','p1048','MEDIUM',['动态规划','01背包'],'有 n 株草药，每株有采摘时间和价值，最多使用 T 时间，每株至多采一次，求最大价值。','T n，随后 n 行 time value。','1 <= T <= 1000；1 <= n <= 100；time,value 1..100。','''limit=read()
n=read()
for i in range(n):
    cost=read()
    value=read()
    for j in range(limit,cost-1,-1):
        a[j]=mx(a[j],a[j-cost]+value)
emit(a[limit])
''',[f'{T} {len(ps)}\n'+''.join(f'{w} {v}\n' for w,v in ps) for T,ps in [(1,[(2,10)]),(5,[(2,3),(3,4)]),(1000,[(10,100)]*100)]+[(rng.randint(1,30),[(rng.randint(1,15),rng.randint(1,30)) for z in range(rng.randint(1,12))]) for _ in range(16)]],lambda s:knapsack_oracle(toks(s)),platform='洛谷')
def knapsack_oracle(ts):
    T,n=ts[:2]; ps=list(zip(ts[2::2],ts[3::2]))
    if n<=15:return max(sum(ps[i][1] for i in range(n) if mask>>i&1) for mask in range(1<<n) if sum(ps[i][0] for i in range(n) if mask>>i&1)<=T)
    @functools.lru_cache(None)
    def f(i,t):
        if i==n:return 0
        return max(f(i+1,t),f(i+1,t-ps[i][0])+ps[i][1] if ps[i][0]<=t else 0)
    return f(0,T)
add('P1216','数字三角形','p1216','MEDIUM',['动态规划'],'从数字三角形顶端出发，每一步走到下一行相邻的两个位置之一，求到最底层的最大数字和。','n，随后第 i 行 i 个非负整数。','1 <= n <= 100；数值 0..100。','''n=read()
for i in range(n):
    for j in range(i+1):
        b[j]=read()
    for j in range(i,-1,-1):
        best=0
        if j<i:
            best=a[j]
        if j>0:
            best=mx(best,a[j-1])
        a[j]=best+b[j]
ans=0
for j in range(n):
    ans=mx(ans,a[j])
emit(ans)
''',[str(n)+'\n'+emit_tokens([rng.randint(0,100) for z in range(n*(n+1)//2)]) for n in [1,2,100]+[rng.randint(1,12) for _ in range(16)]],lambda s:triangle_oracle(toks(s)),platform='洛谷')
def triangle_oracle(ts):
    n=ts[0]; a=ts[1:]
    @functools.lru_cache(None)
    def f(i,j):
        if i==n-1:return a[i*(i+1)//2+j]
        return a[i*(i+1)//2+j]+max(f(i+1,j),f(i+1,j+1))
    return f(0,0)
add('P1439','排列的最长公共子序列','p1439','MEDIUM',['最长递增子序列','动态规划'],'给定两个长度为 n 的排列，元素恰为 1..n 各一次，求最长公共子序列长度。子序列无需连续。','n，随后两个各 n 个整数的排列。','1 <= n <= 200。','''n=read()
for i in range(n):
    a[i]=read()
for i in range(n):
    x=read()
    b[x]=i
ans=0
for i in range(n):
    d[i]=1
    for j in range(i):
        if b[a[j]]<b[a[i]]:
            d[i]=mx(d[i],d[j]+1)
    ans=mx(ans,d[i])
emit(ans)
''',[str(n)+'\n'+emit_tokens(a)+emit_tokens(b) for n,a,b in [(1,[1],[1]),(5,[1,2,3,4,5],[5,4,3,2,1]),(200,list(range(1,201)),list(range(1,201)))]+[(n,rng.sample(list(range(1,n+1)),n),rng.sample(list(range(1,n+1)),n)) for n in [rng.randint(2,30) for _ in range(16)]]],lambda s:lcs_oracle(toks(s)),platform='洛谷')
def lcs_oracle(ts):
    n=ts[0];a=ts[1:n+1];b=ts[n+1:];dp=[[0]*(n+1) for _ in range(n+1)]
    for i in range(n):
        for j in range(n):dp[i+1][j+1]=dp[i][j]+1 if a[i]==b[j] else max(dp[i][j+1],dp[i+1][j])
    return dp[n][n]
# Five original tasks, two EASY, three MEDIUM.
add('SAP001','学习打卡最长连续段','study-streak','EASY',['数组'],'0/1 序列表示每天是否完成学习任务，求连续完成任务的最长天数。','n，随后 n 个 0 或 1。','0 <= n <= 200。',ARRAY_INPUT+'''ans=0
cnt=0
for i in range(n):
    if a[i]==1:
        cnt=cnt+1
    else:
        cnt=0
    ans=mx(ans,cnt)
emit(ans)
''',arr_cases(arrays([[],[0],[1],[1]*200,[1,1,0,1]],0,1)),lambda s:max([0]+[len(x) for x in ''.join(map(str,toks(s)[1:])).split('0')]),platform='原创')
add('SAP002','社团积分区间查询','club-range-sum','EASY',['前缀和'],'给出每天获得的积分，对每个闭区间 [l,r] 查询积分总和。下标从 1 开始。','n q，随后 n 个整数，再有 q 行 l r。','1 <= n,q <= 200；积分 -1000..1000；1 <= l <= r <= n。','''n=read()
q=read()
for i in range(1,n+1):
    a[i]=a[i-1]+read()
for i in range(q):
    l=read()
    r=read()
    emit(a[r]-a[l-1])
''',[range_case(n) for n in [1,2,200]+[rng.randint(1,30) for _ in range(16)]] if False else [],lambda s:range_oracle(toks(s)),platform='原创')
def range_case(n):
    a=[rng.randint(-1000,1000) for _ in range(n)]; ps=[(1,n),(1,1),(n,n)]+[sorted([rng.randint(1,n),rng.randint(1,n)]) for _ in range(10)]
    return f'{n} {len(ps)}\n'+emit_tokens(a)+''.join(f'{l} {r}\n' for l,r in ps)
def range_oracle(ts):
    n,q=ts[:2];a=ts[2:2+n];rest=ts[2+n:];return [sum(a[l-1:r]) for l,r in zip(rest[::2],rest[1::2])]
ITEMS[-1]['_cases']=[range_case(n) for n in [1,2,200]+[rng.randint(1,30) for _ in range(16)]]
add('SAP003','学习计划的最短达标区间','study-min-window','MEDIUM',['滑动窗口'],'每天的学习积分为正数，寻找总积分不少于 target 的最短连续区间，输出长度；无解输出 0。','n target，随后 n 个正整数。','1 <= n <= 200；积分 1..1000；1 <= target <= 1000000。','''n=read()
t=read()
for i in range(n):
    a[i]=read()
l=0
sumv=0
ans=n+1
for r in range(n):
    sumv=sumv+a[r]
    while sumv>=t:
        ans=mn(ans,r-l+1)
        sumv=sumv-a[l]
        l=l+1
if ans==n+1:
    ans=0
emit(ans)
''',[arr_input(a,t) for a,t in [([1],1),([1],2),([2,3,1,2,4,3],7),([1000]*200,200000)]+[(a,rng.randint(1,sum(a)+10)) for a in arrays([],1,20)]],lambda s:(lambda a,t:min([len(a)+1]+[j-i for i in range(len(a)) for j in range(i+1,len(a)+1) if sum(a[i:j])>=t]))(toks(s)[2:],toks(s)[1]) if any(sum(toks(s)[2:])>=toks(s)[1] for _ in [0]) else 0,platform='原创')
add('SAP004','活动签到连通组','attendance-components','MEDIUM',['图','并查集'],'有 n 名成员及若干无向联系，统计通过联系可互相到达的成员组数，孤立成员也计一组。','n m，随后 m 行 u v；成员编号 1..n。','1 <= n <= 200；0 <= m <= 400；允许重复边与自环。','''n=read()
m=read()
for i in range(n+1):
    a[i]=i
ans=n
for i in range(m):
    u=read()
    v=read()
    while a[u]!=u:
        u=a[u]
    while a[v]!=v:
        v=a[v]
    if u!=v:
        a[u]=v
        ans=ans-1
emit(ans)
''',[graph_case(n) for n in [1,2,200]+[rng.randint(1,20) for _ in range(16)]] if False else [],lambda s:components_oracle(toks(s)),platform='原创')
def graph_case(n):
    ps=[(rng.randint(1,n),rng.randint(1,n)) for z in range(rng.randint(0,min(400,n*2)))];return f'{n} {len(ps)}\n'+''.join(f'{a} {b}\n' for a,b in ps)
def components_oracle(ts):
    n,m=ts[:2];g=[set() for z in range(n+1)]
    for u,v in zip(ts[2::2],ts[3::2]):g[u].add(v);g[v].add(u)
    seen=set();cnt=0
    for u in range(1,n+1):
        if u not in seen:
            cnt+=1;stack=[u];seen.add(u)
            while stack:
                for v in g[stack.pop()]:
                    if v not in seen:seen.add(v);stack.append(v)
    return cnt
ITEMS[-1]['_cases']=[graph_case(n) for n in [1,2,200]+[rng.randint(1,20) for _ in range(16)]]+['5 0\n','5 4\n1 2\n2 3\n3 4\n4 5\n']
add('SAP005','积分兑换最少券数','points-min-coins','MEDIUM',['动态规划','完全背包'],'有 n 种兑换券面额，每种可无限使用，求恰好兑换 amount 积分的最少券数；无法兑换输出 -1。','n amount，随后 n 个正整数面额。','1 <= n <= 20；0 <= amount <= 1000；面额 1..1000。','''n=read()
t=read()
for i in range(n):
    a[i]=read()
for i in range(1,t+1):
    b[i]=1000000
    for j in range(n):
        if a[j]<=i:
            b[i]=mn(b[i],b[i-a[j]]+1)
ans=b[t]
if ans>=1000000:
    ans=-1
emit(ans)
''',[arr_input(a,t) for a,t in [([1],0),([2],3),([1,2,5],11),([1],1000)]+[([rng.randint(1,20) for z in range(rng.randint(1,8))],rng.randint(0,100)) for _ in range(16)]],lambda s:coins_oracle(toks(s)),platform='原创')
def coins_oracle(ts):
    n,t=ts[:2];coins=ts[2:];dist={0:0};front=[0]
    while front:
        nxt=[]
        for x in front:
            if x==t:return dist[x]
            for c in coins:
                if x+c<=t and x+c not in dist:dist[x+c]=dist[x]+1;nxt.append(x+c)
        front=nxt
    return -1

# Tree representative replaces telephone combinations, keeping all counts unchanged.
ITEMS[:]=[x for x in ITEMS if x['sourceId']!='17']
def tree_case(nodes):
    return str(len(nodes))+'\n'+''.join(f'{v} {l} {r}\n' for v,l,r in nodes)
def random_tree(n):
    if not n:return []
    nodes=[[rng.randint(-1000,1000),-1,-1] for i in range(n)];slots=[(0,1),(0,2)]
    for i in range(1,n):
        p,c=slots.pop(rng.randrange(len(slots)));nodes[p][c]=i;slots.extend([(i,1),(i,2)])
    return nodes

def bst_oracle(s):
    ts=toks(s);nodes=list(zip(ts[1::3],ts[2::3],ts[3::3]))
    def check(i,lo,hi):
        if i<0:return True
        v,l,r=nodes[i]
        return lo<v<hi and check(l,lo,v) and check(r,v,hi)
    return int(check(0,-math.inf,math.inf)) if nodes else 1
add('98','验证二叉搜索树','validate-binary-search-tree','MEDIUM',['二叉树','深度优先搜索'],'判断二叉树是否满足严格二叉搜索树规则：每个结点的左子树所有值小于该结点，右子树所有值大于该结点；重复值不合法。空树合法，输出 1 或 0。','n，接下来 n 行 value left right；结点按 0..n-1 编号，根为 0；缺失子结点为 -1。输入保证结构为一棵树。','0 <= n <= 100；结点值 -1000..1000。','''n=read()
for i in range(n):
    a[i]=read()
    b[i]=read()
    d[i]=read()
top=0
cur=-1
if n>0:
    cur=0
prev=-1000000000
ok=1
while cur!=-1 or top>0:
    while cur!=-1:
        e[top]=cur
        top=top+1
        cur=b[cur]
    top=top-1
    cur=e[top]
    if a[cur]<=prev:
        ok=0
    prev=a[cur]
    cur=d[cur]
emit(ok)
''',[tree_case(ns) for ns in [[],[[0,-1,-1]],[[2,1,2],[1,-1,-1],[3,-1,-1]],[[5,1,2],[1,-1,-1],[4,3,4],[3,-1,-1],[6,-1,-1]],[[2,1,-1],[2,-1,-1]],[[i,-1,i+1 if i<99 else -1] for i in range(100)]]]+[tree_case(random_tree(rng.randint(1,20))) for _ in range(16)],bst_oracle,function=('tree','isValidBST','is_valid_bst','root'))
for item in ITEMS:
    if item['sourceId']=='9':item['_function']=('scalar','isPalindrome','is_palindrome','x')
    if item['sourceId']=='55':item['_function']=('array','canJump','can_jump','nums')
    if item['sourceId']=='35':item['_function']=('arrtarget','searchInsert','search_insert','nums')
    if item['sourceId']=='33':item['_function']=('arrtarget','search','search','nums')
    if item['sourceId']=='62':item['_function']=('scalarpair','uniquePaths','unique_paths','rows')


# Additional published-limit, degenerate, and adversarial inputs.
STRESS={
'9':['-100000000\n','99999999\n'],
'14':['20\n'+' '.join(['a'*100]*20)+'\n','20\n'+' '.join(['-' if i==19 else 'a'*100 for i in range(20)])+'\n'],
'21':['100 100\n'+emit_tokens(range(-100,0))+emit_tokens(range(100))],
'26':[arr_input([i//10 for i in range(200)]),arr_input([0]*200)],
'27':[arr_input([0]*200,0),arr_input(list(range(200)),1000)],
'28':['a'*199+'b '+'a'*199+'b\n','a'*200+' '+'b'*200+'\n'],
'35':[arr_input(list(range(200)),-1000),arr_input(list(range(200)),1000)],
'58':['50\n'+' '.join(['a'*100]*50)+'\n'],
'3':['a'*200+'\n','abcdefghijklmnopqrstuvwxyz'*7+'abcdefghijklmnopqr\n'],
'15':[arr_input([0]*50),arr_input(list(range(-25,25)))],
'19':[arr_input(list(range(200)),200),arr_input(list(range(200)),1)],
'33':[arr_input(list(range(100,200))+list(range(100)),0),arr_input(list(range(200)),1000)],
'34':[arr_input([0]*200,0),arr_input([0]*200,1)],
'56':['100\n'+''.join(f'{i} {i+1}\n' for i in range(100)),'100\n'+''.join(f'{i*2} {i*2}\n' for i in range(100))],
'76':['a'*200+' '+'a'*200+'\n','a'*200+' b\n'],
'239':[arr_input(list(range(200)),1),arr_input(list(range(200)),200)],
'P1428':[arr_input([5]*100)],
'P1048':['1000 100\n'+'100 100\n'*100],
'P1439':['200\n'+emit_tokens(range(1,201))+emit_tokens(range(200,0,-1))],
'SAP001':[arr_input([0]*200)],
'SAP002':['200 200\n'+emit_tokens([-1000]*200)+'1 200\n'*200],
'SAP003':[arr_input([1]*200,1000000)],
'SAP004':['200 400\n'+'1 1\n'*400],
'SAP005':[arr_input(list(range(2,42,2)),999),arr_input(list(range(1,21)),1000)],
}
for item in ITEMS:
    item['_cases'].extend(STRESS.get(item['sourceId'],[]))


def balanced_tree(values):
    nodes=[]
    def visit(lo,hi):
        if lo>=hi:return -1
        mid=(lo+hi)//2;idx=len(nodes);nodes.append([values[mid],-1,-1]);nodes[idx][1]=visit(lo,mid);nodes[idx][2]=visit(mid+1,hi);return idx
    visit(0,len(values));return nodes
for item in ITEMS:
    if item['sourceId']=='98':item['_cases'].append(tree_case([[5,-1,1],[6,2,-1],[4,-1,-1]]))
    if item['sourceId']=='98':item['_cases'].extend(tree_case(balanced_tree(sorted(rng.sample(list(range(-1000,1001)),rng.randint(1,100))))) for _ in range(10))
    item['sourceAuthor']='SAP OJ 题库整理组' if item['sourcePlatform']=='原创' else '原作者以来源页署名为准；本题面由 SAP OJ 题库整理组改写'
    item['sourceRetrievedAt']='2026-10-01'
    item['statementAdapted']=True
    item['sourceDifficulty']=item['difficulty'] if item['sourcePlatform']=='LeetCode' else None
    if item['sourceId']=='9':item['outputFormat']='是回文输出 1，否则输出 0。'


# Restricted imperative AST renderer. All generated algorithms use bounded i32.
class Renderer:
    def __init__(self,lang):self.lang=lang
    def expr(self,x):
        L=self.lang
        if isinstance(x,ast.Constant):return str(x.value).lower() if isinstance(x.value,bool) else str(x.value)
        if isinstance(x,ast.Name):return x.id
        if isinstance(x,ast.BinOp):
            op={ast.Add:'+',ast.Sub:'-',ast.Mult:'*',ast.FloorDiv:'/' if L!='python' else '//',ast.Mod:'%'}[type(x.op)]
            return '('+self.expr(x.left)+op+self.expr(x.right)+')'
        if isinstance(x,ast.UnaryOp):return '('+('-' if isinstance(x.op,ast.USub) else 'not ' if L=='python' else '!')+self.expr(x.operand)+')'
        if isinstance(x,ast.BoolOp):return '('+(' and ' if isinstance(x.op,ast.And) else ' or ' if L=='python' else ' || ').join(map(self.expr,x.values))+')' if L=='python' else '('+(' && ' if isinstance(x.op,ast.And) else ' || ').join(map(self.expr,x.values))+')'
        if isinstance(x,ast.Compare):
            ops={ast.Lt:'<',ast.LtE:'<=',ast.Gt:'>',ast.GtE:'>=',ast.Eq:'==',ast.NotEq:'!='}
            return '('+self.expr(x.left)+ops[type(x.ops[0])]+self.expr(x.comparators[0])+')'
        if isinstance(x,ast.Subscript):
            idx=self.expr(x.slice)
            if L=='rust':idx='('+idx+') as usize'
            return self.expr(x.value)+'['+idx+']'
        if isinstance(x,ast.Call):
            name=x.func.id;args=[self.expr(a) for a in x.args]
            if name=='read':return 'read()' if L in ['c','cpp','java','python'] else 'reader.read()'
            if name=='readstr':
                if L=='rust':return 'reader.readstr(&mut '+args[0]+')'
                return 'readstr('+args[0]+')'
            if name=='emitstr' and L=='rust':args[0]='&'+args[0]
            return name+'('+','.join(args)+')'
        raise ValueError(ast.dump(x))
    def stmts(self,nodes,level=1):
        out=[];L=self.lang;indent='    '*level
        for x in nodes:
            if isinstance(x,ast.Assign):out.append(indent+self.expr(x.targets[0])+'='+self.expr(x.value)+('' if L=='python' else ';'))
            elif isinstance(x,ast.Expr):out.append(indent+self.expr(x.value)+('' if L=='python' else ';'))
            elif isinstance(x,ast.Return):out.append(indent+'return '+self.expr(x.value)+('' if L=='python' else ';'))
            elif isinstance(x,ast.If):
                condition=self.expr(x.test)
                out.append(indent+'if '+condition+(':' if L=='python' else ' {'))
                out+=self.stmts(x.body,level+1)
                if L!='python':out.append(indent+'}')
                if x.orelse:
                    out.append(indent+('else:' if L=='python' else 'else {'));out+=self.stmts(x.orelse,level+1)
                    if L!='python':out.append(indent+'}')
            elif isinstance(x,ast.While):
                out.append(indent+'while '+self.expr(x.test)+(':' if L=='python' else ' {'));out+=self.stmts(x.body,level+1)
                if L!='python':out.append(indent+'}')
            elif isinstance(x,ast.For):
                name=x.target.id;args=x.iter.args
                start=ast.Constant(0) if len(args)==1 else args[0];end=args[0] if len(args)==1 else args[1];step=1 if len(args)<3 else ast.literal_eval(args[2]);cmp='<' if step>0 else '>'
                if L=='python':out.append(indent+'for '+name+' in range('+','.join(self.expr(z) for z in args)+'):');out+=self.stmts(x.body,level+1)
                elif L=='rust':
                    out.append(indent+name+'='+self.expr(start)+';');out.append(indent+'while '+name+cmp+self.expr(end)+' {');out+=self.stmts(x.body,level+1);out.append('    '*(level+1)+name+'+='+str(step)+';');out.append(indent+'}')
                else:out.append(indent+'for ('+name+'='+self.expr(start)+';'+name+cmp+self.expr(end)+';'+name+'+='+str(step)+') {');out+=self.stmts(x.body,level+1);out.append(indent+'}')
            else:raise ValueError(ast.dump(x))
        return out

def var_names(tree):return sorted({x.id for x in ast.walk(tree) if isinstance(x,ast.Name) and isinstance(x.ctx,ast.Store)}-{'a','b','d','e'})
def locals_block(lang,names,skip=()):
    names=[n for n in names if n not in skip];lines=[]
    if lang=='python':
        lines += [f'{x}=[0]*20000' for x in ('a','b','d','e') if x not in skip]
        if names:lines+=['='.join(names)+'=0']
    elif lang in ('c','cpp'):
        lines += [f'static int {x}[20000];' for x in ('a','b','d','e') if x not in skip]
        if names:lines+=['int '+','.join(x+'=0' for x in names)+';']
    elif lang=='java':
        lines += [f'int[] {x}=new int[20000];' for x in ('a','b','d','e') if x not in skip]
        if names:lines+=['int '+','.join(x+'=0' for x in names)+';']
    else:
        lines += [f'let mut {x}=vec![0i32;20000];' for x in ('a','b','d','e') if x not in skip]
        lines += [f'let mut {x}:i32=0;' for x in names]
    return lines

C_HELP='''#include <stdio.h>
#include <stdlib.h>
#include <string.h>
int mn(int a,int b){return a<b?a:b;}
int mx(int a,int b){return a>b?a:b;}
int read(void){int x=0;if(scanf("%d",&x)!=1)return 0;return x;}
int readstr(int*a){char s[20001];if(scanf("%20000s",s)!=1)return 0;if(strcmp(s,"-")==0)return 0;int n=(int)strlen(s);for(int i=0;i<n;i++)a[i]=(unsigned char)s[i];return n;}
void emit(int x){printf("%d ",x);}
void emitstr(int*a,int n){if(n==0)putchar('-');for(int i=0;i<n;i++)putchar(a[i]);putchar(' ');}
'''
JAVA_HELP='''import java.io.*;
import java.util.*;
class Main {
static String[] tokens; static int cursor;
static int read(){return Integer.parseInt(tokens[cursor++]);}
static int readstr(int[] a){String s=tokens[cursor++];if(s.equals("-"))return 0;for(int i=0;i<s.length();i++)a[i]=s.charAt(i);return s.length();}
static int mn(int a,int b){return Math.min(a,b);}
static int mx(int a,int b){return Math.max(a,b);}
static void emit(int x){System.out.print(x+" ");}
static void emitstr(int[] a,int n){if(n==0)System.out.print("-");for(int i=0;i<n;i++)System.out.print((char)a[i]);System.out.print(" ");}
public static void main(String[] args)throws Exception{
tokens=new String(System.in.readAllBytes()).trim().split("\\\\s+");
'''
PY_HELP='''import sys
_tokens=iter(sys.stdin.read().split())
def read(): return int(next(_tokens))
def readstr(a):
    s=next(_tokens)
    if s=='-': return 0
    for i,c in enumerate(s): a[i]=ord(c)
    return len(s)
def emit(x): print(x,end=' ')
def emitstr(a,n): print(''.join(chr(x) for x in a[:n]) if n else '-',end=' ')
def mn(a,b): return min(a,b)
def mx(a,b): return max(a,b)
'''
RUST_HELP='''use std::io::{self,Read};
fn mn(a:i32,b:i32)->i32{a.min(b)}
fn mx(a:i32,b:i32)->i32{a.max(b)}
fn emit(x:i32){print!("{} ",x);}
fn emitstr(a:&Vec<i32>,n:i32){if n==0{print!("-");}for i in 0..n{print!("{}",a[i as usize] as u8 as char);}print!(" ");}
struct Reader{t:Vec<String>,p:usize}
impl Reader{
fn new()->Self{let mut s=String::new();io::stdin().read_to_string(&mut s).unwrap();Self{t:s.split_whitespace().map(String::from).collect(),p:0}}
fn read(&mut self)->i32{let x=self.t[self.p].parse().unwrap();self.p+=1;x}
fn readstr(&mut self,a:&mut Vec<i32>)->i32{let s=&self.t[self.p];self.p+=1;if s=="-"{return 0;}for(i,c)in s.bytes().enumerate(){a[i]=c as i32;}s.len() as i32}
}
'''

def render_stdio(code,lang):
    tree=ast.parse(code);locals_=locals_block(lang,var_names(tree));body='\n'.join(['    '+x for x in locals_]+Renderer(lang).stmts(tree.body))
    if lang in ('c','cpp'):return C_HELP+'int main(void){\n'+body+'\nreturn 0;\n}\n'
    if lang=='java':return JAVA_HELP+body+'\n}\n}\n'
    if lang=='rust':return RUST_HELP+'fn main(){\nlet mut reader=Reader::new();\n'+body+'\n}\n'
    return PY_HELP+body.replace('    ','',1) if False else PY_HELP+'\n'.join(locals_)+'\n'+'\n'.join(Renderer(lang).stmts(tree.body,0))+'\n'

STARTERS={
'c':'#include <stdio.h>\nint main(void){\n    // Read input, solve, and print the result.\n    return 0;\n}\n',
'cpp':'#include <iostream>\nusing namespace std;\nint main(){\n    // Read input, solve, and print the result.\n    return 0;\n}\n',
'java':'import java.io.*;\nimport java.util.*;\nclass Main {\n    public static void main(String[] args) throws Exception {\n        // Read input, solve, and print the result.\n    }\n}\n',
'python':'import sys\n\ndef main():\n    # Read input, solve, and print the result.\n    pass\n\nif __name__ == "__main__":\n    main()\n',
'rust':'use std::io::{self, Read};\nfn main() {\n    let mut input=String::new();\n    io::stdin().read_to_string(&mut input).unwrap();\n    // Read input, solve, and print the result.\n}\n'}
class ReturnEmit(ast.NodeTransformer):
    def visit_Expr(self,x):
        if isinstance(x.value,ast.Call) and x.value.func.id=='emit':return ast.Return(value=x.value.args[0])
        return x

def function_artifacts(item,lang):
    kind,method,rust_method,arg=item['_function']
    if kind in ('tree','arrtarget','scalarpair'):return extended_function_artifacts(item,lang)
    tree=ast.parse(item['_code'])
    count=2 if kind=='array' else 1;tree.body=tree.body[count:];tree=ReturnEmit().visit(tree)
    names=var_names(tree);skip=('a','n') if kind!='scalar' else (arg,)
    loc=locals_block(lang,names,skip)
    if kind=='scalar':init=[]
    elif kind=='array':init={'c':[f'int *a={arg};',f'int n={arg}Size;'],'cpp':[f'int *a={arg};',f'int n={arg}Size;'],'java':[f'int[] a={arg};',f'int n={arg}.length;'],'python':[f'a={arg}',f'n=len({arg})'],'rust':[f'let mut a={arg};','let n=a.len() as i32;']}[lang]
    else:init={'c':[f'int n=(int)strlen({arg});','int a[20000]={0};',f'for(int v=0;v<n;v++)a[v]=(unsigned char){arg}[v];'],'cpp':[f'int n=(int)strlen({arg});','int a[20000]={0};',f'for(int v=0;v<n;v++)a[v]=(unsigned char){arg}[v];'],'java':[f'int n={arg}.length();','int[] a=new int[20000];',f'for(int v=0;v<n;v++)a[v]={arg}.charAt(v);'],'python':[f'a=[ord(c) for c in {arg}]','n=len(a)'],'rust':[f'let mut a:Vec<i32>={arg}.bytes().map(|v|v as i32).collect();','let n=a.len() as i32;']}[lang]
    body='\n'.join('    '+x for x in init+loc)+'\n'+'\n'.join(Renderer(lang).stmts(tree.body))
    params={'c':f'int {arg}' if kind=='scalar' else f'int* {arg},int {arg}Size' if kind=='array' else f'char* {arg}', 'java':f'int {arg}' if kind=='scalar' else f'int[] {arg}' if kind=='array' else f'String {arg}', 'python':arg,'rust':arg+(':i32' if kind=='scalar' else ':Vec<i32>' if kind=='array' else ':String')}
    if lang=='cpp':params['cpp']=params['c']
    name=rust_method if lang=='rust' else method
    if lang in ('c','cpp'):
        ref=f'int {name}({params[lang]}) {{\n'+body+'\n}\n';starter=f'int {name}({params[lang]}) {{\n    return 0;\n}}\n'
        initmain='int ans='+name+'(read());' if kind=='scalar' else ('int a[20000];int n=read();for(int i=0;i<n;i++)a[i]=read();int ans='+name+'(a,n);' if kind=='array' else 'char s[20001];scanf("%20000s",s);if(strcmp(s,"-")==0)s[0]=0;int ans='+name+'(s);')
        driver=C_HELP+'\n__USER_CODE__\nint main(void){'+initmain+'emit(ans);return 0;}\n'
    elif lang=='java':
        ref='class Solution {\nstatic int mn(int a,int b){return Math.min(a,b);}\nstatic int mx(int a,int b){return Math.max(a,b);}\n'+f'public int {name}({params[lang]}) {{\n'+body+'\n}\n}\n'
        starter=f'class Solution {{\n    public int {name}({params[lang]}) {{\n        return 0;\n    }}\n}}\n'
        initmain='int ans=new Solution().'+name+'(read());' if kind=='scalar' else ('int n=read();int[] a=new int[n];for(int i=0;i<n;i++)a[i]=read();int ans=new Solution().'+name+'(a);' if kind=='array' else 'String s=tokens[cursor++];if(s.equals("-"))s="";int ans=new Solution().'+name+'(s);')
        driver=JAVA_HELP.replace('class Main {','__USER_CODE__\nclass Main {',1)+initmain+'emit(ans);}\n}\n'
    elif lang=='python':
        ref=f'class Solution:\n    def {name}(self,{arg}):\n'+'\n'.join('    '+x for x in body.splitlines())+'\n';starter=f'class Solution:\n    def {name}(self,{arg}):\n        return 0\n'
        initmain='ans=Solution().'+name+'(read())' if kind=='scalar' else ('n=read()\na=[read() for _ in range(n)]\nans=Solution().'+name+'(a)' if kind=='array' else 's=next(_tokens)\nif s=="-": s=""\nans=Solution().'+name+'(s)')
        driver=PY_HELP+'\n__USER_CODE__\n'+initmain+'\nemit(ans)\n'
    else:
        ref='impl Solution {\n'+f'pub fn {name}({params[lang]})->i32 {{\n'+body+'\n}\n}\n';starter='impl Solution {\n'+f'    pub fn {name}({params[lang]})->i32 {{\n        0\n    }}\n}}\n'
        initmain='let ans=Solution::'+name+'(reader.read());' if kind=='scalar' else ('let n=reader.read();let mut a=Vec::new();for _ in 0..n{a.push(reader.read());}let ans=Solution::'+name+'(a);' if kind=='array' else 'let s=reader.t[reader.p].clone();let s=if s=="-"{String::new()}else{s};let ans=Solution::'+name+'(s);')
        driver=RUST_HELP+'struct Solution;\n__USER_CODE__\nfn main(){let mut reader=Reader::new();'+initmain+'emit(ans);}\n'
    return ref,starter,driver

def extended_function_artifacts(item,lang):
    kind,method,rust_method,arg=item['_function']
    if kind=='tree':return tree_function_artifacts(lang)
    clone=dict(item)
    if kind=='scalarpair':
        clone['_code']='rows=read()\n'+item['_code'].split('\n',2)[2]
        clone['_function']=('scalar',method,rust_method,'rows')
        ref,starter,driver=function_artifacts(clone,lang)
        if lang in ('c','cpp'):ref=ref.replace('int rows)','int rows,int cols)');starter=starter.replace('int rows)','int rows,int cols)');driver=driver.replace('(read());','(read(),read());')
        elif lang=='java':ref=ref.replace('int rows)','int rows,int cols)');starter=starter.replace('int rows)','int rows,int cols)');driver=driver.replace('(read());','(read(),read());')
        elif lang=='python':ref=ref.replace('self,rows)','self,rows,cols)');starter=starter.replace('self,rows)','self,rows,cols)');driver=driver.replace('(read())','(read(),read())')
        else:ref=ref.replace('rows:i32)','rows:i32,cols:i32)');starter=starter.replace('rows:i32)','rows:i32,cols:i32)');driver=driver.replace('(reader.read());','(reader.read(),reader.read());')
        return ref,starter,driver
    clone['_code']=item['_code'].replace('t=read()\n','',1)
    clone['_function']=('array',method,rust_method,arg)
    ref,starter,driver=function_artifacts(clone,lang)
    if lang in ('c','cpp'):
        ref=ref.replace(f'int {arg}Size)',f'int {arg}Size,int target)').replace(' {\n',' {\n    int t=target;\n',1)
        starter=starter.replace(f'int {arg}Size)',f'int {arg}Size,int target)')
        driver=driver.replace('int n=read();','int n=read();int target=read();').replace('(a,n);','(a,n,target);')
    elif lang=='java':
        signature=f'{method}(int[] {arg})';ref=ref.replace(signature,f'{method}(int[] {arg},int target)').replace(' {\n    int[]', ' {\n    int t=target;\n    int[]',1)
        starter=starter.replace(signature,f'{method}(int[] {arg},int target)')
        driver=driver.replace('int n=read();','int n=read();int target=read();').replace('(a);','(a,target);')
    elif lang=='python':
        signature=f'self,{arg})';ref=ref.replace(signature,f'self,{arg},target)').replace(f'        a={arg}',f'        t=target\n        a={arg}',1)
        starter=starter.replace(signature,f'self,{arg},target)')
        driver=driver.replace('n=read()\n','n=read()\ntarget=read()\n').replace('(a)','(a,target)')
    else:
        ref=ref.replace(f'{arg}:Vec<i32>)',f'{arg}:Vec<i32>,target:i32)').replace('->i32 {\n','->i32 {\n    let t=target;\n',1)
        starter=starter.replace(f'{arg}:Vec<i32>)',f'{arg}:Vec<i32>,target:i32)')
        driver=driver.replace('let n=reader.read();','let n=reader.read();let target=reader.read();').replace('(a);','(a,target);')
    return ref,starter,driver

def tree_function_artifacts(lang):
    if lang in ('c','cpp'):
        node='typedef struct TreeNode {int val;struct TreeNode *left,*right;} TreeNode;\n'
        ref='''int bstCheck(TreeNode* root,int lo,int hi){if(!root)return 1;return root->val>lo && root->val<hi && bstCheck(root->left,lo,root->val) && bstCheck(root->right,root->val,hi);}
int isValidBST(TreeNode* root){return bstCheck(root,-1000000000,1000000000);}
''';starter='int isValidBST(TreeNode* root){return 0;}\n'
        driver=C_HELP+node+'__USER_CODE__\nint main(void){int n=read();TreeNode nodes[101];int left[101],right[101];for(int i=0;i<n;i++){nodes[i].val=read();left[i]=read();right[i]=read();}for(int i=0;i<n;i++){nodes[i].left=left[i]<0?NULL:&nodes[left[i]];nodes[i].right=right[i]<0?NULL:&nodes[right[i]];}emit(isValidBST(n?&nodes[0]:NULL));return 0;}\n'
    elif lang=='java':
        node='class TreeNode {int val;TreeNode left,right;TreeNode(int v){val=v;}}\n'
        ref='''class Solution {
boolean check(TreeNode node,long lo,long hi){return node==null || node.val>lo && node.val<hi && check(node.left,lo,node.val) && check(node.right,node.val,hi);}
public boolean isValidBST(TreeNode root){return check(root,Long.MIN_VALUE,Long.MAX_VALUE);}
}
''';starter='class Solution {public boolean isValidBST(TreeNode root){return false;}}\n'
        main='int n=read();TreeNode[] nodes=new TreeNode[n];int[] left=new int[n],right=new int[n];for(int i=0;i<n;i++){nodes[i]=new TreeNode(read());left[i]=read();right[i]=read();}for(int i=0;i<n;i++){nodes[i].left=left[i]<0?null:nodes[left[i]];nodes[i].right=right[i]<0?null:nodes[right[i]];}emit(new Solution().isValidBST(n==0?null:nodes[0])?1:0);'
        driver=JAVA_HELP.replace('class Main {',node+'__USER_CODE__\nclass Main {',1)+main+'}\n}\n'
    elif lang=='python':
        node='class TreeNode:\n    def __init__(self,val): self.val=val; self.left=None; self.right=None\n'
        ref='''class Solution:
    def isValidBST(self,root):
        def check(node,lo,hi):
            return node is None or lo<node.val<hi and check(node.left,lo,node.val) and check(node.right,node.val,hi)
        return check(root,float('-inf'),float('inf'))
''';starter='class Solution:\n    def isValidBST(self,root):\n        return False\n'
        driver=PY_HELP+node+'__USER_CODE__\nn=read()\nnodes=[]\nleft=[]\nright=[]\nfor i in range(n):\n    nodes.append(TreeNode(read()));left.append(read());right.append(read())\nfor i in range(n):\n    nodes[i].left=None if left[i]<0 else nodes[left[i]]\n    nodes[i].right=None if right[i]<0 else nodes[right[i]]\nemit(int(Solution().isValidBST(nodes[0] if n else None)))\n'
    else:
        node='''use std::rc::Rc;
use std::cell::RefCell;
#[derive(Debug)]
pub struct TreeNode {pub val:i32,pub left:Option<Rc<RefCell<TreeNode>>>,pub right:Option<Rc<RefCell<TreeNode>>>}
struct Solution;
'''
        ref='''impl Solution {
pub fn is_valid_bst(root:Option<Rc<RefCell<TreeNode>>>)->bool {
fn check(root:&Option<Rc<RefCell<TreeNode>>>,lo:i64,hi:i64)->bool {match root {None=>true,Some(node)=>{let node=node.borrow();let v=node.val as i64;v>lo && v<hi && check(&node.left,lo,v) && check(&node.right,v,hi)}}}
check(&root,i64::MIN,i64::MAX)
}
}
''';starter='impl Solution {pub fn is_valid_bst(root:Option<Rc<RefCell<TreeNode>>>)->bool {false}}\n'
        main='''let n=reader.read() as usize;let mut nodes=Vec::new();let mut left=Vec::new();let mut right=Vec::new();for _ in 0..n {nodes.push(Rc::new(RefCell::new(TreeNode{val:reader.read(),left:None,right:None})));left.push(reader.read());right.push(reader.read());}for i in 0..n{nodes[i].borrow_mut().left=if left[i]<0{None}else{Some(nodes[left[i] as usize].clone())};nodes[i].borrow_mut().right=if right[i]<0{None}else{Some(nodes[right[i] as usize].clone())};}let root=if n==0{None}else{Some(nodes[0].clone())};emit(if Solution::is_valid_bst(root){1}else{0});'''
        driver=RUST_HELP+node+'__USER_CODE__\nfn main(){let mut reader=Reader::new();'+main+'}\n'
    return ref,starter,driver


def cpp_leetcode_artifacts(item,ref,starter):
    kind,method,rust_method,arg=item['_function']
    if kind=='tree':
        ref=ref.replace('int isValidBST(', 'bool isValidBST(')
        starter=starter.replace('int isValidBST(', 'bool isValidBST(')
        node='typedef struct TreeNode {int val;struct TreeNode *left,*right;} TreeNode;\n'
        main='int n=read();TreeNode nodes[101];int left[101],right[101];for(int i=0;i<n;i++){nodes[i].val=read();left[i]=read();right[i]=read();}for(int i=0;i<n;i++){nodes[i].left=left[i]<0?NULL:&nodes[left[i]];nodes[i].right=right[i]<0?NULL:&nodes[right[i]];}emit(Solution().isValidBST(n?&nodes[0]:NULL));'
    else:
        node=''
        if kind in ('array','arrtarget'):
            old=f'int* {arg},int {arg}Size'+(',int target' if kind=='arrtarget' else '')
            new=f'vector<int>& {arg}'+(',int target' if kind=='arrtarget' else '')
            ref=ref.replace(old,new).replace(f'int *a={arg};',f'int *a={arg}.data();').replace(f'int n={arg}Size;',f'int n=(int){arg}.size();')
            starter=starter.replace(old,new)
            main='int n=read();'+('int target=read();' if kind=='arrtarget' else '')+'vector<int> a(n);for(int i=0;i<n;i++)a[i]=read();emit(Solution().'+method+'(a'+(',target' if kind=='arrtarget' else '')+'));'
        elif kind=='string':
            ref=ref.replace(f'char* {arg}',f'string {arg}').replace(f'int n=(int)strlen({arg});',f'int n=(int){arg}.size();')
            starter=starter.replace(f'char* {arg}',f'string {arg}')
            main='char buffer[20001];scanf("%20000s",buffer);string s=strcmp(buffer,"-")==0?string():string(buffer);emit(Solution().'+method+'(s));'
        elif kind=='scalarpair':main='int rows=read(),cols=read();emit(Solution().'+method+'(rows,cols));'
        else:main='int value=read();emit(Solution().'+method+'(value));'
    ref='class Solution {\npublic:\n'+ref+'};\n'
    starter='class Solution {\npublic:\n'+starter+'};\n'
    driver=C_HELP+'#include <vector>\n#include <string>\nusing namespace std;\n'+node+'__USER_CODE__\nint main(){'+main+'return 0;}\n'
    return ref,starter,driver


PILOTS=[
('luogu-p1001','A+B Problem','EASY',['输入输出'],'洛谷','P1001','https://www.luogu.com.cn/problem/P1001'),
('leetcode-1','两数之和','EASY',['数组','哈希'],'LeetCode','1','https://leetcode.com/problems/two-sum/'),
('leetcode-20','有效的括号','EASY',['字符串','栈'],'LeetCode','20','https://leetcode.com/problems/valid-parentheses/'),
('leetcode-704','二分查找','EASY',['二分查找'],'LeetCode','704','https://leetcode.com/problems/binary-search/'),
('leetcode-53','最大子数组和','MEDIUM',['动态规划'],'LeetCode','53','https://leetcode.com/problems/maximum-subarray/'),
('leetcode-206','反转链表','EASY',['链表'],'LeetCode','206','https://leetcode.com/problems/reverse-linked-list/')]

def build():
    manifest=[];validation=[]
    for item in ITEMS:
        if item['sourceId']=='11':item['_cases']=[s for s in item['_cases'] if toks(s)[0]>=2]
        pack={k:v for k,v in item.items() if not k.startswith('_')}
        pack.update(modes=['STDIO','FUNCTION'] if item['_function'] else ['STDIO'],defaultMode='FUNCTION' if item['_function'] else 'STDIO',checker='TOKENS',profiles={},references={},cases=[],testProvenance={'kind':'SELF_GENERATED','seed':SEED,'generator':'corpus/build_corpus.py','officialHiddenTests':False,'oracle':'Independent Python high-level/exhaustive oracle; small random cases plus hand-picked boundaries. Max-size cases use independent formulation where exhaustive enumeration is infeasible.','licensing':'Self-written educational adaptation; source links do not grant permission to republish original full statements.'})
        for language in ('c','cpp','java','python','rust'):
            pack['profiles'][language]={'starterStdio':STARTERS[language],'starterFunction':None,'functionDriver':None}
            pack['references'][language]={'STDIO':render_stdio(item['_code'],language)}
            if item['_function']:
                ref,starter,driver=function_artifacts(item,language)
                if language=='cpp':ref,starter,driver=cpp_leetcode_artifacts(item,ref,starter)
                pack['references'][language]['FUNCTION']=ref
                pack['profiles'][language].update(starterFunction=starter,functionDriver=driver)
                assert driver.count('__USER_CODE__')==1
        for i,inp in enumerate(item['_cases']):
            expected=item['_oracle'](inp)
            expected=expected if isinstance(expected,str) else emit_tokens(expected) if isinstance(expected,(list,tuple)) else str(expected)+'\n'
            pack['cases'].append({'name':('sample-' if i<2 else 'boundary-random-')+str(i+1),'input':inp,'expectedOutput':expected,'sample':i<2})
        path=ROOT.parent/(item['slug']+'.json');path.write_text(json.dumps(pack,ensure_ascii=False,indent=2)+'\n')
        manifest.append({k:pack[k] for k in ('slug','title','difficulty','tags','sourcePlatform','sourceUrl','sourceId','modes','defaultMode') }|{'file':path.name,'caseCount':len(pack['cases']),'status':'LOCAL_ONLY_PENDING_GO_JUDGE','functionExtension': 'COMPLETE' if item['_function'] else 'PLANNED'})
    for slug,title,diff,tags,platform,pid,url in PILOTS:manifest.append(dict(slug=slug,title=title,difficulty=diff,tags=tags,sourcePlatform=platform,sourceId=pid,sourceUrl=url,file=slug+'.json',status='PILOT_OWNED_BY_SEPARATE_AGENT'))
    manifest.sort(key=lambda p:({'EASY':0,'MEDIUM':1,'HARD':2}[p['difficulty']],p['sourcePlatform'],p['sourceId']))
    stats={k:sum(x['difficulty']==k for x in manifest) for k in ['EASY','MEDIUM','HARD']}
    platforms={k:sum(x['sourcePlatform']==k for x in manifest) for k in ['LeetCode','洛谷','原创']}
    assert len(ITEMS)==44 and len(manifest)==50,(len(ITEMS),len(manifest))
    assert stats=={'EASY':20,'MEDIUM':22,'HARD':8},stats
    assert platforms=={'LeetCode':40,'洛谷':5,'原创':5},platforms
    (ROOT.parent/'manifest.json').write_text(json.dumps({'version':1,'seed':SEED,'autoImport':False,'counts':{'total':50,'difficulty':stats,'platform':platforms},'items':manifest},ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'generated':len(ITEMS),'counts':stats,'sources':platforms,'dualMode':sum(bool(x['_function']) for x in ITEMS)},ensure_ascii=False))
if __name__=='__main__':build()
