package com.sap.jw.vo;

import lombok.Data;

/** 单门课程成绩 */
@Data
public class GradeVO {
    /** 开课学期，如 "2023-2024-1" */
    private String term;
    /** 课程编号 */
    private String courseNo;
    /** 课程名称 */
    private String courseName;
    /** 成绩（分数或等级） */
    private String score;
    /** 学分 */
    private String credit;
    /** 总学时 */
    private String hours;
    /** 绩点 */
    private String gradePoint;
    /** 成绩标志（重修/缓考等，正常为空） */
    private String flag;
    /** 考核方式（考试/考查） */
    private String assessMethod;
    /** 考试性质（正常考试/补考等） */
    private String examNature;
    /** 课程属性（必修/选修） */
    private String courseAttr;
    /** 课程性质（公共课/专业课等） */
    private String courseNature;
}
