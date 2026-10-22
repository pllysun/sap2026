# 部署与运维

版本 1.1.2 提供合并版 `pllysun/lume`、网站 `pllysun/lume-server` 和独立判题节点 `pllysun/lume-worker`。三个镜像均不依赖 Redis；账号会话与业务数据存于 MySQL。生产题库初始为空，不预设题目。

## 推荐：合并版

原生 Linux amd64、cgroup v2、Docker；应用预算 4 CPU / 4 GiB，主机为系统和基础服务额外留资源。默认 `compose.yml` 只启用一个 app 服务：

```bash
python3 tools/init.py
# 编辑私有 .env：填写自己的外部 MySQL 地址、账号、密码
docker compose up -d
docker compose ps
docker compose logs --tail 100 app
python3 tools/ojctl.py login
python3 tools/ojctl.py health
```

也可一次 `docker run`；完整命令、能力、内存和私有 cgroup 原理见 [单容器部署](SINGLE_CONTAINER.md)。此模式不用宿主准备脚本，也不挂载宿主 cgroup。不要为合并版改成 host cgroup namespace。

初始化器创建 `.env`（0600）与 `secrets/judger-token`（供分离版使用）。合并版内部令牌自动生成于 app-data；不要公开私有配置或数据卷。默认管理员账号 admin、随机密码在 `.env`，已有账号不被 bootstrap 密码覆盖。管理员修改密码后可移除部署环境的 OJ_ADMIN_PASSWORD。

默认关闭注册；管理端“系统设置”开启，或管理员创建练习用户。用户端 `/`，管理端 `/admin/`，公开探针 `/api/ping`，默认端口 8080。公网使用 HTTPS；设置 `BIND_ADDRESS=127.0.0.1` 供宿主代理访问。

外部 MySQL 需独立空数据库、建表与读写权限；TLS 按自己的服务配置。生成器中的 `mysql:3306/oj` 只适用于显式选择的 Compose mysql profile。

## 可选基础服务

数据库与对象存储配置在私有 `.env` / Compose，业务配置在管理端。无需 Redis。选择本机数据库时保持生成的 JDBC 地址：

```bash
docker compose --profile mysql up -d --wait mysql
docker compose --profile mysql up -d
```

已有外部数据库就不启用 mysql profile。MySQL 镜像固定为 8.4，数据独立持久化。RustFS 为可选 S3 私有归档服务；连接和权限见 [存储手册](STORAGE.md)：

```bash
# 配置 S3_ENABLED、S3_ENDPOINT、S3_CREATE_BUCKET 等以后
docker compose --profile rustfs up -d
# 同机 MySQL 则同时加 --profile mysql
```

原生判题不能通过禁用 cgroup 或 seccomp 适配 Mac / Windows。网站界面可独立运行，判题连接 Linux 节点。

## 分离版：网站与判题机

`compose.split.yml` 的 server 包含两端前端和 Java 后端，worker 是独立五语言沙箱，仍无 Redis。外部 MySQL / S3 配置同合并版。

同机部署需要为 worker 准备一个专用宿主 cgroup 子树，仅在此模式执行：

```bash
python3 tools/init.py
# 编辑 .env；NODE_CGROUP_PREFIX 要与下方一致
sudo env NODE_CGROUP_PREFIX=lume-node sh tools/prepare-judge-host.sh
docker compose -f compose.split.yml --profile worker up -d
docker compose -f compose.split.yml --profile worker ps
python3 tools/ojctl.py login
python3 tools/ojctl.py health
```

准备脚本只操作指定子树，设定 2 CPU / 1536 MiB / 512 进程，禁用交换；已有进程时拒绝调整。宿主重启后需重新准备，按本机策略设置 systemd。节点执行预算默认 1280 MiB，在总预算中保留控制开销。只把该子树 RW 挂入 worker；不挂整个宿主文件系统、主服务数据或 Docker socket。

分离版网站以 UID/GID 2000、capabilities 全部移除、no-new-privileges 运行。worker 授予 SYS_ADMIN / SYS_PTRACE / SYS_RESOURCE，用于建立受限 rootfs、网络和进程命名空间；仍保留项目 Docker seccomp、go-judge seccomp 与 cgroup。

如网站只接外部节点，在 `.env` 设置 `JUDGER_BUILTIN_ENABLED=false`，不加 worker profile：

```bash
docker compose -f compose.split.yml up -d server
```

另一个原生 Linux 主机准备专用 cgroup 和独立的至少 32 字符随机令牌，以 `lume-worker:1.1.2` 运行节点。可在该主机使用同一份 split Compose，只启动 worker；以只读 secret 提供令牌，不传业务 `.env` 中的数据库与 S3 凭据。

