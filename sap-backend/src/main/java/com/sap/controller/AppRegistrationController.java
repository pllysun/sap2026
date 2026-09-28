package com.sap.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.sap.common.Result;
import com.sap.dto.AppRegisterDTO;
import com.sap.service.AppRegistrationService;
import com.sap.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/app/register")
public class AppRegistrationController {
    private final AppRegistrationService service;
    public AppRegistrationController(AppRegistrationService service) { this.service = service; }

    public record EmailCodeRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,20}") String studentId,
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Pattern(regexp = "[1-9][0-9]{4,14}") String qq,
            @Size(max = 64) String captchaId,
            @Size(max = 8) String captchaCode) {}

    // 不使用记录原始参数的操作日志切面，密码及验证码不得进入日志。
    @SaIgnore @PostMapping("/email-code")
    public Result<?> emailCode(@Valid @RequestBody EmailCodeRequest dto, HttpServletRequest request) {
        return Result.ok(service.request(dto.studentId(), dto.name(), dto.qq(), dto.captchaId(), dto.captchaCode(), IpUtil.clientIp(request)));
    }

    @SaIgnore @PostMapping
    public Result<?> register(@Valid @RequestBody AppRegisterDTO dto, HttpServletRequest request) {
        service.register(dto, IpUtil.clientIp(request));
        return Result.ok("注册成功");
    }
}
