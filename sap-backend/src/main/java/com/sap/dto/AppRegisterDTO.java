package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 手机端必须提交 QQ 邮箱挑战；Web RegisterDTO 保持原有协议。 */
@Getter
@Setter
public class AppRegisterDTO extends RegisterDTO {
    @NotBlank(message = "请先获取邮箱验证码")
    @Pattern(regexp = "[0-9a-f-]{36}", message = "邮箱验证请求无效，请重新获取")
    private String emailRequestId;
    @NotBlank(message = "请输入邮箱验证码")
    @Pattern(regexp = "[0-9]{6}", message = "请输入六位邮箱验证码")
    private String emailCode;
}
