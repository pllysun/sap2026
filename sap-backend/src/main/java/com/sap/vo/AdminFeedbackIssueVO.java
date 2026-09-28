package com.sap.vo;

/** 联系信息只由鉴权后的管理端专用接口返回，App 共用视图不包含这些字段。 */
public class AdminFeedbackIssueVO extends FeedbackIssueVO {
    private String reporterAccount;
    private String reporterQq;
    public String getReporterAccount() { return reporterAccount; }
    public void setReporterAccount(String value) { reporterAccount = value; }
    public String getReporterQq() { return reporterQq; }
    public void setReporterQq(String value) { reporterQq = value; }
}
