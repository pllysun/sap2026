import { fn, value, type, profile } from '../catalog.js'
const types = {
  list: [fn('append', ['value'], 'None', '在末尾添加元素。'), fn('extend', ['iterable'], 'None', '追加可迭代对象中的元素。'),
    fn('insert', ['index', 'value'], 'None', '在指定位置插入元素。'), fn('pop', ['index=-1'], '$element', '删除并返回元素；默认取最后一个。'),
    fn('remove', ['value'], 'None', '删除第一个匹配元素。'), fn('sort', ['key=None', 'reverse=False'], 'None', '原地排序。'),
    fn('reverse', [], 'None', '原地反转。'), fn('count', ['value'], 'int', '元素出现次数。'), fn('index', ['value'], 'int', '首次出现的下标。'),
    fn('copy', [], 'list', '浅拷贝。'), fn('clear', [], 'None', '清空列表。')],
  str: [fn('split', ['sep=None', 'maxsplit=-1'], 'list[str]', '分割字符串；默认按空白分割。'), fn('strip', ['chars=None'], 'str', '移除两端指定字符；默认移除空白。'),
    fn('join', ['iterable'], 'str', '以当前字符串连接元素。'), fn('replace', ['old', 'new', 'count=-1'], 'str', '替换子串。'),
    fn('find', ['sub'], 'int', '查找子串；未找到返回 -1。'), fn('count', ['sub'], 'int', '子串出现次数。'),
    fn('startswith', ['prefix'], 'bool', '是否以指定前缀开始。'), fn('endswith', ['suffix'], 'bool', '是否以指定后缀结束。'),
    fn('lower', [], 'str', '转换为小写。'), fn('upper', [], 'str', '转换为大写。'), fn('isdigit', [], 'bool', '是否全部为数字字符。'),
    fn('format', ['*args', '**kwargs'], 'str', '格式化字符串。')],
  dict: [fn('get', ['key', 'default=None'], '$value', '取值；键不存在时返回默认值。'), fn('keys', [], 'dict_keys', '键视图。'),
    fn('values', [], 'dict_values', '值视图。'), fn('items', [], 'dict_items', '键值对视图。'), fn('pop', ['key', 'default=None'], '$value', '删除并返回指定键对应的值。'),
    fn('setdefault', ['key', 'default=None'], '$value', '键不存在时设置默认值，并返回对应值。'),
    fn('update', ['other'], 'None', '更新键值对。'), fn('copy', [], 'dict', '浅拷贝。'), fn('clear', [], 'None', '清空字典。')],
  set: [fn('add', ['value'], 'None', '添加元素。'), fn('discard', ['value'], 'None', '删除元素；不存在时不报错。'),
    fn('remove', ['value'], 'None', '删除元素；不存在时抛出 KeyError。'), fn('union', ['other'], 'set', '并集。'),
    fn('intersection', ['other'], 'set', '交集。'), fn('difference', ['other'], 'set', '差集。'),
    fn('issubset', ['other'], 'bool', '是否为子集。'), fn('clear', [], 'None', '清空集合。')],
  tuple: [fn('count', ['value'], 'int', '元素出现次数。'), fn('index', ['value'], 'int', '首次出现的下标。')],
  deque: [fn('append', ['value'], 'None', '在右端添加元素。'), fn('appendleft', ['value'], 'None', '在左端添加元素。'),
    fn('pop', [], '$element', '取出右端元素。'), fn('popleft', [], '$element', '取出左端元素。'),
    fn('extend', ['iterable'], 'None', '在右端追加元素。'), fn('rotate', ['n=1'], 'None', '旋转双端队列。'), fn('clear', [], 'None', '清空队列。')],
  math: [fn('sqrt', ['x'], 'float', '平方根。'), fn('gcd', ['*integers'], 'int', '最大公约数。'), fn('lcm', ['*integers'], 'int', '最小公倍数。'),
    fn('ceil', ['x'], 'int', '向上取整。'), fn('floor', ['x'], 'int', '向下取整。'), fn('isqrt', ['n'], 'int', '整数平方根。'),
    fn('factorial', ['n'], 'int', '阶乘。'), fn('comb', ['n', 'k'], 'int', '组合数。'), value('pi', 'float', '圆周率。'), value('inf', 'float', '正无穷。')],
  heapq: [fn('heappush', ['heap', 'value'], 'None', '向最小堆中加入元素。'), fn('heappop', ['heap'], '$element', '取出最小元素。'),
    fn('heapify', ['values'], 'None', '将列表原地转为最小堆。'), fn('nlargest', ['n', 'iterable'], 'list', '最大的 n 个元素。'), fn('nsmallest', ['n', 'iterable'], 'list', '最小的 n 个元素。')],
  bisect: [fn('bisect_left', ['values', 'value'], 'int', '有序列表中左侧插入位置。'), fn('bisect_right', ['values', 'value'], 'int', '有序列表中右侧插入位置。'),
    fn('insort', ['values', 'value'], 'None', '保持顺序地插入元素。')],
  collections: [type('deque', '双端队列。'), type('Counter', '计数器。'), type('defaultdict', '带默认工厂的字典。')],
  Counter: [], defaultdict: [], List: [], Dict: [], Set: [], Tuple: []
}
types.Counter = [...types.dict, fn('most_common', ['n=None'], 'list', '按频率返回元素及次数。')]
types.defaultdict = types.dict; types.List = types.list; types.Dict = types.dict; types.Set = types.set; types.Tuple = types.tuple
for (const name of ['math', 'heapq', 'bisect', 'collections']) types[name] = types[name].map(item => ({ ...item, static: true }))
export default profile(
  'and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield match case',
  {
    main: { detail: '完整程序入口', body: 'def main():\n    ${code}\n\n\nif __name__ == "__main__":\n    main()' },
    fori: { detail: '计数循环', body: 'for ${i} in range(${n}):\n    ${code}' },
    foreach: { detail: '遍历元素', body: 'for ${value} in ${values}:\n    ${code}' },
    function: { detail: '定义函数', body: 'def ${solve}(${parameters}):\n    ${code}' },
    ifelse: { detail: '条件分支', body: 'if ${condition}:\n    ${code}\nelse:\n    ${other}' },
    while: { detail: 'while 循环', body: 'while ${condition}:\n    ${code}' }
  },
  [
    fn('print', ['*values', 'sep=" "', 'end="\\n"'], 'None', '输出内容。'),
    fn('input', ['prompt=""'], 'str', '读取一行；不包含末尾换行符。'),
    fn('len', ['object'], 'int', '元素或字符数量。'), fn('range', ['start', 'stop', 'step=1'], 'range', '生成整数序列；支持 range(stop)。'),
    fn('sum', ['iterable', 'start=0'], 'number', '累加元素。'),
    fn('min', ['iterable'], '$element', '最小元素；也支持多个参数。'), fn('max', ['iterable'], '$element', '最大元素；也支持多个参数。'),
    fn('sorted', ['iterable', 'key=None', 'reverse=False'], 'list', '返回排序后的新列表。'),
    fn('enumerate', ['iterable', 'start=0'], 'enumerate', '遍历下标和元素。'),
    fn('zip', ['*iterables'], 'zip', '并行遍历多个可迭代对象。'), fn('map', ['function', 'iterable'], 'map', '逐项应用函数。'),
    fn('filter', ['function', 'iterable'], 'filter', '保留符合条件的元素。'), fn('reversed', ['sequence'], 'iterator', '反向遍历。'),
    fn('abs', ['number'], 'number', '绝对值。'), fn('all', ['iterable'], 'bool', '所有元素是否为真。'), fn('any', ['iterable'], 'bool', '是否存在真值元素。'),
    ...['int', 'float', 'str', 'list', 'dict', 'set', 'tuple', 'bool'].map(name => fn(name, ['value'], name, '转换或构造 ' + name + '。')),
    ...['deque', 'Counter', 'defaultdict'].map(name => type(name, '需要从 collections 导入。')),
    ...['math', 'heapq', 'bisect', 'collections'].map(name => value(name, name, '需要 import ' + name + '。', { type: 'namespace' }))
  ], types
)
