import { fn, value, type, profile } from '../catalog.js'
const list = [fn('add', ['E value'], 'boolean', '添加元素；也支持按下标插入。'), fn('get', ['int index'], '$element', '读取指定下标的元素。'),
  fn('set', ['int index', 'E value'], '$element', '替换指定元素。'), fn('size', [], 'int', '元素数量。'),
  fn('isEmpty', [], 'boolean', '是否为空。'), fn('contains', ['Object value'], 'boolean', '是否包含元素。'),
  fn('remove', ['int index'], '$element', '按下标删除；也有按对象删除的重载。'), fn('clear', [], 'void', '清空列表。'),
  fn('indexOf', ['Object value'], 'int', '首次出现的下标；不存在时返回 -1。'), fn('sort', ['Comparator<? super E> comparator'], 'void', '按比较器排序。'),
  fn('toArray', [], 'Object[]', '转换为数组。')]
const map = [fn('put', ['K key', 'V value'], '$value', '保存键值对；返回此前对应的值。'), fn('get', ['Object key'], '$value', '按键取值；不存在时返回 null。'),
  fn('getOrDefault', ['Object key', 'V defaultValue'], '$value', '按键取值；不存在时返回默认值。'),
  fn('containsKey', ['Object key'], 'boolean', '是否包含键。'), fn('containsValue', ['Object value'], 'boolean', '是否包含值。'),
  fn('remove', ['Object key'], '$value', '删除键值对。'), fn('size', [], 'int', '键值对数量。'), fn('isEmpty', [], 'boolean', '是否为空。'),
  fn('clear', [], 'void', '清空映射。'), fn('keySet', [], 'Set', '键的集合。'), fn('values', [], 'Collection', '值的集合。'), fn('entrySet', [], 'Set', '键值对集合。')]
const set = [fn('add', ['E value'], 'boolean', '添加元素。'), fn('contains', ['Object value'], 'boolean', '是否包含元素。'),
  fn('remove', ['Object value'], 'boolean', '删除元素。'), fn('size', [], 'int', '元素数量。'), fn('isEmpty', [], 'boolean', '是否为空。'), fn('clear', [], 'void', '清空集合。')]
const queue = [fn('offer', ['E value'], 'boolean', '添加元素。'), fn('poll', [], '$element', '取出并删除首元素；空时返回 null。'),
  fn('peek', [], '$element', '查看首元素；空时返回 null。'), fn('size', [], 'int', '元素数量。'), fn('isEmpty', [], 'boolean', '是否为空。')]
