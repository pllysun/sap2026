package com.sap.jw.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * 强智表格(table#dataList 等)的数据行抽取。
 * <p>强智不同页面表头 markup 不统一（成绩表无内联表头、考试表用 th），故统一只取
 * 数据行（含 td 的行），由各解析器按已知列位置取值。</p>
 */
public final class JwTable {

    private JwTable() {}

    /** 取表格的数据行（每行为单元格文本列表）。 */
    public static List<List<String>> rows(String html, String tableId) {
        List<List<String>> rows = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("#" + tableId);
        if (table == null) return rows;
        for (Element tr : table.select("tr")) {
            Elements tds = tr.select("td");
            if (tds.size() < 2) continue; // 跳过表头(th)行、空提示行(单格colspan)
            List<String> row = new ArrayList<>(tds.size());
            for (Element td : tds) row.add(clean(td.text()));
            rows.add(row);
        }
        return rows;
    }

    /** 安全按下标取值。 */
    public static String at(List<String> row, int index) {
        return (index < 0 || index >= row.size()) ? "" : row.get(index);
    }

    static String clean(String s) {
        return s == null ? "" : s.replace("\u00A0", "").replace("&nbsp;", "").trim();
    }
}
