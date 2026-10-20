# 单容器部署

`pllysun/lume:1.1.1` 内含用户端、管理端、Java 后端、判题控制服务、go-judge 与五语言工具链。只启动一个应用容器，外接 MySQL；没有 Redis 服务或依赖。RustFS / S3 可选。

## 环境与配置

使用原生 Linux amd64、cgroup v2、支持私有 cgroup 命名空间的 Docker Engine。验收环境为 Linux 6.1、Docker 20.10.24、cgroup v2。默认应用容器预算 4 CPU / 4 GiB；主机还需预留系统和数据库资源，同机运行 MySQL 与 RustFS 建议至少 8 GiB。Mac / Windows 的 Docker Desktop 不作为生产判题验收环境，分离版网站可连接 Linux 节点。

取得源码包或文档部署包，进入包根目录：

```bash
python3 tools/init.py
```

编辑私有 `.env` 的外部数据库地址、账号和密码。准备独立空数据库并允许建表及读写。参考：

```dotenv
DB_URL=jdbc:mysql://mysql.example.com:3306/lume?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&sslMode=VERIFY_IDENTITY
DB_USER=lume
DB_PASSWORD=replace-with-private-password
OJ_ADMIN_ACCOUNT=admin
OJ_ADMIN_PASSWORD=replace-with-private-bootstrap-password
APP_HEAP_MB=1024
NODE_CPU=2
NODE_MEMORY_MB=1280
NODE_CONCURRENCY=2
S3_ENABLED=false
```

初始化器已经生成随机密码，不要直接使用上面的占位符。Docker `--env-file` 文件中的值不加 shell 引号。默认 JDBC 示例域名不可直接连接；TLS 证书和参数按数据库服务配置。数据库可在其他机器或其他容器中，但必须能从应用容器访问；容器内 `127.0.0.1` 不代表数据库宿主机。

## 一条启动命令

`judge/judger-seccomp.json` 随源码包和文档包提供。在包根目录执行：

```bash
docker run -d --name lume --restart unless-stopped \
  --cgroupns=private \
  --cap-add SYS_ADMIN --cap-add SYS_PTRACE --cap-add SYS_RESOURCE \
  --security-opt seccomp=./judge/judger-seccomp.json \
  --security-opt apparmor=unconfined \
  --security-opt systempaths=unconfined \
  --memory=4g --cpus=4 --pids-limit=1024 --shm-size=256m \
  --stop-timeout=45 --env-file .env \
  -p 8080:8080 -v lume-data:/app/data \
  pllysun/lume:1.1.1
```

也可执行 `bash tools/run-container.sh`，参数相同。脚本可通过 shell 环境 `LUME_IMAGE`、`LUME_CONTAINER_NAME`、`LUME_HOST_BIND`、`LUME_HOST_PORT`、`LUME_DATA_VOLUME` 调整镜像、容器名、绑定地址、端口和卷名；这几个脚本参数需在运行脚本前设置。Compose 则读取 `.env` 中的 `LUME_IMAGE`、`BIND_ADDRESS` 和 `HTTP_PORT`。

部署 HTTPS 代理时，将发布端口改为 `-p 127.0.0.1:8080:8080`，或执行：

```bash
LUME_HOST_BIND=127.0.0.1 bash tools/run-container.sh
```

