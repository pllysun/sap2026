"""Generate original beginner ACM problems 011–020 and deterministic test data.

All statements, implementations and cases are authored for this project.
The independent oracles below use simpler constructions than the references.
Run `python3 generate.py`, then `node format.mjs`, then `python3 validate.py`.
"""

from collections import Counter
from itertools import groupby
from pathlib import Path
import json
import random
import re
import textwrap

ROOT = Path(__file__).resolve().parent
LANGUAGES = ("c", "cpp", "java", "python", "rust")
SOURCE_NOTE = "软件协会原创"


def code(value):
    return textwrap.dedent(value).strip() + "\n"


C_STARTER = code("""
    #include <stdio.h>

    int main(void) {
        /* 读取标准输入，完成题目后输出答案。 */
        return 0;
    }
""")
CPP_STARTER = code("""
    #include <iostream>

    int main() {
        std::ios::sync_with_stdio(false);
        std::cin.tie(nullptr);

        // 读取标准输入，完成题目后输出答案。
        return 0;
    }
""")
JAVA_STARTER = code("""
    import java.io.BufferedReader;
    import java.io.InputStreamReader;

    public class Main {
        public static void main(String[] args) throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            // 读取标准输入，完成题目后输出答案。
        }
    }
""")
PYTHON_STARTER = code("""
    import sys


    def main():
        # 读取标准输入，完成题目后输出答案。
        pass


    if __name__ == "__main__":
        main()
""")
RUST_STARTER = code("""
    use std::io::{self, Read};

    fn main() {
        let mut input = String::new();
        io::stdin().read_to_string(&mut input).unwrap();
        // 读取标准输入，完成题目后输出答案。
    }
""")
STARTERS = dict(zip(LANGUAGES, (C_STARTER, CPP_STARTER, JAVA_STARTER, PYTHON_STARTER, RUST_STARTER)))


def cmain(body, extras=""):
    return code("#include <stdio.h>\n#include <stdlib.h>\n#include <string.h>\n" + extras) + "\nint main(void) {\n" + textwrap.indent(code(body), "    ") + "    return 0;\n}\n"


def cppmain(body):
    return code("""
        #include <algorithm>
        #include <iostream>
        #include <string>
        #include <vector>

        using namespace std;
    """) + "\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n" + textwrap.indent(code(body), "    ") + "    return 0;\n}\n"


JAVA_SCANNER = code("""
    static final class FastScanner {
        private final InputStream input = System.in;
        private final byte[] buffer = new byte[1 << 16];
        private int position = 0;
        private int length = 0;

        private int read() throws IOException {
            if (position == length) {
                length = input.read(buffer);
                position = 0;
                if (length < 0) {
                    return -1;
                }
            }
            return buffer[position++];
        }

        String next() throws IOException {
            int value;
            do {
                value = read();
            } while (value <= 32 && value != -1);
            StringBuilder token = new StringBuilder();
            while (value > 32 && value != -1) {
                token.append((char) value);
                value = read();
            }
            return token.toString();
        }

        int nextInt() throws IOException {
            return Integer.parseInt(next());
        }

        long nextLong() throws IOException {
            return Long.parseLong(next());
        }
    }
""")


def javamain(body, numeric=True):
    main = "public static void main(String[] args) throws Exception {\n"
    if numeric:
        main += "    FastScanner scanner = new FastScanner();\n"
    main += textwrap.indent(code(body), "    ") + "}\n"
    members = JAVA_SCANNER + "\n" + main if numeric else main
    return "import java.io.*;\nimport java.util.*;\n\npublic class Main {\n" + textwrap.indent(members, "    ") + "}\n"


def rustmain(body):
    return "use std::io::{self, Read};\n\nfn main() {\n    let mut input = String::new();\n    io::stdin().read_to_string(&mut input).unwrap();\n" + textwrap.indent(code(body), "    ") + "}\n"


def ints_line(values):
    return " ".join(map(str, values)) + "\n"


def oracle_011(raw):
    tokens = list(map(int, raw.split()))
    n, values = tokens[0], tokens[1:]
    assert 1 <= n <= 10000 and len(values) == n
    assert all(-10**9 <= value <= 10**9 for value in values)
    # Group indices by the number of decreases seen before them.
    groups, current = [], 0
    for index, value in enumerate(values):
        if index and value < values[index - 1]:
            current += 1
        groups.append(current)
    return str(max(sum(1 for _ in group) for _, group in groupby(groups))) + "\n"


def oracle_012(raw):
    tokens = list(map(int, raw.split()))
    n, k = tokens[:2]
    values = tokens[2:]
    assert 1 <= n <= 10000 and 0 <= k <= 10**9 and len(values) == n
    assert all(-10**9 <= value <= 10**9 for value in values)
    return ints_line([values[(index - k) % n] for index in range(n)])


def oracle_013(raw):
    tokens = list(map(int, raw.split()))
    n, scores = tokens[0], tokens[1:]
    assert 1 <= n <= 2000 and len(scores) == n
    assert all(0 <= score <= 10**9 for score in scores)
    # Compute each rank directly from the definition, independently of sort positions.
    ranks = [1 + sum(other > score for other in scores) for score in scores]
    order = sorted(range(n), key=lambda index: (-scores[index], index))
    return "".join(f"{index + 1} {ranks[index]}\n" for index in order)


def oracle_014(raw):
    lines = raw.splitlines()
    initial, q = map(int, lines[0].split())
    assert 0 <= initial <= 10**9 and 1 <= q <= 2000 and len(lines) == q + 1
    accepted_in, accepted_out, answer = [], [], []
    for line in lines[1:]:
        operation, text = line.split()
        amount = int(text)
        assert operation in ("IN", "OUT") and 1 <= amount <= 10**9
        stock = initial + sum(accepted_in) - sum(accepted_out)
        if operation == "IN":
            accepted_in.append(amount)
            answer.append(stock + amount)
        elif amount <= stock:
            accepted_out.append(amount)
            answer.append(stock - amount)
        else:
            answer.append(-1)
    return "".join(f"{value}\n" for value in answer)


def oracle_015(raw):
    tokens = list(map(int, raw.split()))
    n, q = tokens[:2]
    values = tokens[2:2 + n]
    update_tokens = tokens[2 + n:]
    assert 1 <= n <= 2000 and 1 <= q <= 2000
    assert len(values) == n and len(update_tokens) == q * 3
    assert all(-10**9 <= value <= 10**9 for value in values)
    # Apply every operation directly; references use a difference array.
    answers = values.copy()
    for start, end, change in zip(update_tokens[::3], update_tokens[1::3], update_tokens[2::3]):
        assert 1 <= start <= end <= n and -10**9 <= change <= 10**9
        for index in range(start - 1, end):
            answers[index] += change
    return ints_line(answers)


