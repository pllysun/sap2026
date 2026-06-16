package com.sap.jw.vo;

import lombok.Data;

/** 一条评教自动提交的结果。 */
@Data
public class EvalResultVO {
    private String teacher;
    private String typeName;
    /** 是否提交成功 */
    private boolean success;
    /** 失败原因 / 成功提示 */
    private String message;
    /** 本次评教的总评分（成功时） */
    private String score;
}
