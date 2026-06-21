# sap2026 正式环境构建与部署

中南林业软件协会管理系统的生产构建/部署总纲。涵盖 **后端、管理端、用户端、OCR 边车、安卓 App**。
线上域名 **https://csuftsap.top**（用户端 `/`、管理端 `/admin/`、接口 `/api/`，安卓 App 走在线升级）。

---

## 0. 架构一图

**前端 + 后端 + OCR 全部打进同一个镜像 `pllysun/sap`**（沿用既有单镜像方案）：

```
                       ┌───────────────────────── 单一主镜像 pllysun/sap ─────────────────────────┐
 浏览器 / App ─ 443 ─▶ │  Nginx ─ /        → 用户端 SPA(sap-user, base=/)                          │
                       │         ─ /admin/ → 管理端 SPA(sap-admin, base=/admin/)                    │
                       │         ─ /api/   → 127.0.0.1:8081  Spring Boot(sap-backend fat jar)       │
                       │  OCR 边车(ddddocr) ── 127.0.0.1:9000 ◀── 后端 jw.ocr-url 默认连它(无需配)  │
                       │  Certbot + cron 自动签发/续期 Let's Encrypt 证书                            │
                       └────────────────────────────────────────────────────────────────────────────┘
                                          │ jdbc                       ▲ COS SDK
                                ┌─────────▼─────────┐      腾讯云 COS/CDN ── APK / 图片等对象
                                │ MySQL(外部/自带)   │      (COS 密钥在管理端「系统设置」配，存 DB)
                                └────────────────────┘
```

---

## 1. 生产必备密钥 / 凭据清单

| 项 | 说明 | 注入方式 |
|---|---|---|
| **JW_AES_KEY** | 32 字节，加密存储学校教务密码。**必填**，否则容器拒绝启动 | `docker/.env` / `-e` |
| **MySQL 账号密码** | 外部 MySQL 强口令（生产库勿对公网暴露） | `docker/.env` / `-e MYSQL_*` |
| **SSL_EMAIL** | Let's Encrypt 联系邮箱 | `docker/.env` |
| **腾讯云 COS** | secretId/secretKey/bucket/region | 启动后在 **管理端「系统设置→对象存储」** 填，存 DB（不走环境变量） |
| **安卓 keystore** | `sap-android/keystore.properties` + `*.jks`，正式签名 | 本地文件，已 gitignore，**务必备份** |
| 默认管理员 | 首启自动建 `admin/admin123` | **上线后立即改密** |

> `JW_AES_KEY` 一旦设定不要再换：换了所有已绑定教务凭据无法解密，需用户重新绑定。
> 生成：`openssl rand -base64 24 | cut -c1-32`

---

## 2. 一键部署（推荐：docker compose）

```bash
cd sap2026
cp docker/.env.example docker/.env      # 编辑填入 JW_AES_KEY / MySQL 密码 / DOMAIN
docker compose -f docker/docker-compose.yml --env-file docker/.env up -d --build
```

编排了 `app`（主镜像，含两个前端+后端+Nginx）和 `ocr`（验证码边车，仅内网）。
首次启动 `DOMAIN=csuftsap.top` 会触发 certbot 签发 HTTPS。需外部 MySQL 时在 `.env` 填 `MYSQL_*`；
想用容器自带 MySQL，反注释 compose 里的 `mysql` 服务并把 `MYSQL_URL` 指向 `mysql:3306`。

> 已有线上容器在跑 csuftsap.top：复用 `/etc/letsencrypt` 卷里的现有证书，避免重复签发触发速率限制；
> 注意 80/443 端口归属与同机冲突。

