package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyHomeworkDoc;
import com.sap.entity.StudyMaterial;
import com.sap.mapper.StudyActivityMapper;
import com.sap.mapper.StudyHomeworkDocMapper;
import com.sap.mapper.StudyMaterialMapper;
import com.sap.mapper.StudyMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 覆盖作业预览：缓存命中、首次抓取入库、编码、权限、格式降级、抓取失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeworkPreviewServiceTest extends BaseUnitTest {

    @Mock StudyActivityMapper studyActivityMapper;
    @Mock StudyMaterialMapper studyMaterialMapper;
    @Mock StudyMemberMapper studyMemberMapper;
    @Mock StudyHomeworkDocMapper studyHomeworkDocMapper;
    @Mock CosService cosService;

    @InjectMocks HomeworkPreviewService service;

    private StudyActivity activity(int status) {
        StudyActivity a = new StudyActivity();
        a.setId(1L);
        a.setStatus(status);
        a.setCurrentWeek(2);
        a.setActiveWeek(2);
        return a;
    }

    private StudyMaterial homework(String fileName, LocalDateTime publishTime) {
        StudyMaterial m = new StudyMaterial();
        m.setId(9L);
        m.setActivityId(1L);
        m.setWeek(2);
        m.setFileType(1);
        m.setTitle("第二周作业");
        m.setFileName(fileName);
        m.setFileUrl("https://bucket.cos.ap-nanjing.myqcloud.com/uploads/2026-11-15/abc.md");
        m.setPublishTime(publishTime);
        return m;
    }

    private StudyHomeworkDoc doc(String content) {
        StudyHomeworkDoc d = new StudyHomeworkDoc();
        d.setMaterialId(9L);
        d.setActivityId(1L);
        d.setWeek(2);
        d.setContentFormat("markdown");
        d.setContent(content);
        d.setSourceBytes(content.getBytes(StandardCharsets.UTF_8).length);
        d.setUpdatedAt(LocalDateTime.now());
        return d;
    }

    private void basicMocks(StudyMaterial material) {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1));
        when(studyMaterialMapper.selectOne(any())).thenReturn(material);
        when(studyMemberMapper.selectCount(any())).thenReturn(1L);
    }

    @Test
    void preview_cachedContent_returnsWithoutCos() {
        basicMocks(homework("week2.md", null));
        when(studyHomeworkDocMapper.selectOne(any())).thenReturn(doc("# 已缓存"));

        Map<String, Object> r = service.preview(1L, 2, 50L, false);

        assertEquals(true, r.get("previewable"));
        assertEquals("# 已缓存", r.get("content"));
        assertEquals("markdown", r.get("format"));
        verifyNoInteractions(cosService);
    }

    @Test
    void preview_missingDoc_fetchesAndStores() {
        basicMocks(homework("week2.md", null));
        when(studyHomeworkDocMapper.selectOne(any())).thenReturn(null);
        byte[] bytes = "# 新作业".getBytes(StandardCharsets.UTF_8);
        when(cosService.readUploadObject(anyString(), anyLong())).thenReturn(bytes);

        Map<String, Object> r = service.preview(1L, 2, 50L, false);

        assertEquals(true, r.get("previewable"));
        assertEquals("# 新作业", r.get("content"));
        ArgumentCaptor<StudyHomeworkDoc> captor = ArgumentCaptor.forClass(StudyHomeworkDoc.class);
        verify(studyHomeworkDocMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getMaterialId());
        assertEquals("markdown", captor.getValue().getContentFormat());
        assertEquals(bytes.length, captor.getValue().getSourceBytes());
        assertNotNull(captor.getValue().getFetchedAt());
    }

    @Test
    void preview_gb18030Content_decodesCorrectly() {
        basicMocks(homework("week2.md", null));
        when(studyHomeworkDocMapper.selectOne(any())).thenReturn(null);
        byte[] bytes = "# 中文作业".getBytes(Charset.forName("GB18030"));
        when(cosService.readUploadObject(anyString(), anyLong())).thenReturn(bytes);

        Map<String, Object> r = service.preview(1L, 2, 50L, false);

        assertEquals("# 中文作业", r.get("content"));
    }

    @Test
    void preview_scheduledHomework_studentForbidden() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1));
        when(studyMaterialMapper.selectOne(any())).thenReturn(homework("week2.md", LocalDateTime.now().plusDays(1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.preview(1L, 2, 50L, false));

        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("尚未发布"));
        verifyNoInteractions(cosService);
    }

    @Test
    void preview_scheduledHomework_adminAllowed() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1));
        when(studyMaterialMapper.selectOne(any())).thenReturn(homework("week2.md", LocalDateTime.now().plusDays(1)));
        when(studyHomeworkDocMapper.selectOne(any())).thenReturn(doc("# 定时作业"));

        Map<String, Object> r = service.preview(1L, 2, 50L, true);

        assertEquals(true, r.get("previewable"));
        assertEquals("# 定时作业", r.get("content"));
    }

    @Test
    void preview_notJoined_studentForbidden() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1));
        when(studyMaterialMapper.selectOne(any())).thenReturn(homework("week2.md", null));
        when(studyMemberMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.preview(1L, 2, 50L, false));

        assertEquals(403, ex.getCode());
    }

    @Test
    void preview_nonMarkdownFile_returnsFallback() {
        basicMocks(homework("week2.pdf", null));

        Map<String, Object> r = service.preview(1L, 2, 50L, false);

        assertEquals(false, r.get("previewable"));
        assertTrue(String.valueOf(r.get("reason")).contains("不支持在线预览"));
        verifyNoInteractions(cosService);
    }

    @Test
    void preview_cosFailure_returnsFallbackInsteadOfThrowing() {
        basicMocks(homework("week2.md", null));
        when(studyHomeworkDocMapper.selectOne(any())).thenReturn(null);
        when(cosService.readUploadObject(anyString(), anyLong()))
                .thenThrow(new BusinessException("对象不存在"));

        Map<String, Object> r = service.preview(1L, 2, 50L, false);

        assertEquals(false, r.get("previewable"));
        assertTrue(String.valueOf(r.get("reason")).contains("请下载查看"));
        verify(studyHomeworkDocMapper, never()).insert(any(StudyHomeworkDoc.class));
    }

    @Test
    void preview_concurrentSameMaterial_returnsExistingDoc() {
        basicMocks(homework("week2.md", null));
        StudyHomeworkDoc existing = doc("# 并发已写入");
        when(studyHomeworkDocMapper.selectOne(any())).thenReturn(null, existing);
        when(cosService.readUploadObject(anyString(), anyLong()))
                .thenReturn("# 我的抓取".getBytes(StandardCharsets.UTF_8));
        doThrow(new DuplicateKeyException("duplicate")).when(studyHomeworkDocMapper).insert(any());

        Map<String, Object> r = service.preview(1L, 2, 50L, false);

        assertEquals(true, r.get("previewable"));
        assertEquals("# 并发已写入", r.get("content"));
    }

    @Test
    void evict_deletesByActivityAndWeek() {
        service.evict(1L, 2);

        verify(studyHomeworkDocMapper).delete(any());
    }
}
