package com.sap.vo.judger;

/** Execution metadata only; never includes source, inputs, outputs or test names. */
public record JudgeStage(String stage, int caseIndex, int completedCases, int passedCases,
                         int totalCases, String caseVerdict) {}
