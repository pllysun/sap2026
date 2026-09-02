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
 * ⚠️ 只有通过在线升级平台正式发布给用户的版本，才在列表【最前面】新增记录。
 *    调试/测试构建虽有独立 versionCode，但不得写入这里。正式发布时 (versionCode, versionName)
 *    必须与 APK 完全一致，且 changes 必须覆盖自上一个线上版本以来的全部变更；最新版本放最前。
 *
 * 文案规范（用户 2026-06-18 定）：只列「新增 / 优化 / 修复 了什么」，一条一句、不写原因与解释、不暴露内部细节。
 */
object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionCode = 59, versionName = "1.36", date = "2026-08-14",
            changes = listOf(
                "新增课表公告，支持查看最新公告与历史公告",
                "优化账号登录后的功能与使用模式适配",
                "优化 Web 模式下课表的切换、替换与管理体验",
                "修复使用模式调整后课表可能为空并需要重新导入",
                "优化设置页面布局与退出登录入口",
            ),
        ),
        ChangelogEntry(
            versionCode = 56, versionName = "1.35", date = "2026-08-10",
            changes = listOf(
                "新增课表个性化中心，支持实时预览与周次切换",
                "支持调整课表尺寸、文字对齐、课程信息显示及课程卡样式",
                "新增课表背景图片，支持从相册选择、缩放与裁剪",
                "优化个性化配置，切换账号或学期后仍然生效，并支持一键恢复默认",
                "优化意见反馈讨论，支持用户头像、身份标签与分层回复",
                "支持反馈发起人主动关闭自己的 Issue，关闭后不再接受回复",
                "修复教务账号备注无法持久保存",
                "修复部分教务账号无法通过 WebVPN 同步成绩",
                "修复网页登录后可能跳错页面导致课表导入失败",
                "优化软件更新弹窗的日志排版",
            ),
        ),
        ChangelogEntry(
            versionCode = 44, versionName = "1.34", date = "2026-07-20",
            changes = listOf(
                "优化教学评价手动填写与一键满评的兼容性",
                "支持根据学校平台限制自动调整评价分数",
                "优化评价提交异常提示并保留未提交内容",
                "修复课表分享图片可能显示旧周次",
            ),
        ),
        ChangelogEntry(
            versionCode = 42, versionName = "1.33", date = "2026-07-12",
            changes = listOf(
                "优化教学评价入口与同步体验",
                "支持按评教学年切换并缓存评价信息",
                "优化教务缓存账号隔离",
                "优化课表周次选择显示",
                "修复更新后启动闪退",
            ),
        ),
        ChangelogEntry(
            versionCode = 38, versionName = "1.29", date = "2026-07-11",
            changes = listOf(
                "修复若干 bug",
            ),
        ),
        ChangelogEntry(
            versionCode = 37, versionName = "1.28", date = "2026-07-10",
            changes = listOf(
                "新增意见反馈图片附件",
                "优化反馈提交页与图片授权方式",
            ),
        ),
        ChangelogEntry(
            versionCode = 36, versionName = "1.27", date = "2026-07-10",
            changes = listOf(
                "新增会员意见反馈 Issue 中心",
                "支持查看维护者回复与反馈关闭进度",
                "更新教学评价入口，支持手动评价与一键满评",
            ),
        ),
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
