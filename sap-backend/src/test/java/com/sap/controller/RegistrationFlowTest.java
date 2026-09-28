package com.sap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.GlobalExceptionHandler;
import com.sap.config.RateLimitInterceptor;
import com.sap.config.RateLimitProperties;
import com.sap.service.AuthService;
import com.sap.service.CaptchaService;
import com.sap.service.RateLimiterService;
import com.sap.service.RegistrationProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sap.RegistrationPolicyFixture.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实验证码、限流器与控制器组合测试，数据库服务作为边界 mock。 */
class RegistrationFlowTest {
    private MockMvc mvc;
    private CaptchaService captcha;
    private AuthService auth;
    private long now;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        now = 1_000_000L;
        RateLimitProperties rate = new RateLimitProperties();
        rate.setUseRedis(false);
        RateLimiterService limiter = new RateLimiterService();
        ReflectionTestUtils.setField(limiter, "props", rate);
        ReflectionTestUtils.setField(limiter, "clock", (LongSupplier) () -> now);
        RateLimitInterceptor interceptor = new RateLimitInterceptor();
        var settings = settings();
        ReflectionTestUtils.setField(interceptor, "props", rate);
        ReflectionTestUtils.setField(interceptor, "limiter", limiter);
        ReflectionTestUtils.setField(interceptor, "registrationSettings", settings);
        captcha = new CaptchaService(settings);
        ReflectionTestUtils.setField(captcha, "clock", (LongSupplier) () -> now);
        auth = mock(AuthService.class);
        AuthController authController = new AuthController();
        ReflectionTestUtils.setField(authController, "authService", auth);
        ReflectionTestUtils.setField(authController, "captchaService", captcha);
        ReflectionTestUtils.setField(authController, "registrationProtection",
                new RegistrationProtectionService(limiter, settings));
        CaptchaController captchaController = new CaptchaController();
        ReflectionTestUtils.setField(captchaController, "captchaService", captcha);
        mvc = MockMvcBuilders.standaloneSetup(authController, captchaController)
                .addInterceptors(interceptor).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Map<String, Object> form() {
        return new HashMap<>(Map.of("studentId", "20260001", "password", "secret123",
                "name", "测试用户", "qq", "123456", "gender", 1));
    }

    private void solve(Map<String, Object> form) throws Exception {
        var response = mvc.perform(get("/api/auth/captcha"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate"))
                .andReturn().getResponse();
        String id = json.readTree(response.getContentAsString()).path("data").path("captchaId").asText();
        Map<?, ?> stored = (Map<?, ?>) ReflectionTestUtils.getField(captcha, "captchas");
        String answer = (String) ReflectionTestUtils.getField(stored.get(id), "answer");
        assertFalse(response.getContentAsString().contains("\"answer\""));
        form.put("captchaId", id);
        form.put("captchaCode", answer);
        now += 1_000;
    }

    @Test
    void challengeThenRegisterReplayAndRapidSecondAccount() throws Exception {
        Map<String, Object> form = form();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                .andExpect(jsonPath("$.data.captchaRequired").value(true));
        verifyNoInteractions(auth);

        solve(form);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value("注册成功"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                .andExpect(jsonPath("$.code").value(400));

        solve(form);
        form.put("studentId", "20260002");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                .andExpect(jsonPath("$.code").value(429));
        verify(auth, times(1)).register(any());

        now += 10_000;
        solve(form);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                .andExpect(jsonPath("$.code").value(200));
        verify(auth, times(2)).register(any());
    }

    @Test
    void captchaCannotBeTransferredToAnotherSource() throws Exception {
        Map<String, Object> form = form();
        solve(form);
        mvc.perform(post("/api/auth/register").with(r -> { r.setRemoteAddr("192.0.2.2"); return r; })
                        .header("X-Real-IP", "127.0.0.1").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(auth);
    }

    @Test
    void missingCaptchaFloodHitsRateLimitEvenWithSpoofedHeaders() throws Exception {
        for (int i = 0; i < 21; i++) {
            mvc.perform(post("/api/auth/register").with(r -> { r.setRemoteAddr("192.0.2.9"); return r; })
                            .header("X-Real-IP", "198.51.100." + i).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(form())))
                    .andExpect(jsonPath("$.code").value(i < 20 ? 200 : 429));
        }
        verifyNoInteractions(auth);
    }

    @Test
    void captchaMatrixPathAlsoHitsImageRateLimit() throws Exception {
        for (int i = 0; i < 11; i++) {
            mvc.perform(get("/api/auth/captcha;ignored=" + i))
                    .andExpect(jsonPath("$.code").value(i < 10 ? 200 : 429));
        }
    }

    @Test
    void malformedOrOversizedRegistrationNeverReachesDatabase() throws Exception {
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                "studentId", "a".repeat(21), "name", "名".repeat(51), "qq", "0012345",
                "nickname", "n".repeat(51), "captchaId", "c".repeat(65), "gender", 2).entrySet()) {
            var form = form();
            form.put(invalid.getKey(), invalid.getValue());
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                    .andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(auth);
    }
}
