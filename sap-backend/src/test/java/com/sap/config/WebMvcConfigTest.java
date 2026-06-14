package com.sap.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebMvcConfig 单元测试：用真实空 registry 调用各配置方法，覆盖且断言不抛异常。
 */
class WebMvcConfigTest {

    private WebMvcConfig newConfig() {
        WebMvcConfig config = new WebMvcConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins",
                "http://localhost:5173,http://127.0.0.1:5173");
        return config;
    }

    @Test
    void addCorsMappings_registersMappingWithoutError() {
        WebMvcConfig config = newConfig();
        CorsRegistry registry = new CorsRegistry();

        assertDoesNotThrow(() -> config.addCorsMappings(registry));
    }

    @Test
    void addInterceptors_registersSaInterceptorWithoutError() {
        WebMvcConfig config = newConfig();
        InterceptorRegistry registry = new InterceptorRegistry();

        assertDoesNotThrow(() -> config.addInterceptors(registry));
    }

    @Test
    void addResourceHandlers_registersUploadsHandlerWithoutError() {
        WebMvcConfig config = newConfig();
        StaticWebApplicationContext ctx = new StaticWebApplicationContext();
        ResourceHandlerRegistry registry =
                new ResourceHandlerRegistry(ctx, new MockServletContext());

        assertDoesNotThrow(() -> config.addResourceHandlers(registry));
    }
}
