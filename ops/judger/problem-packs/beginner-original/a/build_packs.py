#!/usr/bin/env python3
"""Build ten independently authored, beginner ACM packs and deterministic cases."""
from __future__ import annotations

import datetime as dt
from fractions import Fraction
import itertools
import json
from pathlib import Path
import random
import textwrap

ROOT = Path(__file__).resolve().parent
LANGUAGES = ("c", "cpp", "java", "python", "rust")


def source(text):
    return textwrap.dedent(text).strip() + "\n"


STARTERS = {
    "c": source('''
        #include <stdio.h>

        int main(void) {
            // 读取输入并输出结果。
            return 0;
        }
    '''),
    "cpp": source('''
        #include <iostream>

        int main() {
            std::ios::sync_with_stdio(false);
            std::cin.tie(nullptr);

            // 读取输入并输出结果。
            return 0;
        }
    '''),
    "java": source('''
        import java.util.Scanner;

        public class Main {
            public static void main(String[] args) {
                Scanner scanner = new Scanner(System.in);

                // 读取输入并输出结果。
            }
        }
    '''),
    "python": source('''
        import sys


        def main():
            # 读取输入并输出结果。
            pass


        if __name__ == "__main__":
            main()
    '''),
    "rust": source('''
        use std::io::{self, Read};

        fn main() {
            let mut input = String::new();
            io::stdin().read_to_string(&mut input).unwrap();

            // 读取输入并输出结果。
        }
    '''),
}


def c(body):
    return "#include <stdio.h>\n\nint main(void) {\n" + textwrap.indent(source(body), "    ") + "    return 0;\n}\n"


def cpp(body):
    return "#include <iostream>\n#include <string>\n\nint main() {\n    std::ios::sync_with_stdio(false);\n    std::cin.tie(nullptr);\n\n" + textwrap.indent(source(body), "    ") + "    return 0;\n}\n"


def java(body):
    return "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n\n" + textwrap.indent(source(body), "        ") + "    }\n}\n"


def python(body):
    return source(body)


def rust(body):
    return "use std::io::{self, Read};\n\nfn main() {\n    let mut input = String::new();\n    io::stdin().read_to_string(&mut input).unwrap();\n    let mut words = input.split_whitespace();\n\n" + textwrap.indent(source(body), "    ") + "}\n"


def solutions(c_body, cpp_body, java_body, python_body, rust_body):
    return dict(zip(LANGUAGES, (c(c_body), cpp(cpp_body), java(java_body), python(python_body), rust(rust_body))))


def clock_oracle(values):
    h, m, delay = values
    assert 0 <= h <= 23 and 0 <= m <= 59 and 0 <= delay <= 10**9
    start = dt.datetime(2000, 1, 1, h, m)
    end = start + dt.timedelta(minutes=delay)
    days = (end.date() - start.date()).days
    return f"{days} {end.hour} {end.minute}\n"


def bill_oracle(values):
    (n,) = values
    assert 0 <= n <= 10**9
    # Count the two disjoint sets of chargeable minute numbers.
    expensive = len(range(16, min(60, n) + 1))
    cheap = len(range(61, n + 1))
    return f"{2 * expensive + cheap}\n"


def divide_oracle(values):
    n, m = values
    assert 1 <= n <= 10**6 and 0 <= m <= 10**12
    each, leftover = divmod(m, n)
    assert each * n + leftover == m and 0 <= leftover < n
    return f"{each} {leftover}\n"


