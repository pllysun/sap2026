# CSUFTSAP · 软件协会管理系统

> 中南林业科技大学软件协会一站式社团管理系统 — 前后端一体化镜像
>
> 内含 **Nginx + Spring Boot 3.2 + Vue 3**，开箱即用，支持内嵌 H2 或外部 MySQL。
>
> 源码：[github.com/pllysun/sap2026](https://github.com/pllysun/sap2026) · 许可证：MIT

---

## 📦 镜像简介

单镜像集成了用户端、管理端与后端 API，由 Nginx 统一反向代理：

| 路径 | 服务 |
|------|------|
| `/` | 用户端 SPA（Vue 3） |
| `/admin/` | 管理端 SPA（Vue 3 + Element Plus） |
| `/api/` | 后端 API（Spring Boot，内部 8081） |

覆盖社团核心场景：成员/届别管理、活动组织、学习小组与评分、财务记录、在线招新审批、留言板、知识库笔记等。

---

## 🚀 快速开始

```bash
docker run -d -p 80:80 --name sap pllysun/sap
```

启动后访问：

- 用户端：<http://localhost/>
- 管理端：<http://localhost/admin/>

**默认管理员账号：`admin` / `admin123`** —— 首次登录后请立即修改密码。
（该账号由后端启动时自动创建；若数据库已存在超级管理员则不会重复创建。）

> 默认使用容器内嵌 H2 数据库，**数据随容器删除而丢失**，仅适合快速体验。生产请按下方挂载数据卷或连接外部 MySQL。

---

## 🗄️ 生产部署（数据持久化）

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

---

## 🐬 连接外部 MySQL（推荐）

通过环境变量指定外部 MySQL（8.0+），不传则回退内嵌 H2：

```bash
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 \
  -e MYSQL_URL="jdbc:mysql://your-mysql-host:3306/sap_db?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8" \
  -e MYSQL_USER="sap" \
  -e MYSQL_PASSWORD="your_password" \
  -v sap-uploads:/app/uploads \
  -v sap-logs:/app/logs \
  pllysun/sap
```

应用启动时会通过 Hibernate **自动建表**，并自动初始化基础数据（角色、内置身份、默认管理员），无需手动执行建表 SQL。

---

## 🔒 启用 HTTPS（Let's Encrypt 自动证书）

将域名解析到本机后，传入 `DOMAIN` 即自动申请并续期证书：

```bash
docker run -d \
  --name sap \
  --restart unless-stopped \
  -p 80:80 -p 443:443 \
  -e DOMAIN="sap.example.com" \
  -e SSL_EMAIL="you@example.com" \
  -v sap-uploads:/app/uploads \
  -v letsencrypt:/etc/letsencrypt \
  pllysun/sap
```

---

## ⚙️ 环境变量速查

| 变量 | 说明 | 默认 |
|------|------|------|
| `MYSQL_URL` | 外部 MySQL 的 JDBC URL；不设则用内嵌 H2 | （空） |
| `MYSQL_USER` | MySQL 用户名 | `root` |
| `MYSQL_PASSWORD` | MySQL 密码 | （空） |
| `DOMAIN` | 启用 HTTPS 的域名（需解析到本机） | （空） |
| `SSL_EMAIL` | Let's Encrypt 联系邮箱 | `admin@$DOMAIN` |
| `JAVA_OPTS` | 传给 JVM 的额外参数（如 `-Xmx512m`） | （空） |

> 对象存储（图片/文件上传，腾讯云 COS）在**管理端 → 系统设置 → 对象存储**中配置，无需环境变量。未配置时上传会给出明确提示。

---

## 💾 数据卷与端口

| 挂载点 | 用途 |
|--------|------|
| `/app/data` | 内嵌 H2 数据库文件 |
| `/app/uploads` | 本地上传文件 |
| `/app/logs` | 应用日志 |
| `/etc/letsencrypt` | HTTPS 证书 |

| 端口 | 用途 |
|------|------|
| `80` | HTTP |
| `443` | HTTPS（设置 `DOMAIN` 后启用） |

---

## 🧱 技术栈

`Java 21` · `Spring Boot 3.2` · `Sa-Token` · `MyBatis-Plus` · `JPA` · `MySQL / H2` · `Vue 3` · `Vite` · `Element Plus` · `Nginx` · `腾讯云 COS`

---

## 📚 更多

- 完整部署文档（外部 MySQL、HTTPS、常见问题）：见源码仓库 [`docker/README.md`](https://github.com/pllysun/sap2026/blob/master/docker/README.md)
- 问题反馈 / 源码：<https://github.com/pllysun/sap2026>

> 提示：当前镜像以 root 运行（同容器内 Nginx/Certbot 需特权端口与证书目录）；如需更严格的安全隔离，可将 Nginx/Certbot 与应用拆分为多容器部署。