const types = {
  List: list, ArrayList: list, Collection: [...list.filter(item => !['get', 'set', 'sort'].includes(item.label))],
  LinkedList: [...list, ...queue], Map: map, HashMap: map,
  TreeMap: [...map, fn('firstKey', [], '$element', '最小键。'), fn('lastKey', [], '$element', '最大键。'), fn('floorKey', ['K key'], '$element', '不大于给定键的最大键。')],
  Set: set, HashSet: set, TreeSet: [...set, fn('first', [], '$element', '首元素。'), fn('last', [], '$element', '末元素。')],
  Queue: queue, PriorityQueue: queue, Deque: [...queue, fn('addFirst', ['E value'], 'void', '在首部添加元素。'), fn('addLast', ['E value'], 'void', '在末尾添加元素。'),
    fn('pollFirst', [], '$element', '取出首元素。'), fn('pollLast', [], '$element', '取出末元素。')],
  ArrayDeque: [...queue, fn('push', ['E value'], 'void', '压入栈顶。'), fn('pop', [], '$element', '弹出栈顶。'),
    fn('addFirst', ['E value'], 'void', '在首部添加元素。'), fn('addLast', ['E value'], 'void', '在末尾添加元素。'),
    fn('pollFirst', [], '$element', '取出首元素。'), fn('pollLast', [], '$element', '取出末元素。')],
  Stack: [...list, fn('push', ['E value'], '$element', '压入栈顶。'), fn('pop', [], '$element', '弹出栈顶。'), fn('peek', [], '$element', '查看栈顶。')],
  String: [fn('length', [], 'int', '字符串长度，按 UTF-16 编码单元计数。'), fn('charAt', ['int index'], 'char', '读取指定位置的字符。'),
    fn('substring', ['int beginIndex', 'int endIndex'], 'String', '截取 [beginIndex, endIndex)；也支持单参数重载。'),
    fn('equals', ['Object other'], 'boolean', '比较字符串内容。'), fn('compareTo', ['String other'], 'int', '按字典序比较。'),
    fn('contains', ['CharSequence text'], 'boolean', '是否包含子串。'), fn('indexOf', ['String text'], 'int', '查找子串，未找到返回 -1。'),
    fn('startsWith', ['String prefix'], 'boolean', '是否以指定前缀开始。'), fn('endsWith', ['String suffix'], 'boolean', '是否以指定后缀结束。'),
    fn('trim', [], 'String', '移除两端的低位空白字符。'), fn('split', ['String regex'], 'String[]', '按正则表达式分割。'),
    fn('toCharArray', [], 'char[]', '转换为字符数组。'), fn('isEmpty', [], 'boolean', '是否为空。'),
    fn('toLowerCase', [], 'String', '转换为小写。'), fn('toUpperCase', [], 'String', '转换为大写。'),
    fn('valueOf', ['Object value'], 'String', '转换为字符串。', { static: true })],
  StringBuilder: [fn('append', ['Object value'], 'StringBuilder', '追加内容。'), fn('reverse', [], 'StringBuilder', '反转内容。'),
    fn('toString', [], 'String', '转换为字符串。'), fn('length', [], 'int', '字符数量。'), fn('charAt', ['int index'], 'char', '读取字符。'),
    fn('setCharAt', ['int index', 'char value'], 'void', '修改字符。'), fn('deleteCharAt', ['int index'], 'StringBuilder', '删除字符。')],
  Scanner: [fn('nextInt', [], 'int', '读取下一个整数。'), fn('nextLong', [], 'long', '读取下一个长整数。'), fn('nextDouble', [], 'double', '读取下一个浮点数。'),
    fn('next', [], 'String', '读取下一个非空白单词。'), fn('nextLine', [], 'String', '读取当前行剩余内容。'),
    fn('hasNext', [], 'boolean', '是否还有输入。'), fn('hasNextInt', [], 'boolean', '下一个输入是否为整数。')],
  BufferedReader: [fn('readLine', [], 'String', '读取一行；末尾返回 null，可能抛出 IOException。'), fn('read', [], 'int', '读取字符；末尾返回 -1。')],
  PrintStream: [fn('print', ['Object value'], 'void', '输出内容。'), fn('println', ['Object value'], 'void', '输出内容并换行；也支持无参数重载。'),
    fn('printf', ['String format', 'Object... values'], 'PrintStream', '格式化输出。')],
  array: [value('length', 'int', '数组长度；这是字段，不是方法。')],
  Math: [fn('max', ['T left', 'T right'], 'T', '较大值。', { static: true }), fn('min', ['T left', 'T right'], 'T', '较小值。', { static: true }),
    fn('abs', ['T value'], 'T', '绝对值。', { static: true }), ...['sqrt', 'floor', 'ceil'].map(name => fn(name, ['double value'], 'double', '数学函数。', { static: true })),
    fn('round', ['double value'], 'long', '取最接近的整数。', { static: true }),
    fn('pow', ['double base', 'double exponent'], 'double', '计算乘方。', { static: true }), value('PI', 'double', '圆周率。', { static: true })],
  Arrays: [fn('sort', ['T[] array'], 'void', '对数组排序。', { static: true }), fn('binarySearch', ['T[] array', 'T key'], 'int', '在已排序数组中查找。', { static: true }),
    fn('fill', ['T[] array', 'T value'], 'void', '填充数组。', { static: true }), fn('copyOf', ['T[] array', 'int length'], 'T[]', '复制数组。', { static: true }),
    fn('toString', ['T[] array'], 'String', '数组内容的字符串表示。', { static: true })],
  Collections: [fn('sort', ['List<T> list'], 'void', '按自然顺序排序。', { static: true }), fn('reverse', ['List<?> list'], 'void', '反转列表。', { static: true }),
    fn('min', ['Collection<T> values'], 'T', '最小元素。', { static: true }), fn('max', ['Collection<T> values'], 'T', '最大元素。', { static: true })],
  System: [value('out', 'PrintStream', '标准输出流。', { static: true }), value('in', 'InputStream', '标准输入流。', { static: true }),
    fn('arraycopy', ['Object source', 'int sourcePos', 'Object destination', 'int destinationPos', 'int length'], 'void', '复制数组片段。', { static: true })]
}
for (const [name, primitive] of [['Integer', 'int'], ['Long', 'long'], ['Double', 'double']]) {
  types[name] = [fn('parse' + (name === 'Integer' ? 'Int' : name), ['String text'], primitive, '解析数值。', { static: true }),
    fn('valueOf', ['String text'], name, '解析并装箱。', { static: true }), fn('compare', [primitive + ' left', primitive + ' right'], 'int', '比较两个数值。', { static: true }),
    fn('toString', [], 'String', '数值的字符串表示。'), value('MAX_VALUE', primitive, '最大值。', { static: true }), value('MIN_VALUE', primitive, '最小值。', { static: true })]
}
export default profile(
  'abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new package private protected public record return sealed non-sealed short static strictfp super switch synchronized this throw throws transient try var void volatile while yield permits true false null',
  {
    main: { detail: '完整程序入口', body: 'public class Main {\n    public static void main(String[] args) {\n        ${code}\n    }\n}' },
    fori: { detail: '计数循环', body: 'for (int ${i} = 0; ${i} < ${n}; ${i}++) {\n    ${code}\n}' },
    foreach: { detail: '遍历集合', body: 'for (${int} ${value} : ${values}) {\n    ${code}\n}' },
    ifelse: { detail: '条件分支', body: 'if (${condition}) {\n    ${code}\n} else {\n    ${other}\n}' },
    method: { detail: '定义方法', body: 'public ${int} ${solve}(${parameters}) {\n    ${code}\n}' }
  },
  Object.keys(types).filter(name => name !== 'array').map(name => type(name, ['String', 'StringBuilder', 'System', 'Math', 'Integer', 'Long', 'Double'].includes(name) ? 'java.lang.' + name : name === 'BufferedReader' ? '需要 import java.io.BufferedReader。' : '需要对应的 java.util 或 java.io 导入。')),
  types
)
