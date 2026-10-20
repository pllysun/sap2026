# 1.1.1 发布与验收

登录页改为通用学习练习文案，用户端和管理端的浏览器初始标题不再出现算法字眼。题面描述、输入、输出、数据范围支持统一净化的 Markdown，支持表格、标题、列表、上下标与代码高亮。核心函数模式的五语言常用标准库由后端统一提供，完整程序模式仍自行引包。具体列表见 [CORE_MODE.md](CORE_MODE.md)。

## DockerHub

- [pllysun/lume:1.1.1](https://hub.docker.com/r/pllysun/lume)
  - digest：`sha256:2dbf37ad61f063fa928c03ba1f5cc8a6d91683fb8e6317f9c0187ec59817aeb9`
  - 压缩层合计：1095896069 字节
- [pllysun/lume-server:1.1.1](https://hub.docker.com/r/pllysun/lume-server)
  - digest：`sha256:4072e8a23c73fe1d59bdcd23484e83f506e7620bd2fa58b9a1b7ce7db4dcda6c`
  - 压缩层合计：167916085 字节
- [pllysun/lume-worker:1.1.1](https://hub.docker.com/r/pllysun/lume-worker)
  - digest：`sha256:4e4e3374779e347ff510eaed01147fed57050df08d7b32bcbf2353cf45e74c75`
  - 压缩层合计：972814547 字节

均为 linux/amd64，各自 latest 与 1.1.1 摘要一致。已经匿名获取 manifest、配置与全部层 HEAD，仓库公开且可匿名拉取；三个 Overview 已更新部署与本版功能说明。旧标签保留。完整结果见 [release.json](release.json)。

## 验收结果

- 两端全新构建，用户端 68、管理端 2 项测试通过；后端 135 项、Python 工具 7 项通过。
- 原生 Linux / cgroup v2 合并容器连接隔离 MySQL 8.4 与可选 RustFS 1.0.1。无 Redis，首次题库为 0，所有验收题仅在隔离环境创建，不随运行镜像预装。
- STDIO 五语言、41 用例、三轮共 615 次 AC；原有显式引包的双模式五语言、30 用例、三轮共 900 次 AC；移除驱动引包的双模式五语言、30 用例、三轮再完成 900 次 AC，合计 2415 次成功参考执行。
- 五语言普通用户样例运行与正式提交 AC；五语言不引包核心函数正式提交 AC；Python List / Counter、合法 future 导入与 Rust 显式 HashMap 导入兼容。三种错误实现 WA。
- Python 源码在编译沙箱内准备后写入现有受控 answer 产物缓存，运行阶段确实执行准备后的代码；不放宽节点文件白名单或隔离。
- SSE 编译、逐例和终态推送完整；普通账号不能读管理配置或私有题包。RustFS 归档、完整 JSON 下载、列表及鉴权通过。
- 独立用户端 / 管理端共 19 项实际浏览器检查通过；两套前端另有桌面 / 手机 4 项 Markdown 模拟检查，验证格式、高亮、链接 / 脚本净化、核心模式说明和横向宽度；独立登录页面与标题无算法文案。
- 合并容器重启后 MySQL 会话、设置、题目、归档保留，并继续判题 AC。
- SAP 同步部署 1.5.50，后端 1000 项测试、线上 5 项只读页面检查通过；生产题面未用于模拟修改，原有题库、题单、历史与学习档案指纹保留。
- AI CLI 的精确 slug 查询跨越服务端 50 条分页，不再将标题关键词查询误当成 slug 查找，批量导入重试可正确识别已有题目。

合并版、分离版、外部 MySQL、可选 RustFS、权限、资源预算与备份方式沿用 1.1.0。首次部署仍是空题库，新的验证题不会预装。部署和 AI 操作见包内手册。
