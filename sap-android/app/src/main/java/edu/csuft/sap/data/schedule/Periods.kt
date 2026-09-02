package edu.csuft.sap.data.schedule

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 单节课的时间。node 为节号（1 起）。 */
data class Period(val node: Int, val start: String, val end: String)

/**
 * 节次时间表（WakeUp 风格：课表按“节”排，每节一行）。
 * 教务“大节”sectionIndex(1-6) 映射为节区间 [2N-1, 2N]。
 * 时间可由用户自定义（[load]/[save] 落 prefs）；[period]/[COUNT] 读当前生效表，故各调用点无需改动。
 */
object Periods {

    /**
     * 默认 10 节时间（学校作息）：每节 45 分钟。
     * 上午 1-4 节(08:00 起)、下午 5-8 节(14:00 起)、晚上 9-10 节(19:00 起)；午休在第 4 节后、晚饭在第 8 节后。
     */
    val DEFAULT: List<Period> = listOf(
        Period(1, "08:00", "08:45"),
        Period(2, "08:55", "09:40"),
        Period(3, "10:00", "10:45"),
        Period(4, "10:55", "11:40"),
        Period(5, "14:00", "14:45"),
        Period(6, "14:55", "15:40"),
        Period(7, "16:00", "16:45"),
        Period(8, "16:55", "17:40"),
        Period(9, "19:00", "19:45"),
        Period(10, "19:55", "20:40"),
    )

    /** 默认延伸到 16 节（晚上继续往后排）：当「一天的总课时数」>10 时补齐缺失节次时间，保证 [period] 不返回 null。 */
    val FULL_TEMPLATE: List<Period> = DEFAULT + listOf(
        Period(11, "20:50", "21:35"),
        Period(12, "21:45", "22:30"),
        Period(13, "22:40", "23:25"),
        Period(14, "23:35", "00:20"),
        Period(15, "00:30", "01:15"),
        Period(16, "01:25", "02:10"),
    )

    /** 一天最多节数（与 ScheduleSettings.periodsPerDay 上限一致）。 */
    const val MAX_NODES = 16

    /** 当前生效的节次时间表（默认 = [DEFAULT]，[load] 后可为用户自定义；长度随用户设置 8..16 可变）。 */
    @Volatile
    var current: List<Period> = DEFAULT
        private set

    /**
     * Compose 可观察的变更版本号：[save]/[resetDefault] 后自增。课表网格时间列等 UI 读它即订阅，
     * 节次时间一改就自动重组、无需重进 App（[current] 本身是普通 var，Compose 不追踪其变化）。
     */
    var revision by mutableIntStateOf(0)
        private set

    /** 总节数。 */
    val COUNT: Int get() = current.size

    /** 上午/下午分隔（“午休”）在第几节之后。 */
    const val LUNCH_AFTER_NODE = 4

    /** 下午/晚上分隔在第几节之后。 */
    const val EVENING_AFTER_NODE = 8

    /** 某节的时间：优先用户自定义，缺失（如节号超出已存表）回退默认模板，避免返回 null。 */
    fun period(node: Int): Period? =
        current.firstOrNull { it.node == node } ?: FULL_TEMPLATE.firstOrNull { it.node == node }

    /** 取 [count] 节的时间表：已自定义的用自定义、超出部分用默认模板补齐（供「课表时间设置」按当前总课时数编辑对应行数）。 */
    fun tableFor(count: Int): List<Period> {
        val n = count.coerceIn(1, MAX_NODES)
        return (1..n).map { node -> period(node)!! } // period 带兜底，1..16 必有值
    }

    /** [count] 节的默认时间表（编辑页「恢复默认」用）。 */
    fun defaultTableFor(count: Int): List<Period> = FULL_TEMPLATE.take(count.coerceIn(1, MAX_NODES))

    /** 教务大节 sectionIndex(1 起) → 节区间。 */
    fun nodesOfSection(sectionIndex: Int): IntRange {
        val s = sectionIndex.coerceAtLeast(1)
        return (2 * s - 1)..(2 * s)
    }

    // ---- 自定义时间持久化（App 与小组件进程都应在用前调用 load） ----

    private const val PREFS = "sap_periods"
    private const val KEY = "times"

    fun load(context: Context) {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current = parse(s) ?: DEFAULT
    }

    /** 保存自定义时间（节号按下标 1.. 重新归一）。 */
    fun save(context: Context, list: List<Period>) {
        val normalized = list.mapIndexed { i, p -> p.copy(node = i + 1) }.ifEmpty { DEFAULT }
        current = normalized
        revision++ // 通知 Compose 重组（时间列等随即刷新）
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, normalized.joinToString(",") { "${it.start}-${it.end}" }).apply()
    }

    fun resetDefault(context: Context) {
        current = DEFAULT
        revision++ // 通知 Compose 重组
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun parse(s: String?): List<Period>? {
        if (s.isNullOrBlank()) return null
        return try {
            s.split(",").mapIndexed { i, seg ->
                val (a, b) = seg.split("-")
                Period(i + 1, a.trim(), b.trim())
            }.takeIf { it.isNotEmpty() } // 长度可变（8..16），不再死锁 12
        } catch (_: Exception) {
            null
        }
    }
}
