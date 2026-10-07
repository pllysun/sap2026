"""Reproducible pilot packs. All hidden cases are locally generated, seed 20261001."""
import json,random,pathlib
ROOT=pathlib.Path(__file__).resolve().parent
rng=random.Random(20261001)
LANGS=['c','cpp','java','python','rust']
HEAD={
'c':'#include <stdio.h>\n#include <stdlib.h>\n#include <stdbool.h>\n#include <string.h>\n',
'cpp':'#include <iostream>\n#include <vector>\n#include <string>\n#include <unordered_map>\n#include <algorithm>\nusing namespace std;\n',
'java':'import java.util.*;\n',
'python':'import sys\n',
'rust':'use std::io::{self, Read};\nuse std::collections::HashMap;\nstruct Solution;\n'}
# Functions supplied by contestant, with exact public conventions.
FUN={
'1':{
'c':'''typedef struct {int v,i;} Pair;
int cmpPair(const void*a,const void*b){int x=((const Pair*)a)->v,y=((const Pair*)b)->v;return (x>y)-(x<y);}
/* Caller frees returned array; *returnSize must be 2. */
int* twoSum(int* nums,int numsSize,int target,int* returnSize){Pair*p=malloc(sizeof(Pair)*numsSize);for(int i=0;i<numsSize;i++)p[i]=(Pair){nums[i],i};qsort(p,numsSize,sizeof(Pair),cmpPair);int l=0,r=numsSize-1;while(l<r){long long s=(long long)p[l].v+p[r].v;if(s==target){int*out=malloc(2*sizeof(int));out[0]=p[l].i;out[1]=p[r].i;*returnSize=2;free(p);return out;}if(s<target)l++;else r--;}free(p);*returnSize=0;return NULL;}''',
'cpp':'class Solution { public: vector<int> twoSum(vector<int>& nums,int target){unordered_map<int,int> seen;for(int i=0;i<(int)nums.size();i++){auto it=seen.find(target-nums[i]);if(it!=seen.end())return {it->second,i};seen[nums[i]]=i;}return {};}};',
'java':'class Solution { public int[] twoSum(int[] nums,int target){Map<Integer,Integer> seen=new HashMap<>();for(int i=0;i<nums.length;i++){Integer j=seen.get(target-nums[i]);if(j!=null)return new int[]{j,i};seen.put(nums[i],i);}return new int[0];}}',
'python':'class Solution:\n    def twoSum(self, nums, target):\n        seen={}\n        for i,v in enumerate(nums):\n            if target-v in seen: return [seen[target-v],i]\n            seen[v]=i\n        return []\n',
'rust':'impl Solution { pub fn two_sum(nums: Vec<i32>,target:i32)->Vec<i32>{let mut seen=HashMap::new();for (i,&v) in nums.iter().enumerate(){if let Some(&j)=seen.get(&(target-v)){return vec![j,i as i32];}seen.insert(v,i as i32);}vec![]}}'},
'20':{
'c':'bool isValid(char*s){int n=strlen(s),k=0;char*st=malloc(n+1);for(int i=0;i<n;i++){char c=s[i];if(c==\'(\'||c==\'[\'||c==\'{\')st[k++]=c;else{char want=c==\')\'?\'(\':c==\']\'?\'[\':\'{\';if(!k||st[--k]!=want){free(st);return false;}}}free(st);return k==0;}',
'cpp':'class Solution {public: bool isValid(string s){string st;for(char c:s){if(c==\'(\'||c==\'[\'||c==\'{\')st+=c;else{char w=c==\')\'?\'(\':c==\']\'?\'[\':\'{\';if(st.empty()||st.back()!=w)return false;st.pop_back();}}return st.empty();}};',
'java':'class Solution {public boolean isValid(String s){char[] st=new char[s.length()];int k=0;for(char c:s.toCharArray()){if(c==\'(\'||c==\'[\'||c==\'{\')st[k++]=c;else{char w=c==\')\'?\'(\':c==\']\'?\'[\':\'{\';if(k==0||st[--k]!=w)return false;}}return k==0;}}',
'python':'class Solution:\n    def isValid(self,s):\n        st=[]\n        pairs={")":"(","]":"[","}":"{"}\n        for c in s:\n            if c in "([{": st.append(c)\n            elif not st or st.pop()!=pairs[c]: return False\n        return not st\n',
'rust':'impl Solution {pub fn is_valid(s:String)->bool{let mut st=Vec::new();for c in s.bytes(){match c{b\'(\'|b\'[\'|b\'{\'=>st.push(c),_=>{let w=match c{b\')\'=>b\'(\',b\']\'=>b\'[\',_=>b\'{\'};if st.pop()!=Some(w){return false;}}}}st.is_empty()}}'},
'704':{
'c':'int search(int*nums,int numsSize,int target){int l=0,r=numsSize-1;while(l<=r){int m=l+(r-l)/2;if(nums[m]==target)return m;if(nums[m]<target)l=m+1;else r=m-1;}return -1;}',
'cpp':'class Solution {public:int search(vector<int>&nums,int target){int l=0,r=(int)nums.size()-1;while(l<=r){int m=l+(r-l)/2;if(nums[m]==target)return m;if(nums[m]<target)l=m+1;else r=m-1;}return -1;}};',
'java':'class Solution {public int search(int[] nums,int target){int l=0,r=nums.length-1;while(l<=r){int m=l+(r-l)/2;if(nums[m]==target)return m;if(nums[m]<target)l=m+1;else r=m-1;}return -1;}}',
'python':'class Solution:\n    def search(self,nums,target):\n        l,r=0,len(nums)-1\n        while l<=r:\n            m=(l+r)//2\n            if nums[m]==target:return m\n            if nums[m]<target:l=m+1\n            else:r=m-1\n        return -1\n',
'rust':'impl Solution {pub fn search(nums:Vec<i32>,target:i32)->i32{match nums.binary_search(&target){Ok(i)=>i as i32,Err(_)=>-1}}}'},
'53':{
'c':'int maxSubArray(int*nums,int numsSize){int cur=nums[0],best=cur;for(int i=1;i<numsSize;i++){cur=cur>0?cur+nums[i]:nums[i];if(cur>best)best=cur;}return best;}',
'cpp':'class Solution {public:int maxSubArray(vector<int>&nums){int cur=nums[0],best=cur;for(int i=1;i<(int)nums.size();i++){cur=max(nums[i],cur+nums[i]);best=max(best,cur);}return best;}};',
'java':'class Solution {public int maxSubArray(int[] nums){int cur=nums[0],best=cur;for(int i=1;i<nums.length;i++){cur=Math.max(nums[i],cur+nums[i]);best=Math.max(best,cur);}return best;}}',
'python':'class Solution:\n    def maxSubArray(self,nums):\n        cur=best=nums[0]\n        for x in nums[1:]:\n            cur=max(x,cur+x)\n            best=max(best,cur)\n        return best\n',
'rust':'impl Solution {pub fn max_sub_array(nums:Vec<i32>)->i32{let mut cur=nums[0];let mut best=cur;for &x in &nums[1..]{cur=x.max(cur+x);best=best.max(cur);}best}}'},
'206':{
'c':'struct ListNode* reverseList(struct ListNode*head){struct ListNode*prev=NULL;while(head){struct ListNode*next=head->next;head->next=prev;prev=head;head=next;}return prev;}',
'cpp':'class Solution {public:ListNode* reverseList(ListNode*head){ListNode*prev=nullptr;while(head){auto next=head->next;head->next=prev;prev=head;head=next;}return prev;}};',
'java':'class Solution {public ListNode reverseList(ListNode head){ListNode prev=null;while(head!=null){ListNode next=head.next;head.next=prev;prev=head;head=next;}return prev;}}',
'python':'class Solution:\n    def reverseList(self,head):\n        prev=None\n        while head:\n            nxt=head.next\n            head.next=prev\n            prev,head=head,nxt\n        return prev\n',
'rust':'impl Solution {pub fn reverse_list(mut head:Option<Box<ListNode>>)->Option<Box<ListNode>>{let mut prev=None;while let Some(mut node)=head{head=node.next.take();node.next=prev;prev=Some(node);}prev}}'} }
NODE={
'c':'struct ListNode {int val;struct ListNode*next;};\n',
'cpp':'struct ListNode {int val;ListNode*next;ListNode(int x):val(x),next(nullptr){}};\n',
'java':'class ListNode {int val;ListNode next;ListNode(int x){val=x;}}\n',
'python':'class ListNode:\n    def __init__(self,val=0,next=None):self.val,self.next=val,next\n',
'rust':'#[derive(Debug)]\npub struct ListNode {pub val:i32,pub next:Option<Box<ListNode>>}\n'}
SIGN={
'1':{'c':'int* twoSum(int* nums,int numsSize,int target,int* returnSize){*returnSize=0;return NULL;}','cpp':'class Solution {public:vector<int> twoSum(vector<int>& nums,int target){return {};}};','java':'class Solution {public int[] twoSum(int[] nums,int target){return new int[0];}}','python':'class Solution:\n    def twoSum(self,nums,target):\n        return []\n','rust':'impl Solution {pub fn two_sum(nums:Vec<i32>,target:i32)->Vec<i32>{vec![]}}'},
'20':{'c':'bool isValid(char*s){return false;}','cpp':'class Solution {public:bool isValid(string s){return false;}};','java':'class Solution {public boolean isValid(String s){return false;}}','python':'class Solution:\n    def isValid(self,s):\n        return False\n','rust':'impl Solution {pub fn is_valid(s:String)->bool{false}}'},
'704':{'c':'int search(int*nums,int numsSize,int target){return -1;}','cpp':'class Solution {public:int search(vector<int>&nums,int target){return -1;}};','java':'class Solution {public int search(int[] nums,int target){return -1;}}','python':'class Solution:\n    def search(self,nums,target):\n        return -1\n','rust':'impl Solution {pub fn search(nums:Vec<i32>,target:i32)->i32{-1}}'},
'53':{'c':'int maxSubArray(int*nums,int numsSize){return 0;}','cpp':'class Solution {public:int maxSubArray(vector<int>&nums){return 0;}};','java':'class Solution {public int maxSubArray(int[] nums){return 0;}}','python':'class Solution:\n    def maxSubArray(self,nums):\n        return 0\n','rust':'impl Solution {pub fn max_sub_array(nums:Vec<i32>)->i32{0}}'},
'206':{'c':'struct ListNode* reverseList(struct ListNode*head){return head;}','cpp':'class Solution {public:ListNode* reverseList(ListNode*head){return head;}};','java':'class Solution {public ListNode reverseList(ListNode head){return head;}}','python':'class Solution:\n    def reverseList(self,head):\n        return head\n','rust':'impl Solution {pub fn reverse_list(head:Option<Box<ListNode>>)->Option<Box<ListNode>>{head}}'}}

