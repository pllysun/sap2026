package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.OutstandingMember;
import com.sap.service.OutstandingMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/outstanding-member")
public class OutstandingMemberController {

    @Autowired
    private OutstandingMemberService service;

    @GetMapping("/all")
    @OperationLog("查询全部优秀成员")
    public Result<?> listAll() {
        return Result.ok(service.listAll());
    }

    @GetMapping("/list")
    @OperationLog("查询优秀成员列表")
    public Result<?> list(@RequestParam String grade) {
        return Result.ok(service.listByGrade(grade));
    }

    @PostMapping
    @OperationLog("添加优秀成员")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> add(@RequestBody OutstandingMember member) {
        service.add(member);
        return Result.ok("添加成功");
    }

    @PutMapping("/{id}")
    @OperationLog("修改优秀成员")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> update(@PathVariable Long id, @RequestBody OutstandingMember member) {
        service.update(id, member);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除优秀成员")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }
}
