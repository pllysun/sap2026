# 项目判题沙箱安全审查报告

审查日期：2026年10月3日，Asia/Shanghai。对象为当前工作区的算法题库执行链路、go-judge 沙箱、节点控制服务及部署配置。

**结论：存在可以绕过预期边界的路径，不能把当前执行环境当作绝对安全的虚拟机。** 本次确认了控制接口的文件导入、连接处理、租约并发和输出资源保护缺口。部分缺口需要节点令牌或特定部署条件；没有确认普通用户仅提交一段代码就能取得宿主机权限的完整逃逸链。

普通用户代码的主要直接风险是让后端累积大量输出，以及在部署缺少进程控制器时突破预期进程数量限制。文件导入缺口的利用前提是取得节点令牌；TLS 拒绝服务的前提是能够连接启用原生 TLS 的控制端口。报告中的这几类风险应分别处理。

本次只进行了审查和隔离验证，未修改产品代码或部署配置。新增文件为报告、验证脚本和证据。

## 审查范围与证据强度

基准 Git 提交为 `7bc9a9872af65808fd2ccc786a610f5be0035bdc`。审查对象包含用户原有的已修改和未跟踪文件，因此该提交号不能独自复现审查状态；本次对 137 个相关文件记录了 SHA-256。[源码与证据清单](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/source-and-evidence-manifest.json)

| 证据类型 | 本次结果 | 能支持的结论 |
| --- | --- | --- |
| 当前源码审查 | 检查 Java 后端、Python Agent、Linux 环境、文件传输、seccomp 和 Docker 配置 | 当前源码中的校验缺口与攻击前提 |
| 本地 Agent 复核 | 运行原始 Handler，执行引擎用合成对象替代 | 鉴权、请求体限额、租约转发、HTTP 慢连接和 TLS 阻塞行为 |
| 后端隔离构建 | 当前源码复制到临时目录，110 项测试，失败和错误均为 0 | 命令构造、权限、请求限额、判题结果与隐私边界；其中 2 项断言用于确认现存缺口 |
| JDK 响应复核 | 本地返回固定 41 MiB 数据，完整读取后才拒绝 | 响应大小检查发生过晚；未尝试使 JVM 内存耗尽 |
| 启动校验复核 | 原始 Agent.launch，进程和配置返回值为合成对象 | Agent 将缺少安全配置的响应直接认定为 RUNNING |
| 既有 Linux 实测记录 | 仓库记录于当天 04:21，34 项中 32 项满足预期 | 指定镜像和当时配置下的隔离行为，不能视为本次重新执行的生产验收 |

110 项测试覆盖 `com.sap.service.judger.*Test`、三组限流测试和六项独立审计检查，使用 OpenJDK 26.0.1 编译 Java 21 源码。JaCoCo 已跳过，Mockito 使用 Byte Buddy 的实验兼容参数；这不是业务生产 JDK 的端到端验证。用于确认缺口的测试通过，意味着缺口复现成功，不能解释为安全检查全部通过。[本次测试结果](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/backend-test-results.json)

既有 Linux 记录使用 `pllysun/sap:1.5.23`，镜像 ID 为 `sha256:2ea792ca367d6a182de8dc5bfa0c67972b9b63b6a20bea7b37dfce10e47628fd`，内核为 `3.10.0-1160.119.1.el7.x86_64`。记录称使用一次性容器、合成令牌、无业务数据挂载，结束后清理容器和 cgroup。[既有隔离测试记录](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/oj-sandbox-results.json)

**证据限制：** 本次未连接生产服务器。既有记录中的外层 seccomp 哈希与当前 `docker/judger-seccomp.json` 不同；虽然旧后端记录的九个源码哈希均匹配当前文件，不能据此认定线上镜像、内层过滤器或独立节点也完全匹配。仓库的 `production-metadata-results.json` 为空，不能作为线上配置证据。本次 Go canary 复跑因 Go 1.26 工具链下载超时未完成，因此文件读取结论采用当前源码分析和明确标识的既有 Linux 实测证据。

