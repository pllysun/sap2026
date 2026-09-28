package com.sap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sap.dto.RegistrationProtectionConfig;
import com.sap.service.RegistrationProtectionSettingsService;

import static org.mockito.Mockito.*;

/** 为风控行为测试提供可动态变更的策略源。 */
public final class RegistrationPolicyFixture {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static RegistrationProtectionSettingsService settings() {
        var settings = mock(RegistrationProtectionSettingsService.class);
        when(settings.current()).thenReturn(RegistrationProtectionConfig.defaults());
        return settings;
    }

    public static void change(RegistrationProtectionSettingsService settings, String group, String field, Object value) {
        ObjectNode document = JSON.valueToTree(settings.current());
        ((ObjectNode) document.get(group)).set(field, JSON.valueToTree(value));
        when(settings.current()).thenReturn(JSON.convertValue(document, RegistrationProtectionConfig.class));
    }
}
