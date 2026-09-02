package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.FeedbackCommentCreateDTO;
import com.sap.dto.FeedbackIssueCreateDTO;
import com.sap.dto.FeedbackStatusDTO;
import com.sap.service.AppFeedbackService;
import com.sap.service.AppAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** App 内嵌意见反馈与管理端 Issue 处理接口。 */
@RestController
@RequestMapping("/api/app/feedback")
public class AppFeedbackController {

    private final AppFeedbackService feedbackService;
    private final AppAccessService accessService;

    public AppFeedbackController(AppFeedbackService feedbackService, AppAccessService accessService) {
        this.feedbackService = feedbackService;
        this.accessService = accessService;
    }

    /** 登录账号共享 Issue 列表；管理端复用同一接口查询。 */
    @GetMapping("/issues")
    public Result<?> list(@RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "false") boolean mine) {
        Access access = feedbackAccess();
        return Result.ok(feedbackService.list(access.userId(), access.admin(), current, size,
                status, category, keyword, mine));
    }

    @GetMapping("/issues/{id}")
    public Result<?> detail(@PathVariable Long id) {
        Access access = feedbackAccess();
        return Result.ok(feedbackService.detail(access.userId(), access.admin(), id));
    }

    @PostMapping("/issues")
    @OperationLog("提交软协课表意见反馈")
    public Result<?> create(@Valid @RequestBody FeedbackIssueCreateDTO dto) {
        Access access = feedbackAccess();
        return Result.ok("反馈已提交",
                feedbackService.create(access.userId(), access.member(), dto));
    }

    /** 点击选择图片后调用；最多 4 张，返回本系统 COS URL。 */
    @PostMapping("/images")
    @OperationLog("上传软协课表反馈图片")
    public Result<?> uploadImages(@RequestParam("files") MultipartFile[] files) {
        Access access = feedbackAccess();
        return Result.ok("图片上传成功",
                feedbackService.uploadImages(access.userId(), access.member(), files));
    }

    @PostMapping("/issues/{id}/comments")
    @OperationLog("跟进软协课表意见反馈")
    public Result<?> comment(@PathVariable Long id,
                             @Valid @RequestBody FeedbackCommentCreateDTO dto) {
        Access access = feedbackAccess();
        return Result.ok("回复成功",
                feedbackService.comment(access.userId(), access.admin(), id, dto));
    }

    /** Issue 发起人主动关闭自己的反馈；只能关闭，不能重新打开或删除。 */
    @PutMapping("/issues/{id}/close")
    @OperationLog("关闭本人软协课表反馈")
    public Result<?> closeOwnIssue(@PathVariable Long id) {
        Access access = feedbackAccess();
        return Result.ok("Issue 已关闭",
                feedbackService.closeOwnIssue(access.userId(), access.admin(), id));
    }

    /** 管理端专属概览：课表使用人数、Issue 状态和线上版本。 */
    @GetMapping("/admin/summary")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> summary(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(feedbackService.summary(days));
    }

    /** 只有管理端可以任意关闭或重新打开 Issue。 */
    @PutMapping("/admin/issues/{id}/status")
    @OperationLog("变更软协课表反馈状态")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> changeStatus(@PathVariable Long id,
                                  @Valid @RequestBody FeedbackStatusDTO dto) {
        return Result.ok("状态已更新",
                feedbackService.changeStatus(StpUtil.getLoginIdAsLong(), id, dto.getStatus()));
    }

    /** 只有管理端可以永久删除 Issue 及其全部回复。 */
    @DeleteMapping("/admin/issues/{id}")
    @OperationLog("彻底删除软协课表反馈")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> deleteIssue(@PathVariable Long id) {
        feedbackService.deleteIssue(id);
        return Result.ok("反馈已彻底删除", null);
    }

    private Access feedbackAccess() {
        long userId = StpUtil.getLoginIdAsLong();
        List<String> roles = StpUtil.getRoleList();
        // 完整 App 能力享受同一反馈额度，但不会改变真实角色与维护者身份。
        boolean member = accessService.hasFullAccess(userId);
        boolean admin = roles.stream().anyMatch(role -> roleCode(role) <= 2);
        return new Access(userId, admin, member);
    }

    private int roleCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private record Access(long userId, boolean admin, boolean member) {
    }
}
