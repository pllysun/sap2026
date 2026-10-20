package io.sapoj.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.sapoj.common.BusinessException;
import io.sapoj.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.List;

@Configuration @RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final UserMapper users;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
            StpUtil.checkLogin();
            var u = users.selectById(StpUtil.getLoginIdAsLong());
            if (u == null || !Integer.valueOf(1).equals(u.getStatus()))
                throw new BusinessException(403, "账号不可用");
        })).addPathPatterns("/api/**").excludePathPatterns("/api/ping", "/api/public/config",
            "/api/auth/login", "/api/auth/admin/login", "/api/auth/register", "/api/oj-nodes/*/heartbeat");
    }
    @Bean public StpInterface roles() {
        return new StpInterface() {
            public List<String> getPermissionList(Object id, String type) { return List.of(); }
            public List<String> getRoleList(Object id, String type) {
                var u = users.selectById(Long.valueOf(id.toString()));
                return u == null || !Integer.valueOf(1).equals(u.getStatus()) ? List.of() : List.of(u.getRoleCode().toString());
            }
        };
    }
}