### 手动 docker run（等价；OCR 已在镜像内，单容器即可）
```bash
./docker/build-image.sh        # 强制 linux/amd64 + 自动升镜像版本号(读 docker/IMAGE_VERSION)+推送，打印出新 tag
docker run -d --name sap-app --restart unless-stopped \
  -p 80:80 -p 443:443 \
  -e DOMAIN=csuftsap.top -e SSL_EMAIL=admin@csuftsap.top \
  -e MYSQL_URL="jdbc:mysql://<host>:3306/sap?serverTimezone=Asia/Shanghai&useSSL=false&useUnicode=true&characterEncoding=UTF-8" \
  -e MYSQL_USER=sap -e MYSQL_PASSWORD='<强密码>' \
  -e JW_AES_KEY='<32字节生产密钥>' \
  -e JAVA_OPTS='-Xms256m -Xmx512m' \
  -v /home/sap/data:/app/data -v /home/sap/logs:/app/logs \
  -v /home/sap/uploads:/app/uploads -v /home/sap/letsencrypt:/etc/letsencrypt \
  pllysun/sap:1.1.0
```
> OCR 边车随镜像启动在 127.0.0.1:9000，后端默认连它，**无需 `JW_OCRURL`**。
> 国内构建慢可加 `--build-arg HTTP_PROXY=... --build-arg HTTPS_PROXY=...`（apt 与 pip 都会用）。

---

## 3. 各服务单独构建（CI / 排查用）

| 服务 | 构建命令 | 产物 | 现状 |
|---|---|---|---|
| 后端 | `mvn -f sap-backend/pom.xml clean package -DskipTests` | `target/sap-backend-1.0.0.jar`(可执行 fat jar) | ✅ 已验证 |
| 用户端 | `cd sap-user && npm ci && npm run build` (base=`/`) | `sap-user/dist` | ✅ 已验证 |
| 管理端 | `cd sap-admin && VITE_BASE_URL=/admin/ npm ci && VITE_BASE_URL=/admin/ npm run build` | `sap-admin/dist` | ✅ 已验证 |
| 主镜像 | **`./docker/build-image.sh`**（强制 amd64 + 自动升版本号 + 推送；勿手敲 `docker build`） | 镜像(自动重打两个前端 + 打入后端 jar + OCR) | ✅ 已验证 |

> OCR 边车（`ocr-sidecar/main.py` + `requirements.txt`）已直接打进主镜像（stage3 装 python3 + ddddocr，entrypoint 后台拉起 127.0.0.1:9000），**不再是独立容器**。`ocr-sidecar/Dockerfile` 仅作"未来拆分独立部署"的备用，正式部署用不到。

> **登录态持久化（永久免密）**：主镜像内置 **Redis**（stage3 apt 装 redis-server，entrypoint 起 127.0.0.1:6379，AOF 落 **`/app/data/redis`**）。`docker` profile 用 `sa-token-redis-jackson` 把 token 存 Redis（`dev/local` 已 `spring.autoconfigure.exclude` 回退内存）。配合 `appLogin` 的 `timeout=-1`：后端重启/重新部署/重建容器(重装docker) token 都不丢，只有主动 logout/踢人才失效。**`/app/data` 卷已覆盖 Redis 数据，docker run 无需新增挂载**（沿用 `-v /home/sap/sap-data:/app/data`）。客户端 `AppViewModel.verify()` 配套改为仅在 `code:401`(被踢) 才清 token、网络/宕机错误一律保持登录。已本地实跑验证（重启/重建容器/logout 全过）。

> 后端用 `package`（不要 `verify`，有 JaCoCo 95% 覆盖率门禁会卡）。
> 管理端**必须**带 `VITE_BASE_URL=/admin/`，否则 `/assets` 在 `/admin/` 下 404（主镜像已自动设好）。

---

## 4. 安卓正式包：混淆 + 签名 + 发版 + 加壳

### 4.1 一次性：正式签名
```bash
cd sap-android
keytool -genkeypair -v -keystore sap-release.jks -alias sap \
  -keyalg RSA -keysize 2048 -validity 36500
# 写 keystore.properties（已 gitignore）：
#   storeFile=<绝对路径>/sap-release.jks
#   storePassword=...   keyAlias=sap   keyPassword=...
```
> **keystore 丢失 = 永远无法发布升级包**，务必异地备份。已配置：缺 `keystore.properties` 时回退 debug 签名（不可上架）。

### 4.2 出混淆包（必须走脚本，自动升版本号）
**正式打包统一用脚本** `sap-android/build-release.sh`。
> 🟦 **版本号约定（重要）**：`versionName`（1.13 → 1.14 …）= **对外发布版本**，每次发布递增，是用户看到的「版本」；`versionCode`（21、22…）= **内部构建号**，仅内部使用，每次构建自动 +1（在线升级只靠它比大小判更新）。

