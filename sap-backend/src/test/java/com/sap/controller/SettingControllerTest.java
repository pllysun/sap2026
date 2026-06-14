package com.sap.controller;

import com.sap.common.BusinessException;
import com.sap.common.Result;
import com.sap.entity.Setting;
import com.sap.service.CacheService;
import com.sap.service.CosService;
import com.sap.service.SettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingControllerTest {

    @Mock SettingService settingService;
    @Mock CosService cosService;
    @Mock CacheService cacheService;

    @InjectMocks SettingController controller;

    // ===================== getValue =====================

    @Test
    void getValue_normalKey_returnsValue() {
        when(settingService.getValue("footer_qq")).thenReturn("12345");

        Result<?> result = controller.getValue("footer_qq");

        assertEquals(200, result.getCode());
        assertEquals("12345", result.getData());
    }

    @Test
    void getValue_sensitiveKeySecretId_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.getValue("cos_secret_id"));
        assertEquals("无权通过该接口读取敏感配置", ex.getMessage());
        verify(settingService, never()).getValue(any());
    }

    @Test
    void getValue_sensitiveKeySecretKey_throws() {
        assertThrows(BusinessException.class, () -> controller.getValue("cos_secret_key"));
    }

    // ===================== publicSettings =====================

    @Test
    void publicSettings_returnsMap() {
        Map<String, String> data = Map.of("footer_qq", "1");
        when(settingService.getPublicSettings()).thenReturn(data);

        Result<?> result = controller.publicSettings();

        assertEquals(data, result.getData());
    }

    // ===================== update =====================

    @Test
    void update_returnsMessage() {
        Setting s = new Setting();
        s.setSettingKey("k");

        Result<?> result = controller.update(s);

        assertEquals("更新成功", result.getData());
        verify(settingService).updateSetting(s);
    }

    // ===================== getCosConfig =====================

    @Test
    void getCosConfig_returnsMaskedConfig() {
        Map<String, String> masked = Map.of("secretId", "ab****cd");
        when(cosService.getMaskedConfig()).thenReturn(masked);

        Result<?> result = controller.getCosConfig();

        assertEquals(masked, result.getData());
    }

    // ===================== updateCosConfig =====================

    @Test
    void updateCosConfig_returnsMessage() {
        Map<String, String> config = Map.of("bucketName", "b");

        Result<?> result = controller.updateCosConfig(config);

        assertEquals("配置已保存", result.getData());
        verify(cosService).saveConfig(config);
    }

    // ===================== testCos =====================

    @Test
    void testCos_returnsMessage() {
        Result<?> result = controller.testCos();

        assertEquals("连通性检测通过", result.getData());
        verify(cosService).testConnection();
    }

    // ===================== refreshCache =====================

    @Test
    void refreshCache_refreshesBothCachesAndReturnsMessage() {
        Result<?> result = controller.refreshCache();

        assertEquals("缓存已刷新", result.getData());
        verify(cacheService).refreshUsers();
        verify(cacheService).refreshPositions();
    }
}
