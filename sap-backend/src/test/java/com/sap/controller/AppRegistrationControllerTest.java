package com.sap.controller;

import com.sap.service.AppRegistrationService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.sap.common.GlobalExceptionHandler;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppRegistrationControllerTest {
    AppRegistrationService service; MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(AppRegistrationService.class);
        mvc=MockMvcBuilders.standaloneSetup(new AppRegistrationController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void validPublicEmailRequestExposesChallengeWithoutRegistering() throws Exception {
        when(service.request(any(),any(),any(),any(),any(),any())).thenReturn(Map.of("captchaRequired",true));
        mvc.perform(post("/api/auth/app/register/email-code").contentType("application/json")
                .content("{\"studentId\":\"20260001\",\"name\":\"同学\",\"qq\":\"123456789\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.captchaRequired").value(true));
        verify(service,never()).register(any(),any());
    }
    @Test void mobileEndpointRejectsMissingEmailVerificationAndInvalidRecipient() throws Exception {
        mvc.perform(post("/api/auth/app/register").contentType("application/json").content("{\"studentId\":\"20260001\",\"password\":\"secret123\",\"name\":\"同学\",\"gender\":0,\"qq\":\"123456789\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/auth/app/register/email-code").contentType("application/json").content("{\"studentId\":\"20260001\",\"name\":\"同学\",\"qq\":\"bad@example.com\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }
}
