# 五语言真实判题机验证

50 道本地题包的全部允许模式已在部署服务器上的源码版 go-judge 执行：68 个题目/模式、五种语言，共 340 个组合。每个组合的完整测试集执行三轮，共 23325 次执行，全部通过。

报告：`corpus-gojudge-validation.json`。逐组合进度：`corpus-gojudge-progress.jsonl`。题包文件 SHA-256：`corpus-gojudge-input-manifest.json`。验证结束后再次核对全部题包 hash，没有变更。原始验证阶段题库没有写入业务数据库，当时44道尚待批准。2026-10-01 用户已明确授权全部导入，当前50道经典题已完成平台五语言三轮验证并发布，报告见 `classic-import-production.json`；新增20道原创入门题也已三轮通过，见 `original-import-production.json`。70道发布清单见 `problem-packs/release-manifest.json`。

实际工具链为 GCC/G++ 15.3.0、OpenJDK 27、Python 3.14.7、Rust 1.98.1，镜像 `pllysun/sap-judger-sdk:20261001-1`。独立探测容器和 cgroup 名称为 `sap-judger-corpus-probe`，全局限制 640MiB / 1 核。沙箱工具链只读挂载；编译内存限制 512MiB，工作 tmpfs 64MiB，输出和缓存限制 32MiB。每个编译产物只编译一次并缓存，随后反复执行用例。没有通过增加权限或取消 seccomp 来绕开 Java 打包问题。

JDK 27 在当前 seccomp 下使用 `jar cf answer.jar` 会触发 directCopy 系统调用失败。成功验证的固定打包流程为 `javac` 后 `jar <各 -J 参数> c *.class > answer.jar`，直接从标准输出写归档，运行使用 `java ... -cp answer.jar Main`。Java 参数包括 `-Xmx96m`、SerialGC、单处理器、64m Metaspace、24m CodeCache、32m CompressedClassSpace。

验证脚本：`corpus-probe-server.py`（Python 3.6 兼容），本地启动器：`run-corpus-probe.py`（使用现有 SSH helper，通过 stdin 传入压缩题包与脚本）。执行期间临时调整 namespace 上限，`finally` 恢复原值并清理容器、cgroup 和专属 mount 文件。报告确认 namespace 已恢复、容器已删除、无残留 cgroup，线上 `sap` 容器仍然 running / healthy。

本报告验证的是参考实现和既有测试集在真实受限运行环境中的一致性；业务接口、提交队列、权限、编辑器和部署后端到端流程由业务验收另行验证。报告中不包含隐藏用例输入、预期输出或实际输出。

验证后仅修正 P1001 前两个自建小用例的 `sample` 可见性标记。其余 49 包已有公开样例。所有题包除 `cases.sample` 外逐字段完全一致，判题输入、期望输出、参考解法和驱动均未变化，因此保留既有 23325 次通过记录；最终报告记录原始/新包哈希和判题内容哈希。
