package edu.csuft.sap.data.schedule

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 周次解析与当前周计算。 */
object WeekUtil {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 解析教务周次串，如 "1-8,10-11(周)" / "1-16(单)" / "2-16(双)" → 周次列表。 */
    fun parseWeeks(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val parity = when {
            raw.contains("单") -> 1
            raw.contains("双") -> 0
            else -> -1
        }
        val body = raw.substringBefore('(').replace("周", "").trim()
        val result = sortedSetOf<Int>()
        for (part in body.split(',', '，')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val range = p.split('-', '~')
            try {
                if (range.size == 2) {
                    val a = range[0].trim().toInt()
                    val b = range[1].trim().toInt()
                    for (w in a..b) if (parity == -1 || w % 2 == parity) result.add(w)
                } else {
                    result.add(p.toInt())
                }
            } catch (_: NumberFormatException) { /* 忽略无法解析的片段 */ }
        }
        return result.toList()
    }

    /** 根据开学日期算今天是第几周（1 起）；无开学日期返回 null。 */
    fun currentWeek(semesterStartIso: String?, today: LocalDate = LocalDate.now()): Int? {
        val start = parseDate(semesterStartIso) ?: return null
        val days = ChronoUnit.DAYS.between(start, today)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    /** 某一周的周一到周日日期；无开学日期返回 null。 */
    fun datesOfWeek(semesterStartIso: String?, week: Int): List<LocalDate>? {
        val start = parseDate(semesterStartIso) ?: return null
        val monday = start.plusWeeks((week - 1).toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun parseDate(iso: String?): LocalDate? =
        if (iso.isNullOrBlank()) null else try {
            LocalDate.parse(iso, ISO)
        } catch (_: Exception) {
            null
        }

    fun format(date: LocalDate): String = date.format(ISO)

    /** 周次列表 → 紧凑标签，如 [1,2,3,5,6] → "1-3,5-6周"。 */
    fun formatWeeks(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val s = weeks.toSortedSet().toList()
        val parts = ArrayList<String>()
        var start = s[0]
        var prev = s[0]
        for (i in 1 until s.size) {
            if (s[i] == prev + 1) {
                prev = s[i]
            } else {
                parts.add(if (start == prev) "$start" else "$start-$prev")
                start = s[i]; prev = s[i]
            }
        }
        parts.add(if (start == prev) "$start" else "$start-$prev")
        return parts.joinToString(",") + "周"
    }
}
