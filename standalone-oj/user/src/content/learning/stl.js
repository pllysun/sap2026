// Adapted from No-Boundry/acm-trainer, MIT © 2026 No-Boundry.
// Source revision: 4295a47a913f1ebbce1c108f23ea493fbadfb946; see public/licenses/acm-trainer.txt.
// Examples revised for type correctness and safe empty-container access.
export const STL_ENTRIES = [
  {
    "id": "vector",
    "category": "sequence",
    "name": "std::vector",
    "summary": "动态数组，适合需要按下标访问的数据；随机访问、尾部增删、缓存友好。",
    "code": "vector<int> v;              // 空\nvector<int> a(n, 0);        // n 个 0\nvector<vector<int>> g(n);   // 邻接表\nv.push_back(x);             // 尾插 O(1) 均摊\nv.emplace_back(x);          // 原地构造一个 int\nv.pop_back();\nv.size(); v.empty(); v.clear();\nif (!v.empty()) { v.front(); v.back(); } // 访问前检查\nv.resize(n);\nsort(v.begin(), v.end());   // 排序 O(n log n)\nv.reserve(1 << 20);         // 预分配，避免多次扩容",
    "complexity": "尾插/尾删 O(1) 均摊；随机访问 O(1)；任意位置插删 O(n)",
    "pitfalls": "扩容会使全部迭代器/指针失效；vector<bool> 是位压缩特化，不能取地址。",
    "url": "https://en.cppreference.com/w/cpp/container/vector"
  },
  {
    "id": "deque",
    "category": "sequence",
    "name": "std::deque",
    "summary": "双端队列容器，两端 O(1) 插删；也常用来替代 vector 实现 0-1 BFS。",
    "code": "deque<int> dq;\ndq.push_back(x); dq.push_front(y);\ndq.pop_back();   dq.pop_front();\nif (!dq.empty()) { dq.front(); dq.back(); }",
    "complexity": "两端插删 O(1)；随机访问 O(1)（常数略大于 vector）",
    "pitfalls": "内存不连续，遍历比 vector 慢；中间插删仍是 O(n)。",
    "url": "https://en.cppreference.com/w/cpp/container/deque"
  },
  {
    "id": "array",
    "category": "sequence",
    "name": "std::array",
    "summary": "定长数组容器，避免 C 数组退化为指针，支持 size()/begin()/end()。",
    "code": "array<int, 5> a{};          // 全 0\na.fill(-1);\nsort(a.begin(), a.end());\nfor (int x : a) cout << x;",
    "complexity": "随机访问 O(1)；无动态分配",
    "url": "https://en.cppreference.com/w/cpp/container/array"
  },
  {
    "id": "string",
    "category": "sequence",
    "name": "std::string",
    "summary": "字符串容器：拼接、查找、截取、替换、数值转换。",
    "code": "string s = \"hello\";\ns += \" world\";\ns.substr(1, 3);              // \"ell\"\ns.find(\"world\");             // 下标或 npos\ns.replace(0, 5, \"hi\");\nstoi(\"42\"); stoll(\"1000000000000000000\");   // 字符串 → 数值\nto_string(123);              // 数值 → 字符串\ngetline(cin, s);             // 读整行（可与 cin >> 混用）",
    "complexity": "尾部追加 O(1) 均摊；find O(n·m) 朴素实现",
    "pitfalls": "cin >> 后紧接 getline 会读到空行，需先 cin.ignore()；npos 是 size_t，勿与 int 比较。",
    "url": "https://en.cppreference.com/w/cpp/string/basic_string"
  },
  {
    "id": "stack",
    "category": "adapter",
    "name": "std::stack",
    "summary": "后进先出；括号匹配、表达式求值、单调栈的底层容器。",
    "code": "stack<int> st;\nst.push(x);\nif (!st.empty()) { int value = st.top(); st.pop(); }\nst.size(); st.empty();",
    "complexity": "push/pop/top O(1)",
    "pitfalls": "不提供遍历与迭代器；pop() 不返回元素，需先 top()。",
    "url": "https://en.cppreference.com/w/cpp/container/stack"
  },
  {
    "id": "queue",
    "category": "adapter",
    "name": "std::queue",
    "summary": "先进先出；BFS、模拟队列。",
    "code": "queue<int> q;\nq.push(x);\nif (!q.empty()) { int first = q.front(); int last = q.back(); q.pop(); }",
    "complexity": "push/pop/front O(1)",
    "url": "https://en.cppreference.com/w/cpp/container/queue"
  },
  {
    "id": "priority_queue",
    "category": "adapter",
    "name": "std::priority_queue",
    "summary": "大顶堆（默认）/小顶堆；贪心、Dijkstra、动态取最值。",
    "code": "priority_queue<int> mx;                                   // 大顶堆\npriority_queue<int, vector<int>, greater<int>> mn;        // 小顶堆\npriority_queue<pair<int,int>, vector<pair<int,int>>, greater<>> pq; // 按 first 最小\nstruct Node { int d, u; };\nstruct Cmp { bool operator()(const Node& a, const Node& b) const { return a.d > b.d; } };\npriority_queue<Node, vector<Node>, Cmp> pq2;              // 自定义（注意是\"反着写\"）\n\nmx.push(x); mx.top(); mx.pop(); mx.empty();",
    "complexity": "push/pop O(log n)；top O(1)",
    "pitfalls": "比较器为\"优先级更低者返回 true\"；大顶堆取最小需存负数或用 greater。",
    "url": "https://en.cppreference.com/w/cpp/container/priority_queue"
  },
  {
    "id": "set",
    "category": "associative",
    "name": "std::set / multiset",
    "summary": "有序集合（红黑树）：插入、删除、查找、前驱后继 O(log n)。",
    "code": "set<int> s;\ns.insert(3); s.erase(3);\ns.count(3);                       // 0 / 1\nauto it = s.lower_bound(3);       // ≥ 3 的第一个（用成员函数，O(log n)）\nauto it2 = s.upper_bound(3);      // > 3\nif (it != s.end()) cout << *it;\nif (!s.empty()) s.erase(s.begin());               // 按迭代器删（multiset 常用）\nmultiset<int> ms;\nauto it3 = ms.find(5);\nif (it3 != ms.end()) ms.erase(it3); // 只删一个 5（erase(5) 会删全部）",
    "complexity": "insert/erase/find/lower_bound O(log n)",
    "pitfalls": "multiset 用 erase(key) 会删除所有相等元素；遍历时删除须用迭代器并接收返回值。",
    "url": "https://en.cppreference.com/w/cpp/container/set"
  },
  {
    "id": "map",
    "category": "associative",
    "name": "std::map / multimap",
    "summary": "有序键值表：计数、离散化、区间统计、前驱后继。",
    "code": "map<string, int> cnt;\ncnt[\"abc\"]++;                  // 不存在则插入 0 再自增\nauto it = cnt.find(\"abc\");     // 别用 cnt[\"x\"] 判断存在（会插入！）\nif (it != cnt.end()) cout << it->second;\nfor (auto& [k, v] : cnt) cout << k << ' ' << v;   // C++17 结构化绑定\nif (it != cnt.end()) cnt.erase(it);",
    "complexity": "增删查 O(log n)",
    "pitfalls": "operator[] 会插入默认值，判存在请用 find/count；键为自定义类型需提供比较器。",
    "url": "https://en.cppreference.com/w/cpp/container/map"
  },
  {
    "id": "unordered",
    "category": "associative",
    "name": "std::unordered_set / unordered_map",
    "summary": "哈希表：均摊 O(1) 查找，适合大量计数与判重。",
    "code": "unordered_map<long long, int> mp;\nmp[x]++; mp.reserve(1024);     // 预留桶，减少 rehash\nmp.max_load_factor(0.7);\nunordered_set<int> vis; vis.insert(x); vis.count(x);",
    "complexity": "均摊 O(1)，最坏 O(n)（哈希碰撞）",
    "pitfalls": "特殊数据可能造成较多哈希冲突；需要稳定性能时可选择 map，或使用经过验证的哈希函数。",
    "url": "https://en.cppreference.com/w/cpp/container/unordered_map"
  },
  {
    "id": "pair",
    "category": "misc",
    "name": "std::pair / tuple",
    "summary": "二元组/多元组；自带字典序比较，排序与去重都靠它。",
    "code": "pair<int,int> p = {1, 2};\np.first; p.second;\nvector<pair<int,int>> v;\nsort(v.begin(), v.end());                  // 先 first 再 second\nv.push_back({x, y});\nauto [a, b] = p;                           // C++17 结构化绑定\ntuple<int,int,int> t{1,2,3};\nauto [x1, x2, x3] = t;\nget<0>(t);",
    "complexity": "比较 O(1)（按字典序）",
    "url": "https://en.cppreference.com/w/cpp/utility/pair"
  },
  {
    "id": "bitset",
    "category": "misc",
    "name": "std::bitset",
    "summary": "定长位集合：位运算加速、可达性/背包状压、集合交并。",
    "code": "bitset<100000> bs;\nbs.set(); bs.reset(); bs.flip();\nbs[3] = 1; bs.count();          // 1 的个数\nbs |= (bs << k);                // 背包可达性整体左移\n(bs & bs2).count();\nbs.to_string();\n// to_ulong() 仅适合值能放进 unsigned long 的情况，否则会抛异常。",
    "complexity": "位运算 O(n/64)",
    "pitfalls": "长度必须是编译期常量；大 bitset 每次操作 O(n/64)，注意总复杂度。",
    "url": "https://en.cppreference.com/w/cpp/utility/bitset"
  },
  {
    "id": "numeric-misc",
    "category": "misc",
    "name": "数值与类型工具",
    "summary": "数值类型、极值、溢出与浮点常用写法。",
    "code": "#include <climits>   // INT_MAX、INT_MIN、LLONG_MAX\n#include <numeric>   // gcd、lcm\n#include <bit>       // C++20 popcount；本站 C++23 可用\n\nlong long limit = 1000000000000000000LL;\nlong long a = 12, b = 18;\nlong long g = gcd(a, b);           // 6\nlong long common = lcm(a, b);      // 36；大数时注意溢出\nunsigned int bits = 13;\nint ones = std::popcount(bits);   // 3\n// GCC 扩展 __int128 可存更大的整数，但要自行实现输入输出。\n// __builtin_clz/ctz 在参数为 0 时未定义，调用前须检查。",
    "complexity": "内置位操作通常 O(1)",
    "pitfalls": "int 溢出是 UB（务必用 long long）；浮点比较用 eps；__int128 需自己写输入输出。",
    "url": "https://en.cppreference.com/w/cpp/numeric/gcd"
  },
  {
    "id": "sort",
    "category": "algorithm",
    "name": "sort / stable_sort / partial_sort",
    "summary": "排序与部分排序；自定义比较器、多关键字排序。",
    "code": "sort(v.begin(), v.end());                 // int 数组升序\nsort(v.begin(), v.end(), greater<int>());  // 降序\nstruct Record { int x, y; };\nvector<Record> records;\nsort(records.begin(), records.end(), [](const Record& a, const Record& b) {\n    if (a.x != b.x) return a.x < b.x;\n    return a.y > b.y;                     // 同 x 时按 y 降序\n});\nstable_sort(v.begin(), v.end());          // 相等元素保留原先次序\n// 前提：0 <= k <= v.size()\npartial_sort(v.begin(), v.begin() + k, v.end());",
    "complexity": "sort O(n log n)；stable_sort 缓冲充分时 O(n log n)，否则可达 O(n log² n)",
    "pitfalls": "比较器必须满足严格弱序（不能用 <=，否则 UB/崩溃）。",
    "url": "https://en.cppreference.com/w/cpp/algorithm/sort"
  },
  {
    "id": "binary-search",
    "category": "algorithm",
    "name": "二分查找与手写二分",
    "summary": "有序区间查找、答案二分（练习中也可自行实现）。",
    "code": "// 库函数：返回第一个 ≥ x / > x 的位置\nauto lowerIt = lower_bound(v.begin(), v.end(), x);\nauto upperIt = upper_bound(v.begin(), v.end(), x);\nbinary_search(v.begin(), v.end(), x);            // 是否存在\n\n// 答案二分模板（最大可行值）\nlong long lo = 0, hi = 1000000000000000000LL;\nwhile (lo < hi) {\n  long long mid = lo + (hi - lo + 1) / 2;\n  if (check(mid)) lo = mid; else hi = mid - 1;\n}\n// 最小可行值：mid = lo + (hi - lo) / 2，可行则 hi = mid，否则 lo = mid + 1",
    "complexity": "库函数 O(log n)",
    "pitfalls": "区间需先排序；mid 用 lo + (hi - lo)/2 防溢出；注意边界取整方向。",
    "url": "https://en.cppreference.com/w/cpp/algorithm/lower_bound"
  },
  {
    "id": "unique-erase",
    "category": "algorithm",
    "name": "unique + erase（去重）",
    "summary": "相邻去重（须先排序）压缩为不重复序列。",
    "code": "sort(v.begin(), v.end());\nv.erase(unique(v.begin(), v.end()), v.end());\nint m = v.size();                       // 去重后个数\n\n// 离散化（值域大 → 下标）\nauto b = v; sort(b.begin(), b.end());\nb.erase(unique(b.begin(), b.end()), b.end());\nfor (auto& x : v) x = lower_bound(b.begin(), b.end(), x) - b.begin() + 1;",
    "complexity": "O(n)（不含排序）",
    "pitfalls": "unique 只去相邻重复，必须先排序；返回的是新逻辑尾迭代器。",
    "url": "https://en.cppreference.com/w/cpp/algorithm/unique"
  },
  {
    "id": "permutation",
    "category": "algorithm",
    "name": "next_permutation / prev_permutation",
    "summary": "按字典序枚举全排列或下一个排列。",
    "code": "sort(a.begin(), a.end());\ndo {\n  // 处理当前排列 a\n} while (next_permutation(a.begin(), a.end()));",
    "complexity": "单次 O(n)，全枚举 O(n·n!)",
    "pitfalls": "必须先排序才能枚举全部排列；n > 10 时全排列数量爆炸。",
    "url": "https://en.cppreference.com/w/cpp/algorithm/next_permutation"
  },
  {
    "id": "minmax",
    "category": "algorithm",
    "name": "min / max / minmax_element / clamp",
    "summary": "最值与区间最值位置；也常用于初始化。",
    "code": "min(a, b); max({a, b, c});          // 多值取最大\nauto p = minmax_element(v.begin(), v.end());\nif (p.first != v.end()) { *p.first; *p.second; }                // 最小、最大\nclamp(x, lo, hi);                   // C++17 把 x 限制在区间内\nif (!v.empty()) { int best = *max_element(v.begin(), v.end()); }",
    "complexity": "minmax_element O(n)；其余 O(1)（多值取 max 为 O(k)）",
    "url": "https://en.cppreference.com/w/cpp/algorithm/clamp"
  },
  {
    "id": "accumulate",
    "category": "algorithm",
    "name": "accumulate / iota / partial_sum",
    "summary": "求和、连续赋值、前缀和（<numeric>）。",
    "code": "long long s = accumulate(v.begin(), v.end(), 0LL);   // 注意初值类型！\niota(v.begin(), v.end(), 1);            // 1,2,3,...\npartial_sum(v.begin(), v.end(), pre.begin());  // 前缀和\n\nlong long prod = accumulate(v.begin(), v.end(), 1LL, multiplies<long long>());",
    "complexity": "O(n)",
    "pitfalls": "初值写 0（int）会导致大和溢出；需 0LL。",
    "url": "https://en.cppreference.com/w/cpp/algorithm/accumulate"
  },
  {
    "id": "count-find",
    "category": "algorithm",
    "name": "count / count_if / find / find_if",
    "summary": "计数与查找（线性）。",
    "code": "count(v.begin(), v.end(), x);\ncount_if(v.begin(), v.end(), [](int y) { return y % 2 == 0; });\nauto it = find(v.begin(), v.end(), x);\nif (it == v.end()) { /* 未找到 */ }\nany_of(v.begin(), v.end(), pred);\nall_of(v.begin(), v.end(), pred);\nnone_of(v.begin(), v.end(), pred);",
    "complexity": "O(n)",
    "url": "https://en.cppreference.com/w/cpp/algorithm/find"
  },
  {
    "id": "transform-fill",
    "category": "algorithm",
    "name": "fill / generate / transform / reverse / rotate",
    "summary": "批量赋值、生成、映射、翻转、旋转。",
    "code": "fill(v.begin(), v.end(), 0);\ngenerate(v.begin(), v.end(), [n = 0]() mutable { return n++; });\ntransform(v.begin(), v.end(), v.begin(), [](int x) { return x * 2; });\nreverse(v.begin(), v.end());\nrotate(v.begin(), v.begin() + k, v.end());   // 左旋 k 位",
    "complexity": "O(n)",
    "url": "https://en.cppreference.com/w/cpp/algorithm/transform"
  },
  {
    "id": "remove-erase",
    "category": "algorithm",
    "name": "remove / remove_if + erase",
    "summary": "按条件删除元素（erase-remove 惯用法）。",
    "code": "v.erase(remove(v.begin(), v.end(), 0), v.end());\nv.erase(remove_if(v.begin(), v.end(), [](int x) { return x < 0; }), v.end());",
    "complexity": "O(n)",
    "pitfalls": "remove 不改变容器大小，必须配合 erase。",
    "url": "https://en.cppreference.com/w/cpp/algorithm/remove"
  },
  {
    "id": "set-ops",
    "category": "algorithm",
    "name": "merge / set_union / set_intersection",
    "summary": "有序区间合并与集合运算（结果也是有序区间）。",
    "code": "merge(a.begin(), a.end(), b.begin(), b.end(), back_inserter(c));\nset_union(a.begin(), a.end(), b.begin(), b.end(), back_inserter(c));\nset_intersection(a.begin(), a.end(), b.begin(), b.end(), back_inserter(c));\nset_difference(a.begin(), a.end(), b.begin(), b.end(), back_inserter(c));",
    "complexity": "O(n + m)",
    "pitfalls": "要求输入已排序；输出需预留空间或用 back_inserter。",
    "url": "https://en.cppreference.com/w/cpp/algorithm/set_union"
  },
  {
    "id": "tip-iterator-invalidation",
    "category": "tips",
    "name": "迭代器/引用失效",
    "summary": "容器修改后旧迭代器可能失效，是容器使用中需要留意的隐性错误。",
    "code": "// vector：扩容后全部失效；插入/删除点之后失效\nfor (auto it = v.begin(); it != v.end(); ) {\n  if (bad(*it)) it = v.erase(it);   // erase 返回下一个有效迭代器\n  else ++it;\n}\n// map/set：仅被删元素失效，其余仍有效\nfor (auto it = mp.begin(); it != mp.end(); ) {\n  if (bad(*it)) it = mp.erase(it);\n  else ++it;\n}",
    "pitfalls": "vector 里保存元素指针/引用后再 push_back 极易悬空；先 reserve 或改用 deque/list。",
    "url": "https://en.cppreference.com/w/cpp/container"
  },
  {
    "id": "tip-comparator",
    "category": "tips",
    "name": "自定义比较器要点",
    "summary": "sort/set/map/priority_queue 的比较器语义不同，写错会 UB 或行为异常。",
    "code": "// sort / set / map：返回 a 是否\"排在 b 前面\"（严格弱序，禁止用 <=）\nstruct Cmp { bool operator()(int a, int b) const { return a < b; } };\n// priority_queue：返回 a 的优先级是否\"低于\" b（即堆顶是返回 false 的那个）\nstruct CmpPQ { bool operator()(int a, int b) const { return a > b; } };  // 小顶堆",
    "pitfalls": "比较器必须是 const 成员；含浮点比较要小心 NaN；状态依赖全局变量会导致顺序不确定。",
    "url": "https://en.cppreference.com/w/cpp/named_req/Compare"
  },
  {
    "id": "tip-fast-io",
    "category": "tips",
    "name": "快速 I/O 与输入解析",
    "summary": "大数据量下 cin/cout 会 TLE，需要关闭同步或用 scanf。",
    "code": "int main() {\n  ios::sync_with_stdio(false);\n  cin.tie(nullptr);\n  // ...\n}\n// 按行/按分隔符解析\nstring line; getline(cin, line);\nstringstream ss(line);\nint x; while (ss >> x) { /* ... */ }\n// 读到 EOF\nwhile (cin >> x) { /* ... */ }",
    "pitfalls": "关闭同步后不要再混用 scanf/printf 与 cin/cout；endl 会 flush，输出量大时用 '\\n'。",
    "url": "https://en.cppreference.com/w/cpp/io/cout"
  },
  {
    "id": "tip-io-format",
    "category": "tips",
    "name": "格式化输出",
    "summary": "保留小数、补零、对齐；输出格式与题目要求不符也会得到答案错误。",
    "code": "printf(\"%.10f\\n\", ans);            // 保留 10 位小数（推荐多打 2 位）\ncout << fixed << setprecision(10) << ans << '\\n';\nprintf(\"%lld\\n\", (long long)x);      // 注意类型必须匹配\nprintf(\"%02d:%02d\\n\", h, m);\n// 输出行末空格通常无害（多数 OJ 忽略行尾空白）",
    "complexity": "",
    "pitfalls": "浮点误差：一般多输出 1–2 位小数；%lld 与 int 混用会输出垃圾。"
  },
  {
    "id": "tip-discretization",
    "category": "tips",
    "name": "离散化与坐标压缩",
    "summary": "值域过大时把出现过的值映射为连续下标，常用 map 或排序去重。",
    "code": "// 方式一：排序去重 + lower_bound（见 unique/erase 条目）\n// 方式二：map 边用边编号\nmap<int, int> id;\nauto getId = [&](int x) {\n  auto it = id.find(x);\n  if (it != id.end()) return it->second;\n  int nid = (int)id.size() + 1;\n  id[x] = nid;\n  return nid;\n};",
    "pitfalls": "按出现顺序编号只能保持相等关系，不能保持大小关系；区间统计、树状数组等需要按值排序后离散化。"
  },
  {
    "id": "tip-stl-tree",
    "category": "tips",
    "name": "__gnu_pbds 平衡树（GNU 扩展）",
    "summary": "需要 order_of_key / find_by_order（第 k 小）时的现成平衡树。",
    "code": "#include <ext/pb_ds/assoc_container.hpp>\n#include <ext/pb_ds/tree_policy.hpp>\nusing namespace __gnu_pbds;\ntemplate <class T> using oset =\n  tree<T, null_type, less<T>, rb_tree_tag, tree_order_statistics_node_update>;\n\noset<int> s;\ns.insert(x); s.erase(x);\ns.order_of_key(x);        // 比 x 小的个数（排名，0-based）\nif (k < s.size()) { int value = *s.find_by_order(k); }      // 第 k 小（0-based）",
    "complexity": "各操作 O(log n)",
    "pitfalls": "仅 GCC/MinGW 可用；不允许多重集合直接插入重复值（可存 pair 加唯一编号）。"
  },
  {
    "id": "tip-string-tricks",
    "category": "tips",
    "name": "字符串常用技巧",
    "summary": "回文判断、分割、字符转换、子串计数等。",
    "code": "string s = \"abcba\";\nstring r(s.rbegin(), s.rend());\nbool pal = (s == r);\ns.find(t) != string::npos;          // 子串存在\nfor (char& c : s) c = static_cast<char>(toupper(static_cast<unsigned char>(c)));\ns.append(3, 'x');                   // 追加 3 个 'x'\ns.compare(0, 3, \"abc\") == 0;        // 前缀比较\ncount(s.begin(), s.end(), 'a');     // 字符计数",
    "pitfalls": "toupper 参数先转 unsigned char，避免负 char 导致未定义行为；中文按多字节存储，不能把一个字节当作完整字符。"
  },
  {
    "id": "tip-bitset-knapsack",
    "category": "tips",
    "name": "bitset 优化 DP（可达性/子集和）",
    "summary": "把布尔 DP 压成位运算，复杂度除以 64。",
    "code": "bitset<100005> dp;\ndp[0] = 1;\nfor (int w : weights) dp |= (dp << w);\ncout << dp.count() << '\\n';         // 可达和的个数\n// 判断某和是否可达\nif (dp[target]) { /* ... */ }",
    "complexity": "O(n·S/64)",
    "pitfalls": "S 必须是编译期常量；dp 左移后越界位会被丢弃（这正是所需）。"
  },
  {
    "id": "tip-multiset-multimap",
    "category": "tips",
    "name": "multiset / multimap 正确删除",
    "summary": "按值删除会删掉所有相等元素，通常需要按迭代器删。",
    "code": "multiset<int> ms{1, 2, 2, 3};\nms.erase(2);                      // 删除全部 2\nauto it = ms.find(2);\nif (it != ms.end()) ms.erase(it);  // 只删一个\nauto pos = ms.lower_bound(3);\nif (pos != ms.end()) ms.erase(pos);       // 删第一个 ≥ 3 的元素",
    "pitfalls": "想删\"某一个\"时务必用迭代器；删除后原迭代器失效，需重新获取。"
  },
  {
    "id": "tip-graph-storage",
    "category": "tips",
    "name": "图的 STL 存储写法",
    "summary": "邻接表用 vector 数组；带权图用 pair；边表用于 Kruskal/Bellman-Ford。",
    "code": "int n, m; cin >> n >> m;\nvector<vector<int>> g(n + 1);                          // 无权图\nvector<vector<pair<int,int>>> gw(n + 1);               // 带权图 (to, w)\nfor (int i = 0; i < m; i++) {\n  int u, v, w; cin >> u >> v >> w;\n  gw[u].push_back({v, w});\n  gw[v].push_back({u, w});                             // 无向图\n}\nstruct Edge { int u, v, w; bool operator<(const Edge& o) const { return w < o.w; } };\nvector<Edge> edges(m);                                 // Kruskal 边表",
    "pitfalls": "1-based 下标记得开 n+1；稠密图邻接矩阵可能爆内存（n^2）。"
  },
  {
    "id": "tip-common-functions",
    "category": "tips",
    "name": "常用辅助函数",
    "summary": "取模、快速幂、快读、随机等高频手写工具。",
    "code": "const long long MOD = 1e9 + 7;\nlong long add(long long a, long long b) { return (a + b) % MOD; }\nlong long mul(long long a, long long b) { return a * b % MOD; }\n\nlong long qpow(long long a, long long e, long long mod) {\n  long long r = 1 % mod; a %= mod;\n  while (e) { if (e & 1) r = r * a % mod; a = a * a % mod; e >>= 1; }\n  return r;\n}\nlong long inv(long long a, long long mod) { return qpow(a, mod - 2, mod); }  // mod 为素数\n\nmt19937 rng(chrono::steady_clock::now().time_since_epoch().count());\nshuffle(v.begin(), v.end(), rng);   // 防被卡（随机化）",
    "pitfalls": "示例乘法要求 0 <= a,b < mod <= 10^9+7，指数非负，模数正数；较大模数需 __int128 防乘法溢出。逆元要求 mod 为素数且 a 不是 mod 的倍数。"
  }
]

export const STL_CATEGORIES = [
  {
    "key": "sequence",
    "label": "序列容器",
    "hint": "vector / deque / string / array"
  },
  {
    "key": "adapter",
    "label": "容器适配器",
    "hint": "stack / queue / priority_queue"
  },
  {
    "key": "associative",
    "label": "关联容器",
    "hint": "set / map / unordered_*"
  },
  {
    "key": "misc",
    "label": "其他常用",
    "hint": "pair / tuple / bitset / 数值"
  },
  {
    "key": "algorithm",
    "label": "算法库",
    "hint": "<algorithm> <numeric> 高频函数"
  },
  {
    "key": "tips",
    "label": "技巧与坑",
    "hint": "常用写法与注意事项"
  }
]
