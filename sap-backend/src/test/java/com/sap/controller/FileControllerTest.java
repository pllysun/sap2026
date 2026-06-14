package com.sap.controller;

import com.sap.common.Result;
import com.sap.service.CosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileControllerTest {

    @Mock CosService cosService;

    @InjectMocks FileController controller;

    // ===================== cosStatus =====================
    @Test
    void cosStatus_configuredTrue() {
        when(cosService.isConfigured()).thenReturn(true);
        Result<?> result = controller.cosStatus();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(true, data.get("configured"));
    }

    @Test
    void cosStatus_configuredFalse() {
        when(cosService.isConfigured()).thenReturn(false);
        Result<?> result = controller.cosStatus();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(false, data.get("configured"));
    }

    // ===================== upload =====================
    @Test
    void upload_delegatesToService() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "x".getBytes());
        Map<String, String> uploaded = Map.of("url", "https://x.myqcloud.com/a.jpg");
        when(cosService.upload(any())).thenReturn(uploaded);

        Result<?> result = controller.upload(file);
        assertSame(uploaded, result.getData());
    }

    // ===================== batchUpload =====================
    @Test
    void batchUpload_uploadsNonEmptyFilesOnly() {
        MockMultipartFile f1 = new MockMultipartFile("files", "a.jpg", "image/jpeg", "x".getBytes());
        MockMultipartFile empty = new MockMultipartFile("files", "b.jpg", "image/jpeg", new byte[0]);
        MockMultipartFile f2 = new MockMultipartFile("files", "c.jpg", "image/jpeg", "y".getBytes());
        when(cosService.upload(any())).thenReturn(Map.of("url", "u"));

        Result<?> result = controller.batchUpload(new MultipartFile[]{f1, empty, f2});

        @SuppressWarnings("unchecked")
        List<Map<String, String>> data = (List<Map<String, String>>) result.getData();
        assertEquals(2, data.size());
        verify(cosService, times(2)).upload(any());
    }

    @Test
    void batchUpload_allEmpty_returnsEmptyList() {
        MockMultipartFile empty = new MockMultipartFile("files", "b.jpg", "image/jpeg", new byte[0]);
        Result<?> result = controller.batchUpload(new MultipartFile[]{empty});
        @SuppressWarnings("unchecked")
        List<Map<String, String>> data = (List<Map<String, String>>) result.getData();
        assertTrue(data.isEmpty());
        verify(cosService, never()).upload(any());
    }

    // ===================== download (only rejected branches; no real network) =====================
    @Test
    void download_invalidUri_returns400() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        // a URI that triggers an exception in URI.create (illegal characters)
        controller.download("http://exa mple.com/ ^bad", "file", response);
        assertEquals(400, response.getStatus());
    }

    @Test
    void download_nonHttpScheme_returns403() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.download("ftp://files.myqcloud.com/a.txt", "file", response);
        assertEquals(403, response.getStatus());
    }

    @Test
    void download_fileScheme_returns403() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.download("file:///etc/passwd", "file", response);
        assertEquals(403, response.getStatus());
    }

    @Test
    void download_nonWhitelistedHost_returns403() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.download("https://evil.example.com/a.txt", "file", response);
        assertEquals(403, response.getStatus());
    }

    @Test
    void download_internalMetadataHost_returns403() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.download("http://169.254.169.254/latest/meta-data", "file", response);
        assertEquals(403, response.getStatus());
    }

    @Test
    void download_nullHost_returns403() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        // scheme present but no host
        controller.download("https:///nohost", "file", response);
        assertEquals(403, response.getStatus());
    }
}
