package com.sap.jw.dto;

import lombok.Data;

/** 自动评教请求。 */
@Data
public class EvalAutoDTO {
    /** 教务学号；省略取默认 */
    @jakarta.validation.constraints.Size(max = 64)
    private String account;
    /** 评教学期；省略取最新有评教任务的学期 */
    @jakarta.validation.constraints.Size(max = 64)
    private String term;
    /** 新平台任务 id；优先于 term。 */
    private Long taskId;
    /** 固定评语；省略用后端默认评语。 */
    @jakarta.validation.constraints.Size(max = 2000)
    private String comment;
}
