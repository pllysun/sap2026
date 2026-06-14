package com.sap.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 允许跨域的来源（逗号分隔的 allowedOriginPatterns），可用 app.cors.allowed-origins 覆盖。
     * 默认 "*"：放行所有来源。本系统鉴权 token 走自定义请求头(sap-token)而非 Cookie，
     * 浏览器不会自动携带凭证，故跨域并非其 CSRF 防线，"*" 在该鉴权模型下是可接受的。
     * 注意：默认值不能是写死的几个域名——否则浏览器对同源 POST 携带的 Origin 头
     * 与白名单不匹配时，Spring 会直接返回 403 "Invalid CORS request"，导致生产登录失败。
     * 安全要求更高的部署可显式配置 app.cors.allowed-origins 为真实域名以收紧。
     */
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/admin/login",
                        "/api/auth/register",
                        "/api/file/uploads/**",
                        "/api/setting/public",
                        "/api/log/public/**",
                        "/api/join/status"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/file/uploads/**")
                .addResourceLocations("file:./uploads/");
    }
}
