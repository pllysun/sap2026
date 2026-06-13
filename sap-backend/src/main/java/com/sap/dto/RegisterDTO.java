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
}
