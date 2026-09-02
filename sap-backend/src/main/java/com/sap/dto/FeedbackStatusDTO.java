package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 管理端关闭或重新打开 Issue。 */
@Data
public class FeedbackStatusDTO {
    @NotBlank(message = "状态不能为空")
    private String status;
}
