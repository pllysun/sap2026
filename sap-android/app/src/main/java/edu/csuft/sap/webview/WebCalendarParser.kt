package edu.csuft.sap.webview

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate

/**
 * 端上解析强智「教学周历查看」(jxzl_query) → 第 1 周周一(开学日, ISO)。与后端 CalendarParser 同源逻辑。
 * 表格每行：周次 + 周一..周日(+备注)；周末列稳定带 "MM月DD日" 全日期，据此(周次,星期)反推第 1 周周一。
 */
object WebCalendarParser {

    private val FULL_DATE = Regex("(\\d{1,2})月(\\d{1,2})日")
    private val TERM = Regex("(\\d{4})-(\\d{4})-(\\d)")
    private val WEEK_NO = Regex("^\\s*(\\d{1,2})\\s*$")

    /** @return 第 1 周周一 ISO 日期(如 "2026-03-09")；解析不出/学期对不上返回 null。 */
    fun parseSemesterStart(html: String?, term: String?): String? {
        if (html.isNullOrBlank()) return null
        val doc = Jsoup.parse(html)

        val pageTerm = detectTerm(doc)
        // 指定了期望学期但页面切的是别的学期(参数被忽略) → 放弃，避免用错年份
        if (term != null && TERM.containsMatchIn(term) && pageTerm != null && pageTerm.trim() != term.trim()) return null
        val useTerm = if (term != null && TERM.containsMatchIn(term)) term else (pageTerm ?: return null)
        val tm = TERM.find(useTerm) ?: return null
        val y1 = tm.groupValues[1].toInt()
        val y2 = tm.groupValues[2].toInt()

        val table = findTable(doc) ?: return null
        for (row in table.select("tr")) {
            val tds = row.select("td")
            if (tds.size < 8) continue                          // 周次 + 7 天(+备注)
            val wm = WEEK_NO.matchEntire(tds[0].text()) ?: continue
            val week = wm.groupValues[1].toInt()
            for (col in 1..minOf(7, tds.size - 1)) {            // col 1=周一 … 7=周日
                val dm = FULL_DATE.find(tds[col].text()) ?: continue
                val month = dm.groupValues[1].toInt()
                val day = dm.groupValues[2].toInt()
                val year = if (month >= 8) y1 else y2           // 8月起=学年首年；否则次年(春季/跨年1月)
                return try {
                    LocalDate.of(year, month, day)
                        .minusDays(((week - 1) * 7 + (col - 1)).toLong())
                        .toString()
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun findTable(doc: Document): Element? {
        for (t in doc.select("table")) {
            val txt = t.text()
            if (txt.contains("周次") || (txt.contains("星期一") && txt.contains("星期日"))) return t
        }
        return doc.selectFirst("table")
    }

    private fun detectTerm(doc: Document): String? {
        for (op in doc.select("option[selected]")) {
            val v = op.attr("value")
            if (TERM.containsMatchIn(v)) return v.trim()
        }
        for (op in doc.select("select option")) {
            val v = op.attr("value").trim()
            if (TERM.matches(v)) return v
        }
        return null
    }
}
