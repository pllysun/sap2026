package com.sap.controller;

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
    public Result<?> list(@RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(current, size, keyword));
    }

    @GetMapping("/{id}")
    @OperationLog("查询用户详情")
    public Result<?> getById(@PathVariable Long id) {
        return Result.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @OperationLog("更新用户信息")
    public Result<?> update(@PathVariable Long id, @RequestBody User user) {
        userService.updateUser(id, user);
        return Result.ok("更新成功");
    }

    @PutMapping("/{id}/roles")
    @OperationLog("更新用户权限")
    public Result<?> updateRoles(@PathVariable Long id, @RequestBody List<Integer> roleCodes) {
        userService.updateUserRole(id, roleCodes);
        return Result.ok("权限更新成功");
    }

    @GetMapping("/{id}/roles")
    @OperationLog("查询用户权限")
    public Result<?> getRoles(@PathVariable Long id) {
        return Result.ok(userService.getUserRoles(id));
    }

    @PostMapping("/{id}/upgrade")
    @OperationLog("升级用户为成员")
    public Result<?> upgrade(@PathVariable Long id) {
        userService.upgradeToMember(id);
        return Result.ok("升级成功");
    }

    @PostMapping("/batch-upgrade")
    @OperationLog("批量升级用户为成员")
    public Result<?> batchUpgrade(@RequestBody List<Long> userIds) {
        int count = userService.batchUpgradeToMember(userIds);
        return Result.ok("成功升级 " + count + " 人");
    }

    @GetMapping("/members")
    @OperationLog("查询成员用户列表")
    public Result<?> listMembers() {
        return Result.ok(userService.listMemberUsers());
    }
}
