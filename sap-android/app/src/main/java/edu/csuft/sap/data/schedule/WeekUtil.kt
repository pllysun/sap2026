package edu.csuft.sap.data.schedule

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 周次解析与当前周计算。 */
object WeekUtil {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private const val MAX_WEEK = 60
    private fun normalizedWeeks(raw: String): String = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
        .replace(Regex("[\\[【(][^\\]】)]*节[^\\]】)]*[\\]】)]"), "")
        .replace(Regex("[—–~至到]"), "-").replace(Regex("\\s+"), "")

    /** 空周次才表示每周；有内容但无法识别时不能误当作每周上课。 */
    fun isUnrestricted(raw: String?): Boolean = raw.isNullOrBlank() || normalizedWeeks(raw) in setOf("每周", "全周", "全部周")

    /** 兼容全角括号、单双周前后缀、顿号、分段单双周和附带节次的教务格式。 */
    fun parseWeeks(raw: String?): List<Int> {
        if (raw.isNullOrBlank() || raw.length > 4096) return emptyList()
        val text = normalizedWeeks(raw)
        val parities = text.filter { it == '单' || it == '双' }.toSet()
        val parts = text.split(',', '、', ';', '；')
        // 列表整体的前/后缀适用于所有裸数字分段；带“周”的完整分段各自保留限定。
        val prefix = text.takeWhile { !it.isDigit() }.any { it == '单' || it == '双' }
        val suffix = parts.last().any { it == '单' || it == '双' }
            && parts.dropLast(1).none { it.any { ch -> ch in "周单双()[]【】" } }
        val sharedParity = parities.singleOrNull().takeIf { prefix || suffix }
        val result = sortedSetOf<Int>()
        for (part in parts) {
            val parity = part.filter { it == '单' || it == '双' }.toSet().singleOrNull() ?: sharedParity
            val body = part.replace(Regex("[第周单双数逢()\\[\\]【】]"), "")
            val range = if (body.isEmpty() && parity != null) 1..MAX_WEEK else {
                if (!Regex("\\d{1,2}(?:-\\d{1,2})?").matches(body)) continue
                val bounds = body.split('-').map { it.toInt() }
                val a = bounds.first(); val b = bounds.last()
                if (a !in 1..MAX_WEEK || b !in a..MAX_WEEK) continue
                a..b
            }
            result += range.filter { parity == null || it % 2 == if (parity == '单') 1 else 0 }
        }
        return result.toList()
    }

    /** 根据开学日期算指定日期是第几周（1 起）；开学日前或无开学日期返回 null。 */
    fun currentWeek(semesterStartIso: String?, today: LocalDate = LocalDate.now()): Int? {
        val start = parseDate(semesterStartIso) ?: return null
        val days = ChronoUnit.DAYS.between(start, today)
        // 不能把开学日前的日期压到第 1 周：否则开学后的周次会提前
        // 命中同一个星期几，进而造成课表高亮和上课提醒提前触发。
        if (days < 0) return null
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
