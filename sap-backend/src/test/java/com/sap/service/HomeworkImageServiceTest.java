package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.BaseUnitTest;
import com.sap.entity.StudyMaterial;
import com.sap.mapper.StudyMaterialMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeworkImageServiceTest extends BaseUnitTest {
    @Mock CosService cosService;
    @Mock HomeworkImageFetcher fetcher;
    @Mock StudyMaterialMapper materialMapper;
    @Mock StudyService studyService;
    @InjectMocks HomeworkImageService service;

    private static final String DOCUMENT = "https://dl.csuftsap.top/uploads/original.md";

    @Test
    void copiesImageRewritesMarkdownAndKeepsFailedImage() throws Exception {
        String markdown = "![ok](https://old.example/a.png) ![again](https://old.example/a.png) "
                + "![missing](https://old.example/missing.png)";
        when(cosService.readUploadObject(eq(DOCUMENT), anyLong())).thenReturn(markdown.getBytes(StandardCharsets.UTF_8));
        when(fetcher.fetch(eq("https://old.example/a.png"), anyLong()))
                .thenReturn(new HomeworkImageFetcher.ImageData(new byte[]{1, 2, 3}, "png", "image/png"));
        when(fetcher.fetch(eq("https://old.example/missing.png"), anyLong())).thenThrow(new IOException("404"));
        when(cosService.putObject(any(byte[].class), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(call -> "https://dl.csuftsap.top/" + call.getArgument(1, String.class));

        HomeworkImageService.LocalizeResult result = service.localizeAdminHomework(15L, 2, DOCUMENT, "week2.md");

        assertEquals(1, result.imagesCopied());
        assertEquals(1, result.imagesFailed());
        assertTrue(result.fileUrl().matches("https://dl\\.csuftsap\\.top/uploads/homework/15/2/[0-9a-f]{64}\\.md"));
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cosService, times(2)).putObject(bytes.capture(), keys.capture(), anyString(), nullable(String.class));
        assertTrue(keys.getAllValues().getFirst().matches("homework-images/[0-9a-f]{64}\\.png"));
        assertEquals(3, bytes.getAllValues().getFirst().length);
        String rewritten = new String(bytes.getAllValues().getLast(), StandardCharsets.UTF_8);
        assertEquals(2, rewritten.split("https://dl.csuftsap.top/homework-images/", -1).length - 1);
        assertTrue(rewritten.contains("![missing](https://old.example/missing.png)"));
    }

    @Test
    void repeatedSameImageDoesNotConsumeImageBudget() throws Exception {
        String markdown = ("![same](https://old.example/same.png) ").repeat(31);
        when(cosService.readUploadObject(eq(DOCUMENT), anyLong())).thenReturn(markdown.getBytes(StandardCharsets.UTF_8));
        when(fetcher.fetch(eq("https://old.example/same.png"), anyLong()))
                .thenReturn(new HomeworkImageFetcher.ImageData(new byte[]{1, 2, 3}, "png", "image/png"));
        when(cosService.putObject(any(byte[].class), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(call -> "https://dl.csuftsap.top/" + call.getArgument(1, String.class));

        HomeworkImageService.LocalizeResult result = service.localizeAdminHomework(15L, 2, DOCUMENT, "week2.md");

        assertEquals(1, result.imagesCopied());
        assertEquals(0, result.imagesFailed());
        assertNotNull(result.fileUrl());
        verify(fetcher, times(1)).fetch(anyString(), anyLong());
    }

    @Test
    void readFailureDoesNotTouchDocumentOrCos() {
        when(cosService.readUploadObject(eq(DOCUMENT), anyLong())).thenThrow(new IllegalStateException("not ours"));
        assertThrows(IllegalStateException.class,
                () -> service.localizeAdminHomework(15L, 1, DOCUMENT, "week1.md"));
        verifyNoInteractions(fetcher);
        verify(cosService, never()).putObject(any(), anyString(), anyString(), nullable(String.class));
    }

    @Test
    void noExternalImageLeavesOriginalDocumentUntouched() {
        String markdown = "![own](https://dl.csuftsap.top/homework-images/a.png)";
        when(cosService.readUploadObject(eq(DOCUMENT), anyLong())).thenReturn(markdown.getBytes(StandardCharsets.UTF_8));
        when(cosService.isOwnedPublicHost("dl.csuftsap.top")).thenReturn(true);
        HomeworkImageService.LocalizeResult result = service.localizeAdminHomework(15L, 1, DOCUMENT, "week1.md");
        assertNull(result.fileUrl());
        verifyNoInteractions(fetcher);
        verify(cosService, never()).putObject(any(), anyString(), anyString(), nullable(String.class));
    }

    @Test
    void migrationUpdatesOnlyAdministratorRowsAndEvictsPreview() throws Exception {
        StudyMaterial admin = row(1L, 1, DOCUMENT);
        StudyMaterial student = row(2L, 2, DOCUMENT);
        when(materialMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(admin, student));
        when(cosService.readUploadObject(eq(DOCUMENT), anyLong()))
                .thenReturn("![a](https://old.example/a.png)".getBytes(StandardCharsets.UTF_8));
        when(fetcher.fetch(anyString(), anyLong()))
                .thenReturn(new HomeworkImageFetcher.ImageData(new byte[]{1, 2, 3}, "png", "image/png"));
        when(cosService.putObject(any(byte[].class), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(call -> "https://dl.csuftsap.top/" + call.getArgument(1, String.class));
        when(studyService.replaceAdminHomeworkFileUrl(eq(admin), anyString())).thenReturn(true);

        Map<String, Object> result = service.localizeExisting(15L, 1);
        assertEquals(1, result.get("processed"));
        assertEquals(1, result.get("changed"));
        verify(studyService, times(1)).replaceAdminHomeworkFileUrl(eq(admin), anyString());
        verify(cosService, times(1)).readUploadObject(anyString(), anyLong());
    }

    @Test
    void migrationContinuesAfterOneDocumentFails() throws Exception {
        StudyMaterial broken = row(1L, 1, DOCUMENT);
        StudyMaterial good = row(2L, 1, "https://dl.csuftsap.top/uploads/good.md");
        good.setWeek(2);
        when(materialMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(broken, good));
        when(cosService.readUploadObject(eq(DOCUMENT), anyLong())).thenThrow(new IllegalStateException("missing"));
        when(cosService.readUploadObject(eq(good.getFileUrl()), anyLong()))
                .thenReturn("![a](https://old.example/a.png)".getBytes(StandardCharsets.UTF_8));
        when(fetcher.fetch(anyString(), anyLong()))
                .thenReturn(new HomeworkImageFetcher.ImageData(new byte[]{1, 2, 3}, "png", "image/png"));
        when(cosService.putObject(any(byte[].class), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(call -> "https://dl.csuftsap.top/" + call.getArgument(1, String.class));
        when(studyService.replaceAdminHomeworkFileUrl(eq(good), anyString())).thenReturn(true);

        Map<String, Object> result = service.localizeExisting(15L, null);
        assertEquals(2, result.get("processed"));
        assertEquals(1, result.get("changed"));
        assertEquals("failed", ((Map<?, ?>) ((List<?>) result.get("details")).getFirst()).get("status"));
        verify(studyService, never()).replaceAdminHomeworkFileUrl(eq(broken), anyString());
        verify(studyService).replaceAdminHomeworkFileUrl(eq(good), anyString());
    }

    private static StudyMaterial row(long id, int type, String url) {
        StudyMaterial row = new StudyMaterial();
        row.setId(id);
        row.setActivityId(15L);
        row.setWeek(1);
        row.setFileType(type);
        row.setFileUrl(url);
        row.setFileName("homework.md");
        return row;
    }
}