def driver(pid,l):
    pre=HEAD[l]+(NODE[l] if pid=='206' else '')+'\n__USER_CODE__\n'
    if l=='c':
        if pid=='20':body='char s[10001];if(scanf("%10000s",s)!=1)return 2;printf("%s\\n",isValid(s)?"true":"false");'
        elif pid=='206':body='int n;scanf("%d",&n);struct ListNode *nodes=calloc(n?n:1,sizeof(*nodes));for(int i=0;i<n;i++){scanf("%d",&nodes[i].val);nodes[i].next=i+1<n?&nodes[i+1]:NULL;}struct ListNode*p=reverseList(n?nodes:NULL);int k=0;while(p&&k<n){if(k)printf(" ");printf("%d",p->val);p=p->next;k++;}if(p||k!=n)return 3;printf("\\n");free(nodes);'
        else:
            body='int n;scanf("%d",&n);int*a=malloc(n*sizeof(int));for(int i=0;i<n;i++)scanf("%d",&a[i]);'
            if pid in ['1','704']:body+='int target;scanf("%d",&target);'
            if pid=='1':body+='int count=0;int*r=twoSum(a,n,target,&count);if(!r||count!=2||r[0]<0||r[1]<0||r[0]>=n||r[1]>=n||r[0]==r[1])return 3;printf("%d %d\\n",r[0],r[1]);free(r);'
            else:body+='printf("%d\\n",'+('search(a,n,target)' if pid=='704' else 'maxSubArray(a,n)')+');'
            body+='free(a);'
        return pre+'int main(void){'+body+'return 0;}\n'
    if l=='cpp':
        if pid=='20':body='string s;cin>>s;cout<<(Solution().isValid(s)?"true":"false")<<"\\n";'
        elif pid=='206':body='int n;cin>>n;vector<ListNode*>nodes;ListNode*head=nullptr,*tail=nullptr;for(int i=0;i<n;i++){int v;cin>>v;auto p=new ListNode(v);nodes.push_back(p);if(tail)tail->next=p;else head=p;tail=p;}auto p=Solution().reverseList(head);int k=0;while(p&&k<n){if(k)cout<<" ";cout<<p->val;p=p->next;k++;}if(p||k!=n)return 3;cout<<"\\n";for(auto q:nodes)delete q;'
        else:
            body='int n;cin>>n;vector<int>a(n);for(auto&v:a)cin>>v;'
            if pid in ['1','704']:body+='int target;cin>>target;'
            if pid=='1':body+='auto r=Solution().twoSum(a,target);if(r.size()!=2||r[0]<0||r[1]<0||r[0]>=n||r[1]>=n||r[0]==r[1])return 3;cout<<r[0]<<" "<<r[1]<<"\\n";'
            else:body+='cout<<'+('Solution().search(a,target)' if pid=='704' else 'Solution().maxSubArray(a)')+'<<"\\n";'
        return pre+'int main(){ios::sync_with_stdio(false);cin.tie(nullptr);'+body+'return 0;}\n'
    if l=='java':
        # Fast scanner avoids Scanner startup/large-input overhead.
        scanner='static class Fast {private final byte[] b=new byte[65536];int p=0,n=0;int read()throws Exception{if(p>=n){n=System.in.read(b);p=0;if(n<0)return -1;}return b[p++];}String next()throws Exception{StringBuilder s=new StringBuilder();int c;do{c=read();}while(c<=32&&c>=0);while(c>32){s.append((char)c);c=read();}return s.toString();}int num()throws Exception{return Integer.parseInt(next());}}'
        if pid=='20':body='System.out.println(new Solution().isValid(f.next()));'
        elif pid=='206':body='int n=f.num();ListNode head=null,tail=null;for(int i=0;i<n;i++){ListNode p=new ListNode(f.num());if(tail==null)head=p;else tail.next=p;tail=p;}ListNode p=new Solution().reverseList(head);StringBuilder out=new StringBuilder();int k=0;while(p!=null&&k<n){if(k>0)out.append(" ");out.append(p.val);p=p.next;k++;}if(p!=null||k!=n)throw new IllegalStateException("invalid list length/cycle");System.out.println(out);'
        else:
            body='int n=f.num();int[]a=new int[n];for(int i=0;i<n;i++)a[i]=f.num();'
            if pid in ['1','704']:body+='int target=f.num();'
            if pid=='1':body+='int[]r=new Solution().twoSum(a,target);if(r==null||r.length!=2||r[0]<0||r[1]<0||r[0]>=n||r[1]>=n||r[0]==r[1])throw new IllegalStateException("invalid indices");System.out.println(r[0]+" "+r[1]);'
            else:body+='System.out.println('+('new Solution().search(a,target)' if pid=='704' else 'new Solution().maxSubArray(a)')+');'
        return pre+'public class Main {'+scanner+'public static void main(String[]args)throws Exception{Fast f=new Fast();'+body+'}}\n'
    if l=='python':
        body='data=sys.stdin.buffer.read().split()\n'
        if pid=='20':body+='print(str(Solution().isValid(data[0].decode())).lower())\n'
        elif pid=='206':body+='n=int(data[0]);head=None\nfor v in reversed(data[1:]):head=ListNode(int(v),head)\np=Solution().reverseList(head);out=[]\nwhile p is not None and len(out)<n:\n    out.append(str(p.val));p=p.next\nif p is not None or len(out)!=n:raise ValueError("invalid list length/cycle")\nprint(" ".join(out))\n'
        else:
            body+='n=int(data[0]);a=list(map(int,data[1:n+1]))\n'
            if pid in ['1','704']:body+='target=int(data[n+1])\n'
            if pid=='1':body+='r=Solution().twoSum(a,target)\nif len(r)!=2 or r[0]==r[1] or any(i<0 or i>=n for i in r):raise ValueError("invalid indices")\nprint(*r)\n'
            else:body+='print('+('Solution().search(a,target)' if pid=='704' else 'Solution().maxSubArray(a)')+')\n'
        return pre+body
    body='let mut s=String::new();io::stdin().read_to_string(&mut s).unwrap();let mut it=s.split_whitespace();'
    if pid=='20':body+='println!("{}",Solution::is_valid(it.next().unwrap().to_string()));'
    elif pid=='206':body+='let n:usize=it.next().unwrap().parse().unwrap();let vals:Vec<i32>=(0..n).map(|_|it.next().unwrap().parse().unwrap()).collect();let mut head=None;for &v in vals.iter().rev(){head=Some(Box::new(ListNode{val:v,next:head}));}let mut p=Solution::reverse_list(head);let mut out=Vec::new();while let Some(mut node)=p{assert!(out.len()<n,"invalid list length");out.push(node.val.to_string());p=node.next.take();}assert_eq!(out.len(),n);println!("{}",out.join(" "));'
    else:
        body+='let n:usize=it.next().unwrap().parse().unwrap();let a:Vec<i32>=(0..n).map(|_|it.next().unwrap().parse().unwrap()).collect();'
        if pid in ['1','704']:body+='let target:i32=it.next().unwrap().parse().unwrap();'
        if pid=='1':body+='let r=Solution::two_sum(a,target);assert!(r.len()==2&&r[0]!=r[1]&&r.iter().all(|&i|i>=0&&(i as usize)<n));println!("{} {}",r[0],r[1]);'
        else:body+='println!("{}",'+('Solution::search(a,target)' if pid=='704' else 'Solution::max_sub_array(a)')+');'
    return pre+'fn main(){'+body+'}\n'

