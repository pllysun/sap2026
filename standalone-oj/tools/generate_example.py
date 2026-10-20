#!/usr/bin/env python3
"""Reproducible original arithmetic example. No untrusted solution runs on the host."""
import argparse,json,random,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CODES={
'c':r'''#include <stdio.h>
int main(void) {
    // 使用 long long，统一承载读入的整数及其和。
    long long a, b;
    if (scanf("%lld %lld", &a, &b) != 2) return 0;
    // 两个有符号整数直接相加，负数和零无需特殊判断。
    long long sum = a + b;
    // 输出整数并换行，不附加说明文字。
    printf("%lld\n", sum);
    return 0;
}
''',
'cpp':r'''#include <iostream>
int main() {
    // 用 long long 读取整数，预留足够的取值范围。
    long long a, b;
    if (!(std::cin >> a >> b)) return 0;
    // 加法对正数、负数和零都遵循同一条规则。
    long long sum = a + b;
    // 按题目协议只输出答案和一个换行。
    std::cout << sum << '\n';
    return 0;
}
''',
'java':r'''import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        // Scanner 按空白分隔读取输入，换行和空格都可作为分隔符。
        Scanner input = new Scanner(System.in);
        long a = input.nextLong();
        long b = input.nextLong();
        // long 可承载输入值及它们的和，避免类型范围造成误判。
        long sum = a + b;
        // 标准输出中只放答案，println 自动追加换行。
        System.out.println(sum);
    }
}
''',
'python':r'''import sys
# 按空白切分标准输入，所以末尾换行不会影响读取。
values = sys.stdin.buffer.read().split()
a, b = map(int, values)
# Python 整数支持负数；这里直接计算两个整数的和。
sum_value = a + b
# print 自动换行，不输出提示信息。
print(sum_value)
''',
'rust':r'''use std::io::{self, Read};
fn main() {
    // 一次读取输入，并按空白获取两个整数。
    let mut input = String::new();
    io::stdin().read_to_string(&mut input).unwrap();
    let mut values = input.split_whitespace();
    let a: i64 = values.next().unwrap().parse().unwrap();
    let b: i64 = values.next().unwrap().parse().unwrap();
    // i64 保留正负号和足够的取值范围。
    let sum = a + b;
    // 只输出结果，println! 自动追加换行。
    println!("{}", sum);
}
'''
}
STARTERS={
'c':r'''#include <stdio.h>
int main(void) {
    long long a, b;
    scanf("%lld %lld", &a, &b);
    // TODO：计算两个整数的和并输出。
    return 0;
}
''',
'cpp':r'''#include <iostream>
int main() {
    long long a, b;
    std::cin >> a >> b;
    // TODO：计算两个整数的和并输出。
    return 0;
}
''',
'java':r'''import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        long a = input.nextLong(), b = input.nextLong();
        // TODO：计算两个整数的和并输出。
    }
}
''',
'python':'''import sys\na, b = map(int, sys.stdin.buffer.read().split())\n# TODO：计算两个整数的和并输出。\n''',
'rust':r'''use std::io::{self, Read};
fn main() {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input).unwrap();
    let mut values = input.split_whitespace();
    let a: i64 = values.next().unwrap().parse().unwrap();
    let b: i64 = values.next().unwrap().parse().unwrap();
    // TODO：计算两个整数的和并输出。
}
'''
}
SECTIONS=[
('understand','读懂题目','输入给出两个整数 `a` 和 `b`，任务是输出它们的和。整数包括正整数、负整数和零，例如 `-3` 也属于合法输入。输入中的空格和换行是分隔信息的方式，不属于需要计算的内容。输出只需要一个整数，不要附加“答案是”等说明文字。例如输入 `3 5` 时，两个数相加得到 `8`，因此输出 `8`。'),
('approach','解法思路','先读取输入中的第一个整数，再读取第二个整数，将它们保存到两个变量中。变量可以理解为带名字的存储位置，后面的计算通过名字访问其中的值。随后使用加法运算符 `+` 计算两者的和，保存为 `sum`，最后输出。无论输入是正数、负数还是零，步骤都相同，所以不需要分情况讨论，也不需要循环遍历。'),
('walkthrough','样例推演','以输入 `3 5` 为例，按顺序执行下面三步：\n\n| 步骤 | 操作 | 当前结果 |\n| --- | --- | --- |\n| 1 | 读取两个整数 | `a = 3`，`b = 5` |\n| 2 | 计算 `a + b` | `sum = 8` |\n| 3 | 输出 `sum` | 标准输出为 `8` |\n\n如果输入 `-7 4`，第二步得到 `-3`，其他步骤不发生变化。'),
('steps','实现步骤','1. 从标准输入读取两个整数。C 使用 `scanf`，C++ 使用 `cin`，Java 使用 `Scanner`，Python 读取后按空白切分，Rust 通过 `split_whitespace` 获得各个数。\n2. 将两个输入值保存到 `a` 和 `b`。C/C++ 选用 `long long`，Java 选用 `long`，Rust 选用 `i64`，让输入和答案都处于类型范围内。\n3. 计算 `a + b`，把结果保存在答案变量中。\n4. 输出答案并换行；所有调试信息都应在提交前移除。'),
('correctness','为什么正确','第一步把题目给出的两个整数准确读入，因此 `a` 和 `b` 分别代表需要参与运算的两个值。第二步使用整数加法求出这两个值的和，与题目要求的运算完全一致。最后输出保存的结果，没有修改它，也没有增加无关信息。因此程序输出的整数正是题目所要求的答案。'),
('complexity','复杂度','题目始终只有两个整数，输入值的个数不会随数据规模增长。程序执行固定次数的读入、加法和输出，算法的时间复杂度为 `O(1)`，额外空间复杂度为 `O(1)`。这里的大 O 用来说明运算量随输入规模如何变化，而不是某台计算机的具体耗时。Python 和 Rust 的示例会暂存输入文本；在这道题固定格式和固定数值范围下，输入长度有界。'),
('pitfalls','边界与常见错误','- 只输出结果，不要输出交互式提示，例如“请输入两个数”。判题机会自动把输入交给程序。\n- 不要将数字当成字符串拼接。输入 `3 5` 的答案是 `8`，不是 `35`。\n- 输入允许负数和零，不能用只适用于正数的读取或判断方式。\n- 类型范围需要覆盖输入和答案。使用这里的 64 位整数类型，可以避免以后扩大输入范围时出现溢出。\n- Java 完整程序的公开类名必须为 `Main`，这与平台编译入口相匹配。'),
('review','复盘练习','这道题的核心流程是读取输入、计算结果、按约定输出。掌握它以后，可以尝试将中间的加法换成其他合法运算，但仍要注意输出格式和数值范围。\n\n1. 输入 `0 -9` 应输出什么？答案是 `-9`。\n2. 输入 `1000000000 1000000000` 应输出什么？答案是 `2000000000`。先手算，再比较程序输出。')
]

