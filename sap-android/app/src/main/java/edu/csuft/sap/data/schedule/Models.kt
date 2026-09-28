package edu.csuft.sap.data.schedule

import kotlin.math.roundToInt

/** 用户自建课程（本地存储）。node 为“节”序号 1-12（WakeUp 风格，按节排）。 */
data class CustomCourse(
    val id: String,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,                 // 1=周一 … 7=周日
    val startNode: Int,           // 节 1-12
    val endNode: Int,             // 节 1-12
    val weeks: List<Int> = emptyList(),
    val colorIndex: Int = 0,
    val customColor: Long? = null, // 自定义颜色(ARGB)；非空时优先于 colorIndex
)

/**
 * 课表设置。
 *
 * 开学日期与总周数属于具体学期；其余显示项会由 [ScheduleRoot.displaySettings] 覆盖，
 * 因而在同一台设备上的所有登录账号、教务账号和学期之间共享。
 */
data class ScheduleSettings(
    val semesterStartDate: String? = null, // 课表第一天（第一周周一），ISO 如 "2026-02-23"
    val semesterStartDateManual: Boolean = false, // true=用户手动设过，自动同步不覆盖；false=自动/未设
    val totalWeeks: Int = 20,
    val showWeekend: Boolean = false,      // 是否显示周末（默认 5 天）
    val showNonWeek: Boolean = false,      // 是否显示非本周课程（灰显）
    val showOtherWeekInDetail: Boolean = true, // 点某节课时，弹窗是否一并列出该时段在其他周的不同课程
    val weekStartSunday: Boolean = false,  // 每周起始日：周日作为一周开始
    val periodsPerDay: Int = 10,           // 一天的总课时数（节），可设 8-16
    val showNowLine: Boolean = false,      // 是否在课表上画“当前时间”红线（默认关）
    val rowHeight: Int = 0,                // 单节课格高度(dp)，0=兼容旧数据的默认值
    val cardTextScale: Int = 0,            // 课表文字缩放百分比，0=兼容旧数据的默认100
    val sidebarWidth: Int = 0,              // 左侧节次/时间栏宽度(dp)
    val headerHeight: Int = 0,              // 星期与日期表头高度(dp)
    val hideLocation: Boolean = false,
    val hideTeacher: Boolean = false,
    val centerTextHorizontally: Boolean = true,
    val centerTextVertically: Boolean = true,
    val cardFontSize: Int = 0,              // 课程卡基础字号(sp)
    val cardBorderStyle: Int = 0,           // 0=无 1=实线 2=虚线
    val cardCornerRadius: Int = 0,          // 课程卡圆角(dp)
    val cardInnerPadding: Int = 0,          // 课程卡内部填充(dp)
    val cardOuterSpacing: Int = 0,          // 课程卡外部间距(dp)
    val cardOpacity: Int = 0,               // 课程卡不透明度百分比
    val cardColorIntensity: Int = 0,        // 旧版浓淡，仅用于读取并换算历史设置；旧 0/100 使用新默认值
    val cardColorIntensityPercent: Int? = null, // 新标尺 0..200；null=未设置，100 对应旧版 165
    val colorIntensityAffectsCustom: Boolean = false, // 是否同时调整用户自建课程（含预设颜色）
    val backgroundImagePath: String? = null, // 裁剪后复制到 App 私有目录的背景图
) {
    /** 有效每日节数：兼容旧数据缺该字段(Gson 反序列化为 0) → 默认 10，并夹在 8..16。 */
    val dailyPeriods: Int get() = periodsPerDay.takeIf { it in 8..16 } ?: 10

    /** 有效课格高度(dp)：旧数据缺字段时 Gson 会给 0，统一回退到新默认值。 */
    val rowHeightDp: Int get() = rowHeight.takeIf { it in ScheduleAppearanceLimits.ROW_HEIGHT } ?: 56

    val cardScale: Float get() = (cardTextScale.takeIf { it in ScheduleAppearanceLimits.TEXT_SCALE } ?: 100) / 100f
    val sidebarWidthDp: Int get() = sidebarWidth.takeIf { it in ScheduleAppearanceLimits.SIDEBAR_WIDTH } ?: 42
    val headerHeightDp: Int get() = headerHeight.takeIf { it in ScheduleAppearanceLimits.HEADER_HEIGHT } ?: 48
    val cardFontSizeSp: Int get() = cardFontSize.takeIf { it in ScheduleAppearanceLimits.CARD_FONT_SIZE } ?: 11
    val borderStyle: Int get() = cardBorderStyle.coerceIn(0, 2)
    val cornerRadiusDp: Int get() = cardCornerRadius.takeIf { it in ScheduleAppearanceLimits.CORNER_RADIUS } ?: 8
    val innerPaddingDp: Int get() = cardInnerPadding.takeIf { it in ScheduleAppearanceLimits.INNER_PADDING } ?: 4
    val outerSpacingDp: Int get() = cardOuterSpacing.takeIf { it in ScheduleAppearanceLimits.OUTER_SPACING } ?: 2
    val opacityFraction: Float get() = (cardOpacity.takeIf { it in ScheduleAppearanceLimits.OPACITY } ?: 100) / 100f
    val colorIntensityPercent: Int
        get() = cardColorIntensityPercent?.let {
            it.takeIf { value -> value in ScheduleAppearanceLimits.COLOR_INTENSITY } ?: 100
        } ?: if (cardColorIntensity in 25..200 && cardColorIntensity != 100) {
            (cardColorIntensity * 100f / ScheduleAppearanceLimits.LEGACY_COLOR_BASELINE).roundToInt()
        } else 100
}

