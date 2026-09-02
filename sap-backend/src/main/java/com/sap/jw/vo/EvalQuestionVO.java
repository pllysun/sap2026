package com.sap.jw.vo;

import lombok.Data;

import java.util.List;

/** 教学评价表中的一项指标或开放题。 */
@Data
public class EvalQuestionVO {
    private Long indexId;
    private Integer order;
    private String section;
    private String type;
    private String title;
    private String remark;
    private boolean required;
    private boolean scored;
    private Double maxScore;
    /** 0 小数、1 正整数、2 含 0 整数。 */
    private Integer scoringType;
    private List<EvalOptionVO> options;
}
