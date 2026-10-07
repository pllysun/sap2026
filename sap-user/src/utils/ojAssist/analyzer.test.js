import test from 'node:test'
import assert from 'node:assert/strict'
import { createAnalyzer } from './analyzer.js'
import { loadLanguage } from './languages.js'

async function engine(language) { const { parser, catalog } = await loadLanguage(language); return createAnalyzer(parser, catalog, language) }
async function query(language, source, kind = 'complete') {
  const position = source.indexOf('§'); assert(position >= 0)
  const code = source.replace('§', ''), analyzer = await engine(language)
  return kind === 'complete' ? analyzer.complete(code, position, true) : analyzer[kind](code, position)
}
const labels = result => result?.options.map(item => item.label) || []

for (const [language, code, expected] of [
  ['c', 'int solve(int amount){ int count=0; cou§ }', 'count'],
  ['cpp', 'int main(){ std::vector<int> nums; nums.pu§ }', 'push_back'],
  ['java', 'class Main { void solve(){ java.util.ArrayList<Integer> nums = new java.util.ArrayList<>(); nums.ad§ }}', 'add'],
  ['python', 'def solve(nums: list[int]):\n    nums.ap§', 'append'],
  ['rust', 'fn main(){ let mut nums = Vec::new(); nums.pu§ }', 'push']
]) test(language + ': contextual variable/member completion', async () => assert(labels(await query(language, code)).includes(expected)))

for (const [language, code] of [
  ['c', 'void first(){ int privateVariable = 1; } void second(){ pri§ }'],
  ['cpp', 'int main(){ if(true){ int privateVariable = 1; } pri§ }'],
  ['java', 'class Main { void first(){ int privateVariable = 1; } void second(){ pri§ }}'],
  ['python', 'def first():\n    privateVariable = 1\n\ndef second():\n    pri§'],
  ['rust', 'fn main(){ { let privateVariable = 1; } pri§ }']
]) test(language + ': symbols from another scope are excluded', async () => assert(!labels(await query(language, code)).includes('privateVariable')))

