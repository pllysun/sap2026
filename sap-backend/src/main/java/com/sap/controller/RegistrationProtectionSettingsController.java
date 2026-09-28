package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.RegistrationProtectionSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setting/registration-protection")
@SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
public class RegistrationProtectionSettingsController {
    private final RegistrationProtectionSettingsService service;

    public RegistrationProtectionSettingsController(RegistrationProtectionSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public Result<?> get() { return Result.ok(service.reload()); }

    @PutMapping
    @OperationLog("修改注册防护配置")
    public Result<?> save(@Valid @RequestBody RegistrationProtectionSettingsService.Update update) {
        return Result.ok(service.save(update));
    }
}
