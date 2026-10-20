# Lume 1.1.1

独立算法练习与在线判题平台，初始题库为空。合并版内含用户端、管理端、Java 后端与 C / C++ / Java / Python / Rust 判题机，一次 docker run 即可启动应用；数据库外接 MySQL，无 Redis。独立判题机与分离版网站也保留。

| 镜像 | 用途 |
| --- | --- |
| pllysun/lume:1.1.1 | 单容器合并版 |
| pllysun/lume-server:1.1.1 | 两端前端和后端，连接独立节点 |
| pllysun/lume-worker:1.1.1 | 独立五语言判题机 |

三个镜像均为 linux/amd64，latest 与当前验收版本一致。固定版本或 digest 用于生产。完整源码和文档部署包包含 Compose、seccomp、初始化与 AI 工具；从包根目录操作。

## 单容器启动

原生 Linux amd64、cgroup v2、Docker，应用容器预算 4 CPU / 4 GiB，主机另给系统和基础服务留资源。外部 MySQL 准备独立空数据库、建表及读写权限。

执行 python3 tools/init.py，在生成的私有 .env 中填写外部 DB_URL / DB_USER / DB_PASSWORD。管理员密码随机生成，默认 admin。生成器的 mysql:3306/oj 仅用于显式选择的同机 Compose MySQL，外部部署必须修改。配置 S3 时同样修改私有 .env。


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


用户端 http://服务器地址:8080/，管理端 /admin/。公开注册默认关闭，管理员在“系统设置”启用或创建练习账号。首次只创建必要表、管理员和语言，不导入题目、题单、题解和提交。

合并版使用容器自己的私有 cgroup 自动准备判题子树，不挂宿主 cgroup、不需宿主准备脚本、不挂 Docker socket，不用 privileged。Java 进程 UID 2000、capabilities 清零、no-new-privileges；控制服务只监听容器回环 5051，不继承数据库 / S3 凭据。学生代码继续使用独立文件系统、网络、进程、seccomp 与 cgroup 资源限制。缺少隔离条件直接启动失败，不取消资源检查。

持久卷包含节点密钥，MySQL 保存账号、题目、任务、业务设置与登录状态。有效登录在重启后保留；旧版本 Redis 登录需升级后重新登录一次。必需进程意外退出时监督进程退出，由 Docker 重启；管理员主动停止节点不影响网站健康。

## Compose 和独立节点

默认 compose.yml 只有一个应用服务，基础设施为可选 profile：

```bash
docker compose up -d
# 若明确选择同机 MySQL
docker compose --profile mysql up -d --wait mysql
docker compose --profile mysql up -d
```

分离版使用 compose.split.yml，不依赖 Redis：

```bash
sudo env NODE_CGROUP_PREFIX=lume-node sh tools/prepare-judge-host.sh
docker compose -f compose.split.yml --profile worker up -d
```

只有独立 worker 需要宿主专用子树；保持 NODE_CGROUP_PREFIX 一致。网站容器无额外 capabilities。跨机节点通过内网或 HTTPS、防火墙与长随机令牌接入，在管理端登记；不要把业务数据库 / S3 配置传给节点。5051 是私有控制接口，默认不发布。

## RustFS / S3

在私有 .env 配置基础服务，不在管理端暴露密钥：

```dotenv
S3_ENABLED=true
S3_ENDPOINT=https://s3.example.com
S3_REGION=us-east-1
S3_BUCKET=sap-oj-private
S3_ACCESS_KEY=replace-with-scoped-access-key
S3_SECRET_KEY=replace-with-private-secret-key
S3_PATH_STYLE=true
S3_CREATE_BUCKET=false
```

提前创建私有桶，仅授权该桶的列表、上传和读取。自动建桶需显式开启 S3_CREATE_BUCKET 并授权 CreateBucket。管理端“题包归档”支持检查连接、按 ID 归档与下载，归档含隐藏测试和参考代码，仅管理员可读取，不提供公共 S3 地址。

同机可选 RustFS：配置 Endpoint=http://rustfs:9000、S3_CREATE_BUCKET=true 后执行 docker compose --profile rustfs up -d。官方镜像固定 rustfs/rustfs:1.0.1，数据与日志独立持久化，S3 在容器网内、控制台只绑定 127.0.0.1。已有外部 RustFS 就不启动此 profile。

## 1.1.1 更新

登录页和浏览器标题去除算法文案；题面四个字段支持净化的 Markdown、表格和代码高亮。核心函数五语言默认引入常用标准库，兼容原有显式导入、Python 类型标注与合法 future 导入；完整程序模式保持自行引包。详细列表见文档包 docs/CORE_MODE.md。

## AI 出题流程

先读包内 AGENTS.md、docs/AI_OPERATIONS.md、docs/PROBLEM_AUTHORING.md、docs/SOLUTION_STANDARD.md。

```bash
python3 tools/ojctl.py login
python3 tools/ojctl.py health
python3 tools/generate_example.py --output examples/my-problem --slug original-my-problem --seed 20261014
python3 tools/ojctl.py lint examples/my-problem/pack.json
python3 tools/ojctl.py create examples/my-problem/pack.json
# 用返回的 id 代入
python3 tools/ojctl.py validate <id>
python3 tools/ojctl.py publish <id>
python3 tools/ojctl.py solution <id> examples/my-problem/solution.json
```

CLI 用 sap-token，从私有配置读取凭据，令牌绑定站点，拒绝跨站复用和 API 重定向。远程使用 --url https://practice.example.com，选项在子命令前。代码只在判题沙箱执行，不在 AI 宿主机运行。

每题独立撰写题意、约束、输入输出、公开样例、初始模板、五语言参考代码、测试集与比较器。STDIO / FUNCTION 各模式均覆盖五语言和全部用例，服务端执行三轮，AC 后才能发布。复杂题需要独立暴力 oracle、固定随机种子、差分、边界与常见错误实现检查，不用参考实现自证。

题解固定八节，五语言注释代码、语言标签与高亮，复杂过程使用安全 SVG。比赛题解由后端在比赛结束后开放。修改题目携带 revision，内容变更重新验证；题解匹配当前 revision。批量导入显式提供自己的目录，题库不会自动预装。

## 运维和构建

基础设施连接在私有配置，注册、队列、每日提交、组时限、语言与节点策略在管理端。每个站点独立 MySQL，本版单主服务、多判题节点；不要同时运行两套主服务写同一库。

公网使用自己的 HTTPS 代理，SSE 关闭 proxy_buffering。升级前备份 MySQL、app-data、本地密钥、私有配置与 S3 桶，等待任务结束后更新固定镜像，保留卷。不要用 down -v 升级。工具链或语言资源签名变化需重新验证题目。

```bash
bash tools/build-local.sh
bash tools/build-images.sh
```

根 Dockerfile 的 server / worker / bundle 三目标可从源码独立构建，无需提前推送中间镜像。默认目标 bundle。许可证与上游材料见 LICENSE、docs/THIRD_PARTY_NOTICES.md；完整操作、接口、题包与测试设计见包内文档。
