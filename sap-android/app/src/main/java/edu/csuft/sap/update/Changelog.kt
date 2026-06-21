package edu.csuft.sap.update

/** 单个版本的更新日志条目。 */
data class ChangelogEntry(
    val versionCode: Int,
    val versionName: String,
    val date: String,            // 发布日期 yyyy-MM-dd
    val changes: List<String>,   // 该版本的更新点，逐条
)

/**
 * App 更新日志（本地内置，离线可见；教务 / Web / 离线模式均可在「设置 → 更新日志」查看，展示全部历史版本）。
 *
 * 版本号约定：versionName（如 1.13/1.14）= 对外发布版本，每次发布递增（用户看到的「版本」）；
 *            versionCode（21/22…）     = 内部构建号，仅内部使用、每次构建自动 +1（驱动「检查更新」比对）。
 *
 * ⚠️ 打包铁律：每次发布必须在列表【最前面】新增一条记录，其 (versionCode, versionName) 必须与本次构建完全一致，
 *    否则 build-release.sh 会拒绝打包（与"强制 versionCode +1"同级的硬约束，杜绝发版忘写更新日志）。
 *    最新版本放最前；date 用实际发布日期。
 *
 * 文案规范（用户 2026-06-18 定）：只列「新增 / 优化 / 修复 了什么」，一条一句、不写原因与解释、不暴露内部细节。
 */
object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionCode = 35, versionName = "1.26", date = "2026-06-19",
            changes = listOf(
                "新增成绩菜单显示/隐藏开关（我的 → 设置）",
                "优化默认课表节次时间",
            ),
        ),
        ChangelogEntry(
            versionCode = 34, versionName = "1.25", date = "2026-06-18",
            changes = listOf(
                "优化课表节次时间修改后即时生效",
            ),
        ),
        ChangelogEntry(
            versionCode = 33, versionName = "1.24", date = "2026-06-18",
            changes = listOf(
                "上课弹窗支持上下左右滑动关闭",
                "课表时间设置支持逐节调整起止时间",
                "优化对话框与选择弹窗样式",
            ),
        ),
        ChangelogEntry(
            versionCode = 32, versionName = "1.23", date = "2026-06-18",
            changes = listOf(
                "优化上课提醒的准时性与稳定性",
                "上课提醒自动跟随课表变化",
                "修复桌面小组件课表显示",
            ),
        ),
        ChangelogEntry(
            versionCode = 31, versionName = "1.22", date = "2026-06-18",
            changes = listOf(
                "优化上课弹窗与选择类弹窗样式",
                "上课提醒跟随当前课表与提醒设置",
            ),
        ),
        ChangelogEntry(
            versionCode = 30, versionName = "1.21", date = "2026-06-18",
            changes = listOf(
                "优化上课提醒样式，显示课程、时间、地点与倒计时",
                "新增锁屏通知",
            ),
        ),
        ChangelogEntry(
            versionCode = 29, versionName = "1.20", date = "2026-06-18",
            changes = listOf(
                "上课提醒新增「确保准时收到」权限引导",
            ),
        ),
        ChangelogEntry(
            versionCode = 28, versionName = "1.19", date = "2026-06-18",
            changes = listOf(
                "教务短信验证支持在 App 内输入验证码",
            ),
        ),
        ChangelogEntry(
            versionCode = 27, versionName = "1.18", date = "2026-06-18",
            changes = listOf(
                "新增上课弹窗提醒（悬浮窗）",
            ),
        ),
        ChangelogEntry(
            versionCode = 26, versionName = "1.17", date = "2026-06-18",
            changes = listOf(
                "修复更换头像后不刷新",
                "优化头像上传失败提示",
            ),
        ),
        ChangelogEntry(
            versionCode = 25, versionName = "1.16", date = "2026-06-18",
            changes = listOf(
                "优化平台身份显示",
                "优化多账号下的教务账号记忆",
                "修复切换账号后的信息显示",
            ),
        ),
        ChangelogEntry(
            versionCode = 24, versionName = "1.15", date = "2026-06-18",
            changes = listOf(
                "个人资料新增平台身份展示",
                "非会员也可修改个人资料",
                "头像智能缓存，更省流量",
            ),
        ),
        ChangelogEntry(
            versionCode = 23, versionName = "1.14", date = "2026-06-18",
            changes = listOf(
                "支持永久免密登录",
                "新增离线模式",
                "新增更新日志",
            ),
        ),
    )
}
