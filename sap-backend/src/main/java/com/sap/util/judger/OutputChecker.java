package com.sap.util.judger;

import java.util.*;

public final class OutputChecker {
    private OutputChecker() {}
    public static boolean matches(String actual, String expected, String checker) {
        List<String> a = tokens(actual), b = tokens(expected);
        if ("UNORDERED_TOKENS".equals(checker)) {
            Collections.sort(a); Collections.sort(b);
        }
        return a.equals(b);
    }
    private static List<String> tokens(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.strip().split("\\s+")));
    }
}