def text_line(raw, minimum=1):
    assert raw.endswith("\n") and raw.count("\n") == 1
    line = raw[:-1]
    assert minimum <= len(line) <= 10000
    return line


def oracle_016(raw):
    line = text_line(raw)
    assert re.fullmatch(r"[A-Za-z ]+", line) and any(char != " " for char in line)
    counts = Counter(char.lower() for char in line if char != " ")
    letter = min(counts, key=lambda char: (-counts[char], char))
    return f"{letter} {counts[letter]}\n"


def oracle_017(raw):
    line = text_line(raw)
    assert re.fullmatch(r"[A-Za-z ]+", line)
    return str(len(re.findall(r"[A-Za-z]+", line))) + "\n"


def oracle_018(raw):
    line = text_line(raw)
    assert re.fullmatch(r"[A-Z]+", line)
    return "".join(f"{letter} {sum(1 for _ in run)}\n" for letter, run in groupby(line))


def oracle_019(raw):
    lines = raw.splitlines()
    n, m = map(int, lines[0].split())
    rows = lines[1:]
    assert 1 <= n <= 40 and 1 <= m <= 40 and len(rows) == n
    assert all(len(row) == m and set(row) <= {".", "#"} for row in rows)
    lamps = [(row, col) for row in range(n) for col in range(m) if rows[row][col] == "#"]
    answer = []
    for row in range(n):
        answer.append("".join(
            "#" if rows[row][col] == "#" else str(sum(max(abs(row - r), abs(col - c)) == 1 for r, c in lamps))
            for col in range(m)
        ))
    return "\n".join(answer) + "\n"


def oracle_020(raw):
    tokens = list(map(int, raw.split()))
    n, m = tokens[:2]
    values = tokens[2:]
    assert 1 <= n <= 50 and 1 <= m <= 50 and len(values) == n * m
    assert all(-10**9 <= value <= 10**9 for value in values)
    rows = [values[start:start + m] for start in range(0, len(values), m)]
    # A clockwise turn is a transpose of vertically reversed rows.
    return "".join(ints_line(row) for row in zip(*reversed(rows)))


ORACLES = {index: globals()[f"oracle_{index:03}"] for index in range(11, 21)}


REFERENCES = {}

REFERENCES[11] = {
    "c": cmain("""
        int n;
        scanf("%d", &n);
        long long previous, value;
        scanf("%lld", &previous);
        int current = 1, best = 1;
        for (int i = 1; i < n; ++i) {
            scanf("%lld", &value);
            current = value >= previous ? current + 1 : 1;
            if (current > best) {
                best = current;
            }
            previous = value;
        }
        printf("%d\\n", best);
    """),
    "cpp": cppmain("""
        int n;
        cin >> n;
        long long previous, value;
        cin >> previous;
        int current = 1, best = 1;
        for (int i = 1; i < n; ++i) {
            cin >> value;
            current = value >= previous ? current + 1 : 1;
            best = max(best, current);
            previous = value;
        }
        cout << best << '\\n';
    """),
    "java": javamain("""
        int n = scanner.nextInt();
        long previous = scanner.nextLong();
        int current = 1;
        int best = 1;
        for (int i = 1; i < n; i++) {
            long value = scanner.nextLong();
            current = value >= previous ? current + 1 : 1;
            best = Math.max(best, current);
            previous = value;
        }
        System.out.println(best);
    """),
    "python": code("""
        import sys

        data = list(map(int, sys.stdin.buffer.read().split()))
        n = data[0]
        values = data[1:]
        current = best = 1
        for index in range(1, n):
            current = current + 1 if values[index] >= values[index - 1] else 1
            best = max(best, current)
        print(best)
    """),
    "rust": rustmain("""
        let mut tokens = input.split_whitespace();
        let n: usize = tokens.next().unwrap().parse().unwrap();
        let mut previous: i64 = tokens.next().unwrap().parse().unwrap();
        let mut current = 1usize;
        let mut best = 1usize;
        for _ in 1..n {
            let value: i64 = tokens.next().unwrap().parse().unwrap();
            current = if value >= previous { current + 1 } else { 1 };
            best = best.max(current);
            previous = value;
        }
        println!("{}", best);
    """),
}

REFERENCES[12] = {
    "c": cmain("""
        int n;
        long long k, values[10000];
        scanf("%d%lld", &n, &k);
        for (int i = 0; i < n; ++i) {
            scanf("%lld", &values[i]);
        }
        int shift = (int) (k % n);
        for (int i = 0; i < n; ++i) {
            if (i > 0) {
                printf(" ");
            }
            printf("%lld", values[(i - shift + n) % n]);
        }
        printf("\\n");
    """),
    "cpp": cppmain("""
        int n;
        long long k;
        cin >> n >> k;
        vector<long long> values(n);
        for (auto& value : values) {
            cin >> value;
        }
        int shift = static_cast<int>(k % n);
        rotate(values.begin(), values.end() - shift, values.end());
        for (int i = 0; i < n; ++i) {
            cout << (i ? " " : "") << values[i];
        }
        cout << '\\n';
    """),
    "java": javamain("""
        int n = scanner.nextInt();
        int shift = (int) (scanner.nextLong() % n);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) {
            values[i] = scanner.nextLong();
        }
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                answer.append(' ');
            }
            answer.append(values[(i - shift + n) % n]);
        }
        System.out.println(answer);
    """),
    "python": code("""
        import sys

        data = list(map(int, sys.stdin.buffer.read().split()))
        n, k = data[:2]
        values = data[2:]
        shift = k % n
        result = values[n - shift:] + values[:n - shift]
        print(*result)
    """),
    "rust": rustmain("""
        let mut tokens = input.split_whitespace();
        let n: usize = tokens.next().unwrap().parse().unwrap();
        let k: usize = tokens.next().unwrap().parse().unwrap();
        let mut values: Vec<i64> = tokens.map(|token| token.parse().unwrap()).collect();
        values.rotate_right(k % n);
        let answer = values.iter().map(|value| value.to_string()).collect::<Vec<_>>().join(" ");
        println!("{}", answer);
    """),
}

