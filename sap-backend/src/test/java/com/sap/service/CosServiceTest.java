package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CosServiceTest {

    @Mock SettingService settingService;

    @InjectMocks CosService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "allowedTypes", "jpg,png,md");
        ReflectionTestUtils.setField(service, "maxSize", 52428800L);
    }

    private void configureCos() {
        when(settingService.getValue("cos_secret_id")).thenReturn("sid");
        when(settingService.getValue("cos_secret_key")).thenReturn("skey");
        when(settingService.getValue("cos_region")).thenReturn("cos.ap-nanjing");
        when(settingService.getValue("cos_bucket_name")).thenReturn("bucket-123");
    }

    // ===================== isConfigured =====================
    @Test
    void isConfigured_allPresent_true() {
        configureCos();
        assertTrue(service.isConfigured());
    }

    @Test
    void isConfigured_secretIdBlank_false() {
        when(settingService.getValue("cos_secret_id")).thenReturn("");
        when(settingService.getValue("cos_secret_key")).thenReturn("skey");
        when(settingService.getValue("cos_region")).thenReturn("r");
        when(settingService.getValue("cos_bucket_name")).thenReturn("b");
        assertFalse(service.isConfigured());
    }

    @Test
    void isConfigured_secretKeyBlank_false() {
        when(settingService.getValue("cos_secret_id")).thenReturn("sid");
        when(settingService.getValue("cos_secret_key")).thenReturn("  ");
        when(settingService.getValue("cos_region")).thenReturn("r");
        when(settingService.getValue("cos_bucket_name")).thenReturn("b");
        assertFalse(service.isConfigured());
    }

    @Test
    void isConfigured_regionNull_false() {
        when(settingService.getValue("cos_secret_id")).thenReturn("sid");
        when(settingService.getValue("cos_secret_key")).thenReturn("skey");
        when(settingService.getValue("cos_region")).thenReturn(null);
        when(settingService.getValue("cos_bucket_name")).thenReturn("b");
        assertFalse(service.isConfigured());
    }

    @Test
    void isConfigured_bucketBlank_false() {
        when(settingService.getValue("cos_secret_id")).thenReturn("sid");
        when(settingService.getValue("cos_secret_key")).thenReturn("skey");
        when(settingService.getValue("cos_region")).thenReturn("r");
        when(settingService.getValue("cos_bucket_name")).thenReturn("");
        assertFalse(service.isConfigured());
    }

    // ===================== upload =====================
    @Test
    void upload_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));
        assertEquals("文件不能为空", ex.getMessage());
    }

    @Test
    void upload_tooLarge_throws() {
        ReflectionTestUtils.setField(service, "maxSize", 10L); // 10 bytes limit
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                "this is more than ten bytes".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));
        assertTrue(ex.getMessage().contains("文件大小超过限制"));
    }

    @Test
    void upload_illegalExtension_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream",
                "content".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));
        assertTrue(ex.getMessage().contains("不支持的文件类型"));
        assertTrue(ex.getMessage().contains("exe"));
    }

    @Test
    void upload_noExtension_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "noext", "text/plain",
                "content".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));
        assertTrue(ex.getMessage().contains("不支持的文件类型"));
        assertTrue(ex.getMessage().contains("(无扩展名)"));
    }

    @Test
    void upload_validExtensionButCosNotConfigured_throws() {
        // allowed extension jpg, but COS settings blank -> buildClient throws before any network call
        when(settingService.getValue("cos_secret_id")).thenReturn("");
        when(settingService.getValue("cos_secret_key")).thenReturn("");
        when(settingService.getValue("cos_region")).thenReturn("");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                "content".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));
        assertTrue(ex.getMessage().contains("尚未配置"));
    }

    // ===================== testConnection =====================
    @Test
    void testConnection_cosNotConfigured_throws() {
        when(settingService.getValue("cos_secret_id")).thenReturn(null);
        when(settingService.getValue("cos_secret_key")).thenReturn(null);
        when(settingService.getValue("cos_region")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.testConnection());
        assertTrue(ex.getMessage().contains("尚未配置"));
    }

    // ===================== getMaskedConfig =====================
    @Test
    void getMaskedConfig_masksSecrets() {
        when(settingService.getValue("cos_bucket_name")).thenReturn("bucket-1");
        when(settingService.getValue("cos_region")).thenReturn("cos.ap-nanjing");
        when(settingService.getValue("cos_secret_id")).thenReturn("ABCD1234567890WXYZ"); // > 8
        when(settingService.getValue("cos_secret_key")).thenReturn("short"); // <= 8

        Map<String, String> config = service.getMaskedConfig();

        assertEquals("bucket-1", config.get("bucketName"));
        assertEquals("cos.ap-nanjing", config.get("region"));
        assertEquals("ABCD****WXYZ", config.get("secretId"));
        assertEquals("short", config.get("secretKey")); // <=8 returned as-is
    }

    @Test
    void getMaskedConfig_nullSecret_returnedAsNull() {
        when(settingService.getValue("cos_bucket_name")).thenReturn("b");
        when(settingService.getValue("cos_region")).thenReturn("r");
        when(settingService.getValue("cos_secret_id")).thenReturn(null);
        when(settingService.getValue("cos_secret_key")).thenReturn(null);

        Map<String, String> config = service.getMaskedConfig();
        assertNull(config.get("secretId"));
        assertNull(config.get("secretKey"));
    }

    // ===================== saveConfig =====================
    @Test
    void saveConfig_savesBucketRegionAndNonMaskedSecrets() {
        Map<String, String> config = new HashMap<>();
        config.put("bucketName", "my-bucket");
        config.put("region", "cos.ap-nanjing");
        config.put("secretId", "realSecretId");
        config.put("secretKey", "realSecretKey");

        service.saveConfig(config);

        ArgumentCaptor<Setting> captor = ArgumentCaptor.forClass(Setting.class);
        verify(settingService, times(4)).updateSetting(captor.capture());
        // verify the bucket value was saved
        assertTrue(captor.getAllValues().stream()
                .anyMatch(s -> "cos_bucket_name".equals(s.getSettingKey()) && "my-bucket".equals(s.getSettingValue())));
    }

    @Test
    void saveConfig_skipsMaskedAndEmptySecrets() {
        Map<String, String> config = new HashMap<>();
        config.put("bucketName", "my-bucket");
        config.put("region", "r");
        config.put("secretId", "ABCD****WXYZ"); // contains * -> skip
        config.put("secretKey", "");            // empty -> skip

        service.saveConfig(config);

        // only bucket + region saved (2 calls)
        verify(settingService, times(2)).updateSetting(any(Setting.class));
    }

    @Test
    void saveConfig_nullFields_skipsAll() {
        Map<String, String> config = new HashMap<>();
        // all null
        service.saveConfig(config);
        verify(settingService, never()).updateSetting(any());
    }

    @Test
    void saveConfig_nullSecretFields_skipsSecrets() {
        Map<String, String> config = new HashMap<>();
        config.put("bucketName", "b");
        config.put("region", "r");
        // secretId / secretKey absent (null)
        service.saveConfig(config);
        verify(settingService, times(2)).updateSetting(any(Setting.class));
    }
}
