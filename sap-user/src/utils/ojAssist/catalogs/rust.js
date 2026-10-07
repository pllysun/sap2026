import { fn, value, type, profile } from '../catalog.js'
const vec = [fn('push', ['value: T'], '()', '在末尾加入元素。'), fn('pop', [], 'Option<T>', '取出末元素；空时返回 None。'),
  fn('len', [], 'usize', '元素数量。'), fn('is_empty', [], 'bool', '是否为空。'), fn('clear', [], '()', '清空容器。'),
  fn('get', ['index: usize'], 'Option<&T>', '按下标访问，返回 Option。'), fn('sort', [], '()', '原地排序；元素须实现 Ord。'),
  fn('sort_unstable', [], '()', '原地不稳定排序。'), fn('reverse', [], '()', '反转元素。'),
  fn('iter', [], 'Iterator', '不可变迭代器。'), fn('iter_mut', [], 'Iterator', '可变迭代器。'),
  fn('contains', ['value: &T'], 'bool', '是否包含元素。'), fn('capacity', [], 'usize', '已分配容量。'),
  fn('reserve', ['additional: usize'], '()', '预留额外容量。'), fn('new', [], 'Vec', '创建空向量。', { static: true }),
  fn('with_capacity', ['capacity: usize'], 'Vec', '创建指定容量的向量。', { static: true })]
