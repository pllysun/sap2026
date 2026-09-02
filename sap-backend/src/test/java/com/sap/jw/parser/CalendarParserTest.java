package com.sap.jw.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarParserTest {

    @Test
    void parsesCurrentCalendarWeekLabelsAndDates() {
        String html = """
                <select name="xnxq01id">
                  <option value="2025-2026-2" selected>2025-2026-2</option>
                </select>
                <table class="qz-calendar">
                  <tr><th>周次</th><th>星期一</th><th>星期二</th><th>星期三</th>
                      <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th><th>备注</th></tr>
                  <tr><td>第1周</td><td>03月09日</td><td>03月10日</td><td>03月11日</td>
                      <td>03月12日</td><td>03月13日</td><td>03月14日</td><td>03月15日</td><td></td></tr>
                </table>
                """;

        assertEquals("2026-03-09", new CalendarParser().parseSemesterStart(html, "2025-2026-2"));
    }

    @Test
    void acceptsPlainWeekNumberAndSlashDate() {
        String html = """
                <select name="xnxq01id"><option value="2025-2026-1" selected>2025-2026-1</option></select>
                <table><tr><th>周次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th>
                  <th>星期五</th><th>星期六</th><th>星期日</th></tr>
                  <tr><td>1</td><td>09/01</td><td>09/02</td><td>09/03</td><td>09/04</td>
                    <td>09/05</td><td>09/06</td><td>09/07</td></tr></table>
                """;

        assertEquals("2025-09-01", new CalendarParser().parseSemesterStart(html, "2025-2026-1"));
    }
}
