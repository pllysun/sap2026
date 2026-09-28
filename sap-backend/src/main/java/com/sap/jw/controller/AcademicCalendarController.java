package com.sap.jw.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.jw.service.AcademicCalendarStore;
import com.sap.service.AppAccessService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/academic-calendar")
public class AcademicCalendarController {
    private final AcademicCalendarStore store;
    private final AppAccessService access;
    public AcademicCalendarController(AcademicCalendarStore store, AppAccessService access) {
        this.store = store;
        this.access = access;
    }

    @GetMapping
    public Result<?> list() {
        access.requireBasicAccess(StpUtil.getLoginIdAsLong());
        return Result.ok(store.list());
    }

    @GetMapping("/admin")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> adminList() { return Result.ok(store.list()); }

    public record Update(String term, String semesterStartDate) {}

    @PutMapping("/admin")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    @OperationLog("维护共享教学日历")
    public Result<?> save(@RequestBody Update body) {
        store.save(body.term(), body.semesterStartDate(), true, StpUtil.getLoginIdAsLong());
        return Result.ok();
    }

    @DeleteMapping("/admin/{term}")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    @OperationLog("删除共享教学日历")
    public Result<?> delete(@PathVariable String term) {
        store.delete(term);
        return Result.ok();
    }
}
