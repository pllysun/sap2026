package com.sap.config;

import com.sap.service.TrafficService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 全局接口请求计数拦截器：覆盖全部 /api/**（含未加 @OperationLog 注解的接口）。
 * <p>仅在 afterCompletion 按 规范化路由模式(如 /api/user/{id}) × 当前用户 原子自增计数；
 * 全程 try-catch，绝不影响主请求。</p>
 */
@Component
public class ApiStatInterceptor implements HandlerInterceptor {

    @Autowired
    private TrafficService trafficService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String method = request.getMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) return; // 跳过 CORS 预检

            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern == null) return; // 无匹配处理器(静态/404)，跳过
            String endpoint = pattern.toString();

            // 排除统计自身，避免打开统计页时被反复计数造成自激增长
            if (endpoint.startsWith("/api/stats")) return;

            trafficService.recordApiRequest(endpoint, method);
        } catch (Exception ignore) {
            // 埋点失败不影响主请求
        }
    }
}
