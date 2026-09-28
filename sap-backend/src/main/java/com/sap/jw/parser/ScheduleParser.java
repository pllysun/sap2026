package com.sap.jw.parser;

import com.sap.jw.vo.CourseVO;
import com.sap.jw.vo.RemarkVO;
import com.sap.jw.vo.ScheduleVO;
import com.sap.jw.vo.TermVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 强智教务课表(xskb_list.do)解析器。
 * <p>表格 #kbtable：表头一行为星期，其后每行 = 一个节次（th 标签）+ 7 个星期格(td)。
 * 每格 div.kbcontent 内：课程名[学时类型]&lt;br&gt;&lt;font title='老师'&gt;…&lt;font title='周次(节次)'&gt;…&lt;font title='教室'&gt;…，
 * 多门课用一串短横线分隔。</p>
 */
@Component
public class ScheduleParser {

    private static final List<String> DEFAULT_WEEKDAYS =
            Arrays.asList("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日");
    private static final Pattern TYPE = Pattern.compile("\\[(.*?)]");
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    /** 单元格内多门课的分隔符：一串连续短横线('-')或全角破折号('—')，可被 &lt;br&gt; 包裹。 */
    private static final Pattern DIVIDER = Pattern.compile("(?i)(?:<br\\s*/?>|\\s)*[-—]{4,}(?:<br\\s*/?>|\\s)*");
    private static final Pattern WEEKS = Pattern.compile("\\d+(?:[\\-,]\\d+)*周");

