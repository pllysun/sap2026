# SAP Android —— 软协课表 App

中南林业软件协会 App：会员登录（永久免密）+ 课表 / 成绩 / 考试，数据来自 `sap-backend` 的 `/api/jw/*` 教务接口。

AI、人工与 GitHub Actions 的测试打包、正式构建和在线发布统一遵循仓库根目录 [`APP_BUILD_RELEASE.md`](../APP_BUILD_RELEASE.md)。测试包自动递增构建号但不发布；正式流程会再次递增、全新构建，并把同一份更新日志写入 App 与升级平台。

## 技术栈
- Kotlin + Jetpack Compose（Material 3）
- Retrofit + OkHttp + Gson（网络）
- Coroutines + ViewModel + StateFlow
- EncryptedSharedPreferences（加密保存登录 token）
- 手写依赖容器 `di/Graph`（无 DI 框架）

## 运行
> 本工程需用 **Android Studio**（Koala/2024.1+）打开构建，命令行需自备 Android SDK。

1. 先启动后端 `sap-backend`（默认 `http://localhost:8081`）。
2. Android Studio 打开本目录（`sap-android/`），等待 Gradle 同步（会自动补全 wrapper）。
3. 用**模拟器**运行：App 通过 `http://10.0.2.2:8081` 访问宿主机后端（已配置）。
   - 真机调试：把 `app/build.gradle.kts` 里 `BASE_URL` 改成电脑局域网 IP（如 `http://192.168.x.x:8081`），
     并在 `res/xml/network_security_config.xml` 增加该域名的明文放行；生产请用 https 域名。

## 功能与流程
- **账号注册**：登录页进入原生注册页，基本字段与 Web 一致（学号、密码、姓名、性别、QQ）。手机端额外验证 QQ 邮箱：通过 `POST /api/auth/app/register/email-code` 申请验证码，完成图形挑战后发信；凭六位邮箱验证码和请求标识调用 `POST /api/auth/app/register`。验证码 15 分钟有效，180 秒后可重新申请，修改学号或 QQ 后需重新验证。注册成功后返回登录并填入学号；Web 注册流程保持独立。
- **会员登录**：`POST /api/auth/app/login` 取**永不过期** token，存入加密本地存储。
- **免密**：启动时若本地有 token，调 `GET /api/auth/info` 校验；通过则直接进主页。
- **绑定教务**：「我的 → 绑定教务账号」，输入学校统一身份账号密码（`POST /api/jw/bind`，后端校验并 AES 加密存储）。
- **课表**：`GET /api/jw/schedule?term=`，按学期切换；网格展示。
- **成绩**：`GET /api/jw/grades`，汇总学分/绩点 + 按学期分组。
- **考试**：`GET /api/jw/exams?term=`，按学期查看（当前学期考试期才有数据）。

## 关键配置
| 位置 | 说明 |
|------|------|
| `app/build.gradle.kts` → `BASE_URL` | 后端地址（默认模拟器 `http://10.0.2.2:8081`） |
| `applicationId` | `edu.csuft.sap` |
| `minSdk` / `targetSdk` | 26 / 34 |
| `res/xml/network_security_config.xml` | 明文放行的调试域名 |

## 目录
```
app/src/main/java/edu/csuft/sap/
  data/local      TokenStore（加密 token）
  data/remote     ApiService / ApiClient / DTO / Outcome
  data/repository AuthRepository / JwRepository
  di              Graph（依赖容器）
  ui/auth         登录 / 注册 / 图形验证码 / QQ 邮箱验证
  ui/home         底部导航主壳
  ui/schedule     课表（网格）
  ui/grade        成绩
  ui/exam         考试
  ui/profile      我的（绑定/解绑/登出）
  ui/common       通用组件（Loading/Error/学期选择器）
  ui/theme        主题
```

## 课表初始设置

- 所有身份的新账号默认使用班级课表。模式选择按登录账号保存，登录进入首页前先恢复；已有教务、Web 或班级课表选择的账号继续恢复历史模式。
- 课表个性化的颜色浓淡范围为 0–200%，新 100% 对应原 165% 的效果，0% 为白底。课表网格、效果预览与分享图片使用同一计算。
- 旧版默认值使用新的 100%；旧版手动选择的其他浓淡按新标尺换算（165% → 100%）。新的百分比独立保存，避免 0% 与旧版“未设置”混淆或反复换算。
