package com.sap.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.ObjectMetadata;
import com.sap.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CosService 成功路径：用 mockConstruction 拦截 COSClient 构造，覆盖 buildClient/upload/testConnection。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CosServiceSuccessTest {

    @Mock SettingService settingService;
    @Mock TrafficService trafficService;
    @InjectMocks CosService cosService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cosService, "allowedTypes", "jpg,jpeg,png,gif,webp,md,pdf");
        ReflectionTestUtils.setField(cosService, "maxSize", 52428800L);
    }

    private void configCos(String region) {
        when(settingService.getValue("cos_secret_id")).thenReturn("AKIDexample");
        when(settingService.getValue("cos_secret_key")).thenReturn("secretkeyexample");
        when(settingService.getValue("cos_region")).thenReturn(region);
        when(settingService.getValue("cos_bucket_name")).thenReturn("mybucket-123");
    }

    @Test
    void upload_success_buildsPublicUrl() {
        configCos("ap-nanjing");
        MockMultipartFile file = new MockMultipartFile("file", "pic.jpg", "image/jpeg", "imgdata".getBytes());
        try (MockedConstruction<COSClient> ignored = mockConstruction(COSClient.class)) {
            Map<String, String> r = cosService.upload(file);
            assertTrue(r.get("url").startsWith("https://mybucket-123.cos.ap-nanjing.myqcloud.com/uploads/"));
            assertTrue(r.get("url").endsWith(".jpg"));
            assertEquals("pic.jpg", r.get("name"));
        }
    }

    @Test
    void upload_stripsCosPrefixFromRegion() {
        configCos("cos.ap-guangzhou");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "x".getBytes());
        try (MockedConstruction<COSClient> ignored = mockConstruction(COSClient.class)) {
            Map<String, String> r = cosService.upload(file);
            assertTrue(r.get("url").contains(".cos.ap-guangzhou.myqcloud.com/"));
        }
    }

    @Test
    void upload_cosClientThrows_wrappedAsBusinessException() {
        configCos("ap-nanjing");
        MockMultipartFile file = new MockMultipartFile("file", "pic.png", "image/png", "d".getBytes());
        try (MockedConstruction<COSClient> ignored = mockConstruction(COSClient.class,
                (mock, ctx) -> when(mock.putObject(any())).thenThrow(new RuntimeException("cos down")))) {
            BusinessException ex = assertThrows(BusinessException.class, () -> cosService.upload(file));
            assertTrue(ex.getMessage().contains("上传到 COS 失败"));
        }
    }

    @Test
    void readUploadObject_readsMarkdownBytes() throws Exception {
        configCos("ap-nanjing");
        byte[] bytes = "# 作业".getBytes(StandardCharsets.UTF_8);
        COSObject object = mock(COSObject.class);
        COSObjectInputStream in = new COSObjectInputStream(
                new ByteArrayInputStream(bytes),
                mock(org.apache.http.client.methods.HttpRequestBase.class));
        when(object.getObjectContent()).thenReturn(in);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        try (MockedConstruction<COSClient> ignored = mockConstruction(COSClient.class, (mock, ctx) -> {
            when(mock.getObjectMetadata("mybucket-123", "uploads/2026-11-15/a.md")).thenReturn(metadata);
            when(mock.getObject("mybucket-123", "uploads/2026-11-15/a.md")).thenReturn(object);
        })) {
            byte[] result = cosService.readUploadObject(
                    "https://mybucket-123.cos.ap-nanjing.myqcloud.com/uploads/2026-11-15/a.md", 1024);
            assertArrayEquals(bytes, result);
        }
    }

    @Test
    void readUploadObject_rejectsNonOwnedHostAndNonMarkdown() {
        configCos("ap-nanjing");
        BusinessException host = assertThrows(BusinessException.class, () -> cosService.readUploadObject(
                "https://evil.example.com/uploads/2026-11-15/a.md", 1024));
        assertTrue(host.getMessage().contains("不在本站对象存储"));

        BusinessException type = assertThrows(BusinessException.class, () -> cosService.readUploadObject(
                "https://mybucket-123.cos.ap-nanjing.myqcloud.com/uploads/2026-11-15/a.pdf", 1024));
        assertTrue(type.getMessage().contains("Markdown"));
    }

    @Test
    void testConnection_success_uploadsAndDeletesProbe() {
        configCos("ap-nanjing");
        try (MockedConstruction<COSClient> ignored = mockConstruction(COSClient.class)) {
            assertDoesNotThrow(() -> cosService.testConnection());
        }
    }

    @Test
    void testConnection_cosThrows_wrappedAsBusinessException() {
        configCos("ap-nanjing");
        try (MockedConstruction<COSClient> ignored = mockConstruction(COSClient.class,
                (mock, ctx) -> when(mock.putObject(any())).thenThrow(new RuntimeException("net error")))) {
            BusinessException ex = assertThrows(BusinessException.class, () -> cosService.testConnection());
            assertTrue(ex.getMessage().contains("COS 连通性检测失败"));
        }
    }
}