    /**
     * 判断响应是否为课表页面。强智新版有时把 id 从 kbtable 改成 class/data-*，
     * 或把表头单元格从 th 改成 td；不能再只用字符串 contains("kbtable") 判定。
     */
    public boolean supports(String html) {
        if (html == null || html.isBlank()) return false;
        String lower = html.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("课表暂未公布") || lower.contains("没有符合条件的数据")) return true;
        if (looksLikeJsonRows(html)) return true;
        return findScheduleTable(Jsoup.parse(html)) != null;
    }

    public ScheduleVO parse(String html) {
        ScheduleVO vo = new ScheduleVO();
        Document doc = Jsoup.parse(html);

        if (looksLikeJsonRows(html)) return parseJsonRows(html);
        if (html.contains("课表暂未公布") || html.contains("没有符合条件的数据")) {
            // 新版查询接口在没有发布课表时返回一段 alert 脚本；这是正常的
            // 空课表，不应再向客户端报告“页面格式更新”。
            return vo;
        }

        // 学期下拉
        Elements options = doc.select("select[name=xnxq01id] option");
        for (Element op : options) {
            String value = op.attr("value");
            if (value == null || value.isBlank()) continue;
            TermVO t = new TermVO();
            t.setValue(value.trim());
            t.setLabel(clean(op.text()));
            t.setCurrent(op.hasAttr("selected"));
            vo.getTerms().add(t);
            if (t.isCurrent()) vo.setTerm(t.getValue());
        }
        if (vo.getTerm() == null && !vo.getTerms().isEmpty()) {
            vo.setTerm(vo.getTerms().get(0).getValue());
        }

        Element table = findScheduleTable(doc);
        if (table == null) return vo;

        // 星期表头
        Element header = table.selectFirst("tr");
        List<String> weekdays = DEFAULT_WEEKDAYS;
        if (header != null) {
            Elements hcells = header.select("th,td");
            if (hcells.size() >= 8) {
                weekdays = hcells.stream().skip(1).limit(7).map(e -> clean(e.text())).toList();
            }
        }
        vo.setWeekdays(weekdays);

        int sectionIndex = 0;
        for (Element row : table.select("tr")) {
            Element th = row.selectFirst("th");
            if (th != null && th.text().contains("备注")) {
                parseRemarks(row, vo); // 备注行：无固定时间格的实验/实习/集中实践
                continue;
            }
            // 新版把表头也渲染成 td；不要把“周一/周二…”这一行当成第 1 节。
            if (isWeekdayHeaderRow(row)) continue;
            Elements dayCells = row.select("td");
            if (dayCells.isEmpty()) continue; // 表头行
            sectionIndex++;
            boolean modernLabelCell = !dayCells.isEmpty()
                    && hasClassContaining(dayCells.get(0), "weeklyTable-label");
            String section = th != null ? clean(th.text())
                    : modernLabelCell ? clean(dayCells.get(0).select(".index-title").text())
                    : ("第" + sectionIndex + "节");

            // 新版 qz-weeklyTable 的第一格是节次标签，后面才是周一至周日；
            // 旧版表格则以 th 作为节次，不要改变旧版的索引。
            int dayOffset = modernLabelCell ? 1 : 0;
            int courseSectionIndex = modernLabelCell ? parseSectionIndex(section) : sectionIndex;
            for (int i = dayOffset; i < dayCells.size() && i < dayOffset + 7; i++) {
                Element td = dayCells.get(i);
                int day = i - dayOffset + 1;
                Elements blocks = new Elements();
                for (Element candidate : td.select("div, [data-kbcontent]")) {
                    if (candidate.hasAttr("data-kbcontent") || hasClassContaining(candidate, "kbcontent")) {
                        blocks.add(candidate);
                    }
                }
                // 2026 新版个人课表使用 qz-weeklyTable：每门课是
                // .courselists-item，标题与教师/周次/地点分别放在
                // .qz-hasCourse-title、.qz-hasCourse-detaillists 中，
                // 不再输出旧版 font[title] 或 kbcontent。
                Elements modernItems = td.select(".courselists-item");
                if (!modernItems.isEmpty()) {
                    for (Element item : modernItems) {
                        CourseVO c = parseModernItem(item);
                        if (c == null || c.getName() == null || c.getName().isBlank()) continue;
                        c.setDay(day);
                        c.setDayName(day <= weekdays.size() ? weekdays.get(day - 1) : "");
                        c.setSection(section);
                        // 新版页面包含“午休”等非节次行，不能用物理行号作为大节索引。
                        c.setSectionIndex(courseSectionIndex > 0 ? courseSectionIndex : sectionIndex);
                        vo.getCourses().add(c);
                    }
                    continue;
                }
                // 新版某些页面直接把 font 元素放在 td 内，不再包一层 kbcontent。
                if (blocks.isEmpty() && !td.select("font").isEmpty()) blocks = new Elements(td);
                for (Element div : blocks) {
                    String inner = div.html();
                    if (inner == null || !inner.toLowerCase(java.util.Locale.ROOT).contains("<font")) continue;
                    for (String seg : DIVIDER.split(inner)) {
                        if (!seg.toLowerCase(java.util.Locale.ROOT).contains("<font")) continue;
                        CourseVO c = parseSegment(seg);
                        if (c == null || c.getName() == null || c.getName().isBlank()) continue;
                        c.setDay(day);
                        c.setDayName(day <= weekdays.size() ? weekdays.get(day - 1) : "");
                        c.setSection(section);
                        c.setSectionIndex(sectionIndex);
                        vo.getCourses().add(c);
                    }
                }
            }
        }
        return vo;
    }

    /** 解析新版 qz-weeklyTable 的单门课程块。 */
    private CourseVO parseModernItem(Element item) {
        if (item == null) return null;
        String name = clean(item.select(".qz-hasCourse-title, .course-title, [class*=title]").text());
        if (name.isBlank()) {
            // 某些主题只保留课程块文本，去掉明细后以第一行作为课程名。
            String text = clean(item.text());
            int split = text.indexOf('\n');
            name = clean(split > 0 ? text.substring(0, split) : text);
        }
        CourseVO c = new CourseVO();
        Matcher tm = TYPE.matcher(name);
        if (tm.find()) {
            c.setType(tm.group(1).trim());
            name = name.substring(0, tm.start()).trim();
        }
        c.setName(name);
        String details = clean(item.select(".qz-hasCourse-detaillists, .course-detail, [class*=detail]").text());
        if (details.isBlank()) details = clean(item.text());
        c.setTeacher(labeled(details, "教师", "老师", "任课教师"));
        c.setWeeks(labeled(details, "周次", "上课周次"));
        c.setRoom(labeled(details, "地点", "教室", "上课地点"));
        if (c.getWeeks() == null || c.getWeeks().isBlank()) {
            Matcher weeks = Pattern.compile("\\d+(?:[\\-,，、]\\d+)*周").matcher(details);
            if (weeks.find()) c.setWeeks(weeks.group());
        }
        return c;
    }

    /** 从新版明细文本提取“标签：值”，兼容空格、换行和英文冒号。 */
    private static String labeled(String text, String... labels) {
        if (text == null || text.isBlank()) return null;
        for (String label : labels) {
            // 新版明细是一行以分号分隔的“老师:…;时间:…;地点:…”；
            // 以分号作为值的边界，避免把后续字段拼进教师名。
            Matcher m = Pattern.compile("(?:^|[;；\\s])" + Pattern.quote(label)
                    + "\\s*[：:]\\s*([^;；]+)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) return clean(m.group(1));
        }
        return null;
    }

    private static boolean looksLikeJsonRows(String html) {
        String s = html == null ? "" : html.trim();
        if (!(s.startsWith("{") || s.startsWith("["))) return false;
        try {
            Object value = com.alibaba.fastjson2.JSON.parse(s);
            return jsonRows(value).stream().anyMatch(ScheduleParser::looksLikeCourseRow);
        } catch (Exception ignored) { }
        return false;
    }

    /** JSON 接口可能返回其它列表（公告、分页元数据等），至少要有两个课表字段才认定为课表。 */
    private static boolean looksLikeCourseRow(Object row) {
        if (!(row instanceof Map<?, ?> map)) return false;
        String name = textValue(map, "kcmc", "kcmc1", "courseName", "课程名称", "kc", "课程");
        if (name == null || name.isBlank()) return false;
        int scheduleFields = 0;
        if (hasJsonText(map, "skxq", "xq", "xqmc", "weekday", "星期", "上课星期", "day")) scheduleFields++;
        if (hasJsonText(map, "jcdm", "jcs", "sksj", "jc", "节次", "上课节次", "section")) scheduleFields++;
        if (hasJsonText(map, "zc", "skzc", "zcsm", "周次", "上课周次", "weeks")) scheduleFields++;
        if (hasJsonText(map, "jsxm", "jsxmzc", "teacher", "teacherName", "任课教师", "教师", "老师")) scheduleFields++;
        if (hasJsonText(map, "jxcd", "jxcdmc", "skdd", "room", "教室", "上课地点", "地点")) scheduleFields++;
        return scheduleFields >= 2;
    }

    private static boolean hasJsonText(Map<?, ?> map, String... keys) {
        Object value = firstValue(map, keys);
        return value != null && !String.valueOf(value).isBlank();
    }

    private static Collection<?> firstCollection(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Collection<?> collection) return collection;
            if (value instanceof Map<?, ?> nested) {
                Collection<?> collection = firstCollection(nested, "rows", "list", "items", "records");
                if (collection != null) return collection;
            }
        }
        return null;
    }

    private static Collection<?> jsonRows(Object root) {
        if (root instanceof Collection<?> collection) return collection;
        if (root instanceof Map<?, ?> map) {
            Collection<?> rows = firstCollection(map, "data", "rows", "list", "items", "records");
            if (rows != null) return rows;
        }
        return List.of();
    }

    /** 解析新版 layui 查询接口的 JSON 行数据（字段名按不同强智节点做兼容映射）。 */
    private ScheduleVO parseJsonRows(String html) {
        ScheduleVO vo = new ScheduleVO();
        Object root;
        try { root = com.alibaba.fastjson2.JSON.parse(html.trim()); }
        catch (Exception ignored) { return vo; }
        Collection<?> rows = jsonRows(root);
        if (root instanceof Map<?, ?> m) {
            Object term = firstValue(m, "term", "xnxq01id", "学期");
            if (term != null) vo.setTerm(String.valueOf(term));
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) continue;
            String name = textValue(map, "kcmc", "kcmc1", "courseName", "课程名称", "kc", "课程");
            if (name == null || name.isBlank()) continue;
            CourseVO c = new CourseVO(); c.setName(name);
            c.setType(textValue(map, "kclbmc", "kcxzmc", "courseType", "课程类别", "课程性质"));
            c.setTeacher(textValue(map, "jsxm", "jsxmzc", "teacher", "teacherName", "任课教师", "教师", "老师"));
            c.setRoom(textValue(map, "jxcd", "jxcdmc", "skdd", "room", "教室", "上课地点", "地点"));
            c.setWeeks(textValue(map, "zc", "skzc", "zcsm", "周次", "上课周次", "weeks"));
            String day = textValue(map, "skxq", "xq", "xqmc", "weekday", "星期", "上课星期", "day");
            c.setDay(parseDay(day));
            c.setDayName(c.getDay() >= 1 && c.getDay() <= 7 ? DEFAULT_WEEKDAYS.get(c.getDay() - 1) : day);
            String section = textValue(map, "jcdm", "jcs", "sksj", "jc", "节次", "上课节次", "section");
            c.setSection(section);
            c.setSectionIndex(parseSectionIndex(section));
            if (c.getDay() < 1 || c.getSectionIndex() < 1) continue;
            vo.getCourses().add(c);
        }
        return vo;
    }

    private static Object firstValue(Map<?, ?> map, String... keys) {
        for (String key : keys) if (map.containsKey(key) && map.get(key) != null) return map.get(key);
        return null;
    }

    private static String textValue(Map<?, ?> map, String... keys) {
        Object value = firstValue(map, keys);
        return value == null ? null : clean(String.valueOf(value));
    }

    private static int parseDay(String value) {
        if (value == null) return 0;
        Matcher m = Pattern.compile("[1-7]").matcher(value);
        if (m.find()) return Integer.parseInt(m.group());
        for (int i = 0; i < DEFAULT_WEEKDAYS.size(); i++)
            if (value.contains(DEFAULT_WEEKDAYS.get(i)) || value.contains(DEFAULT_WEEKDAYS.get(i).replace("星期", "周"))) return i + 1;
        return 0;
    }

    private static int parseFirstInt(String value) {
        if (value == null) return 0;
        Matcher m = Pattern.compile("\\d+").matcher(value);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    /** 新版接口有时返回具体节次(如“第3,4节”)，App 需要对应第2个大节。 */
    private static int parseSectionIndex(String value) {
        int first = parseFirstInt(value);
        if (first <= 0) return 0;
        if (value != null && (value.contains(",") || value.contains("，")
                || value.contains("-") || value.contains("~") || value.contains("－"))) {
            return (first + 1) / 2;
        }
        return first;
    }

    private CourseVO parseSegment(String seg) {
        CourseVO c = new CourseVO();
        // 去掉分隔残留的前导 <br>，避免课程名被切成空串
        seg = seg.replaceAll("(?i)^(?:<br\\s*/?>|\\s)+", "");
        String namePart = BR.split(seg, 2)[0];
        String name = clean(Jsoup.parse(namePart).text());
        Matcher tm = TYPE.matcher(name);
        if (tm.find()) {
            c.setType(tm.group(1).trim());
            name = name.substring(0, tm.start()).trim();
        }
        c.setName(name);

        Document f = Jsoup.parseBodyFragment(seg);
        c.setTeacher(textOf(firstFont(f, "老师", "教师")));
        c.setWeeks(textOf(firstFont(f, "周次")));
        c.setRoom(textOf(firstFont(f, "教室", "地点")));
        return c;
    }

    /** 找到旧版、新版强智课表表格；优先使用明确 id/class，再按课表单元格启发式回退。 */
    private static Element findScheduleTable(Document doc) {
        if (doc == null) return null;
        // 个别主题把课表容器从 table 改成 div，但仍保留稳定的 kbtable id。
        Element explicit = doc.getElementById("kbtable");
        if (explicit != null) return explicit;
        // 当前强智个人课表的稳定容器，优先于页面中的布局/筛选表格。
        Element modern = doc.selectFirst("table.qz-weeklyTable");
        if (modern != null) return modern;
        for (Element table : doc.select("table")) {
            String id = table.id();
            if ("kbtable".equalsIgnoreCase(id) || hasClassIgnoreCase(table, "kbtable")) return table;
        }
        for (Element table : doc.select("table")) {
            if (table.select("div.kbcontent, [data-kbcontent]").stream()
                    .anyMatch(e -> e.hasAttr("data-kbcontent") || hasClassContaining(e, "kbcontent"))) return table;
            // 另一种新版是 td 直接包含字段 font，无 kbcontent 包装。
            boolean hasTeacher = table.select("td font[title]").stream().anyMatch(e -> {
                String title = e.attr("title");
                return title.contains("老师") || title.contains("教师");
            });
            if (hasTeacher && table.select("tr").size() >= 2) return table;
            // 没有课程时页面可能没有 font 字段，但学期选择器和课表行仍会保留。
            if (!doc.select("select[name=xnxq01id] option").isEmpty()
                    && table.select("tr").size() >= 2 && table.select("td").size() >= 5) return table;
        }
        return null;
    }

    private static boolean hasClassIgnoreCase(Element element, String expected) {
        for (String value : element.classNames()) {
            if (expected.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static boolean hasClassContaining(Element element, String fragment) {
        String needle = fragment.toLowerCase(java.util.Locale.ROOT);
        return element.classNames().stream()
                .anyMatch(value -> value.toLowerCase(java.util.Locale.ROOT).contains(needle));
    }

    private static Element firstFont(Document fragment, String... titleParts) {
        for (Element font : fragment.select("font[title]")) {
            String title = font.attr("title");
            for (String part : titleParts) {
                if (title != null && title.contains(part)) return font;
            }
        }
        return null;
    }

    private static boolean isWeekdayHeaderRow(Element row) {
        if (row == null || !row.select("font").isEmpty()) return false;
        String text = clean(row.text());
        if (text.isBlank()) return false;
        int hits = 0;
        for (String day : DEFAULT_WEEKDAYS) {
            if (text.contains(day) || text.contains(day.replace("星期", "周"))) hits++;
        }
        return hits >= 3;
    }

    /** 解析备注行：td 文本按 ; 拆条；注意用 td.text()（保留空格作分隔），勿用 clean()。 */
    private void parseRemarks(Element row, ScheduleVO vo) {
        Element td = row.selectFirst("td");
        if (td == null) return;
        String full = td.text();
        if (full == null || full.isBlank()) return;
        for (String part : full.split("[;；]")) {
            String e = part.trim();
            if (!e.isEmpty()) vo.getRemarks().add(parseRemark(e));
        }
    }

    /** 单条备注：`课程名 [教师] 周次 班级`，以周次(\\d+(-,\\d+)*周)为锚。 */
    private RemarkVO parseRemark(String e) {
        RemarkVO r = new RemarkVO();
        r.setRaw(e);
        Matcher m = WEEKS.matcher(e);
        if (m.find()) {
            r.setWeeks(m.group());
            String before = e.substring(0, m.start()).trim();
            String after = e.substring(m.end()).trim();
            if (!after.isBlank()) r.setClazz(after);
            String[] toks = before.split("\\s+");
            if (toks.length >= 2) {
                r.setTeacher(dedupTeacher(toks[toks.length - 1]));
                r.setName(String.join(" ", Arrays.copyOf(toks, toks.length - 1)).trim());
            } else {
                r.setName(before);
            }
        } else {
            // 无周次（如军训等集中实践）：仍尝试拆“课程名 + 教师列表”，并对强智把同一老师重复几十次的脏数据去重，
            // 避免课程名和人名糊在一起。
            String[] toks = e.split("\\s+");
            int tIdx = -1;
            for (int i = 0; i < toks.length; i++) {
                if (toks[i].contains(",") || toks[i].contains("，") || toks[i].contains("、")) { tIdx = i; break; }
            }
            if (tIdx >= 1) {
                r.setName(String.join(" ", Arrays.copyOfRange(toks, 0, tIdx)).trim());
                r.setTeacher(dedupTeacher(toks[tIdx]));
                if (tIdx + 1 < toks.length) {
                    r.setClazz(String.join(" ", Arrays.copyOfRange(toks, tIdx + 1, toks.length)).trim());
                }
            } else {
                r.setName(e);
            }
        }
        return r;
    }

    /**
     * 去除老师名脏数据中重复的姓名（强智偶发把同一老师重复几十次，逗号分隔）。
     * 如 "李剑,李剑,...,李剑" → "李剑"；多个不同老师按出现顺序保留去重 "张三,李四,张三" → "张三,李四"。
     */
    private static String dedupTeacher(String teacher) {
        if (teacher == null || teacher.isBlank()) return teacher;
        // 兼容半角/全角逗号、顿号
        String[] names = teacher.split("[,，、]");
        if (names.length <= 1) return teacher;
        java.util.LinkedHashSet<String> distinct = new java.util.LinkedHashSet<>();
        for (String n : names) {
            String t = n.trim();
            if (!t.isEmpty()) distinct.add(t);
        }
        return String.join(",", distinct);
    }

    private static String textOf(Element e) {
        return e == null ? null : clean(e.text());
    }

    private static String clean(String s) {
        return s == null ? null : s.replace(" ", "").replace("&nbsp;", "").trim();
    }
}
