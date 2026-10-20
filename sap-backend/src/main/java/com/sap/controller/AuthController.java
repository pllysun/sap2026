package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.LoginDTO;
import com.sap.dto.AppRegisterDTO;
import com.sap.service.AuthService;
import com.sap.service.RegistrationProtectionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private com.sap.service.CaptchaService captchaService;

    @Autowired
    private RegistrationProtectionService registrationProtection;

    @Autowired
    private com.sap.service.AppRegistrationService emailRegistration;

    @GetMapping("/register/policy")
    public Result<?> registrationPolicy(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return Result.ok(emailRegistration.policy());
    }

    @PostMapping("/register/email-code")
    public Result<?> registrationEmailCode(@Valid @RequestBody AppRegistrationController.EmailCodeRequest dto,
                                          jakarta.servlet.http.HttpServletRequest request) {
        return Result.ok(emailRegistration.request(dto.studentId(), dto.name(), dto.qq(), dto.captchaId(), dto.captchaCode(), clientIp(request)));
    }

    @PostMapping("/admin/login")
    @OperationLog("管理端登录")
    public Result<?> adminLogin(@Valid @RequestBody LoginDTO dto) {
        Map<String, Object> result = authService.adminLogin(dto);
        return Result.ok(result);
    }

    @PostMapping("/login")
    @OperationLog("用户端登录")
    public Result<?> login(@Valid @RequestBody LoginDTO dto) {
        Map<String, Object> result = authService.login(dto);
        return Result.ok(result);
    }

    @PostMapping("/app/login")
    @OperationLog("App端登录")
    public Result<?> appLogin(@Valid @RequestBody LoginDTO dto) {
        Map<String, Object> result = authService.appLogin(dto);
        return Result.ok(result);
    }

    @PostMapping("/register")
    @OperationLog("用户注册")
    public Result<?> register(@Valid @RequestBody AppRegisterDTO dto,
                              jakarta.servlet.http.HttpServletRequest request) {
        // Web 与 App 共用服务端验证策略，入会关闭时仍强制校验邮箱挑战。
        emailRegistration.register(dto, clientIp(request));
        return Result.ok("注册成功");
    }

    /** 取客户端 IP：统一走 {@link com.sap.util.IpUtil}（采信可信 X-Real-IP，避免 XFF 首段被伪造绕过风控）。 */
    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        return com.sap.util.IpUtil.clientIp(request);
    }

    @GetMapping("/info")
    @OperationLog("获取当前用户信息")
    public Result<?> info() {
        return Result.ok(authService.getCurrentUser());
    }

    /** 轻量用户信息：不含头像 + 平台身份 + updatedAt 修改时间；App 据此判断是否需重新拉头像（省流量）。 */
    @GetMapping("/info/light")
    @OperationLog("获取当前用户信息(轻量·无头像)")
    public Result<?> infoLight() {
        return Result.ok(authService.getCurrentUserLight());
    }

    @PostMapping("/logout")
    @OperationLog("退出登录")
    public Result<?> logout() {
        StpUtil.logout();
        return Result.ok("退出成功");
    }

    @PutMapping("/profile")
    @OperationLog("修改个人信息")
    public Result<?> updateProfile(@RequestBody Map<String, Object> params) {
        authService.updateProfile(params);
        return Result.ok("更新成功");
    }
}
