package com.sap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Note;
import com.sap.entity.NoteDownload;
import com.sap.entity.NoteView;
import com.sap.entity.User;
import com.sap.mapper.NoteDownloadMapper;
import com.sap.mapper.NoteMapper;
import com.sap.mapper.NoteViewMapper;
import com.sap.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoteServiceTest extends BaseUnitTest {

    @Mock NoteMapper noteMapper;
    @Mock NoteViewMapper noteViewMapper;
    @Mock NoteDownloadMapper noteDownloadMapper;
    @Mock UserMapper userMapper;

    @InjectMocks NoteService service;

    private Note note(long id, String title, String content) {
        Note n = new Note();
        n.setId(id);
        n.setTitle(title);
        n.setContent(content);
        n.setAuthorId(7L);
        n.setViewCount(3);
        n.setDownloadCount(2);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    private User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setStudentId("S" + id);
        return u;
    }

    // ============ listNotes ============

    @Test
    void listNotes_populatesRecordsWithAuthorAndWordCount() {
        Note n = note(1, "标题", "0123456789"); // 10 chars
        when(noteMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Note> p = inv.getArgument(0);
            p.setRecords(List.of(n));
            p.setTotal(1);
            return p;
        });
        when(userMapper.selectById(7L)).thenReturn(user(7, "作者甲"));

        Page<Map<String, Object>> result = service.listNotes(1, 20, "标");

        assertEquals(1, result.getRecords().size());
        Map<String, Object> item = result.getRecords().get(0);
        assertEquals("标题", item.get("title"));
        assertEquals("作者甲", item.get("authorName"));
        assertEquals(10, item.get("wordCount"));
        assertEquals(1, item.get("readMinutes"));
        assertEquals(3, item.get("viewCount"));
        assertEquals(2, item.get("downloadCount"));
    }

    @Test
    void listNotes_blankKeyword_andNullCountsDefaultToZero_unknownAuthor() {
        Note n = new Note();
        n.setId(5L);
        n.setTitle("无作者");
        n.setContent(null);
        n.setAuthorId(null);
        n.setViewCount(null);
        n.setDownloadCount(null);
        n.setDescription(null);
        when(noteMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Note> p = inv.getArgument(0);
            p.setRecords(List.of(n));
            p.setTotal(1);
            return p;
        });

        Page<Map<String, Object>> result = service.listNotes(1, 20, "   ");

        Map<String, Object> item = result.getRecords().get(0);
        assertEquals("未知", item.get("authorName"));
        assertEquals(0, item.get("viewCount"));
        assertEquals(0, item.get("downloadCount"));
        assertEquals(0, item.get("wordCount"));
        assertEquals(1, item.get("readMinutes"));
        assertEquals("", item.get("description"));
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void listNotes_authorLookupReturnsNullName_usesUnknown() {
        Note n = note(2, "t", "abc");
        when(noteMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Note> p = inv.getArgument(0);
            p.setRecords(List.of(n));
            p.setTotal(1);
            return p;
        });
        when(userMapper.selectById(7L)).thenReturn(null);

        Page<Map<String, Object>> result = service.listNotes(1, 20, null);
        assertEquals("未知", result.getRecords().get(0).get("authorName"));
    }

    // ============ getDetail ============

    @Test
    void getDetail_recordsViewAndReturnsDetail() {
        Note n = note(1, "标题", "正文");
        when(noteMapper.selectById(1L)).thenReturn(n);
        when(noteViewMapper.selectOne(any())).thenReturn(null); // first view -> insert
        when(noteViewMapper.selectCount(any())).thenReturn(5L);
        when(userMapper.selectById(7L)).thenReturn(user(7, "作者"));

        Map<String, Object> result = service.getDetail(1L, 100L);

        assertEquals("标题", result.get("title"));
        assertEquals("正文", result.get("content"));
        assertEquals("作者", result.get("authorName"));
        verify(noteViewMapper).insert(any(NoteView.class));
        verify(noteMapper, atLeastOnce()).updateById(any(Note.class));
    }

    @Test
    void getDetail_notFound_throws() {
        when(noteMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDetail(99L, 1L));
        assertEquals("笔记不存在", ex.getMessage());
    }

    @Test
    void getDetail_nullAuthorId_unknownAuthor() {
        Note n = note(1, "t", "c");
        n.setAuthorId(null);
        when(noteMapper.selectById(1L)).thenReturn(n);
        when(noteViewMapper.selectOne(any())).thenReturn(null);
        when(noteViewMapper.selectCount(any())).thenReturn(1L);

        Map<String, Object> result = service.getDetail(1L, 1L);
        assertEquals("未知", result.get("authorName"));
    }

    // ============ recordView (via getDetail) branches ============

    @Test
    void recordView_existingRecord_incrementsCount() {
        Note n = note(1, "t", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);
        NoteView existing = new NoteView();
        existing.setId(9L);
        existing.setViewCount(2);
        when(noteViewMapper.selectOne(any())).thenReturn(existing);
        when(noteViewMapper.selectCount(any())).thenReturn(2L);
        when(userMapper.selectById(any())).thenReturn(user(7, "a"));

        service.getDetail(1L, 100L);

        assertEquals(3, existing.getViewCount());
        verify(noteViewMapper).updateById(existing);
        verify(noteViewMapper, never()).insert(any());
    }

    @Test
    void recordView_duplicateKeyFallback_incrementsExisting() {
        Note n = note(1, "t", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);
        NoteView again = new NoteView();
        again.setViewCount(4);
        // first selectOne -> null (insert path), second selectOne (in catch) -> again
        when(noteViewMapper.selectOne(any())).thenReturn(null).thenReturn(again);
        doThrow(new DuplicateKeyException("dup")).when(noteViewMapper).insert(any(NoteView.class));
        when(noteViewMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectById(any())).thenReturn(user(7, "a"));

        service.getDetail(1L, 100L);

        assertEquals(5, again.getViewCount());
        verify(noteViewMapper).updateById(again);
    }

    @Test
    void recordView_duplicateKeyFallback_secondSelectNull_noUpdate() {
        Note n = note(1, "t", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);
        when(noteViewMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        doThrow(new DuplicateKeyException("dup")).when(noteViewMapper).insert(any(NoteView.class));
        when(noteViewMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectById(any())).thenReturn(user(7, "a"));

        service.getDetail(1L, 100L);
        // no NPE; updateById only on note, not noteView for the again branch
        verify(noteViewMapper, never()).updateById(any());
    }

    // ============ addNote ============

    @Test
    void addNote_emptyContent_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addNote("t", "d", "   ", 1L));
        assertEquals("笔记内容不能为空", ex.getMessage());
        ex = assertThrows(BusinessException.class, () -> service.addNote("t", "d", null, 1L));
        assertEquals("笔记内容不能为空", ex.getMessage());
    }

    @Test
    void addNote_usesProvidedTitle() {
        service.addNote("我的标题", "简介", "正文内容", 1L);
        ArgumentCaptor<Note> cap = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).insert(cap.capture());
        assertEquals("我的标题", cap.getValue().getTitle());
        assertEquals(0, cap.getValue().getViewCount());
        assertEquals(0, cap.getValue().getDeleted());
    }

    @Test
    void addNote_resolvesTitleFromMarkdownHeading() {
        service.addNote(null, "d", "## Markdown标题\n正文", 1L);
        ArgumentCaptor<Note> cap = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).insert(cap.capture());
        assertEquals("Markdown标题", cap.getValue().getTitle());
    }

    @Test
    void addNote_resolvesTitleFromFirstLine_truncatedTo20() {
        String content = "这是一段没有标题的很长很长的正文超过二十个字符以测试截断逻辑";
        service.addNote("  ", null, content, 1L);
        ArgumentCaptor<Note> cap = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).insert(cap.capture());
        assertEquals(20, cap.getValue().getTitle().length());
        assertEquals(content.substring(0, 20), cap.getValue().getTitle());
    }

    @Test
    void addNote_titleFromShortFirstLine_notTruncated() {
        service.addNote(null, null, "短标题", 1L);
        ArgumentCaptor<Note> cap = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).insert(cap.capture());
        assertEquals("短标题", cap.getValue().getTitle());
    }

    // ============ updateNote ============

    @Test
    void updateNote_notFound_throws() {
        when(noteMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateNote(99L, "t", "d", "c"));
        assertEquals("笔记不存在", ex.getMessage());
    }

    @Test
    void updateNote_updatesAllFields() {
        Note n = note(1, "old", "oldContent");
        when(noteMapper.selectById(1L)).thenReturn(n);

        service.updateNote(1L, "newTitle", "newDesc", "newContent");

        assertEquals("newTitle", n.getTitle());
        assertEquals("newDesc", n.getDescription());
        assertEquals("newContent", n.getContent());
        verify(noteMapper).updateById(n);
    }

    @Test
    void updateNote_blankDescriptionSetsNull() {
        Note n = note(1, "old", "oldContent");
        when(noteMapper.selectById(1L)).thenReturn(n);

        service.updateNote(1L, "t", "   ", null);
        assertNull(n.getDescription());
    }

    @Test
    void updateNote_blankTitleWithContent_resolvesTitleFromContent() {
        Note n = note(1, "old", "oldContent");
        when(noteMapper.selectById(1L)).thenReturn(n);

        service.updateNote(1L, "  ", null, "# 从正文解析的标题\n内容");
        assertEquals("从正文解析的标题", n.getTitle());
    }

    @Test
    void updateNote_nullTitleAndDescription_keepsTitle_descUntouched() {
        Note n = note(1, "保留标题", "oldContent");
        n.setDescription("原简介");
        when(noteMapper.selectById(1L)).thenReturn(n);

        service.updateNote(1L, null, null, null);
        assertEquals("保留标题", n.getTitle());
        assertEquals("原简介", n.getDescription());
    }

    // ============ deleteNote ============

    @Test
    void deleteNote_cascadesViewsAndDownloads() {
        service.deleteNote(3L);
        verify(noteMapper).deleteById(3L);
        verify(noteViewMapper).delete(any());
        verify(noteDownloadMapper).delete(any());
    }

    // ============ recordDownload ============

    @Test
    void recordDownload_noteNotFound_throws() {
        when(noteMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDownload(99L, 1L));
        assertEquals("笔记不存在", ex.getMessage());
    }

    @Test
    void recordDownload_existingRecord_increments() {
        Note n = note(1, "t", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);
        NoteDownload existing = new NoteDownload();
        existing.setDownloadCount(2);
        when(noteDownloadMapper.selectOne(any())).thenReturn(existing);
        when(noteDownloadMapper.selectCount(any())).thenReturn(2L);

        service.recordDownload(1L, 100L);

        assertEquals(3, existing.getDownloadCount());
        verify(noteDownloadMapper).updateById(existing);
        verify(noteMapper).updateById(n);
        assertEquals(2, n.getDownloadCount());
    }

    @Test
    void recordDownload_firstTime_insert() {
        Note n = note(1, "t", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);
        when(noteDownloadMapper.selectOne(any())).thenReturn(null);
        when(noteDownloadMapper.selectCount(any())).thenReturn(1L);

        service.recordDownload(1L, 100L);
        verify(noteDownloadMapper).insert(any(NoteDownload.class));
        assertEquals(1, n.getDownloadCount());
    }

    @Test
    void recordDownload_duplicateKeyFallback_incrementsExisting() {
        Note n = note(1, "t", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);
        NoteDownload again = new NoteDownload();
        again.setDownloadCount(7);
        when(noteDownloadMapper.selectOne(any())).thenReturn(null).thenReturn(again);
        doThrow(new DuplicateKeyException("dup")).when(noteDownloadMapper).insert(any(NoteDownload.class));
        when(noteDownloadMapper.selectCount(any())).thenReturn(1L);

        service.recordDownload(1L, 100L);
        assertEquals(8, again.getDownloadCount());
        verify(noteDownloadMapper).updateById(again);
    }

    @Test
    void recordDownload_duplicateKeyFallback_secondSelectNull_noUpdate() {
        Note n = note(1, "t", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);
        when(noteDownloadMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        doThrow(new DuplicateKeyException("dup")).when(noteDownloadMapper).insert(any(NoteDownload.class));
        when(noteDownloadMapper.selectCount(any())).thenReturn(1L);

        service.recordDownload(1L, 100L);
        verify(noteDownloadMapper, never()).updateById(any());
    }

    // ============ getStats ============

    @Test
    void getStats_notFound_throws() {
        when(noteMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getStats(99L));
        assertEquals("笔记不存在", ex.getMessage());
    }

    @Test
    void getStats_returnsViewsAndDownloadsWithUsers() {
        Note n = note(1, "标题", "c");
        when(noteMapper.selectById(1L)).thenReturn(n);

        NoteView v = new NoteView();
        v.setUserId(11L);
        v.setViewCount(2);
        v.setLastViewAt(LocalDateTime.now());
        when(noteViewMapper.selectList(any())).thenReturn(List.of(v));

        NoteDownload d = new NoteDownload();
        d.setUserId(12L);
        d.setDownloadCount(1);
        d.setLastDownloadAt(LocalDateTime.now());
        when(noteDownloadMapper.selectList(any())).thenReturn(List.of(d));

        when(userMapper.selectById(11L)).thenReturn(user(11, "浏览者"));
        when(userMapper.selectById(12L)).thenReturn(null); // unknown downloader

        Map<String, Object> result = service.getStats(1L);

        assertEquals("标题", result.get("title"));
        assertEquals(3, result.get("viewCount"));
        assertEquals(2, result.get("downloadCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> views = (List<Map<String, Object>>) result.get("views");
        assertEquals(1, views.size());
        assertEquals("浏览者", views.get(0).get("userName"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> downloads = (List<Map<String, Object>>) result.get("downloads");
        assertEquals("未知", downloads.get(0).get("userName"));
        assertEquals("", downloads.get(0).get("studentId"));
    }

    @Test
    void getStats_nullCounts_defaultZero() {
        Note n = new Note();
        n.setId(1L);
        n.setTitle("t");
        n.setViewCount(null);
        n.setDownloadCount(null);
        when(noteMapper.selectById(1L)).thenReturn(n);
        when(noteViewMapper.selectList(any())).thenReturn(List.of());
        when(noteDownloadMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.getStats(1L);
        assertEquals(0, result.get("viewCount"));
        assertEquals(0, result.get("downloadCount"));
    }
}
