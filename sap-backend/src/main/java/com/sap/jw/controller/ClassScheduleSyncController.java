package com.sap.jw.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.jw.service.ClassScheduleSyncService;
import com.sap.service.AppAccessService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/class-schedule")
public class ClassScheduleSyncController {
    private final ClassScheduleSyncService sync;
    private final AppAccessService access;
    public ClassScheduleSyncController(ClassScheduleSyncService sync, AppAccessService access) {
        this.sync = sync;
        this.access = access;
    }

    @PostMapping("/sync")
    @OperationLog("后台校验并刷新班级课表缓存")
    public Result<?> sync(@RequestBody ClassScheduleSyncService.Request request) {
        access.requireBasicAccess(StpUtil.getLoginIdAsLong());
        return Result.ok(sync.sync(request));
    }
}