不要加 `--cgroupns=host`，不用 `--privileged`，不挂宿主 cgroup、Docker socket 或宿主根目录。私有命名空间行为见 [Docker cgroup 文档](https://docs.docker.com/engine/containers/runmetrics/)，命令参数见 [docker run](https://docs.docker.com/reference/cli/docker/container/run/)。

## 启动和工作原理

1. 根进程仅在本容器的私有 cgroup 根目录启用 CPU、内存和进程控制器，创建网站与沙箱子树。缺少必要能力、控制器或预算时直接失败，不降低隔离要求。
2. 持久卷中自动生成内部节点令牌。控制服务只监听 `127.0.0.1:5051`，无需对外开放；只收到节点资源与令牌配置，不继承 MySQL / S3 凭据。
3. 判题初始探针通过后，启动 Java 网站。Java 使用 UID/GID 2000，capabilities 清零、开启 no-new-privileges；学生程序使用受限 rootfs、独立命名空间、seccomp 和 cgroup。
4. MySQL 创建必要表、管理员和语言配置，不导入题库。会话保存在 `auth_state` 表，过期记录定期清理。
5. 任何必需进程意外退出，监督进程停止其他进程并退出；Docker 重启策略重启整个容器。正常停止向两部分发送终止信号，保留数据。

合并容器为了建立沙箱授予三个 Linux capabilities 并解除默认 AppArmor 挂载拦截，仍使用项目 Docker seccomp；用户程序另有 go-judge seccomp。此设计用于单机单实例。需要更明确的进程部署边界或独立扩容时，使用 `lume-server` / `lume-worker` 分离版；同样不需要 Redis。

## 资源设置

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `APP_HEAP_MB` | 1024 | Java 最大堆；网站子树另预留 512 MiB |
| `NODE_CPU` | 2 | 沙箱共享 CPU 总预算 |
| `NODE_MEMORY_MB` | 1280 | 节点执行预算；沙箱子树另预留 256 MiB |
| `NODE_CONCURRENCY` | 2 | 控制服务请求上限；内置节点调度固定为 1，独立节点可按资源配置 |
| `--memory` | 4g | 必须不少于两部分预约内存总和 |
| `--cpus` | 4 | 合并容器整体 CPU 预算 |

网站与判题共享一个容器，所以不要只给它 1 GiB。语言单次限制、队列、每日提交限额、整组时限与节点启停在管理端配置；外部 DB / S3、容器预算属于部署配置。内置节点地址由合并部署自动指定，不需要配置 `JUDGER_AGENT_ENDPOINT`。

## 验收、持久化与升级

```bash
docker ps --filter name=lume
docker logs --tail 100 lume
python3 tools/ojctl.py login
python3 tools/ojctl.py health
```

入口：用户端 `/`、管理端 `/admin/`、探针 `/api/ping`。健康检查同时检查网站与内置控制服务。管理员主动停止节点后，容器仍健康，但判题不接收新任务；重新启动节点即可恢复。

首次使用应检查题目数量为 0、五语言启用、节点 `RUNNING` 且 `capacity > 0`。按 AI 手册在自己的隔离环境生成一题、完成三轮验证并用练习账号运行和提交。

必须同时保存外部 MySQL、`lume-data`、私有 `.env`；使用 S3 时另备份私有桶。数据卷包含节点凭据加密密钥与内部节点令牌，不能公开。SQL 保存账号、会话、题目、设置、任务和历史；正常重启后会话仍可用。清理卷会丢失本地密钥，不能用于普通升级。

升级前备份并等待任务结束，拉取新固定版本，停止并删除旧容器（保留卷），用相同配置、端口和卷重新运行。不得同时启动两个主服务写同一业务库。Compose 用户执行 `docker compose pull app && docker compose up -d app`。

从 1.0.0 分离版升级时停止旧主服务和判题机，保留原 MySQL 与应用卷，替换为新镜像；旧 Redis 中的登录不迁移，用户需重新登录一次。1.1.1 新会话之后存于 MySQL。切换 split/bundle 时确保沿用实际卷名；内置节点地址会更新为当前部署地址，已有节点禁用状态会保留。不要把原协会业务整库接到本独立项目。

## 排障

| 现象 | 处理 |
| --- | --- |
| cgroup namespace / controllers 报错 | 核对原生 Linux amd64、cgroup v2、私有命名空间与完整启动参数 |
| cannot delegate cgroup | 核对三个 capabilities、项目 seccomp 与 AppArmor / systempaths 参数 |
| memory budget 报错 | 增加容器内存，或合理调整网站与节点预约，不能关闭资源检查 |
| MySQL 连接失败 | 核对容器可达地址、密码、TLS、建表权限和独立数据库 |
| 节点 capacity 为 0 | 查看探针和语言预算，核对节点启停状态；不取消隔离 |
| health 正常但无题目 | 首次题库本来为空，按 AI 手册显式创建、验证、发布 |
| SSE 不逐步显示 | HTTPS 代理关闭响应缓冲，并设置足够读取超时 |

更完整的节点、备份、权限和发布说明见 [部署与运维](DEPLOYMENT.md)。