const types = {
  Vec: vec,
  String: [fn('push', ['value: char'], '()', '追加一个字符。'), fn('push_str', ['text: &str'], '()', '追加字符串。'),
    fn('len', [], 'usize', '字节数量。'), fn('is_empty', [], 'bool', '是否为空。'), fn('as_str', [], 'str', '借用字符串切片。'),
    fn('trim', [], 'str', '移除两端空白。'), fn('split_whitespace', [], 'Iterator', '按空白分割。'),
    fn('chars', [], 'Iterator', '遍历 Unicode 标量值。'), fn('bytes', [], 'Iterator', '遍历字节。'),
    fn('contains', ['pattern: &str'], 'bool', '是否包含子串。'), fn('new', [], 'String', '创建空字符串。', { static: true }),
    fn('from', ['text: &str'], 'String', '由字符串切片创建 String。', { static: true })],
  str: [fn('len', [], 'usize', '字节数量。'), fn('is_empty', [], 'bool', '是否为空。'),
    fn('trim', [], 'str', '移除两端空白。'), fn('split', ['pattern: &str'], 'Iterator', '分割字符串。'),
    fn('split_whitespace', [], 'Iterator', '按空白分割。'), fn('chars', [], 'Iterator', '遍历 Unicode 标量值。'),
    fn('bytes', [], 'Iterator', '遍历字节。'), fn('to_string', [], 'String', '转换为拥有所有权的 String。'),
    fn('parse', [], 'Result', '解析目标类型，通常需要类型注解或泛型参数。')],
  HashMap: [fn('insert', ['key: K', 'value: V'], 'Option<V>', '保存键值对。'), fn('get', ['key: &K'], 'Option<&V>', '按键取值。'),
    fn('contains_key', ['key: &K'], 'bool', '是否包含键。'), fn('remove', ['key: &K'], 'Option<V>', '删除键。'),
    fn('len', [], 'usize', '键值对数量。'), fn('is_empty', [], 'bool', '是否为空。'), fn('keys', [], 'Iterator', '遍历键。'),
    fn('values', [], 'Iterator', '遍历值。'), fn('iter', [], 'Iterator', '遍历键值对。'), fn('clear', [], '()', '清空映射。'),
    fn('new', [], 'HashMap', '创建空映射。', { static: true })],
  HashSet: [fn('insert', ['value: T'], 'bool', '插入元素。'), fn('contains', ['value: &T'], 'bool', '是否包含元素。'),
    fn('remove', ['value: &T'], 'bool', '删除元素。'), fn('len', [], 'usize', '元素数量。'), fn('is_empty', [], 'bool', '是否为空。'),
    fn('iter', [], 'Iterator', '遍历元素。'), fn('new', [], 'HashSet', '创建空集合。', { static: true })],
  VecDeque: [fn('push_back', ['value: T'], '()', '在队尾添加。'), fn('push_front', ['value: T'], '()', '在队首添加。'),
    fn('pop_front', [], 'Option<T>', '取出队首元素。'), fn('pop_back', [], 'Option<T>', '取出队尾元素。'),
    fn('len', [], 'usize', '元素数量。'), fn('is_empty', [], 'bool', '是否为空。'), fn('new', [], 'VecDeque', '创建空队列。', { static: true })],
  Option: [fn('unwrap', [], '$element', '取出 Some 的值；None 时 panic。'), fn('unwrap_or', ['default: T'], '$element', '取值或返回默认值。'),
    fn('is_some', [], 'bool', '是否为 Some。'), fn('is_none', [], 'bool', '是否为 None。')],
  Result: [fn('unwrap', [], '$element', '取出 Ok 的值；Err 时 panic。'), fn('expect', ['message: &str'], '$element', '取出 Ok 的值；Err 时按指定信息 panic。'),
    fn('is_ok', [], 'bool', '是否为 Ok。'), fn('is_err', [], 'bool', '是否为 Err。')],
  Iterator: [fn('collect', [], '', '收集到容器，通常需要目标类型注解。'), fn('count', [], 'usize', '消费迭代器并统计元素。'),
    fn('enumerate', [], 'Iterator', '遍历下标及元素。'), fn('sum', [], '', '累计元素，通常需要目标类型注解。'),
    fn('map', ['function'], 'Iterator', '逐项转换。'), fn('filter', ['predicate'], 'Iterator', '筛选元素。')],
  io: [fn('stdin', [], 'Stdin', '取得标准输入句柄。', { static: true })],
  Stdin: [fn('read_line', ['buffer: &mut String'], 'Result', '读取一行到字符串。')],
  collections: [], std: []
}
const globals = [type('Vec', '动态数组，标准预导入类型。'), type('String', '拥有所有权的字符串。'), type('Option', '可选值。'), type('Result', '成功或错误。'),
  ...['HashMap', 'HashSet', 'VecDeque'].map(name => type(name, '需要 use std::collections::' + name + '。')),
  value('Some', 'Option', 'Option 的有值分支。'), value('None', 'Option', 'Option 的空值分支。'),
  value('Ok', 'Result', 'Result 的成功分支。'), value('Err', 'Result', 'Result 的错误分支。'),
  fn('println!', ['format', '...'], '()', '格式化输出并换行。'), fn('print!', ['format', '...'], '()', '格式化输出。'),
  fn('vec!', ['values'], 'Vec', '创建向量，例如 vec![1, 2, 3]。', { macroDelimiter: '[' }),
  value('std', 'std', '标准库。', { type: 'namespace' })]
types.collections = ['HashMap', 'HashSet', 'VecDeque'].map(name => ({ ...type(name), static: true }))
types.std = [value('io', 'io', '输入输出模块。', { type: 'namespace', static: true }), value('collections', 'collections', '集合模块。', { type: 'namespace', static: true })]
export default profile(
  'as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return Self self static struct super trait true type unsafe use where while',
  {
    main: { detail: '完整程序入口', body: 'fn main() {\n    ${code}\n}' },
    fori: { detail: '范围循环', body: 'for ${i} in 0..${n} {\n    ${code}\n}' },
    foreach: { detail: '遍历元素', body: 'for ${value} in ${values} {\n    ${code}\n}' },
    function: { detail: '定义函数', body: 'fn ${solve}(${parameters}) -> ${i32} {\n    ${code}\n}' },
    ifelse: { detail: '条件分支', body: 'if ${condition} {\n    ${code}\n} else {\n    ${other}\n}' },
    match: { detail: '模式匹配', body: 'match ${value} {\n    ${pattern} => ${result},\n    _ => ${other},\n}' }
  }, globals, types
)
