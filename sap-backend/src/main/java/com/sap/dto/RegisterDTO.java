package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "学号需为1-20位字母、数字、下划线或短横线")
    private String studentId;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为6-64位")
    private String password;
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50位")
    private String name;
    @Size(max = 50, message = "昵称不能超过50位")
    private String nickname;
    @Min(value = 0, message = "性别参数无效")
    @Max(value = 1, message = "性别参数无效")
    private Integer gender;
    @NotBlank(message = "QQ号不能为空")
    @Pattern(regexp = "[1-9][0-9]{4,14}", message = "请输入5-15位有效QQ号")
    private String qq;

    /** 接口返回 captchaRequired 后回填，默认所有注册均需验证。 */
    @Size(max = 64, message = "验证码标识过长")
    private String captchaId;
    @Size(max = 8, message = "验证码过长")
    private String captchaCode;
}
