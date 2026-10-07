package com.sap.config;

import com.sap.annotation.OperationLog;
import com.sap.aspect.OperationLogAspect;
import com.sap.common.Result;
import com.sap.entity.SysLog;
import com.sap.mapper.*;
import com.sap.service.TrafficService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.HandlerMapping;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthCheckAuditTest extends com.sap.BaseUnitTest {
    private final SysLogMapper logs = mock(SysLogMapper.class);
    private final LogStatsMapper counts = mock(LogStatsMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final TrafficService traffic = mock(TrafficService.class);

    private OperationLogAspect aspect() {
        var aspect = new OperationLogAspect();
        ReflectionTestUtils.setField(aspect, "sysLogMapper", logs);
        ReflectionTestUtils.setField(aspect, "logStatsMapper", counts);
        ReflectionTestUtils.setField(aspect, "userMapper", users);
        return aspect;
    }
    private ApiStatInterceptor statistics() {
        var interceptor = new ApiStatInterceptor();
        ReflectionTestUtils.setField(interceptor, "trafficService", traffic);
        return interceptor;
    }

    @ParameterizedTest @ValueSource(strings = {"/api/ping", "/sap/api/ping"})
    void probesBypassAllAuditAndCountEntriesButStillExecute(String path) throws Throwable {
        var request = new MockHttpServletRequest("GET", path);
        if (path.startsWith("/sap/")) request.setContextPath("/sap");
        request.setQueryString("source=docker");
        request.setRemoteAddr("::1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/ping");
        var response = new MockHttpServletResponse();
        var audit = aspect(); var stats = statistics();
        assertTrue(stats.preHandle(request, response, null));
        var point = mock(ProceedingJoinPoint.class); var result = Result.ok("pong");
        when(point.proceed()).thenReturn(result);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            new ApiAuditFilter(audit).doFilter(request, response, (req, res) -> {
                try { assertSame(result, audit.unannotated(point)); }
                catch (Throwable error) { throw new RuntimeException(error); }
            });
            assertSame(result, audit.around(point, mock(OperationLog.class)));
            audit.recordRequest(request, 1, 200);
            stats.afterCompletion(request, response, null, null);
        } finally { RequestContextHolder.resetRequestAttributes(); }
        verify(point, times(2)).proceed();
        verifyNoInteractions(logs, counts, users, traffic);
    }

    @Test void healthCheckErrorsStillReachTheCallerWithoutBeingBusinessLogs() throws Throwable {
        var request = new MockHttpServletRequest("GET", "/api/ping");
        var failure = new IllegalStateException("health check failed");
        var point = mock(ProceedingJoinPoint.class); when(point.proceed()).thenThrow(failure);
        var audit = aspect();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try { assertSame(failure, assertThrows(IllegalStateException.class, () -> audit.unannotated(point))); }
        finally { RequestContextHolder.resetRequestAttributes(); }
        verifyNoInteractions(logs, counts, users);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/oj/run", "/api/ping-extra", "/api/ping/details"})
    void businessAndSimilarlyNamedEndpointsStillLogAndCountExactlyOnce(String path) throws Throwable {
        var request = new MockHttpServletRequest("GET", path);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, path);
        request.setAttribute(OperationLogAspect.ACTOR, 42L);
        var response = new MockHttpServletResponse();
        var audit = aspect(); var point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenReturn(Result.ok());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            audit.unannotated(point);
            audit.recordRequest(request, 1, 200);
            statistics().afterCompletion(request, response, null, null);
        } finally { RequestContextHolder.resetRequestAttributes(); }
        verify(logs, times(1)).insert(any(SysLog.class));
        verify(counts, times(1)).insert(any(com.sap.entity.LogStats.class));
        verify(traffic, times(1)).recordApiRequest(path, "GET");
    }

    @Test void policyUsesExactHealthRouteAndSupportsContextAndMatchedPatterns() {
        var contextual = new MockHttpServletRequest("GET", "/sap/api/ping"); contextual.setContextPath("/sap");
        assertTrue(ApiRequestPolicy.isHealthCheck(contextual));
        var matched = new MockHttpServletRequest("GET", "/internal/probe");
        matched.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/ping");
        assertTrue(ApiRequestPolicy.isHealthCheck(matched));
        assertFalse(ApiRequestPolicy.isHealthCheck(new MockHttpServletRequest()));
        assertFalse(ApiRequestPolicy.isHealthCheck(new MockHttpServletRequest("GET", "/api/ping-extra")));
    }
}
