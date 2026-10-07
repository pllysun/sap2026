# 经典题库本地预备材料

本目录配套 `../manifest.json`。总清单为 50 道：LeetCode 40、洛谷 5、原创 5；简单 20、中等 22、困难 8。六道联调题由独立工作流提供，生成器只管理其他 44 道题。所有材料保持本地，`autoImport=false`，不会自动导入生产库。

## 格式与内容

每个 JSON 包包含自写题面、输入输出格式、约束、来源 URL／题号／整理说明、语言模板、参考解法和固定测试集。`cases` 内含 `sample` 标记，默认仅前两组公开。所有数据均为自建，**没有获取或声称使用外站的隐藏官方测试**。

- `modes` 为 `STDIO` 或 `STDIO/FUNCTION`；`defaultMode` 以 FUNCTION 优先。
- 五语言键固定为 `c`、`cpp`、`java`、`python`、`rust`。
- 每个 FUNCTION 驱动只包含一个 `__USER_CODE__` 插入位置。
- Java 驱动入口为 `Main`，核心函数为 `Solution` 方法。
- Rust 使用离线标准库，树题采用 `Rc<RefCell<TreeNode>>`。
- 当前 14 道非联调 LeetCode 题有完整双模式：9、3、11、32、33、35、41、42、55、62、69、70、84、98。其余题的函数扩展状态列在 manifest 中，当前可按 STDIO 导入。
- LeetCode 98 覆盖二叉树，联调题 206 覆盖链表；数组与字符串代表题已有双模式。
- 空字符串用单独 `-` 表示，空数组通过长度 0 表示；这些约定写在对应题面中。

题面是教学改编，数据范围和输入输出以本平台题面为准，不声称完整复刻来源平台的资源限制或函数契约。例如中位数题输出中位数两倍，集合结果规定排序。原平台难度单独保存在 `sourceDifficulty`，平台难度可以后续人工调整。来源 URL 并不代表获得原站完整题面转载授权；未复制全文或受限题解。

## 生成与校验

```sh
python3 ops/judger/problem-packs/corpus/build_corpus.py
python3 ops/judger/problem-packs/corpus/check_test_quality.py
python3 ops/judger/problem-packs/corpus/verify_corpus.py --rounds 3
```

固定随机种子为 `20261001`。期望输出由独立 Python oracle 构造：小规模用例采用枚举、搜索、集合或不同动态规划形式；最大规模使用独立可运行的数学／递归／动态规划公式。参考程序由受限命令式 AST 生成五语言实现，oracle 与参考算法分开维护。附加用例覆盖公布的最大规模、空值（如题目允许）、重复、全部相等、单调、全负、端点、无解、全部命中等情况。

`test-quality.json` 保存代表性错误算法被测试集拒绝的记录；它不是测试完整性的证明。

`local-validation.json` 是本机原生工具校验，不等于实际 go-judge 沙箱验证。记录实际工具版本、每题每语言每模式的完整用例运行轮数、缺失工具链与失败情况。最终必须在实际镜像上用配置的 GCC/JDK 27/Python/Rust 重新完成所有五语言完整套件至少 3 轮，再批准题目发布。

不依赖容器特权或 SSH；生成和本地校验均不访问生产数据库。
