package com.sap.jw.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.sap.jw.vo.GradeVO;
import org.junit.jupiter.api.Test;

class GradeParserTest {

    private final GradeParser parser = new GradeParser();

    @Test
    void keepsLegacyDataListColumnOrder() {
        String html = "<table id='dataList'><tr><td>1</td><td>2025-2026-2</td><td>CS001</td>"
                + "<td>数据结构</td><td>92</td><td>3</td><td>48</td><td>4.2</td><td></td>"
                + "<td>考试</td><td>正常考试</td><td>必修</td><td>专业课</td></tr></table>";

        assertTrue(parser.supports(html));
        List<com.sap.jw.vo.GradeVO> grades = parser.parse(html);
        assertEquals(1, grades.size());
        assertEquals("数据结构", grades.getFirst().getCourseName());
        assertEquals("92", grades.getFirst().getScore());
    }

    @Test
    void mapsNewTableByHeaderInsteadOfColumnPosition() {
        String html = "<table class='qz-table'><tr><th>课程名称</th><th>成绩</th><th>学年学期</th>"
                + "<th>学分</th><th>课程代码</th><th>绩点</th></tr><tr><td>大学英语</td><td>88</td>"
                + "<td>2026-2027-1</td><td>2</td><td>EN001</td><td>3.8</td></tr></table>";

        assertTrue(parser.supports(html));
        List<com.sap.jw.vo.GradeVO> grades = parser.parse(html);
        assertEquals(1, grades.size());
        assertEquals("大学英语", grades.getFirst().getCourseName());
        assertEquals("88", grades.getFirst().getScore());
        assertEquals("2026-2027-1", grades.getFirst().getTerm());
        assertEquals("EN001", grades.getFirst().getCourseNo());
    }

    @Test
    void mapsCurrentCourseGradeColumnsAndCheckboxCell() {
        String html = """
                <select name="xnxq01id"><option value="2025-2026-1" selected>2025-2026-1</option></select>
                <table id="dataList"><thead><tr>
                  <th></th><th>序号</th><th>开课时间</th><th>课程编号</th><th>课程名称</th>
                  <th>开课单位</th><th>成绩</th><th>成绩标识</th><th>学分</th><th>学时</th>
                </tr></thead><tbody><tr>
                  <td><input type="checkbox"></td><td>1</td><td>2025-2026-1</td><td>109030013</td>
                  <td>人工智能</td><td>自动化系</td><td>84</td><td></td><td>1.5</td><td>24</td>
                </tr><tr>
                  <td><input type="checkbox"></td><td>2</td><td></td><td>130090115</td>
                  <td>自动控制基础</td><td>自动化系</td><td>缓考</td><td>缓考</td><td>3</td><td>48</td>
                </tr></tbody></table>
                """;
        assertTrue(parser.supports(html));
        List<GradeVO> grades = parser.parse(html);
        assertEquals(2, grades.size());
        assertEquals("人工智能", grades.get(0).getCourseName());
        assertEquals("84", grades.get(0).getScore());
        assertEquals("1.5", grades.get(0).getCredit());
        assertEquals("24", grades.get(0).getHours());
        assertEquals("2025-2026-1", grades.get(1).getTerm());
        assertEquals("缓考", grades.get(1).getFlag());
    }

    @Test
    void doesNotTreatTheNewLoginPageAsAGradePage() {
        String html = "<form name='loginForm' action='/jsxsd/xk/LoginToXk'><input id='userAccount'></form>";
        assertFalse(parser.supports(html));
    }

    @Test
    void parsesCurrentAjaxJsonResponse() {
        String json = "{\"code\":0,\"count\":2,\"data\":["
                + "{\"xnxqid\":\"2025-2026-1\",\"kch\":\"109030013\",\"kc_mc\":\"人工智能\","
                + "\"xf\":1.5,\"zxs\":24,\"zcj\":84,\"zcjstr\":\"84\",\"jd\":3.4,"
                + "\"ksfs\":\"考查\",\"ksxz\":\"正常考试\",\"kcsx\":\"必修\",\"kcxzmc\":\"专业选修课\"},"
                + "{\"xnxqid\":\"2025-2026-1\",\"kch\":\"130090115\",\"kc_mc\":\"自动控制基础\","
                + "\"xf\":3,\"zxs\":48,\"zcj\":0,\"zcjstr\":\"0\",\"cjbs\":\"缓考\"}]}";
        assertTrue(parser.supportsJson(json));
        assertEquals(2, parser.jsonCount(json));
        List<GradeVO> grades = parser.parseJson(json);
        assertEquals(2, grades.size());
        assertEquals("人工智能", grades.get(0).getCourseName());
        assertEquals("84", grades.get(0).getScore());
        assertEquals("3.4", grades.get(0).getGradePoint());
        assertEquals("缓考", grades.get(1).getFlag());
    }

    @Test
    void rejectsNonGradeJson() {
        assertFalse(parser.supportsJson("{\"code\":0,\"data\":{\"message\":\"ok\"}}"));
    }
}
