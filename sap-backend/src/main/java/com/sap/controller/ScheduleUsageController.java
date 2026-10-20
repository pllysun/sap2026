package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.Result;
import com.sap.service.AppAccessService;
import com.sap.service.ScheduleUsageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/schedule")
public class ScheduleUsageController {
    private final ScheduleUsageService usage;
    private final AppAccessService access;

    public ScheduleUsageController(ScheduleUsageService usage, AppAccessService access) {
        this.usage = usage;
        this.access = access;
    }

    @PostMapping("/usage")
    public Result<?> report(@RequestBody ScheduleUsageService.Report report) {
        long userId = StpUtil.getLoginIdAsLong();
        access.requireBasicAccess(userId);
        usage.report(userId, report);
        return Result.ok("已记录", null);
    }
}
