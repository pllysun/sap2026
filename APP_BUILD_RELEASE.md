# 软协课表 App：AI 打包与在线发布规范

本文件是 AI 在本仓库构建 Android App、生成测试包和发布在线更新时的操作依据。若旧文档与这里冲突，以本文件为准。

## 1. 先判断构建类型

| 类型 | 用途 | 版本号 | App 内更新日志 | 升级平台更新说明 | 允许上传平台 |
|---|---|---|---|---|---|
| 测试构建 | 模拟器、真机调试、交付测试 APK | `versionCode` 必须递增；`versionName` 默认不变 | 不写 | 不写 | 不允许 |
| 正式发布 | 通过 URL/升级平台向用户推送 | `versionCode` 必须大于线上；`versionName` 对外递增 | 必须写 | 必须写，且与 App 内完全一致 | 允许 |

任何 APK 都必须有明确版本号，`versionCode` 永不复用。测试构建不是用户版本，不得为了让脚本通过而伪造更新日志。

## 2. GitHub Actions CI/CD

仓库内有两个工作流：

- `.github/workflows/android-test.yml`：测试包 CI。
- `.github/workflows/android-release.yml`：正式包 CD。

两个工作流共用 `android-app-version` 并发锁，同一时间只能有一个流程预留 App 版本，避免并发构建取得相同的 `versionCode`。

### 2.1 测试包流程

向默认分支推送 Android 相关改动时自动运行，也可以在 GitHub Actions 页面手动运行“Android 测试包”：

1. 在源码中将 `versionCode` 精确加一，`versionName` 保持不变。
2. 先把新 `versionCode` 提交回默认分支，再进行全新签名构建；预留提交带 `[skip ci]`，不会递归触发自己。
3. 从 APK 本体复核版本号和签名。
4. 将 APK 与 R8 mapping 保存为 GitHub Actions Artifact，保留 14 天。
5. 不修改更新日志，不调用在线发布接口。

版本预留一旦成功便不回滚。即使后续编译失败，下次测试打包也会继续加一，从而保证已经使用过的构建号不会被复用。

### 2.2 正式发布流程

正式发布只能在 GitHub Actions 页面手动运行“Android 正式发布”，并填写本次用户可见更新日志。工作流会：

1. 再次将 `versionCode` 加一，并自动递增 `versionName` 末位；也可手动指定新的 `versionName`。
2. 立即持久化正式版本号，防止与测试包或其他发布任务复用版本。
3. 把输入的更新说明写到 App 的 `Changelog.kt`。
4. 运行 `clean`、单元测试、lint，并从源码重新构建正式签名 APK；绝不复用测试 APK。
5. 校验 APK 的实际版本与签名，先执行发布 dry-run，再上传 App 升级平台。
6. 发布脚本从 `Changelog.kt` 提取并还原同一份更新说明写入发布页，所以 App 内与发布页不会出现两套文案。
7. 平台返回的版本、SHA-256 和文件大小全部校验成功后，才把正式更新日志提交回默认分支，并保存正式 APK 与 mapping Artifact。

更新日志可以逐行填写；GitHub 单行输入框也可用 `||` 分隔，例如：

```text
新增意见反馈回复功能||修复教务账号备注无法保存||优化课表周次显示
```

正式发布若在构建或上传途中失败，已经预留的版本不回滚；修复问题后重新发起流程会使用更大的新版本号并重新打包。

## 3. GitHub Actions 首次配置

仓库 Settings → Secrets and variables → Actions 中需要配置：

| 名称 | 类型 | 用途 |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | Secret | 正式签名文件的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | Secret | 签名库密码 |
| `ANDROID_KEY_ALIAS` | Secret | 签名别名 |
| `ANDROID_KEY_PASSWORD` | Secret | 签名私钥密码 |
| `SAP_ADMIN_TOKEN` | Secret | 升级平台管理 token，推荐 |
| `SAP_ADMIN_ACCOUNT` | Secret | 未配置 token 时使用的管理账号 |
| `SAP_ADMIN_PASSWORD` | Secret | 未配置 token 时使用的管理密码 |
| `APP_VERSION_PAT` | Secret，可选 | 默认 `GITHUB_TOKEN` 被分支保护禁止推送时使用 |
| `SAP_BASE_URL` | Repository variable，可选 | 升级平台地址，默认 `https://csuftsap.top` |

