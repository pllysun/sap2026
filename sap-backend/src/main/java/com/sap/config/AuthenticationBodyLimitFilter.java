package com.sap.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** 在 JSON 反序列化及鉴权前约束匿名认证、节点心跳的内存占用，包含 chunked 请求。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationBodyLimitFilter extends OncePerRequestFilter {
    static final int MAX_BYTES = 16 * 1024;

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !(path.matches("/api/auth/(?:login|admin/login|app/login|(?:app/)?register(?:/email-code)?|password-recovery/(?:send|reset))")
                || path.matches("/api/oj-nodes/[^/]+/heartbeat"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) { reject(response); return; }
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("同步认证请求"); }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":413,\"message\":\"请求内容过大\",\"data\":null}");
    }
}
