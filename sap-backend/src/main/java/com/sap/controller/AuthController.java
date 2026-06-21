package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.LoginDTO;
import com.sap.dto.RegisterDTO;
import com.sap.service.AuthService;
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
    public Result<?> register(@Valid @RequestBody RegisterDTO dto,
                              jakarta.servlet.http.HttpServletRequest request) {
        // 风控触发式验证码：默认不要求；仅当该 IP 注册数超过宽松阈值时才要验证码。
        String ip = clientIp(request);
        if (captchaService.captchaRequired(ip)) {
            boolean blank = dto.getCaptchaId() == null || dto.getCaptchaId().isBlank()
                    || dto.getCaptchaCode() == null || dto.getCaptchaCode().isBlank();
            if (blank) {
                // 非错误：告诉前端"请展示验证码后重提"，前端据 captchaRequired 弹出验证码
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("captchaRequired", true);
                return Result.ok(data);
            }
            if (!captchaService.verify(dto.getCaptchaId(), dto.getCaptchaCode())) {
                return Result.error("验证码错误或已过期，请重试");
            }
        }
        authService.register(dto);
        captchaService.recordRegister(ip); // 注册成功后对该 IP 计数，驱动风控阈值
        return Result.ok("注册成功");
    }

    /** 取客户端 IP（经 nginx 反代取 X-Forwarded-For 首段，否则 RemoteAddr）。 */
    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
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
