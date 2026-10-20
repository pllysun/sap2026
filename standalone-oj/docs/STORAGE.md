# RustFS / S3 配置和私有归档

RustFS 通过 S3 协议接入。后端使用 AWS SDK for Java v2，Path Style 默认开启，默认区域为 us-east-1，连接方式参考 [RustFS 官方 Java SDK 文档](https://docs.rustfs.com/en/developer/sdk/java)。数据库保存题目、提交、题解和业务配置；S3 作为可选的管理员题包归档，不是判题服务启动的前提。

## 连接已有 RustFS

在部署目录的私有 `.env` 配置：

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

将占位符替换为实际凭据，不提交该文件。也可以在后端 application.yml 的 storage.s3 节点填写同名设置；容器部署统一通过 Compose 环境变量覆盖。Endpoint 是 API 地址，不是管理控制台地址；默认端口分别为 9000 和 9001。已有 S3 服务同样可配置，保持 SDK 与服务的区域及 URL 风格一致。

桶默认私有，提前创建并分配限定该桶的 HeadBucket、ListBucket、PutObject、GetObject 权限。归档对象统一位于 problem-archives/ 前缀下；没有读取其他任意对象的接口。需要自动建桶时显式启用 S3_CREATE_BUCKET 并授权 CreateBucket，完成初始化后可关闭。服务账号不应拥有站点之外的存储权限。

更改基础设施配置后重建应用：

```bash
docker compose up -d --force-recreate app
```

进入管理端“题包归档”点击“检查连接”。页面只返回启用状态、桶和区域，不回显连接密钥。

## 选择 Compose 自带 RustFS

生成器已经为本部署生成随机 S3 凭据。修改 `.env`：

```dotenv
S3_ENABLED=true
S3_ENDPOINT=http://rustfs:9000
S3_CREATE_BUCKET=true
RUSTFS_IMAGE=rustfs/rustfs:1.0.1
```

```bash
docker compose --profile rustfs up -d rustfs
# 使用外部 MySQL 时：
docker compose --profile rustfs up -d
# 若选择内置 MySQL，再加 --profile mysql
```

Compose 的初始化服务仅修正两个 RustFS 数据卷的所有权。RustFS 使用 UID 10001，S3 端口只在容器网络内部开放，控制台默认只绑定宿主 127.0.0.1:9001。需要远程控制台时通过 SSH 隧道访问。卷 rustfs-data / rustfs-logs 独立持久化，停止容器不会删除数据。

本地组合将随机凭据用于 RustFS 初始化和应用连接。上线后可在控制台创建只针对归档桶的服务账号，然后将应用连接切换到该账号；已有独立 RustFS 时不启动本项目的 rustfs profile。不要设置公共读策略。

## 归档与恢复

1. 管理端“题包归档”输入题目 ID，点击“归档题包”。后端读取当前管理员题包，保存为随机 UUID.json；重复归档创建新对象，不覆盖旧版本。
2. 点击下载。后端验证管理员权限后读取固定归档前缀，并以 no-store 返回 JSON；不提供公共地址或浏览器直接访问隐藏数据的链接。
3. 恢复时先保存私有 JSON，查看题库当前 revision。如果作为新题导入，修改 slug 避免冲突；如果更新旧题，使用带 revision 的更新接口。重新执行五语言三轮验证，成功后发布。

归档包含题面、配置、参考实现、初始代码和测试集；不包含题解、账号、历史提交或全部数据库。题解通过对应接口另行导出，完整站点恢复仍需备份 MySQL 和 app-data 的节点加密密钥。归档列表按对象名显示最多 100 条，大量历史归档可在 RustFS 控制台管理。

## AI 接口

- GET /api/admin/storage：查看安全状态。
- POST /api/admin/storage/check：检查桶，可按部署设置创建。
- POST /api/admin/storage/problems/{id}/archive：归档指定题目。
- GET /api/admin/storage/archives：列出归档。
- GET /api/admin/storage/archives/{UUID}：下载 JSON。

均使用 sap-token，限超级管理员与题库管理员。普通用户与匿名请求不能读取归档。连接失败返回通用错误，不输出 SDK 中可能携带的签名、URL 或凭据。每份归档最多 4 MB，连接和读取设置了超时；不承担 App 下载或任意文件托管。
