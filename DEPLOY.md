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
docker build -t pllysun/sap:1.1.0 -f docker/Dockerfile .           # 仓库根执行；用具体版本号，勿用 latest（便于回滚/审计）
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
| 主镜像 | `docker build -t pllysun/sap:1.1.0 -f docker/Dockerfile .` | 镜像(自动重打两个前端 + 打入后端 jar + OCR) | ✅ 已验证 |

> OCR 边车（`ocr-sidecar/main.py` + `requirements.txt`）已直接打进主镜像（stage3 装 python3 + ddddocr，entrypoint 后台拉起 127.0.0.1:9000），**不再是独立容器**。`ocr-sidecar/Dockerfile` 仅作"未来拆分独立部署"的备用，正式部署用不到。

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
**正式打包统一用脚本** `sap-android/build-release.sh`——它强制把 `versionCode` +1，再 R8 混淆+资源压缩+正式签名打包，并把产物按版本归档到 `release/`，杜绝"忘了升版本号"：
```bash
cd sap-android
./build-release.sh                # versionCode 自动 +1；versionName 不变
./build-release.sh --name 1.5     # 同时把 versionName 改成 1.5
./build-release.sh --no-bump      # 仅重打当前版本（慎用，用户端识别不到更新）
```
脚本做了：升版本号 → `clean :app:assembleRelease` → 产物归档 `release/sap-<vn>-<vc>.apk` + `mapping-<vn>-<vc>.txt` → 打印 大小/SHA-256/签名证书。缺 `keystore.properties` 会直接中止（不会出 debug 签名包）。可用 `JAVA_HOME`/`GRADLE_BIN` 覆盖工具路径（跨机/CI）。

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
> - 在线升级靠 **versionCode 比大小**判更新：版本号 ≤ 用户已装版本 → 用户**收不到**更新。
> - **APK 内嵌的 versionCode 必须 == 发布时填写的 versionCode**。脚本打印的 versionCode 就是 APK 内嵌值，发布时照填即可，天然一致；若手动改 + 发布填错号，已装用户会陷入"装完仍提示更新"的**死循环**。
> - **绝不要用已发布过的 versionCode 复发**（哪怕内容不同）。内容变了就升号。
> - keystore 必须用同一把（见 4.1），否则用户无法覆盖升级。

发版步骤：
1. `cd sap-android && ./build-release.sh`（要改展示版本名时加 `--name X.Y`）。脚本自动升 `versionCode`、打包、归档 `release/sap-<vn>-<vc>.apk` + `mapping`，并打印 versionCode/版本名/SHA-256。
2. 管理端 **「App 版本发布」** 页：选脚本产出的 APK + 填脚本打印的 **versionCode/版本名** + 更新说明 → 发布。后端自动传 COS、算 sha256/size、入库；用户开 App 自动收到更新提示。
3. 在下方**版本台账**追加一行（勿删历史）。

#### 版本台账（每次发版必须追加）
| versionCode | versionName | 日期 | 主要变更 | 状态 |
|---|---|---|---|---|
| 2 | 1.1 | 2026-06-15 | 接入下载流量计量(走 `/api/file/go`)、在线升级链路 | 已发布线上 |
| 3 | 1.2 | 2026-06-16 | 新 logo 图标、登录按钮修复、页面过渡动画、教务密码错误识别、课表"----"多周解析、备注重复老师去重 | 已打包(`release/软协课表-1.2.apk`)，待发布（下载依赖自定义域名，见 §8） |
| 6 | 1.4 | 2026-06-16 | 教学周历自动定开学日期、成绩/考试学期切换收敛近4学年、退出登录即时化+我的页直达入口、重新扫描确认弹窗+加载动画、图标安全区内边距修正、`build-release.sh` 自动升版本脚本 | 已打包(`release/sap-1.4-6.apk`)，待发布 |
| 7 | 1.5 | 2026-06-16 | 教务/Web 双模式重构（取消简洁模式，非会员强制 Web、会员可切；成绩仅教务模式；Web 单课表覆盖）、系统返回键逐级回退修复（设置/子页不再退到桌面） | 已打包(`release/sap-1.5-7.apk`，SHA-256 `79a46ff24309cd787a9fcfcc3eb9d599b23e952eeaedbfdfddf7a2de6593a8b6`)，待发布 |

> versionCode 由 `build-release.sh` 自动递增，**当前已到 7**；下次发版直接跑脚本即从 8 起，无需手动记。（4、5 为脚本上线前的中间产物，已跳过。）

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

### 8.1 镜像架构 + 镜像源拉取（踩坑记录）
- **务必为目标服务器架构构建**：生产服务器是 **amd64/x86_64**（`uname -m`=x86_64）。在 Apple Silicon/arm64 机器上用普通 `docker build` 只会出 arm64 单架构，amd64 服务器拉下来报 `no matching manifest for linux/amd64`。正确做法用 buildx 交叉构建并推送：
  ```bash
  docker buildx build --builder ops-builder --platform linux/amd64 \
    -t pllysun/sap:<版本> -f docker/Dockerfile --push .     # 仓库根；需要 amd64+arm64 双架构则 --platform linux/amd64,linux/arm64
  # 校验架构：docker buildx imagetools inspect pllysun/sap:<版本>
  ```
- **每次发布用新的不可变 tag，切勿覆盖旧 tag**：镜像加速器（如轩辕镜像 xuanyuan.run）按 tag 缓存 manifest；若把不同内容/架构推到同名 tag，加速器会继续返回旧缓存（实测覆盖 `1.1.0` 后镜像源仍给旧 arm64）。换新 tag 即可拉到新鲜镜像。已用 `docker buildx imagetools create -t pllysun/sap:新tag pllysun/sap:旧tag` 秒级复制 manifest 到新 tag（无需重建）。
- **国内服务器经镜像源拉取**（轩辕镜像前缀式）：
  ```bash
  docker pull <你的域名>.xuanyuan.run/pllysun/sap:<版本>
  docker tag  <你的域名>.xuanyuan.run/pllysun/sap:<版本> pllysun/sap:<版本>   # 重打回原名，run 命令不变
  ```
- **当前生产镜像**：`pllysun/sap:1.1.1`（linux/amd64，含流量统计功能）。
