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

    public ScheduleVO parse(String html) {
        ScheduleVO vo = new ScheduleVO();
        Document doc = Jsoup.parse(html);

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

        Element table = doc.selectFirst("#kbtable");
        if (table == null) return vo;

        // 星期表头
        Element header = table.selectFirst("tr");
        List<String> weekdays = DEFAULT_WEEKDAYS;
        if (header != null) {
            Elements hcells = header.select("th");
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
            Elements dayCells = row.select("td");
            if (dayCells.isEmpty()) continue; // 表头行
            sectionIndex++;
            String section = th != null ? clean(th.text()) : ("第" + sectionIndex + "节");

            for (int i = 0; i < dayCells.size() && i < 7; i++) {
                Element td = dayCells.get(i);
                for (Element div : td.select("div.kbcontent")) {
                    String inner = div.html();
                    if (inner == null || !inner.contains("<font")) continue;
                    for (String seg : DIVIDER.split(inner)) {
                        if (!seg.contains("<font")) continue;
                        CourseVO c = parseSegment(seg);
                        if (c == null || c.getName() == null || c.getName().isBlank()) continue;
                        c.setDay(i + 1);
                        c.setDayName(i < weekdays.size() ? weekdays.get(i) : "");
                        c.setSection(section);
                        c.setSectionIndex(sectionIndex);
                        vo.getCourses().add(c);
                    }
                }
            }
        }
        return vo;
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
        c.setTeacher(textOf(f.selectFirst("font[title=老师]")));
        c.setWeeks(textOf(f.selectFirst("font[title^=周次]")));
        c.setRoom(textOf(f.selectFirst("font[title=教室]")));
        return c;
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
