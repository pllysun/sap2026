# CSUFTSAP Docker 部署指南

> 中南林业科技大学软件协会管理系统 — Docker 一体化部署
>
> DockerHub: [pllysun/sap](https://hub.docker.com/r/pllysun/sap)

---

## 目录

- [一、前提条件](#一前提条件)
- [二、项目架构](#二项目架构)
- [三、构建镜像](#三构建镜像)
- [四、运行容器](#四运行容器)
- [五、数据库配置](#五数据库配置)
- [六、日志管理](#六日志管理)
- [七、数据持久化与卷挂载](#七数据持久化与卷挂载)
- [八、HTTPS / SSL 证书配置](#八https--ssl-证书配置)
- [九、推送到 DockerHub](#九推送到-dockerhub)
- [十、常用运维命令](#十常用运维命令)
- [十一、常见问题](#十一常见问题)

---

## 一、前提条件

| 依赖 | 要求 |
|------|------|
| Docker | Windows：Docker Desktop；Linux：Docker Engine |
| Shell | Windows：PowerShell；Linux：Bash |
| 代理（可选） | 如果无法直接访问 DockerHub，需要本地代理运行在 `7890` 端口 |

> ⚠️ **首次构建**会拉取基础镜像（约 1GB），请确保网络畅通或代理可用。

---

## 二、项目架构

```
┌───────────────────────────────────────────────┐
│               Docker Container                │
│                                               │
│   ┌─────────┐                                 │
│   │  Nginx  │    /        -> User SPA         │
│   │  :80    │    /admin/  -> Admin SPA        │
│   └────┬────┘    /api/    -> Spring Boot      │
│        │ proxy_pass                           │
│   ┌────▼──────┐                               │
│   │  Spring   │                               │
│   │  Boot     │--> H2 Database                │
│   │  :8081    │   (or external MySQL)         │
│   └───────────┘                               │
│                                               │
│   /app/data     -> Database files             │
│   /app/logs     -> Log files                  │
│   /app/uploads  -> Upload files               │
└───────────────────────────────────────────────┘

  User SPA    = 用户端单页应用
  Admin SPA   = 管理端单页应用
  H2 Database = H2 文件数据库（或外部 MySQL）
```

### 访问地址

| 页面         | 地址                        |
|--------------|---------------------------|
| **用户端**   | `http://localhost/`        |
| **管理端**   | `http://localhost/admin/`  |
| **API 文档** | `http://localhost/api/doc.html` |

---

## 三、构建镜像

### 3.1 使用构建脚本（推荐，仅 Windows）

在**项目根目录** (`sap/`) 下打开 PowerShell：

```powershell
# 方式一：使用默认代理构建（代理地址：host.docker.internal:7890）
.\docker\build.ps1

# 方式二：不使用代理
.\docker\build.ps1 -NoProxy

# 方式三：指定自定义代理
.\docker\build.ps1 -Proxy "http://host.docker.internal:1080"

# 方式四：自定义镜像名和标签
.\docker\build.ps1 -ImageName "myuser/sap" -Tag "v1.0"
```

> 📌 **注意**：Docker 容器内的 `127.0.0.1` 指向容器自身，不是你的电脑。
> 脚本默认使用 `host.docker.internal:7890` 来访问宿主机的代理。
> 如果你的代理端口不是 `7890`，请用 `-Proxy` 参数指定。

### 3.2 手动构建

**PowerShell (Windows)：**

```powershell
# 不带代理
docker build -t pllysun/sap:latest -f docker/Dockerfile .

# 带代理构建（完整命令，实际验证通过）
docker build -t pllysun/sap:latest -f docker/Dockerfile `
  --build-arg HTTP_PROXY=http://host.docker.internal:7890 `
  --build-arg HTTPS_PROXY=http://host.docker.internal:7890 `
  --build-arg http_proxy=http://host.docker.internal:7890 `
  --build-arg https_proxy=http://host.docker.internal:7890 `
  .
```

**Bash (Linux/macOS)：**

```bash
# 不带代理
docker build -t pllysun/sap:latest -f docker/Dockerfile .

# 带代理构建
docker build -t pllysun/sap:latest -f docker/Dockerfile \
  --build-arg HTTP_PROXY=http://127.0.0.1:7890 \
  --build-arg HTTPS_PROXY=http://127.0.0.1:7890 \
  --build-arg http_proxy=http://127.0.0.1:7890 \
  --build-arg https_proxy=http://127.0.0.1:7890 \
  .
```

> 📌 Windows 下代理地址用 `host.docker.internal`，Linux 下直接用 `127.0.0.1`。

### 3.3 构建产物

构建完成后，查看镜像：

```bash
docker image ls pllysun/sap
```

输出示例：
```
REPOSITORY     TAG       IMAGE ID       CREATED          SIZE
pllysun/sap    latest    23be6c3726d8   2 minutes ago    669MB
```

---

## 四、运行容器

### 4.1 最简运行（快速体验）

```bash
docker run -d -p 80:80 --name sap pllysun/sap
```

打开浏览器访问：
- 用户端：http://localhost/
- 管理端：http://localhost/admin/

> ⚠️ 这种方式数据不持久化。容器删除后数据丢失。

### 4.2 标准运行（数据持久化）

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 80:80 `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 80:80 \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  pllysun/sap
```

### 4.3 指定端口

如果 80 端口被占用，可以映射到其他端口：

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 8080:80 `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 8080:80 \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  pllysun/sap
```

访问地址变为：`http://localhost:8080/` 和 `http://localhost:8080/admin/`

### 4.4 设置自动重启

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  --restart unless-stopped `
  -p 80:80 `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  pllysun/sap
```

### 4.5 生产环境完整部署（所有参数）

包含外部 MySQL 数据库、HTTPS 域名、SSL 邮箱、自动重启、全部数据卷挂载的完整命令：

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  --restart unless-stopped `
  -p 80:80 `
  -p 443:443 `
  -e DOMAIN="sap.example.com" `
  -e SSL_EMAIL="admin@example.com" `
  -e MYSQL_URL="jdbc:mysql://host.docker.internal:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" `
  -e MYSQL_USER="root" `
  -e MYSQL_PASSWORD="your_password" `
  -e JAVA_OPTS="-Xms256m -Xmx512m" `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  -v sap-ssl:/etc/letsencrypt `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 \
  -p 443:443 \
  -e DOMAIN="sap.example.com" \
  -e SSL_EMAIL="admin@example.com" \
  -e MYSQL_URL="jdbc:mysql://host.docker.internal:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" \
  -e MYSQL_USER="root" \
  -e MYSQL_PASSWORD="your_password" \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  -v /home/sap/sap-data:/app/data \
  -v /home/sap/sap-logs:/app/logs \
  -v /home/sap/sap-uploads:/app/uploads \
  -v /home/sap/sap-ssl:/etc/letsencrypt \
  pllysun/sap
```

> 📌 按需修改以下参数后直接复制运行：

| 参数 | 修改为 | 说明 |
|------|--------|------|
| `DOMAIN` | 你的域名 | 不需要 HTTPS 则删除此行和 `SSL_EMAIL` 行 |
| `SSL_EMAIL` | 你的邮箱 | Let's Encrypt 证书过期提醒邮箱 |
| `MYSQL_URL` | 你的数据库地址 | 不需要外部 MySQL 则删除 MYSQL 相关三行（自动用内嵌 H2） |
| `MYSQL_USER` | 数据库用户名 | — |
| `MYSQL_PASSWORD` | 数据库密码 | — |
| `JAVA_OPTS` | JVM 参数 | 可选，按服务器内存调整 |
| `-p 443:443` | — | 不需要 HTTPS 则删除此行 |
| `-v sap-ssl` | — | 不需要 HTTPS 则删除此行 |

---

## 五、数据库配置

系统支持两种数据库模式，通过**是否传入 `MYSQL_URL` 环境变量**自动切换。

### 5.1 默认模式：内嵌 H2 数据库

**不传入任何 MySQL 环境变量，即自动使用内嵌 H2 数据库。**

H2 是一个 Java 嵌入式数据库，运行在 MySQL 兼容模式下。数据存储在容器内的 `/app/data/sap.mv.db` 文件中。

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 80:80 `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 80:80 \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  pllysun/sap
```

**持久化数据库文件到宿主机指定路径：**

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 80:80 `
  -v D:\sap-docker\data:/app/data `
  -v D:\sap-docker\logs:/app/logs `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 80:80 \
  -v /home/sap/sap-data:/app/data \
  -v /home/sap/sap-logs:/app/logs \
  pllysun/sap
```

挂载后，你可以在对应目录下看到这些文件：

```
sap-docker/data/
  ├── sap.mv.db        ← H2 数据库主文件
  └── sap.trace.db     ← H2 追踪日志（可忽略）
```

> 💡 **为什么用 H2 而不是 SQLite？**
>
> 本项目的 JPA 实体类使用了 MySQL 特有的列定义语法（如 `COMMENT`、`TINYINT`），
> SQLite 不支持这些语法，会导致 JPA 自动建表失败。
> H2 数据库有 MySQL 兼容模式，完美支持所有 MySQL 语法，不需要修改任何代码。

### 5.2 MySQL 模式：连接外部 MySQL

传入 `MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD` 环境变量：

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 80:80 `
  -e MYSQL_URL="jdbc:mysql://your-host:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" `
  -e MYSQL_USER="root" `
  -e MYSQL_PASSWORD="your_password" `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 80:80 \
  -e MYSQL_URL="jdbc:mysql://your-host:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" \
  -e MYSQL_USER="root" \
  -e MYSQL_PASSWORD="your_password" \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  pllysun/sap
```

#### 连接宿主机上的 MySQL

如果 MySQL 运行在你的宿主机上（不是 Docker 里）：

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 80:80 `
  -e MYSQL_URL="jdbc:mysql://host.docker.internal:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" `
  -e MYSQL_USER="root" `
  -e MYSQL_PASSWORD="your_password" `
  -v sap-logs:/app/logs `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 80:80 \
  -e MYSQL_URL="jdbc:mysql://host.docker.internal:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" \
  -e MYSQL_USER="root" \
  -e MYSQL_PASSWORD="your_password" \
  -v sap-logs:/app/logs \
  pllysun/sap
```

> 📌 `host.docker.internal` 是 Docker Desktop 提供的特殊域名，指向你的宿主机。Linux 上需要在 `docker run` 时添加 `--add-host=host.docker.internal:host-gateway`。

#### 连接另一个 Docker 容器里的 MySQL

**PowerShell (Windows)：**

```powershell
# 1. 创建 Docker 网络
docker network create sap-network

# 2. 启动 MySQL 容器
docker run -d `
  --name sap-mysql `
  --network sap-network `
  -e MYSQL_ROOT_PASSWORD=root123 `
  -e MYSQL_DATABASE=sap `
  -v sap-mysql-data:/var/lib/mysql `
  mysql:8.0

# 3. 启动应用容器
docker run -d `
  --name sap `
  --network sap-network `
  -p 80:80 `
  -e MYSQL_URL="jdbc:mysql://sap-mysql:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" `
  -e MYSQL_USER="root" `
  -e MYSQL_PASSWORD="root123" `
  -v sap-logs:/app/logs `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
# 1. 创建 Docker 网络
docker network create sap-network

# 2. 启动 MySQL 容器
docker run -d \
  --name sap-mysql \
  --network sap-network \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=sap \
  -v sap-mysql-data:/var/lib/mysql \
  mysql:8.0

# 3. 启动应用容器
docker run -d \
  --name sap \
  --network sap-network \
  -p 80:80 \
  -e MYSQL_URL="jdbc:mysql://sap-mysql:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false" \
  -e MYSQL_USER="root" \
  -e MYSQL_PASSWORD="root123" \
  -v sap-logs:/app/logs \
  pllysun/sap
```

#### 连接腾讯云/阿里云 MySQL

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 80:80 `
  -e MYSQL_URL="jdbc:mysql://gz-cynosdbmysql-xxx.sql.tencentcdb.com:27118/sap?serverTimezone=Asia/Shanghai" `
  -e MYSQL_USER="sap" `
  -e MYSQL_PASSWORD="your_password" `
  -v sap-logs:/app/logs `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 80:80 \
  -e MYSQL_URL="jdbc:mysql://gz-cynosdbmysql-xxx.sql.tencentcdb.com:27118/sap?serverTimezone=Asia/Shanghai" \
  -e MYSQL_USER="sap" \
  -e MYSQL_PASSWORD="your_password" \
  -v sap-logs:/app/logs \
  pllysun/sap
```

### 5.3 环境变量速查

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `MYSQL_URL` | 否 | 不设则用 H2 | MySQL JDBC 连接 URL |
| `MYSQL_USER` | 否 | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | 否 | 空 | MySQL 密码 |

---

## 六、日志管理

### 6.1 查看容器控制台日志

```bash
# 查看最近 100 行
docker logs --tail 100 sap

# 实时跟踪日志
docker logs -f sap

# 查看指定时间段
docker logs --since "2026-03-30T00:00:00" sap
```

### 6.2 日志文件结构

容器内 `/app/logs/` 目录包含以下日志文件：

```
/app/logs/
  ├── sap.log                  ← 主日志（全部级别）
  ├── sap.2026-03-29.0.log     ← 历史日志（按日期切割）
  ├── sap-error.log            ← 错误日志（仅 ERROR 级别）
  ├── nginx-access.log         ← Nginx 访问日志
  └── nginx-error.log          ← Nginx 错误日志
```

### 6.3 挂载日志到宿主机

**使用 Docker 命名卷：**

```bash
docker run -d -p 80:80 -v sap-logs:/app/logs pllysun/sap

# 查看卷在宿主机的实际路径
docker volume inspect sap-logs
```

**挂载到指定目录：**

```powershell
# Windows - 日志保存到 D:\sap-docker\logs
docker run -d -p 80:80 -v D:\sap-docker\logs:/app/logs pllysun/sap
```

```bash
# Linux - 日志保存到 /home/sap/sap-logs
docker run -d -p 80:80 -v /home/sap/sap-logs:/app/logs pllysun/sap
```

### 6.4 日志轮转策略

| 配置项 | 值 |
|--------|-----|
| 单个文件大小上限 | 50 MB |
| 保留天数 | 30 天 |
| 总大小上限 | 1 GB（主日志）/ 500 MB（错误日志） |
| 切割规则 | 按日期 + 大小（每天或超 50MB 时切割） |

---

## 七、数据持久化与卷挂载

### 7.1 三个数据卷

| 容器路径 | 内容 | 是否必须挂载 |
|----------|------|-------------|
| `/app/data` | H2 数据库文件 | ✅ **强烈建议**（否则数据丢失） |
| `/app/logs` | 日志文件 | ✅ 建议（方便排查问题） |
| `/app/uploads` | 用户上传文件 | ✅ 建议（保留上传内容） |

### 7.2 推荐的完整挂载命令

**使用命名卷（推荐，Docker 管理）：**

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  --restart unless-stopped `
  -p 80:80 `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  pllysun/sap
```

**挂载到宿主机指定目录：**

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  --restart unless-stopped `
  -p 80:80 `
  -v D:\sap-docker\data:/app/data `
  -v D:\sap-docker\logs:/app/logs `
  -v D:\sap-docker\uploads:/app/uploads `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 \
  -v /home/sap/sap-data:/app/data \
  -v /home/sap/sap-logs:/app/logs \
  -v /home/sap/sap-uploads:/app/uploads \
  pllysun/sap
```

### 7.3 备份数据

**PowerShell (Windows)：**

```powershell
# 停止容器
docker stop sap

# 备份数据卷
docker run --rm -v sap-data:/data -v D:\backup:/backup alpine `
  tar czf /backup/sap-data-backup.tar.gz -C /data .

# 重启容器
docker start sap
```

**Bash (Linux/macOS)：**

```bash
# 停止容器
docker stop sap

# 备份数据卷
docker run --rm -v sap-data:/data -v /home/sap/backup:/backup alpine \
  tar czf /backup/sap-data-backup.tar.gz -C /data .

# 重启容器
docker start sap
```

---

## 八、HTTPS / SSL 证书配置

系统支持通过 `DOMAIN` 环境变量自动申请 Let's Encrypt 免费 SSL 证书，实现 HTTPS 访问。
**不传入 `DOMAIN` 变量时，只使用 HTTP，不会有任何影响。**

### 8.1 前提条件

| 条件 | 说明 |
|------|------|
| 域名已解析 | 域名的 A 记录必须已指向运行 Docker 的服务器 IP |
| 80 端口开放 | Let's Encrypt 通过 HTTP-01 方式验证域名所有权 |
| 443 端口开放 | HTTPS 服务需要使用 443 端口 |

> ⚠️ **重要**：如果域名未解析到该服务器，证书申请会失败。但系统会自动回退到 HTTP 模式，不会导致容器崩溃。

### 8.2 启用 HTTPS

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  --restart unless-stopped `
  -p 80:80 `
  -p 443:443 `
  -e DOMAIN="sap.example.com" `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  -v sap-ssl:/etc/letsencrypt `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 \
  -p 443:443 \
  -e DOMAIN="sap.example.com" \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  -v sap-ssl:/etc/letsencrypt \
  pllysun/sap
```

> 📌 `sap-ssl:/etc/letsencrypt` 卷用于持久化证书文件，避免重新部署时重复申请。

### 8.3 自定义联系邮箱

Let's Encrypt 要求提供邮箱用于证书过期提醒。默认使用 `admin@你的域名`，可通过 `SSL_EMAIL` 自定义：

**PowerShell (Windows)：**

```powershell
docker run -d `
  --name sap `
  -p 80:80 -p 443:443 `
  -e DOMAIN="sap.example.com" `
  -e SSL_EMAIL="your-email@example.com" `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-ssl:/etc/letsencrypt `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d \
  --name sap \
  -p 80:80 -p 443:443 \
  -e DOMAIN="sap.example.com" \
  -e SSL_EMAIL="your-email@example.com" \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-ssl:/etc/letsencrypt \
  pllysun/sap
```

### 8.4 SSL 环境变量速查

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `DOMAIN` | 否 | 不设则仅 HTTP | 申请 SSL 证书的域名 |
| `SSL_EMAIL` | 否 | `admin@域名` | Let's Encrypt 联系邮箱 |

### 8.5 证书自动续期

系统内置 cron 定时任务，**每天凌晨 3 点**自动检查证书是否即将过期（30 天内），如果是则自动续期并重载 Nginx。续期日志保存在 `/app/logs/certbot-renew.log`。

### 8.6 工作原理

```
容器启动
  │
  ├─ 无 DOMAIN 变量 → 纯 HTTP 模式（端口 80）
  │
  └─ 有 DOMAIN 变量
       │
       ├─ 1. 启动临时 Nginx（仅 HTTP）
       ├─ 2. certbot 通过 HTTP-01 验证申请证书
       ├─ 3. 申请成功 → 生成 HTTPS 配置 → 重启 Nginx
       │     HTTP 80 自动跳转 HTTPS 443
       ├─ 4. 启动 cron 自动续期
       │
       └─ 3. 申请失败 → 回退到纯 HTTP 模式
            （域名未解析或端口未开放时）
```

---

## 九、推送到 DockerHub

### 9.1 登录 DockerHub

```bash
docker login
# 输入你的 DockerHub 用户名和密码
```

### 9.2 使用脚本构建并推送（仅 Windows）

```powershell
# 在项目根目录执行，构建并推送到 DockerHub
.\docker\build.ps1 -Push

# 指定版本标签
.\docker\build.ps1 -Tag "v1.0" -Push
```

### 9.3 手动构建并推送（实际验证通过的命令）

以下是实际执行并验证通过的完整命令：

**PowerShell (Windows)：**

```powershell
# Step 1: 构建镜像（在项目根目录 sap/ 下执行）
docker build -t pllysun/sap:latest -f docker/Dockerfile `
  --build-arg HTTP_PROXY=http://host.docker.internal:7890 `
  --build-arg HTTPS_PROXY=http://host.docker.internal:7890 `
  --build-arg http_proxy=http://host.docker.internal:7890 `
  --build-arg https_proxy=http://host.docker.internal:7890 `
  .

# Step 2: 设置代理并推送到 DockerHub
$env:HTTP_PROXY='http://127.0.0.1:7890'
$env:HTTPS_PROXY='http://127.0.0.1:7890'
docker push pllysun/sap:latest
```

**Bash (Linux/macOS)：**

```bash
# Step 1: 构建镜像（在项目根目录 sap/ 下执行）
docker build -t pllysun/sap:latest -f docker/Dockerfile \
  --build-arg HTTP_PROXY=http://127.0.0.1:7890 \
  --build-arg HTTPS_PROXY=http://127.0.0.1:7890 \
  --build-arg http_proxy=http://127.0.0.1:7890 \
  --build-arg https_proxy=http://127.0.0.1:7890 \
  .

# Step 2: 设置代理并推送到 DockerHub
export HTTP_PROXY='http://127.0.0.1:7890'
export HTTPS_PROXY='http://127.0.0.1:7890'
docker push pllysun/sap:latest
```

> 📌 **注意**：Windows 构建时代理使用 `host.docker.internal`（容器内访问宿主机），Linux 下直接用 `127.0.0.1`。

### 9.4 在其他机器上使用

**PowerShell (Windows)：**

```powershell
# 拉取镜像
docker pull pllysun/sap:latest

# 运行
docker run -d -p 80:80 `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  pllysun/sap:latest
```

**Bash (Linux/macOS)：**

```bash
# 拉取镜像
docker pull pllysun/sap:latest

# 运行
docker run -d -p 80:80 \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  pllysun/sap:latest
```

---

## 十、常用运维命令

### 容器管理

```bash
# 查看容器状态
docker ps -a --filter name=sap

# 启动 / 停止 / 重启
docker start sap
docker stop sap
docker restart sap

# 删除容器（数据卷不受影响）
docker stop sap
docker rm sap
```

### 进入容器

```bash
# 进入容器 Shell
docker exec -it sap /bin/bash

# 查看日志目录
docker exec sap ls -la /app/logs/

# 查看数据库文件
docker exec sap ls -la /app/data/
```

### 更新部署

**PowerShell (Windows)：**

```powershell
# 1. 重新构建镜像
.\docker\build.ps1

# 2. 停止并删除旧容器
docker stop sap
docker rm sap

# 3. 用新镜像启动（数据卷自动复用）
docker run -d `
  --name sap `
  --restart unless-stopped `
  -p 80:80 `
  -v sap-data:/app/data `
  -v sap-logs:/app/logs `
  -v sap-uploads:/app/uploads `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
# 1. 重新构建镜像
docker build -t pllysun/sap:latest -f docker/Dockerfile .

# 2. 停止并删除旧容器
docker stop sap
docker rm sap

# 3. 用新镜像启动（数据卷自动复用）
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 \
  -v sap-data:/app/data \
  -v sap-logs:/app/logs \
  -v sap-uploads:/app/uploads \
  pllysun/sap
```

### 清理

```bash
# 清理所有未使用的镜像
docker image prune -a

# 清理构建缓存
docker builder prune
```

---

## 十一、常见问题

### Q1：构建时提示网络超时 / 无法拉取镜像

**原因**：无法访问 DockerHub 或 Maven 仓库。

**解决**：

```powershell
# Windows - 使用构建脚本
.\docker\build.ps1 -Proxy "http://host.docker.internal:7890"
```

```bash
# Linux - 手动指定代理
docker build -t pllysun/sap:latest -f docker/Dockerfile \
  --build-arg HTTP_PROXY=http://127.0.0.1:7890 \
  --build-arg HTTPS_PROXY=http://127.0.0.1:7890 \
  .
```

确保你的代理客户端（如 Clash）已开启，并且端口正确。

### Q2：容器启动后无法访问页面

**排查步骤**：

```bash
# 1. 检查容器是否在运行
docker ps

# 2. 查看启动日志
docker logs sap

# 3. 检查端口映射
docker port sap

# 4. 检查健康状态
docker inspect --format='{{.State.Health.Status}}' sap
```

### Q3：H2 数据库切换到 MySQL 后，数据怎么迁移？

H2 和 MySQL 的数据不互通。如果需要切换：
1. 在旧模式下通过 API 或管理端导出数据
2. 切换到新数据库模式启动容器
3. 系统会自动建表，通过管理端重新导入数据

### Q4：如何修改 Java 内存限制？

通过 `JAVA_OPTS` 环境变量：

**PowerShell (Windows)：**

```powershell
docker run -d -p 80:80 `
  -e JAVA_OPTS="-Xms256m -Xmx512m" `
  -v sap-data:/app/data `
  pllysun/sap
```

**Bash (Linux/macOS)：**

```bash
docker run -d -p 80:80 \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  -v sap-data:/app/data \
  pllysun/sap
```

### Q5：Windows 路径挂载失败

确保使用正确的 Windows 路径格式：
```powershell
# ✅ 正确
-v D:\sap-docker\data:/app/data

# ❌ 错误（不要用 Linux 路径）
-v /d/sap-docker/data:/app/data
```

同时确保 Docker Desktop 的 **Settings → Resources → File sharing** 中已允许访问对应磁盘。

### Q6：管理端 `/admin/` 返回 404

确保 URL 末尾带斜杠 `/admin/`，而不是 `/admin`。Nginx 配置使用的是 `location /admin/` 进行匹配。

### Q7：Linux 上无法使用 host.docker.internal

Linux 默认不支持 `host.docker.internal`，需要手动添加：

```bash
docker run -d \
  --add-host=host.docker.internal:host-gateway \
  -p 80:80 \
  pllysun/sap
```

或者直接使用 `--network host` 模式：

```bash
docker run -d --network host pllysun/sap
```