脚本默认升「对外版本」`versionName` 末位、`versionCode` 始终内部 +1，再 R8 混淆+资源压缩+正式签名打包，按版本归档到 `release/`，杜绝"忘了升版本号"：
```bash
cd sap-android
./build-release.sh                # 发对外新版本：versionName 末位 +1（1.13→1.14）、versionCode 内部 +1
./build-release.sh --name 2.0     # 指定 versionName=2.0（大版本跳号）、versionCode 内部 +1
./build-release.sh --build-only   # 仅内部构建：versionCode +1、versionName 不变（不对外发版）
./build-release.sh --no-bump      # 仅重打当前版本（慎用，用户端识别不到更新）
```
脚本做了：升版本号 → **校验更新日志（条目的 (versionCode, versionName) 必须与本次构建完全一致）** → `clean :app:assembleRelease` → 产物归档 `release/sap-<vn>-<vc>.apk` + `mapping-<vn>-<vc>.txt` → 打印 大小/SHA-256/签名证书。缺 `keystore.properties` 会直接中止（不会出 debug 签名包）。可用 `JAVA_HOME`/`GRADLE_BIN` 覆盖工具路径（跨机/CI）。

> 🔴 **打包铁律：每个版本都要写「更新日志」**。即将发布的 `(versionCode, versionName)` 必须在
> `sap-android/app/src/main/java/edu/csuft/sap/update/Changelog.kt` 的 `entries` 里有**完全匹配**的条目，**否则 `build-release.sh` 直接中止**（与"强制 versionCode +1"同级的硬约束）。
> - 发版前先在 `Changelog.kt` 的 `entries`【最前面】加一条（`versionCode` 与 `versionName` 写同一行）：`ChangelogEntry(versionCode = <新号>, versionName = "<对外版本>", date = "<yyyy-MM-dd>", changes = listOf("...", "..."))`，再跑脚本。
> - 该更新日志**内置在 App 内**，用户在「设置 → 更新日志」可查看全部历史版本（教务 / Web / 离线模式均可见，离线也能看）。
> - 这与下方 §4.4「版本台账」是两份独立记录：台账给我们维护用、Changelog.kt 给用户看，**每次发版两者都要更新**。

- `isMinifyEnabled=true` + `isShrinkResources=true`；`proguard-rules.pro` 已 keep 全部 Gson 反射模型
  （`data.remote.dto / data.schedule / data.local / data.account` + 枚举 + Tink），实测 12.5MB→**~3.0MB**。
- BASE_URL 已硬编码 `https://csuftsap.top`。
- 手动方式（仅排障用，**不推荐**，需自己先改 `versionCode`）：`./gradlew clean :app:assembleRelease`；上架商店 AAB：`./gradlew :app:bundleRelease`。

### 4.3 加壳/加固（仅上架或需防破解时）
混淆包送第三方加固平台（腾讯云乐固 / 360加固保 / 梆梆）→ 下载加固包 → **重签**：
```bash
cd sap-android
./harden-resign.sh <加固后的apk>             # 内部 zipalign -p 4 → apksigner 重签(v1+v2+v3) → verify
```
- 必须**先对齐后重签**、用**同一把 keystore**（否则用户无法覆盖升级）。
- 加固平台白名单排除 `edu.csuft.sap.data.**` 反射类，避免壳二次混淆已 keep 的类。
- 加固后实机回归：覆盖安装、三个桌面小组件、应用内自升级。

### 4.4 发新版本（在线升级）

> 🔴 **发版铁律：每次打包发版，`versionCode` 必须 +1（单调递增、永不复用、永不回退）**。**用 `build-release.sh` 打包即自动满足**（脚本强制 +1），不要手动绕过。
> - **对外发布版本 = `versionName`**（1.13 → 1.14 …），每次对外发版递增（脚本默认末位 +1）——这是用户在「关于 / 更新日志」看到的「版本」。`versionCode` 是**内部构建号**，仅内部使用。
> - 在线升级靠 **versionCode 比大小**判更新：版本号 ≤ 用户已装版本 → 用户**收不到**更新。
> - **APK 内嵌的 versionCode 必须 == 发布时填写的 versionCode**。脚本打印的 versionCode 就是 APK 内嵌值，发布时照填即可，天然一致；若手动改 + 发布填错号，已装用户会陷入"装完仍提示更新"的**死循环**。
> - **绝不要用已发布过的 versionCode 复发**（哪怕内容不同）。内容变了就升号。
> - keystore 必须用同一把（见 4.1），否则用户无法覆盖升级。

