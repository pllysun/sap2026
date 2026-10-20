package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.UserDirectoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/directory")
@SaCheckRole(value={"0","1","2"},mode=SaMode.OR)
public class UserDirectoryController {
    private final UserDirectoryService service;
    public UserDirectoryController(UserDirectoryService service) { this.service=service; }

    @GetMapping
    @OperationLog("筛选平台用户与职级")
    public Result<?> list(@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="20") int size,
                          @RequestParam(required=false) String keyword,@RequestParam(required=false) String grade,
                          @RequestParam(required=false) String position,@RequestParam(required=false) Integer roleCode,
                          @RequestParam(defaultValue="highest") String roleMatch,@RequestParam(required=false) Integer gender,
                          @RequestParam(required=false) Integer status) {
        return Result.ok(service.list(current,size,new UserDirectoryService.Filters(keyword,grade,position,roleCode,roleMatch,gender,status)));
    }

    @GetMapping("/filters")
    @OperationLog("查询平台用户筛选选项")
    public Result<?> filters() { return Result.ok(service.options()); }
}
