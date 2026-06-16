package com.sap.jw.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 课表整体结果 */
@Data
public class ScheduleVO {
    /** 当前学期，如 "2025-2026-2" */
    private String term;
    /** 开学日期(第1周周一，ISO 如 "2026-03-09")，来自教学周历；取不到为 null（仅当前学期填充）。 */
    private String semesterStartDate;
    /** 可选学期列表 */
    private List<TermVO> terms = new ArrayList<>();
    /** 星期表头，如 [星期一 … 星期日] */
    private List<String> weekdays = new ArrayList<>();
    /** 课程列表 */
    private List<CourseVO> courses = new ArrayList<>();
    /** 备注（无固定时间格的实验/实习/集中实践课） */
    private List<RemarkVO> remarks = new ArrayList<>();
}
