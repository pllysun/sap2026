# 软协课表 · 图标与登录页

应用图标以**纯白底**为固定要求，标识名为「课间节奏」：四块错位的圆角课程色块整体轻倾 12°，用长短变化和块间留白表达课程与课间的节奏。主色晴蓝 `#3564DC`，以浅蓝 `#9EBFFC` 和杏橙 `#F2A77D` 点缀。标准桌面图标与应用内 Logo 都使用 `#FFFFFF` 底色。界面图标使用 24 × 24 网格、1.75dp 描边和圆角端点；三种课表来源分别用学位帽、网页导入、三人组表示。

## 资源维护

- `icons.json`：33 个界面图标的唯一几何数据源。
- `app-icon.svg`：可直接用于设计工具的应用标识。
- `app-icon.png`：1024 × 1024 白底导出图；图形变更后需从 `app-icon.svg` 重新导出。
- `svg/`：静态 SVG 与 7 个动态 SVG，均可随 `currentColor` 着色。
- `../scripts/generate_icons.py`：同时生成 SVG、Compose `AppIcons.kt`、启动图标与通知图标；仅依赖 Python 标准库。

在仓库根目录运行：

```bash
python3 sap-android/scripts/generate_icons.py
python3 sap-android/scripts/generate_icons.py --check
```

不要直接修改生成的 `AppIcons.kt` 或 SVG。界面图标修改 `icons.json`，应用标识修改生成脚本中的 `BRAND` 和 `course_block`。Android 原生使用相同 SVG 路径生成 `ImageVector`，无须嵌入 WebView。启动图标采用自适应矢量资源，覆盖所有支持的设备（API 26+）；前景相对母版缩放至 88%，将完整标识保留在中央 66dp 安全圆内。API 33+ 的单色主题图标与通知图标保留四块之间的透明间隙；通知导出使用独立视口，避免倾斜后的边缘被裁掉。

## 动效

- 底栏：240ms 线框/实心交叉淡化与轻微上浮，操作结束后停止。
- 模式选择：240ms 容器颜色变化与图标反馈。
- 同步：仅请求进行时匀速旋转，1200ms 一周；系统关闭动画时显示静态图标。
- 登录：360ms 淡入，12dp 上浮；按钮保留原生点击反馈与加载语义。
- 导出的动态 SVG 遵循 `prefers-reduced-motion`。

## 登录与无障碍

登录页按安全区布局，表单宽度最大 460dp；小屏、横屏、软键盘和放大字体下可滚动。学号支持键盘“下一步”，密码支持“完成”登录；加载中不可重复提交，错误文案完整换行并通过 live region 提示。默认主题色调整为晴蓝，保留用户已保存的自定义颜色。

## 本地视觉验收

`src/debug` 中的 `DesignGalleryActivity` 复用真实登录、模式选择和动态矢量组件，提供本地示例状态，不调用登录接口。所有 release 包均不包含该入口。APK 构建号须遵循根目录 `APP_BUILD_RELEASE.md`，每次构建递增。

安装 debug 包后，可通过 adb 打开：

```bash
adb shell am start -n edu.csuft.sap/.design.DesignGalleryActivity --es screen login
adb shell am start -n edu.csuft.sap/.design.DesignGalleryActivity --es screen modes
adb shell am start -n edu.csuft.sap/.design.DesignGalleryActivity --es screen icons
```

登录示例还支持 `login-error`、`login-long-error`、`login-loading`。切换页面时先关闭该 Activity，或为 `am start` 加 `-S`。若使用独立的 `applicationIdSuffix` 构建设计预览包，将上述组件名前的包名替换为实际 applicationId，Activity 类名仍是 `edu.csuft.sap.design.DesignGalleryActivity`。

注册页复用登录页的圆角、输入框颜色、图标和主题色，性别以单选按钮呈现。支持 `register`、`register-captcha`、`register-error`、`register-loading` 本地示例；密码只保存在页面 ViewModel 内存中，退出注册或成功后清理。表单可滚动，适配小屏、横屏、键盘和放大字体。字段错误定位到首个错误输入框，输入修正过程中不自动跳走焦点。

完整注册验收使用隔离的 `.registrationqa` 包，账号仅存在本地桩服务内存中，不连接真实注册服务：

```bash
python3 scripts/qa/registration_server.py  # 需要 Pillow
./build-release.sh --test --prepare-only
gradle -I scripts/qa/registration.init.gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n edu.csuft.sap.registrationqa/edu.csuft.sap.design.DesignGalleryActivity --es screen registration-flow
```