META={
'1':('两数之和','two-sum','EASY',['数组','哈希表'],'找到数组中和为 target 的两个不同元素，返回它们的下标。保证恰好存在一组解，两个下标顺序不限。','2 ≤ n ≤ 10000；元素及 target 在 [-10^9,10^9]。'),
'20':('有效的括号','valid-parentheses','EASY',['字符串','栈'],'判断仅含圆括号、方括号和花括号的字符串是否正确嵌套且全部配对。输出 true 或 false。','1 ≤ 字符串长度 ≤ 10000。'),
'704':('二分查找','binary-search','EASY',['数组','二分查找'],'在严格递增数组中查找 target，存在则返回从零开始的下标，否则返回 -1。要求 O(log n) 时间。','1 ≤ n ≤ 10000；数组元素及 target 严格位于 (-10000,10000)。'),
'53':('最大子数组和','maximum-subarray','MEDIUM',['数组','动态规划'],'选择一个非空连续子数组，使其元素和最大，输出这个最大和。','1 ≤ n ≤ 100000；元素在 [-10000,10000]。'),
'206':('反转链表','reverse-linked-list','EASY',['链表'],'反转单向链表并返回新表头。标准输入以节点值序列表示链表，输出反转后序列。','0 ≤ 节点数 ≤ 5000；节点值在 [-5000,5000]。')}
CASES={k:[] for k in META}
def add(pid,a,target=None,sample=False,name=None):
    if pid=='20':
        inp=a+'\n';st=[];ok=True
        for ch in a:
            if ch in '([{':st.append(ch)
            elif not st or st.pop()!={')':'(',']':'[','}':'{'}[ch]:ok=False;break
        out=str(ok and not st).lower()
    else:
        inp=str(len(a))+'\n'+' '.join(map(str,a))+'\n'+(str(target)+'\n' if target is not None else '')
        if pid=='1':
            seen={};ans=[]
            for i,v in enumerate(a):
                if target-v in seen:ans.append((seen[target-v],i))
                seen[v]=i
            assert len(ans)==1,(len(a),target,ans[:4]);out=' '.join(map(str,ans[0]))
        elif pid=='704':out=str(a.index(target) if target in a else -1)
        elif pid=='206':out=' '.join(map(str,reversed(a)))
        else:
            # Small cases brute-force oracle, larger independent prefix/min-prefix oracle.
            if len(a)<=100:best=max(sum(a[i:j]) for i in range(len(a)) for j in range(i+1,len(a)+1))
            else:
                pref=low=0;best=-10**18
                for v in a:pref+=v;best=max(best,pref-low);low=min(low,pref)
            out=str(best)
    CASES[pid].append(dict(name=name or ('sample-' if sample else 'generated-')+str(len(CASES[pid])+1),input=inp,expectedOutput=out+'\n',sample=sample))
