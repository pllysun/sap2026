package com.sap.jw.vo;

import lombok.Data;

/** 新教学质量保障系统中的一门待评/已评课程。 */
@Data
public class EvalTaskVO {
    /** 任务名称（兼容旧客户端的 term 字段）。 */
    private String term;
    /** 平台评教任务 id。 */
    private Long taskId;
    /** 学生课程记录 id，单门评价的稳定标识。 */
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String classNo;
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
    /** 平台状态：0 未评价、1 已评价、2 评价中。 */
    private Integer status;
    private String statusText;
    /** 旧强智字段，保留仅为旧客户端 JSON 兼容。 */
    private String editUrl;
    /** 兼容旧客户端：值等于 courseId。 */
    private String jx0404id;
}
