package com.sap.dto.judger;

import lombok.Data;
import java.util.*;

/** Administrative import format. Never serialize this object in a user response. */
@Data
public class ProblemPack {
    private String slug, title, difficulty, description, inputFormat, outputFormat, constraints;
    private String sourcePlatform, sourceUrl, sourceId, sourceNote;
    private List<String> tags = new ArrayList<>();
    private List<String> modes = new ArrayList<>(List.of("STDIO"));
    private String defaultMode = "STDIO", checker = "TOKENS";
    private Map<String, Profile> profiles = new LinkedHashMap<>();
    private List<TestCase> cases = new ArrayList<>();
    private Map<String, Map<String, String>> references = new LinkedHashMap<>();

    @Data public static class Profile {
        private String starterStdio = "", starterFunction = "", functionDriver = "";
    }
    @Data public static class TestCase {
        private String name, input, expectedOutput;
        private boolean sample;
    }
}
