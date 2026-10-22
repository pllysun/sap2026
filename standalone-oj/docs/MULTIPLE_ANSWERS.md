# 多答案题与自定义校验器

从 1.1.2 起，网站版与单容器合并版都支持多答案。无需迁移数据库；新增字段保存在题包 JSON 中。原有 `TOKENS`、`UNORDERED_TOKENS` 题包继续适用。

## 选择判定方式

| checker | 适用情况 | 比较规则 |
| --- | --- | --- |
| `TOKENS` | 唯一的数字、字符串或序列 | 忽略空白，逐个比较 token |
| `UNORDERED_TOKENS` | 同一组元素可以任意排列 | 比较 token 多重集合，重复次数必须一致 |
| `UNORDERED_LINES` | 记录可以换行排序，记录内部有序 | 忽略空行与行间顺序，保留每行 token 顺序与重复行数量 |
| `ANY_OF` | 少量、可以完整列出的合法答案 | 匹配 `expectedOutput` 或 `acceptedOutputs` 中任意一项；每项内部按 `TOKENS` 比较 |
| `SPECIAL_JUDGE` | 任意峰值、拓扑序、构造方案、满足约束的任意答案 | Python 校验器根据本次输入判断实际输出是否合法 |

无序比较不能代替通用多答案检查。例如数组可能有两个峰值，即使两个下标都合法，它们的 token 仍不相同。LeetCode 的[寻找峰值](https://leetcode.com/problems/find-peak-element/)和[课程表 II](https://leetcode.com/problems/course-schedule-ii/)公开题面允许返回任意一个符合规则的答案；[字母异位词分组](https://leetcode.com/problems/group-anagrams/)则允许结果调整顺序。这里借鉴的是题目的判定语义；LeetCode 没有在这些页面公开其内部检查器实现。

## 有限的多个合法输出

选择 `ANY_OF`，在管理端测试集的“其他合法输出”添加答案，或导入：

```json
{
  "name": "two-peaks",
  "input": "1 2 1 3 5 6 4\n",
  "expectedOutput": "1\n",
  "acceptedOutputs": ["5\n"],
  "sample": true
}
```

每个用例最多 20 个其他合法输出，每项最多 256 KiB，计入全题测试数据 2 MB 总限制。合法答案很多、随输入增长，或无法完整列举时，使用 `SPECIAL_JUDGE`。

## 自定义校验器接口

题包顶层增加 `checker: "SPECIAL_JUDGE"` 和字符串字段 `checkerSource`。界面位于“题目内容 → 答案判定 → Python 答案校验器”。必须定义：

```python
def check(input_text, actual_output, expected_output):
    # input_text：该测试的原始输入
    # actual_output：本次用户程序的标准输出
    # expected_output：题包里的参考输出，可以辅助检查，但不要求相等
    return True  # 必须为 bool；False 表示答案错误
```

校验器使用运行镜像内的 Python，与用户选择的 C/C++/Java/Python/Rust 无关。标准输入输出、核心函数两种模式共用同一规则。调用发生在每次用户程序正常退出之后，原程序的超时、内存超限、输出超限或运行异常保持原判定。自定义输入没有参考输出时，只运行用户程序。

一个可直接使用的峰值检查器如下。其输入协议为“一行整数数组”，输出协议为“从 0 开始的一个峰值下标”；相邻元素不等是题面的输入约束。

```python
def check(input_text, actual_output, expected_output):
    try:
        values = list(map(int, input_text.split()))
        answer = actual_output.split()
        if len(answer) != 1:
            return False
        index = int(answer[0])
        return (0 <= index < len(values)
                and (index == 0 or values[index] > values[index - 1])
                and (index == len(values) - 1 or values[index] > values[index + 1]))
    except (ValueError, IndexError):
        return False
```

拓扑序检查器需要检查输出节点数量、取值范围、无重复，以及每条有向边的先后关系。如果题面允许无解时输出空序列，也必须验证输入确实有环。构造题应核对全部约束、最优目标和输出完整性，不能只与某份参考输出做宽松比较。浮点误差题可在校验器中显式实现绝对/相对误差，但要拒绝 NaN、无穷值和多余输出。

## 隔离与限制

- 校验器代码 UTF-8 大小最多 64 KiB，执行限时 1 秒、内存 128 MiB；沿用已有的网络、文件系统、进程与 cgroup/seccomp 隔离，不在 Web 进程或 AI 宿主机执行。
- 自定义校验器每个用例的输入与参考输出合计最多 512 KiB 字符；CLI 使用更保守的 UTF-8 字节限制。用户输出仍限制 256 KiB。数据通过按 UTF-8 字节长度分段的标准输入传输，用户输出不会拼接成可执行源代码。
- 校验器抛异常、超时、返回非 bool、或输出协议错误时，结果为 `SYSTEM_ERROR`，不记作用户 `WA` 或比赛罚时。不要依赖打印文字作为返回值。
- 校验器源码、隐藏用例和参考实现不进入用户题目接口。公开样例的输入、参考输出和所列其他合法输出会公开；不要把隐藏数据标记为样例。

## AI 出题与验收步骤

1. 先确定完整的输入输出协议与全部约束，选择最窄的正确判定方式。
2. 为校验器建立独立小规模 oracle；固定随机种子生成边界、重复值、极端值、单元素、无解、多解与随机测试。
3. 执行 `python3 tools/ojctl.py lint <pack.json>`，确认五语言参考解和全部支持模式齐全。
4. 导入隔离测试环境，执行 `python3 tools/ojctl.py validate <id> --timeout 1200`。服务端必须完成五语言、全部模式、全部用例的三轮验证。
5. 额外提交不同于参考输出的合法答案，确认 `AC`；提交非法下标、重复节点、缺失节点、违反边约束、空输出、额外内容与伪造答案，确认 `WA`。
6. 测试校验器报错、非 bool 返回值和超时，确认 `SYSTEM_ERROR`。检查用户接口没有源码或隐藏测试。
7. 通过后按实际 revision 保存并发布；修改题意、用例、比较规则或校验器源码都会使原验证失效，必须重新验证。

管理端测试集采用左侧用例列表、右侧单用例编辑，支持名称搜索、公开/隐藏标记、JSON 导入导出与多答案编辑。窗口中的操作按钮固定在底部，用例列表和编辑内容独立滚动，适配 1080p、2K 和窄屏。