发版步骤：
0. **先写更新日志**：在 `sap-android/app/.../update/Changelog.kt` 的 `entries` 最前面加好新 `versionCode` 的 `ChangelogEntry`（不写脚本会中止）。
1. `cd sap-android && ./build-release.sh`（要改展示版本名时加 `--name X.Y`）。脚本自动升 `versionCode`、校验更新日志、打包、归档 `release/sap-<vn>-<vc>.apk` + `mapping`，并打印 versionCode/版本名/SHA-256。
2. 管理端 **「App 版本发布」** 页：选脚本产出的 APK + 填脚本打印的 **versionCode/版本名** + 更新说明 → 发布。后端自动传 COS、算 sha256/size、入库；用户开 App 自动收到更新提示。
3. 在下方**版本台账**追加一行（勿删历史）。

#### 版本台账（每次发版必须追加）
| versionCode | versionName | 日期 | 主要变更 | 状态 |
|---|---|---|---|---|
| 2 | 1.1 | 2026-06-15 | 接入下载流量计量(走 `/api/file/go`)、在线升级链路 | 已发布线上 |
| 3 | 1.2 | 2026-06-16 | 新 logo 图标、登录按钮修复、页面过渡动画、教务密码错误识别、课表"----"多周解析、备注重复老师去重 | 已打包(`release/软协课表-1.2.apk`)，待发布（下载依赖自定义域名，见 §8） |
| 6 | 1.4 | 2026-06-16 | 教学周历自动定开学日期、成绩/考试学期切换收敛近4学年、退出登录即时化+我的页直达入口、重新扫描确认弹窗+加载动画、图标安全区内边距修正、`build-release.sh` 自动升版本脚本 | 已打包(`release/sap-1.4-6.apk`)，待发布 |
| 7 | 1.5 | 2026-06-16 | 教务/Web 双模式重构（取消简洁模式，非会员强制 Web、会员可切；成绩仅教务模式；Web 单课表覆盖）、系统返回键逐级回退修复（设置/子页不再退到桌面） | 已打包(`release/sap-1.5-7.apk`，SHA-256 `79a46ff24309cd787a9fcfcc3eb9d599b23e952eeaedbfdfddf7a2de6593a8b6`)，待发布 |
| 8 | 1.6 | 2026-06-16 | 退出登录移到设置页（我的页不再显示）、教务/Web 隐私协议分版本、教务模式课表设置隐藏 WebVPN（仅教务账号切换+课表管理）、Web 模式课表管理按会员细分（会员多课表合并/非会员单课表覆盖）、WebView 加「换账号」并在退出登录时清网页会话 | 已打包(`release/sap-1.6-8.apk`，SHA-256 `e36ef95afbf5549de167c72709ab7b74377c2ff217dbee872877a4c9805237d3`)，待发布 |
| 9 | 1.7 | 2026-06-16 | Web 导入会员遍历所有学期(升序逐个抓取合并，同教务；空学期跳过、最新有数据学期为当前)、非会员仅手动导入当前学期(取消登录自动导入)；WebImport 改协程顺序驱动(超时/去重/UA每次重置)；Web→教务切回自动激活上次教务账号(无则提示绑定，教务模式不残留 Web 课表) | 已打包(`release/sap-1.7-9.apk`，SHA-256 `081bf724b844b991945c67e3dd858720b47a03b1ec045b4fb03ec0e80d8f228a`)，待发布 |
| 10 | 1.8 | 2026-06-16 | 修复退出登录无效(先跳转再延后清 WebView 会话)；课表扫描改“从最早课表起必扫8学期(覆盖四年含大四下)、之后有课继续到无课为止”(教务+会员Web 同逻辑，TermUtil)；补全 ic_launcher 旧版/圆形图标位图+roundIcon(修系统“应用信息”页旧图标) | 已打包(`release/sap-1.8-10.apk`，SHA-256 `babb4aa9d3bd5e9c5bac82d25341a4eb3a56e85bd63ebb5a5f9006af73034f87`)，待发布 |
| 11 | 1.9 | 2026-06-16 | WebView 导入：检测到登录后自动跳课表页——会员全自动扫描多学期、非会员停在课表页手动导入；离开导入页自动清网页登录态(Cookie/缓存)，下次进入需重新登录(便于换账号) | 已打包(`release/sap-1.9-11.apk`，SHA-256 `24d93a9a0eee214d8867d38db7a5950cec36bb3ebe417e21c8cce7f6cf1ca0e6`)，待发布 |
| 12 | 1.10 | 2026-06-16 | 修复备注(无固定时间课，如军训)解析：无周次时课程名与教师糊在一起、且教师被重复几十次——改为拆出含逗号的教师列表并去重(WebScheduleParser + 后端 ScheduleParser 同步修复，**后端需重新部署 Docker 才对教务模式生效**) | 已打包(`release/sap-1.10-12.apk`，SHA-256 `e7085f155b87702730c0ba8c7005f727f245c5041c19c70f3e00de60c47835e1`)，待发布 |
| 13 | 1.11 | 2026-06-16 | 统一课表扫描算法(TermScan)：以**当前学期为锚**向前后扫、连续2个空学期止(大四下空学期不误停)、防强智回显去重(修会员Web 扫到2002年/课表切不走)、按日期默认选当前或下一学期(过7月)；教务与会员Web 共用同一逻辑 | 已打包(`release/sap-1.11-13.apk`，SHA-256 `e289964a1582c89b0a525038b4b47e811cbaf66dcdf48601b44ce741aa34a4ac`)，待发布 |
| 17 | 1.12 | 2026-06-16 | 教务代抓支持**安全手机短信二次验证(MFA)闭环**：绑定时检测到 MFA→后端发短信→App 输码→校验→继续登录(`/api/jw/bind/mfa`+/resend，BindDialog 短信码 UI)；深澜为动态风控 MFA(服务器 IP 陌生触发，用户手机一般不触发)。**后端需重新部署 Docker 才生效**。仅支持 securephone | 已打包(`release/sap-1.12-17.apk`，SHA-256 `529ac15a83257befc983fd5361b2c05f0383eaa06da30ee12f5ccee24d262549`)，被 18 取代 |
| 18 | 1.13 | 2026-06-16 | 与后端镜像 `pllysun/sap:1.4.0` 配套出包（App 代码同 1.12，含短信 MFA 输码 UI），App+Docker 一并发布 | 已打包(`release/sap-1.13-18.apk`，SHA-256 `e2e49f4f99425dac5848fe721a6725b872c22fa1c39f4fbb2d4250bef0a97135`)，待发布 |
| 21 | 1.13 | 2026-06-17 | 永久免密登录（只在服务端 401 才清 token）+ 离线兜底模式（宕机/没网丝滑切离线 web）；离线优化：移除课表顶部离线横幅、账号位直接显示「离线模式」、离线隐藏退出登录、离线版隐私协议（不联网不收集） | 已打包(`release/sap-1.13-21.apk`)，1.13 内部构建（功能并入对外版 1.14） |
| 22 | 1.13 | 2026-06-17 | 新增「更新日志」：设置内查看各版本更新内容，教务 / Web / 离线模式均可见（本地内置、离线可看） | 已打包(`release/sap-1.13-22.apk`)，1.13 内部构建（功能并入对外版 1.14） |
| 23 | 1.14 | 2026-06-18 | 对外版本：永久免密登录 + 离线兜底模式 + 离线优化 + 新增「更新日志」（即 21/22 内部构建的全部工作，毕业为对外 1.14；in-app 更新日志合并为单条 v1.14） | 已打包(`release/sap-1.14-23.apk`，SHA-256 `b2d7f0f77ac5ae5836887e3c88188f40b26b944b6fbab428a1cd747f2c0b86de`)，待发布 |
| 24 | 1.15 | 2026-06-18 | 个人资料显示**平台身份**(游客/正式成员/部长/会长，按届) + **非会员也可改资料** + **头像按 updatedAt 智能缓存**。**配套后端 ≥ 1.4.4**(新增 `/api/auth/info/light`) | 已打包(`release/sap-1.15-24.apk`，SHA-256 `50b1b5d13f7fefbef76af3afd27321ae5ea46543c33c82d941ba158b64dccd5f`)，待发布 |
| 25 | 1.16 | 2026-06-18 | 身份只显示最高届 + 加「届」(如 2022届会长) + **多账号本地状态按会员账号隔离**(教务激活号/用户&头像缓存各账号一份，修“切号丢教务选择”“切号显示上一个账号信息”) + 按钮整块按下高亮(替代文字行灰条)。**纯 App 端，后端无需变(仍 1.4.4)** | 已打包(`release/sap-1.16-25.apk`，SHA-256 `e736bc363005847e6469d06699b3714d8c3bf9e80db0c797a26591dcc19e7695`)，待发布 |
| 26 | **1.17** | 2026-06-18 | **修“换头像后 App 不刷新”**：头像显示 URL 附 `?v=updatedAt` 缓存破坏(固定 URL 原地换图也能刷新) + 头像上传失败显示真实原因(不再静默)。**配套后端 ≥ 1.4.5**(updateProfile 显式刷新 updated_at) | 已打包(`release/sap-1.17-26.apk`，SHA-256 `fa2b1ac9737a061e5e257f65e492f3863a7fd741e13674173fe67ac6b70aae71`)，**当前最新·待发布** |

