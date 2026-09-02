package com.sap.jw.parser;

import com.sap.jw.vo.GradeVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 当前强智「课程成绩查询」解析器。
 * <p>新版成绩表列由页面配置动态生成，首列还可能是复选框，不能再依赖旧版固定下标；
 * 统一按表头名称映射字段，并对未提供的字段保留空值。</p>
 */
@Component
public class GradeParser {

    private static final String TERM_PATTERN = "\\d{4}-\\d{4}-\\d";

    /** 成绩页必须同时存在课程名称和成绩表头，避免把登录/空壳页误判为成功。 */
    public boolean supports(String html) {
        if (html == null || html.isBlank()) return false;
        return findGradeTable(html) != null;
    }

    /** 新版强智成绩接口返回 {code:0,data:[...],count:n} JSON，而不是 HTML 表格。 */
    public boolean supportsJson(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            JSONObject root = JSON.parseObject(body.trim());
            Object rawData = root == null ? null : root.get("data");
            return root != null && Integer.valueOf(0).equals(root.getInteger("code"))
                    && rawData instanceof JSONArray;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 解析新版 cjcx_list JSON，字段名以当前教务页面实际返回为准。 */
    public List<GradeVO> parseJson(String body) {
        if (!supportsJson(body)) return List.of();
        JSONObject root = JSON.parseObject(body.trim());
        JSONArray data = root.getJSONArray("data");
        List<GradeVO> list = new ArrayList<>();
        for (Object item : data) {
            if (!(item instanceof JSONObject row)) continue;
            String name = text(row, "kc_mc", "kcmc", "courseName");
            if (name.isBlank()) continue;
            GradeVO g = new GradeVO();
            g.setTerm(text(row, "xnxqid", "xqstr", "xqmc", "term"));
            g.setCourseNo(text(row, "kch", "kcbh", "courseNo"));
            g.setCourseName(name);
            // zcjstr 保留“及格/缓考”等教务显示文本，zcj 作为数字兜底。
            g.setScore(text(row, "zcjstr", "zcj", "score"));
            g.setCredit(text(row, "xf", "credit"));
            g.setHours(text(row, "zxs", "hours"));
            g.setGradePoint(text(row, "jd", "gradePoint"));
            g.setFlag(text(row, "cjbs", "cj0708bz", "flag"));
            g.setAssessMethod(text(row, "ksfs", "assessMethod"));
            g.setExamNature(text(row, "ksxz", "examNature"));
            g.setCourseAttr(text(row, "kcsx", "courseAttr"));
            g.setCourseNature(text(row, "kcxzmc", "courseNature"));
            list.add(g);
        }
        return list;
    }

    /** 新版接口分页总数；接口可能不返回 count，调用方按空页结束。 */
    public int jsonCount(String body) {
        if (!supportsJson(body)) return -1;
        Integer count = JSON.parseObject(body.trim()).getInteger("count");
        return count == null ? -1 : Math.max(0, count);
    }

    private static String text(JSONObject row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) continue;
            String text = String.valueOf(value).trim();
            if (!text.isBlank() && !"null".equalsIgnoreCase(text)) return text;
        }
        return "";
    }

    public List<GradeVO> parse(String html) {
        Document doc = Jsoup.parse(html);
        Element table = findGradeTable(doc);
        if (table == null) return List.of();

        Header header = readHeader(table);
        if (header != null && header.courseName >= 0 && header.score >= 0) {
            return parseMappedRows(table, header, selectedTerm(doc));
        }
        // 仅对确实没有表头的历史 dataList 保留旧列序兼容；新版有表头时绝不走旧下标。
        return parseLegacyRows(table);
    }

    private List<GradeVO> parseMappedRows(Element table, Header header, String fallbackTerm) {
        List<GradeVO> list = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            Elements tds = tr.select("td");
            if (tds.size() <= Math.max(header.courseName, header.score)) continue;
            List<String> row = new ArrayList<>(tds.size());
            for (Element td : tds) row.add(JwTable.clean(td.text()));
            String name = value(row, header.courseName);
            if (name.isBlank()) continue;
            GradeVO g = new GradeVO();
            g.setTerm(value(row, header.term));
            if (g.getTerm() == null || g.getTerm().isBlank()) g.setTerm(fallbackTerm);
            g.setCourseNo(value(row, header.courseNo));
            g.setCourseName(name);
            g.setScore(value(row, header.score));
            g.setCredit(value(row, header.credit));
            g.setHours(value(row, header.hours));
            g.setGradePoint(value(row, header.gradePoint));
            g.setFlag(value(row, header.flag));
            g.setAssessMethod(value(row, header.assessMethod));
            g.setExamNature(value(row, header.examNature));
            g.setCourseAttr(value(row, header.courseAttr));
            g.setCourseNature(value(row, header.courseNature));
            list.add(g);
        }
        return list;
    }

    private List<GradeVO> parseLegacyRows(Element table) {
        List<GradeVO> list = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            Elements tds = tr.select("td");
            if (tds.size() < 13) continue;
            List<String> row = new ArrayList<>(tds.size());
            for (Element td : tds) row.add(JwTable.clean(td.text()));
            String courseName = JwTable.at(row, 3);
            if (courseName.isBlank()) continue;
            GradeVO g = new GradeVO();
            g.setTerm(JwTable.at(row, 1));
            g.setCourseNo(JwTable.at(row, 2));
            g.setCourseName(courseName);
            g.setScore(JwTable.at(row, 4));
            g.setCredit(JwTable.at(row, 5));
            g.setHours(JwTable.at(row, 6));
            g.setGradePoint(JwTable.at(row, 7));
            g.setFlag(JwTable.at(row, 8));
            g.setAssessMethod(JwTable.at(row, 9));
            g.setExamNature(JwTable.at(row, 10));
            g.setCourseAttr(JwTable.at(row, 11));
            g.setCourseNature(JwTable.at(row, 12));
            list.add(g);
        }
        return list;
    }

    private Header readHeader(Element table) {
        Element headerRow = findHeaderRow(table);
        if (headerRow == null) return null;
        Elements cells = headerRow.select("th,td");
        Header h = new Header();
        for (int i = 0; i < cells.size(); i++) {
            String text = normalize(cells.get(i).text());
            if (text.isBlank()) continue;
            if (h.term < 0 && matches(text, "开课学期", "开课时间", "学年学期", "学期")) h.term = i;
            if (h.courseNo < 0 && matches(text, "课程编号", "课程代码", "课程号")) h.courseNo = i;
            if (h.courseName < 0 && matches(text, "课程名称", "课程名")) h.courseName = i;
            if (h.score < 0 && matches(text, "成绩", "总评成绩", "最终成绩", "课程成绩")) h.score = i;
            if (h.credit < 0 && matches(text, "学分")) h.credit = i;
            if (h.hours < 0 && matches(text, "总学时", "学时")) h.hours = i;
            if (h.gradePoint < 0 && matches(text, "绩点")) h.gradePoint = i;
            if (h.flag < 0 && matches(text, "成绩标识", "成绩标志", "标志")) h.flag = i;
            if (h.assessMethod < 0 && matches(text, "考核方式")) h.assessMethod = i;
            if (h.examNature < 0 && matches(text, "考试性质")) h.examNature = i;
            if (h.courseAttr < 0 && matches(text, "课程属性")) h.courseAttr = i;
            if (h.courseNature < 0 && matches(text, "课程性质")) h.courseNature = i;
        }
        return h;
    }

    private static final class Header {
        int term = -1, courseNo = -1, courseName = -1, score = -1, credit = -1, hours = -1,
                gradePoint = -1, flag = -1, assessMethod = -1, examNature = -1,
                courseAttr = -1, courseNature = -1;
    }

    private Element findGradeTable(String html) {
        return findGradeTable(Jsoup.parse(html));
    }

    private Element findGradeTable(Document doc) {
        Element dataList = doc.selectFirst("#dataList");
        if (dataList != null && isGradeTable(dataList)) return dataList;
        for (Element table : doc.select("table")) {
            if (isGradeTable(table)) return table;
        }
        return null;
    }

    private boolean isGradeTable(Element table) {
        if (findHeaderRow(table) != null) return true;
        // 历史 dataList 没有表头，仅在存在足够数据列时接受。
        return "dataList".equalsIgnoreCase(table.id())
                && table.select("tr").stream().anyMatch(tr -> tr.select("td").size() >= 13);
    }

    /** 表头在新版主题中可能位于 thead，也可能是普通 tr，甚至使用 td 而非 th。 */
    private Element findHeaderRow(Element table) {
        for (Element tr : table.select("tr")) {
            String text = normalize(tr.select("th,td").text());
            if ((text.contains("课程名称") || text.contains("课程名"))
                    && (text.contains("成绩") || text.contains("总评成绩"))) return tr;
        }
        return null;
    }

    private String selectedTerm(Document doc) {
        for (Element op : doc.select("select option[selected]")) {
            String value = op.attr("value").trim();
            if (value.matches(TERM_PATTERN)) return value;
        }
        for (Element op : doc.select("select option")) {
            String value = op.attr("value").trim();
            if (value.matches(TERM_PATTERN)) return value;
        }
        return "";
    }

    private static boolean matches(String value, String... names) {
        for (String name : names) {
            String normalized = normalize(name);
            if (value.equals(normalized) || (normalized.length() > 2 && value.contains(normalized))) return true;
        }
        return false;
    }

    private static String value(List<String> row, int index) {
        return index < 0 ? "" : JwTable.at(row, index);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }
}
