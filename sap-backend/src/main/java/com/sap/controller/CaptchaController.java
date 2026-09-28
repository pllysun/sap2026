package com.sap.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.sap.common.Result;
import com.sap.service.CaptchaService;
import com.sap.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 免登录获取一次性验证码，供注册及密码找回使用；图片禁止缓存并绑定请求来源。
 */
@RestController
@RequestMapping("/api/auth")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    /** @SaIgnore：注册前未登录即可获取，故跳过登录校验（无需改 WebMvcConfig 放行清单）。 */
    @SaIgnore
    @GetMapping("/captcha")
    public Result<?> captcha(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        return Result.ok(captchaService.generate(IpUtil.clientIp(request)));
    }
}
