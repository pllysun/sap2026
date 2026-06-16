package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.AppVersionService;
import com.sap.vo.AppVersionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * App 版本 / 在线升级。版本元数据存数据库(Setting 表)，由管理端「App 版本发布」页维护。
 * <ul>
 *   <li>{@code GET /api/app/latest} —— 客户端取最新版本元数据（需会员登录，下载地址只对会员下发）。</li>
 *   <li>{@code GET /api/app/version} —— 管理端查看当前已发布版本（预填表单）。</li>
 *   <li>{@code POST /api/app/version/publish} —— 管理端上传 APK 发布新版本：APK 传腾讯云 COS、
 *       自动算 sha256/size、写入 DB。APK 与下载流量全部走 COS/CDN，不经过本服务器。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/app")
public class AppVersionController {

    @Autowired
    private AppVersionService appVersionService;

    /** 客户端检查更新（走 /api/** 登录拦截，仅会员可得下载地址）。 */
    @GetMapping("/latest")
    public Result<AppVersionVO> latest() {
        return Result.ok(appVersionService.getLatest());
    }

    /** 管理端：当前已发布版本（用于发布页预填/展示）。 */
    @GetMapping("/version")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<AppVersionVO> current() {
        return Result.ok(appVersionService.getLatest());
    }

    /** 管理端：发布新版本（上传 APK → COS，自动算 sha256/size，存 DB）。 */
    @PostMapping("/version/publish")
    @OperationLog("发布App新版本")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<AppVersionVO> publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam int versionCode,
            @RequestParam String versionName,
            @RequestParam(required = false) String changelog,
            @RequestParam(defaultValue = "false") boolean forceUpdate,
            @RequestParam(defaultValue = "1") int minSupportedVersionCode) {
        AppVersionVO vo = appVersionService.publish(
                file, versionCode, versionName, changelog, forceUpdate, minSupportedVersionCode);
        return Result.ok("发布成功", vo);
    }
}
