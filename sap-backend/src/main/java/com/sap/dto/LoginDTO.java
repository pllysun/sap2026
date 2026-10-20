package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "学号不能为空")
    @jakarta.validation.constraints.Size(max = 64)
    private String studentId;
    @NotBlank(message = "密码不能为空")
    @jakarta.validation.constraints.Size(max = 256)
    private String password;
}
