package com.sap.vo.judger;

import lombok.Data;
import java.util.*;

@Data
public class JudgeResult {
    private String verdict = "AC", message = "通过", compilerOutput = "";
    private int passedCases, totalCases;
    private long timeMs, memoryBytes;
    private boolean outputTruncated;
    /** Contains outputs only for RUN jobs and administrative validation. */
    private List<Map<String,Object>> cases = new ArrayList<>();
}