REFERENCES[13] = {
    "c": cmain("""
        int n;
        scanf("%d", &n);
        Student students[2000];
        for (int i = 0; i < n; ++i) {
            students[i].id = i + 1;
            scanf("%d", &students[i].score);
        }
        qsort(students, n, sizeof(Student), compare_students);
        int rank = 1;
        for (int i = 0; i < n; ++i) {
            if (i == 0 || students[i].score != students[i - 1].score) {
                rank = i + 1;
            }
            printf("%d %d\\n", students[i].id, rank);
        }
    """, code("""
        typedef struct {
            int id;
            int score;
        } Student;

        int compare_students(const void* left, const void* right) {
            const Student* a = (const Student*) left;
            const Student* b = (const Student*) right;
            if (a->score != b->score) {
                return a->score > b->score ? -1 : 1;
            }
            return a->id - b->id;
        }
    """)),
    "cpp": cppmain("""
        int n;
        cin >> n;
        vector<int> scores(n), order(n);
        for (int i = 0; i < n; ++i) {
            cin >> scores[i];
            order[i] = i;
        }
        sort(order.begin(), order.end(), [&](int a, int b) {
            if (scores[a] != scores[b]) {
                return scores[a] > scores[b];
            }
            return a < b;
        });
        int rank = 1;
        for (int i = 0; i < n; ++i) {
            if (i == 0 || scores[order[i]] != scores[order[i - 1]]) {
                rank = i + 1;
            }
            cout << order[i] + 1 << ' ' << rank << '\\n';
        }
    """),
    "java": javamain("""
        int n = scanner.nextInt();
        int[] scores = new int[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            scores[i] = scanner.nextInt();
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            if (scores[a] != scores[b]) {
                return Integer.compare(scores[b], scores[a]);
            }
            return Integer.compare(a, b);
        });
        StringBuilder answer = new StringBuilder();
        int rank = 1;
        for (int i = 0; i < n; i++) {
            if (i == 0 || scores[order[i]] != scores[order[i - 1]]) {
                rank = i + 1;
            }
            answer.append(order[i] + 1).append(' ').append(rank).append('\\n');
        }
        System.out.print(answer);
    """),
    "python": code("""
        import sys

        data = list(map(int, sys.stdin.buffer.read().split()))
        n = data[0]
        scores = data[1:]
        order = sorted(range(n), key=lambda index: (-scores[index], index))
        answer = []
        rank = 1
        for position, index in enumerate(order):
            if position == 0 or scores[index] != scores[order[position - 1]]:
                rank = position + 1
            answer.append(f"{index + 1} {rank}")
        print("\\n".join(answer))
    """),
    "rust": rustmain("""
        let mut tokens = input.split_whitespace();
        let n: usize = tokens.next().unwrap().parse().unwrap();
        let scores: Vec<i64> = tokens.map(|token| token.parse().unwrap()).collect();
        let mut order: Vec<usize> = (0..n).collect();
        order.sort_by(|&a, &b| scores[b].cmp(&scores[a]).then(a.cmp(&b)));
        let mut rank = 1usize;
        let mut answer = String::new();
        for position in 0..n {
            let index = order[position];
            if position == 0 || scores[index] != scores[order[position - 1]] {
                rank = position + 1;
            }
            answer.push_str(&format!("{} {}\\n", index + 1, rank));
        }
        print!("{}", answer);
    """),
}

REFERENCES[14] = {
    "c": cmain("""
        long long stock, amount;
        int q;
        scanf("%lld%d", &stock, &q);
        for (int i = 0; i < q; ++i) {
            char operation[4];
            scanf("%3s%lld", operation, &amount);
            if (strcmp(operation, "IN") == 0) {
                stock += amount;
                printf("%lld\\n", stock);
            } else if (stock >= amount) {
                stock -= amount;
                printf("%lld\\n", stock);
            } else {
                printf("-1\\n");
            }
        }
    """),
    "cpp": cppmain("""
        long long stock, amount;
        int q;
        cin >> stock >> q;
        for (int i = 0; i < q; ++i) {
            string operation;
            cin >> operation >> amount;
            if (operation == "IN") {
                stock += amount;
                cout << stock << '\\n';
            } else if (stock >= amount) {
                stock -= amount;
                cout << stock << '\\n';
            } else {
                cout << -1 << '\\n';
            }
        }
    """),
    "java": javamain("""
        long stock = scanner.nextLong();
        int q = scanner.nextInt();
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < q; i++) {
            String operation = scanner.next();
            long amount = scanner.nextLong();
            if (operation.equals("IN")) {
                stock += amount;
                answer.append(stock);
            } else if (stock >= amount) {
                stock -= amount;
                answer.append(stock);
            } else {
                answer.append(-1);
            }
            answer.append('\\n');
        }
        System.out.print(answer);
    """),
    "python": code("""
        import sys

        tokens = iter(sys.stdin.buffer.read().split())
        stock = int(next(tokens))
        q = int(next(tokens))
        answer = []
        for _ in range(q):
            operation = next(tokens)
            amount = int(next(tokens))
            if operation == b"IN":
                stock += amount
                answer.append(str(stock))
            elif stock >= amount:
                stock -= amount
                answer.append(str(stock))
            else:
                answer.append("-1")
        print("\\n".join(answer))
    """),
    "rust": rustmain("""
        let mut tokens = input.split_whitespace();
        let mut stock: i64 = tokens.next().unwrap().parse().unwrap();
        let q: usize = tokens.next().unwrap().parse().unwrap();
        let mut answer = String::new();
        for _ in 0..q {
            let operation = tokens.next().unwrap();
            let amount: i64 = tokens.next().unwrap().parse().unwrap();
            let result = if operation == "IN" {
                stock += amount;
                stock
            } else if stock >= amount {
                stock -= amount;
                stock
            } else {
                -1
            };
            answer.push_str(&format!("{}\\n", result));
        }
        print!("{}", answer);
    """),
}

