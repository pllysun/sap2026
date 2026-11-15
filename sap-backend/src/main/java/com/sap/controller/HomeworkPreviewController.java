package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.Result;
import com.sap.service.HomeworkPreviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作业在线预览（只读）。
 * <p>学生只能看已发布作业；管理员可预览未发布的排期作业，方便发布前校对。</p>
 */
@RestController
@RequestMapping("/api/study/homework")
public class HomeworkPreviewController {

    @Autowired
    private HomeworkPreviewService homeworkPreviewService;

    @GetMapping("/preview")
    public Result<?> preview(@RequestParam Long activityId, @RequestParam Integer week) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<String> roles = StpUtil.getRoleList();
        boolean admin = roles.stream().anyMatch(r -> "0".equals(r) || "1".equals(r) || "2".equals(r));
        if (!admin) {
            StpUtil.checkRoleOr("0", "1", "2", "3");
        }
        return Result.ok(homeworkPreviewService.preview(activityId, week, userId, admin));
    }
}
