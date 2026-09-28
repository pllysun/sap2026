package com.sap.jw.parser;

import com.sap.jw.vo.ScheduleVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleParserTest {

    @Test
    void supportsAndParsesLegacyKbtable() {
        String html = """
                <select name="xnxq01id"><option value="2026-2027-1" selected>2026-2027-1</option></select>
                <table id="kbtable"><tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
                <tr><th>第1节</th><td><div class="kbcontent">高数<br><font title="老师">张三</font><br><font title="周次(节次)">1-8周</font><br><font title="教室">A101</font></div></td></tr></table>
                """;
        ScheduleParser parser = new ScheduleParser();
        assertTrue(parser.supports(html));
        ScheduleVO result = parser.parse(html);
        assertEquals("2026-2027-1", result.getTerm());
        assertEquals(1, result.getCourses().size());
        assertEquals("张三", result.getCourses().get(0).getTeacher());
    }

    @Test
    void supportsNewClassTableAndDirectFontCells() {
        String html = """
                <select name="xnxq01id"><option value="2026-2027-1" selected>2026-2027-1</option></select>
                <table class="schedule-kbtable"><tr><td>周一</td><td>周二</td><td>周三</td><td>周四</td><td>周五</td></tr>
                <tr><th>第1节</th><td><font title="教师">李四</font><br><font title="周次">1-8周</font><br><font title="地点">B202</font></td></tr></table>
                """;
        ScheduleParser parser = new ScheduleParser();
        assertTrue(parser.supports(html));
        ScheduleVO result = parser.parse(html);
        assertEquals(1, result.getCourses().size());
        assertEquals("李四", result.getCourses().get(0).getTeacher());
        assertEquals("B202", result.getCourses().get(0).getRoom());
    }

    @Test
    void treatsUnpublishedNewScheduleAsEmptyInsteadOfFormatError() {
        ScheduleParser parser = new ScheduleParser();
        String alert = "<script>alert('课表暂未公布，不能查看课表！');</script>";
        assertTrue(parser.supports(alert));
        assertTrue(parser.parse(alert).getCourses().isEmpty());
    }

    @Test
    void parsesLayuiScheduleRows() {
        ScheduleParser parser = new ScheduleParser();
        String json = "{\"code\":0,\"count\":1,\"data\":[{\"kcmc\":\"数据库原理\",\"jsxm\":\"张三\",\"jxcd\":\"A101\",\"zc\":\"1-8周\",\"skxq\":\"星期二\",\"jcdm\":\"第3,4节\"}]}";
        assertTrue(parser.supports(json));
        ScheduleVO result = parser.parse(json);
        assertEquals(1, result.getCourses().size());
        assertEquals(2, result.getCourses().get(0).getDay());
        assertEquals("张三", result.getCourses().get(0).getTeacher());
        assertEquals("第3,4节", result.getCourses().get(0).getSection());
        assertEquals(2, result.getCourses().get(0).getSectionIndex());
    }

    @Test
    void parsesLayuiRowsAndAlternateFieldNames() {
        ScheduleParser parser = new ScheduleParser();
        String json = "{\"code\":0,\"count\":1,\"data\":[{\"kcmc\":\"操作系统\",\"jsxmzc\":\"李老师\",\"skdd\":\"南教A101\",\"skzc\":\"2-16周\",\"xqmc\":\"周四\",\"sksj\":\"第5,6节\"}]}";
        assertTrue(parser.supports(json));
        ScheduleVO result = parser.parse(json);
        assertEquals(1, result.getCourses().size());
        assertEquals(4, result.getCourses().get(0).getDay());
        assertEquals("李老师", result.getCourses().get(0).getTeacher());
        assertEquals("南教A101", result.getCourses().get(0).getRoom());
        assertEquals(3, result.getCourses().get(0).getSectionIndex());
    }

    @Test
    void ignoresJsonListsWithoutScheduleFields() {
        ScheduleParser parser = new ScheduleParser();
        String json = "{\"code\":0,\"count\":1,\"data\":[{\"kcmc\":\"公告标题\",\"author\":\"教务处\"}]}";
        assertFalse(parser.supports(json));
        assertTrue(parser.parse(json).getCourses().isEmpty());
    }

    @Test
    void parsesCurrentQzWeeklyTableLayout() {
        ScheduleParser parser = new ScheduleParser();
        String html = """
                <select name="xnxq01id"><option value="2025-2026-2" selected>2025-2026-2</option></select>
                <table class="qz-weeklyTable"><thead><tr>
                  <th class="qz-weeklyTable-label">周次</th><th>星期一</th><th>星期二</th><th>星期三</th>
                  <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th>
                </tr></thead><tbody><tr>
                  <td class="qz-weeklyTable-label"><div class="index-title">第1，2节</div></td>
                  <td></td><td></td><td></td><td></td><td></td><td></td>
                  <td class="qz-hasCourse"><ul class="courselists"><li class="courselists-item qz-hasCourse-1">
                    <div class="qz-hasCourse-title">新版课程</div>
                    <p class="qz-hasCourse-detaillists"><span>老师:王老师;时间:1-8周[1-2节];地点:北教A101</span></p>
                  </li></ul></td>
                </tr></tbody></table>
                """;
        assertTrue(parser.supports(html));
        ScheduleVO result = parser.parse(html);
        assertEquals(1, result.getCourses().size());
        assertEquals(7, result.getCourses().get(0).getDay());
        assertEquals("星期日", result.getCourses().get(0).getDayName());
        assertEquals("王老师", result.getCourses().get(0).getTeacher());
        assertEquals("1-8周", result.getCourses().get(0).getWeeks());
        assertEquals("北教A101", result.getCourses().get(0).getRoom());
    }
}
