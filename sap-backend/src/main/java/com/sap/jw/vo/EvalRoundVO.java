package com.sap.jw.vo;

import lombok.Data;

/** 一轮教学评价任务。 */
@Data
public class EvalRoundVO {
    private Long id;
    private String name;
    private String startTime;
    private String endTime;
    private String status;
}