for a,t in [([2,7,11,15],9),([3,2,4],6),([3,3],6)]:add('1',a,t,True)
for a,t in [([-10**9,10**9],0),([0,0],0),([-3,1,5],2),([10**9,10**9-1,-1],10**9-2)]:add('1',a,t)
for z in range(22):
    # All non-answer values positive even, one negative odd with unique complement.
    n=rng.randint(2,120);a=rng.sample(range(2,20000,2),n-1);v=rng.choice(a);a.append(-v-1);rng.shuffle(a);add('1',a,-1)
a=list(range(2,20000,2))+[-19999];add('1',a,-1,name='max-n-unique-pair')
for s in ['()','()[]{}','(]','([])','([)]']:add('20',s,sample=True)
for s in ['(',')','(((((',']{}','{[()]}','(()','())','{'*5000+'}'*5000,'()'*5000,'('*5000+')'*4999+']'] :add('20',s)
for z in range(20):add('20',''.join(rng.choice('()[]{}') for _ in range(rng.randint(1,200))))
add('704',[-1,0,3,5,9,12],9,True);add('704',[-1,0,3,5,9,12],2,True)
for a,t in [([0],0),([0],1),([-9999],-9999),([9999],-9999),([-2,2],-2),([-2,2],2)]:add('704',a,t)
for z in range(22):
    a=sorted(rng.sample(range(-9999,10000),rng.randint(1,150)));add('704',a,rng.choice(a) if z%2 else rng.randint(-9999,9999))
