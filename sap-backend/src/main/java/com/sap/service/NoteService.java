package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.BusinessException;
import com.sap.entity.Note;
import com.sap.entity.NoteDownload;
import com.sap.entity.NoteView;
import com.sap.entity.User;
import com.sap.mapper.NoteDownloadMapper;
import com.sap.mapper.NoteMapper;
import com.sap.mapper.NoteViewMapper;
import com.sap.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NoteService {

    @Autowired
    private NoteMapper noteMapper;
    @Autowired
    private NoteViewMapper noteViewMapper;
    @Autowired
    private NoteDownloadMapper noteDownloadMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 分页查询笔记列表
     */
    public Page<Map<String, Object>> listNotes(int current, int size, String keyword) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Note::getTitle, keyword);
        }
        wrapper.orderByDesc(Note::getCreatedAt);

        Page<Note> page = noteMapper.selectPage(new Page<>(current, size), wrapper);

        // 批量查作者名
        Set<Long> authorIds = page.getRecords().stream()
                .map(Note::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> authorNameMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            for (Long aid : authorIds) {
                User u = userMapper.selectById(aid);
                authorNameMap.put(aid, u != null ? u.getName() : "未知");
            }
        }

        Page<Map<String, Object>> result = new Page<>(current, size, page.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Note note : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", note.getId());
            item.put("title", note.getTitle());
            item.put("authorName", authorNameMap.getOrDefault(note.getAuthorId(), "未知"));
            item.put("viewCount", note.getViewCount() != null ? note.getViewCount() : 0);
            item.put("downloadCount", note.getDownloadCount() != null ? note.getDownloadCount() : 0);
            item.put("createdAt", note.getCreatedAt());
            item.put("description", note.getDescription() != null ? note.getDescription() : "");
            // 字数 & 预计阅读时间
            int wordCount = note.getContent() != null ? note.getContent().length() : 0;
            item.put("wordCount", wordCount);
            item.put("readMinutes", Math.max(1, (int) Math.ceil(wordCount / 400.0)));
            records.add(item);
        }
        result.setRecords(records);
        return result;
    }

    /**
     * 查看笔记详情 + 记录浏览
     */
    public Map<String, Object> getDetail(Long noteId, Long userId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) throw new BusinessException("笔记不存在");

        // 记录浏览
        recordView(noteId, userId);

        // 刷新浏览数
        note = noteMapper.selectById(noteId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", note.getId());
        result.put("title", note.getTitle());
        result.put("description", note.getDescription());
        result.put("content", note.getContent());
        result.put("viewCount", note.getViewCount() != null ? note.getViewCount() : 0);
        result.put("downloadCount", note.getDownloadCount() != null ? note.getDownloadCount() : 0);
        result.put("createdAt", note.getCreatedAt());

        User author = note.getAuthorId() != null ? userMapper.selectById(note.getAuthorId()) : null;
        result.put("authorName", author != null ? author.getName() : "未知");

        return result;
    }

    /**
     * 新增笔记
     */
    public void addNote(String title, String description, String content, Long authorId) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("笔记内容不能为空");
        }

        Note note = new Note();
        note.setTitle(resolveTitle(title, content));
        note.setDescription(description);
        note.setContent(content);
        note.setAuthorId(authorId);
        note.setViewCount(0);
        note.setDownloadCount(0);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        note.setDeleted(0);
        noteMapper.insert(note);
    }

    /**
     * 编辑笔记
     */
    public void updateNote(Long id, String title, String description, String content) {
        Note note = noteMapper.selectById(id);
        if (note == null) throw new BusinessException("笔记不存在");

        if (title != null && !title.isBlank()) {
            note.setTitle(title);
        }
        // description 允许设为空字符串（清除简介）
        if (description != null) {
            note.setDescription(description.isBlank() ? null : description);
        }
        if (content != null && !content.isBlank()) {
            note.setContent(content);
            if (title == null || title.isBlank()) {
                note.setTitle(resolveTitle(null, content));
            }
        }
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
    }

    /**
     * 删除笔记
     */
    public void deleteNote(Long id) {
        noteMapper.deleteById(id);
    }

    /**
     * 记录浏览（upsert）
     */
    private void recordView(Long noteId, Long userId) {
        NoteView existing = noteViewMapper.selectOne(
                new LambdaQueryWrapper<NoteView>()
                        .eq(NoteView::getNoteId, noteId)
                        .eq(NoteView::getUserId, userId)
        );
        if (existing != null) {
            existing.setViewCount(existing.getViewCount() + 1);
            existing.setLastViewAt(LocalDateTime.now());
            noteViewMapper.updateById(existing);
        } else {
            NoteView view = new NoteView();
            view.setNoteId(noteId);
            view.setUserId(userId);
            view.setViewCount(1);
            view.setLastViewAt(LocalDateTime.now());
            noteViewMapper.insert(view);
        }

        // 更新笔记总浏览数
        Note note = noteMapper.selectById(noteId);
        if (note != null) {
            long totalViews = noteViewMapper.selectCount(
                    new LambdaQueryWrapper<NoteView>().eq(NoteView::getNoteId, noteId)
            );
            note.setViewCount((int) totalViews);
            noteMapper.updateById(note);
        }
    }

    /**
     * 记录下载
     */
    public void recordDownload(Long noteId, Long userId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) throw new BusinessException("笔记不存在");

        NoteDownload existing = noteDownloadMapper.selectOne(
                new LambdaQueryWrapper<NoteDownload>()
                        .eq(NoteDownload::getNoteId, noteId)
                        .eq(NoteDownload::getUserId, userId)
        );
        if (existing != null) {
            existing.setDownloadCount(existing.getDownloadCount() + 1);
            existing.setLastDownloadAt(LocalDateTime.now());
            noteDownloadMapper.updateById(existing);
        } else {
            NoteDownload download = new NoteDownload();
            download.setNoteId(noteId);
            download.setUserId(userId);
            download.setDownloadCount(1);
            download.setLastDownloadAt(LocalDateTime.now());
            noteDownloadMapper.insert(download);
        }

        // 更新笔记总下载数
        long totalDownloads = noteDownloadMapper.selectCount(
                new LambdaQueryWrapper<NoteDownload>().eq(NoteDownload::getNoteId, noteId)
        );
        note.setDownloadCount((int) totalDownloads);
        noteMapper.updateById(note);
    }

    /**
     * 获取浏览/下载统计明细
     */
    public Map<String, Object> getStats(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) throw new BusinessException("笔记不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", note.getTitle());
        result.put("viewCount", note.getViewCount() != null ? note.getViewCount() : 0);
        result.put("downloadCount", note.getDownloadCount() != null ? note.getDownloadCount() : 0);

        // 浏览明细
        List<NoteView> views = noteViewMapper.selectList(
                new LambdaQueryWrapper<NoteView>()
                        .eq(NoteView::getNoteId, noteId)
                        .orderByDesc(NoteView::getLastViewAt)
        );
        List<Map<String, Object>> viewList = new ArrayList<>();
        for (NoteView v : views) {
            Map<String, Object> vm = new LinkedHashMap<>();
            User u = userMapper.selectById(v.getUserId());
            vm.put("userName", u != null ? u.getName() : "未知");
            vm.put("studentId", u != null ? u.getStudentId() : "");
            vm.put("viewCount", v.getViewCount());
            vm.put("lastViewAt", v.getLastViewAt());
            viewList.add(vm);
        }
        result.put("views", viewList);

        // 下载明细
        List<NoteDownload> downloads = noteDownloadMapper.selectList(
                new LambdaQueryWrapper<NoteDownload>()
                        .eq(NoteDownload::getNoteId, noteId)
                        .orderByDesc(NoteDownload::getLastDownloadAt)
        );
        List<Map<String, Object>> downloadList = new ArrayList<>();
        for (NoteDownload d : downloads) {
            Map<String, Object> dm = new LinkedHashMap<>();
            User u = userMapper.selectById(d.getUserId());
            dm.put("userName", u != null ? u.getName() : "未知");
            dm.put("studentId", u != null ? u.getStudentId() : "");
            dm.put("downloadCount", d.getDownloadCount());
            dm.put("lastDownloadAt", d.getLastDownloadAt());
            downloadList.add(dm);
        }
        result.put("downloads", downloadList);

        return result;
    }

    /**
     * 解析标题：
     * 1. 用户提供 → 直接用
     * 2. Markdown 第一个 # 标题
     * 3. 正文前 20 个字符
     */
    private String resolveTitle(String title, String content) {
        if (title != null && !title.isBlank()) return title.trim();

        // 尝试提取 Markdown 标题
        Matcher matcher = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE).matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 取第一行非空文本前 20 个字符
        String firstLine = content.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("无标题");
        return firstLine.length() > 20 ? firstLine.substring(0, 20) : firstLine;
    }

    /**
     * 提取纯文本摘要（去掉 Markdown 标记符号）
     */
    private String extractSummary(String content, int maxLen) {
        if (content == null || content.isBlank()) return "";
        // 简单去掉常见 Markdown 标记
        String plain = content
                .replaceAll("#{1,6}\\s*", "")
                .replaceAll("\\*{1,2}(.+?)\\*{1,2}", "$1")
                .replaceAll("!?\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("`{1,3}[^`]*`{1,3}", "")
                .replaceAll("\\n+", " ")
                .trim();
        return plain.length() > maxLen ? plain.substring(0, maxLen) + "..." : plain;
    }
}
