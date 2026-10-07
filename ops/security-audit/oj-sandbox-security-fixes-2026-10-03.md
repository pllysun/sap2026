# 判题沙箱安全修复与兼容性验证

2026年10月3日。针对审查报告的 J01–J06，已完成源码修复，并增加隔离配置的启动检查。本地后端、Agent 接口与前端构建验证通过。**代码尚未部署；新增启动自检仍需在原生 Linux 的 cgroup v1、v2 环境完成验收，不能将本地测试视为线上沙箱已通过验收。**

本次保留五种语言、STDIO 与 FUNCTION 模式、编译参数、样例数量、判题比较规则、编译诊断和节点调度协议。没有改动数据库结构、题目数据或线上配置。原始审查报告与漏洞证据保留，本文记录修复后的状态。

## 后续原生验收与合并发布

2026-10-03，本文记录的修复已与执行进度推送一并发布。主服务版本 1.5.26，独立节点版本 1.5.25，实际 Agent SHA-256 均为 `b914cd233546cca771e6df96bdb793fb59f1aee9d2aa160bfe9221998cca9ed4`。两个服务器的 cgroup v1 / v2 原生验收各 40 项通过，包括五语言两模式；独立节点的绑定 Agent 文件已更新，旧容器及 Agent 备份保留。发布时另修正了大 CPU 节点的 Rust 链接线程数量与前端长整数进度字段解析。

以上“尚未部署”及“尚未原生验收”描述保留为本安全会话完成时的记录；当前部署与验收状态见 [1.5.26 发布记录](/Users/pllysun/Code/claude/sap/sap2026/ops/deployment-1.5.26.md)。本次接口加固并未消除本文列出的共享内核与 FUNCTION 驱动保密结构性限制。

## 修复结果

| 审查项 | 已实现的修复 | 验证 |
| --- | --- | --- |
| J01 控制接口导入外层文件 | Agent 限制请求结构、单命令、执行路径、环境变量、文件名和资源参数；拒绝全部 `src`、额外文件导出和管道配置；拒绝重复 JSON 键，避免 Python 与 Go 解析差异。引擎增加私有空输入目录的 `-src-prefix` 限制。 | 非法文件来源及协议扩展请求未到达合成引擎；真实后端生成的正常请求全部接受。 |
| J02 TLS 握手阻塞 | TLS 握手移入有连接上限的工作线程，单次握手期限 5 秒。 | 未发送握手的 TCP 连接存在时，正确认证的 HTTPS 请求仍返回 200；闲置连接随后关闭。 |
| J03 未认证慢连接 | 最多 128 个处理连接，请求头总读取上限 64 KiB、期限 10 秒，请求体期限 15 秒；HTTP/1.1 后续请求重新计时。错误鉴权及请求分帧错误关闭连接。 | 连接超额拒绝、闲置回收、慢速请求头、请求头字节限额、请求体超时与正常连接复用均通过。 |
| J04 租约重入和过期复用 | 在锁内检查过期、执行中和已释放状态；一个租约只允许一个引擎调用；运行中释放租约时继续占用槽位，直至执行调用结束。编译产物绑定所属租约。 | 同租约重入返回 409，另一租约仍可工作；已过期租约立即拒绝；跨租约产物读写拒绝；顺序编译、执行和删除产物通过。 |
| J05 展示输出累计过大 | stdout、stderr、期望输出共享 2 MiB 的 JSON 转义 UTF-8 展示预算；用例名称另有 64 KiB 预算，避免大量输出挤掉普通用例名称。截断按 Unicode 码点边界进行，并返回全局与字段标记。 | 500 个正确样例仍全部执行并 AC，结果 JSON 小于 2 MiB 加 200,000 字节；预算耗尽后的错误答案仍判 WA；小输出内容保持一致。 |
| J06 节点响应先分配后限额 | Java 使用按字节计数的 BodySubscriber，超限取消订阅；执行响应最多 4 MiB，控制响应最多 64 KiB；总期限覆盖响应头之后持续发送的响应体。Agent 的引擎响应读取也采用相同量级上限。 | 定长和分块超量响应取消，超量数据块未被复制；正常中文、emoji、最大 JSON 转义输出、409 异常语义和线程中断处理通过。 |

启动检查现在要求 mount.yaml 存在，验证 memory、pids、CPU 控制器，UID/GID 1000、独立只读工具链、受限 tmpfs 和只读 proc。节点公布 RUNNING 前，执行一次有限的 Python 自检，检查 capability、NoNewPrivs、seccomp、独立网络 namespace，并以最多 12 次 fork 尝试验证 8 进程限额。所有已创建子进程都由该自检回收；不创建无界进程树。

缺少必要配置或自检失败时，节点报告 ERROR，由现有调度器避开。这会停止原先可能在隔离降级状态下接受任务的节点。正常部署配置来自现有隔离方案；原生验收用于确认实际执行效果与兼容性。

