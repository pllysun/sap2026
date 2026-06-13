package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Activity;
import com.sap.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @GetMapping("/list")
    @OperationLog("查询活动列表")
    public Result<?> list(@RequestParam String grade) {
        return Result.ok(activityService.listByGrade(grade));
    }

    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "5") int size) {
        return Result.ok(activityService.pageAll(current, size));
    }

    @PostMapping
    @OperationLog("新增活动")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> add(@RequestBody Map<String, Object> params) {
        Activity activity = new Activity();
        activity.setGrade((String) params.get("grade"));
        activity.setTitle((String) params.get("title"));
        activity.setContent((String) params.get("content"));
        List<String> imageUrls = (List<String>) params.get("imageUrls");
        activityService.addActivity(activity, imageUrls);
        return Result.ok("添加成功");
    }

    @PutMapping("/{id}")
    @OperationLog("修改活动")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Activity activity = new Activity();
        activity.setTitle((String) params.get("title"));
        activity.setContent((String) params.get("content"));
        List<String> imageUrls = (List<String>) params.get("imageUrls");
        activityService.updateActivity(id, activity, imageUrls);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除活动")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> delete(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return Result.ok("删除成功");
    }

    @GetMapping("/years")
    @OperationLog("查询活动年份列表")
    public Result<?> years() {
        return Result.ok(activityService.getActivityYears());
    }

    @GetMapping("/count")
    @OperationLog("查询活动数量")
    public Result<?> count(@RequestParam String grade) {
        return Result.ok(activityService.getCountByGrade(grade));
    }
}
