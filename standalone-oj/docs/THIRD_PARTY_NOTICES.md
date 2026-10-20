# 来源与许可证

独立应用代码来自 pllysun 的 SAP 项目，在保留题库功能的基础上拆分账号、权限、部署与开发依赖，按根目录 MIT License 分发。新的目录可以独立构建，不包含原协会平台的生产配置、真实账号、教务代码或 App 发布凭据。

## 上游组件

- go-judge 引擎源码和原始许可证保存在 `judge/go-judge/`，包含本项目使用的隔离与协议适配。分发时保留该目录的 LICENSE。
- 前端 Vue、CodeMirror、Lezer、Highlight.js、Element Plus、格式化工具等使用各自许可证，相关说明保存在两端 `public/licenses/`，npm 锁文件记录精确依赖。
- 学习训练界面的内容与能力吸收了 ACM-Trainer 的设计，原有归属说明保存在 `user/public/licenses/acm-trainer.txt`；不能删除该声明。
- Spring Boot、MyBatis-Plus、Hibernate、Sa-Token、数据库驱动等使用其各自许可证，Maven POM 记录依赖。

## 题目与题解

不分发预置题库，仅保留自行编写的算术生成示例。新题的来源分别保存在 sourcePlatform / sourceUrl / sourceId；原创题标记“软件协会原创”。引用题型不代表取得第三方内容的许可，应用 MIT License 不替代题面、商标或资料的权利。

测试集与参考代码以仓库实际生成和核验记录为依据，不分发官方隐藏测试。新题应自行撰写题面、代码、测试和题解，保留真实来源。用户提交和真实个人数据不属于公开源码内容，也不随本次源码包或镜像分发。

## 工具链与镜像

判题镜像含 GCC / C++、OpenJDK、CPython、Rust 及其运行库。它们分别适用 GNU、OpenJDK classpath exception、Python、Rust 等上游许可证，不能仅用应用 MIT 标签概括其全部许可。

SDK 固定为 `pllysun/sap-judger-sdk@sha256:ff9484d4ba7b2a510cd49b10e701d8ff2106a86f19cf36ee8fb2049298914ca0`。应用 Java 21 与用户代码 Java 27 分属不同运行环境；不要把应用进程或其环境变量放进判题 rootfs。基础镜像、SDK 和最终镜像 digest 应在每次发布的构建记录中保存。

RustFS 可选基础服务使用 Apache 2.0 许可证，采用其官方镜像。AWS SDK for Java v2 用于 S3 兼容连接，使用其上游许可证。
