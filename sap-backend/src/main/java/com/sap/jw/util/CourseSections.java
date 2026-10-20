package com.sap.jw.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** 学校的实际小节列表，独立于为旧客户端保留的大节序号。 */
public final class CourseSections {
    private CourseSections() {}

    public static List<Integer> parse(String value) {
        if (value == null || value.length() > 128) return List.of();
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[第节\\s\\[\\]()【】]", "")
                .replaceAll("[—–~～至到]", "-");
        if (text.isBlank()) return List.of();
        var nodes = new TreeSet<Integer>();
        for (String part : text.split("[,、;；]", -1)) {
            if (!part.matches("\\d{1,2}(?:-\\d{1,2})?")) return List.of();
            String[] ends = part.split("-");
            int start = Integer.parseInt(ends[0]), end = Integer.parseInt(ends[ends.length - 1]);
            if (start < 1 || end < start || end > 16) return List.of();
            for (int n = start; n <= end; n++) nodes.add(n);
        }
        return new ArrayList<>(nodes);
    }

    /** 历史“第N节”指大节；带范围的标签才指实际小节。 */
    public static List<Integer> fromRow(String section, int sectionIndex) {
        if (section != null && section.matches(".*[,，、\\-－—–~～至到].*")) {
            var nodes = parse(section);
            if (!nodes.isEmpty()) return nodes;
        }
        int start = Math.max(1, sectionIndex) * 2 - 1;
        return start <= 15 ? List.of(start, start + 1) : List.of();
    }
}
