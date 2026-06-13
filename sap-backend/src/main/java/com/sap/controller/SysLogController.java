package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.SysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/log")
public class SysLogController {

    @Autowired
    private SysLogService sysLogService;

    @GetMapping("/list")
    @OperationLog("查询操作日志")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String httpMethod
    ) {
        return Result.ok(sysLogService.pageQuery(current, size, operationType, httpMethod));
    }

    @GetMapping("/stats")
    @OperationLog("查询日志统计")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> stats(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(sysLogService.getStats(days));
    }

    /**
     * 日历热力图数据（近一年每日操作次数）
     */
    @GetMapping("/calendar")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> calendar(@RequestParam(defaultValue = "365") int days) {
        return Result.ok(sysLogService.getDailyCalendar(days));
    }

    /**
     * 公开接口：供用户端获取日历热力图数据（免登录）
     */
    @GetMapping("/public/calendar")
    public Result<?> publicCalendar() {
        return Result.ok(sysLogService.getDailyCalendar(365));
    }
}
