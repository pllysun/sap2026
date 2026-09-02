package edu.csuft.sap.webview

import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.data.schedule.Remark
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** WebView 抓到的课表页(xskb_list.do)解析结果。 */
data class WebScheduleResult(
    val term: String?,
    val courses: List<CachedCourse>,
    val remarks: List<Remark>,
)

/**
 * 端上解析强智课表页（旧版 #kbtable / 新版 qz-weeklyTable）——与后端 ScheduleParser 同源逻辑，移植到 App 用 jsoup 解析。
 * 单元格 div.kbcontent 内：课程名[学时类型]<br><font title=老师>…<font title=周次>…<font title=教室>…，
 * 多门课用一串短横线分隔；含“备注”表头的行是无固定时间格的集中实践课。
 */
object WebScheduleParser {

    private val TYPE = Regex("\\[(.*?)]")
    private val BR = Regex("(?i)<br\\s*/?>")
    private val DIVIDER = Regex("(?i)(?:<br\\s*/?>|\\s)*[-—]{4,}(?:<br\\s*/?>|\\s)*")
    private val WEEKS = Regex("\\d+(?:[\\-,]\\d+)*周")
    private val LEAD_BR = Regex("(?i)^(?:<br\\s*/?>|\\s)+")

    /** 解析整页 HTML；拿不到课表表格返回 null（通常意味着没登录/页面不对）。 */
    fun parse(html: String?): WebScheduleResult? {
        if (html.isNullOrBlank()) return null
        val doc = Jsoup.parse(html)

        // 当前学期
        var term: String? = null
        for (op in doc.select("select[name=xnxq01id] option")) {
            val v = op.attr("value").trim()
            if (v.isEmpty()) continue
            if (op.hasAttr("selected")) term = v
        }
        if (term == null) {
            term = doc.selectFirst("select[name=xnxq01id] option[value~=\\d{4}-\\d{4}-\\d]")
                ?.attr("value")?.trim()
        }

        val table = findScheduleTable(doc)
        if (table == null) {
            // 新版强智在课表尚未发布时返回 alert 脚本；这是合法的空课表。
            val lower = html.lowercase()
            if (lower.contains("课表暂未公布") || lower.contains("没有符合条件的数据")) {
                return WebScheduleResult(term, emptyList(), emptyList())
            }
            return null
        }
        val courses = ArrayList<CachedCourse>()
        val remarks = ArrayList<Remark>()

        var sectionIndex = 0
        for (row in table.select("tr")) {
            val th = row.selectFirst("th")
            if (th != null && th.text().contains("备注")) {
                parseRemarks(row, remarks)
                continue
            }
            if (isWeekdayHeaderRow(row)) continue
            val dayCells = row.select("td")
            if (dayCells.isEmpty()) continue // 表头行

            // 2026 新版 qz-weeklyTable 的第一格是节次标签，后面七格才是
            // 周一至周日；“午休”等行没有可用节次，不能按物理行号计数。
            val labelCell = dayCells.firstOrNull()
            val modernLabelCell = labelCell?.classNames()?.any {
                it.contains("weeklyTable-label", ignoreCase = true)
            } == true
            if (modernLabelCell) {
                val section = clean(labelCell?.select(".index-title")?.text())
                    .ifBlank { clean(labelCell?.text()) }
                val modernSectionIndex = parseSectionIndex(section).takeIf { it > 0 } ?: sectionIndex
                for (i in 1 until minOf(dayCells.size, 8)) {
                    val td = dayCells[i]
                    val items = td.select(".courselists-item")
                    if (items.isNotEmpty()) {
                        for (item in items) {
                            val c = parseModernItem(item) ?: continue
                            if (c.name.isBlank()) continue
                            courses.add(c.copy(day = i, sectionIndex = modernSectionIndex))
                        }
                    } else {
                        // 个别主题仍把旧版 font 直接放在新版单元格中。
                        parseLegacyCell(td, i, modernSectionIndex, courses)
                    }
                }
                continue
            }

            sectionIndex++
            for (i in 0 until minOf(dayCells.size, 7)) {
                val td = dayCells[i]
                parseLegacyCell(td, i + 1, sectionIndex, courses)
            }
        }
        return WebScheduleResult(term, courses, remarks)
    }

    private fun parseLegacyCell(
        td: Element,
        day: Int,
        section: Int,
        out: MutableList<CachedCourse>,
    ) {
        val blocks = td.select("div.kbcontent, [data-kbcontent]").let { selected ->
            if (selected.isNotEmpty()) selected
            else if (td.select("font").isNotEmpty()) org.jsoup.select.Elements(td)
            else selected
        }
        for (div in blocks) {
            val inner = div.html()
            if (!inner.lowercase().contains("<font")) continue
            for (seg in DIVIDER.split(inner)) {
                if (!seg.lowercase().contains("<font")) continue
                val c = parseSegment(seg, day, section) ?: continue
                if (c.name.isNotBlank()) out.add(c)
            }
        }
    }

    /** 解析新版 qz-weeklyTable 的单门课程块。 */
    private fun parseModernItem(item: Element): CachedCourse? {
        var name = clean(item.select(".qz-hasCourse-title, .course-title, [class*=title]").text())
        if (name.isBlank()) {
            val text = clean(item.text())
            name = text.substringBefore('\n').trim().ifBlank { text }
        }
        TYPE.find(name)?.let { name = name.substring(0, it.range.first).trim() }
        if (name.isBlank()) return null

        val details = normalize(item.select(".qz-hasCourse-detaillists, .course-detail, [class*=detail]").text())
            .ifBlank { normalize(item.text()) }
        val teacher = labeled(details, "教师", "老师", "任课教师")
        val weeks = labeled(details, "周次", "上课周次")
            ?: Regex("\\d+(?:[\\-,，、]\\d+)*周").find(details)?.value
        val room = labeled(details, "地点", "教室", "上课地点")
        return CachedCourse(
            name = name,
            teacher = teacher ?: "",
            location = room ?: "",
            day = 0,
            sectionIndex = 0,
            weeksRaw = weeks,
            colorIndex = 0,
        )
    }

