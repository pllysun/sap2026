package com.sap.vo;

import lombok.Data;

import java.util.Map;

/** 管理端“软协课表”专属页概览。 */
@Data
public class AppFeedbackSummaryVO {
    private int days;
    private long scheduleUsers;
    private long totalIssues;
    private long openIssues;
    private long closedIssues;
    private Map<String, Long> categoryCounts;
    private AppVersionVO latestVersion;
}
