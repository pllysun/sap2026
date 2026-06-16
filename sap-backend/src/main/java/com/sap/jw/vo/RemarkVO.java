package com.sap.jw.vo;

import lombok.Data;

/**
 * 课表“备注”里的一条（多为无固定时间格的实验/实习/集中实践课）。
 * 原文形如：`课程名 [教师] 周次 班级`，如 "生产实习(计算机科学与技术) 邝祝芳 15-16周 2023计算机科学与技术3班"。
 */
@Data
public class RemarkVO {
    /** 课程名 */
    private String name;
    /** 教师（可空，部分条目无教师） */
    private String teacher;
    /** 周次，如 "1-8,10-17周" */
    private String weeks;
    /** 班级/班号，如 "2023计算机科学与技术3班" */
    private String clazz;
    /** 原始整条文本 */
    private String raw;
}
