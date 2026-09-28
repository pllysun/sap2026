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
import static com.sap.RegistrationPolicyFixture.*;

/** 限流拦截器单测：分类、桶键(IP/用户)、放行/拦截429/灰度、未归类放行。 */
class RateLimitInterceptorTest {

    private RateLimitInterceptor interceptor(RateLimiterService limiter, RateLimitProperties props) {
        RateLimitInterceptor it = new RateLimitInterceptor();
        ReflectionTestUtils.setField(it, "limiter", limiter);
        ReflectionTestUtils.setField(it, "props", props);
        ReflectionTestUtils.setField(it, "registrationSettings", settings());
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
        assertEquals(RateLimitInterceptor.Category.LOGIN, it.categorize(req("POST", "/api/auth/password-recovery/send")));
        assertEquals(RateLimitInterceptor.Category.LOGIN, it.categorize(req("POST", "/api/auth/password-recovery/reset")));
        assertEquals(RateLimitInterceptor.Category.CAPTCHA, it.categorize(req("GET", "/api/auth/captcha")));
        assertEquals(RateLimitInterceptor.Category.REGISTER, it.categorize(req("POST", "/api/auth/register")));
        assertEquals(RateLimitInterceptor.Category.JW, it.categorize(req("GET", "/api/jw/schedule")));
        assertEquals(RateLimitInterceptor.Category.PDF, it.categorize(req("GET", "/api/note/5/pdf")));
        assertEquals(RateLimitInterceptor.Category.DOWNLOAD, it.categorize(req("GET", "/api/file/download")));
        assertEquals(RateLimitInterceptor.Category.DOWNLOAD, it.categorize(req("GET", "/api/file/go")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("POST", "/api/activity/create")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("DELETE", "/api/activity/1")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("PUT", "/api/activity/1")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("POST", "/api/app/feedback/issues")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("POST", "/api/app/feedback/images")));
        assertEquals(RateLimitInterceptor.Category.WRITE, it.categorize(req("DELETE", "/api/app/feedback/admin/issues/1")));
        assertNull(it.categorize(req("GET", "/api/activity/list")));  // 普通读不限流
        assertNull(it.categorize(req("OPTIONS", "/api/auth/login"))); // CORS 预检不限流
        assertNull(it.categorize(req("POST", "/other/x")));           // 非 /api 写不限流
    }

    @Test
    void ruleOf映射每个类别() {
        RateLimitProperties props = new RateLimitProperties();
        RateLimitInterceptor it = interceptor(mock(RateLimiterService.class), props);
        assertSame(props.getLogin(), it.ruleOf(RateLimitInterceptor.Category.LOGIN));
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
        boolean r = it.preHandle(req("POST", "/api/auth/login"), resp, new Object());
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
        assertTrue(it.preHandle(req("POST", "/api/auth/login"), resp, new Object()));
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
        r.setRemoteAddr("127.0.0.1");
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

    @Test
    void 直连不能通过轮换代理头绕过注册限流() throws Exception {
        RateLimiterService limiter = mock(RateLimiterService.class);
        when(limiter.tryAcquireEnforced(anyString(), anyInt(), anyDouble())).thenReturn(true);
        RateLimitInterceptor it = interceptor(limiter, new RateLimitProperties());
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest r = req("POST", "/api/auth/register");
            r.addHeader("X-Real-IP", "192.0.2." + i);
            r.addHeader("X-Forwarded-For", "192.0.2." + i);
            assertTrue(it.preHandle(r, new MockHttpServletResponse(), new Object()));
        }
        verify(limiter, times(3)).tryAcquireEnforced(eq("rl:register:ip:9.9.9.9"), anyInt(), anyDouble());
    }

    @Test
    void 验证码独立按IP和全局限流() throws Exception {
        RateLimiterService limiter = mock(RateLimiterService.class);
        when(limiter.tryAcquireEnforced(eq("rl:captcha:ip:9.9.9.9"), anyInt(), anyDouble())).thenReturn(true);
        when(limiter.tryAcquireEnforced(eq("rl:captcha:global"), anyInt(), anyDouble())).thenReturn(false);
        RateLimitInterceptor it = interceptor(limiter, new RateLimitProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(it.preHandle(req("GET", "/api/auth/captcha"), response, new Object()));
        assertTrue(response.getContentAsString().contains("\"code\":429"));
        verify(limiter).tryAcquireEnforced("rl:captcha:global", 60, 1.0);
    }

    @Test
    void 矩阵参数编码与部署前缀不能绕过验证码或注册分类() {
        RateLimitInterceptor it = interceptor(mock(RateLimiterService.class), new RateLimitProperties());
        assertEquals(RateLimitInterceptor.Category.CAPTCHA, it.categorize(req("GET", "/api/auth/captcha;x=1")));
        assertEquals(RateLimitInterceptor.Category.CAPTCHA, it.categorize(req("GET", "/api/auth/%63aptcha")));
        var request = req("POST", "/sap/api/auth/register;x=1");
        request.setContextPath("/sap");
        assertEquals(RateLimitInterceptor.Category.REGISTER, it.categorize(request));
    }

    @Test
    void 注册请求实时使用数据库策略且不受旧灰度开关影响() throws Exception {
        var limiter = mock(RateLimiterService.class);
        var legacy = new RateLimitProperties();
        legacy.setEnabled(false);
        legacy.setDryRun(true);
        var it = interceptor(limiter, legacy);
        var settings = (com.sap.service.RegistrationProtectionSettingsService)
                ReflectionTestUtils.getField(it, "registrationSettings");
        change(settings, "requests", "registerCapacity", 3);
        change(settings, "requests", "registerPerMinute", 6);
        when(limiter.tryAcquireEnforced("rl:register:ip:9.9.9.9", 3, 0.1)).thenReturn(true, false);
        assertTrue(it.preHandle(req("POST", "/api/auth/register"), new MockHttpServletResponse(), new Object()));
        assertFalse(it.preHandle(req("POST", "/api/auth/register"), new MockHttpServletResponse(), new Object()));
        change(settings, "requests", "enabled", false);
        assertTrue(it.preHandle(req("POST", "/api/auth/register"), new MockHttpServletResponse(), new Object()));
        verify(limiter, times(2)).tryAcquireEnforced("rl:register:ip:9.9.9.9", 3, 0.1);
    }
}
