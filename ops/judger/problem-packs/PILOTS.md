# 联调题包

这六份题包仅用于首批导入。`generate_pilots.py` 可重新生成全部材料，随机种子为 `20261001`。题意以中文简洁整理，保留来源链接；公开样例标记 `sample: true`，其他测试全部自行构造，没有收集或声称拥有官方隐藏测试数据。

| 包 | 模式 | 用例数 | 范围 |
| --- | --- | ---: | --- |
| luogu-p1001 | STDIO | 25 | 负数、零、正数、上下界 |
| leetcode-1 | STDIO / FUNCTION | 30 | 唯一解、相同值、负数、零、10000 元素 |
| leetcode-20 | FUNCTION | 35 | 非空字符串、嵌套、交叉、缺失、10000 字符 |
| leetcode-704 | STDIO / FUNCTION | 32 | 单元素、首尾、缺失、10000 元素 |
| leetcode-53 | STDIO / FUNCTION | 35 | 全负、零、断开、100000 元素 |
| leetcode-206 | STDIO / FUNCTION | 32 | 空链表、单节点、重复值、5000 节点 |

## 输入和函数约定

所有驱动读取与 STDIO 模式相同的题包输入。数组题第一行 `n`、第二行整数数组；两数之和与二分查找第三行 `target`。链表同样使用节点数量和节点值序列，空链表为 `0` 加空行。括号题直接输入一行括号字符串。

`functionDriver` 含且仅含一个 `__USER_CODE__` 标记。驱动提供标准头文件、Python `sys`、Rust `HashMap`、链表节点定义；用户只提交 `starterFunction` 形式的函数/类。C 两数之和返回 `malloc` 分配的两个索引，并设置 `*returnSize = 2`，驱动释放返回数组。C 链表返回原节点构成的新表头，驱动释放原分配块。链表驱动在输出原输入长度后停止，并检查多余节点或长度不足，避免错误返回链表导致驱动无限遍历。Rust 链表使用 `Option<Box<ListNode>>`，输出时迭代解包，避免长链递归析构。

两数之和保证唯一一组解，使用 `UNORDERED_TOKENS` 比较两个索引，驱动还检查范围及重复索引。其余用 `TOKENS`，空链表的预期输出只有换行。最大子数组小规模使用穷举 oracle，大规模使用前缀和/最小前缀 oracle，与参考程序的 Kadane 算法交叉验证。

## 验证

`validate_pilots.py` 不依赖第三方 Python 包，按题包允许模式编译完整参考程序，再对全部用例反复运行。默认验证五语言三轮；可使用 `CC`、`CXX`、`JAVAC`、`JAVA`、`PYTHON`、`RUSTC` 指定实际判题镜像内的工具链。脚本是普通进程验证，实际沙箱资源限制和隔离验证由部署流程完成；完整 go-judge 验证报告应另行保存。报告由运行产生，不能视为未运行语言或未来工具链版本的通过证明。

```sh
python3 ops/judger/problem-packs/validate_pilots.py --rounds 3
```

公开题目来源可见各 `pack.json` 的 `sourceUrl` 与 `sourceId`。首批样例来自公开题面；P1001 用例均为自建，前两个作为本平台公开样例；`sample: true` 仅代表用户可见，不代表来自官方公开样例。
