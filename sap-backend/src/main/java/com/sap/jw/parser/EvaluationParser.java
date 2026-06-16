package com.sap.jw.parser;

import com.sap.jw.vo.EvalTaskVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学生评教（强智 xspj）页面解析：
 * <ul>
 *   <li>{@link #parseRounds} —— xspj_find.do：各学期评教批次（pj0502id + 类型 pj01id）</li>
 *   <li>{@link #parseTasks} —— xspj_list.do：某批次下的教师评教行（已评/未评 + editUrl）</li>
 *   <li>{@link #parseForm} —— xspj_edit.do：评教表单（隐藏域 + 指标 radio + 分值），供自动填分提交</li>
 * </ul>
 */
@Component
public class EvaluationParser {

    private static final Pattern OPEN_WIN = Pattern.compile("openWindow\\('([^']*xspj_edit\\.do[^']*)'");
    private static final Pattern PARAM = Pattern.compile("[?&]([^=]+)=([^&]*)");
    private static final Pattern FZ = Pattern.compile("^pj0601fz_([^_]+)_(.+)$");

    /** 一个评教批次（某学期一轮评教，含若干类型）。 */
    public static class Round {
        public String term;       // xnxq01id
        public String pj0502id;   // 批次
        public final List<String[]> types = new ArrayList<>(); // [pj01id, 类型名]
    }

    /** 评教表单的可提交模型。 */
    public static class EvalForm {
        /** 全部具名隐藏域（保留顺序与重复，如 pj06xh、pj0601fz_*）。 */
        public final List<String[]> hidden = new ArrayList<>();
        /** 指标序号 → 选项 id 列表（按列序，第 0 个为最高档=满分）。 */
        public final LinkedHashMap<String, List<String>> radios = new LinkedHashMap<>();
        /** "指标序号|选项id" → 分值。 */
        public final Map<String, Double> fz = new LinkedHashMap<>();
        public String saveAction; // 表单 action（/jsxsd/xspj/xspj_save.do）
    }

    /** 解析 find 页：返回各评教批次，新→旧（按 find 页顺序）。 */
    public List<Round> parseRounds(String html) {
        Document doc = Jsoup.parse(html);
        Map<String, Round> byKey = new LinkedHashMap<>();
        for (Element a : doc.select("a[href*=xspj_list.do]")) {
            String href = a.attr("href");
            Map<String, String> p = params(href);
            String term = p.get("xnxq01id");
            String pj0502id = p.get("pj0502id");
            String pj01id = p.get("pj01id");
            if (term == null || pj0502id == null) continue;
            String key = pj0502id + "|" + term;
            Round r = byKey.computeIfAbsent(key, k -> {
                Round x = new Round();
                x.term = term;
                x.pj0502id = pj0502id;
                return x;
            });
            // 仅记录带 pj01id 的具体类型（忽略“进入评价”聚合链接）
            if (pj01id != null && !pj01id.isBlank()) {
                boolean exists = r.types.stream().anyMatch(t -> t[0].equals(pj01id));
                if (!exists) r.types.add(new String[]{pj01id, a.text().trim()});
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** 解析 list 页：某批次某类型下的全部评教行。 */
    public List<EvalTaskVO> parseTasks(String html, String term, String typeName) {
        Document doc = Jsoup.parse(html);
        List<EvalTaskVO> list = new ArrayList<>();
        for (Element tr : doc.select("tr")) {
            String trHtml = tr.html();
            Matcher m = OPEN_WIN.matcher(trHtml);
            if (!m.find()) continue;
            String editUrl = m.group(1).replace("&amp;", "&");
            Map<String, String> p = params(editUrl);
            EvalTaskVO t = new EvalTaskVO();
            t.setTerm(term);
            t.setTypeName(typeName);
            t.setEditUrl(editUrl);
            t.setJx0404id(p.get("jx0404id"));
            t.setScore(blankToNull(p.get("zpf")));
            boolean view = "view".equalsIgnoreCase(p.get("type"));
            List<Element> tds = tr.select("td");
            if (tds.size() >= 5) {
                t.setTeacherNo(text(tds, 1));
                t.setTeacher(text(tds, 2));
                t.setCollege(text(tds, 3));
                if (typeName == null || typeName.isBlank()) t.setTypeName(text(tds, 4));
            }
            // “是否评教/是否提交”两列（倒数：操作列前两格常为是/否）；兜底用 type/zpf
            boolean evaluated = view || hasYes(tds, 6) || (t.getScore() != null);
            boolean submitted = view || hasYes(tds, 7);
            t.setEvaluated(evaluated);
            t.setSubmitted(submitted);
            list.add(t);
        }
        return list;
    }

    /** 解析 edit 页：抽取隐藏域、指标 radio 与分值。 */
    public EvalForm parseForm(String html) {
        Document doc = Jsoup.parse(html);
        Element form = doc.selectFirst("form#Form1");
        if (form == null) form = doc.selectFirst("form");
        EvalForm f = new EvalForm();
        if (form == null) return f;
        f.saveAction = form.attr("action");
        for (Element in : form.select("input")) {
            String type = in.attr("type").toLowerCase();
            String name = in.attr("name");
            if (name == null || name.isBlank()) continue;
            String val = in.attr("value");
            if ("radio".equals(type)) {
                if (name.startsWith("pj0601id_")) {
                    String xh = name.substring("pj0601id_".length());
                    f.radios.computeIfAbsent(xh, k -> new ArrayList<>()).add(val);
                }
                continue; // radio 不进 hidden，提交时按选择单独加
            }
            if ("button".equals(type) || "submit".equals(type) || "reset".equals(type)) continue;
            f.hidden.add(new String[]{name, val});
            Matcher fm = FZ.matcher(name);
            if (fm.matches()) {
                try {
                    f.fz.put(fm.group(1) + "|" + fm.group(2), Double.parseDouble(val));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return f;
    }

    private Map<String, String> params(String url) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = PARAM.matcher(url);
        while (m.find()) map.put(m.group(1), m.group(2));
        return map;
    }

    private String text(List<Element> tds, int i) {
        return i < tds.size() ? tds.get(i).text().trim() : null;
    }

    private boolean hasYes(List<Element> tds, int i) {
        String s = text(tds, i);
        return s != null && s.contains("是");
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
