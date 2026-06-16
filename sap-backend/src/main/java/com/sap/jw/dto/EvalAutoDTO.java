package com.sap.jw.dto;

import lombok.Data;

/** 自动评教请求。 */
@Data
public class EvalAutoDTO {
    /** 教务学号；省略取默认 */
    private String account;
    /** 评教学期；省略取最新有评教任务的学期 */
    private String term;
    /** 固定评语（须≥30汉字）；省略用后端默认评语 */
    private String comment;
}