a=list(range(-5000,5000));add('704',a,4999,name='max-n-last');add('704',a,9999,name='max-n-missing')
for a in [[-2,1,-3,4,-1,2,1,-5,4],[1],[5,4,-1,7,8]]:add('53',a,sample=True)
for a in [[-10000],[-9,-3,-7],[0,0],[10000],[-1,0,-1],[9,-20,8,9],[-4,9,-1],[9,-1,-10],[10000]*100000,[-10000]*100000]:add('53',a)
for z in range(22):add('53',[rng.randint(-10000,10000) for _ in range(rng.randint(1,60))])
for a in [[1,2,3,4,5],[1,2],[]]:add('206',a,sample=True)
for a in [[0],[-5000],[5000],[2,2,2],list(range(-2500,2500)),[5000]*5000]:add('206',a)
for z in range(23):add('206',[rng.randint(-5000,5000) for _ in range(rng.randint(0,150))])
for pid,(title,ename,diff,tags,desc,cons) in META.items():
    profiles={};refs={}
    for l in LANGS:
        d=driver(pid,l);full=d.replace('__USER_CODE__',FUN[pid][l]);starter=d.replace('__USER_CODE__',SIGN[pid][l])
        profiles[l]={'starterStdio':starter,'starterFunction':SIGN[pid][l],'functionDriver':d}
        refs[l]={'STDIO':full,'FUNCTION':FUN[pid][l]}
    inp='一行非空括号字符串。' if pid=='20' else '第一行 n，第二行 n 个整数'+('，第三行 target。' if pid in ['1','704'] else '；n=0 时第二行为空。' if pid=='206' else '。')
    output={'1':'两个不同的零基下标，以空格分隔，顺序不限。','20':'true 或 false。','704':'目标下标，未找到时为 -1。','53':'最大非空子数组和。','206':'反转后的节点值，以空格分隔；空链表输出空行。'}[pid]
    pack=dict(slug='leetcode-'+pid,title=title,difficulty=diff,tags=tags,description=desc,inputFormat=inp,outputFormat=output,constraints=cons,sourcePlatform='LeetCode',sourceUrl='https://leetcode.com/problems/'+ename+'/',sourceId=pid,sourceNote='题意自行简洁整理；标准输入格式为本平台适配。标记 sample 的用例来自公开示例，其余全部为固定种子 20261001 自建测试，不包含或声称包含官方隐藏数据。C 两数之和返回 malloc 分配数组，returnSize=2；链表节点定义由驱动提供。',modes=['FUNCTION'] if pid=='20' else ['STDIO','FUNCTION'],defaultMode='FUNCTION',checker='UNORDERED_TOKENS' if pid=='1' else 'TOKENS',profiles=profiles,cases=CASES[pid],references=refs)
    p=ROOT/pack['slug'];p.mkdir(exist_ok=True);(p/'pack.json').write_text(json.dumps(pack,ensure_ascii=False,indent=2)+'\n')
