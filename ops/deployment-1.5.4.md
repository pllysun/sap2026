# 1.5.4 算法题库界面优化（2026-10-01）

已上线 `pllysun/sap:1.5.4`，linux/amd64；容器健康、重启次数 0。

- 仓库 digest：`sha256:8943300e41d49e9efac1e2861a37a45cbacef004ca8e1b9c82608b9898dde3fa`
- 镜像配置：`sha256:1e60eab275143b0b09aa755d8ebb7d71e8ef16cdfae97a4ddb1fe2a8c34d2c39`
- 后端 JAR：`0433473d307f0f72872c0fadbfced63c1e9ebb3384db086076dff4493d01ea40`（与 1.5.3 相同）
- 管理端入口：`9bbc833e5b0abcbb36bbe989895f3cefb71500aa6c6f61e65e563fbea588bb06`
- 用户端入口：`6259504aca4d000eb5ffea3288f6386774bf533e54dc30669c3df6ac9d5008df`

用户入口：https://csuftsap.top/oj；管理入口：https://csuftsap.top/admin/oj。

管理端增加题库、启用语言与判题服务状态概览；整理题目列表、发布步骤和操作菜单，重新设计语言配置页、题目编辑分区、测试集摘要和发布验证报告。模板编辑区使用带语法高亮和行号的 CodeMirror，按语言、模式和初始模板/参考解/调用驱动切换，弹窗保持保存与取消按钮可见。

用户端的语言与难度选择器改为自定义菜单，支持方向键、Home/End、Enter 和 Escape。模式切换位于编辑器右侧，以按钮组展示；恢复模板位于语言选择旁。调整题目卡片、示例、代码文件栏和控制台层次，保持当前项目的蓝白风格。

五种语言使用真正的格式化工具，在 Web Worker 内按需加载。全部本地 50 份题目包的 785 个非空代码字段已格式化，调用驱动保留唯一插入标记。用户未改动的旧模板草稿自动整理，已编辑草稿保留原样；恢复模板与手动格式化结果正常保存。

验证：

- 格式化后的 50 题、五种语言、全部 340 个支持组合，7775 次真实 go-judge 用例执行全部通过，见 `ops/judger/formatted-corpus-validation.json`，输入文件哈希见 `formatted-corpus-input-manifest.json`。
- 上线后六题全部 50 个语言/模式提交组合通过，完整测试集共 1590 次执行，见 `ops/judger/final-release-validation.json`。仍仅发布六道联调题，其余 44 道未导入。
- 线上浏览器检查通过：五语言/双模式模板、代码格式化、驱动标记、键盘菜单、旧模板迁移、自定义草稿保护、恢复模板、公开样例运行、隐藏用例提交、提交记录、管理端测试集与配置页。用户端检查 390/768/1024/1440/1920 宽度，管理端检查 390/768/1024，运行错误为 0。报告见 `browser-production-validation.json`、`ui-format-production-validation.json`、`draft-ui-production-validation.json`。
- 原有业务 API、页面和资源回归通过；没有写原业务数据。Nginx 配置、Redis、OCR 与判题机健康检查均通过。

构建通过 `docker/build-image.sh`，预构建镜像继续从 scratch 组装已清理的运行时与固定 SDK。服务器仓库链路下载大型镜像层较慢，改为本机按上述仓库 digest 拉取后，将同一镜像归档经现有 SSH 链路导入；服务端配置 ID 与构建产物一致。使用原部署脚本保留环境变量、数据挂载、端口、网络、重启策略和判题隔离配置，逐项核对通过。清理仅涉及无容器引用、可从仓库恢复的旧镜像缓存。

本次回滚容器：`sap-rollback-20261001-150122`（1.5.3）。更早的回滚容器仍保留。回滚时停止并移除当前 `sap`，将该容器重命名为 `sap` 后启动，挂载和配置均保留。

WASM MIME、压缩与缓存配置已加入 Nginx 及 HTTPS 生成模板。格式化工具与许可说明见 `ops/judger/TEMPLATE-FORMATTING.md`，前端发行包的 `licenses/oj-formatters.txt` 包含依赖声明。
