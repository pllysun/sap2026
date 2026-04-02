package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Position;
import com.sap.service.PositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/position")
public class PositionController {

    @Autowired
    private PositionService positionService;

    @GetMapping("/list")
    @OperationLog("查询身份列表")
    public Result<?> list() {
        return Result.ok(positionService.listAll());
    }

    @PostMapping
    @OperationLog("新增身份")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> add(@RequestBody Position position) {
        positionService.addPosition(position);
        return Result.ok("添加成功");
    }

    @PutMapping("/{id}")
    @OperationLog("修改身份")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> update(@PathVariable Integer id, @RequestBody Position position) {
        positionService.updatePosition(id, position);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除身份")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> delete(@PathVariable Integer id) {
        positionService.deletePosition(id);
        return Result.ok("删除成功");
    }
}

