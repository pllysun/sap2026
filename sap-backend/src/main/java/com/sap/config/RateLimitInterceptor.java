package com.sap.config;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.service.RateLimiterService;
import com.sap.service.RegistrationProtectionSettingsService;
import com.sap.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

/**
 * 全局限流拦截器：按 URI+方法归类，取桶键（登录/注册按 IP；其余优先按登录用户），向
 * {@link RateLimiterService} 取令牌。超限默认返回 429（业务体 code=429），灰度模式只记日志放行。
 * <p>未归类的请求（普通 GET 读接口等）一律放行，避免误伤管理端首页一次性多请求等正常突发。</p>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    @Autowired
    private RateLimiterService limiter;

    @Autowired
    private RateLimitProperties props;

    @Autowired
    private RegistrationProtectionSettingsService registrationSettings;

    enum Category {LOGIN, REGISTER, CAPTCHA, JW, PDF, DOWNLOAD, WRITE}

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Category c = categorize(request);
        if (c == null) return true; // 不限流的请求直接放行
        if (c == Category.REGISTER || c == Category.CAPTCHA) {
            return registrationLimit(request, response, c);
        }

        RateLimitProperties.Rule rule = ruleOf(c);
        String key = "rl:" + c.name().toLowerCase() + ":" + keyOf(request, c);
        double refillPerSec = rule.getRefillPerMinute() / 60.0;
        boolean allowed = limiter.tryAcquire(key, rule.getCapacity(), refillPerSec);
        if (allowed) return true;

        if (props.isDryRun()) {
            log.warn("[限流·灰度] 命中但未拦截 {} {} key={}", request.getMethod(), request.getRequestURI(), key);
            return true;
        }
        return reject(request, response, key);
    }

    private boolean registrationLimit(HttpServletRequest request, HttpServletResponse response, Category category) throws Exception {
        var policy = registrationSettings.current().requests();
        if (!policy.enabled()) return true;
        String key = "rl:" + category.name().toLowerCase() + ":" + keyOf(request, category);
        boolean allowed;
        if (category == Category.REGISTER) {
            allowed = limiter.tryAcquireEnforced(key, policy.registerCapacity(), policy.registerPerMinute() / 60.0);
        } else {
            allowed = limiter.tryAcquireEnforced(key, policy.captchaCapacity(), policy.captchaPerMinute() / 60.0)
                    && limiter.tryAcquireEnforced("rl:captcha:global", policy.captchaGlobalCapacity(), policy.captchaGlobalPerMinute() / 60.0);
        }
        return allowed || reject(request, response, key);
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, String key) throws Exception {
        log.warn("[限流] 拦截 {} {} key={}", request.getMethod(), request.getRequestURI(), key);
        // 沿用本系统约定：业务信号走 HTTP 200 + 响应体 code（与 401/403 一致），
        // 前端 request.js 对 code!==200 既有处理会自动弹出 message，无需前端改动。
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":429,\"data\":null,\"message\":\"请求过于频繁，请稍后再试\"}");
        return false;
    }

    /** 路径+方法 → 限流类别；返回 null 表示该请求不限流。 */
    Category categorize(HttpServletRequest req) {
        if (req.getRequestURI() == null) return null;
        // 与 MVC 一致去掉 context path / 矩阵参数并解码，避免同一接口换路径写法绕过分类。
        String uri = UrlPathHelper.defaultInstance.getPathWithinApplication(req);
        String m = req.getMethod();
        if (uri == null) return null;
        if ("OPTIONS".equalsIgnoreCase(m)) return null; // CORS 预检不限流
        if (uri.equals("/api/auth/captcha")) return Category.CAPTCHA;
        if (uri.startsWith("/api/auth/password-recovery/")) return Category.LOGIN;
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/admin/login") || uri.equals("/api/auth/app/login")) {
            return Category.LOGIN;
        }
        if (uri.equals("/api/auth/register") || uri.equals("/api/auth/app/register") || uri.equals("/api/auth/app/register/email-code")) return Category.REGISTER;
        if (uri.startsWith("/api/jw/")) return Category.JW;
        if (uri.startsWith("/api/note/") && uri.endsWith("/pdf")) return Category.PDF;
        if (uri.equals("/api/file/download") || uri.equals("/api/file/go")) return Category.DOWNLOAD;
        if (uri.startsWith("/api/")
                && ("POST".equalsIgnoreCase(m) || "PUT".equalsIgnoreCase(m)
                || "DELETE".equalsIgnoreCase(m) || "PATCH".equalsIgnoreCase(m))) {
            return Category.WRITE;
        }
        return null;
    }

    RateLimitProperties.Rule ruleOf(Category c) {
        return switch (c) {
            case LOGIN -> props.getLogin();
            case REGISTER, CAPTCHA -> throw new IllegalArgumentException("注册及验证码规则由数据库配置提供");
            case JW -> props.getJw();
            case PDF -> props.getPdf();
            case DOWNLOAD -> props.getDownload();
            case WRITE -> props.getWrite();
        };
    }

    /** 登录/注册按 IP；其余优先按登录用户，未登录退回 IP。 */
    String keyOf(HttpServletRequest req, Category c) {
        if (c == Category.LOGIN || c == Category.REGISTER || c == Category.CAPTCHA) {
            return "ip:" + IpUtil.clientIp(req);
        }
        try {
            if (StpUtil.isLogin()) return "u:" + StpUtil.getLoginIdAsLong();
        } catch (Exception ignore) {
            // 取登录态异常（无上下文/无效 token）→ 退回 IP
        }
        return "ip:" + IpUtil.clientIp(req);
    }
}
