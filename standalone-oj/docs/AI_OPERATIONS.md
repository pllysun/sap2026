# AI 操作手册

本手册用于让 AI 安全、可重现地维护独立 SAP OJ：部署环境、创建题目、生成测试、补充五语言参考代码、编写题解，并在服务器验证通过后发布。以本仓库的 DTO、校验器和实际 API 响应为准，不依赖原协会系统，也不从网页截图猜接口。

## 开始操作

1. 阅读根目录 `AGENTS.md`、`README.md` 和本手册；出题前继续读 `PROBLEM_AUTHORING.md`，题解前读 `SOLUTION_STANDARD.md`。
2. 确认工作目录是独立项目。查看 `VERSION`、`git status` 和目标站点，保留已有改动。
3. 明确用户授权的范围。部署、导入测试环境、发布题目、发布镜像是不同动作；在已有明确授权内继续，不重复询问。
4. 登录令牌、数据库密码、节点密钥不进入回答、代码、截图或公开日志。使用私有 `.env` 与 `secrets/`，不要打印文件内容。
5. 所有学生代码和不可信参考实现均在判题沙箱中编译运行。不要在 AI 所在宿主机执行来自题包的代码，也不要挂载 Docker socket 给题库或判题容器。

## 站点与认证

所有命令从项目根目录运行。CLI 仅使用 Python 标准库，认证头是 `sap-token`。默认站点 `http://127.0.0.1:8080`：

```bash
python3 tools/ojctl.py login
```

CLI 从私有 `.env` 或 `OJ_ADMIN_ACCOUNT` / `OJ_ADMIN_PASSWORD` 环境变量读取凭据，保存站点绑定的 `secrets/admin-api-token.json`，权限为 0600。不会输出令牌。

远程站点应使用 HTTPS，且全局选项放在子命令前：

```bash
python3 tools/ojctl.py --url https://oj.example.com --token-file secrets/remote-token.json login
python3 tools/ojctl.py --url https://oj.example.com --token-file secrets/remote-token.json health
```

不同站点使用不同令牌文件。CLI 拒绝跨站复用令牌和 API 重定向，避免把认证信息带到错误站点。不要把密码放在 CLI 参数里；脚本只解析 `.env` 文本，不通过 shell 执行它。

## 创建一题的固定流程

### 1. 明确题意和协议

先写出输入、输出、约束、边界、算法、复杂度和检查器。确定是否只提供 `STDIO`，或确实需要 `FUNCTION`。两种模式必须使用同一套测试协议，函数签名与驱动规则必须一致。

多答案、构造题、任意方案题先阅读 [MULTIPLE_ANSWERS.md](MULTIPLE_ANSWERS.md)，不要将无序比较当作通用多答案校验。

### 2. 准备独立文件

建议一题一个目录：

```text
examples/my-problem/
  pack.json
  solution.json
  generate.py
  coverage.json
  oracle.py
  mutants/
```

生成脚本固定随机种子。`coverage.json` 保存覆盖矩阵、oracle 说明和错误实现检查结果；这些资料不加到 API 未定义的题包字段里。

### 3. 本地检查格式

```bash
python3 tools/ojctl.py lint examples/my-problem/pack.json
```

这一步检查数据格式、五语言完整性、公开样例、测试名称、体积和模式约定，不执行参考代码，也不证明算法正确。算法正确性由独立 oracle、差分设计和后续沙箱验证共同保证。

### 4. 保存草稿

```bash
python3 tools/ojctl.py create examples/my-problem/pack.json
```

返回 `id` 和 `revision`。记录它们；新题通常为 revision 1。不要通过 SQL 插入 `PUBLISHED`、伪造 `validationSignature` 或修改验证任务状态。

### 5. 五语言三轮验证

```bash
python3 tools/ojctl.py validate <id> --timeout 1200
```

CLI 要求 C、C++、Java、Python、Rust 全部启用，调用服务端验证并等待终态。服务端执行每个启用语言、每个题目模式、全部用例，重复三轮。必须收到 `AC` 才能继续。每账号至多两个未完成任务，批量导入最多并行两题。