/** 设备级课表显示配置；不包含必须随学期变化的开学日期和总周数。 */
data class ScheduleDisplaySettings(
    val showWeekend: Boolean = false,
    val showNonWeek: Boolean = false,
    val showOtherWeekInDetail: Boolean = true,
    val weekStartSunday: Boolean = false,
    val periodsPerDay: Int = 10,
    val showNowLine: Boolean = false,
    val rowHeight: Int = 0,
    val cardTextScale: Int = 0,
    val sidebarWidth: Int = 0,
    val headerHeight: Int = 0,
    val hideLocation: Boolean = false,
    val hideTeacher: Boolean = false,
    val centerTextHorizontally: Boolean = true,
    val centerTextVertically: Boolean = true,
    val cardFontSize: Int = 0,
    val cardBorderStyle: Int = 0,
    val cardCornerRadius: Int = 0,
    val cardInnerPadding: Int = 0,
    val cardOuterSpacing: Int = 0,
    val cardOpacity: Int = 0,
    val cardColorIntensity: Int = 0,
    val cardColorIntensityPercent: Int? = null,
    val colorIntensityAffectsCustom: Boolean = false,
    val backgroundImagePath: String? = null,
)

fun ScheduleSettings.toDisplaySettings() = ScheduleDisplaySettings(
    showWeekend = showWeekend,
    showNonWeek = showNonWeek,
    showOtherWeekInDetail = showOtherWeekInDetail,
    weekStartSunday = weekStartSunday,
    periodsPerDay = periodsPerDay,
    showNowLine = showNowLine,
    rowHeight = rowHeight,
    cardTextScale = cardTextScale,
    sidebarWidth = sidebarWidth,
    headerHeight = headerHeight,
    hideLocation = hideLocation,
    hideTeacher = hideTeacher,
    centerTextHorizontally = centerTextHorizontally,
    centerTextVertically = centerTextVertically,
    cardFontSize = cardFontSize,
    cardBorderStyle = cardBorderStyle,
    cardCornerRadius = cardCornerRadius,
    cardInnerPadding = cardInnerPadding,
    cardOuterSpacing = cardOuterSpacing,
    cardOpacity = cardOpacity,
    cardColorIntensityPercent = colorIntensityPercent,
    colorIntensityAffectsCustom = colorIntensityAffectsCustom,
    backgroundImagePath = backgroundImagePath,
)

