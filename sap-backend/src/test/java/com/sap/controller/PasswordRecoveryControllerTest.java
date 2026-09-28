package com.sap.controller;

import com.sap.service.PasswordRecoveryService;
import com.sap.common.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordRecoveryControllerTest {
    PasswordRecoveryService service; MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(PasswordRecoveryService.class);
        mvc=MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void sendIsExplicitPostAndDoesNotAcceptUserSelectedRecipient() throws Exception {
        when(service.request("20260007","image","ABCD","192.0.2.1")).thenReturn(Map.of("requestId","opaque"));
        mvc.perform(post("/api/auth/password-recovery/send").header("X-Real-IP","192.0.2.1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"account\":\"20260007\",\"captchaId\":\"image\",\"captcha\":\"ABCD\"}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.requestId").value("opaque"));
        verify(service).request("20260007","image","ABCD","192.0.2.1");
        // 本项目全局异常处理统一返回 HTTP 200 + 业务 code；不支持 GET，且不得触发发送。
        mvc.perform(get("/api/auth/password-recovery/send")).andExpect(jsonPath("$.code").value(500));
        verifyNoMoreInteractions(service);
    }
    @Test void invalidRequestsNeverReachBusinessService() throws Exception {
        mvc.perform(post("/api/auth/password-recovery/send").contentType(MediaType.APPLICATION_JSON).content("{\"account\":\"20260007\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/auth/password-recovery/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"id\",\"code\":\"123\",\"newPassword\":\"pw\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }
    @Test void resetPassesSecretOnlyToRecoveryService() throws Exception {
        mvc.perform(post("/api/auth/password-recovery/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"opaque\",\"code\":\"123456\",\"newPassword\":\"new-password\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("密码已重置，请使用新密码重新登录"));
        verify(service).reset("opaque","123456","new-password");
    }
}
