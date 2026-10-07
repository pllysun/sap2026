package com.sap.vo.judger;

import lombok.Data;

/** Aggregate of formal submissions; contains no submitted code or private test data. */
@Data
public class OjProgressSummary {
    private Long problemId;
    private Integer accepted;
    private Integer pending;
    private String lastStatus;
}
