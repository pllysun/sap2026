package com.sap.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 验证方式由服务端入会开关决定；邮箱字段在邮箱验证模式下由服务层校验必填。 */
@Getter
@Setter
public class AppRegisterDTO extends RegisterDTO {
    @Pattern(regexp = "(?:[0-9a-f-]{36})?", message = "邮箱验证请求无效，请重新获取")
    private String emailRequestId;
    @Pattern(regexp = "(?:[0-9]{6})?", message = "请输入六位邮箱验证码")
    private String emailCode;
}
