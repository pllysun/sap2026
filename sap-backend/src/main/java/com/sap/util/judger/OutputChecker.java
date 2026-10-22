package com.sap.util.judger;

import java.util.*;

public final class OutputChecker {
    public static final Set<String> TYPES = Set.of("TOKENS", "UNORDERED_TOKENS", "UNORDERED_LINES", "ANY_OF", "SPECIAL_JUDGE");
    private OutputChecker() {}
    public static boolean matches(String actual, String expected, String checker) {
        return matches(actual,expected,checker,List.of());
    }
    public static boolean matches(String actual,String expected,String checker,List<String> alternatives) {
        if ("UNORDERED_LINES".equals(checker)) return lines(actual).equals(lines(expected));
        if ("ANY_OF".equals(checker)) return tokens(actual).equals(tokens(expected)) ||
            alternatives!=null && alternatives.stream().anyMatch(value -> tokens(actual).equals(tokens(value)));
        if (!"TOKENS".equals(checker) && !"UNORDERED_TOKENS".equals(checker)) return false;
        List<String> a = tokens(actual), b = tokens(expected);
        if ("UNORDERED_TOKENS".equals(checker)) {
            Collections.sort(a); Collections.sort(b);
        }
        return a.equals(b);
    }
    private static List<String> lines(String value) {
        if (value==null || value.isBlank()) return List.of();
        return value.lines().filter(line -> !line.isBlank()).map(line -> String.join(" ",tokens(line))).sorted().toList();
    }
    private static List<String> tokens(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.strip().split("\\s+")));
    }
}