> versionCode 由 `build-release.sh` 自动递增，**当前已到 26**（19、20 为中间产物/跳过；21、22 为 1.13 内部构建，工作已并入对外版 1.14）。
> **对外版本号约定（自 1.14 起执行）**：`versionName` 为对外发布版本（用户看到的「版本」），每次对外发版 `./build-release.sh` 默认 **versionName 末位 +1**（下次即 1.16→**1.17**、versionCode→26，脚本会要求先在 `Changelog.kt` 加好 `(26, "1.17")` 条目，否则中止）；只想出内部测试包用 `--build-only`（versionName 不变、仅 versionCode +1）。
>
> **后端镜像台账**（⚠️镜像 tag 由多会话并行推进，本文件易滞后——发版前先问清线上实际 tag）：线上实际跑到过 **`pllysun/sap:1.3.2`** → **`pllysun/sap:1.4.0`**(2026-06-16，amd64，短信MFA闭环 + cas 失败判别/captcha failN 修复 + 备注解析去重) → **`1.4.2`**(永久免密：appLogin timeout=-1 + 内置 Redis 持久化 + `/api/ping`) → **`1.4.3`**(2026-06-18，amd64，**关键修复**：全局 `sa-token.active-timeout` 3600→-1，根除"长时间空闲被冻结→401→跳登录"；App 1.14 配套) → **`1.4.4`**(2026-06-18，amd64，含 1.4.3 全部 + 新增 `GET /api/auth/info/light`〔无头像+identities身份+updatedAt〕、`/info` 补 identities+updatedAt；App 1.15 配套) → **`1.4.5`**(2026-06-18，amd64，**修复**：`updateProfile` 显式 `setUpdatedAt(now)`——MyBatis-Plus `strictUpdateFill` 只填 null，selectById 读出的旧 updatedAt 被写回导致改资料/换头像后 updatedAt 不变、App 永不重取头像；App 1.17 配套) → **`1.4.6`**(2026-06-18，amd64，**修复 APK/文件下载**：`/api/file/go` 不再追加 `response-content-disposition`——COS 对匿名公开 GET 拒绝该参数〔InvalidRequest〕、自定义 CDN 域名又不能预签名，导致走 CDN 下载 400；改为直接 302 直链) → **`1.4.7`**(2026-06-18，amd64，**拉数据路径 MFA**：`getSession` 遇短信验证不再只提示"重新绑定"，改抛 `JwMfaPendingException`→全局返回 `code=428 + challengeId/phone`，App 拦截后弹全局短信框续验、验证通过自动重试；配套 App 1.19)。`1.2.0` 作废勿用。
>
> 🔴 **APK 在线更新下载部署要点**：① APK **禁止走 COS 默认域名**(myqcloud.com 对 .apk 返 403/被腾讯封)；必须在 COS 桶配**自定义 CDN 加速域名**(本项目 `dl.csuftsap.top` → 桶 raunxie-1320660133/ap-guangzhou) + DNS CNAME + **CDN 侧 HTTPS 证书**(App 走 https,缺证书必失败)。② 后端「系统设置→对象存储」填 **自定义下载域名 `dl.csuftsap.top`**(= `cos_cdn_domain`,运行时生效、换镜像不丢);`AppVersionService.getLatest()` 会把存量 myqcloud 下载地址自动重写到该域名,**已发布版本无需重发**。③ 需后端 ≥ `1.4.6`(否则 `/go` 的 disposition 参数会让 CDN 下载 400)。已实测:直取 `https://dl.csuftsap.top/apk/...` 200 且 SHA-256 与发布登记一致。
>
> 🔴 **登录态"放久就掉"排障**：症状=杀进程秒进不掉、但长时间(>1h)不进就跳登录。根因=`active-timeout`(无操作冻结)。**必须部署后端 ≥ `1.4.3`**（含 active-timeout=-1）才根治；旧镜像即使 App 用 appLogin 也会被全局 1h 冻结。部署后**已冻结但未过 24h 绝对时效、Redis 仍在**的会话会自动解冻恢复。

