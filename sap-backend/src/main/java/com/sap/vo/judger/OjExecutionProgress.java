package com.sap.vo.judger;

import java.util.List;
import java.util.Map;

/** A cumulative snapshot allows slow clients and reconnects to skip intermediate events. */
public record OjExecutionProgress(String streamId, long sequence, Long jobId, String stage,
    String status, int attempt, int caseIndex, int completedCases, int passedCases, int totalCases,
    String caseVerdict, long updatedAt, Map<String,Long> timings, List<CaseState> cases) {
    public record CaseState(int index, String verdict) {}
    public boolean terminal() { return "FINISHED".equals(stage); }
}
