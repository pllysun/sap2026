package com.sap.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.sap.common.Result;
import com.sap.service.CaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册风控验证码：免登录获取图形验证码。
 * <p>默认前端<b>不展示</b>验证码，仅当 /api/auth/register 返回 {@code captchaRequired=true}
 * （该 IP 注册数超过宽松阈值）时，前端才来此拉取一张验证码后让用户填写重提。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    /** @SaIgnore：注册前未登录即可获取，故跳过登录校验（无需改 WebMvcConfig 放行清单）。 */
    @SaIgnore
    @GetMapping("/captcha")
    public Result<?> captcha() {
        return Result.ok(captchaService.generate());
    }
}