---

## 5. 首次上线后

1. 访问 `https://csuftsap.top/admin/`（注意尾斜杠），用 `admin/admin123` 登录 → **立即改密**。
2. 「系统设置 → 对象存储」填腾讯云 COS（否则文件上传 / App 发版不可用）。
3. 冒烟：用户端 `/`、管理端 `/admin/`、`/api/` 正常；触发一次教务绑定确认 OCR 边车连通（验证码自动识别生效）。

---

## 6. 运维注意

- **OCR 边车**已在主镜像内、随容器启动（127.0.0.1:9000，仅本机回环，天然不暴露公网）；后端默认连它，无需任何网络配置。日志在 `/app/logs/ocr.log`；它只在用户绑定教务时被调用（识别失败会回退人工验证码）。
- 容器以 root 跑、Nginx+Java+Certbot+Cron 同容器（特权端口+证书目录所需）；本期可接受，后续可拆多容器/降权。
- `HEALTHCHECK` 仅探 Nginx:80，不探 Java；Java 挂了 Nginx 活着会误报健康，排障看 `/app/logs`。
- 数据库与上传走卷持久化（`/app/data`、`/app/uploads`），定期备份。

---

## 7. 本次已完成 / 已验证

- ✅ 安卓 release 开启 R8 混淆+资源压缩+完整 keep 规则；正式签名(v1/v2/v3)；实机连生产 https 登录/解析正常；mapping 归档。
- ✅ 一键发版脚本 `sap-android/build-release.sh`：**每次自动 `versionCode` +1** + 混淆签名打包 + 归档 `release/` + 打印 SHA-256（实跑通，已出 `sap-1.4-6.apk`）。发版统一走它，杜绝忘升版本号。
- ✅ 加壳重签脚本 `sap-android/harden-resign.sh`（语法校验通过）。
- ✅ OCR 边车（ddddocr）打进主镜像：stage3 装 python3 + 依赖，entrypoint 后台拉起 127.0.0.1:9000；jammy py3.10 依赖解析验证通过。
- ✅ `docker/docker-compose.yml`（单容器）+ `docker/.env.example`；entrypoint 增加 JW_AES_KEY fail-fast。
- ✅ 后端 fat jar、用户端/管理端 dist 均构建通过。
- ✅ `.gitignore` 补充 keystore / `.env` 等敏感文件排除。
- ✅ **流量统计**（管理端 `/admin/` →「流量统计」）：COS 上传/下载按用户计量 + 全接口请求计数（总计/按用户/按接口下钻），图表+表格；3 张 `stat_*` 表 `ddl-auto` 自动建（已连云 MySQL 实测建表+读写+并发 upsert 去重）；下载改重定向计量端点 `/api/file/go`（302 跳 COS，**后端零带宽**，契合 2mbps）；管理端浏览器端到端验证通过、零控制台报错；安卓 `compileDebugKotlin`、两个前端生产构建均通过。详见第 8 节。

