package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.common.Result;
import com.sap.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 流量统计聚合接口。仅超管/会长可访问。
 * <p>注意：本控制器路径 /api/stats/** 已在 WebMvcConfig 的请求计数拦截器中排除，避免自计数。</p>
 */
@RestController
@RequestMapping("/api/stats")
@SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
public class StatsController {

    @Autowired
    private StatsService statsService;

    /** 概览卡片。 */
    @GetMapping("/overview")
    public Result<?> overview(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.overview(days));
    }

    /** COS 流量按用户聚合（上传/下载字节与次数，倒序）。 */
    @GetMapping("/cos/by-user")
    public Result<?> cosByUser(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.cosByUser(days));
    }

    /** COS 流量按日趋势。 */
    @GetMapping("/cos/trend")
    public Result<?> cosTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.cosTrend(days));
    }

    /** 全部接口请求总计 Top N。 */
    @GetMapping("/api/top")
    public Result<?> apiTop(@RequestParam(defaultValue = "7") int days,
                            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(statsService.apiTop(days, limit));
    }

    /** 接口请求按日趋势。 */
    @GetMapping("/api/trend")
    public Result<?> apiTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.apiTrend(days));
    }

    /** 指定用户的逐接口请求数。 */
    @GetMapping("/api/by-user")
    public Result<?> apiByUser(@RequestParam Long userId,
                               @RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.apiByUser(userId, days));
    }

    /** 指定接口的逐用户请求数。 */
    @GetMapping("/api/by-endpoint")
    public Result<?> apiByEndpoint(@RequestParam String endpoint,
                                   @RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.apiByEndpoint(endpoint, days));
    }

    /** 有统计记录的用户列表（供下钻选择器）。 */
    @GetMapping("/users")
    public Result<?> users() {
        return Result.ok(statsService.users());
    }
}
