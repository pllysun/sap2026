package com.sap.config;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 限流拦截器单测：分类、桶键(IP/用户)、放行/拦截429/灰度、未归类放行。 */
class RateLimitInterceptorTest {

    private RateLimitInterceptor interceptor(RateLimiterService limiter, RateLimitProperties props) {
        RateLimitInterceptor it = new RateLimitInterceptor();
        ReflectionTestUtils.setField(it, "limiter", limiter);
        ReflectionTestUtils.setField(it, "props", props);
        return it;
    }

    private MockHttpServletRequest req(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRemoteAddr("9.9.9.9");
        return r;
    }

    @Test
    void 分类覆盖各桶与放行项() {
        RateLimitInterceptor it = interceptor(mock(RateLimiterService.class), new RateLimitProperties());
        assertEquals(RateLimitInterceptor.Category.LOGIN, it.categorize(req("POST", "/api/auth/login")));
        assertEquals(RateLimitInterceptor.Category.LOGIN, it.categorize(req("POST", "/api/auth/admin/login")));
        assertEquals(RateLimitInterceptor.Category.LOGIN, it.categorize(req("POST", "/api/auth/app/login")));
        assertEquals(RateLimitInterceptor.Category.REGISTER, it.categorize(req("POST", "/api/auth/register")));
        assertEquals(RateLimitInterceptor.Category.JW, it.categorize(req("GET", "/api/jw/schedule")));
        assertEquals(RateLimitInterceptor.Category.PDF, it.categorize(req("GET", "/api/note/5/pdf")));
        assertEquals(RateLimitInterceptor.Category.DOWNLOAD, it.categorize(req("GET", "/api/file/download")));
        assertEquals(RateLimitInterceptor.Category.DOWNLOAD, it.categorize(req("GET", "/api/file/go")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("POST", "/api/activity/create")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("DELETE", "/api/activity/1")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("PUT", "/api/activity/1")));
        assertNull(it.categorize(req("GET", "/api/activity/list")));  // 普通读不限流
        assertNull(it.categorize(req("OPTIONS", "/api/auth/login"))); // CORS 预检不限流
        assertNull(it.categorize(req("POST", "/other/x")));           // 非 /api 写不限流
    }

    @Test
    void ruleOf映射每个类别() {
        RateLimitProperties props = new RateLimitProperties();
        RateLimitInterceptor it = interceptor(mock(RateLimiterService.class), props);
        assertSame(props.getLogin(), it.ruleOf(RateLimitInterceptor.Category.LOGIN));
        assertSame(props.getRegister(), it.ruleOf(RateLimitInterceptor.Category.REGISTER));
        assertSame(props.getJw(), it.ruleOf(RateLimitInterceptor.Category.JW));
        assertSame(props.getPdf(), it.ruleOf(RateLimitInterceptor.Category.PDF));
        assertSame(props.getDownload(), it.ruleOf(RateLimitInterceptor.Category.DOWNLOAD));
        assertSame(props.getWrite(), it.ruleOf(RateLimitInterceptor.Category.WRITE));
    }

    @Test
    void 未归类请求直接放行且不调用限流器() throws Exception {
        RateLimiterService limiter = mock(RateLimiterService.class);
        RateLimitInterceptor it = interceptor(limiter, new RateLimitProperties());
        assertTrue(it.preHandle(req("GET", "/api/activity/list"), new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(limiter);
    }

    @Test
    void 放行时preHandle为true() throws Exception {
        RateLimiterService limiter = mock(RateLimiterService.class);
        when(limiter.tryAcquire(anyString(), anyInt(), anyDouble())).thenReturn(true);
        RateLimitInterceptor it = interceptor(limiter, new RateLimitProperties());
        assertTrue(it.preHandle(req("POST", "/api/auth/login"), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void 超限时中断并写code429体() throws Exception {
        RateLimiterService limiter = mock(RateLimiterService.class);
        when(limiter.tryAcquire(anyString(), anyInt(), anyDouble())).thenReturn(false);
        RateLimitInterceptor it = interceptor(limiter, new RateLimitProperties());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean r = it.preHandle(req("POST", "/api/auth/register"), resp, new Object());
        assertFalse(r); // 中断后续链
        // 沿用 HTTP 200 + 体 code 约定（与 401/403 一致），前端按 code!==200 处理
        assertTrue(resp.getContentAsString().contains("\"code\":429"));
        assertTrue(resp.getContentAsString().contains("请求过于频繁"));
        assertEquals("application/json;charset=UTF-8", resp.getContentType());
    }

    @Test
    void 灰度模式超限仍放行不写429() throws Exception {
        RateLimiterService limiter = mock(RateLimiterService.class);
        when(limiter.tryAcquire(anyString(), anyInt(), anyDouble())).thenReturn(false);
        RateLimitProperties props = new RateLimitProperties();
        props.setDryRun(true);
        RateLimitInterceptor it = interceptor(limiter, props);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(it.preHandle(req("POST", "/api/auth/register"), resp, new Object()));
        assertNotEquals(429, resp.getStatus());
    }

    @Test
    void 桶键_登录按IP_jw按用户或IP() {
        RateLimitInterceptor it = interceptor(mock(RateLimiterService.class), new RateLimitProperties());
        assertEquals("ip:9.9.9.9", it.keyOf(req("POST", "/api/auth/login"), RateLimitInterceptor.Category.LOGIN));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::isLogin).thenReturn(true);
            st.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertEquals("u:42", it.keyOf(req("GET", "/api/jw/x"), RateLimitInterceptor.Category.JW));
        }
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::isLogin).thenReturn(false);
            assertEquals("ip:9.9.9.9", it.keyOf(req("GET", "/api/jw/x"), RateLimitInterceptor.Category.JW));
        }
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::isLogin).thenThrow(new RuntimeException("no ctx"));
            assertEquals("ip:9.9.9.9", it.keyOf(req("GET", "/api/jw/x"), RateLimitInterceptor.Category.JW));
        }
    }

    @Test
    void 经可信XRealIP取IP作键() throws Exception {
        RateLimiterService limiter = mock(RateLimiterService.class);
        when(limiter.tryAcquire(anyString(), anyInt(), anyDouble())).thenReturn(true);
        RateLimitInterceptor it = interceptor(limiter, new RateLimitProperties());
        MockHttpServletRequest r = req("POST", "/api/auth/login");
        r.addHeader("X-Real-IP", "1.2.3.4");
        r.addHeader("X-Forwarded-For", "evil-spoof, 1.2.3.4"); // 伪造首段不被采信
        it.preHandle(r, new MockHttpServletResponse(), new Object());
        verify(limiter).tryAcquire(eq("rl:login:ip:1.2.3.4"), anyInt(), anyDouble());
    }

    @Test
    void OPTIONS预检在重端点上也不限流() {
        RateLimitInterceptor it = interceptor(mock(RateLimiterService.class), new RateLimitProperties());
        assertNull(it.categorize(req("OPTIONS", "/api/jw/schedule")));
        assertNull(it.categorize(req("OPTIONS", "/api/file/download")));
        assertNull(it.categorize(req("OPTIONS", "/api/auth/register")));
    }
}
