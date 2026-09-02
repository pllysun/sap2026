package com.sap.jw.vo;

import lombok.Data;

import java.util.List;

/** 单门课程的可填写教学评价表。 */
@Data
public class EvalFormVO {
    private Long taskId;
    private String taskName;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String teacherNo;
    private String teacher;
    private String typeName;
    private boolean restrictHighest;
    private boolean restrictLowest;
    private Double maxTotal;
    private String defaultComment;
    private List<EvalQuestionVO> questions;
}
