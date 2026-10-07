package com.sap.vo.judger;

import java.util.Map;

public record ProblemSetScore(int acCount, long penalty, long lastAcAt, Map<Long, Cell> cells) {
    public record Cell(boolean accepted, int wrong, int pending, Long acceptedAt, long elapsedMinutes) {}
}
