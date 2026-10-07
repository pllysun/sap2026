package com.sap.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/** Shared exclusion for infrastructure probes, not business API requests. */
public final class ApiRequestPolicy {
    private ApiRequestPolicy() { }

    public static boolean isHealthCheck(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null && "/api/ping".equals(pattern.toString())) return true;
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (path != null && context != null && !context.isEmpty() && path.startsWith(context + "/")) {
            path = path.substring(context.length());
        }
        return "/api/ping".equals(path);
    }
}
