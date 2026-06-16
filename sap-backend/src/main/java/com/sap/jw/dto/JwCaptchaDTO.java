package com.sap.jw.dto;

import lombok.Data;

/** 人工验证码续登请求体。 */
@Data
public class JwCaptchaDTO {
    private String challengeId;
    private String code;
}
