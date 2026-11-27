package com.sap.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.mcp.AcademicMcpTools.Invocation;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration(proxyBeanMethods = false)
public class AcademicMcpConfiguration {
    private static final String PATH = "/mcp";

    @Bean
    HttpServletStatelessServerTransport academicMcpTransport(ObjectMapper json) {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .messageEndpoint(PATH)
                .maxRequestSize(64 * 1024)
                .contextExtractor(request -> {
                    Object invocation = request.getAttribute(Invocation.ATTRIBUTE);
                    return invocation == null ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of(Invocation.ATTRIBUTE, invocation));
                })
                .build();
    }

    @Bean(destroyMethod = "closeGracefully")
    McpStatelessSyncServer academicMcpServer(HttpServletStatelessServerTransport transport,
                                          AcademicMcpTools tools, ObjectMapper json) {
        return McpServer.sync(transport)
                .serverInfo("sap-academic", "1.0.0")
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .validateToolInputs(true)
                .tools(tools.definitions())
                .addToolFilter(tools::isVisible)
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStatelessServerTransport> academicMcpServlet(HttpServletStatelessServerTransport transport) {
        return new ServletRegistrationBean<>(transport, PATH);
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> academicMcpAuthentication(ObjectProvider<McpKeyService> keys) {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                // This endpoint serves native/remote agents. Browser requests have no allowed origin.
                if (request.getHeader(HttpHeaders.ORIGIN) != null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                McpKeyService.Principal principal;
                try {
                    principal = keys.getObject().authenticate(request.getHeader(HttpHeaders.AUTHORIZATION));
                } catch (BusinessException e) {
                    if (e.getCode() == HttpServletResponse.SC_UNAUTHORIZED) {
                        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"sap-mcp\"");
                    }
                    response.sendError(e.getCode());
                    return;
                }
                var invocation = new Invocation(principal, new AtomicBoolean());
                request.setAttribute(Invocation.ATTRIBUTE, invocation);
                // Preserve the SDK's JSON-RPC response body while reporting the HTTP quota status.
                chain.doFilter(request, new HttpServletResponseWrapper(response) {
                    @Override
                    public void setStatus(int status) {
                        super.setStatus(status == SC_OK && invocation.rateLimited().get() ? 429 : status);
                    }
                });
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.addUrlPatterns(PATH);
        return registration;
    }
}