REFERENCES[15] = {
    "c": cmain("""
        int n, q;
        long long values[2000], difference[2001] = {0};
        scanf("%d%d", &n, &q);
        for (int i = 0; i < n; ++i) {
            scanf("%lld", &values[i]);
        }
        for (int i = 0; i < q; ++i) {
            int left, right;
            long long change;
            scanf("%d%d%lld", &left, &right, &change);
            difference[left - 1] += change;
            difference[right] -= change;
        }
        long long added = 0;
        for (int i = 0; i < n; ++i) {
            added += difference[i];
            printf("%s%lld", i > 0 ? " " : "", values[i] + added);
        }
        printf("\\n");
    """),
    "cpp": cppmain("""
        int n, q;
        cin >> n >> q;
        vector<long long> values(n), difference(n + 1, 0);
        for (auto& value : values) {
            cin >> value;
        }
        for (int i = 0; i < q; ++i) {
            int left, right;
            long long change;
            cin >> left >> right >> change;
            difference[left - 1] += change;
            difference[right] -= change;
        }
        long long added = 0;
        for (int i = 0; i < n; ++i) {
            added += difference[i];
            cout << (i ? " " : "") << values[i] + added;
        }
        cout << '\\n';
    """),
    "java": javamain("""
        int n = scanner.nextInt();
        int q = scanner.nextInt();
        long[] values = new long[n];
        long[] difference = new long[n + 1];
        for (int i = 0; i < n; i++) {
            values[i] = scanner.nextLong();
        }
        for (int i = 0; i < q; i++) {
            int left = scanner.nextInt();
            int right = scanner.nextInt();
            long change = scanner.nextLong();
            difference[left - 1] += change;
            difference[right] -= change;
        }
        StringBuilder answer = new StringBuilder();
        long added = 0;
        for (int i = 0; i < n; i++) {
            added += difference[i];
            if (i > 0) {
                answer.append(' ');
            }
            answer.append(values[i] + added);
        }
        System.out.println(answer);
    """),
    "python": code("""
        import sys

        data = list(map(int, sys.stdin.buffer.read().split()))
        n, q = data[:2]
        values = data[2:2 + n]
        difference = [0] * (n + 1)
        for query in range(q):
            left, right, change = data[2 + n + query * 3:5 + n + query * 3]
            difference[left - 1] += change
            difference[right] -= change
        answer = []
        added = 0
        for index in range(n):
            added += difference[index]
            answer.append(values[index] + added)
        print(*answer)
    """),
    "rust": rustmain("""
        let mut tokens = input.split_whitespace();
        let n: usize = tokens.next().unwrap().parse().unwrap();
        let q: usize = tokens.next().unwrap().parse().unwrap();
        let mut values = vec![0i64; n];
        for value in &mut values {
            *value = tokens.next().unwrap().parse().unwrap();
        }
        let mut difference = vec![0i64; n + 1];
        for _ in 0..q {
            let left: usize = tokens.next().unwrap().parse().unwrap();
            let right: usize = tokens.next().unwrap().parse().unwrap();
            let change: i64 = tokens.next().unwrap().parse().unwrap();
            difference[left - 1] += change;
            difference[right] -= change;
        }
        let mut answer = String::new();
        let mut added = 0i64;
        for i in 0..n {
            added += difference[i];
            if i > 0 {
                answer.push(' ');
            }
            answer.push_str(&(values[i] + added).to_string());
        }
        println!("{}", answer);
    """),
}

REFERENCES[16] = {
    "c": cmain("""
        char line[10002];
        int counts[26] = {0};
        fgets(line, sizeof(line), stdin);
        for (int i = 0; line[i] != '\\0'; ++i) {
            char letter = line[i];
            if (letter >= 'A' && letter <= 'Z') {
                letter = (char) (letter - 'A' + 'a');
            }
            if (letter >= 'a' && letter <= 'z') {
                ++counts[letter - 'a'];
            }
        }
        int best = 0;
        for (int i = 1; i < 26; ++i) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        printf("%c %d\\n", 'a' + best, counts[best]);
    """),
    "cpp": cppmain("""
        string line;
        getline(cin, line);
        int counts[26] = {};
        for (char letter : line) {
            if (letter >= 'A' && letter <= 'Z') {
                letter = static_cast<char>(letter - 'A' + 'a');
            }
            if (letter >= 'a' && letter <= 'z') {
                ++counts[letter - 'a'];
            }
        }
        int best = 0;
        for (int i = 1; i < 26; ++i) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        cout << static_cast<char>('a' + best) << ' ' << counts[best] << '\\n';
    """),
    "java": javamain("""
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = reader.readLine();
        int[] counts = new int[26];
        for (char letter : line.toCharArray()) {
            if (letter >= 'A' && letter <= 'Z') {
                letter = (char) (letter - 'A' + 'a');
            }
            if (letter >= 'a' && letter <= 'z') {
                counts[letter - 'a']++;
            }
        }
        int best = 0;
        for (int i = 1; i < 26; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        System.out.println((char) ('a' + best) + " " + counts[best]);
    """, numeric=False),
    "python": code("""
        import sys

        line = sys.stdin.readline().lower()
        counts = [0] * 26
        for letter in line:
            if "a" <= letter <= "z":
                counts[ord(letter) - ord("a")] += 1
        best = 0
        for index in range(1, 26):
            if counts[index] > counts[best]:
                best = index
        print(chr(ord("a") + best), counts[best])
    """),
    "rust": rustmain("""
        let mut counts = [0usize; 26];
        for byte in input.bytes() {
            let letter = byte.to_ascii_lowercase();
            if letter.is_ascii_lowercase() {
                counts[(letter - b'a') as usize] += 1;
            }
        }
        let mut best = 0usize;
        for i in 1..26 {
            if counts[i] > counts[best] {
                best = i;
            }
        }
        println!("{} {}", (b'a' + best as u8) as char, counts[best]);
    """),
}

REFERENCES[17] = {
    "c": cmain("""
        char line[10002];
        fgets(line, sizeof(line), stdin);
        int count = 0, inside_word = 0;
        for (int i = 0; line[i] != '\\0'; ++i) {
            char letter = line[i];
            int is_letter = (letter >= 'a' && letter <= 'z') || (letter >= 'A' && letter <= 'Z');
            if (is_letter && !inside_word) {
                ++count;
            }
            inside_word = is_letter;
        }
        printf("%d\\n", count);
    """),
    "cpp": cppmain("""
        string line;
        getline(cin, line);
        int count = 0;
        bool inside_word = false;
        for (char letter : line) {
            bool is_letter = (letter >= 'a' && letter <= 'z') || (letter >= 'A' && letter <= 'Z');
            if (is_letter && !inside_word) {
                ++count;
            }
            inside_word = is_letter;
        }
        cout << count << '\\n';
    """),
    "java": javamain("""
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = reader.readLine();
        int count = 0;
        boolean insideWord = false;
        for (char letter : line.toCharArray()) {
            boolean isLetter = (letter >= 'a' && letter <= 'z') || (letter >= 'A' && letter <= 'Z');
            if (isLetter && !insideWord) {
                count++;
            }
            insideWord = isLetter;
        }
        System.out.println(count);
    """, numeric=False),
    "python": code("""
        import sys

        line = sys.stdin.readline()
        print(len(line.split()))
    """),
    "rust": rustmain("""
        println!("{}", input.split_whitespace().count());
    """),
}