AB={
'c':'#include <stdio.h>\nint main(void){long long a,b;if(scanf("%lld%lld",&a,&b)!=2)return 2;printf("%lld\\n",a+b);return 0;}\n',
'cpp':'#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}\n',
'java':'import java.util.*;\npublic class Main {public static void main(String[]args){Scanner s=new Scanner(System.in);long a=s.nextLong(),b=s.nextLong();System.out.println(a+b);}}\n',
'python':'a,b=map(int,input().split())\nprint(a+b)\n',
'rust':'use std::io::{self,Read};\nfn main(){let mut s=String::new();io::stdin().read_to_string(&mut s).unwrap();let a:Vec<i64>=s.split_whitespace().map(|x|x.parse().unwrap()).collect();println!("{}",a[0]+a[1]);}\n'}
abcases=[(1,2),(0,0),(-10**9,-10**9),(10**9,10**9),(-10**9,10**9),(-1,0),(0,-1),(999999999,1),(-999999999,-1),(123,-456)]+[(rng.randint(-10**9,10**9),rng.randint(-10**9,10**9)) for _ in range(15)]
pack=dict(slug='luogu-p1001',title='A+B Problem',difficulty='EASY',tags=['输入输出','入门'],description='读取两个整数，输出它们的和。',inputFormat='一行两个整数 a、b，以空白分隔。',outputFormat='输出 a+b。',constraints='-10^9 ≤ a,b ≤ 10^9。',sourcePlatform='洛谷',sourceUrl='https://www.luogu.com.cn/problem/P1001',sourceId='P1001',sourceNote='题意自行整理；测试集全部自建，覆盖正负数、零与上下界，不包含官方隐藏数据。',modes=['STDIO'],defaultMode='STDIO',checker='TOKENS',profiles={l:dict(starterStdio=HEAD[l]+({'c':'int main(void){return 0;}','cpp':'int main(){return 0;}','java':'public class Main {public static void main(String[]args){}}','python':'# 读取输入并输出答案\n','rust':'fn main(){}'}[l]),starterFunction='',functionDriver='') for l in LANGS},cases=[dict(name='generated-'+str(i+1),input=f'{a} {b}\n',expectedOutput=f'{a+b}\n',sample=i<2) for i,(a,b) in enumerate(abcases)],references={l:{'STDIO':AB[l]} for l in LANGS})
p=ROOT/pack['slug'];p.mkdir(exist_ok=True);(p/'pack.json').write_text(json.dumps(pack,ensure_ascii=False,indent=2)+'\n')
print('Generated',[(p.parent.name,len(json.loads(p.read_text())['cases'])) for p in sorted(ROOT.glob('*/pack.json')) if p.parent.name in ['luogu-p1001']+['leetcode-'+k for k in META]])
