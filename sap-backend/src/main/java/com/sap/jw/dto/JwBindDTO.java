package com.sap.jw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 绑定学校教务账号请求 */
@Data
public class JwBindDTO {
    @NotBlank(message = "学校账号不能为空")
    @jakarta.validation.constraints.Size(max = 64)
    private String account;

    @NotBlank(message = "学校密码不能为空")
    @jakarta.validation.constraints.Size(max = 256)
    private String password;
}
