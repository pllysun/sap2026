package edu.csuft.sap.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WebScheduleParser 端上解析逻辑单测（用真实格式的强智课表页 HTML 片段）。 */
class WebScheduleParserTest {

    private val html = """
        <html><body>
        <select name="xnxq01id">
          <option value="2024-2025-1">2024-2025学年1学期</option>
          <option value="2025-2026-2" selected="selected">2025-2026学年2学期</option>
        </select>
        <table id="kbtable">
          <tr><th>节次/星期</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
          <tr>
            <th>第1,2节</th>
            <td><div class="kbcontent">高等数学[理论]<br><font title="老师">张三</font><br><font title="周次(节次)">1-16(周)</font><br><font title="教室">理1-101</font></div></td>
            <td><div class="kbcontent">&nbsp;</div></td>
            <td><div class="kbcontent">大学英语[理论]<br><font title="老师">李四</font><br><font title="周次(节次)">1-8,10-16(周)</font><br><font title="教室">外语楼202</font></div></td>
            <td></td><td></td><td></td><td></td>
          </tr>
          <tr>
            <th>第3,4节</th>
            <td></td><td></td><td></td><td></td>
            <td><div class="kbcontent">程序设计[实践]<br><font title="老师">王五</font><br><font title="周次(节次)">2-9(周)</font><br><font title="教室">机房A</font></div></td>
            <td></td><td></td>
          </tr>
          <tr><th>备注</th><td>军事理论 赵六 1-2周 计科2101;专业实习 周七 5-6周 ;</td></tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesTermCoursesAndRemarks() {
        val r = WebScheduleParser.parse(html)
        assertNotNull("应能解析出结果", r)
        r!!

        // 学期取选中项
        assertEquals("2025-2026-2", r.term)

        // 3 门有时间课程（空格子/无 font 的跳过）
        assertEquals(3, r.courses.size)

        val math = r.courses.first { it.name == "高等数学" }
        assertEquals("张三", math.teacher)
        assertEquals("理1-101", math.location)
        assertEquals("1-16(周)", math.weeksRaw)
        assertEquals(1, math.day)          // 周一
        assertEquals(1, math.sectionIndex) // 第1大节

        val eng = r.courses.first { it.name == "大学英语" }
        assertEquals(3, eng.day)           // 周三
        assertEquals("1-8,10-16(周)", eng.weeksRaw)

        val prog = r.courses.first { it.name == "程序设计" }
        assertEquals(5, prog.day)          // 周五
        assertEquals(2, prog.sectionIndex) // 第2大节
        assertEquals("王五", prog.teacher)

        // 备注按 ; 拆 2 条
        assertEquals(2, r.remarks.size)
        assertTrue(r.remarks.any { it.name == "军事理论" && it.weeks == "1-2周" })
    }

    @Test
    fun returnsNullWhenNoKbtable() {
        assertEquals(null, WebScheduleParser.parse("<html><body>not logged in</body></html>"))
    }
}
