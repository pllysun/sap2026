package com.sap.jw.vo;

import lombok.Data;

import java.util.List;

/** 评教总览：某学期(批次)下的全部评教任务 + 可切换的学期列表。 */
@Data
public class EvalOverviewVO {
    /** 当前展示的学年学期 */
    private String term;
    /** 全部有评教批次的学年学期（供切换），新→旧 */
    private List<String> terms;
    /** 该学期下的全部评教任务（已评 + 未评） */
    private List<EvalTaskVO> tasks;
}
