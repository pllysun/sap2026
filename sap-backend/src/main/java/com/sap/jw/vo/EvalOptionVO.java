package com.sap.jw.vo;

import lombok.Data;

/** 单选/多选评价题的一个选项。 */
@Data
public class EvalOptionVO {
    private Long id;
    private String title;
    private Double score;
}
