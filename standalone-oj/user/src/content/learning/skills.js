// Adapted from No-Boundry/acm-trainer, MIT © 2026 No-Boundry.
// Source revision: 4295a47a913f1ebbce1c108f23ea493fbadfb946; see public/licenses/acm-trainer.txt.
export const SKILL_NODES = [
  {
    "id": "math_basic",
    "name": "基础数学",
    "layer": 0,
    "tags": [
      "math",
      "数学",
      "最大公约数",
      "最小公倍数",
      "质因数分解",
      "素性测试",
      "素数筛法",
      "欧几里得算法",
      "扩展欧几里得算法",
      "裴蜀定理",
      "抽屉原理"
    ],
    "dependsOn": [],
    "intro": "整除、素数、最大公约数与同余等基本概念贯穿几乎所有算法题；先掌握这些数论工具，再向进阶数论专题迈进。"
  },
  {
    "id": "impl",
    "name": "模拟与实现",
    "layer": 0,
    "tags": [
      "implementation",
      "模拟"
    ],
    "dependsOn": [
      "io_basics",
      "loop_basics"
    ],
    "intro": "按题目的规则，一步一步模拟发生的事情。先在纸上走一遍小例子，再把每一步写成代码，检查零、负数和最后一步等边界。"
  },
  {
    "id": "enum",
    "name": "枚举与暴力",
    "layer": 0,
    "tags": [
      "枚举"
    ],
    "dependsOn": [
      "loop_basics"
    ],
    "intro": "穷举所有可能并逐一检验；数据范围较小时最直接有效，也是理解剪枝、折半枚举等优化思路的出发点。"
  },
  {
    "id": "sort",
    "name": "排序",
    "layer": 0,
    "tags": [
      "sortings",
      "排序",
      "快速排序",
      "归并排序",
      "计数排序",
      "桶排序",
      "基数排序",
      "冒泡排序",
      "快速选择",
      "锦标赛排序",
      "Tim 排序"
    ],
    "dependsOn": [
      "array_basics"
    ],
    "intro": "让数据有序的基础操作：快排/归并/计数排序及其应用（逆序对、去重、top-k）；许多题目先排序才能继续。"
  },
  {
    "id": "construct",
    "name": "构造题",
    "layer": 0,
    "tags": [
      "constructive algorithms",
      "构造"
    ],
    "dependsOn": [
      "enum"
    ],
    "intro": "根据要求设计一种可行的方案。先观察小例子中重复出现的规律，再解释为什么你的方案对所有允许的输入都成立。"
  },
  {
    "id": "greedy",
    "name": "贪心",
    "layer": 1,
    "tags": [
      "greedy",
      "贪心"
    ],
    "dependsOn": [
      "sort"
    ],
    "intro": "每一步取当前最优并证明全局最优；适用于具备交换论证/拟阵结构的问题，是区间调度、取最值类题目的常用策略。"
  },
  {
    "id": "prefix",
    "name": "前缀和与差分",
    "layer": 1,
    "tags": [
      "前缀和",
      "差分"
    ],
    "dependsOn": [
      "math_basic"
    ],
    "intro": "用预处理把子段和、区间增量查询降到 O(1)/O(log n)；把区间操作转化为点操作，是大量题目的前置优化技巧。"
  },
  {
    "id": "recursion",
    "name": "递归与分治",
    "layer": 1,
    "tags": [
      "divide and conquer",
      "递归",
      "分治"
    ],
    "dependsOn": [
      "enum"
    ],
    "intro": "把问题拆成同构子问题再合并结果；是搜索、动态规划、归并排序与快速幂等方法论基础。"
  },
  {
    "id": "twopointer",
    "name": "双指针",
    "layer": 1,
    "tags": [
      "two pointers",
      "双指针 two-pointer",
      "双指针",
      "滑动窗口"
    ],
    "dependsOn": [
      "sort"
    ],
    "intro": "两个指针协同扫描线性结构，把 O(n²) 压到 O(n)；常用于有序数组配对、滑动窗口与去重类问题。"
  },
  {
    "id": "bitops",
    "name": "位运算",
    "layer": 1,
    "tags": [
      "bitmasks",
      "位运算"
    ],
    "dependsOn": [
      "math_basic"
    ],
    "intro": "按位与/或/异或/移位的性质与技巧；状压 DP、集合运算、快速幂与二进制枚举都建立在位运算之上。"
  },
  {
    "id": "binary",
    "name": "二分",
    "layer": 1,
    "tags": [
      "binary search",
      "二分",
      "二分查找",
      "三分查找"
    ],
    "dependsOn": [
      "sort"
    ],
    "intro": "在具备单调性的序列/答案空间上折半查找；二分答案、二分图判定等经典模型都以二分思想为核心。"
  },
  {
    "id": "bigint",
    "name": "高精度",
    "layer": 1,
    "tags": [
      "高精度"
    ],
    "dependsOn": [
      "math_basic"
    ],
    "intro": "用数组/字符串模拟大整数四则运算（C++ 无内置大数）；涉及加减乘除与压位优化，使用 Java/Python 时可略。"
  },
  {
    "id": "stl_usage",
    "name": "容器与标准库",
    "layer": 1,
    "tags": [
      "数组",
      "哈希表",
      "有序集合",
      "堆（优先队列）",
      "字符串",
      "data structures"
    ],
    "dependsOn": [
      "impl"
    ],
    "intro": "认识动态数组、映射、集合和优先队列。先理解它们各自保存什么、如何查找或删除，再调用语言提供的库，减少重复编写代码。"
  },
  {
    "id": "sim_impl",
    "name": "大模拟与工程实现",
    "layer": 1,
    "tags": [
      "模拟",
      "设计",
      "数据流",
      "矩阵"
    ],
    "dependsOn": [
      "impl"
    ],
    "intro": "把包含多个状态的规则拆成小步骤，分别处理每种情况。用清楚的变量名称和小函数，让流程容易检查、调试和维护。"
  },
  {
    "id": "brute_force",
    "name": "暴力与剪枝",
    "layer": 1,
    "tags": [
      "brute force",
      "枚举",
      "Brute-Force Search"
    ],
    "dependsOn": [
      "enum"
    ],
    "intro": "先枚举所有可能，写出正确但可能较慢的方法。再跳过不可能成功的分支，减少重复工作，同时检查优化前后是否得到相同答案。"
  },
  {
    "id": "expression",
    "name": "表达式与括号处理",
    "layer": 3,
    "tags": [
      "expression parsing",
      "括号序列"
    ],
    "dependsOn": [
      "stack_queue"
    ],
    "intro": "中缀/后缀表达式求值、括号匹配与括号序列合法性判定；是大模拟题与「字符串 + 栈」组合题的常见内核。"
  },
  {
    "id": "game_theory",
    "name": "博弈论",
    "layer": 3,
    "tags": [
      "games",
      "博弈",
      "极小化极大",
      "零和博弈",
      "无偏博弈",
      "Nim 游戏",
      "Sprague–Grundy"
    ],
    "dependsOn": [
      "dp",
      "math_basic"
    ],
    "intro": "把游戏局面分成能赢和不能赢的状态，从小局面推导大局面。熟悉递归和状态转移以后，再了解 Nim 与 SG 函数。"
  },
  {
    "id": "prob_stats",
    "name": "概率与期望",
    "layer": 4,
    "tags": [
      "probabilities",
      "概率与统计"
    ],
    "dependsOn": [
      "dp",
      "math_basic"
    ],
    "intro": "从简单的概率和平均值开始，理解期望为什么可以相加。再学习用动态规划保存状态，用模拟实验帮助检查推导。"
  },
  {
    "id": "matrix_linear",
    "name": "矩阵与线性代数",
    "layer": 4,
    "tags": [
      "matrices",
      "线性代数",
      "矩阵"
    ],
    "dependsOn": [
      "math_basic",
      "mod_inv"
    ],
    "intro": "矩阵乘法与递推加速（矩阵快速幂）、高斯消元与线性基；把「递推 + 取模」的大规模问题降到 O(n³ log k)。"
  },
  {
    "id": "meet_middle",
    "name": "折半搜索与双向 BFS",
    "layer": 4,
    "tags": [
      "meet-in-the-middle",
      "中途相遇",
      "双向搜索",
      "0-1 BFS"
    ],
    "dependsOn": [
      "brute_force",
      "bitops"
    ],
    "intro": "把指数级搜索折半（meet-in-the-middle）或从两端同时 BFS：在 n≈40 的子集和、状态空间双向可达等场景里把复杂度开平方。"
  },
  {
    "id": "hash_str",
    "name": "字符串哈希",
    "layer": 4,
    "tags": [
      "hashing",
      "哈希函数",
      "滚动哈希"
    ],
    "dependsOn": [
      "strings"
    ],
    "intro": "用多项式哈希 + 前缀哈希在 O(1) 内比较子串，解决重复子串、回文判定与「哈希 + 二分」类问题；注意双模/自然溢出与卡哈希风险。"
  },
  {
    "id": "stack_queue",
    "name": "栈与队列",
    "layer": 2,
    "tags": [
      "栈",
      "队列"
    ],
    "dependsOn": [
      "recursion"
    ],
    "intro": "受限线性结构及其应用：括号匹配、表达式求值、单调队列滑动窗口等；也是单调栈、堆等进阶结构的前置。"
  },
  {
    "id": "search",
    "name": "搜索 DFS/BFS",
    "layer": 2,
    "tags": [
      "dfs and similar",
      "深度优先搜索 DFS",
      "广度优先搜索 BFS",
      "深度优先搜索",
      "广度优先搜索",
      "回溯",
      "双向搜索",
      "A* 搜索",
      "Heuristic Search"
    ],
    "dependsOn": [
      "recursion",
      "enum"
    ],
    "intro": "深度优先与广度优先遍历状态空间：DFS 负责回溯与连通性，BFS 擅长无权最短路与按层扩展；是暴力枚举与图论的核心手段。"
  },
  {
    "id": "graph",
    "name": "图论入门",
    "layer": 2,
    "tags": [
      "graphs",
      "图论",
      "图",
      "拓扑排序",
      "有向无环图",
      "图的着色",
      "欧拉路径",
      "欧拉回路",
      "欧拉图",
      "半欧拉图",
      "强连通分量",
      "Tarjan 强连通分量算法",
      "Kosaraju 算法",
      "双连通分量",
      "割点",
      "桥",
      "平面图",
      "哈密顿通路"
    ],
    "dependsOn": [
      "search"
    ],
    "intro": "图的存储与遍历、连通性、环与拓扑排序等基本概念；最短路、生成树、网络流等一切图算法都建立在此之上。"
  },
  {
    "id": "dp",
    "name": "动态规划入门",
    "layer": 2,
    "tags": [
      "dp",
      "动态规划 DP",
      "动态规划",
      "记忆化",
      "最长上升子序列",
      "最长公共子序列"
    ],
    "dependsOn": [
      "recursion",
      "greedy",
      "prefix"
    ],
    "intro": "当很多计算会重复出现时，把已经算出的答案保存下来。先明确一个状态表示什么，再找到它如何由更小的状态得到。"
  },
  {
    "id": "dsu",
    "name": "并查集",
    "layer": 2,
    "tags": [
      "dsu",
      "并查集"
    ],
    "dependsOn": [
      "recursion"
    ],
    "intro": "近常数时间维护集合合并与连通性查询；最小生成树、离线问题、带权并查集等大量场景的通用底座。"
  },
  {
    "id": "fenwick",
    "name": "树状数组",
    "layer": 2,
    "tags": [
      "树状数组"
    ],
    "dependsOn": [
      "prefix"
    ],
    "intro": "基于 lowbit 的轻量数据结构，支持单点修改与前缀求和；比线段树实现简单，适合动态前缀统计与逆序对问题。"
  },
  {
    "id": "heap",
    "name": "堆与优先队列",
    "layer": 3,
    "tags": [
      "堆",
      "优先队列",
      "堆（优先队列）"
    ],
    "dependsOn": [
      "stack_queue"
    ],
    "intro": "维护动态集合的最值（插入/取最值 O(log n)）；用于贪心选择、K 路归并、Dijkstra 等需要反复取最值的场景。"
  },
  {
    "id": "monotone",
    "name": "单调栈与单调队列",
    "layer": 3,
    "tags": [
      "单调栈",
      "单调队列"
    ],
    "dependsOn": [
      "stack_queue"
    ],
    "intro": "利用元素单调性在线性时间求左右最近更大/更小元素与滑动窗口最值；「下一个更大元素」类问题的标准解法。"
  },
  {
    "id": "segtree",
    "name": "线段树",
    "layer": 3,
    "tags": [
      "data structures",
      "线段树",
      "扫描线",
      "Sqrt Decomposition",
      "Sparse Table",
      "区间最值查询",
      "树堆",
      "Splay Tree",
      "Cartesian Tree",
      "K-D Tree",
      "Li Chao Tree",
      "有序集合"
    ],
    "dependsOn": [
      "fenwick"
    ],
    "intro": "把区间问题分解为 O(log n) 个节点维护，支持区间查询/修改与懒标记；是可持久化、树套树等高级结构的前置。"
  },
  {
    "id": "number",
    "name": "数论基础",
    "layer": 3,
    "tags": [
      "number theory",
      "数论",
      "费马小定理",
      "欧拉函数",
      "欧拉定理"
    ],
    "dependsOn": [
      "math_basic"
    ],
    "intro": "从整除、素数和最大公约数开始，学习辗转相除、筛法与快速幂。先能解释过程，再考虑如何处理更大的数据。"
  },
  {
    "id": "combo",
    "name": "组合数学",
    "layer": 3,
    "tags": [
      "combinatorics",
      "组合数学",
      "容斥原理"
    ],
    "dependsOn": [
      "math_basic"
    ],
    "intro": "排列组合计数、二项式系数与常用恒等式（隔板法、容斥初探）；计数题与概率题的核心工具。"
  },
  {
    "id": "shortest",
    "name": "最短路",
    "layer": 3,
    "tags": [
      "shortest paths",
      "最短路",
      "Dijkstra 算法",
      "Floyd 算法",
      "Bellman–Ford 算法",
      "0-1 BFS",
      "k 短路"
    ],
    "dependsOn": [
      "graph"
    ],
    "intro": "Dijkstra/SPFA/Bellman-Ford 与 Floyd 全源最短路；负权处理、分层图、次短路与路径计数等大量变体由此展开。"
  },
  {
    "id": "mst",
    "name": "最小生成树",
    "layer": 3,
    "tags": [
      "生成树",
      "最小生成树",
      "Kruskal 算法",
      "Prim 算法",
      "Boruvka 算法"
    ],
    "dependsOn": [
      "graph",
      "dsu"
    ],
    "intro": "Kruskal（并查集）与 Prim 求连通代价最小的生成树；衍生最小瓶颈路、次小生成树与生成树计数问题。"
  },
  {
    "id": "dp_interval",
    "name": "区间 DP",
    "layer": 3,
    "tags": [
      "区间 DP"
    ],
    "dependsOn": [
      "dp"
    ],
    "intro": "状态以区间为维度、转移枚举断点合并子区间；典型应用如石子合并、矩阵链乘与括号序列计数。"
  },
  {
    "id": "dp_knapsack",
    "name": "背包 DP",
    "layer": 3,
    "tags": [
      "背包 DP",
      "背包问题",
      "0-1 背包",
      "完全背包",
      "多重背包",
      "混合背包"
    ],
    "dependsOn": [
      "dp"
    ],
    "intro": "0/1、完全、多重背包及滚动数组优化；最基础的选与不选型 DP，衍生分组背包、依赖背包与方案计数。"
  },
  {
    "id": "dp_tree",
    "name": "树形 DP",
    "layer": 3,
    "tags": [
      "树形 DP",
      "trees",
      "二叉树",
      "二叉搜索树"
    ],
    "dependsOn": [
      "dp",
      "graph"
    ],
    "intro": "以树为结构定义状态（子树/节点上做 DP），含换根 DP 与树上背包；处理树上最优化、计数与博弈问题。"
  },
  {
    "id": "dp_state",
    "name": "状态压缩 DP",
    "layer": 4,
    "tags": [
      "状压 DP",
      "bitmasks",
      "位掩码"
    ],
    "dependsOn": [
      "dp",
      "bitops"
    ],
    "intro": "用二进制位表示集合作为状态维度（旅行商、覆盖与排列类问题）；与位运算紧密结合，小规模集合问题的最优解。"
  },
  {
    "id": "strings",
    "name": "字符串与 KMP",
    "layer": 4,
    "tags": [
      "strings",
      "KMP 算法",
      "字符串匹配",
      "扩展 KMP",
      "字典树",
      "Manacher 算法"
    ],
    "dependsOn": [
      "stack_queue",
      "dp"
    ],
    "intro": "字符串哈希与 KMP 单模式匹配（前缀函数 O(n+m)）；是字符串处理的基础，也是 AC 自动机、后缀结构的前置。"
  },
  {
    "id": "lca",
    "name": "LCA 与树上问题",
    "layer": 4,
    "tags": [
      "最近公共祖先 LCA",
      "trees",
      "最近公共祖先",
      "Binary Lifting"
    ],
    "dependsOn": [
      "graph",
      "dp_tree"
    ],
    "intro": "倍增/树剖/Tarjan 求最近公共祖先，配合树上路径、直径、树上差分；处理树上距离与路径统计类问题的骨架。"
  },
  {
    "id": "geo",
    "name": "计算几何入门",
    "layer": 4,
    "tags": [
      "geometry",
      "计算几何",
      "几何",
      "多边形",
      "凸包",
      "最小圆覆盖",
      "三角剖分"
    ],
    "dependsOn": [
      "math_basic"
    ],
    "intro": "向量叉积/点积、线段相交判定、多边形面积与凸包；需要严谨处理浮点误差与边界情形。"
  },
  {
    "id": "mod_inv",
    "name": "数论进阶：逆元与筛",
    "layer": 4,
    "tags": [
      "逆元",
      "线性筛法",
      "筛法"
    ],
    "dependsOn": [
      "number"
    ],
    "intro": "模意义除法（费马小定理/扩展欧几里得求逆元）与线性筛预处理积性函数；组合数取模、莫比乌斯反演的前置。"
  },
  {
    "id": "flow",
    "name": "网络流",
    "layer": 4,
    "tags": [
      "flows",
      "网络流",
      "最大流",
      "最小割",
      "Edmonds–Karp 算法",
      "Dinic 算法",
      "MPM 算法",
      "Push-Relabel 算法"
    ],
    "dependsOn": [
      "shortest",
      "graph"
    ],
    "intro": "把资源和容量画成有方向的连接，寻找从起点到终点最多能传递多少。掌握图的遍历后，再了解最大流和最小割。"
  },
  {
    "id": "match",
    "name": "二分图匹配",
    "layer": 4,
    "tags": [
      "graph matchings",
      "二分图",
      "图的匹配",
      "最大匹配",
      "完美匹配",
      "匈牙利算法"
    ],
    "dependsOn": [
      "shortest",
      "dsu"
    ],
    "intro": "匈牙利算法求最大匹配，及最小点覆盖/最大独立集/最大流的转化；图论建模中的高频套路。"
  },
  {
    "id": "strings_suf",
    "name": "后缀自动机/数组",
    "layer": 5,
    "tags": [
      "string suffix structures",
      "后缀自动机 SAM",
      "后缀数组 SA",
      "后缀数组",
      "Suffix Tree",
      "Suffix Automato",
      "Lyndon 分解",
      "最小表示法",
      "AC 自动机"
    ],
    "dependsOn": [
      "strings"
    ],
    "intro": "SA/SAM 等后缀数据结构用于本质不同子串统计、LCP、子串出现次数等；字符串进阶难题的主力工具。"
  },
  {
    "id": "dp_opt",
    "name": "DP 优化（斜率等）",
    "layer": 5,
    "tags": [
      "斜率优化"
    ],
    "dependsOn": [
      "dp",
      "number"
    ],
    "intro": "单调队列、斜率优化与四边形不等式把 O(n²) 压到 O(n log n)/O(n)；大数据范围 DP 的进阶必修课。"
  },
  {
    "id": "persist",
    "name": "可持久化与树套树",
    "layer": 5,
    "tags": [
      "可持久化",
      "可持久化线段树",
      "树套树",
      "可持久化数据结构"
    ],
    "dependsOn": [
      "segtree"
    ],
    "intro": "主席树等可持久化结构与线段树套平衡树；解决静态区间第 k 大、历史版本查询与多维动态问题。"
  },
  {
    "id": "tree_high",
    "name": "点分治与树上高级",
    "layer": 5,
    "tags": [
      "点分治"
    ],
    "dependsOn": [
      "lca"
    ],
    "intro": "点分治统计树上路径类问题（长度/数量约束），配合树上启发式合并等技巧处理复杂树上统计。"
  },
  {
    "id": "mobius",
    "name": "莫比乌斯反演",
    "layer": 5,
    "tags": [
      "莫比乌斯反演"
    ],
    "dependsOn": [
      "mod_inv"
    ],
    "intro": "利用莫比乌斯函数反演化简 gcd/lcm 类求和；数论高级专题核心，通常配合线性筛与整除分块使用。"
  },
  {
    "id": "fft",
    "name": "多项式与 FFT",
    "layer": 5,
    "tags": [
      "fft",
      "快速傅里叶变换 FFT"
    ],
    "dependsOn": [
      "combo",
      "mod_inv"
    ],
    "intro": "快速傅里叶变换把多项式乘法降到 O(n log n)；是生成函数计数与字符串快速匹配的底层引擎。"
  },
  {
    "id": "flow_adv",
    "name": "费用流与高级图论",
    "layer": 6,
    "tags": [
      "费用流",
      "2-SAT",
      "最小费用流",
      "SSP 算法"
    ],
    "dependsOn": [
      "flow",
      "match"
    ],
    "intro": "最小费用最大流、上下界网络流与 2-SAT 逻辑建模；把复杂约束翻译成流或布尔可满足问题。"
  },
  {
    "id": "math_adv",
    "name": "数学拓展",
    "layer": 6,
    "tags": [
      "杜教筛",
      "牛顿迭代法",
      "线性代数"
    ],
    "dependsOn": [
      "mobius"
    ],
    "intro": "在数论与数学基础熟练后，按兴趣学习大范围函数求和和迭代求解。遇到陌生概念时，先回到小规模例子验证。"
  }
]

export const LAYER_NAMES = [
  "编程基础",
  "常用方法",
  "算法起步",
  "专题练习",
  "进阶方法",
  "深入探索",
  "扩展知识"
]
