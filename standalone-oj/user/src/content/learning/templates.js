export const ioTemplates = {
  c:`#include <stdio.h>

int main(void) {
    long long a, b;
    // 先确认输入完整，再处理和输出。按题目的实际格式修改这里。
    if (scanf("%lld %lld", &a, &b) != 2) return 0;
    printf("%lld\\n", a + b);
    return 0;
}
`,
  cpp:`#include <iostream>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    long long a, b;
    // 根据题目修改读取方式、计算逻辑与输出。
    if (!(cin >> a >> b)) return 0;
    cout << a + b << '\\n';
    return 0;
}
`,
  java:`import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // Scanner 便于入门；大输入量时使用缓冲读取。
        Scanner input = new Scanner(System.in);
        long a = input.nextLong();
        long b = input.nextLong();
        System.out.println(a + b);
    }
}
`,
  python:`import sys

def main():
    # 按空白分割输入；需要整行字符串时改用 readline。
    tokens = sys.stdin.buffer.read().split()
    if len(tokens) < 2:
        return
    a, b = int(tokens[0]), int(tokens[1])
    print(a + b)

if __name__ == "__main__":
    main()
`,
  rust:`use std::io::{self, Read};

fn main() {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input).unwrap();
    // 按空白分割输入，再将字符串转换为所需类型。
    let mut tokens = input.split_whitespace();
    let a: i64 = tokens.next().unwrap().parse().unwrap();
    let b: i64 = tokens.next().unwrap().parse().unwrap();
    println!("{}", a + b);
}
`,
}
