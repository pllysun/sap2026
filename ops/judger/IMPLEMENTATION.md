# 单容器算法题库

应用内提供用户 `/oj`、做题 `/oj/:id` 和管理端 `/admin/oj`。后端新类严格位于各层 `judger` 子包。数据库增加 `oj_problem`、`oj_language`、`oj_submission`；旧业务表保持现有行为。

## 题目、模式与发布

每题保存来源平台、原题编号、HTTPS 来源链接（原创无需链接）、说明、难度、标签、测试用例、语言模板、参考实现及版本。STDIO 为完整程序；FUNCTION 用题目专属可信驱动处理输入、数据结构和输出。管理员可选择单模式或双模式、默认模式，修改样例及隐藏用例，导入/导出 JSON 测试集。

保存即草稿，并使旧验证失效。发布需要当前修订、当前启用语言/统一限制、当前工具链通过全部用例，每个语言和模式运行三轮。禁用题目对用户隐藏并拒绝提交。语言配置变化后原发布题目暂时隐藏，完成新配置验证后可恢复。导入题包本身不发布。

提交保存当时题目和语言限制的快照，异步持久化排队；重启后未完成任务重新排队。单用户最多两个未完成任务，全队列最多50个，同时只运行一个编译/判题任务。用户运行样例或自定义输入可查看输出，正式提交只返回判定和统计，不返回隐藏输入、期望输出、驱动和参考代码。提交记录按用户隔离。

## 判题底座

源码：`third_party/go-judge`，锁定 `e9d70a0d9a3df0c62182a6e7090d7af650a1d5f8`，MIT，含源码及许可。编译启用内层 seccomp；唯一上游补丁是启动日志隐藏 API token。SDK 镜像锁定 digest，最终单容器内隔离于 `/opt/judger/rootfs`，不覆盖业务 Java 21 或 OCR Python。

工具链：GCC/G++ 15.3，C23/C++23；OpenJDK 27 GA；CPython 3.14.7；Rust 1.98.1 / Edition 2024。Java 参考及用户程序使用 JDK27，业务服务继续 Java21。

沙箱 API `127.0.0.1:5050`，无外部端口；每次启动生成仅 root/业务用户可读的随机令牌。执行环境不继承业务变量，沙箱文件系统只暴露工具链、设备和临时工作目录，不含应用、上传或数据目录；网络独立。stdout256 KiB/stderr64 KiB，缓存产物32 MiB，临时目录64 MiB，逐用例 CPU、内存、进程限制以及每组合120秒总预算。

宿主当前 CentOS7/cgroup v1，必须先准备专属 cgroup 和 user namespace。仅给容器增加 SYS_ADMIN/SYS_PTRACE/SYS_RESOURCE，外层使用 Docker 默认 seccomp加 pivot_root；无需 privileged。只把各控制器的 `sapjudger` 子树挂入容器，该子树合计640 MiB、一个CPU、256进程；正常业务进程不在此子树。Java/Redis/OCR以UID2000运行，额外能力全部清除、禁止提权；Nginx去掉判题新增的三个能力。

`ops/judger/host-runtime.py` 实施资源预算；`install-host-runtime.py` 安装 `/etc/sap/judger-seccomp.json`、sysctl 和先于 Docker 启动的 systemd 服务。宿主重启后自动准备挂载目录；安装无需重启Docker。`ops/deploy-preserving-container.py --judger` 按当前正式容器的环境、数据挂载、端口和网络重建，后续检测到既有 JUDGER_ENABLED=true 自动保留判题配置。健康检查同时覆盖后端与沙箱，失败自动恢复旧容器。

## 题库交付与验证

50题：LeetCode40、洛谷5、原创5；简单20、中等22、困难8。题面自行整理，来源可追溯，测试集自主生成，含边界及确定性随机数据。题包和索引在 `problem-packs/`，只有目录内6个pilot用于当前正式发布，其余44个flat JSON等待用户同意后导入。

真实服务器五语言/全部68种题目模式，三轮共340组合、23325次执行全部通过。详见 `corpus-gojudge-validation.json`。样例标记的元数据修正记录在同一报告中，判题内容未改变。应用端另外以自身队列、编译接口、校验器验证六题并做提交与权限检查；结果在 application-*-validation.json。后端全项目870测试通过，新增8个判题边界和隐私测试已在最终修改后复测。
