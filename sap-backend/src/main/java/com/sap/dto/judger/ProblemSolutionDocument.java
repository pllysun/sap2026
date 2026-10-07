package com.sap.dto.judger;

import lombok.Data;
import java.util.*;

/** Versioned editorial content; never contains test cases or function drivers. */
@Data
public class ProblemSolutionDocument {
    private int schemaVersion = 1;
    private String problemSlug, title, summary;
    private Long problemRevision;
    private List<Section> sections = new ArrayList<>();
    private Map<String, Map<String, String>> codes = new LinkedHashMap<>();
    @Data public static class Section {
        private String key, title, markdown;
        private Diagram diagram;
    }
    @Data public static class Diagram { private String caption, svg; }
}