5051 默认不发布到宿主。跨机接入通过私有网络或 HTTPS、限制来源的防火墙，并在管理端登记 name / endpoint / token / maxConcurrency。需要暴露控制端口时仅绑定受控内网地址。`NODE_TLS_CERT` / `NODE_TLS_KEY` 可配置只读证书；客户端校验 TLS，不跳过验证。节点必须上报兼容 runtimeId、正常资源探针和容量。

节点启停在管理端进行；停止节点保留控制服务。没有可用节点时返回明确不可用，不执行无沙箱的宿主代码。

## 配置边界

| 变量 | 说明 |
| --- | --- |
| LUME_IMAGE | 合并版，默认 pllysun/lume:1.1.2 |
| LUME_SERVER_IMAGE / LUME_WORKER_IMAGE | 分离版固定版本镜像 |
| HTTP_PORT / BIND_ADDRESS | Compose 网站端口与绑定地址 |
| DB_URL / DB_USER / DB_PASSWORD | 外部 MySQL 连接，两个模式均必需 |
| OJ_ADMIN_ACCOUNT / OJ_ADMIN_PASSWORD | 首次创建管理员，不覆盖已有账号 |
| APP_HEAP_MB | 合并版 Java 最大堆，默认 1024 MiB |
| NODE_CPU / NODE_MEMORY_MB / NODE_CONCURRENCY | 默认 2 / 1280 / 2；实际容量受资源探针限制 |
| NODE_CGROUP_PREFIX | 分离 worker 专用子树，默认 lume-node；合并版自动管理 |
| JUDGER_BUILTIN_ENABLED | 分离版是否登记本地 worker，合并版固定使用内置节点 |
| S3_* | 可选 RustFS / S3 基础服务连接与权限 |

注册、站点名称、队列容量、每日正式提交限额、整组判题时限、语言资源、节点策略、题目和题单在管理端配置，保存在 MySQL。超级管理员可设置队列 1–1000、每日提交 0–100000（0 为不限）、整组时限 10000–1800000 ms。

会话也存 MySQL，按独立站点使用独立业务库；本版按单主服务实例设计，不宣称支持多个主服务共享库进行集群调度。可以接多个独立判题节点。

## 账号与权限

| roleCode | 角色 | 能力 |
| --- | --- | --- |
| 0 | 超级管理员 | 题库、节点、审计、业务配置与账号 |
| 2 | 题库管理员 | 题库、题解、题单、节点与审计 |
| 3 | 练习用户 | 做题、训练、题单与个人档案 |

只有超级管理员可创建管理账号、重置他人密码、修改账号状态。系统保留可用超级管理员，不允许当前管理员禁用自己。密码至少 12 个字符，UTF-8 不超过 72 字节；重置密码使该账号旧登录失效。注册只创建角色 3，忽略请求中的管理权限。登录有账号和来源限流。

## HTTPS 与缓存

`docker/nginx.conf.example` 是宿主反向代理参考，将 HTTPS 请求代理到 127.0.0.1:8080。SSE 必须关闭 proxy_buffering，增加读取超时，否则过程到结束才显示。证书由部署者管理。

鉴权题包、题解和提交代码不放公共 CDN，不开启管理接口公共缓存；静态前端资源可正常缓存。归档包含隐藏测试与参考实现，必须保持私有。

## 备份与恢复

同时保存外部 MySQL、app-data、私有部署配置和节点令牌；使用 S3 时另保存私有归档桶。app-data 含节点凭据加密密钥，合并版另含内部节点令牌。仅备份 SQL 不足以恢复已有外部节点凭据。

可选同机 mysql 的备份例子：

```bash
umask 077
mkdir -p backups
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > backups/database.sql
docker compose run --rm --no-deps --entrypoint tar app -C /app/data -czf - . > backups/app-data.tar.gz
```

外部 MySQL 用供应方备份或受控连接导出。分离版卷备份命令将 app 服务替换为 `-f compose.split.yml` 的 server。私下复制 `.env` 和 secrets 到受控位置，不打进公开包。

恢复时停止主服务写入，导入 SQL，恢复同一份 app-data 和密钥，再启动核对账号、会话、节点与提交。原协会数据库不直接恢复到独立项目；题目应通过授权题包和题解接口导入。

## 升级和回退

1. 备份，记录旧镜像版本与 digest，等待运行任务结束。
2. 更新所用模式的固定镜像版本，保持数据库、实际数据卷与密钥。
3. 拉取并重建对应服务，执行 health 与一题实际提交。

```bash
# 合并版
docker compose pull app
docker compose up -d app
# 分离版
# docker compose -f compose.split.yml --profile worker pull
# docker compose -f compose.split.yml --profile worker up -d
python3 tools/ojctl.py health
```

1.0.0 的旧 Redis 登录不迁移，首次升级需重新登录；新版本会话存于 MySQL，之后重启保留。切换模式前停止旧部署，显式沿用同一实际数据卷，不同时运行两套主服务。内置节点地址自动匹配当前部署；禁用状态不被强制重置。

