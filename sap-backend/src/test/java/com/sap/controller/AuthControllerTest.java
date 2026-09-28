package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.Result;
import com.sap.dto.LoginDTO;
import com.sap.dto.RegisterDTO;
import com.sap.service.AuthService;
import com.sap.service.RegistrationProtectionService;
import com.sap.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock AuthService authService;

    @Mock com.sap.service.CaptchaService captchaService;
    @Mock RegistrationProtectionService registrationProtection;
    @Mock RegistrationProtectionService.Permit permit;

    @InjectMocks AuthController controller;

    @Test
    void adminLogin_returnsServiceResult() {
        Map<String, Object> data = Map.of("token", "t");
        when(authService.adminLogin(any())).thenReturn(data);

        Result<?> result = controller.adminLogin(new LoginDTO());

        assertEquals(200, result.getCode());
        assertEquals(data, result.getData());
        verify(authService).adminLogin(any());
    }

    @Test
    void login_returnsServiceResult() {
        Map<String, Object> data = Map.of("token", "t");
        when(authService.login(any())).thenReturn(data);

        Result<?> result = controller.login(new LoginDTO());

        assertEquals(200, result.getCode());
        assertEquals(data, result.getData());
    }

    @Test
    void register_returnsSuccessMessage() {
        when(captchaService.requiresCaptchaForAttempt(any())).thenReturn(false);
        when(registrationProtection.acquire(any(), any())).thenReturn(permit);
        Result<?> result = controller.register(new RegisterDTO(),
                new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(200, result.getCode());
        assertEquals("注册成功", result.getData());
        verify(authService).register(any());
        verify(permit).close();
    }

    @Test
    void register_whenRiskTriggered_returnsCaptchaRequired() {
        // 风控触发且未带验证码 → 返回 captchaRequired，不创建用户
        when(captchaService.requiresCaptchaForAttempt(any())).thenReturn(true);
        Result<?> result = controller.register(new RegisterDTO(),
                new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(200, result.getCode());
        assertTrue(result.getData() instanceof Map);
        assertEquals(Boolean.TRUE, ((Map<?, ?>) result.getData()).get("captchaRequired"));
        verify(authService, never()).register(any());
    }

    @Test
    void register_whenRiskTriggered_wrongCaptcha_returnsError() {
        // 风控触发且验证码错误 → 返回错误，不创建用户
        when(captchaService.requiresCaptchaForAttempt(any())).thenReturn(true);
        when(captchaService.verify(any(), any(), any())).thenReturn(false);
        RegisterDTO dto = new RegisterDTO();
        dto.setCaptchaId("cid");
        dto.setCaptchaCode("bad");
        Result<?> result = controller.register(dto,
                new org.springframework.mock.web.MockHttpServletRequest());

        assertNotEquals(200, result.getCode());
        verify(authService, never()).register(any());
    }

    @Test
    void info_returnsCurrentUser() {
        Map<String, Object> data = new HashMap<>();
        data.put("roles", java.util.List.of(1));
        when(authService.getCurrentUser()).thenReturn(data);

        Result<?> result = controller.info();

        assertEquals(200, result.getCode());
        assertEquals(data, result.getData());
    }

    @Test
    void register_validCaptcha_reservesQuotaBeforeDatabase_andReleasesOnFailure() {
        when(captchaService.requiresCaptchaForAttempt(any())).thenReturn(true);
        when(captchaService.verify("cid", "ABC123", "127.0.0.1")).thenReturn(true);
        when(registrationProtection.acquire("127.0.0.1", "123456")).thenReturn(permit);
        RegisterDTO dto = new RegisterDTO();
        dto.setCaptchaId("cid");
        dto.setCaptchaCode("ABC123");
        dto.setQq("123456");
        doThrow(new BusinessException("该学号已注册")).when(authService).register(dto);

        assertThrows(BusinessException.class, () -> controller.register(dto,
                new org.springframework.mock.web.MockHttpServletRequest()));
        var order = inOrder(captchaService, registrationProtection, authService, permit);
        order.verify(captchaService).requiresCaptchaForAttempt("127.0.0.1");
        order.verify(captchaService).verify("cid", "ABC123", "127.0.0.1");
        order.verify(registrationProtection).acquire("127.0.0.1", "123456");
        order.verify(authService).register(dto);
        order.verify(permit).close();
    }

    @Test
    void register_quotaExceeded_neverCreatesAccount() {
        when(registrationProtection.acquire(any(), any())).thenThrow(new BusinessException(429, "注册过于频繁"));
        BusinessException error = assertThrows(BusinessException.class, () -> controller.register(new RegisterDTO(),
                new org.springframework.mock.web.MockHttpServletRequest()));
        assertEquals(429, error.getCode());
        verifyNoInteractions(authService);
    }

    @Test
    void logout_callsStpUtilAndReturnsMessage() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            Result<?> result = controller.logout();

            assertEquals(200, result.getCode());
            assertEquals("退出成功", result.getData());
            st.verify(StpUtil::logout);
        }
    }

    @Test
    void updateProfile_delegatesAndReturnsMessage() {
        Map<String, Object> params = Map.of("nickname", "n");

        Result<?> result = controller.updateProfile(params);

        assertEquals(200, result.getCode());
        assertEquals("更新成功", result.getData());
        verify(authService).updateProfile(params);
    }
}