def generate(output:Path,slug:str,seed:int):
    if not re.fullmatch('[a-z0-9][a-z0-9-]{0,99}',slug):raise ValueError('slug 格式无效')
    output.mkdir(parents=True,exist_ok=True)
    if any((output/n).exists() for n in ('pack.json','solution.json','coverage.json')):raise ValueError('示例文件已存在，拒绝覆盖；选择另一个输出目录')
    rng=random.Random(seed)
    tuples=[(3,5),(-7,4),(0,0),(0,-9),(-1,-1),(-10,10),(10,-10),(-10**9,-10**9),(10**9,10**9),(-10**9,10**9),(10**9,-10**9)]
    tuples.extend((rng.randint(-10**9,10**9),rng.randint(-10**9,10**9)) for _ in range(30))
    cases=[{'name':f'case-{i+1:02d}','input':f'{a} {b}\n','expectedOutput':f'{a+b}\n','sample':i<2} for i,(a,b) in enumerate(tuples)]
    pack={'slug':slug,'title':'两个整数的和','difficulty':'EASY','tags':['输入输出','基础数学'],'description':'给定两个整数 a 和 b，计算并输出它们的和。','inputFormat':'输入一行，包含两个用空白分隔的整数 a、b。','outputFormat':'输出一个整数，表示 a 与 b 的和。','constraints':'-1000000000 ≤ a, b ≤ 1000000000。','sourcePlatform':'原创','sourceUrl':'','sourceId':'','sourceNote':'软件协会原创','modes':['STDIO'],'defaultMode':'STDIO','checker':'TOKENS','profiles':{k:{'starterStdio':STARTERS[k],'starterFunction':'','functionDriver':''} for k in CODES},'cases':cases,'references':{k:{'STDIO':v} for k,v in CODES.items()}}
    solution={'schemaVersion':1,'problemSlug':slug,'problemRevision':1,'title':pack['title'],'summary':'读取两个整数，用加法计算它们的和，并按约定格式输出。','sections':[{'key':key,'title':title,'markdown':text} for key,title,text in SECTIONS],'codes':{'STDIO':CODES}}
    coverage={'seed':seed,'oracle':'Python 整数加法，仅生成可信数据；参考代码由服务端五语言沙箱执行验证','samples':2,'cases':len(cases),'categories':['公开样例','全零','单零','负数','相反数','上下界','固定种子随机'],'mutantsToCheck':['将加法写成减法','忽略负号','只读第一个整数','始终输出零'],'note':'覆盖矩阵单独保存，不向题包添加未知字段'}
    for file,data in [('pack.json',pack),('solution.json',solution),('coverage.json',coverage)]:
        (output/file).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print('已生成原创示例、五语言代码、41 个测试用例、题解和覆盖说明：'+str(output))

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=ROOT/'examples/original-sum');p.add_argument('--slug',default='original-sum-demo');p.add_argument('--seed',type=int,default=20261013);args=p.parse_args();generate(args.output,args.slug,args.seed)
if __name__=='__main__':main()
