# pllysun.top 独立判题节点部署记录

验收日期：2026-10-02，Asia/Shanghai。业务平台仍运行 `pllysun/sap:1.5.10`，本次没有实现题单业务或升级主平台镜像。

## 部署结果

管理端 → 算法题库 → 统一管理 → 判题节点，新增节点 **#4「pllysun.top 独立判题节点」**。内置节点 #1 继续启用，资源负载均衡自动分配任务。

| 项目 | 独立节点 |
| --- | --- |
| 宿主系统 | Debian 12、Linux 6.1.0-37-amd64、原生 x86_64、cgroup v2 |
| 宿主资源 | 16 个逻辑 CPU、31,492 MiB 内存 |
| 判题资源配额 | 8 CPU、6,144 MiB，8 个并发任务 |
| 每个沙箱 | 分配一个独立 CPU，CPU 0–7；按题目语言限制运行时间、内存等 |
| 容器控制器配额 | 另设 1 CPU、512 MiB；判题引擎进入专用沙箱资源子树 |
| 工具链 | GCC 15.3 / C23 / C++23、OpenJDK 27、CPython 3.14.7、Rust 1.98.1 / Edition 2024 |
| 容器 | `sap-judger-node`，node-only，不启动业务、数据库或 OCR |
| 平台连接地址 | `http://172.17.0.1:15051`，通过 SSH 加密隧道转发到节点回环 5051 |
| 合计容量 | 独立节点 8 + 内置节点 1 = 9 个并行任务 |

该服务器已有 18 个 Docker 容器。原 Docker 服务、数据目录与网络配置保持运行，采用单独的 judge Docker daemon 及数据根目录。原 `/var` 分区空间较少，判题镜像和数据保存在空间充足的根分区 `/opt/sap-judger`。

## 连接与隔离

平台无法从服务器公网地址直接访问节点端口，因此建立平台到节点的持久 SSH 隧道。节点控制器仅监听 `127.0.0.1:5051`，执行引擎仅监听 `127.0.0.1:5050`；不开放判题公网端口。

平台隧道只绑定 Docker 网关 `172.17.0.1:15051`，由 `/etc/systemd/system/sap-judger-pllysun-tunnel.service` 管理，已设置开机启动与自动重连。使用独立 `sap-judge-link` 账号，其 SSH key 仅允许转发到节点 `127.0.0.1:5051`，不能取得交互式 shell。私钥与已核验主机密钥位于平台 `/etc/sap/judger-pllysun/`，权限受限。

管理 API 与节点机器心跳使用独立认证。节点令牌位于节点 `/opt/sap-judger/node.token`（0600），不记录在本文件或仓库。平台加密保存令牌，备份平台数据库时同时备份数据卷中的 `judger-node.key`。

节点容器没有 privileged 权限，仅开放 go-judge 所需能力、系统调用及专用 cgroup 子树。Debian 的默认 AppArmor profile 会阻止沙箱 mount，当前控制容器采用 `apparmor=unconfined`；go-judge 任务沙箱仍独立使用 mount namespace、资源控制和内部 seccomp，租约与认证接口均通过实测。

## 文件、服务与镜像

节点服务器：

| 路径/服务 | 用途 |
| --- | --- |
| `/opt/sap-judger/daemon.json` | 第二个 Docker daemon 配置，不创建 bridge、不调整宿主 iptables |
| `/run/sap-judger-docker.sock` | 判题专用 Docker socket |
| `/opt/sap-judger/docker` | 判题 Docker 镜像/容器数据，不复用 `/var/lib/docker` |
| `/etc/systemd/system/sap-judger-budget.service` | 开机创建 8 CPU / 6 GiB 专用 cgroup 预算及 cpuset 委派 |
| `/etc/systemd/system/sap-judger-docker.service` | 判题专用 Docker daemon，依赖资源准备服务 |
| `/opt/sap-judger/prepare.py` | cgroup v1/v2 资源准备脚本，本机使用 v2 |
| `/opt/sap-judger/agent.py` | 当前节点控制器，挂载到容器 `/opt/judger/agent.py` |
| `/opt/sap-judger/seccomp.json` | 节点容器 seccomp 配置 |
| `/opt/sap-judger/container-launch.json` | 当前容器启动参数，恢复时保留令牌与数据挂载 |
| `/opt/sap-judger/data/judger-agent/state.json` | 启停意图，控制容器重启后恢复 |
| `/opt/sap-judger/data/judger-agent/engine.log` | go-judge 引擎日志 |
| `/etc/logrotate.d/sap-judger-node` | 引擎日志每日检查轮转（含 10 MiB 大小条件），保留 7 份并压缩旧副本 |
| `/opt/sap-judger/native-probe.json` | 原生运行底座最近一次验收结果 |

