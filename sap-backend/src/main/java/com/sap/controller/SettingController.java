package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Setting;
import com.sap.service.CosService;
import com.sap.service.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/setting")
public class SettingController {

    @Autowired
    private SettingService settingService;

    @Autowired
    private CosService cosService;

    @GetMapping("/value")
    @OperationLog("查询系统设置值")
    public Result<?> getValue(@RequestParam String key) {
        return Result.ok(settingService.getValue(key));
    }

    /**
     * 公开接口：获取页脚与二维码设置（免登录）
     */
    @GetMapping("/public")
    public Result<?> publicSettings() {
        return Result.ok(settingService.getPublicSettings());
    }

    @PutMapping
    @OperationLog("修改系统设置")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> update(@RequestBody Setting setting) {
        settingService.updateSetting(setting);
        return Result.ok("更新成功");
    }

    @GetMapping("/cos-config")
    @OperationLog("查询COS配置")
    public Result<?> getCosConfig() {
        return Result.ok(cosService.getMaskedConfig());
    }

    @PutMapping("/cos-config")
    @OperationLog("修改COS配置")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> updateCosConfig(@RequestBody Map<String, String> config) {
        cosService.saveConfig(config);
        return Result.ok("配置已保存");
    }

    @PostMapping("/cos-test")
    @OperationLog("测试COS连通性")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> testCos() {
        cosService.testConnection();
        return Result.ok("连通性检测通过");
    }

    @Autowired
    private com.sap.service.CacheService cacheService;

    @PostMapping("/cache/refresh")
    @OperationLog("刷新内存缓存")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> refreshCache() {
        cacheService.refreshUsers();
        cacheService.refreshPositions();
        return Result.ok("缓存已刷新");
    }
}

