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

    @PostMapping("/register")
    @OperationLog("用户注册")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.ok("注册成功");
    }

    @GetMapping("/info")
    @OperationLog("获取当前用户信息")
    public Result<?> info() {
        return Result.ok(authService.getCurrentUser());
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
