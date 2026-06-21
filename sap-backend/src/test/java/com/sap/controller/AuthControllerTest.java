package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.Result;
import com.sap.dto.LoginDTO;
import com.sap.dto.RegisterDTO;
import com.sap.service.AuthService;
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
        // 默认风控不要求验证码（mock captchaRequired→false）→ 正常注册
        when(captchaService.captchaRequired(any())).thenReturn(false);
        Result<?> result = controller.register(new RegisterDTO(),
                new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(200, result.getCode());
        assertEquals("注册成功", result.getData());
        verify(authService).register(any());
        verify(captchaService).recordRegister(any());
    }

    @Test
    void register_whenRiskTriggered_returnsCaptchaRequired() {
        // 风控触发且未带验证码 → 返回 captchaRequired，不创建用户
        when(captchaService.captchaRequired(any())).thenReturn(true);
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
        when(captchaService.captchaRequired(any())).thenReturn(true);
        when(captchaService.verify(any(), any())).thenReturn(false);
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
