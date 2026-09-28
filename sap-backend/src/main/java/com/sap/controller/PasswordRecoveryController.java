package com.sap.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.sap.common.Result;
import com.sap.service.PasswordRecoveryService;
import com.sap.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password-recovery")
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;
    public PasswordRecoveryController(PasswordRecoveryService service) { this.service=service; }
    public record SendRequest(@NotBlank @Size(max=20) String account, @NotBlank @Size(max=64) String captchaId, @NotBlank @Size(max=8) String captcha) {}
    public record ResetRequest(@NotBlank @Size(max=64) String requestId,@Pattern(regexp="[0-9]{6}") @NotNull String code,@NotNull @Size(min=6,max=64) String newPassword) {}
    // 不使用记录请求参数的操作日志切面；业务层仅记录不含秘密的结果。
    @SaIgnore @PostMapping("/send")
    public Result<?> send(@Valid @RequestBody SendRequest dto,HttpServletRequest request) {
        return Result.ok(service.request(dto.account(),dto.captchaId(),dto.captcha(),IpUtil.clientIp(request)));
    }
    @SaIgnore @PostMapping("/reset")
    public Result<?> reset(@Valid @RequestBody ResetRequest dto) {
        service.reset(dto.requestId(),dto.code(),dto.newPassword());
        return Result.ok("密码已重置，请使用新密码重新登录");
    }
}
