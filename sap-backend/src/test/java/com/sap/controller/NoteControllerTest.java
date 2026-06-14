package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.common.Result;
import com.sap.entity.Note;
import com.sap.mapper.NoteMapper;
import com.sap.service.NoteService;
import com.sap.service.PdfService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoteControllerTest extends BaseUnitTest {

    @Mock NoteService noteService;
    @Mock PdfService pdfService;
    @Mock NoteMapper noteMapper;

    @InjectMocks NoteController controller;

    @Test
    void list_delegates() {
        Page<Map<String, Object>> page = new Page<>(1, 20);
        when(noteService.listNotes(1, 20, "k")).thenReturn(page);
        Result<?> r = controller.list(1, 20, "k");
        assertEquals(200, r.getCode());
        assertSame(page, r.getData());
    }

    @Test
    void detail_member_returnsDetail() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            st.when(StpUtil::getRoleList).thenReturn(List.of("3"));
            when(noteService.getDetail(1L, 7L)).thenReturn(Map.of("id", 1L));
            Result<?> r = controller.detail(1L);
            assertEquals(200, r.getCode());
            assertEquals(Map.of("id", 1L), r.getData());
        }
    }

    @Test
    void detail_guestRole4_rejected403() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            st.when(StpUtil::getRoleList).thenReturn(List.of("4"));
            Result<?> r = controller.detail(1L);
            assertEquals(403, r.getCode());
            assertEquals("仅正式成员可查看笔记详情", r.getMessage());
            verify(noteService, never()).getDetail(any(), any());
        }
    }

    @Test
    void detail_emptyRoles_rejected403() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            st.when(StpUtil::getRoleList).thenReturn(List.of());
            Result<?> r = controller.detail(1L);
            assertEquals(403, r.getCode());
        }
    }

    @Test
    void add_delegates() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Map<String, String> params = Map.of("title", "t", "description", "d", "content", "c");
            Result<?> r = controller.add(params);
            verify(noteService).addNote("t", "d", "c", 7L);
            assertEquals("添加成功", r.getData());
        }
    }

    @Test
    void upload_readsFileAndAddsNote() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            MultipartFile file = new MockMultipartFile("file", "note.md", "text/markdown",
                    "# 内容".getBytes());
            Result<?> r = controller.upload(file, "标题", "简介");
            verify(noteService).addNote("标题", "简介", "# 内容", 7L);
            assertEquals("上传成功", r.getData());
        }
    }

    @Test
    void upload_readError_returnsError() throws Exception {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            MultipartFile file = mock(MultipartFile.class);
            when(file.getBytes()).thenThrow(new java.io.IOException("boom"));
            Result<?> r = controller.upload(file, null, null);
            assertEquals(500, r.getCode());
            assertTrue(r.getMessage().contains("文件读取失败"));
        }
    }

    @Test
    void update_delegates() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            Map<String, String> params = Map.of("title", "t", "description", "d", "content", "c");
            Result<?> r = controller.update(1L, params);
            verify(noteService).updateNote(1L, "t", "d", "c");
            assertEquals("更新成功", r.getData());
        }
    }

    @Test
    void delete_delegates() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            Result<?> r = controller.delete(1L);
            verify(noteService).deleteNote(1L);
            assertEquals("删除成功", r.getData());
        }
    }

    @Test
    void stats_delegates() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            when(noteService.getStats(1L)).thenReturn(Map.of("title", "t"));
            Result<?> r = controller.stats(1L);
            assertEquals(Map.of("title", "t"), r.getData());
        }
    }

    @Test
    void download_recordsDownload() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Result<?> r = controller.download(1L);
            verify(noteService).recordDownload(1L, 7L);
            assertEquals("下载已记录", r.getData());
        }
    }

    @Test
    void downloadPdf_noteNotFound_throws() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(noteMapper.selectById(1L)).thenReturn(null);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.downloadPdf(1L, resp));
            assertEquals("笔记不存在", ex.getMessage());
        }
    }

    @Test
    void downloadPdf_success_writesBytesAndRecords() throws Exception {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Note note = new Note();
            note.setId(1L);
            note.setTitle("笔记标题");
            note.setContent("正文");
            when(noteMapper.selectById(1L)).thenReturn(note);
            when(pdfService.markdownToPdf("笔记标题", "正文")).thenReturn(new byte[]{1, 2, 3});

            HttpServletResponse resp = mock(HttpServletResponse.class);
            ServletOutputStream os = mock(ServletOutputStream.class);
            when(resp.getOutputStream()).thenReturn(os);

            controller.downloadPdf(1L, resp);

            verify(noteService).recordDownload(1L, 7L);
            verify(resp).setContentType("application/pdf");
            verify(resp).setContentLength(3);
            verify(os).write(new byte[]{1, 2, 3});
            verify(os).flush();
        }
    }

    @Test
    void downloadPdf_pdfGenerationFails_throws() throws Exception {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Note note = new Note();
            note.setId(1L);
            note.setTitle("t");
            note.setContent("c");
            when(noteMapper.selectById(1L)).thenReturn(note);
            when(pdfService.markdownToPdf(any(), any())).thenThrow(new RuntimeException("fail"));

            HttpServletResponse resp = mock(HttpServletResponse.class);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.downloadPdf(1L, resp));
            assertTrue(ex.getMessage().contains("PDF 生成失败"));
        }
    }
}