中断任务在启动时恢复到队列。工具链或语言资源签名变化后重新验证题目。回退必须检查表结构兼容性，必要时恢复备份；`down -v` 会删除持久数据，不能用于普通升级。

## 源码构建与推送

```bash
bash tools/build-local.sh
bash tools/build-images.sh
docker login
docker push pllysun/lume:1.1.2
docker push pllysun/lume-server:1.1.2
docker push pllysun/lume-worker:1.1.2
```

根 Dockerfile 的三个目标可独立构建，无需先发布中间镜像：

```bash
docker buildx build --platform linux/amd64 --target bundle -t pllysun/lume:1.1.2 --load .
docker buildx build --platform linux/amd64 --target server -t pllysun/lume-server:1.1.2 --load .
docker buildx build --platform linux/amd64 --target worker -t pllysun/lume-worker:1.1.2 --load .
```

源码构建全新 npm ci、前后端测试、产物构建，判题固定 SDK digest 和引擎源码。Maven 不自动使用 shell HTTPS_PROXY，代理环境用 `--secret id=maven-settings,src=/私有路径/settings.xml`；BuildKit 临时挂载，不写进运行层。Go 模块可通过 `--build-arg GOPROXY=...` 指定。个人代理凭据不写入 Dockerfile。

推送前验收真实编译、五语言三轮、权限、错误实现与重启恢复。发布新版本后验证匿名拉取与摘要，再同步 latest。已发布版本保留；不要将未经验收的版本覆盖到同一标签。

## 排障

| 现象 | 检查 |
| --- | --- |
| app 不健康 | MySQL、首次管理员、完整启动参数和应用日志，无需检查 Redis |
| 合并 cgroup 初始化失败 | 私有命名空间、原生架构、controllers、capabilities、内存预约 |
| 独立 worker capacity 为零 | 专用宿主子树、挂载、资源、令牌与启停状态 |
| runtimeId 不兼容 | 使用匹配版本，不伪造上报值 |
| 题目不可见 | 草稿、验证签名、语言设置、比赛与白名单 |
| 题解不能保存 | revision、八节、五语言、模式、安全 SVG |
| 401 / 403 | 正确站点、sap-token、账号状态和角色 |
| 429 | 等待任务与限流窗口，不用新账号绕过 |
| 下载超时 | 核对 Docker / BuildKit 镜像源和各自代理配置 |

## 会话加密与安全默认值（2026-10-18）

新初始化配置及 Compose 默认仅绑定 `127.0.0.1:8080`；公网访问通过宿主 HTTPS 反向代理。不要把示例的 `replace-*` 值当作密码，初始化器会生成私有随机凭据。

会话在 MySQL 中使用 AES-GCM 加密，包含令牌索引原文和会话内容；主索引仍使用 SHA-256。密钥默认在 app-data 卷的 `auth-state.key`，权限 0600，可用 `app.auth-state-key-file` 指向其它私有路径。升级会自动加密历史明文记录，保留有效会话。数据库与该密钥必须成套备份；不要删除、重生成或公开密钥，否则旧会话无法恢复。密文绑定记录和类型，不能在不同记录之间替换使用。

分离部署的 `http://worker:5051` 只用于可信的 Docker 私有网络；跨主机节点使用受控网络或配置 TLS，不能把明文端口暴露公网。节点必须由受信管理员维护：签名不能证明被攻陷的执行器诚实执行了程序。
# 升级到 1.1.2

本版增加多答案与沙箱校验器，不需要导入题库或修改既有题目。先备份 MySQL 与应用数据卷，保留当前私有 `.env`、节点密钥和全部数据卷。合并版将 `LUME_IMAGE` 更新为 `pllysun/lume:1.1.2`；分离版同时更新 `LUME_SERVER_IMAGE` 与 `LUME_WORKER_IMAGE` 为对应的 `1.1.2` 标签，再在原 Compose 项目目录拉取镜像并重建相关服务。不要执行 `down -v`。

从合并版改成分离版时，MySQL 与应用数据卷必须一同保留。应用数据卷中的加密密钥用于读取数据库内的认证状态，换成空卷会导致解密失败；这不是需要清空数据库的信号。若更换 Compose 项目名，先通过 `docker volume ls` 确认原卷名，并将新 Compose 的 `app-data` 显式映射为原卷，例如：

```yaml
volumes:
  app-data:
    external: true
    name: existing-project_app-data
```

`existing-project_app-data` 必须替换为实际的原应用卷名。分离版的 server 与 worker 使用同一份私有 `secrets/judger-token`。保留 MySQL、应用密钥和会话数据即可继续使用原账号、题目与登录状态。切换后先确认网站与判题节点健康，再在隔离环境验证多答案题。操作规范见 [MULTIPLE_ANSWERS.md](MULTIPLE_ANSWERS.md)。