REFERENCES[18] = {
    "c": cmain("""
        char text[10001];
        scanf("%10000s", text);
        int length = (int) strlen(text);
        int start = 0;
        while (start < length) {
            int end = start + 1;
            while (end < length && text[end] == text[start]) {
                ++end;
            }
            printf("%c %d\\n", text[start], end - start);
            start = end;
        }
    """),
    "cpp": cppmain("""
        string text;
        cin >> text;
        int start = 0;
        int length = static_cast<int>(text.size());
        while (start < length) {
            int end = start + 1;
            while (end < length && text[end] == text[start]) {
                ++end;
            }
            cout << text[start] << ' ' << end - start << '\\n';
            start = end;
        }
    """),
    "java": javamain("""
        String text = scanner.next();
        StringBuilder answer = new StringBuilder();
        int start = 0;
        while (start < text.length()) {
            int end = start + 1;
            while (end < text.length() && text.charAt(end) == text.charAt(start)) {
                end++;
            }
            answer.append(text.charAt(start)).append(' ').append(end - start).append('\\n');
            start = end;
        }
        System.out.print(answer);
    """),
    "python": code("""
        import sys

        text = sys.stdin.readline().strip()
        answer = []
        start = 0
        while start < len(text):
            end = start + 1
            while end < len(text) and text[end] == text[start]:
                end += 1
            answer.append(f"{text[start]} {end - start}")
            start = end
        print("\\n".join(answer))
    """),
    "rust": rustmain("""
        let text = input.trim().as_bytes();
        let mut answer = String::new();
        let mut start = 0usize;
        while start < text.len() {
            let mut end = start + 1;
            while end < text.len() && text[end] == text[start] {
                end += 1;
            }
            answer.push_str(&format!("{} {}\\n", text[start] as char, end - start));
            start = end;
        }
        print!("{}", answer);
    """),
}

REFERENCES[19] = {
    "c": cmain("""
        int n, m;
        char grid[40][41];
        scanf("%d%d", &n, &m);
        for (int row = 0; row < n; ++row) {
            scanf("%40s", grid[row]);
        }
        for (int row = 0; row < n; ++row) {
            for (int col = 0; col < m; ++col) {
                if (grid[row][col] == '#') {
                    putchar('#');
                    continue;
                }
                int count = 0;
                for (int dr = -1; dr <= 1; ++dr) {
                    for (int dc = -1; dc <= 1; ++dc) {
                        int r = row + dr, c = col + dc;
                        if ((dr != 0 || dc != 0) && r >= 0 && r < n && c >= 0 && c < m && grid[r][c] == '#') {
                            ++count;
                        }
                    }
                }
                putchar('0' + count);
            }
            putchar('\\n');
        }
    """),
    "cpp": cppmain("""
        int n, m;
        cin >> n >> m;
        vector<string> grid(n);
        for (string& row : grid) {
            cin >> row;
        }
        for (int row = 0; row < n; ++row) {
            for (int col = 0; col < m; ++col) {
                if (grid[row][col] == '#') {
                    cout << '#';
                    continue;
                }
                int count = 0;
                for (int dr = -1; dr <= 1; ++dr) {
                    for (int dc = -1; dc <= 1; ++dc) {
                        int r = row + dr, c = col + dc;
                        if ((dr != 0 || dc != 0) && r >= 0 && r < n && c >= 0 && c < m && grid[r][c] == '#') {
                            ++count;
                        }
                    }
                }
                cout << count;
            }
            cout << '\\n';
        }
    """),
    "java": javamain("""
        int n = scanner.nextInt();
        int m = scanner.nextInt();
        String[] grid = new String[n];
        for (int row = 0; row < n; row++) {
            grid[row] = scanner.next();
        }
        StringBuilder answer = new StringBuilder();
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < m; col++) {
                if (grid[row].charAt(col) == '#') {
                    answer.append('#');
                    continue;
                }
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int r = row + dr;
                        int c = col + dc;
                        if ((dr != 0 || dc != 0) && r >= 0 && r < n && c >= 0 && c < m && grid[r].charAt(c) == '#') {
                            count++;
                        }
                    }
                }
                answer.append(count);
            }
            answer.append('\\n');
        }
        System.out.print(answer);
    """),
    "python": code("""
        import sys

        lines = sys.stdin.buffer.read().split()
        n, m = map(int, lines[:2])
        grid = lines[2:]
        answer = []
        for row in range(n):
            result = []
            for col in range(m):
                if grid[row][col] == ord("#"):
                    result.append("#")
                    continue
                count = 0
                for dr in range(-1, 2):
                    for dc in range(-1, 2):
                        r, c = row + dr, col + dc
                        if (dr or dc) and 0 <= r < n and 0 <= c < m and grid[r][c] == ord("#"):
                            count += 1
                result.append(str(count))
            answer.append("".join(result))
        print("\\n".join(answer))
    """),
    "rust": rustmain("""
        let mut tokens = input.split_whitespace();
        let n: usize = tokens.next().unwrap().parse().unwrap();
        let m: usize = tokens.next().unwrap().parse().unwrap();
        let grid: Vec<&[u8]> = tokens.map(|row| row.as_bytes()).collect();
        let mut answer = String::new();
        for row in 0..n {
            for col in 0..m {
                if grid[row][col] == b'#' {
                    answer.push('#');
                    continue;
                }
                let mut count = 0u8;
                for dr in -1i32..=1 {
                    for dc in -1i32..=1 {
                        let r = row as i32 + dr;
                        let c = col as i32 + dc;
                        if (dr != 0 || dc != 0) && r >= 0 && r < n as i32 && c >= 0 && c < m as i32 && grid[r as usize][c as usize] == b'#' {
                            count += 1;
                        }
                    }
                }
                answer.push((b'0' + count) as char);
            }
            answer.push('\\n');
        }
        print!("{}", answer);
    """),
}