`SAP_ADMIN_TOKEN` 与“`SAP_ADMIN_ACCOUNT` + `SAP_ADMIN_PASSWORD`”任选一种认证方式即可。任何密码、token、keystore 原文件都不得提交到仓库。

macOS 可这样生成不换行的签名 Base64，再把输出完整写入 Secret：

```bash
base64 < sap-release.jks | tr -d '\n'
```

还需要在仓库 Actions 设置中允许 Workflow 使用“Read and write permissions”。如果默认分支保护不允许 `GITHUB_TOKEN` 直接写入，则创建具备该仓库 Contents 写权限的细粒度 PAT，保存为 `APP_VERSION_PAT`；是否允许绕过分支保护应按仓库规则决定。

## 4. 本地测试构建

测试包只用于验证，不进入在线更新链路：

```bash
cd sap-android
./build-release.sh --test
```

规则：

- 自动将 `versionCode` 加一，`versionName` 默认保持不变。
- 不修改 `Changelog.kt`，构建脚本也不会校验它。
- 产物写入 `release/test/sap-test-<versionName>-<versionCode>.apk`。
- 测试包禁止调用 `publish-release.sh`，禁止上传管理平台。

## 5. 本地正式发布前准备

1. 先查询线上版本，确认本次 `versionCode` 必须更大。
2. 汇总“自上一个线上版本以来”的全部用户可感知变更，包括期间所有测试构建中的有效改动。
3. 在 `sap-android/app/src/main/java/edu/csuft/sap/update/Changelog.kt` 最前面新增一条正式版本记录：

```kotlin
ChangelogEntry(
    versionCode = 43, versionName = "1.34", date = "2026-07-12",
    changes = listOf(
        "新增……",
        "优化……",
        "修复……",
    ),
),
```

每条变更必须独占一行，只写用户能理解的结果。未曾在线发布的测试版本不得单独留在 App 更新日志里，它们的有效变更应合并到下一次正式发布记录。

## 6. 本地构建正式发布包

正常发新版本：

```bash
cd sap-android
./build-release.sh --release
```

指定对外版本名：

```bash
./build-release.sh --release --name 2.0
```

仅当当前版本尚未上线、只是按相同版本号重新打包时，才允许：

```bash
./build-release.sh --release --no-bump
```

正式包输出为 `release/sap-<versionName>-<versionCode>.apk`，同时归档 R8 mapping。脚本会先运行单元测试与 lint，并拒绝没有对应 `Changelog.kt` 条目的正式构建。

## 7. 本地发布到在线升级平台

`publish-release.sh` 会完成以下强制校验：

- 从 APK 读取真实 `versionCode/versionName`。
- 检查 APK 是否晚于全部 App 源码，防止上传旧产物。
- 确认 APK 对应的 App 内更新日志存在。
- 直接从 `Changelog.kt` 提取该版本的 changes，作为平台更新说明，保证两处文字一致。
- 确认 `versionCode` 大于当前线上版本。
- 上传后核对服务器返回的版本号、文件大小和 SHA-256。

先做只读预检：

```bash
cd sap-android
SAP_ADMIN_ACCOUNT='<管理账号>' \
SAP_ADMIN_PASSWORD='<管理密码>' \
./publish-release.sh --apk ../release/sap-1.34-43.apk --dry-run
```

预检通过后正式发布：

```bash
SAP_ADMIN_ACCOUNT='<管理账号>' \
SAP_ADMIN_PASSWORD='<管理密码>' \
./publish-release.sh --apk ../release/sap-1.34-43.apk
```

也可以只传 `SAP_ADMIN_TOKEN`。账号、密码、token、签名密码均不得写入仓库、文档或日志。

默认发布参数为非强制更新、最低支持 `versionCode=1`。只有用户明确要求时才使用 `--force-update` 或 `--min-supported N`。

## 8. 发布完成后的验收

必须同时满足：

1. `GET /api/app/version` 返回的新版本号与 APK 一致。
2. 平台 `changelog` 与 `Changelog.kt` 当前正式版本的 changes 逐行一致。
3. 平台 SHA-256、文件大小与本地 APK 一致。
4. 下载 URL 可访问。
5. App 从上一个线上版本能够覆盖安装并收到升级提示。

未完成上传和以上核验时，只能报告“已打包”，不能报告“已发布”。
