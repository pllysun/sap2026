package com.sap.jw.dto;

import lombok.Data;

/** 人工验证码续登请求体。 */
@Data
public class JwCaptchaDTO {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 64)
    private String challengeId;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 16)
    private String code;
}
