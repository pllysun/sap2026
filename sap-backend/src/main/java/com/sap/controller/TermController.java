package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Term;
import com.sap.service.TermService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/term")
public class TermController {

    @Autowired
    private TermService termService;

    @GetMapping("/list")
    @OperationLog("查询换届记录列表")
    public Result<?> list(@RequestParam String grade,
                          @RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "40") int size) {
        var page = termService.listByGradePaged(grade, current, size);
        boolean member = false;
        try { member = cn.dev33.satoken.stp.StpUtil.getRoleList().stream().anyMatch(r -> java.util.Set.of("0", "1", "2", "3").contains(r)); }
        catch (Exception ignored) { }
        if (!member) page.setRecords(page.getRecords().stream().map(record -> {
            Map<String, Object> view = new java.util.LinkedHashMap<>(record);
            view.remove("studentId"); view.remove("userId");
            return view;
        }).toList());
        return Result.ok(page);
    }

    @GetMapping("/grades")
    @OperationLog("查询年级列表")
    public Result<?> grades() {
        return Result.ok(termService.listGrades());
    }

    @PostMapping
    @OperationLog("新增换届记录")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> add(@RequestBody Term term) {
        termService.addTerm(term);
        return Result.ok("添加成功");
    }

    @PostMapping("/changeover")
    @OperationLog("执行换届")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> changeover(@RequestBody List<Map<String, Object>> assignments) {
        termService.changeover(assignments);
        return Result.ok("换届成功");
    }
}