/** 把设备级显示配置合并到课表，同时保留该课表自己的学期字段。 */
fun ScheduleSettings.withDisplaySettings(display: ScheduleDisplaySettings) = copy(
    showWeekend = display.showWeekend,
    showNonWeek = display.showNonWeek,
    showOtherWeekInDetail = display.showOtherWeekInDetail,
    weekStartSunday = display.weekStartSunday,
    periodsPerDay = display.periodsPerDay,
    showNowLine = display.showNowLine,
    rowHeight = display.rowHeight,
    cardTextScale = display.cardTextScale,
    sidebarWidth = display.sidebarWidth,
    headerHeight = display.headerHeight,
    hideLocation = display.hideLocation,
    hideTeacher = display.hideTeacher,
    centerTextHorizontally = display.centerTextHorizontally,
    centerTextVertically = display.centerTextVertically,
    cardFontSize = display.cardFontSize,
    cardBorderStyle = display.cardBorderStyle,
    cardCornerRadius = display.cardCornerRadius,
    cardInnerPadding = display.cardInnerPadding,
    cardOuterSpacing = display.cardOuterSpacing,
    cardOpacity = display.cardOpacity,
    cardColorIntensity = display.cardColorIntensity,
    cardColorIntensityPercent = display.cardColorIntensityPercent,
    colorIntensityAffectsCustom = display.colorIntensityAffectsCustom,
    backgroundImagePath = display.backgroundImagePath,
)

/** 恢复“个性化”二级页中的选项；保留开学日期、总周数和二级页之外的课表行为设置。 */
fun ScheduleSettings.withDefaultPersonalization(): ScheduleSettings {
    val defaults = ScheduleSettings()
    return copy(
        showWeekend = defaults.showWeekend,
        showNonWeek = defaults.showNonWeek,
        rowHeight = defaults.rowHeight,
        cardTextScale = defaults.cardTextScale,
        sidebarWidth = defaults.sidebarWidth,
        headerHeight = defaults.headerHeight,
        hideLocation = defaults.hideLocation,
        hideTeacher = defaults.hideTeacher,
        centerTextHorizontally = defaults.centerTextHorizontally,
        centerTextVertically = defaults.centerTextVertically,
        cardFontSize = defaults.cardFontSize,
        cardBorderStyle = defaults.cardBorderStyle,
        cardCornerRadius = defaults.cardCornerRadius,
        cardInnerPadding = defaults.cardInnerPadding,
        cardOuterSpacing = defaults.cardOuterSpacing,
        cardOpacity = defaults.cardOpacity,
        cardColorIntensity = defaults.cardColorIntensity,
        cardColorIntensityPercent = defaults.cardColorIntensityPercent,
        colorIntensityAffectsCustom = defaults.colorIntensityAffectsCustom,
        backgroundImagePath = defaults.backgroundImagePath,
    )
}

/** 课表个性化滑条的统一取值范围，预览与正式课表共用。 */
object ScheduleAppearanceLimits {
    val ROW_HEIGHT = 40..100
    val SIDEBAR_WIDTH = 34..64
    val HEADER_HEIGHT = 36..72
    val CARD_FONT_SIZE = 8..16
    val TEXT_SCALE = 75..150
    val CORNER_RADIUS = 2..24
    val INNER_PADDING = 1..12
    val OUTER_SPACING = 1..8
    val OPACITY = 30..100
    val COLOR_INTENSITY = 0..200
    const val LEGACY_COLOR_BASELINE = 165
}

/**
 * 网格展示用的统一课程模型（教务课与自建课合并后）。
 * startNode/endNode 为“节”1-12；isThisWeek 表示该课在当前选中周是否上课（false 即“非本周”，灰显）。
 */