test('Shadowed variables use the innermost declaration type', async () => {
  const result = labels(await query('java', 'class Main { void solve(){ String data=""; { ArrayList<Integer> data = new ArrayList<>(); data.§ } }}'))
  assert(result.includes('add')); assert(!result.includes('substring'))
})
test('Declarations after the cursor are excluded', async () => assert(!labels(await query('cpp', 'int main(){ lat§; int laterValue = 3; }')).includes('laterValue')))
test('Function parameters are visible and include their declared types', async () => {
  const result = await query('cpp', 'int solve(std::vector<int>& nums, int target){ nums.si§ }')
  assert(labels(result).includes('size'))
  assert(labels(await query('python', 'def solve(target: int):\n    tar§')).includes('target'))
})
test('Unfinished core-function member expressions retain parameter types', async () => {
  for (const [lang, source, member] of [
    ['cpp', 'class Solution { public: vector<int> solve(vector<int>& nums) { nums.§\n return {}; } };', 'size'],
    ['java', 'class Solution { int solve(int[] nums) { nums.§\n return 0; } }', 'length'],
    ['python', 'class Solution:\n    def solve(self, nums: list[int]):\n        nums.§\n        return 0', 'append'],
    ['rust', 'impl Solution { fn solve(nums: Vec<i32>) -> i32 { nums.§\n 0 } }', 'len']
  ]) assert(labels(await query(lang, source)).includes(member), lang)
})
test('Python assignments infer common literal types', async () => {
  assert(labels(await query('python', 'values = []\nvalues.ap§')).includes('append'))
  assert(labels(await query('python', 'text = "abc"\ntext.sp§')).includes('split'))
  assert(labels(await query('python', 'counts = {}\ncounts.ge§')).includes('get'))
})
test('Rust constructors and explicit generic constructors infer container types', async () => {
  assert(labels(await query('rust', 'fn main(){ let nums = Vec::<i32>::new(); nums.pu§ }')).includes('push'))
  assert(labels(await query('rust', 'fn main(){ let text = String::from("a"); text.pu§ }')).includes('push_str'))
  assert(labels(await query('rust', 'fn main(){ let mut nums = vec![1, 2]; nums.pu§ }')).includes('push'))
})
test('Assignment call chains retain their final result type', async () => {
  assert(labels(await query('python', 'values = input().split()\nvalues.ap§')).includes('append'))
  assert(labels(await query('java', 'class Main { void solve(){ var text = new String("a").substring(0); text.sub§ }}')).includes('substring'))
})
test('Range loop variables carry types inside their own scope', async () => {
  assert(labels(await query('java', 'class Main { void solve(){ List<String> values = new ArrayList<>(); for (String value : values) { value.sub§ } }}')).includes('substring'))
  assert(labels(await query('cpp', 'int main(){ vector<string> values; for (const auto& value : values) { value.si§ } }')).includes('size'))
  assert(labels(await query('rust', 'fn main(){ let values: Vec<String> = Vec::new(); for value in &values { value.pu§ } }')).includes('push_str'))
  for (const [lang, source] of [
    ['java', 'class Main { void solve(){ List<String> values; for (String item : values) {} ite§ }}'],
    ['cpp', 'int main(){ vector<string> values; for (auto item : values) {} ite§ }'],
    ['rust', 'fn main(){ let values: Vec<String>; for item in &values {} ite§ }']
  ]) assert(!labels(await query(lang, source)).includes('item'), lang)
})
test('C structs and Rust fields expose their actual member names', async () => {
  assert.deepEqual(labels(await query('c', 'struct Point { int x; int y; }; int main(){ struct Point point; point.§ }')).sort(), ['x', 'y'])
  assert.deepEqual(labels(await query('rust', 'struct Point { x: i32, y: i32 } fn main(){ let point: Point; point.§ }')).sort(), ['x', 'y'])
})
test('Java field and call chains retain result types', async () => {
  assert(labels(await query('java', 'class Main { void solve(){ System.out.pr§ }}')).includes('println'))
  assert(labels(await query('java', 'class Main { void solve(){ List<String> values = new ArrayList<>(); values.get(0).sub§ }}')).includes('substring'))
  assert.deepEqual(labels(await query('java', 'class Main { void solve(int[] nums){ nums.§ }}')), ['length'])
})
test('C++ auto variables retain inferred alias types', async () => assert(labels(await query('cpp', 'int main(){ std::vector<int> nums; auto copy = nums; copy.re§ }')).includes('reserve')))
test('Referenced and nested containers retain their element types in call chains', async () => {
  assert(labels(await query('cpp', 'void solve(const vector<string>& names){ names.at(0).sub§; }')).includes('substr'))
  assert(labels(await query('cpp', 'void solve(vector<vector<string>>& names){ names.at(0).at(0).sub§; }')).includes('substr'))
  assert(labels(await query('rust', 'fn solve(mut names: Vec<&str>){ names.pop().unwrap().tr§; }')).includes('trim'))
})
test('Displayed member signatures use resolved types without internal placeholders', async () => {
  const cpp = await query('cpp', 'void solve(vector<int>& nums){ nums.§ }')
  assert.equal(cpp.options.find(item => item.label === 'at').signature, 'at(size_t index) → int')
  assert.equal(cpp.options.find(item => item.label === 'push_back').parameters[0], 'const int& value')
  const java = await query('java', 'class Main { void solve(List<String> names){ names.§ }}')
  assert.equal(java.options.find(item => item.label === 'get').signature, 'get(int index) → String')
  assert(!JSON.stringify([cpp, java]).includes('$element')); assert(!JSON.stringify([cpp, java]).includes('$value'))
})
test('Python imported module and constructor aliases are resolved', async () => {
  assert(labels(await query('python', 'import math as m\nm.sq§')).includes('sqrt'))
  assert(labels(await query('python', 'from collections import deque as Queue\nvalues = Queue()\nvalues.po§')).includes('popleft'))
})
test('User-defined methods are available through this/self', async () => {
  assert(labels(await query('java', 'class Solution { int calculate(int n){ return n; } void solve(){ this.ca§ }}')).includes('calculate'))
  assert(labels(await query('python', 'class Solution:\n    def calculate(self, n: int):\n        return n\n    def solve(self):\n        self.ca§')).includes('calculate'))
  assert(labels(await query('rust', 'struct Solution {} impl Solution { fn calculate(&self, n: i32) -> i32 {n} fn solve(&self){ self.ca§ } }')).includes('calculate'))
})
test('Keywords remain separate between C and C++', async () => {
  assert(labels(await query('cpp', 'name§')).includes('namespace'))
  assert(!labels(await query('c', 'name§')).includes('namespace'))
})
for (const [language, source] of [
  ['c', 'int main(){ // prin§'], ['cpp', 'int main(){ const char* text = "prin§"; }'],
  ['java', 'class Main { // System.out.pr§'], ['python', '# pri§'],
  ['rust', 'fn main(){ let text = "pri§"; }']
]) test(language + ': comments and strings do not offer code completions', async () => assert.equal(await query(language, source), null))
test('Unknown receiver does not offer unrelated standard-library members', async () => assert.deepEqual(labels(await query('cpp', 'int main(){ missing.§ }')), []))
test('Signatures select arguments and ignore commas inside nested calls and strings', async () => {
  const result = await query('c', 'int main(){ printf("%d,%d", abs(3), §); }', 'signature')
  assert.equal(result.label, 'printf'); assert.equal(result.activeParameter, 2)
  const java = await query('java', 'class Main { void solve(){ Map<Integer, Integer> data = new HashMap<>(); data.put(1, §); }}', 'signature')
  assert.equal(java.label, 'put'); assert.equal(java.activeParameter, 1)
})
test('A signature closes when the call has ended', async () => assert.equal(await query('python', 'print(1)\n§', 'signature'), null))
test('Source-provided descriptions remain inert text', async () => {
  const result = await query('java', 'class Main { void solve(){ String text = "<img src=x onerror=alert(1)>"; text.su§ }}')
  assert(labels(result).includes('substring'))
  assert(!JSON.stringify(result).includes('onerror'))
})
test('Correct representative programs do not get syntax warnings', async () => {
  for (const [language, code] of [
    ['c', '#include <stdio.h>\nint main(void){ int a,b; scanf("%d%d", &a, &b); printf("%d\\n", a+b); return 0; }'],
    ['cpp', '#include <iostream>\nint main(){ int a,b; std::cin >> a >> b; std::cout << a+b; }'],
    ['java', 'import java.util.*; public class Main { public static void main(String[] args){ Scanner s = new Scanner(System.in); System.out.println(s.nextInt()+s.nextInt()); } }'],
    ['python', 'a, b = map(int, input().split())\nprint(a + b)\n'],
    ['rust', 'fn main(){ let mut values = Vec::new(); values.push(1); println!("{}", values.len()); }']
  ]) assert.deepEqual((await engine(language)).diagnostics(code), [], language)
})
test('Invalid syntax produces bounded warnings without executing source', async () => {
  const result = (await engine('java')).diagnostics('class Main { void solve( {')
  assert(result.length > 0 && result.length <= 12); assert(result.every(item => item.severity === 'warning' && item.to >= item.from))
})
test('Analysis does not execute arbitrary source', async () => {
  const result = await query('python', 'import os\nos.system("touch /tmp/should-not-exist")\nvalues = []\nvalues.ap§')
  assert(labels(result).includes('append'))
})
