import { fn, value, type, profile } from '../catalog.js'
const sequence = [
  fn('size', [], 'size_t', '元素数量。'), fn('empty', [], 'bool', '是否为空。'),
  fn('clear', [], 'void', '清空容器。'), fn('begin', [], 'iterator', '首元素迭代器。'),
  fn('end', [], 'iterator', '尾后迭代器。'), fn('front', [], '$element', '首元素；容器必须非空。'),
  fn('back', [], '$element', '末元素；容器必须非空。')
]
const vector = [...sequence, fn('push_back', ['const T& value'], 'void', '在末尾添加元素。'),
  fn('pop_back', [], 'void', '移除末元素；容器必须非空。'), fn('at', ['size_t index'], '$element', '带边界检查的元素访问。'),
  fn('resize', ['size_t count'], 'void', '调整元素数量。'), fn('reserve', ['size_t capacity'], 'void', '预留容量。'),
  fn('capacity', [], 'size_t', '已分配容量。'), fn('insert', ['iterator position', 'const T& value'], 'iterator', '在指定位置插入元素。'),
  fn('erase', ['iterator position'], 'iterator', '删除指定元素。'), fn('data', [], 'T *', '连续存储区地址。')]
const map = [
  fn('size', [], 'size_t', '键值对数量。'), fn('empty', [], 'bool', '是否为空。'), fn('clear', [], 'void', '清空容器。'),
  fn('find', ['const Key& key'], 'iterator', '查找键；不存在时返回 end()。'),
  fn('count', ['const Key& key'], 'size_t', '键的数量。'), fn('contains', ['const Key& key'], 'bool', '是否包含键。'),
  fn('at', ['const Key& key'], '$value', '访问键对应的值。'), fn('insert', ['const value_type& entry'], 'pair', '插入键值对。'),
  fn('erase', ['const Key& key'], 'size_t', '删除键。'), fn('begin', [], 'iterator', '首元素迭代器。'), fn('end', [], 'iterator', '尾后迭代器。')
]
const set = [fn('size', [], 'size_t', '元素数量。'), fn('empty', [], 'bool', '是否为空。'), fn('clear', [], 'void', '清空容器。'),
  fn('insert', ['const T& value'], 'pair', '插入元素。'), fn('erase', ['const T& value'], 'size_t', '删除元素。'),
  fn('count', ['const T& value'], 'size_t', '元素数量。'), fn('contains', ['const T& value'], 'bool', '是否包含元素。'),
  fn('find', ['const T& value'], 'iterator', '查找元素。'), fn('begin', [], 'iterator', '首元素迭代器。'), fn('end', [], 'iterator', '尾后迭代器。')]
const stack = [fn('push', ['const T& value'], 'void', '添加元素。'), fn('pop', [], 'void', '移除顶部元素。'),
  fn('top', [], '$element', '查看顶部元素。'), fn('size', [], 'size_t', '元素数量。'), fn('empty', [], 'bool', '是否为空。')]