**待你提供/决定**：生产 keystore（或沿用已生成的）、JW_AES_KEY、MySQL 实例与强口令、是否上架商店(决定加固/AAB)、数据库选 MySQL 还是 H2。

---

## 8. 流量统计功能部署须知（本次新增）

**结论：`docker run` 的端口 / 环境变量 / 卷都无需改动，只需重建并推送镜像 `pllysun/sap`。**

- **自动建表**：首启在目标库（生产为 `sap`）自动创建 `stat_file_object` / `stat_cos_traffic` / `stat_api_request`（公共与 docker profile 均 `ddl-auto=update`），无需手工建表或迁移。
- **下载计量端点 `/api/file/go`**：记一笔下载流量后 **302 重定向到 COS 直链**，文件字节始终走 CDN、**不经过本服务器**（契合小带宽）。管理端、用户端下载链接、安卓 APK 下载已全部切到它（APK 下载请求带 `sap-token`，以按会员归属流量）。旧的流式代理 `/api/file/download` 保留未删。
  - Nginx `/api/` 反代未设 `proxy_redirect`，302 的 `Location`（`*.myqcloud.com`）原样透传，**无需改 Nginx**。
  - **前提**：COS 桶为**公有读**（与现网用户端直链图片一致）。若改私有桶，需把 `/api/file/go` 改为签发 COS 预签名 URL。