失败时从管理端“判题日志与队列”查看对应任务，核对编译入口、输入协议、超时、内存、输出和函数驱动。修复题包后按 revision 更新，并重新验证。不要换成更少的语言、删除困难用例或调高资源直到错误实现能够通过。

### 6. 发布与题解

```bash
python3 tools/ojctl.py publish <id>
python3 tools/ojctl.py solution <id> examples/my-problem/solution.json
```

题解的 `problemRevision` 必须与当前题目一致；过期题解会被拒绝。保存题解前，核对正文、代码、样例和复杂度。五语言代码必须与已经通过验证的实现一致；只增加注释和排版时也应保留代码核验记录。

比赛专用题使用：

```bash
python3 tools/ojctl.py publish <id> --contest-only
```

然后在管理端创建比赛题单。比赛题解在服务器判定比赛结束后开放，不能把答案放入 public 目录、前端常量或题面图片。

### 7. 普通用户验收

用独立练习账号确认：题目可打开、五语言可切换、样例运行可用、正式提交返回正确状态、过程逐步更新、个人记录与题解可见。确认普通账号不能读取管理员题包、参考实现、隐藏测试或其他账号的私人代码。

## 更新已有题目

先从管理端或管理员详情接口读取当前 revision。需要私有导出时：

```bash
python3 tools/ojctl.py export <id> secrets/problem-export.json
```

导出包含参考实现和隐藏测试，必须保存在受控位置。更新：

```bash
python3 tools/ojctl.py update <id> examples/my-problem/pack.json --revision <当前revision>
python3 tools/ojctl.py validate <id>
python3 tools/ojctl.py publish <id>
```

revision 冲突时重新读取并合并修改，不重试旧版本覆盖。编辑会使旧验证失效；语言配置和工具链签名变化也会要求重新验证。题单使用题目快照，已运行比赛不能悄悄替换题目定义；需要调整时使用题单已有的作废、归档或新建流程。

## 批量导入自己生成的题目

本项目不预装题库，批量工具必须明确提供自己的目录：

```text
my-corpus/
  manifest.json         {"items":[{"slug":"original-my-problem"}]}
  problems/<slug>.json
  solutions/<slug>.json
```

```bash
python3 tools/ojctl.py corpus --dir my-corpus --slugs original-my-problem --publish --parallel 1 --report reports/pilot.json
python3 tools/ojctl.py corpus --dir my-corpus --all --publish --parallel 2 --report reports/import.json
```

不加 `--publish` 时只保存草稿和题解。已有完全相同的题包可复用；内容不同要求显式按 revision 更新。失败返回非零退出码，成功项保留。报告只保存 slug、ID 和状态。

## 配置与归档

MySQL / RustFS 连接由私有 `.env` 与 Compose 管理，不把基础设施凭据写进题包、题面或题解。注册开关、提交限制、队列和时限由管理端“系统设置”配置；五语言资源和节点策略在“题库与判题”配置。使用 S3 私有题包归档时遵守 `STORAGE.md`，下载包含隐藏测试，仅管理员可用。

## 构建与发布应用

修改源码后先运行：

```bash
bash tools/build-local.sh
bash tools/build-images.sh
```

分别验收合并版 lume、网站 lume-server 和独立判题机 lume-worker 后再推送指定版本。合并版一次 docker run；分离版用 compose.split.yml，均无 Redis，详情见 SINGLE_CONTAINER.md 和 DEPLOYMENT.md。当前用户未授权的新环境上线、删除数据库卷、切换生产域名或迁移真实账号，不能因为“生成题目”而一并执行。镜像、题包和运维配置应分别归档，报告实际完成的验证。

## AI 工作记录

每次交付至少包括：题目 slug、当前 revision、公开样例、模式、五语言实现、测试覆盖矩阵、固定种子、oracle 方法、错误实现检测结果、验证任务 ID 与终态、题解及图解核对、发布状态。操作失败要给出可复现的命令和原因，不报告尚未完成的发布或验证。
