package com.sap.service.judger;

import java.util.Map;

/** Bounds retained display strings; answer checking always uses original output. */
final class JudgeOutputBudget {
    static final int LIMIT = 2 * 1024 * 1024;
    static final int NAME_LIMIT = 64 * 1024;
    private int remaining;
    private boolean truncated;

    JudgeOutputBudget() { this(LIMIT); }
    JudgeOutputBudget(int limit) { remaining = limit; }

    void put(Map<String,Object> view, String key, String value) {
        if (value == null) { view.put(key,null); return; }
        int end = 0;
        while (end < value.length()) {
            int point = value.codePointAt(end);
            // Bound JSON-escaped UTF-8, including control characters and emoji.
            int cost = point == '"' || point == '\\' ? 2 : point < 32 || (point >= 0xd800 && point <= 0xdfff) ? 6 :
                point < 128 ? 1 : point < 2048 ? 2 : point < 65536 ? 3 : 4;
            if (cost > remaining) break;
            remaining -= cost;
            end += Character.charCount(point);
        }
        view.put(key,end == value.length() ? value : value.substring(0,end));
        if (end != value.length()) {
            view.put(key+"Truncated",true);
            truncated = true;
        }
    }

    boolean isTruncated() { return truncated; }
}
