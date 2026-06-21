package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "学号不能为空")
    private String studentId;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为6-64位")
    private String password;
    @NotBlank(message = "姓名不能为空")
    private String name;
    private String nickname;
    private Integer gender;
    @NotBlank(message = "QQ号不能为空")
    private String qq;

    /** 风控验证码标识（仅当注册接口返回 captchaRequired 时由前端回填，正常注册留空）。 */
    private String captchaId;
    /** 风控验证码用户输入值（同上，正常注册留空）。 */
    private String captchaCode;
}
