package com.sap.jw.vo;

import lombok.Data;

import java.util.List;

/** 教学质量保障系统评教总览。 */
@Data
public class EvalOverviewVO {
    /** 当前任务名称；兼容旧客户端的 term 字段。 */
    private String term;
    /** 全部任务名称；兼容旧客户端的学期选择器。 */
    private List<String> terms;
    private Long taskId;
    private String taskName;
    private String startTime;
    private String endTime;
    private String status;
    private boolean restrictHighest;
    private boolean restrictLowest;
    private List<EvalRoundVO> rounds;
    /** 当前任务下的课程评价（已评 + 未评）。 */
    private List<EvalTaskVO> tasks;
}
