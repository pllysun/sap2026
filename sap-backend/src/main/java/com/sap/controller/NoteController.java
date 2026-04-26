package com.sap.controller;

import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Note;
import com.sap.mapper.NoteMapper;
import com.sap.service.NoteService;
import com.sap.service.PdfService;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/note")
public class NoteController {

    @Autowired
    private NoteService noteService;
    @Autowired
    private PdfService pdfService;
    @Autowired
    private NoteMapper noteMapper;

    /**
     * 分页查询笔记列表（所有登录用户可访问）
     */
    @GetMapping("/list")
    @OperationLog("查询笔记列表")
    public Result<?> list(@RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String keyword) {
        return Result.ok(noteService.listNotes(current, size, keyword));
    }

    /**
     * 查看笔记详情（成员及以上：roleCode ≤ 3）
     */
    @GetMapping("/{id}")
    @OperationLog("查看笔记详情")
    public Result<?> detail(@PathVariable Long id) {
        // 权限校验：游客不允许查看详情
        Long userId = StpUtil.getLoginIdAsLong();
        List<String> roles = StpUtil.getRoleList();
        boolean isGuest = roles.isEmpty() || (roles.contains("4") && roles.stream().noneMatch(r -> {
            try { return Integer.parseInt(r) <= 3; } catch (Exception e) { return false; }
        }));
        if (isGuest) {
            return Result.error(403, "仅正式成员可查看笔记详情");
        }
        return Result.ok(noteService.getDetail(id, userId));
    }

    /**
     * 新增笔记（管理员：roleCode ≤ 2）
     */
    @PostMapping
    @OperationLog("新增笔记")
    public Result<?> add(@RequestBody Map<String, String> params) {
        StpUtil.checkRoleOr("0", "1", "2");
        Long authorId = StpUtil.getLoginIdAsLong();
        noteService.addNote(params.get("title"), params.get("description"), params.get("content"), authorId);
        return Result.ok("添加成功");
    }

    /**
     * 上传 .md 文件创建笔记（管理员：roleCode ≤ 2）
     */
    @PostMapping("/upload")
    @OperationLog("上传Markdown笔记")
    public Result<?> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "title", required = false) String title,
                            @RequestParam(value = "description", required = false) String description) {
        StpUtil.checkRoleOr("0", "1", "2");
        Long authorId = StpUtil.getLoginIdAsLong();

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            noteService.addNote(title, description, content, authorId);
            return Result.ok("上传成功");
        } catch (Exception e) {
            return Result.error("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 编辑笔记（管理员：roleCode ≤ 2）
     */
    @PutMapping("/{id}")
    @OperationLog("编辑笔记")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, String> params) {
        StpUtil.checkRoleOr("0", "1", "2");
        noteService.updateNote(id, params.get("title"), params.get("description"), params.get("content"));
        return Result.ok("更新成功");
    }

    /**
     * 删除笔记（管理员：roleCode ≤ 2）
     */
    @DeleteMapping("/{id}")
    @OperationLog("删除笔记")
    public Result<?> delete(@PathVariable Long id) {
        StpUtil.checkRoleOr("0", "1", "2");
        noteService.deleteNote(id);
        return Result.ok("删除成功");
    }

    /**
     * 查看浏览/下载统计明细（管理员：roleCode ≤ 2）
     */
    @GetMapping("/{id}/stats")
    @OperationLog("查看笔记统计")
    public Result<?> stats(@PathVariable Long id) {
        StpUtil.checkRoleOr("0", "1", "2");
        return Result.ok(noteService.getStats(id));
    }

    /**
     * 记录下载次数（成员及以上：roleCode ≤ 3）
     */
    @PostMapping("/{id}/download")
    @OperationLog("下载笔记")
    public Result<?> download(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        noteService.recordDownload(id, userId);
        return Result.ok("下载已记录");
    }

    /**
     * 下载笔记 PDF（成员及以上：roleCode ≤ 3）
     */
    @GetMapping("/{id}/pdf")
    @OperationLog("下载笔记PDF")
    public void downloadPdf(@PathVariable Long id, HttpServletResponse response) {
        Long userId = StpUtil.getLoginIdAsLong();

        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new com.sap.common.BusinessException("笔记不存在");
        }

        try {
            byte[] pdfBytes = pdfService.markdownToPdf(note.getTitle(), note.getContent());

            // 记录下载
            noteService.recordDownload(id, userId);

            // 设置响应头
            String fileName = (note.getTitle() != null ? note.getTitle() : "笔记") + ".pdf";
            response.setContentType("application/pdf");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8") + "\"");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new com.sap.common.BusinessException("PDF 生成失败: " + e.getMessage());
        }
    }
}