data class DisplayCourse(
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int,
    val startNode: Int,
    val endNode: Int,
    val weeks: List<Int>,
    val colorIndex: Int,
    val isCustom: Boolean,
    val customId: String? = null,
    val weeksLabel: String? = null,
    val isThisWeek: Boolean = true,
    val customColor: Long? = null, // 自建课自定义颜色(ARGB)；非空时优先
    val sourceId: String? = null, // 导入底本的稳定标识；编辑时用于替换而不是复制
)

/** 课表类型：教务学期课表（可重拉刷新） / 自定义课表（另存为，冻结、不被重拉覆盖）。 */
enum class ProfileKind { TERM, CUSTOM }

/** 教务课快照（落本地，离线展示 / 另存为底本）。 */
data class CachedCourse(
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,
    val sectionIndex: Int,
    val weeksRaw: String? = null,
    val colorIndex: Int = 0,
)

/**
 * 一份课表。TERM 绑定某个教务学期、底本来自 [AccountData.termCourses]；
 * CUSTOM 由“另存为”生成，底本冻结在 [frozenCourses]，不随重拉变化。
 * 两类都可叠加 [customCourses]（自建课）。[settings] 中的学期字段各自保存，显示字段由根级配置统一。
 */
data class ScheduleProfile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    val termValue: String? = null,
    val settings: ScheduleSettings = ScheduleSettings(),
    val customCourses: List<CustomCourse> = emptyList(),
    val frozenCourses: List<CachedCourse> = emptyList(),
    val hiddenSourceIds: Set<String>? = null, // 可空兼容旧缓存；本地替换/删除不修改学校原始数据
)

/** 课表备注（无固定时间格的实验/实习/集中实践课）。 */
data class Remark(
    val name: String,
    val teacher: String = "",
    val weeks: String = "",
    val clazz: String = "",
)

/** 单个教务学号名下的全部本地数据（课表、当前选中、教务课快照、备注、是否已扫描过有数据学期）。 */
data class AccountData(
    val classIdentity: ClassIdentity? = null,
    val classRevisions: Map<String, String>? = null,
    val activeProfileId: String? = null,
    val profiles: List<ScheduleProfile> = emptyList(),
    val termCourses: Map<String, List<CachedCourse>> = emptyMap(),
    val termRemarks: Map<String, List<Remark>>? = null, // 可空：兼容升级前缺该字段的旧本地数据
    val scanned: Boolean = false,
)

/**
 * 本地根：课表数据按「会员登录账号 + 教务学号」隔离；显示配置只与设备有关，跨全部账号共享。
 * [displaySettings] 可空用于兼容升级前的数据，首次打开有课表的页面时从当前课表无损迁移。
 */
data class ScheduleRoot(
    val accounts: Map<String, AccountData> = emptyMap(),
    val displaySettings: ScheduleDisplaySettings? = null,
)

/** 三种来源共用校历，保留手动日期和自建课表。 */
fun ScheduleRoot.withAcademicCalendar(dates: Map<String, String>): ScheduleRoot = copy(
    accounts = accounts.mapValues { (_, data) ->
        data.copy(profiles = data.profiles.map { profile ->
            val date = dates[profile.termValue]
            if (profile.kind != ProfileKind.TERM || profile.settings.semesterStartDateManual || date == null) profile
            else profile.copy(settings = profile.settings.copy(semesterStartDate = date))
        })
    },
)

/** 把全局显示配置同步进所有 profile，兼容仍直接读取 profile.settings 的后台/小组件代码。 */
fun ScheduleRoot.withDisplaySettings(display: ScheduleDisplaySettings): ScheduleRoot = copy(
    displaySettings = display,
    accounts = accounts.mapValues { (_, data) ->
        data.copy(
            profiles = data.profiles.map { profile ->
                profile.copy(settings = profile.settings.withDisplaySettings(display))
            },
        )
    },
)
