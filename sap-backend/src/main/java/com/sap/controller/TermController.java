package com.sap.controller;

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
        return Result.ok(termService.listByGradePaged(grade, current, size));
    }

    @GetMapping("/grades")
    @OperationLog("查询年级列表")
    public Result<?> grades() {
        return Result.ok(termService.listGrades());
    }

    @PostMapping
    @OperationLog("新增换届记录")
    public Result<?> add(@RequestBody Term term) {
        termService.addTerm(term);
        return Result.ok("添加成功");
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除换届记录")
    public Result<?> delete(@PathVariable Long id) {
        termService.deleteTerm(id);
        return Result.ok("删除成功");
    }

    @PostMapping("/changeover")
    @OperationLog("执行换届")
    public Result<?> changeover(@RequestBody List<Map<String, Object>> assignments) {
        termService.changeover(assignments);
        return Result.ok("换届成功");
    }
}
