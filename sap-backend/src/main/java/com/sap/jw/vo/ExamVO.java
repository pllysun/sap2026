package com.sap.jw.vo;

import lombok.Data;

/** 单场考试安排 */
@Data
public class ExamVO {
    /** 考试场次 */
    private String session;
    /** 课程编号 */
    private String courseNo;
    /** 课程名称 */
    private String courseName;
    /** 考试时间，如 "第15周 星期四01-02节" */
    private String time;
    /** 考场（可能多个，逗号分隔） */
    private String room;
    /** 座位号 */
    private String seat;
    /** 准考证号 */
    private String admissionTicket;
}
