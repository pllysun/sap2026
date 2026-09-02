package com.sap.jw.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 一项自定义教学评价答案。按题型使用对应字段。 */
@Data
public class EvalAnswerDTO {
    private Long indexId;
    private BigDecimal score;
    private String text;
    private Long optionId;
    private List<Long> optionIds;
    /** 填空题的各空内容。 */
    private List<String> values;
}
