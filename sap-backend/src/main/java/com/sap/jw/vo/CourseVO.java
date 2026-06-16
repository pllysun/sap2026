package com.sap.jw.vo;

import lombok.Data;

/** 单门课程（课表里的一格内容） */
@Data
public class CourseVO {
    /** 星期：1=周一 … 7=周日 */
    private int day;
    /** 星期名，如 "星期一" */
    private String dayName;
    /** 节次标签，如 "第1,2节" */
    private String section;
    /** 节次序号：1..6（第1,2节=1，第3,4节=2 …） */
    private int sectionIndex;
    /** 课程名（不含[学时类型]） */
    private String name;
    /** 学时类型，如 "讲课学时"/"实验学时"/"实践学时" */
    private String type;
    /** 教师 */
    private String teacher;
    /** 周次，如 "1-8,10-11(周)" */
    private String weeks;
    /** 教室 */
    private String room;
}
