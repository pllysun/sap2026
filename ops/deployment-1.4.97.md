# 1.4.97 部署记录（2026-09-23）

## 测试 App

- 版本：2.2，构建号 117；文件：`release/test/sap-test-2.2-117.apk`。
- SHA-256：`cac3e69a004478115db63391f774c208373463eb585dc6b17e15db4b573b550f`。
- 包名 `edu.csuft.sap`，发布签名 SHA-256：`1d822bbbcdcac38b20673069632228721242734aea69fb9de750e2960d648697`。
- Gradle 单元测试、Release 构建和 lint vital 通过；R8 mapping 存于同目录。
- 仅为测试包，未上传 App 在线升级平台。

## 后端镜像与部署

- 镜像 `pllysun/sap:1.4.97`，linux/amd64，已推送至 Docker Hub。
- Registry 摘要：`sha256:1952056264ca2eef83134d2c7446e8c638733c18776fa352cb797b79e6c0e2de`。
- 服务器镜像 ID：`sha256:d4be6ff1549ab191249d61fe9eb544d4a819c10867e7bf76bcd51e1217dcca00`。
- 服务器直拉镜像在单层停滞；使用本地从上述摘要拉取的同一镜像，经压缩 SSH 流传输并在服务器 `docker load`，镜像 ID 与构建结果相符。
- 原容器保留为 `sap-rollback-20260923-213614`；新容器继承原环境变量、挂载、端口、网络和重启策略，健康检查通过，重启次数 0。
- 部署前 MySQL 一致性备份：服务器 `/home/sap/sap-data/ops/backups/before-1.4.97-20260923-132526.sql.gz`，51 张表、558566 行、44223443 字节，gzip 完整性校验通过，SHA-256 `744966bcef71b95c8cfc3e87d6da30d114bc9171155de1824c680d3ab4a9484f`。备份仅留服务器，包含私人数据，不得提交或公开下载。

## 验证

- 镜像与运行容器的 JAR SHA-256：`eff7200983ccfad63b9cced545ad577afe3ea881220dcee3a4143f3932f941d5`。
- 管理端入口 SHA-256：`0a5bdaa4188d8b9e709f04b314b0dec543e9386513fe15d2d6ffafe0a5efaa76`；用户端入口 SHA-256：`45e06a0fba379d0afcfaf54760ced86686c1e30912056a01ee20af8da1445aa0`。
- 公网 `/admin/`、`/` 内容摘要与镜像一致，`/api/ping` HTTP 200；新容器启动日志中 ERROR/Exception 计数 0。
- 镜像构建中的 Maven 采用 `-DskipTests`；本次未声称完成全量后端测试，也未触发教务采集或发送测试邮件。
