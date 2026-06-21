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
 * 端上解析强智课表页(#kbtable)——与后端 ScheduleParser 同源逻辑，移植到 App 用 jsoup 解析。
 * 单元格 div.kbcontent 内：课程名[学时类型]<br><font title=老师>…<font title=周次>…<font title=教室>…，
 * 多门课用一串短横线分隔；含“备注”表头的行是无固定时间格的集中实践课。
 */
object WebScheduleParser {

    private val TYPE = Regex("\\[(.*?)]")
    private val BR = Regex("(?i)<br\\s*/?>")
    private val DIVIDER = Regex("(?i)(?:<br\\s*/?>|\\s)*[-—]{4,}(?:<br\\s*/?>|\\s)*")
    private val WEEKS = Regex("\\d+(?:[\\-,]\\d+)*周")
    private val LEAD_BR = Regex("(?i)^(?:<br\\s*/?>|\\s)+")

    /** 解析整页 HTML；拿不到 #kbtable 返回 null（通常意味着没登录/页面不对）。 */
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

        val table = doc.selectFirst("#kbtable") ?: return null
        val courses = ArrayList<CachedCourse>()
        val remarks = ArrayList<Remark>()

        var sectionIndex = 0
        for (row in table.select("tr")) {
            val th = row.selectFirst("th")
            if (th != null && th.text().contains("备注")) {
                parseRemarks(row, remarks)
                continue
            }
            val dayCells = row.select("td")
            if (dayCells.isEmpty()) continue // 表头行
            sectionIndex++
            for (i in 0 until minOf(dayCells.size, 7)) {
                val td = dayCells[i]
                for (div in td.select("div.kbcontent")) {
                    val inner = div.html()
                    if (!inner.contains("<font")) continue
                    for (seg in DIVIDER.split(inner)) {
                        if (!seg.contains("<font")) continue
                        val c = parseSegment(seg, i + 1, sectionIndex) ?: continue
                        if (c.name.isBlank()) continue
                        courses.add(c)
                    }
                }
            }
        }
        return WebScheduleResult(term, courses, remarks)
    }

    private fun parseSegment(seg: String, day: Int, sectionIndex: Int): CachedCourse? {
        val cleaned = seg.replaceFirst(LEAD_BR, "")
        val namePart = BR.split(cleaned, 2).firstOrNull() ?: return null
        var name = clean(Jsoup.parse(namePart).text())
        TYPE.find(name)?.let { name = name.substring(0, it.range.first).trim() }

        val f = Jsoup.parseBodyFragment(cleaned)
        val teacher = textOf(f.selectFirst("font[title=老师]"))
        val weeks = textOf(f.selectFirst("font[title^=周次]"))
        val room = textOf(f.selectFirst("font[title=教室]"))
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

    private fun textOf(e: Element?): String? = e?.let { clean(it.text()) }

    private fun clean(s: String?): String =
        s?.replace(" ", "")?.replace("&nbsp;", "")?.trim() ?: ""
}