从登录页点击“注册账号”，填写学号、6–64 位密码、姓名、性别（男=1、女=0）和 5–15 位 QQ。首次提交应出现验证码，输入 `A7K2` 后注册成功并返回登录页，学号自动填入，密码留空。错误验证码、已注册学号及任何注册失败均须换图、清空旧答案并保留其他字段。点击图片手动换图，空字段或无效 QQ 不得发送注册请求；连点提交只发送一次。

通过 `http://127.0.0.1:18893/stats` 查看请求次数和内存账号数量；`/control?captcha_fail=1` 模拟图片加载失败，改回 `0` 后点击重试；`/control?delay=3` 检查加载态，`/control?exempt=1` 检查服务端豁免验证码时直接成功。关闭服务后检查网络失败保留输入、不进入主页。上述入口仅在 debug 包存在，完整流程入口还校验隔离包名。

周次弹窗支持 `week-picker-upcoming`、`week-picker-active`、`week-picker-past`、`week-picker-missing`，固定日期为 2026-09-05，复用正式弹窗来检查未开学、学期中、学期结束和日期缺失四种状态。

`schedule-weeks` 会激活独立的 `week-picker-qa` 班级缓存样本，复用真实 `ScheduleViewModel` 和课表页。使用独立 applicationId 的预览包验收：从旧学期第 20 周切换到新学期，应自动显示第 1 周和对应示例课；手动选择第 7 周后，“查看第 1 周”应恢复首周。该样本以设备日期的下一个周一作为开学日。

头像验收使用真实首页和“我的”页面。从 `sap-android` 目录在两个终端运行本地桩服务和独立 debug 构建：

```bash
python3 scripts/qa/avatar_server.py
gradle -I scripts/qa/avatar.init.gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n edu.csuft.sap.avatarqa/edu.csuft.sap.design.DesignGalleryActivity --es screen profile-avatar
```

初始红色头像只应下载一次，多次切换“课表 / 我的”不增加 `/avatar.png?v=1` 的请求数。通过本机 `http://127.0.0.1:18891/stats` 查看计数，`/control?version=2&color=blue&delay=7&fail=0` 模拟延迟 7 秒的蓝色新头像，`/control?version=3&color=green&delay=0&fail=1` 模拟失败；把 `fail` 改为 `0` 后再次进“我的”应成功重试。新图加载期间和失败时都须保留旧头像。该入口只允许 `.avatarqa` 测试包使用，release 中不存在。

班级目录和本地课程验收使用独立的 `.classqa` 包：

```bash
python3 scripts/qa/class_schedules_server.py
gradle -I scripts/qa/class-schedules.init.gradle :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n edu.csuft.sap.classqa/edu.csuft.sap.design.DesignGalleryActivity --es screen class-schedules --ez reset true
```

`reset=true` 只重置隔离测试包中的三个班级样本，每个样本有两个学期，其中一个班级没有底本课程。重启验收时省略 `reset`，确认删除后的数据不会重新出现。

首次加载时应显示动画，加载完成后再显示筛选项；右上角菜单只有“刷新目录”和“批量删除”。`http://127.0.0.1:18892/stats` 可检查强制刷新是否再次请求学期与班级两个接口；`/control?delay=3&fail=1` 模拟失败，改回 `fail=0` 后可重试。

从样本班级进入真实课表页，分别检查详情“＋”、悬浮“＋”、空白课格和课程编辑。新增课程须归属当前班级/学期；同一时段的多门课由折角和详情列表展示。批量删除当前班级与另一班级后，应保留剩余班级；全部删除后应回到未选班级状态。本地课表槽（包含全部学期、自建课程和备注）以及上次选择都必须清理，重启后不应恢复。所有样本入口均不进入 release 包。

颜色浓淡验收沿用上述 `.classqa` 构建，无需启动目录桩服务：

```bash
adb shell am start -n edu.csuft.sap.classqa/edu.csuft.sap.design.DesignGalleryActivity --es screen course-colors --ez reset true
```

该入口使用真实 ViewModel、存储、个性化预览和课表页，三张同色卡片分别来自系统课程、自建预设色课程和自建指定色课程。100% 对应旧版 165% 的效果；0% 为白底，200% 沿新的标尺进一步加深。旧版保存的 165% 应显示为新的 100%，0% 保存并重启后仍为 0%。默认只有系统课程随滑条实时变化，勾选“同时调整自建课程”后，另外两张卡片也应变化；取消勾选后自建课程立即恢复原色。返回课表、重新进入和省略 `reset` 冷启动后均应保持设置，恢复默认后应回到 100% 且不勾选，两个学期的日期和课程内容保留。