def staircase_oracle(values):
    (n,) = values
    assert 1 <= n <= 10**9
    # Pair symmetric layer widths; also enumerate every small test independently.
    result = (n // 2) * (n + 1) + (n % 2) * ((n + 1) // 2)
    if n <= 10000:
        assert result == sum(range(1, n + 1))
    return f"{result}\n"


def checksum_oracle(values):
    (digits,) = values
    assert 1 <= len(digits) <= 100 and digits.isascii() and digits.isdigit()
    weighted = sum(int(d) * weight for d, weight in zip(digits, itertools.cycle((1, 3))))
    candidates = [check for check in range(10) if (weighted + check) % 10 == 0]
    assert len(candidates) == 1
    return f"{candidates[0]}\n"


def multiples_oracle(values):
    left, right, step = values
    assert 0 <= left <= right <= 10**12 and 1 <= step <= 10**9
    first = -(-left // step) * step
    result = len(range(first, right + 1, step))
    if right - left <= 5000:
        assert result == sum(value % step == 0 for value in range(left, right + 1))
    return f"{result}\n"


def border_oracle(values):
    rows, columns = values
    assert 1 <= rows <= 10**9 and 1 <= columns <= 10**9
    # Form the union of four boundary lines, rather than mirroring the reference branch.
    result = rows * columns - max(rows - 2, 0) * max(columns - 2, 0)
    if rows <= 50 and columns <= 50:
        cells = {(r, col) for r in range(rows) for col in range(columns) if r in (0, rows - 1) or col in (0, columns - 1)}
        assert result == len(cells)
    return f"{result}\n"


def date_oracle(values):
    year, month, day = values
    assert 1 <= year <= 9998
    current = dt.date(year, month, day)
    following = current + dt.timedelta(days=1)
    return f"{following.year} {following.month} {following.day}\n"


def recurrence_oracle(values):
    index, a, b, modulus = values
    assert 0 <= index <= 10**6 and 0 <= a <= 10**9 and 0 <= b <= 10**9 and 1 <= modulus <= 10**9 + 7
    if index == 0:
        return f"{a % modulus}\n"

    def multiply(x, y):
        return tuple(sum(x[row * 2 + k] * y[k * 2 + col] for k in range(2)) % modulus for row in range(2) for col in range(2))

    power, base, exponent = (1, 0, 0, 1), (1, 1, 1, 0), index - 1
    while exponent:
        if exponent & 1:
            power = multiply(power, base)
        base = multiply(base, base)
        exponent //= 2
    result = (power[0] * b + power[1] * a) % modulus
    if index <= 1000:
        previous, current = a % modulus, b % modulus
        for _ in range(1, index):
            previous, current = current, (previous + current) % modulus
        assert result == current
    return f"{result}\n"


def fraction_oracle(values):
    numerator, denominator = values
    assert -10**12 <= numerator <= 10**12 and -10**12 <= denominator <= 10**12 and denominator != 0
    value = Fraction(numerator, denominator)
    return f"{value.numerator} {value.denominator}\n"


def pack(number, title, tags, description, input_format, output_format, constraints, oracle, samples, boundaries, random_values, reference):
    cases = []
    for name, values, sample in [(f"sample-{i}", value, True) for i, value in enumerate(samples, 1)] + [(name, value, False) for name, value in boundaries] + [(f"random-{i:02}", value, False) for i, value in enumerate(random_values, 1)]:
        cases.append({"name": name, "input": " ".join(map(str, values)) + "\n", "expectedOutput": oracle(values), "sample": sample})
    assert len(cases) >= 20
    slug = f"original-acm-{number:03}"
    return {
        "slug": slug, "title": title, "difficulty": "EASY", "tags": tags,
        "description": description, "inputFormat": input_format, "outputFormat": output_format, "constraints": constraints,
        "sourcePlatform": "原创", "sourceUrl": "https://csuftsap.top/oj", "sourceId": f"ORIGINAL-ACM-{number:03}",
        "sourceNote": "软件协会原创",
        "modes": ["STDIO"], "defaultMode": "STDIO", "checker": "TOKENS",
        "profiles": {language: {"starterStdio": STARTERS[language], "starterFunction": "", "functionDriver": ""} for language in LANGUAGES},
        "cases": cases, "references": {language: {"STDIO": reference[language]} for language in LANGUAGES},
    }


def build():
    rng = random.Random(2026100101)
    packs = []
    packs.append(pack(1, "延时提醒", ["输入输出", "基础数学"],
        "实验室的提醒器显示当前时刻 h 时 m 分。设定延迟 d 分钟后，它会显示新的时刻，同时记录经过了多少次午夜。一天固定为24小时。请计算经过的天数和新的时、分。经过的天数从0开始计数，当前日期无须输入。",
        "一行三个整数 h、m、d，以空格分隔。", "输出三个整数：经过的天数、新的小时、新的分钟，以空格分隔。小时和分钟无须补前导零。",
        "0 ≤ h ≤ 23，0 ≤ m ≤ 59，0 ≤ d ≤ 10^9。", clock_oracle,
        [(10, 20, 45), (23, 50, 20), (8, 0, 2880)],
        [("zero-delay", (0, 0, 0)), ("before-midnight", (23, 58, 1)), ("exact-midnight", (23, 59, 1)), ("full-day", (0, 0, 1440)), ("max-delay", (23, 59, 10**9)), ("minute-carry", (7, 59, 1)), ("many-days", (12, 34, 999999999))],
        [(rng.randrange(24), rng.randrange(60), rng.randrange(10**9 + 1)) for _ in range(10)],
        solutions('''
            long long h, m, d;
            scanf("%lld%lld%lld", &h, &m, &d);
            long long total = h * 60 + m + d;
            printf("%lld %lld %lld\\n", total / 1440, total % 1440 / 60, total % 60);
        ''', '''
            long long h, m, d;
            std::cin >> h >> m >> d;
            long long total = h * 60 + m + d;
            std::cout << total / 1440 << ' ' << total % 1440 / 60 << ' ' << total % 60 << '\\n';
        ''', '''
            long h = in.nextLong();
            long m = in.nextLong();
            long d = in.nextLong();
            long total = h * 60 + m + d;
            System.out.println(total / 1440 + " " + total % 1440 / 60 + " " + total % 60);
        ''', '''
            h, m, d = map(int, input().split())
            total = h * 60 + m + d
            print(total // 1440, total % 1440 // 60, total % 60)
        ''', '''
            let h: i64 = words.next().unwrap().parse().unwrap();
            let m: i64 = words.next().unwrap().parse().unwrap();
            let d: i64 = words.next().unwrap().parse().unwrap();
            let total = h * 60 + m + d;
            println!("{} {} {}", total / 1440, total % 1440 / 60, total % 60);
        ''')))

    packs.append(pack(2, "终端使用积分", ["条件判断", "基础数学"],
        "实验室按整分钟记录公共终端的一次使用时长 n。前15分钟免费；第16到第60分钟，每分钟扣除2积分；第61分钟起，每分钟扣除1积分。三个区间分别计费，而不是按最终时长对所有分钟统一收费。请计算本次扣除的总积分。",
        "一行一个整数 n，表示使用的整分钟数。", "输出一个整数，表示扣除的总积分。", "0 ≤ n ≤ 10^9。", bill_oracle,
        [(10,), (20,), (70,)],
        [("unused", (0,)), ("free-limit", (15,)), ("first-charge", (16,)), ("before-second-limit", (59,)), ("second-limit", (60,)), ("third-start", (61,)), ("max-duration", (10**9,))],
        [(rng.randrange(10**9 + 1),) for _ in range(10)],
        solutions('''
            long long n, total = 0;
            scanf("%lld", &n);
            if (n > 15) {
                long long end = n < 60 ? n : 60;
                total += (end - 15) * 2;
            }
            if (n > 60) {
                total += n - 60;
            }
            printf("%lld\\n", total);
        ''', '''
            long long n, total = 0;
            std::cin >> n;
            if (n > 15) {
                long long end = n < 60 ? n : 60;
                total += (end - 15) * 2;
            }
            if (n > 60) {
                total += n - 60;
            }
            std::cout << total << '\\n';
        ''', '''
            long n = in.nextLong();
            long total = 0;
            if (n > 15) {
                total += (Math.min(n, 60) - 15) * 2;
            }
            if (n > 60) {
                total += n - 60;
            }
            System.out.println(total);
        ''', '''
            n = int(input())
            total = max(0, min(n, 60) - 15) * 2 + max(0, n - 60)
            print(total)
        ''', '''
            let n: i64 = words.next().unwrap().parse().unwrap();
            let total = (n.min(60) - 15).max(0) * 2 + (n - 60).max(0);
            println!("{}", total);
        ''')))

    packs.append(pack(3, "贴纸等量分装", ["输入输出", "基础数学"],
        "活动组准备了 m 张贴纸，要装入 n 个袋子。每个袋子必须装相同数量，并尽量装满；无法等量分装的贴纸留在桌上。请计算每个袋子的贴纸数和留在桌上的数量。允许袋子为空。",
        "一行两个整数 n、m，分别表示袋子数和贴纸总数。", "输出两个整数，依次表示每个袋子的贴纸数、剩余贴纸数。",
        "1 ≤ n ≤ 10^6，0 ≤ m ≤ 10^12。", divide_oracle,
        [(4, 19), (5, 3), (1, 25)],
        [("no-stickers", (1, 0)), ("zero-large-bags", (10**6, 0)), ("exact-division", (7, 49)), ("one-short", (100, 99)), ("one-extra", (100, 101)), ("large-total", (1, 10**12)), ("large-both", (999983, 10**12))],
        [(rng.randrange(1, 10**6 + 1), rng.randrange(10**12 + 1)) for _ in range(10)],
        solutions('''
            long long n, m;
            scanf("%lld%lld", &n, &m);
            printf("%lld %lld\\n", m / n, m % n);
        ''', '''
            long long n, m;
            std::cin >> n >> m;
            std::cout << m / n << ' ' << m % n << '\\n';
        ''', '''
            long n = in.nextLong();
            long m = in.nextLong();
            System.out.println(m / n + " " + m % n);
        ''', '''
            n, m = map(int, input().split())
            print(m // n, m % n)
        ''', '''
            let n: i64 = words.next().unwrap().parse().unwrap();
            let m: i64 = words.next().unwrap().parse().unwrap();
            println!("{} {}", m / n, m % n);
        ''')))

    packs.append(pack(4, "展示阶台", ["基础数学", "等差数列"],
        "社团用方块搭建一个有 n 层的展示阶台：第一层放1块，第二层放2块，依此类推，第 i 层恰好放 i 块。方块只按每层的规定计数，不额外添加支撑块。请计算需要的方块总数。",
        "一行一个整数 n，表示层数。", "输出一个整数，表示方块总数。", "1 ≤ n ≤ 10^9。答案可能超过32位整数范围。", staircase_oracle,
        [(1,), (4,), (10,)],
        [("two-levels", (2,)), ("odd-levels", (3,)), ("int32-edge", (65535,)), ("above-int32", (65536,)), ("large-even", (10**9,)), ("large-odd", (999999999,)), ("thousand", (1000,))],
        [(rng.randrange(1, 10**9 + 1),) for _ in range(10)],
        solutions('''
            long long n;
            scanf("%lld", &n);
            printf("%lld\\n", n * (n + 1) / 2);
        ''', '''
            long long n;
            std::cin >> n;
            std::cout << n * (n + 1) / 2 << '\\n';
        ''', '''
            long n = in.nextLong();
            System.out.println(n * (n + 1) / 2);
        ''', '''
            n = int(input())
            print(n * (n + 1) // 2)
        ''', '''
            let n: i64 = words.next().unwrap().parse().unwrap();
            println!("{}", n * (n + 1) / 2);
        ''')))

    packs.append(pack(5, "领取码校验位", ["字符串", "基础数学"],
        "活动领取码由一串十进制数字组成，前导零也是领取码的一部分。从左到右给数字的位置编号为1、2、3……：奇数位置的数字乘1，偶数位置的数字乘3，把这些乘积相加得到 S。需要选一个0到9之间的校验位 c，使 S+c 能被10整除。请输出唯一的 c。校验位自身不再按位置乘权重。",
        "一行一个只包含字符0到9的字符串，表示领取码。", "输出一个0到9的整数，表示校验位。",
        "领取码长度为1到100，可以全部为零或包含前导零。", checksum_oracle,
        [("1234",), ("007",), ("0",)],
        [("one-nine", ("9",)), ("leading-zero", ("01",)), ("all-zero-max", ("0" * 100,)), ("all-nine-max", ("9" * 100,)), ("odd-length", ("12345",)), ("alternating-max", ("09" * 50,)), ("already-divisible", ("13",))],
        [("".join(str(rng.randrange(10)) for _ in range(rng.randrange(1, 101))),) for _ in range(10)],
        solutions('''
            char digits[101];
            scanf("%100s", digits);
            int sum = 0;
            for (int i = 0; digits[i] != '\\0'; i++) {
                int weight = i % 2 == 0 ? 1 : 3;
                sum += (digits[i] - '0') * weight;
            }
            printf("%d\\n", (10 - sum % 10) % 10);
        ''', '''
            std::string digits;
            std::cin >> digits;
            int sum = 0;
            for (std::size_t i = 0; i < digits.size(); i++) {
                int weight = i % 2 == 0 ? 1 : 3;
                sum += (digits[i] - '0') * weight;
            }
            std::cout << (10 - sum % 10) % 10 << '\\n';
        ''', '''
            String digits = in.next();
            int sum = 0;
            for (int i = 0; i < digits.length(); i++) {
                int weight = i % 2 == 0 ? 1 : 3;
                sum += (digits.charAt(i) - '0') * weight;
            }
            System.out.println((10 - sum % 10) % 10);
        ''', '''
            digits = input().strip()
            total = 0
            for index, digit in enumerate(digits):
                total += int(digit) * (1 if index % 2 == 0 else 3)
            print((10 - total % 10) % 10)
        ''', '''
            let digits = words.next().unwrap();
            let mut sum: i32 = 0;
            for (index, digit) in digits.bytes().enumerate() {
                let weight = if index % 2 == 0 { 1 } else { 3 };
                sum += (digit - b'0') as i32 * weight;
            }
            println!("{}", (10 - sum % 10) % 10);
        ''')))

    packs.append(pack(6, "巡检编号", ["基础数学", "区间计数"],
        "一排设备的编号都是非负整数。巡检规则要求检查编号能被 k 整除的设备。现在只负责编号从 L 到 R 的设备，两个端点都包含在内。请统计需要检查的设备数。编号0能被任意正整数 k 整除。",
        "一行三个整数 L、R、k。", "输出一个整数，表示区间内能被 k 整除的编号数。",
        "0 ≤ L ≤ R ≤ 10^12，1 ≤ k ≤ 10^9。", multiples_oracle,
        [(3, 16, 4), (0, 0, 7), (5, 7, 10)],
        [("unit-step", (0, 10**12, 1)), ("left-divisible", (12, 12, 4)), ("single-not-divisible", (13, 13, 4)), ("large-step", (0, 10**12, 10**9)), ("last-divisible", (7, 21, 7)), ("both-excluded", (8, 20, 7)), ("high-small-range", (10**12 - 10, 10**12, 3))],
        [(left := rng.randrange(10**12 + 1), rng.randrange(left, 10**12 + 1), rng.randrange(1, 10**9 + 1)) for _ in range(10)],
        solutions('''
            long long left, right, step;
            scanf("%lld%lld%lld", &left, &right, &step);
            long long answer = right / step;
            if (left == 0) {
                answer++;
            } else {
                answer -= (left - 1) / step;
            }
            printf("%lld\\n", answer);
        ''', '''
            long long left, right, step;
            std::cin >> left >> right >> step;
            long long answer = right / step;
            if (left == 0) {
                answer++;
            } else {
                answer -= (left - 1) / step;
            }
            std::cout << answer << '\\n';
        ''', '''
            long left = in.nextLong();
            long right = in.nextLong();
            long step = in.nextLong();
            long answer = right / step;
            if (left == 0) {
                answer++;
            } else {
                answer -= (left - 1) / step;
            }
            System.out.println(answer);
        ''', '''
            left, right, step = map(int, input().split())
            answer = right // step + 1 if left == 0 else right // step - (left - 1) // step
            print(answer)
        ''', '''
            let left: i64 = words.next().unwrap().parse().unwrap();
            let right: i64 = words.next().unwrap().parse().unwrap();
            let step: i64 = words.next().unwrap().parse().unwrap();
            let answer = if left == 0 {
                right / step + 1
            } else {
                right / step - (left - 1) / step
            };
            println!("{}", answer);
        ''')))

    packs.append(pack(7, "网格边框灯", ["条件判断", "基础数学"],
        "一个矩形灯板有 r 行、c 列，每个格子一盏灯。只点亮最外侧的边框：第一行、最后一行、第一列、最后一列的灯。同一盏灯即使属于多条边也只能计数一次。当只有一行或一列时，该行或该列的所有灯都属于边框。请计算亮灯总数。",
        "一行两个整数 r、c。", "输出一个整数，表示亮灯总数。", "1 ≤ r,c ≤ 10^9。", border_oracle,
        [(3, 4), (1, 5), (2, 2)],
        [("one-cell", (1, 1)), ("single-column", (5, 1)), ("two-rows", (2, 100)), ("two-columns", (100, 2)), ("small-square", (3, 3)), ("max-square", (10**9, 10**9)), ("max-single-row", (1, 10**9))],
        [(rng.randrange(1, 10**9 + 1), rng.randrange(1, 10**9 + 1)) for _ in range(10)],
        solutions('''
            long long rows, columns;
            scanf("%lld%lld", &rows, &columns);
            long long answer;
            if (rows == 1 || columns == 1) {
                answer = rows * columns;
            } else {
                answer = 2 * rows + 2 * columns - 4;
            }
            printf("%lld\\n", answer);
        ''', '''
            long long rows, columns;
            std::cin >> rows >> columns;
            long long answer;
            if (rows == 1 || columns == 1) {
                answer = rows * columns;
            } else {
                answer = 2 * rows + 2 * columns - 4;
            }
            std::cout << answer << '\\n';
        ''', '''
            long rows = in.nextLong();
            long columns = in.nextLong();
            long answer = rows == 1 || columns == 1 ? rows * columns : 2 * rows + 2 * columns - 4;
            System.out.println(answer);
        ''', '''
            rows, columns = map(int, input().split())
            answer = rows * columns if rows == 1 or columns == 1 else 2 * rows + 2 * columns - 4
            print(answer)
        ''', '''
            let rows: i64 = words.next().unwrap().parse().unwrap();
            let columns: i64 = words.next().unwrap().parse().unwrap();
            let answer = if rows == 1 || columns == 1 {
                rows * columns
            } else {
                2 * rows + 2 * columns - 4
            };
            println!("{}", answer);
        ''')))

    dates = []
    for _ in range(10):
        year = rng.randrange(1, 9999)
        start = dt.date(year, 1, 1)
        offset = rng.randrange((dt.date(year + 1, 1, 1) - start).days)
        value = start + dt.timedelta(days=offset)
        dates.append((value.year, value.month, value.day))
    packs.append(pack(8, "明日值班日期", ["模拟", "条件判断"],
        "值班表给出一个合法的公历日期 y 年 m 月 d 日。请计算第二天的日期。公历闰年规则：能被400整除的年份是闰年；或者能被4整除且不能被100整除的年份是闰年。闰年的2月有29天，平年的2月有28天。其余月份按通常的公历天数计算。",
        "一行三个整数 y、m、d，保证表示合法日期。", "输出第二天的年、月、日三个整数，以空格分隔，无须补前导零。",
        "1 ≤ y ≤ 9998，1 ≤ m ≤ 12，d 为对应年月中的合法日号。", date_oracle,
        [(2026, 10, 1), (2024, 2, 28), (2026, 12, 31)],
        [("first-date", (1, 1, 1)), ("ordinary-february", (2023, 2, 28)), ("leap-last-day", (2000, 2, 29)), ("century-not-leap", (1900, 2, 28)), ("century-leap", (2000, 2, 28)), ("thirty-day-month", (2026, 4, 30)), ("thirty-one-day-month", (2026, 1, 31)), ("max-year-end", (9998, 12, 31))],
        dates,
        solutions('''
            int year, month, day;
            scanf("%d%d%d", &year, &month, &day);
            int lengths[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) {
                lengths[1] = 29;
            }
            day++;
            if (day > lengths[month - 1]) {
                day = 1;
                month++;
            }
            if (month > 12) {
                month = 1;
                year++;
            }
            printf("%d %d %d\\n", year, month, day);
        ''', '''
            int year, month, day;
            std::cin >> year >> month >> day;
            int lengths[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) {
                lengths[1] = 29;
            }
            day++;
            if (day > lengths[month - 1]) {
                day = 1;
                month++;
            }
            if (month > 12) {
                month = 1;
                year++;
            }
            std::cout << year << ' ' << month << ' ' << day << '\\n';
        ''', '''
            int year = in.nextInt();
            int month = in.nextInt();
            int day = in.nextInt();
            int[] lengths = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) {
                lengths[1] = 29;
            }
            day++;
            if (day > lengths[month - 1]) {
                day = 1;
                month++;
            }
            if (month > 12) {
                month = 1;
                year++;
            }
            System.out.println(year + " " + month + " " + day);
        ''', '''
            year, month, day = map(int, input().split())
            lengths = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
            if year % 400 == 0 or (year % 4 == 0 and year % 100 != 0):
                lengths[1] = 29
            day += 1
            if day > lengths[month - 1]:
                day = 1
                month += 1
            if month > 12:
                month = 1
                year += 1
            print(year, month, day)
        ''', '''
            let mut year: i32 = words.next().unwrap().parse().unwrap();
            let mut month: usize = words.next().unwrap().parse().unwrap();
            let mut day: i32 = words.next().unwrap().parse().unwrap();
            let mut lengths = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
            if year % 400 == 0 || (year % 4 == 0 && year % 100 != 0) {
                lengths[1] = 29;
            }
            day += 1;
            if day > lengths[month - 1] {
                day = 1;
                month += 1;
            }
            if month > 12 {
                month = 1;
                year += 1;
            }
            println!("{} {} {}", year, month, day);
        ''')))

    packs.append(pack(9, "滚动编号", ["递推", "取模"],
        "观测仪按序生成编号 x₀、x₁、x₂……。给定两个初始数 a、b 和正整数 p：x₀=a 对 p 取余，x₁=b 对 p 取余；从 x₂ 开始，每个编号等于前两个编号之和对 p 取余，即 xᵢ=(xᵢ₋₁+xᵢ₋₂) mod p。请求出第 n 个编号 xₙ。编号从0开始，输入的 n 可以为0。",
        "一行四个整数 n、a、b、p。", "输出一个整数 xₙ，其值在0到p−1之间。",
        "0 ≤ n ≤ 10^6，0 ≤ a,b ≤ 10^9，1 ≤ p ≤ 10^9+7。", recurrence_oracle,
        [(5, 1, 1, 100), (0, 123, 5, 10), (4, 2, 3, 7)],
        [("index-one", (1, 123, 45, 10)), ("modulus-one", (10**6, 10**9, 10**9, 1)), ("zero-seeds", (10**6, 0, 0, 1000000007)), ("first-recurrence", (2, 10**9, 10**9, 1000000007)), ("max-index", (10**6, 1, 1, 1000000007)), ("small-modulus", (997, 999999999, 888888888, 2)), ("initial-zero", (0, 0, 10**9, 1))],
        [(rng.randrange(0, 1001), rng.randrange(10**9 + 1), rng.randrange(10**9 + 1), rng.randrange(1, 10**9 + 8)) for _ in range(10)],
        solutions('''
            long long n, a, b, modulus;
            scanf("%lld%lld%lld%lld", &n, &a, &b, &modulus);
            a %= modulus;
            b %= modulus;
            for (long long i = 1; i < n; i++) {
                long long next = (a + b) % modulus;
                a = b;
                b = next;
            }
            printf("%lld\\n", n == 0 ? a : b);
        ''', '''
            long long n, a, b, modulus;
            std::cin >> n >> a >> b >> modulus;
            a %= modulus;
            b %= modulus;
            for (long long i = 1; i < n; i++) {
                long long next = (a + b) % modulus;
                a = b;
                b = next;
            }
            std::cout << (n == 0 ? a : b) << '\\n';
        ''', '''
            long n = in.nextLong();
            long a = in.nextLong();
            long b = in.nextLong();
            long modulus = in.nextLong();
            a %= modulus;
            b %= modulus;
            for (long i = 1; i < n; i++) {
                long next = (a + b) % modulus;
                a = b;
                b = next;
            }
            System.out.println(n == 0 ? a : b);
        ''', '''
            n, a, b, modulus = map(int, input().split())
            a %= modulus
            b %= modulus
            for _ in range(1, n):
                a, b = b, (a + b) % modulus
            print(a if n == 0 else b)
        ''', '''
            let n: usize = words.next().unwrap().parse().unwrap();
            let mut a: i64 = words.next().unwrap().parse().unwrap();
            let mut b: i64 = words.next().unwrap().parse().unwrap();
            let modulus: i64 = words.next().unwrap().parse().unwrap();
            a %= modulus;
            b %= modulus;
            for _ in 1..n {
                let next = (a + b) % modulus;
                a = b;
                b = next;
            }
            println!("{}", if n == 0 { a } else { b });
        ''')))

    fractions = []
    for _ in range(10):
        denominator = rng.randrange(-10**12, 10**12 + 1)
        if denominator == 0:
            denominator = 1
        fractions.append((rng.randrange(-10**12, 10**12 + 1), denominator))
    packs.append(pack(10, "比例卡规范化", ["基础数学", "最大公约数"],
        "一张比例卡用两个整数 a、b 表示分数 a/b，其中 b 不为0。为了统一展示，需要把它改写为最简分数：分母必须为正，分子和分母的最大公约数为1；零统一写成0/1。请输出规范化后的分子和分母。",
        "一行两个整数 a、b。", "输出两个整数，依次为最简分数的分子、正分母。", "−10^12 ≤ a,b ≤ 10^12，b ≠ 0。", fraction_oracle,
        [(12, 18), (9, -6), (0, -8)],
        [("both-negative", (-12, -18)), ("negative-numerator", (-7, 3)), ("equal-max", (10**12, 10**12)), ("coprime-large", (999999999999, 10**12)), ("zero-positive", (0, 10**12)), ("unit-negative-denominator", (1, -1)), ("opposite-max", (-10**12, 10**12))],
        fractions,
        solutions('''
            long long a, b;
            scanf("%lld%lld", &a, &b);
            if (b < 0) {
                a = -a;
                b = -b;
            }
            long long x = a < 0 ? -a : a;
            long long y = b;
            while (y != 0) {
                long long remainder = x % y;
                x = y;
                y = remainder;
            }
            printf("%lld %lld\\n", a / x, b / x);
        ''', '''
            long long a, b;
            std::cin >> a >> b;
            if (b < 0) {
                a = -a;
                b = -b;
            }
            long long x = a < 0 ? -a : a;
            long long y = b;
            while (y != 0) {
                long long remainder = x % y;
                x = y;
                y = remainder;
            }
            std::cout << a / x << ' ' << b / x << '\\n';
        ''', '''
            long a = in.nextLong();
            long b = in.nextLong();
            if (b < 0) {
                a = -a;
                b = -b;
            }
            long x = Math.abs(a);
            long y = b;
            while (y != 0) {
                long remainder = x % y;
                x = y;
                y = remainder;
            }
            System.out.println(a / x + " " + b / x);
        ''', '''
            from math import gcd

            a, b = map(int, input().split())
            if b < 0:
                a, b = -a, -b
            common = gcd(a, b)
            print(a // common, b // common)
        ''', '''
            let mut a: i64 = words.next().unwrap().parse().unwrap();
            let mut b: i64 = words.next().unwrap().parse().unwrap();
            if b < 0 {
                a = -a;
                b = -b;
            }
            let mut x = a.abs();
            let mut y = b;
            while y != 0 {
                let remainder = x % y;
                x = y;
                y = remainder;
            }
            println!("{} {}", a / x, b / x);
        ''')))

    manifest = []
    for item in packs:
        directory = ROOT / item["slug"]
        directory.mkdir(exist_ok=True)
        (directory / "pack.json").write_text(json.dumps(item, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        manifest.append({"slug": item["slug"], "title": item["title"], "difficulty": item["difficulty"], "tags": item["tags"], "modes": item["modes"], "cases": len(item["cases"]), "publicSamples": sum(case["sample"] for case in item["cases"]), "path": f"{item['slug']}/pack.json"})
    (ROOT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"packCount": len(packs), "caseCount": sum(len(item['cases']) for item in packs), "seed": 2026100101}, ensure_ascii=False))


if __name__ == "__main__":
    build()
