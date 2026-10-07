# OJ 模板格式化

用户端与管理端使用真正的语言格式化器：C / C++ / Java 为 Clang-Format，Python 为 Ruff，Rust 为 rustfmt。依赖版本由两个前端各自的 package-lock.json 固定；浏览器按需加载，在独立 Web Worker 中运行，代码不发送到外部服务。发行包包含依赖许可与声明。

用户首次打开或恢复模板时自动格式化。若本机存储的旧草稿与原始模板相同，也会自动格式化；已编辑的草稿保留原样。用户可以手动格式化当前代码。管理端打开或导入题目时整理初始模板、参考解与调用驱动，并提供单份代码格式化按钮。调用驱动中的唯一 `__USER_CODE__` 标记在格式化过程中暂作注释，结束后恢复。

本地 50 份题目包的 785 个非空代码字段已格式化。重新生成题目包后运行：

```sh
node ops/judger/format-problem-packs.mjs
```

该命令不导入数据库。仍然只有六道联调题已发布，其余 44 道保持本地。

格式化后的参考解与调用驱动已通过真实 go-judge 复测：50 题、五种语言、340 组合、7775 次用例执行、一轮全部通过。结果和输入文件校验值分别保存在 `formatted-corpus-validation.json`、`formatted-corpus-input-manifest.json`。

浏览器回归脚本：`check-browser.cjs`（运行、提交、记录与管理功能）、`check-ui-formatting.cjs`（五语言/双模式、键盘选择、模板/参考解/驱动、移动端与弹窗）、`check-drafts.cjs`（旧模板迁移、自定义草稿保护、恢复模板持久化）。测试凭据由忽略的本机环境文件生成到权限 0600 的临时缓存，不入库。