    private fun labeled(text: String, vararg labels: String): String? {
        if (text.isBlank()) return null
        for (label in labels) {
            val regex = Regex("(?:^|[;；\\s])${Regex.escape(label)}\\s*[：:]\\s*([^;；]+)")
            val match = regex.find(text) ?: continue
            return clean(match.groupValues[1])
        }
        return null
    }

    private fun parseSectionIndex(value: String?): Int {
        val first = Regex("\\d+").find(value ?: "")?.value?.toIntOrNull() ?: return 0
        return if (value.orEmpty().contains(Regex("[,，、~～\\-－]"))) (first + 1) / 2 else first
    }

    private fun parseSegment(seg: String, day: Int, sectionIndex: Int): CachedCourse? {
        val cleaned = seg.replaceFirst(LEAD_BR, "")
        val namePart = BR.split(cleaned, 2).firstOrNull() ?: return null
        var name = clean(Jsoup.parse(namePart).text())
        TYPE.find(name)?.let { name = name.substring(0, it.range.first).trim() }

        val f = Jsoup.parseBodyFragment(cleaned)
        val teacher = textOf(firstFont(f, "老师", "教师"))
        val weeks = textOf(firstFont(f, "周次"))
        val room = textOf(firstFont(f, "教室", "地点"))
        return CachedCourse(
            name = name,
            teacher = teacher ?: "",
            location = room ?: "",
            day = day,
            sectionIndex = sectionIndex,
            weeksRaw = weeks,
            colorIndex = 0,
        )
    }

    private fun parseRemarks(row: Element, out: MutableList<Remark>) {
        val td = row.selectFirst("td") ?: return
        val full = td.text()
        if (full.isBlank()) return
        for (part in full.split(Regex("[;；]"))) {
            val e = part.trim()
            if (e.isNotEmpty()) out.add(parseRemark(e))
        }
    }

    private fun parseRemark(e: String): Remark {
        val m = WEEKS.find(e)
        if (m != null) {
            val weeks = m.value
            val before = e.substring(0, m.range.first).trim()
            val after = e.substring(m.range.last + 1).trim()
            val toks = before.split(Regex("\\s+"))
            return if (toks.size >= 2) {
                Remark(
                    name = toks.dropLast(1).joinToString(" ").trim(),
                    teacher = dedupTeacher(toks.last()),
                    weeks = weeks,
                    clazz = after,
                )
            } else {
                Remark(name = before, weeks = weeks, clazz = after)
            }
        }
        // 无周次（如军训等集中实践）：仍尝试拆“课程名 + 教师列表”，并对强智把同一老师重复几十次的脏数据去重，
        // 避免课程名和人名糊在一起。
        val toks = e.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tIdx = toks.indexOfFirst { it.contains(',') || it.contains('，') || it.contains('、') }
        if (tIdx >= 1) {
            return Remark(
                name = toks.subList(0, tIdx).joinToString(" "),
                teacher = dedupTeacher(toks[tIdx]),
                clazz = toks.drop(tIdx + 1).joinToString(" "),
            )
        }
        return Remark(name = e)
    }

    private fun dedupTeacher(teacher: String): String {
        val names = teacher.split(Regex("[,，、]"))
        if (names.size <= 1) return teacher
        return names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
    }

    private fun findScheduleTable(doc: org.jsoup.nodes.Document): Element? {
        doc.getElementById("kbtable")?.let { return it }
        doc.selectFirst("table.qz-weeklyTable")?.let { return it }
        for (table in doc.select("table")) {
            val id = table.id()
            if (id.equals("kbtable", ignoreCase = true) ||
                table.classNames().any { it.equals("kbtable", ignoreCase = true) }) return table
        }
        for (table in doc.select("table")) {
            val hasBlock = table.select("div.kbcontent, [data-kbcontent]").any {
                it.hasAttr("data-kbcontent") || it.classNames().any { c -> c.contains("kbcontent", ignoreCase = true) }
            }
            if (hasBlock) return table
            val hasTeacher = table.select("td font[title]").any {
                val title = it.attr("title")
                title.contains("老师") || title.contains("教师")
            }
            if (hasTeacher && table.select("tr").size >= 2) return table
            if (doc.select("select[name=xnxq01id] option").isNotEmpty() &&
                table.select("tr").size >= 2 && table.select("td").size >= 5) return table
        }
        return null
    }

    private fun firstFont(fragment: org.jsoup.nodes.Document, vararg parts: String): Element? =
        fragment.select("font[title]").firstOrNull { font ->
            parts.any { part -> font.attr("title").contains(part) }
        }

    private fun isWeekdayHeaderRow(row: Element): Boolean {
        if (row.select("font").isNotEmpty()) return false
        val text = clean(row.text())
        if (text.isBlank()) return false
        val hits = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "周一", "周二", "周三", "周四", "周五", "周六", "周日").count { text.contains(it) }
        return hits >= 3
    }

    private fun textOf(e: Element?): String? = e?.let { clean(it.text()) }

    private fun clean(s: String?): String =
        s?.replace(" ", "")?.replace("&nbsp;", "")?.trim() ?: ""

    /** 新版明细需要保留字段之间的空白/分隔符，不能复用旧版的紧凑清洗。 */
    private fun normalize(s: String?): String =
        s?.replace('\u00a0', ' ')?.replace(Regex("\\s+"), " ")?.trim() ?: ""
}