REFERENCES[20] = {
    "c": cmain("""
        int n, m;
        long long values[50][50];
        scanf("%d%d", &n, &m);
        for (int row = 0; row < n; ++row) {
            for (int col = 0; col < m; ++col) {
                scanf("%lld", &values[row][col]);
            }
        }
        for (int row = 0; row < m; ++row) {
            for (int col = 0; col < n; ++col) {
                if (col > 0) {
                    printf(" ");
                }
                printf("%lld", values[n - 1 - col][row]);
            }
            printf("\\n");
        }
    """),
    "cpp": cppmain("""
        int n, m;
        cin >> n >> m;
        vector<vector<long long>> values(n, vector<long long>(m));
        for (auto& row : values) {
            for (auto& value : row) {
                cin >> value;
            }
        }
        for (int row = 0; row < m; ++row) {
            for (int col = 0; col < n; ++col) {
                cout << (col ? " " : "") << values[n - 1 - col][row];
            }
            cout << '\\n';
        }
    """),
    "java": javamain("""
        int n = scanner.nextInt();
        int m = scanner.nextInt();
        long[][] values = new long[n][m];
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < m; col++) {
                values[row][col] = scanner.nextLong();
            }
        }
        StringBuilder answer = new StringBuilder();
        for (int row = 0; row < m; row++) {
            for (int col = 0; col < n; col++) {
                if (col > 0) {
                    answer.append(' ');
                }
                answer.append(values[n - 1 - col][row]);
            }
            answer.append('\\n');
        }
        System.out.print(answer);
    """),
    "python": code("""
        import sys

        data = list(map(int, sys.stdin.buffer.read().split()))
        n, m = data[:2]
        values = [data[2 + row * m:2 + (row + 1) * m] for row in range(n)]
        for row in range(m):
            print(*(values[n - 1 - col][row] for col in range(n)))
    """),
    "rust": rustmain("""
        let mut tokens = input.split_whitespace();
        let n: usize = tokens.next().unwrap().parse().unwrap();
        let m: usize = tokens.next().unwrap().parse().unwrap();
        let mut values = vec![vec![0i64; m]; n];
        for row in &mut values {
            for value in row {
                *value = tokens.next().unwrap().parse().unwrap();
            }
        }
        let mut answer = String::new();
        for row in 0..m {
            for col in 0..n {
                if col > 0 {
                    answer.push(' ');
                }
                answer.push_str(&values[n - 1 - col][row].to_string());
            }
            answer.push('\\n');
        }
        print!("{}", answer);
    """),
}


METADATA = {
    11: dict(
        title="连续进步的记录", tags=["数组", "模拟"],
        description="训练员记录了连续 n 天的训练得分。请找出最长的一段连续日期，使这段日期内的得分从前一天到后一天始终不下降。相邻两天得分相等也满足要求。输出这段日期的天数；只需要输出长度，不需要输出起止日期。",
        inputFormat="第一行一个整数 n。第二行 n 个整数 a₁,…,aₙ，按日期先后给出每天的得分。",
        outputFormat="输出一个整数，表示最长连续非下降段的长度。",
        constraints="1 ≤ n ≤ 10000；-10^9 ≤ aᵢ ≤ 10^9。",
    ),
    12: dict(
        title="循环轮值表", tags=["数组", "模拟"],
        description="轮值表包含 n 个整数编号。管理员需要将整张表向右循环移动 k 次：每移动一次，最后一个编号移到最前面，其余编号各向右移动一位。请输出移动后的轮值表。编号可以重复，负数编号也按普通整数处理。",
        inputFormat="第一行两个整数 n、k。第二行 n 个整数 a₁,…,aₙ，表示原轮值表。",
        outputFormat="一行输出移动后的 n 个整数，以空格分隔。",
        constraints="1 ≤ n ≤ 10000；0 ≤ k ≤ 10^9；-10^9 ≤ aᵢ ≤ 10^9。",
    ),
    13: dict(
        title="训练积分名次", tags=["排序", "数组"],
        description="n 名同学的编号依次为 1 到 n，每人有一个训练积分。积分越高，名次越靠前；相同积分共享名次。某人的名次等于“积分严格高于他的人数 + 1”。请按积分从高到低列出所有同学；积分相同的同学按编号从小到大排列，并输出每个人的编号与名次。",
        inputFormat="第一行一个整数 n。第二行 n 个整数 s₁,…,sₙ，第 i 个数表示编号 i 的同学的积分。",
        outputFormat="输出 n 行，每行两个整数：同学编号和名次，按题目要求排序。并列第一之后可能直接出现第三名。",
        constraints="1 ≤ n ≤ 2000；0 ≤ sᵢ ≤ 10^9。",
    ),
    14: dict(
        title="物资库的出入", tags=["模拟", "基础数学"],
        description="物资库最初有 S 件物资，需要依次执行 q 次操作。IN x 表示入库 x 件，入库总能成功；OUT x 表示申请出库 x 件。若当前库存不少于 x，则出库成功并扣减库存，否则出库失败且库存保持不变。每次操作成功后输出新的库存；出库失败时输出 -1。失败只影响当前操作，后续操作仍按顺序执行。",
        inputFormat="第一行两个整数 S、q。接下来 q 行，每行一个操作名称 IN 或 OUT，再跟一个整数 x。",
        outputFormat="输出 q 行。每行对应一次操作，成功时输出操作后的库存，失败时输出 -1。",
        constraints="0 ≤ S ≤ 10^9；1 ≤ q ≤ 2000；每次操作的 1 ≤ x ≤ 10^9。库存及结果可能超过 32 位整数范围，请使用 64 位整数。",
    ),
    15: dict(
        title="分段温度调节", tags=["差分数组", "前缀和"],
        description="温控设备排成一行，共有 n 个位置，编号从 1 到 n，每个位置有一个初始整数温度。管理员依次给出 q 个调节操作 l、r、d，表示把编号位于闭区间 [l,r] 的每个位置的温度都增加 d。d 为负数时表示降温。所有操作完成后，输出每个位置的最终温度。",
        inputFormat="第一行两个整数 n、q。第二行 n 个整数 a₁,…,aₙ，表示初始温度。接下来 q 行，每行三个整数 l、r、d，表示一次区间调节。",
        outputFormat="一行输出 n 个整数，按位置从 1 到 n 给出最终温度，以空格分隔。",
        constraints="1 ≤ n,q ≤ 2000；-10^9 ≤ aᵢ,d ≤ 10^9；1 ≤ l ≤ r ≤ n。最终温度可能超过 32 位整数范围。",
    ),
    16: dict(
        title="海报字母统计", tags=["字符串", "计数"],
        description="一张英文海报的文字由英文字母和空格组成。统计出现次数最多的英文字母，不区分大小写，空格不参与统计。如果有多个字母出现次数相同且最多，选择字母表顺序最靠前的字母。输出选中字母的小写形式和出现次数。",
        inputFormat="输入一整行文本，可以包含行首、行尾和连续空格，只含 A–Z、a–z 与普通空格。文本至少包含一个英文字母。",
        outputFormat="一行输出一个小写英文字母及其出现次数，以空格分隔。",
        constraints="1 ≤ 文本长度 ≤ 10000（不包括行末换行）；至少包含一个英文字母。",
    ),
    17: dict(
        title="值班留言的词数", tags=["字符串", "模拟"],
        description="值班留言是一行只包含英文字母和空格的文本。一个单词定义为一段长度至少为 1 的连续英文字母；空格用于分隔单词。行首、行尾及连续多个空格都不算单词。请输出留言的单词数量。整行只有空格时，答案为 0。大小写不影响单词的划分。",
        inputFormat="输入一整行文本，只含 A–Z、a–z 与普通空格。需要完整读取该行，不能只读取第一个单词。",
        outputFormat="输出一个整数，表示单词数量。",
        constraints="1 ≤ 文本长度 ≤ 10000（不包括行末换行）；允许整行都是空格。",
    ),
    18: dict(
        title="连续标记压缩", tags=["字符串", "模拟"],
        description="训练日志用大写英文字母标记事件。请从左到右，将每一段连续相同的字母压缩为“字母 次数”，每段输出一行。只有相邻且相同的字母才能合并；同一字母在不同位置出现的段必须分别输出。",
        inputFormat="一行包含一个非空字符串，只含大写英文字母 A–Z。",
        outputFormat="按出现顺序输出每段连续相同字母，每行一个字母及该段长度，以空格分隔。",
        constraints="1 ≤ 字符串长度 ≤ 10000。",
    ),
    19: dict(
        title="灯位周边计数", tags=["二维数组", "模拟"],
        description="教室的灯位布局是一个 n 行 m 列的网格。字符 # 表示该位置装有灯，字符 . 表示空位。请将每个空位替换为它周围八个方向（上、下、左、右及四个斜角）中装灯的位置数量；超出网格的位置忽略。已有灯的位置仍输出 #。网格不会循环连接，计数时不包含位置自身。",
        inputFormat="第一行两个整数 n、m。接下来 n 行，每行 m 个字符，只含 # 与 .。",
        outputFormat="输出 n 行，每行 m 个字符。灯位输出 #，空位输出一个 0 到 8 的数字；字符之间不加空格。",
        constraints="1 ≤ n,m ≤ 40。",
    ),
    20: dict(
        title="旋转数据表", tags=["二维数组", "模拟"],
        description="一张数据表有 n 行 m 列，每格存放一个整数。请将整张表顺时针旋转 90 度，输出旋转后的数据表。旋转后共有 m 行 n 列，原表左下角的数字将出现在新表左上角。数据值可以重复，也可以为负数。",
        inputFormat="第一行两个整数 n、m。接下来 n 行，每行 m 个整数。",
        outputFormat="输出 m 行，每行 n 个整数，以空格分隔，表示顺时针旋转后的数据表。",
        constraints="1 ≤ n,m ≤ 50；每个整数的范围为 [-10^9,10^9]。",
    ),
}


