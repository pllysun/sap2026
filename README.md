# CSUFTSAP — 中南林业科技大学软件协会管理系统

> Software Association Platform — 一站式社团管理解决方案
>
> DockerHub: [pllysun/sap](https://hub.docker.com/r/pllysun/sap) · License: [MIT](LICENSE)

---

## 📋 目录

- [快速开始](#快速开始)
- [Docker 部署](#docker-部署)
- [项目简介](#项目简介)
- [功能一览](#功能一览)
- [技术栈](#技术栈)
- [项目架构](#项目架构)
- [目录结构](#目录结构)
- [许可证](#许可证)

---

## 快速开始

### 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 后端编译运行 |
| Maven | 3.8+ | 后端依赖管理 |
| Node.js | 18+ | 前端构建 |
| npm | 9+ | 前端包管理 |
| MySQL | 8.0+ | 数据库 (开发/生产) |

### 1. 克隆项目

```bash
git clone https://github.com/pllysun/sap2026.git
cd sap2026
```

### 2. 配置后端数据库

复制示例配置文件并填入你的数据库信息：

```powershell
# Windows PowerShell
Copy-Item sap-backend\src\main\resources\application-dev.yml.example `
          sap-backend\src\main\resources\application-dev.yml
```

```bash
# Linux / macOS
cp sap-backend/src/main/resources/application-dev.yml.example \
   sap-backend/src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，填入你的 MySQL 连接信息。

### 3. 启动后端

```powershell
cd sap-backend
mvn spring-boot:run
```

后端启动后访问 API 文档：http://localhost:8081/doc.html

### 4. 启动管理端

```powershell
cd sap-admin
npm install
npm run dev
```

管理端默认运行在 http://localhost:5173

### 5. 启动用户端

```powershell
cd sap-user
npm install
npm run dev
```

用户端默认运行在 http://localhost:5174

---

## Docker 部署

提供一体化 Docker 镜像，内含 Nginx + Spring Boot，支持内嵌 H2 数据库或外部 MySQL。

### 快速体验

```bash
docker run -d -p 80:80 --name sap pllysun/sap
```

访问：
- 用户端：http://localhost/
- 管理端：http://localhost/admin/

### 生产部署

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

### 完整部署文档

详见 **[Docker 部署文档](docker/README.md)**，包含：
- 数据持久化与卷挂载
- 外部 MySQL 配置
- HTTPS / Let's Encrypt 自动证书
- 构建与推送脚本
- 常见问题排查

---

## 项目简介

CSUFTSAP 是为中南林业科技大学软件协会量身打造的全栈社团管理系统，前后端分离架构，覆盖社团日常运营的核心场景：成员管理、活动组织、学习小组、财务记录、招新审批等。

系统由三个子项目组成：

| 子项目 | 说明 | 技术 |
|--------|------|------|
| **sap-backend** | 后端 API 服务 | Spring Boot 3.2 + JPA + MyBatis-Plus |
| **sap-admin** | 管理端 SPA | Vue 3 + Element Plus |
| **sap-user** | 用户端 SPA | Vue 3 + 原生 CSS |

---

## 功能一览

### 管理端（sap-admin）

| 模块 | 功能 |
|------|------|
| 📊 Dashboard | 数据看板、成员统计、活动概览 |
| 👥 成员管理 | 成员列表、职位分配、届别管理、优秀成员评选 |
| 📅 活动管理 | 活动 CRUD、图片上传（腾讯云 COS）、时间轴展示 |
| 📚 学习小组 | 课题管理、成员分组、评分系统、材料分享 |
| 💰 财务管理 | 收支记录、账单明细、凭证图片 |
| 📝 招新管理 | 在线申请、审批流程、入社引导 |
| 💬 留言管理 | 留言板、回复、点赞 |
| ⚙️ 系统设置 | COS 存储配置、招新开关、页脚信息、操作日志 |

### 用户端（sap-user）

| 模块 | 功能 |
|------|------|
| 🏠 首页 | 协会介绍、优秀成员展示、最新动态 |
| 📅 活动浏览 | 活动列表与详情、图片画廊 |
| 📚 学习小组 | 加入课题组、查看材料与成绩 |
| 💬 留言板 | 发表留言、回复互动 |
| 🚀 在线招新 | 填写加入申请、查看审批状态 |
| 👤 个人中心 | 个人信息管理 |

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 运行时 |
| Spring Boot | 3.2.5 | 核心框架 |
| Spring Data JPA | — | ORM / 自动建表 |
| MyBatis-Plus | 3.5.5 | 复杂查询 |
| Sa-Token | 1.38.0 | 认证授权 |
| Knife4j | 4.5.0 | API 文档 (Swagger) |
| Fastjson2 | 2.0.47 | JSON 序列化 |
| MySQL | 8.0+ | 生产数据库 |
| H2 | — | Docker 内嵌数据库 (MySQL 兼容模式) |
| Apache POI | 5.2.5 | Excel 导出 |
| 腾讯云 COS SDK | 5.6.227 | 对象存储 |
| Lombok | — | 代码简化 |

### 前端（管理端）

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 框架 |
| Vite | 8.0 | 构建工具 |
| Element Plus | 2.13 | UI 组件库 |
| ECharts | 6.0 | 数据可视化 |
| Axios | 1.13 | HTTP 客户端 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.6 | 路由管理 |

### 前端（用户端）

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 框架 |
| Vite | 8.0 | 构建工具 |
| Axios | 1.13 | HTTP 客户端 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.6 | 路由管理 |
| 原生 CSS | — | 自定义样式 (无 UI 框架依赖) |

### DevOps

| 技术 | 说明 |
|------|------|
| Docker | 容器化部署 |
| Nginx | 反向代理 / 静态资源服务 |
| Let's Encrypt | 自动 HTTPS 证书 |

---

## 项目架构

```
                    +-------+
                    | Users |
                    +---+---+
                        |
              +---------+---------+
              |                   |
         HTTP :80            HTTPS :443
              |                   |
    +---------v-------------------v---------+
    |              Nginx (Reverse Proxy)    |
    |                                       |
    |   /         -> sap-user  (User SPA)   |
    |   /admin/   -> sap-admin (Admin SPA)  |
    |   /api/     -> Spring Boot :8081      |
    +-------------------+-------------------+
                        |
              +---------v---------+
              |   Spring Boot     |
              |   :8081           |
              |                   |
              |   JPA + MP        |
              |   Sa-Token        |
              |   COS SDK         |
              +---------+---------+
                        |
               +--------+--------+
               |                 |
         +-----v-----+   +------v------+
         |   MySQL    |   |  Tencent    |
         |  (or H2)   |   |  COS        |
         +------------+   +-------------+
```

---

## 目录结构

```
sap/
├── sap-backend/                # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/sap/
│       │   ├── annotation/     # 自定义注解
│       │   ├── aspect/         # AOP 切面 (日志等)
│       │   ├── common/         # 通用类 (Result, Exception)
│       │   ├── config/         # 配置类 (Sa-Token, CORS, etc.)
│       │   ├── controller/     # REST 控制器
│       │   ├── dto/            # 数据传输对象
│       │   ├── entity/         # JPA 实体类
│       │   ├── mapper/         # MyBatis-Plus Mapper
│       │   ├── service/        # 业务逻辑层
│       │   ├── util/           # 工具类
│       │   └── vo/             # 视图对象
│       └── resources/
│           ├── application.yml           # 公共配置
│           ├── application-dev.yml       # 开发环境 (gitignored)
│           ├── application-dev.yml.example
│           ├── application-prod.yml      # 生产环境 (gitignored)
│           ├── application-prod.yml.example
│           └── application-docker.yml    # Docker 环境
│
├── sap-admin/                  # Vue 3 管理端
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                # API 调用模块
│       ├── assets/             # 静态资源
│       ├── components/         # 公共组件
│       ├── layouts/            # 布局组件
│       ├── router/             # 路由配置
│       ├── styles/             # CSS 样式
│       ├── utils/              # 工具函数
│       └── views/              # 页面视图
│
├── sap-user/                   # Vue 3 用户端
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                # API 调用模块
│       ├── assets/             # 静态资源
│       ├── router/             # 路由配置
│       ├── stores/             # Pinia 状态管理
│       ├── utils/              # 工具函数
│       └── views/              # 页面视图
│
├── docker/                     # Docker 部署相关
│   ├── Dockerfile
│   ├── README.md               # 详细部署文档
│   ├── build.ps1               # 构建脚本 (PowerShell)
│   ├── entrypoint.sh           # 容器入口脚本
│   └── nginx.conf              # Nginx 配置
│
├── .gitignore
├── .dockerignore
├── LICENSE
└── README.md                   # 本文件
```

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源，你可以自由使用、修改和分发。
