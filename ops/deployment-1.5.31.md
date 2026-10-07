# 1.5.31：首页荣誉成员双行展示

2026-10-03，已部署至 https://csuftsap.top/home。

荣誉成员每页由 4 人增加到 8 人，桌面每行 4 人，共两行；窄屏每行 2 人，保持姓名与荣誉信息可读。分页按 8 人计算，保留原有顺序。线上 21 位成员分成 8、8、5 人三页。

用户端构建、3 项本地浏览器检查及 3 项线上浏览器检查通过，验证两行布局、翻页边界、全部成员可达和 320／390 像素窄屏无溢出。线上验证仅读取数据。500 条历史提交及代码哈希全部保留；业务数据数量一致，判题日志仅新增正常 STARTUP／READY 启动记录。后端、管理端和判题 Agent 文件哈希保持一致，主容器健康、重启计数为 0。

镜像 `pllysun/sap:1.5.31` 继承 1.5.30，仅替换用户端静态文件；使用仓库构建脚本生成 amd64 镜像，并核对父镜像层、新增层和注册仓库摘要。镜像 ID：`sha256:a83ad41f0e2f63b040966fe6799dcb0844b9bdcc7cf931ca6f30dbe441573754`；仓库摘要：`sha256:51f17187a418740676ae475cc5486a699eb6fd28bbac660a5fb765abc441df14`。

保留 1.5.30 回滚容器 `sap-rollback-20261003-192015`，保留原有环境、数据卷及判题配置。独立构建机临时构建目录已清理，保留发布镜像供后续构建。

证据：`ops/judger/home-members-tests.json`、`home-members-browser-live.json`、`home-members-native.json`、`home-members-registry.json`、`home-members-deployment.json`、`home-members-data.json`。
