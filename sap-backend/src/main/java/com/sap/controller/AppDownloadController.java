package com.sap.controller;

import cn.dev33.satoken.annotation.*;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/app/download")
public class AppDownloadController {
    private final AppDownloadService downloads;
    private final AppDownloadStore store;
    public AppDownloadController(AppDownloadService downloads, AppDownloadStore store) { this.downloads = downloads; this.store = store; }
    @PostMapping("/tickets") @OperationLog("申请 App 一次性下载凭证")
    public Result<?> ticket(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.ok(downloads.issue(StpUtil.getLoginIdAsLong(), request));
    }
    @GetMapping("/file") @OperationLog("使用 App 下载凭证")
    public void file(@RequestParam String ticket, HttpServletRequest request, HttpServletResponse response) throws IOException { downloads.download(ticket, request, response); }
    @GetMapping("/current") @OperationLog("下载当前 App 安装包")
    public void current(HttpServletRequest request, HttpServletResponse response) throws IOException { downloads.current(StpUtil.getLoginIdAsLong(), request, response); }
    @GetMapping("/admin") @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> view(@RequestParam(defaultValue = "30") int days) { return Result.ok(store.view(days)); }
    @PutMapping("/admin") @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR) @OperationLog("修改 App 下载防护配置")
    public Result<?> settings(@RequestBody AppDownloadStore.Settings settings) { return Result.ok(store.settings(settings)); }
    public record Mode(String mode, String revision) {}
    @PostMapping("/admin/mode") @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR) @OperationLog("切换 App 下载方式并重置防护计数")
    public Result<?> mode(@RequestBody Mode mode) { return Result.ok(store.changeMode(mode.mode(), mode.revision())); }
    @ExceptionHandler(com.sap.common.BusinessException.class)
    public org.springframework.http.ResponseEntity<Result<?>> failure(com.sap.common.BusinessException error, HttpServletRequest request) {
        request.setAttribute(com.sap.aspect.OperationLogAspect.CODE, error.getCode());
        return org.springframework.http.ResponseEntity.status(error.getCode()).body(Result.error(error.getCode(), error.getMessage()));
    }
}
