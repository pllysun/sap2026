package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.common.GlobalExceptionHandler;
import com.sap.config.FastjsonConfig;
import com.sap.dto.RegistrationProtectionConfig;
import com.sap.service.RegistrationProtectionSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationProtectionSettingsControllerTest {
    private RegistrationProtectionSettingsService service;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private final RegistrationProtectionConfig config = RegistrationProtectionConfig.defaults();
    private static final String PATH = "/api/setting/registration-protection";

    @BeforeEach
    void setup() {
        service = mock(RegistrationProtectionSettingsService.class);
        var converters = new ArrayList<HttpMessageConverter<?>>();
        new FastjsonConfig().configureMessageConverters(converters);
        mvc = MockMvcBuilders.standaloneSetup(new RegistrationProtectionSettingsController(service))
                .setMessageConverters(converters.toArray(HttpMessageConverter[]::new))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void settingsEndpointIsRestrictedToSuperAdminOrPresident() {
        var annotation = RegistrationProtectionSettingsController.class.getAnnotation(SaCheckRole.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{"0", "1"}, annotation.value());
        assertEquals(SaMode.OR, annotation.mode());
    }

    @Test
    void readAndSaveUseTypedConfigWithActualProductionJsonConverter() throws Exception {
        var view = new RegistrationProtectionSettingsService.View("revision", config, config);
        when(service.reload()).thenReturn(view);
        mvc.perform(get(PATH)).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.config.captcha.enabled").value(true))
                .andExpect(jsonPath("$.data.config.quotas.ipHourlyLimit").value(20))
                .andExpect(jsonPath("$.data.defaults.requests.registerCapacity").value(20));
        var update = new RegistrationProtectionSettingsService.Update("revision", config);
        when(service.save(update)).thenReturn(view);
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.revision").value("revision"));
        verify(service).save(update);
    }

    @Test
    void incompleteAndInvalidConfigNeverReachSave() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":\"v1\"}"))
                .andExpect(jsonPath("$.code").value(400));
        var invalid = new RegistrationProtectionConfig(config.captcha(), config.quotas(),
                new RegistrationProtectionConfig.Requests(true, 0, 20, 10, 10, 60, 60), config.trustedProxies());
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegistrationProtectionSettingsService.Update("v1", invalid))))
                .andExpect(jsonPath("$.code").value(400));
        var incomplete = json.valueToTree(new RegistrationProtectionSettingsService.Update("v1", config));
        ((com.fasterxml.jackson.databind.node.ObjectNode) incomplete.path("config").path("captcha")).remove("minSolveSeconds");
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(incomplete)))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void staleVersionReturnsConflictWithoutClaimingSaveSuccess() throws Exception {
        when(service.save(any())).thenThrow(new BusinessException(409, "配置已被其他管理员更新"));
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegistrationProtectionSettingsService.Update("stale", config))))
                .andExpect(jsonPath("$.code").value(409));
    }
}