固定镜像 ID：`sha256:435a65685676e58288b59191425cdab9821b78ec1f12c6466ab0045877e1f785`。由于服务器访问 Docker Hub 失败，传输同一镜像的本地精确归档，校验 SHA-256 后加载；临时归档已删除。

运行环境标识保持 `go-judge-e9d70a0-gcc15.3-jdk27-python3.14.7-rust1.98.1-v1`，与业务平台一致。节点控制器 SHA-256 为 `f65fdbeed07892be980cc0a8e6556d3eed4ffa6478b9c9fd144578fd6b702910`。

当前镜像挂载修正的控制器，而不是声称原始 1.5.10 镜像已包含所有修正。以下改动已保存到仓库，后续镜像构建应保留：

- `docker/judger-agent.py`：HTTP/1.1 兼容 SSH 隧道上的 Java HTTP 连接复用；重启前回收空的引擎 cgroup 子树；显式确认写入 cgroup 成功；允许按单核分配任务。
- `docker/node-start.sh`：将 `NODE_CPU_SETS` 转为节点控制器的 `--cpu-sets` 参数。
- `sap-admin/public/judger-node/prepare.py`：委派 cgroup v2 的 cpuset（宿主支持时）。

每个 worker 限定到单独 CPU，可避免 Rust LLVM 链接器根据整台 16 核服务器创建过多线程而触发既有编译内存限制。编译和题目资源限制没有因此扩大。重启清理只作用于该节点的专用引擎子树，写入资源预算失败时拒绝启动执行引擎。

## 验收证据

- [原生资源及并发验收](pllysun-node-native.json)：真实八任务同时执行，各在独立 CPU；第九租约被拒绝；未认证控制与未租约执行被拒绝；宿主私有路径及环境变量隔离；CPU 限制有效。
- 同份原生验收验证三次启停、所有沙箱进程退出，停止后沙箱 cgroup 匿名内存由 109,510,656 bytes 归零。轻量控制器继续运行以接收再次启动命令。
- [线上正式提交验收](pllysun-node-production.json)：C、C++、Java、Python、Rust × 完整程序/核心函数，共 10 次正式提交，全部在节点 #4 通过两数之和的 30 个测试用例，共 300 次用例执行。代码快照保留，隐藏用例不返回用户。
- 运行中停止独立节点，任务 #414 完整重试到内置节点，最终 AC；保留 NODE_ERROR、REASSIGNED、FINISHED 时间线。重连 SSH 隧道后运行通过；控制容器重启后自动恢复启用状态，任务 #416 在独立节点返回 AC、输出 42。
- 节点管理接口与机器心跳均出现在平台日志管理中。
- [管理界面检查](pllysun-node-ui.json)：两张真实在线节点卡片、资源报告及桌面管理视图加载正常，无页面运行异常。检查了多个宽度的横向溢出；该项不能替代移动端可用性验收，现有管理端侧栏在 390 px 下仍挤压内容。
- 最终检查：两个节点运行、任务租约归还，业务容器 healthy、隧道 active；判题 Docker daemon/budget 与 SSH 隧道均已配置开机恢复；原有 18 个容器持续运行。

本次只在新节点验收上述 10 次正式提交及资源/故障流程，不声称重新执行过整库所有题目。现有题库的多语言参考代码完整验证报告仍独立保留。

## 日常操作与恢复

首选管理端的启动/停止按钮。停止会终止沙箱、释放沙箱内存并撤销租约，平台会避开该节点。保留控制服务和令牌，以便恢复；不要通过删除令牌来停用节点。

节点宿主查询：

```sh
systemctl status sap-judger-budget.service sap-judger-docker.service
docker -H unix:///run/sap-judger-docker.sock ps
docker -H unix:///run/sap-judger-docker.sock logs --tail 80 sap-judger-node
```

平台宿主查询：

```sh
systemctl status sap-judger-pllysun-tunnel.service
journalctl -u sap-judger-pllysun-tunnel.service -n 40 --no-pager
```

节点整体退出时先从平台停止节点，待任务切换/结束后再停止其容器或专用 daemon。恢复时先启动预算服务、专用 daemon 与容器，确认隧道在线，再从平台启用节点并刷新资源。日常操作该容器必须带 `-H unix:///run/sap-judger-docker.sock`，不要误操作原 Docker 上的其他业务。

重建容器时使用保存的启动参数，保留 token、data、agent.py、专用 cgroup 子树、cpuset 与安全参数；普通 `docker restart` 不会更新环境或启动参数。当前 daemon 已为同机双 Docker 单独设置 socket、data-root、exec-root、pidfile、containerd namespace 与网络选项，不能替换成共享默认配置。

临时密码文件、访问令牌测试缓存和诊断日志已清理；运维 SSH 私钥留在受限缓存位置以便后续维护，不纳入仓库或文档。
