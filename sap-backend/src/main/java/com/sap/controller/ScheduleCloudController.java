package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.AppAnnouncementDTO;
import com.sap.service.AppAccessService;
import com.sap.service.AppAnnouncementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端课表云控与 App 公告。超级管理员与会长具有相同的管理权限。 */
@RestController
@RequestMapping("/api/app/cloud")
public class ScheduleCloudController {

    private final AppAccessService accessService;
    private final AppAnnouncementService announcementService;

    public ScheduleCloudController(AppAccessService accessService,
                                   AppAnnouncementService announcementService) {
        this.accessService = accessService;
        this.announcementService = announcementService;
    }

    /** App 公告列表：登录用户均可查看已发布公告，最新在前。 */
    @GetMapping("/announcements")
    public Result<?> announcements() {
        return Result.ok(announcementService.publishedList());
    }

    /** 管理端只读云控概览：超管、会长、管理员均可查看。 */
    @GetMapping("/admin")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> adminOverview() {
        return Result.ok(Map.of(
                "guestAccessLevel", accessService.guestAccessLevel(),
                "announcements", announcementService.adminList()));
    }

    @PutMapping("/admin/guest-access-level")
    @OperationLog("修改软协课表游客权限等级")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> updateGuestAccessLevel(@RequestBody Map<String, Object> body) {
        Object raw = body.get("level");
        int level;
        try {
            level = Integer.parseInt(String.valueOf(raw));
        } catch (Exception ignored) {
            throw new com.sap.common.BusinessException(400, "缺少有效的游客权限等级");
        }
        accessService.updateGuestAccessLevel(level);
        return Result.ok("游客权限等级已更新", Map.of("guestAccessLevel", level));
    }

    @PostMapping("/admin/announcements")
    @OperationLog("发布软协课表公告")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> createAnnouncement(@Valid @RequestBody AppAnnouncementDTO dto) {
        return Result.ok("公告已保存",
                announcementService.create(StpUtil.getLoginIdAsLong(), dto));
    }

    @PutMapping("/admin/announcements/{id}")
    @OperationLog("修改软协课表公告")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> updateAnnouncement(@PathVariable Long id,
                                        @Valid @RequestBody AppAnnouncementDTO dto) {
        return Result.ok("公告已更新", announcementService.update(id, dto));
    }

    @DeleteMapping("/admin/announcements/{id}")
    @OperationLog("删除软协课表公告")
    @SaCheckRole(value = {"0", "1"}, mode = SaMode.OR)
    public Result<?> deleteAnnouncement(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.ok("公告已删除", null);
    }
}
