package com.sap.jw.vo;

import lombok.Data;

/** 一条评教提交结果（手动与自动提交共用）。 */
@Data
public class EvalResultVO {
    private Long courseId;
    private String courseName;
    private String teacher;
    private String typeName;
    /** 是否提交成功 */
    private boolean success;
    /** 源站已经接收，但仍处于异步处理中；此时禁止立即重复提交。 */
    private boolean pending;
    /** 本次失败是否适合稍后重试。 */
    private boolean retryable;
    /** 一键评教因源站全局故障而主动跳过，未向源站发送该课程。 */
    private boolean skipped;
    /** 失败原因 / 成功提示 */
    private String message;
    /** 本次评教的总评分（成功时） */
    private String score;
}