## 正常功能验证

后端在隔离临时目录构建，使用本机 Maven 与 OpenJDK 26，编译目标沿用项目 Java 21 配置。117 项测试全部通过，包含原有判题边界、隐藏数据权限、竞赛计分、节点管理、重试恢复和限流测试，以及新增响应保护、累计输出和语言协议测试。

Agent 的 25 项标准库测试全部通过。HTTP 和 TLS 使用真实本地 socket，执行引擎由合成对象替代。兼容性测试捕获实际 `JudgeEngine → GoJudgeClient` 生成的请求，覆盖 C、C++、Java、Python、Rust × STDIO/FUNCTION，并通过真实 Agent HTTP 接口重放 78 次编译、执行和产物清理请求。该测试证明请求协议兼容，不等于重新运行了五种原生编译器。

用户端与管理端生产构建均通过。结果界面会提示超量输出被截断，省略内容不会显示成“无输出”；编译诊断和小输出继续完整展示。构建存在原有大体积 chunk 提示，没有构建错误。

本次没有启动生产节点、提交生产任务或调用线上管理接口；没有把既有 Linux 测试记录计作修复后的实测。

## 发布与原生验收

1. 使用本次代码构建后端 JAR、两个前端和 Agent。增量镜像模板见 [安全更新 Dockerfile](/Users/pllysun/Code/claude/sap/sap2026/docker/Dockerfile.judger-security-update)，基于已有 1.5.23 的固定摘要；构建继续经过 `build-image.sh` 的 amd64、不可变新版本及产物校验。此模板已准备，尚未构建或推送镜像。仅使用旧的 application-update 或 user-update 模板会遗漏 Agent 更新。
2. 先在无业务数据的临时原生 amd64 节点验收 cgroup v1、v2：启动自检通过、五语言两模式编译运行、CE/WA/TLE/MLE/OLE、线程创建、进程上限、停止重启及产物清理。记录镜像摘要、Agent 与 seccomp 哈希、内核版本和控制器列表。该步骤尚未执行。
3. 通过后逐节点更新，再更新业务服务，保持其他已验证节点提供执行容量。既有独立节点记录使用宿主 `agent.py` 绑定挂载，更新镜像之外还必须同步该挂载文件，否则仍会加载旧 Agent。保留原令牌、数据卷、端口和 cgroup 配额，并通过启停与故障转移验收；异常时使用对应的既有镜像与 Agent 备份恢复。

## 保留的结构性风险

共享宿主内核以及内置节点与业务同容器的故障范围，不能由本次接口修复消除。嵌套 namespace 攻击面仍存在。本次没有删除 clone/unshare 权限或迁移节点；直接收紧这些权限可能破坏沙箱初始化以及 Java、Rust 的正常工作。进一步加固需要独立执行阶段的系统调用策略和原生兼容性验证，或迁移到独立节点、独立内核的隔离方案。

FUNCTION 驱动与用户代码在同一执行环境中，不能保证驱动保密。本次在管理端编辑提示中明确要求驱动不包含参考解、隐藏答案或密钥，保留正常编译诊断。需要保密的逻辑仍必须放在用户进程之外；新增提示不等同于已建立驱动保密边界。

每日任务配额属于业务策略，现有队列、账号并发及任务期限继续生效。本次未设置可能拦截正常练习的任意新配额；持续合法占用资源的公平调度问题仍需结合使用量制定策略。

## 文件与复测入口

- [Agent 实现](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py)与[Agent 测试](/Users/pllysun/Code/claude/sap/sap2026/ops/judger/test_agent_security.py)
- [节点响应保护](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/BoundedResponseSubscriber.java)与[传输实现](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/JudgerNodeTransport.java)
- [展示预算](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/JudgeOutputBudget.java)与[判题逻辑](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/JudgeEngine.java)
- [用户结果展示](/Users/pllysun/Code/claude/sap/sap2026/sap-user/src/components/OjRunResult.vue)与[管理端驱动提示](/Users/pllysun/Code/claude/sap/sap2026/sap-admin/src/views/OjView.vue)
- [测试结果及源码哈希](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/fix-2026-10-03/results.json)、[后端日志](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/fix-2026-10-03/backend-tests.log)、[Agent 日志](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/fix-2026-10-03/agent-tests.log)与[后端请求样本](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/fix-2026-10-03/backend-contract.json)

在单独的后端构建目录运行相关测试时，传入 `-Djudger.contract.output=/absolute/path/backend-contract.json` 导出实际请求。项目根目录运行 `SAP_JUDGE_CONTRACT=/absolute/path/backend-contract.json python3 -m unittest discover -s ops/judger -p 'test_agent_security.py' -v`，即可同时验证安全边界与跨语言协议；未设置该环境变量时会明确跳过协议重放这一项。
