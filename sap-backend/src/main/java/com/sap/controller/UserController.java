package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.User;
import com.sap.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    @OperationLog("查询用户列表")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> list(@RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(current, size, keyword));
    }

    @GetMapping("/{id}")
    @OperationLog("查询用户详情")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> getById(@PathVariable Long id) {
        return Result.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @OperationLog("更新用户信息")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> update(@PathVariable Long id, @RequestBody User user) {
        userService.updateUser(id, user);
        return Result.ok("更新成功");
    }

    @PutMapping("/{id}/roles")
    @OperationLog("更新用户权限")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> updateRoles(@PathVariable Long id, @RequestBody List<Integer> roleCodes) {
        userService.updateUserRole(id, roleCodes);
        return Result.ok("权限更新成功");
    }

    @GetMapping("/{id}/roles")
    @OperationLog("查询用户权限")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> getRoles(@PathVariable Long id) {
        return Result.ok(userService.getUserRoles(id));
    }

    @PostMapping("/{id}/upgrade")
    @OperationLog("升级用户为成员")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> upgrade(@PathVariable Long id) {
        userService.upgradeToMember(id);
        return Result.ok("升级成功");
    }

    @PostMapping("/batch-upgrade")
    @OperationLog("批量升级用户为成员")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> batchUpgrade(@RequestBody List<Long> userIds) {
        int count = userService.batchUpgradeToMember(userIds);
        return Result.ok("成功升级 " + count + " 人");
    }

    @GetMapping("/members")
    @OperationLog("查询成员用户列表")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> listMembers() {
        return Result.ok(userService.listMemberUsers());
    }
}
