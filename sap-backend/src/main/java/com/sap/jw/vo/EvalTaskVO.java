package com.sap.jw.vo;

import lombok.Data;

/** 一条学生评教任务（某学期某批次下、对某教师某教学班的评价）。 */
@Data
public class EvalTaskVO {
    /** 学年学期，如 "2023-2024-1" */
    private String term;
    /** 教师工号 */
    private String teacherNo;
    /** 教师姓名 */
    private String teacher;
    /** 开课/教师所属学院 */
    private String college;
    /** 评价类型名（理论课程评价 / 实践课评价） */
    private String typeName;
    /** 总评分（已评才有；未评为空） */
    private String score;
    /** 是否已评 */
    private boolean evaluated;
    /** 是否已提交（提交后不可改） */
    private boolean submitted;
    /** 进入评教表单的相对路径（xspj_edit.do?...），自动评教/查看详情均用它 */
    private String editUrl;
    /** 教学班 id（jx0404id），任务唯一标识 */
    private String jx0404id;
}
