package com.sap.jw.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 强智「教学周历查看」(/jsxsd/jxzl/jxzl_query) 解析器。
 * <p>表格每行：周次 + 周一..周日(+备注)；周末列稳定带 "MM月DD日" 全日期。
 * 取最早一个带全日期的格子，按其(周次,星期)反推第 1 周周一 = 开学日期。</p>
 */
@Component
public class CalendarParser {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CalendarParser.class);

    private static final Pattern FULL_DATE = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern TERM = Pattern.compile("(\\d{4})-(\\d{4})-(\\d)");
    private static final Pattern WEEK_NO = Pattern.compile("^\\s*(\\d{1,2})\\s*$");

    /**
     * @param html jxzl_query 页面 HTML
     * @param term 期望学期(如 2025-2026-2)；非空时若页面实际选中学期不一致则返回 null(避免算错)
     * @return 第 1 周周一 ISO 日期(如 "2026-03-09")，解析不出返回 null
     */
    public String parseSemesterStart(String html, String term) {
        if (html == null || html.isBlank()) return null;
        Document doc = Jsoup.parse(html);

        String pageTerm = detectTerm(doc);
        // 若指定了期望学期，但页面没切到它(参数被忽略) → 放弃，避免用错年份反推出错误日期
        if (term != null && TERM.matcher(term).find() && pageTerm != null
                && !norm(pageTerm).equals(norm(term))) {
            log.info("[教学周历] 页面学期={} 与请求={} 不符（切学期参数可能无效），跳过以免算错年份", pageTerm, term);
            return null;
        }
        String useTerm = (term != null && TERM.matcher(term).find()) ? term : pageTerm;
        if (useTerm == null) return null;
        Matcher tm = TERM.matcher(useTerm);
        if (!tm.find()) return null;
        int y1 = Integer.parseInt(tm.group(1));
        int y2 = Integer.parseInt(tm.group(2));

        Element table = findCalendarTable(doc);
        if (table == null) {
            log.warn("[教学周历] 未找到周历表格（页面可能非周历页/需登录/编码异常）");
            return null;
        }

        for (Element row : table.select("tr")) {
            Elements tds = row.select("td");
            if (tds.size() < 8) continue;                       // 周次 + 7 天(+备注)
            Matcher wm = WEEK_NO.matcher(tds.get(0).text());
            if (!wm.matches()) continue;                        // 跳过表头/页脚行
            int week = Integer.parseInt(wm.group(1));
            for (int col = 1; col <= 7 && col < tds.size(); col++) {  // col: 1=周一 … 7=周日
                Matcher dm = FULL_DATE.matcher(tds.get(col).text());
                if (!dm.find()) continue;
                int month = Integer.parseInt(dm.group(1));
                int day = Integer.parseInt(dm.group(2));
                int year = (month >= 8) ? y1 : y2;              // 8月及以后=上学年起始年；否则次年(春季/跨年1月)
                try {
                    LocalDate cell = LocalDate.of(year, month, day);
                    return cell.minusDays((long) (week - 1) * 7 + (col - 1)).toString();
                } catch (Exception ignore) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 含"周次/星期"表头的那张表。 */
    private Element findCalendarTable(Document doc) {
        for (Element table : doc.select("table")) {
            String txt = table.text();
            if (txt.contains("周次") || (txt.contains("星期一") && txt.contains("星期日"))) return table;
        }
        return doc.selectFirst("table");
    }

    /** 读页面学期下拉的选中项(形如 2025-2026-2)。 */
    private String detectTerm(Document doc) {
        for (Element op : doc.select("option[selected]")) {
            String v = op.attr("value");
            if (v != null && TERM.matcher(v).find()) return v.trim();
        }
        for (Element op : doc.select("select option")) {
            String v = op.attr("value");
            if (v != null && TERM.matcher(v.trim()).matches()) return v.trim();
        }
        return null;
    }

    private static String norm(String t) {
        return t == null ? null : t.trim();
    }
}
