package com.sap.jw.dto;

import lombok.Data;

import java.util.List;

/** 自定义提交一门课程的教学评价。 */
@Data
public class EvalSubmitDTO {
    private String account;
    private Long taskId;
    private Long courseId;
    private List<EvalAnswerDTO> answers;
}
