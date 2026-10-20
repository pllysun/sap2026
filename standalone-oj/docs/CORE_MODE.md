# 核心函数模式与默认标准库

选择“核心函数”后，只需实现题目模板规定的函数或 Solution 类。平台负责输入解析、参数构造、调用和输出；常用标准库由后端统一加入，公开样例运行、正式提交、题目五语言三轮验证及题单都使用同一处理流程。完整程序模式仍自行引包与处理输入输出。

## 默认提供的内容

| 语言 | 标准库与常用名称 |
| --- | --- |
| C | stdio、stdlib、string、math、stdbool、stdint、stddef、limits、float、ctype、inttypes、assert；可直接使用 malloc、qsort、abs、strlen、sqrt 等 |
| C++ | bits/stdc++.h 和 using namespace std；可直接使用 vector、string、map、unordered_map、set、queue、priority_queue、sort 等 |
| Java | java.util、java.io、java.math、java.time、java.util.function、java.util.stream；可直接使用 List、ArrayList、HashMap、Arrays、BigInteger、BigDecimal、Stream 等 |
| Python | sys、math、collections、itertools、functools、heapq、bisect、statistics、operator、decimal、fractions、re、copy、typing；并直接提供 List、Optional、Dict、Set、Tuple、Deque、Any、Iterable、Iterator、Counter、defaultdict、deque、OrderedDict、排列组合、累加、缓存、堆、二分、常见数学函数、Decimal、Fraction、deepcopy 等 |
| Rust | HashMap、HashSet、BTreeMap、BTreeSet、BinaryHeap、VecDeque、LinkedList；min、max、Ordering、Reverse；io、Read、Write、BufRead；once、repeat、swap、replace、take。Vec、String、Option、Result 等继续来自 Rust 自带 prelude |

只提供工具链中已有的标准库，不下载第三方依赖。现有显式引包仍然支持；无需批量改写已有题目模板、参考实现或测试集。Rust 使用可避让显式导入和本地定义的 glob prelude，避免重复导入报错；crate 级属性保留在文件开头。

Python 在编译沙箱中用 AST 插入默认导入，保留模块文档字符串与合法 __future__ 导入的位置，检查语法后输出受控的运行产物。该步骤不执行提交代码，运行阶段继续使用同一个沙箱和资源限制。用户草稿、提交记录与题解中的原始代码和注释不被改写。

## 出题约定

函数驱动仍需唯一的 __USER_CODE__ 标记，规定函数签名、数据类型、数组长度、序列化协议与内存归属。默认引包不会替代驱动，也不会自动猜测参数。例如 C 的动态返回数组仍需由驱动正确释放。

新增或修改题目时，五种语言、每个支持模式和全部用例都完成三轮验证。测试需覆盖不写引包的实现、已有显式引包的兼容实现，以及 Python 类型标注等语言特性。不要通过停用某种语言绕过验证。

## 题面 Markdown

题目描述、输入格式、输出格式与数据范围支持 Markdown：标题、粗体、列表、引用、表格、链接、图片、行内代码与围栏代码块。C、C++、Java、Python、Rust 代码块按标签高亮。图片按需加载；宽表格与代码块在内容区内横向滚动，手机页面不会被撑宽。

上下标可用安全的 sup / sub 标签。内容经过 HTML 白名单净化；脚本、事件属性、危险链接、内联样式及未经允许的元素不会进入页面。测试输入与期望输出继续按原始文本显示，保证复制内容不会因 Markdown 解析改变。