const types = {
  vector, deque: [...vector, fn('push_front', ['const T& value'], 'void', '在首部添加元素。'), fn('pop_front', [], 'void', '移除首元素。')],
  string: [...sequence, fn('length', [], 'size_t', '字符串长度。'), fn('substr', ['size_t position', 'size_t count'], 'string', '截取子串；count 可省略。'),
    fn('find', ['const string& text'], 'size_t', '查找子串；不存在时返回 string::npos。'),
    fn('append', ['const string& text'], 'string', '追加字符串。'), fn('push_back', ['char value'], 'void', '追加字符。'),
    fn('pop_back', [], 'void', '删除末字符。'), fn('c_str', [], 'const char *', '以空字符结尾的字符串。'),
    fn('at', ['size_t index'], 'char', '带边界检查的字符访问。'), value('npos', 'size_t', '未找到的位置标记。', { static: true })],
  map, unordered_map: map, set, unordered_set: set, multiset: set,
  stack, priority_queue: stack,
  queue: [fn('push', ['const T& value'], 'void', '在队尾添加元素。'), fn('pop', [], 'void', '移除队首元素。'),
    fn('front', [], '$element', '队首元素。'), fn('back', [], '$element', '队尾元素。'), fn('size', [], 'size_t', '元素数量。'), fn('empty', [], 'bool', '是否为空。')],
  pair: [value('first', '$element', '第一个成员。'), value('second', '$value', '第二个成员。')],
  istream: [fn('getline', ['char *buffer', 'streamsize count'], 'istream', '读取一行。'), fn('get', [], 'int', '读取字符。'), fn('good', [], 'bool', '流状态是否正常。')],
  ostream: [fn('put', ['char value'], 'ostream', '输出字符。'), fn('flush', [], 'ostream', '刷新输出缓冲区。')]
}
const globals = [
  ...Object.keys(types).filter(name => !['istream', 'ostream'].includes(name)).map(name => type(name, 'std::' + name + '，需要对应标准库头文件。')),
  fn('sort', ['Iterator first', 'Iterator last'], 'void', '排序 [first, last)；可传入比较函数。需要 <algorithm>。'),
  fn('stable_sort', ['Iterator first', 'Iterator last'], 'void', '稳定排序。'),
  fn('reverse', ['Iterator first', 'Iterator last'], 'void', '反转区间。'),
  fn('lower_bound', ['Iterator first', 'Iterator last', 'const T& value'], 'iterator', '已排序区间中第一个不小于 value 的位置。'),
  fn('upper_bound', ['Iterator first', 'Iterator last', 'const T& value'], 'iterator', '已排序区间中第一个大于 value 的位置。'),
  fn('binary_search', ['Iterator first', 'Iterator last', 'const T& value'], 'bool', '在已排序区间中查找值。'),
  fn('min', ['const T& left', 'const T& right'], 'T', '较小值。'), fn('max', ['const T& left', 'const T& right'], 'T', '较大值。'),
  fn('swap', ['T& left', 'T& right'], 'void', '交换两个值。'), fn('abs', ['T value'], 'T', '绝对值。'),
  fn('accumulate', ['Iterator first', 'Iterator last', 'T initial'], 'T', '累计区间；需要 <numeric>。'),
  fn('gcd', ['Integer left', 'Integer right'], 'Integer', '最大公约数；需要 <numeric>。'),
  fn('lcm', ['Integer left', 'Integer right'], 'Integer', '最小公倍数；需要 <numeric>。'),
  fn('next_permutation', ['Iterator first', 'Iterator last'], 'bool', '生成下一个字典序排列。'),
  fn('getline', ['istream& input', 'string& text'], 'istream', '读取一行到字符串。'),
  fn('stoi', ['const string& text'], 'int', '将字符串转换为整数。'),
  fn('stoll', ['const string& text'], 'long long', '将字符串转换为长整数。'),
  fn('to_string', ['T value'], 'string', '将数值转换为字符串。'),
  value('cin', 'istream', '标准输入流；需要 <iostream>。'), value('cout', 'ostream', '标准输出流；需要 <iostream>。'),
  value('endl', 'manipulator', '换行并刷新流。'), value('std', 'std', '标准库命名空间。', { type: 'namespace' })
]
types.std = globals.filter(item => item.label !== 'std').map(item => ({ ...item, static: true }))
export default profile(
  'alignas alignof auto bool break case catch char char8_t char16_t char32_t class concept const consteval constexpr constinit continue co_await co_return co_yield decltype default delete do double else enum explicit export extern false float for friend if inline int long mutable namespace new noexcept nullptr operator private protected public register requires return short signed sizeof static static_assert struct switch template this thread_local throw true try typedef typename union unsigned using virtual void volatile while',
  {
    main: { detail: '完整程序入口', body: '#include <iostream>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n    ${code}\n    return 0;\n}' },
    fori: { detail: '计数循环', body: 'for (int ${i} = 0; ${i} < ${n}; ++${i}) {\n    ${code}\n}' },
    foreach: { detail: '范围循环', body: 'for (auto& ${value} : ${values}) {\n    ${code}\n}' },
    ifelse: { detail: '条件分支', body: 'if (${condition}) {\n    ${code}\n} else {\n    ${other}\n}' },
    function: { detail: '定义函数', body: '${int} ${solve}(${parameters}) {\n    ${code}\n}' }
  }, globals, types
)