## 执行环境与信任边界

当前代码使用 go-judge 的 Linux 容器沙箱，编译与运行都在受限环境内进行。普通用户调用业务接口，由 Java 后端构造固定命令、文件和资源限制，再通过节点令牌与租约访问 Python Agent，Agent 将请求转发给 root 权限的 go-judge 控制进程。用户程序以沙箱 UID/GID 1000 执行，只挂载工具链和临时目录。[命令与限制构造](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/GoJudgeClient.java:25)、[沙箱挂载](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-mount.yaml:1)

内置 Agent 和执行引擎默认监听容器回环 5051、5050，普通 OJ 提交接口没有向用户开放完整执行 JSON。独立节点的启动脚本默认绑定 `0.0.0.0`，可配置原生 TLS；其实际可达范围由端口映射、防火墙和隧道决定。[独立节点启动脚本](/Users/pllysun/Code/claude/sap/sap2026/docker/node-start.sh:10)

这类隔离仍与宿主共享 Linux 内核。go-judge 的上游说明将其定位为基于 Linux 容器技术的沙箱服务；内核文档也说明 seccomp 是缩小系统调用攻击面的工具，需要配合其他隔离机制。[go-judge 官方说明](https://github.com/criyle/go-judge)、[Linux seccomp 文档](https://docs.kernel.org/userspace-api/seccomp_filter.html)

## 已确认的代码与接口缺口

P1 表示满足所列前提后可能造成较严重的数据泄露或节点不可用，应优先修复；P2 表示资源保护或可用性缺口。等级不代表普通学生已经具备相应权限。

| 编号 | 等级 | 问题 | 利用前提 | 验证状态 |
| --- | --- | --- | --- | --- |
| J01 | P1 | 完整执行请求可导入沙箱外文件 | 可连接节点并持有有效节点令牌与租约 | 当前转发已复核，文件读取有既有 Linux canary 证据 |
| J02 | P1 | 原生 TLS 握手阻塞整个接入循环 | 可连接启用原生 TLS 的控制端口，无需令牌 | 本地复现 |
| J03 | P2 | HTTP 请求开始前没有超时及线程上限 | 可连接控制端口，无需令牌 | 本地复现 |
| J04 | P2 | 单个租约允许并发执行请求及短暂过期重用 | 有效节点令牌与租约 | 本地转发复现，未突破实际引擎并行上限 |
| J05 | P2 | 多个样例输出在业务 JVM 中无任务总量上限 | 普通有效账号，可运行含多个公开样例的题目 | 实际 JudgeEngine 配合 mock 引擎复现 |
| J06 | P2 | 节点响应完整读取后才检查大小 | 恶意、受控或异常节点响应 | JDK API 复现，未制造 OOM |

### J01 执行请求可导入沙箱外文件

Agent 只检查请求体是否超过 4 MiB，没有验证 `/engine/run` 内部字段。取得有效令牌和租约后，调用者可以使用 go-judge 的原始文件输入能力。启动参数没有设置 `-src-prefix`，而 Go 的 `ValidateSourcePath` 在前缀列表为空时不限制目录；`src` 随后转换为控制进程在沙箱外读取的本地文件。[Agent 转发](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py:310)、[启动参数](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py:127)、[文件来源校验](/Users/pllysun/Code/claude/sap/sap2026/third_party/go-judge/cmd/go-judge/model/model.go:350)

本次证明带有任意执行字段和文件来源的请求会被原样转发。既有 Linux 测试的 `control_plane_host_source_canary` 则通过 `copyIn.src` 读出了只存在于外层一次性容器的合成文件。[本次 Agent 证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/agent-boundary-results.json)、[既有文件读取证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/oj-sandbox-results.json)

影响范围是 **go-judge 控制进程可读取的外层容器文件**。内置节点与业务同容器时，应用数据和挂载卷可能进入此范围；独立 node-only 节点的影响范围较小。该结果不能推导为读取任意宿主机文件，也不构成普通提交代码的直接逃逸。普通用户提交模型没有 `src` 字段，后端只构造源码 `content` 和编译产物 `fileId`。

建议在 Agent 拒绝所有 `src` 文件来源，限制命令数量、可执行路径、文件名、环境变量和资源参数。现有业务链路不需要任意宿主文件导入；引擎同时配置专用输入目录前缀作为第二道限制。节点令牌应作为高权限机器凭据管理，令牌泄露后的处置应包括轮换与相关容器数据检查。

### J02 原生 TLS 握手可以阻塞整个节点

Agent 将监听 socket 直接交给 `SSLContext.wrap_socket`，未关闭自动握手，也未配置握手超时。接收连接时会先进行握手，之后才创建请求处理线程；请求函数中的 65 秒超时尚未生效。[TLS 初始化](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py:372)、[请求超时位置](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py:339)

本地测试使用一条未发送 TLS 握手内容的原始 TCP 连接，使另一条携带正确令牌的 HTTPS 请求超时；关闭原始连接后服务恢复并返回 200。该问题发生在认证之前。[本次 TLS 证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/agent-boundary-results.json)

Python 官方文档确认，包装监听 socket 后，新接受的连接会自动包装；显式握手才能自行控制其阻塞行为。[Python SSL 文档](https://docs.python.org/3/library/ssl.html#ssl.SSLContext.wrap_socket)

建议由具备握手期限和连接限额的反向代理终止 TLS，Agent 仅监听回环；或将握手放入有并发上限的处理阶段，并设置独立握手期限。只在 `do_GET` 中加超时不能修复此问题。仓库独立节点部署记录描述的是 SSH 隧道连接，不能把此漏洞直接认定为该节点当前公网可利用。

### J03 未认证 HTTP 连接可以长期占用线程

`ThreadingHTTPServer` 为连接创建线程，当前代码在已解析出请求、进入 `run_request` 后才设置超时。连接如果一直不发送请求行，就不会进入此函数；代码也没有并发连接上限。[服务器创建](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py:372)、[超时设置](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py:340)

本地仅建立 24 条短时间回环连接，观察到新增 24 个线程，接受连接时的超时全部为 `None`。全部连接随后关闭。放大后可能耗尽线程、文件描述符或控制器内存，但本次没有进行大规模耗尽测试。[慢连接证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/agent-boundary-results.json)

建议从接收连接或 Handler.setup 阶段设置请求行和请求头期限，增加线程与连接限额，并处理 HTTP/1.1 后续请求的期限。`daemon_threads=True` 只影响退出等待，不提供资源上限。[Python socketserver 文档](https://docs.python.org/3/library/socketserver.html#socketserver.ThreadingMixIn)

### J04 租约没有限制同一任务的并发转发

执行入口只检查租约是否存在和节点是否运行，然后增加 `executing` 计数，没有要求此前为 0，也没有现场判断 `expires`。因此同一租约可以被多个请求同时使用；过期租约在状态清理运行之前还可以被续期。[租约使用](/Users/pllysun/Code/claude/sap/sap2026/docker/judger-agent.py:312)

本次一个租约同时转发了六个请求，六个均返回 200。手动设为已过期的合成租约也被接受。真实 Agent 的状态清理约每五秒运行，因此过期重用是清理间隔中的竞态。[租约证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/agent-boundary-results.json)

这证明租约不能提供“一项任务一个在途请求”的边界；**没有证明**实际沙箱突破 go-judge 的 `parallelism` 或外层 cgroup 总预算。普通后端当前按顺序执行编译、用例和清理，直接利用仍需要机器令牌。

建议在锁内检查租约有效期并拒绝同一租约的重入，用全局有上限的在途请求计数约束转发；编译产物应绑定所属租约，任务结束后统一清理。

### J05 用户输出可以在后端累计到远高于单用例上限

单次执行限制 stdout 256 KiB、stderr 64 KiB，但 `JudgeEngine` 在运行模式下把每个样例的两种输出保存到 `result.cases`，没有累计字节限额。题包可包含最多 500 个用例，也没有公开样例数量的单独上限。[输出收集](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/JudgeEngine.java:95)、[题包限制](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/OjService.java:287)、[运行选择样例](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/OjService.java:525)

本次在实际 JudgeEngine 中使用合成引擎返回值，八个正确样例累计保留 `2,621,408` 字节。校验器忽略多余空白，因此大量空白加正确答案不会必然触发 WA，stderr 也不影响答案比较。若存在 500 个公开样例，按此负载推算可累计 `163,838,000` 字节，约 156.25 MiB，尚未计入 JSON 序列化和其他对象开销。[后端验证](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/backend-test-results.json)、[输出比较器](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/util/judger/OutputChecker.java:8)

该路径不需要节点令牌。实际能积累多少取决于已发布题目的公开样例数、代码是否通过前面的用例及任务期限；本次没有检查线上题目数量，也没有制造 OOM。缺口出现在业务 JVM 的数据保留，沙箱本身的内存限额不能限制这部分内存。

建议保留现有逐用例执行限额，同时为一项任务的展示输出、结果 JSON 和保存到数据库的内容设置总量限额。达到展示限额时截断后续展示并明确标记，判题仍使用受限的原始输出；截断内容不得改变答案比较或 AC 判定。公开样例数量应另设合理限制。

### J06 节点响应大小限制发生在完整分配内存之后

`JudgerNodeTransport` 使用 `BodyHandlers.ofString()` 完整接收响应，然后才检查字符串是否超过 40 MiB。远端节点可以让业务 JVM 先分配过大的响应，后续拒绝不能撤销此前消耗的内存。字符串字符数也不是网络字节数。[响应处理](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/JudgerNodeTransport.java:22)

本次在本地使用同样的 JDK API，固定 41 MiB 响应完整写入字符串后才被大小检查拒绝。测试没有加载 Spring Transport bean，也未尝试更大数据或 OOM；部署 JDK 21 的官方文档确认 `ofString()` 在返回响应时已完整接收字符串。[响应证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/node-response-results.json)、[JDK 21 文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.BodyHandlers.html#ofString())

建议使用按字节限额、超限取消订阅的 BodySubscriber，或流式读取并在达到限额时关闭响应。`Content-Length` 可以用于预拒绝，但不能代替实际读取限额；`/status`、租约响应和执行响应应使用各自需要的上限。

## 部署条件与加固风险

### 缺少 pids 控制器时进程限制没有强制生效

`-no-fallback` 没有覆盖所有安全配置。当前 Go 源码在缺少 memory 控制器时可报错退出，但缺少 pids 控制器仅写入警告。设置进程限额的代码还忽略 `ErrNotInitialized` 和文件不存在错误，执行参数中没有替代的 `RLIMIT_NPROC`。因此部署缺少 pids 控制器时，32 或 64 的进程限额可能不生效。[控制器检查](/Users/pllysun/Code/claude/sap/sap2026/third_party/go-judge/env/env_cgroup_linux.go:147)、[资源限制执行](/Users/pllysun/Code/claude/sap/sap2026/third_party/go-judge/env/linuxcontainer/environment_linux.go:179)

Agent 启动时只验证 `/config` 能否成功返回，未检查内容。本次原始 `Agent.launch` 在合成响应仅包含 memory、没有 pids、空挂载且 UID 0 时仍报告 RUNNING。该测试没有启动真实 Linux 引擎；UID 0 是验证未校验配置的合成值，不能视为发现生产以 root 执行用户代码。[启动证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/degraded-startup-results.json)

此项评为 **P2 条件风险**。既有 Linux 记录包含 pids 控制器，并成功限制进程数量；本次没有证明该记录对应的部署缺少控制器。建议启动时要求 memory、pids 和必要 CPU 控制器完整可用，对限制设置失败停止接受任务，并用小规模实际 fork 检查确认限额。

同样，挂载配置文件缺失时，Go 会回退到默认挂载宿主容器的 `/usr`、`/bin` 等目录，而不是拒绝启动。建议明确要求项目的 mount.yaml 存在，验证来源、只读标志和用户身份，再发布可用状态。[默认挂载回退](/Users/pllysun/Code/claude/sap/sap2026/third_party/go-judge/env/env_linux.go:104)

### 用户代码仍能触达部分 namespace 内核路径

内层 seccomp 明确允许 `clone`、`clone3` 和 `unshare`。既有实测中创建新 user namespace 返回成功，而 mount、network namespace 操作被拒绝。[内层过滤器](/Users/pllysun/Code/claude/sap/sap2026/third_party/go-judge/seccomp/moby.yaml:277)、[namespace 实测](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/oj-sandbox-results.json)

成功创建嵌套 namespace 只是内核攻击面证据，不能解释为提权或逃逸。当前证据没有一个可在该内核上利用的具体漏洞链；老发行版会回补补丁，也不能仅凭 `3.10` 版本号判定已存在可利用 CVE。

业务容器增加了 `SYS_ADMIN`、`SYS_PTRACE`、`SYS_RESOURCE` 并放宽外层系统路径限制。虽然业务 Java、Redis、OCR 被降权，root 控制进程仍与业务数据同处一个外层容器；控制进程被攻破后的影响范围较大。[容器能力配置](/Users/pllysun/Code/claude/sap/sap2026/docker/docker-compose.judger.yml:7)、[业务降权](/Users/pllysun/Code/claude/sap/sap2026/docker/entrypoint.sh:408)

建议优先让判题在独立 node-only 主机运行，并使用受维护且经过完整回归验证的宿主内核。若要加强系统调用策略，应为执行用户代码的阶段采用更严格的规则，限制创建新 namespace，同时保留 Java 和 Rust 正常线程需要的 clone 行为。直接删除所有 clone 许可会损坏正常运行。隔离要求较高时，可评估独立内核的虚拟机方案；当前报告未进行这类产品选型。

### 核心函数驱动无法仅靠接口隐藏保证保密

FUNCTION 模式把用户代码嵌入驱动源码，编译错误的 stderr 最多返回 16,000 字符且没有过滤驱动源码。用户可以用预处理指令影响后续驱动代码的编译，使编译器诊断引用驱动内容。Python FUNCTION 模式下，合并源码本身也存在于用户代码可读取的工作目录。[源码合并](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/JudgeEngine.java:33)、[诊断返回](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/JudgeEngine.java:63)

本次本地 Apple Clang 编译合成驱动，诊断包含 `PRIVATE_DRIVER_MARKER`；生产 GCC 的格式未重新验证。此项评为 **P3 条件信息泄露**，不是宿主逃逸，也没有证明隐藏测试答案或参考解已泄露。[合成驱动证据](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/function-driver-results.json)

建议把同进程驱动视为可被用户观察的代码，不在其中放秘密或参考答案；需要保密的比较逻辑继续留在沙箱外，并限制诊断回显。仅从题目详情 JSON 删除 `functionDriver` 字段不能建立保密边界。

### 默认配额不能阻止持续占用判题资源

每日运行与提交上限默认是 0，即不限制。现有保护包含每个账号最多两个未完成任务、有限队列、逐用例时间限额、任务保护期限和通用写请求限流，因此不存在已经证明的“无限并发”。但代码可以合法消耗额度内的全部编译或运行时间，持续占用节点。[配额判断](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/java/com/sap/service/judger/OjService.java:376)、[默认配置](/Users/pllysun/Code/claude/sap/sap2026/sap-backend/src/main/resources/application-docker.yml:41)

建议按账号累计编译和执行 CPU 时间，设置符合业务规模的每日额度，并为 OJ 建立独立限流和调度公平性。上线额度数值需根据实际正常使用量确定；本次未确认生产是否已经覆盖默认值。

## 已观察到有效的保护

以下结果仅覆盖相应测试与已说明的配置，不是不存在其他绕过路径的证明。

- 当前后端源码把用户源码、stdin 当作数据传入，五种语言的固定命令不包含用户可插入的命令片段。Java 使用的 shell 命令也是固定字符串，本次未发现这条链路的 shell 注入。
- 源码上限 65,536 字符、自定义输入上限 1,048,576 字符，Agent 的 4 MiB 请求体上限及错误令牌、无效租约拒绝均已验证。
- 现有权限测试限制普通账号访问管理接口、读取他人源码和读取正式提交的隐藏输出；错误答案不会被直接判为通过。
- 既有 Linux 记录显示用户程序 `CapEff=0`、`NoNewPrivs=1`、`Seccomp=2`，读取应用目录、令牌、Docker socket、父进程环境失败；工具链只读，后续任务看不到此前临时标记。
- 既有记录中 CPU 循环、长时间休眠、内存和输出超限均被限制；受限进程创建、磁盘和 inode 测试满足预期，子进程被清理，符号链接导出没有读出外层 canary。

网络证据应单独理解：现有实测允许创建 socket，但连接外层回环、引擎端口和外部地址失败。测试外层容器还使用了 `network=none`，因此这一记录对生产外网阻断的证明有限；允许创建 socket 本身不等同于已经突破网络隔离。

初始测试记录中的未认证 `/config` 返回 200 是上游注册顺序所致，执行入口 `/run` 的未认证请求返回 401。初始记录的失败项还包含错误的 socket 禁用假设及运行器错误，不能按“六个失败等于六个漏洞”统计。最终 34 项中的两个不满足预期项分别是嵌套 user namespace 和令牌持有者读取外层 canary，均已按实际含义解释。

## 修复顺序与验收要求

| 顺序 | 修复工作 | 完成后的验收证据 |
| --- | --- | --- |
| 1 | 限制 Agent 的完整执行 JSON，禁止 src，固定命令及资源参数 | 持有效机器令牌仍无法导入允许目录外的合成文件，正常五语言编译运行通过 |
| 2 | 修复 TLS 握手阻塞和认证前慢连接，设置连接与线程限额 | 单条未完成握手或未发送请求行的连接不影响正常状态探测；超时后线程和 FD 回收 |
| 3 | 限制同一租约的并发，现场检查期限，绑定产物 | 同一租约并发第二请求被拒绝，过期租约立即拒绝，不影响顺序编译与用例执行 |
| 4 | 增加展示输出累计限额及流式节点响应限额 | 多样例任务的保留内容不超过任务上限；超大及分块响应在读取限额处取消，判题比较正确 |
| 5 | 对控制器、挂载、运行身份和实际隔离做启动校验 | 缺少 pids 或 mount.yaml 时节点保持不可用，真实原生 Linux 验证资源限额及清理 |
| 6 | 缩小内核攻击面并分离业务与判题故障域 | 独立节点无业务挂载，实际过滤器哈希可追溯，五语言线程及编译回归通过 |

复测应覆盖内置 cgroup v1 节点和独立 cgroup v2 节点，保存镜像 digest、Agent 与实际 seccomp 哈希、宿主补丁信息和控制器列表。在这些信息核对之前，本报告支持当前源码缺口的修复优先级，不构成线上环境已通过逃逸防护验收的声明。

## 交付证据

- [本次 Agent 边界复核](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/agent-boundary-results.json)
- [本次后端测试明细](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/backend-test-results.json)与[构建日志](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/backend-test.log)
- [节点响应复核](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/node-response-results.json)
- [安全配置缺失复核](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/degraded-startup-results.json)与[可复跑脚本](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/review-degraded-startup-probe.py)
- [核心函数诊断复核](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/function-driver-results.json)
- [当前源码与既有证据校验清单](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/review-2026-10-03/source-and-evidence-manifest.json)
- [既有 Linux 隔离实测记录](/Users/pllysun/Code/claude/sap/sap2026/ops/security-audit/oj-sandbox-results.json)