def sequence(values, extra=None):
    header = str(len(values)) if extra is None else f"{len(values)} {extra}"
    return header + "\n" + ints_line(values)


def inventory(initial, operations):
    return f"{initial} {len(operations)}\n" + "".join(f"{op} {value}\n" for op, value in operations)


def temperature_updates(values, updates):
    return f"{len(values)} {len(updates)}\n" + ints_line(values) + "".join(f"{left} {right} {change}\n" for left, right, change in updates)


def grid(rows):
    return f"{len(rows)} {len(rows[0])}\n" + "\n".join(rows) + "\n"


def matrix(rows):
    return f"{len(rows)} {len(rows[0])}\n" + "".join(ints_line(row) for row in rows)


def inputs_for(index):
    rng = random.Random(20261001000 + index)
    if index == 11:
        cases = [
            sequence([3, 3, 5, 2, 4, 4]), sequence([9]), sequence([5, 4, 3, 2, 1]),
            sequence([0] * 10000), sequence(list(range(10000))), sequence(list(range(10000, 0, -1))),
            sequence([-10**9, 0, 10**9]), sequence([10**9, -10**9]),
            sequence([1, 2, 0, 1, 2, 3]), sequence([1, 2, 2, 3, 0]),
            sequence([1, 0] * 5000),
        ]
        cases += [sequence([rng.randint(-20, 20) for _ in range(rng.randint(2, 150))]) for _ in range(14)]
    elif index == 12:
        cases = [
            sequence([10, 20, 30, 40, 50], 2), sequence([7, 8, 9], 0), sequence([-2, 4, -2, 8], 5),
            sequence([10**9], 10**9), sequence(list(range(10000)), 10**9),
            sequence([1, 2, 3], 3), sequence([1, 2, 3], 1), sequence([1, 2, 3], 2),
            sequence([-10**9, 10**9], 1), sequence([5] * 10000, 999999999),
            sequence(list(range(10000)), 9999),
        ]
        cases += [sequence([rng.randint(-100, 100) for _ in range(rng.randint(2, 150))], rng.randint(0, 10**9)) for _ in range(14)]
    elif index == 13:
        cases = [
            sequence([80, 100, 100]), sequence([7]), sequence([30, 20, 20, 10]),
            sequence([0] * 2000), sequence(list(range(2000))), sequence(list(range(2000, 0, -1))),
            sequence([10**9, 0, 10**9, 0]), sequence([9, 9, 8, 8, 8, 7]),
            sequence([10**9] * 2000), sequence([1, 0] * 1000), sequence([0, 10**9]),
        ]
        cases += [sequence([rng.randint(0, 30) for _ in range(rng.randint(2, 100))]) for _ in range(14)]
    elif index == 14:
        cases = [
            inventory(5, [("OUT", 3), ("OUT", 3), ("IN", 4), ("OUT", 6)]),
            inventory(0, [("OUT", 1), ("IN", 1), ("OUT", 1)]),
            inventory(10, [("IN", 5), ("OUT", 4), ("OUT", 11)]),
            inventory(0, [("OUT", 10**9)]), inventory(10**9, [("OUT", 10**9)]),
            inventory(10**9, [("IN", 10**9)] * 2000),
            inventory(0, [("IN", 1), ("OUT", 1)] * 1000),
            inventory(0, [("OUT", 1)] * 2000),
            inventory(2, [("OUT", 3), ("OUT", 2), ("IN", 5), ("OUT", 6), ("OUT", 5)]),
            inventory(0, [("IN", 10**9), ("IN", 10**9), ("IN", 10**9), ("OUT", 10**9)]),
            inventory(10**9, [("OUT", 1)] * 2000),
        ]
        cases += [inventory(rng.randint(0, 100), [(rng.choice(["IN", "OUT"]), rng.randint(1, 100)) for _ in range(rng.randint(2, 100))]) for _ in range(14)]
    elif index == 15:
        cases = [
            temperature_updates([2, -1, 4, 0, -3], [(1, 3, 2), (2, 5, -1), (4, 4, 7)]),
            temperature_updates([-7], [(1, 1, 10)]),
            temperature_updates([10**9] * 3, [(1, 3, 10**9)] * 2),
            temperature_updates([10**9] * 2000, [(1, 2000, 10**9)] * 2000),
            temperature_updates([-10**9] * 2000, [(1, 2000, -10**9)] * 2000),
            temperature_updates([0] * 2000, [(i, i, i) for i in range(1, 2001)]),
            temperature_updates([1, -1] * 1000, [(1, 2000, 7), (1, 2000, -7), (2, 1999, 0)]),
            temperature_updates([5, 6, 7], [(1, 1, 3), (3, 3, -3), (1, 3, 2)]),
            temperature_updates([-10**9, 10**9], [(1, 2, 1), (1, 1, 10**9), (2, 2, -10**9)]),
            temperature_updates([9], [(1, 1, 1)] * 2000),
            temperature_updates(list(range(2000)), [(1, 2000, 0), (999, 1001, -500)]),
        ]
        for _ in range(14):
            n = rng.randint(2, 150)
            values = [rng.randint(-10**9, 10**9) for _ in range(n)]
            updates = []
            for _ in range(rng.randint(2, 100)):
                left = rng.randint(1, n)
                updates.append((left, rng.randint(left, n), rng.randint(-10**9, 10**9)))
            cases.append(temperature_updates(values, updates))
    elif index == 16:
        cases = [
            "Code Club\n", "bBaA\n", "   Z  z Z   \n", "A\n", "z" * 10000 + "\n",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ\n", " " * 9999 + "Q\n",
            "Q" + " " * 9999 + "\n", "Aa" * 5000 + "\n", "B" * 4999 + "a" * 5000 + "\n",
            "  c   B  b  C A  a  \n",
        ]
        alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ    "
        for _ in range(14):
            cases.append(rng.choice("abcdefghijklmnopqrstuvwxyz") + "".join(rng.choice(alphabet) for _ in range(rng.randint(1, 1000))) + "\n")
    elif index == 17:
        cases = [
            "  hello   Code club  \n", "   \n", "one\n", " \n", " " * 10000 + "\n",
            "A" * 10000 + "\n", "a " * 5000 + "\n", " " * 9999 + "a\n", "a" + " " * 9999 + "\n",
            "ONE two THREE four\n", "a  b   c    d\n",
        ]
        for _ in range(14):
            words = ["".join(rng.choice("abcXYZ") for _ in range(rng.randint(1, 12))) for _ in range(rng.randint(1, 50))]
            cases.append(" " * rng.randint(0, 8) + (" " * rng.randint(1, 8)).join(words) + " " * rng.randint(0, 8) + "\n")
    elif index == 18:
        cases = [
            "AAABBCA\n", "Z\n", "ABAB\n", "A" * 10000 + "\n", "AB" * 5000 + "\n",
            "Z" * 9999 + "A\n", "A" + "Z" * 9999 + "\n", "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n",
            "AABBAA\n", "A" * 9 + "B" * 10 + "C" * 11 + "\n", "ZZZZZZZZZZZZ\n",
        ]
        for _ in range(14):
            cases.append("".join(rng.choice("ABCDE") * rng.randint(1, 25) for _ in range(rng.randint(2, 25))) + "\n")
    elif index == 19:
        cases = [
            grid(["#..", ".#.", "..."]), grid(["."]), grid(["###", "#.#", "###"]),
            grid(["#"]), grid(["." * 40] * 40), grid(["#" * 40] * 40),
            grid(["#.##..#"]), grid(["#", ".", "#", ".", "."]),
            grid(["#...#", ".....", ".....", ".....", "#...#"]),
            grid(["#." * 20, ".#" * 20] * 20),
            grid(["." * 39 + "#"] * 40),
        ]
        for _ in range(14):
            n, m = rng.randint(1, 16), rng.randint(1, 16)
            cases.append(grid(["".join(rng.choice("...##") for _ in range(m)) for _ in range(n)]))
    elif index == 20:
        cases = [
            matrix([[1, 2, 3], [4, 5, 6]]), matrix([[-7]]), matrix([[1], [2], [3]]),
            matrix([[1, 2, 3, 4]]), matrix([[0] * 50 for _ in range(50)]),
            matrix([[row * 50 + col for col in range(50)] for row in range(50)]),
            matrix([[10**9, -10**9], [-10**9, 10**9]]), matrix([[1, 2], [3, 4]]),
            matrix([[row - col for col in range(50)] for row in range(1)]),
            matrix([[row] for row in range(50)]), matrix([[-1, -2], [-3, -4], [-5, -6]]),
        ]
        for _ in range(14):
            n, m = rng.randint(1, 15), rng.randint(1, 15)
            cases.append(matrix([[rng.randint(-10**9, 10**9) for _ in range(m)] for _ in range(n)]))
    else:
        raise ValueError(index)
    assert len(cases) == 25
    assert len(set(cases)) == len(cases), f"duplicate tests for {index}"
    return cases