- **接口计数**：全局拦截器统计所有 `/api/**`（按 用户 × 规范化路径(如 `/api/user/{id}`) × 日 滚动计数，`ON DUPLICATE KEY UPDATE` 原子自增）；`/api/stats/**` 自身已排除，不自计数。
- **上传计量**：管理端 + 用户端共用 `/api/file/upload`，已在 `CosService` 内统一埋点，**两端自动覆盖**，无需前端改动。
- **权限**：`/api/stats/**` 仅角色 0/1（超管/会长）可访问；管理端「流量统计」菜单对其他角色隐藏。
- **重建即生效**：前端 ×2（`npm run build`）+ 后端（`mvn package`）都从源码构建（见第 2/3 节），重建镜像自动带上本次改动；安卓 APK 计量需重新出包（见 4.2）才生效。

### 8.1 镜像构建铁律（务必走脚本）

**镜像统一用脚本构建：`./docker/build-image.sh`** —— 它把下面两条铁律写死，杜绝手敲 `docker build` 踩坑：

```bash
./docker/build-image.sh                 # 读 docker/IMAGE_VERSION → patch +1 → buildx amd64 构建并推送 → 写回新版本号
./docker/build-image.sh --version 1.5.0 # 次/主版本升级时指定
# 脚本内部等价于：
#   docker buildx build --builder ops-builder --platform linux/amd64 \
#     -t pllysun/sap:<新版本> -f docker/Dockerfile --push .
```

- **铁律①：平台必须 `linux/amd64`**。生产服务器是 amd64/x86_64；在 Apple Silicon/arm64 上用普通 `docker build` 只出 arm64，amd64 服务器拉下来报 `no matching manifest for linux/amd64`。脚本已写死 `--platform linux/amd64`。校验：`docker buildx imagetools inspect pllysun/sap:<版本> | grep Platform` 应见 `linux/amd64`。
- **铁律②：每次构建必须升镜像版本号、用新的不可变 tag，切勿覆盖旧 tag**。镜像加速器（如轩辕镜像 xuanyuan.run）按 tag 缓存 manifest；同名 tag 推不同内容/架构，加速器会继续返回旧缓存（实测覆盖 `1.1.0` 后仍给旧 arm64）。脚本每次读 `docker/IMAGE_VERSION` 自动 patch +1 并在成功后写回，保证 tag 单调递增、不复用。无需重建只想复制 manifest 到新 tag：`docker buildx imagetools create -t pllysun/sap:新tag pllysun/sap:旧tag`。
- **当前镜像版本号记在 `docker/IMAGE_VERSION`**（脚本的唯一真源）。⚠️多会话并行推镜像时该文件可能滞后——发版前先 `docker buildx imagetools inspect` 确认线上实际 tag，必要时手动校正 `IMAGE_VERSION` 再 `--version` 指定。
- **国内服务器经镜像源拉取**（轩辕镜像前缀式）：
  ```bash
  docker pull <你的域名>.xuanyuan.run/pllysun/sap:<版本>
  docker tag  <你的域名>.xuanyuan.run/pllysun/sap:<版本> pllysun/sap:<版本>   # 重打回原名，run 命令不变
  ```
- **当前生产镜像**：`pllysun/sap:1.1.1`（linux/amd64，含流量统计功能）。
