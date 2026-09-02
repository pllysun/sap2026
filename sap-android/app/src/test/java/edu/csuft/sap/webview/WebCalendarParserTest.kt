package edu.csuft.sap.webview

import org.junit.Assert.assertEquals
import org.junit.Test

class WebCalendarParserTest {

    @Test
    fun parsesCurrentCalendarWeekLabelsAndDates() {
        val html = """
            <select name="xnxq01id"><option value="2025-2026-2" selected>2025-2026-2</option></select>
            <table class="qz-calendar">
              <tr><th>周次</th><th>星期一</th><th>星期二</th><th>星期三</th>
                <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th><th>备注</th></tr>
              <tr><td>第1周</td><td>03月09日</td><td>03月10日</td><td>03月11日</td>
                <td>03月12日</td><td>03月13日</td><td>03月14日</td><td>03月15日</td><td></td></tr>
            </table>
        """.trimIndent()

        assertEquals("2026-03-09", WebCalendarParser.parseSemesterStart(html, "2025-2026-2"))
    }
}