def generate():
    manifest = []
    for index in range(11, 21):
        slug = f"original-acm-{index:03}"
        raw_inputs = inputs_for(index)
        cases = [dict(
            name=f"sample-{position + 1}" if position < 3 else f"edge-{position - 2}" if position < 11 else f"random-{position - 10}",
            input=raw,
            expectedOutput=ORACLES[index](raw),
            sample=position < 3,
        ) for position, raw in enumerate(raw_inputs)]
        pack = dict(
            slug=slug,
            **METADATA[index],
            difficulty="EASY",
            sourcePlatform="原创",
            sourceId=f"ORIGINAL-ACM-{index:03}",
            sourceUrl="https://csuftsap.top/oj",
            sourceNote=SOURCE_NOTE,
            modes=["STDIO"],
            defaultMode="STDIO",
            checker="TOKENS",
            profiles={language: dict(starterStdio=STARTERS[language], starterFunction="", functionDriver="") for language in LANGUAGES},
            references={language: {"STDIO": REFERENCES[index][language]} for language in LANGUAGES},
            cases=cases,
        )
        directory = ROOT / slug
        directory.mkdir(exist_ok=True)
        (directory / "pack.json").write_text(json.dumps(pack, ensure_ascii=False, indent=2) + "\n")
        manifest.append(dict(slug=slug, title=pack["title"], path=f"{slug}/pack.json", cases=len(cases), samples=3, modes=["STDIO"], languages=list(LANGUAGES), randomSeed=20261001000 + index))
    (ROOT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(dict(packs=len(manifest), cases=sum(item["cases"] for item in manifest)), ensure_ascii=False))


if __name__ == "__main__":
    generate()
