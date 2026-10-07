package com.sap.config;

import com.sap.aspect.OperationLogAspect;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Covers denials before controller invocation; never buffers request/response bodies. */
@Component @RequiredArgsConstructor @Order(Ordered.HIGHEST_PRECEDENCE+20)
public class ApiAuditFilter extends OncePerRequestFilter {
    private final OperationLogAspect audit;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !path.startsWith("/api/")||path.startsWith("/api/file/uploads/")||ApiRequestPolicy.isHealthCheck(request)||"OPTIONS".equals(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        long start=System.nanoTime();
        try {chain.doFilter(request,response);}
        catch(ServletException|IOException|RuntimeException error) {request.setAttribute(OperationLogAspect.CODE,500);throw error;}
        finally {
            int code=request.getAttribute(OperationLogAspect.CODE) instanceof Number n?n.intValue():response.getStatus();
            audit.recordRequest(request,(System.nanoTime()-start)/1_000_000,code);
        }
    }
}
