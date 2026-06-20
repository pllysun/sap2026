package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyLeader;
import com.sap.entity.StudyScore;
import com.sap.service.StudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    @Autowired
    private StudyService studyService;

    // ---- 学习活动 ----
    @GetMapping("/activity/years")
    @OperationLog("查询学习活动年份列表")
    public Result<?> studyActivityYears() {
        return Result.ok(studyService.getStudyActivityYears());
    }

    @GetMapping("/activity/all-with-stats")
    @OperationLog("查询所有学习活动统计")
    public Result<?> allActivitiesWithStats() {
        return Result.ok(studyService.listAllActivitiesWithStats());
    }

    @GetMapping("/activity/list")
    @OperationLog("查询学习活动列表")
    public Result<?> listActivities(@RequestParam(required = false) String grade) {
        return Result.ok(studyService.listActivities(grade));
    }

    @PostMapping("/activity")
    @OperationLog("创建学习活动")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> createActivity(@RequestBody StudyActivity activity) {
        studyService.createActivity(activity);
        return Result.ok("创建成功");
    }

    @GetMapping("/activity/{id}/detail")
    @OperationLog("查询学习活动详情")
    public Result<?> getActivityDetail(@PathVariable Long id) {
        return Result.ok(studyService.getActivityDetail(id));
    }

    @PutMapping("/activity/{id}/close")
    @OperationLog("关闭学习活动")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> closeActivity(@PathVariable Long id) {
        studyService.closeActivity(id);
        return Result.ok("已关闭");
    }

    @PutMapping("/activity/{id}/active-week")
    @OperationLog("设置活动周期")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> setActiveWeek(@PathVariable Long id, @RequestBody Map<String, Integer> params) {
        studyService.setActiveWeek(id, params.get("week"));
        return Result.ok("活动周期已更新");
    }

    // ---- 周期数据 ----
    @GetMapping("/cycle/detail")
    @OperationLog("查询周期详情")
    public Result<?> getCycleDetail(@RequestParam Long activityId,
                                     @RequestParam Integer week) {
        return Result.ok(studyService.getCycleDetail(activityId, week));
    }

    // ---- 负责人 ----
    @GetMapping("/leader/list")
    @OperationLog("查询负责人列表")
    public Result<?> listLeaders(@RequestParam Long activityId) {
        return Result.ok(studyService.listLeaders(activityId));
    }

    @PostMapping("/leader")
    @OperationLog("添加负责人")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> addLeader(@RequestBody StudyLeader leader) {
        studyService.addLeader(leader);
        return Result.ok("添加成功");
    }

    @DeleteMapping("/leader/{id}")
    @OperationLog("删除负责人")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> deleteLeader(@PathVariable Long id) {
        studyService.deleteLeader(id);
        return Result.ok("删除成功");
    }

    @PutMapping("/leader/{id}/restore")
    @OperationLog("恢复负责人")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> restoreLeader(@PathVariable Long id) {
        studyService.restoreLeader(id);
        return Result.ok("恢复成功");
    }

    // ---- 成员 ----
    @GetMapping("/member/list")
    @OperationLog("查询学习小组成员")
    public Result<?> listMembers(@RequestParam Long activityId,
                                  @RequestParam(required = false) Integer week) {
        return Result.ok(studyService.getCycleDetail(activityId, week));
    }

    @PostMapping("/member/auto-join")
    @OperationLog("自动加入学习活动")
    public Result<?> autoJoin(@RequestBody(required = false) Map<String, Long> params) {
        // 防越权：强制本人自助加入，忽略请求体里的 userId
        Long userId = StpUtil.getLoginIdAsLong();
        studyService.autoJoinLatest(userId);
        return Result.ok("自动加入最新活动成功");
    }

    @PostMapping("/member/join")
    @OperationLog("加入学习活动")
    public Result<?> memberJoin(@RequestBody Map<String, Long> params) {
        Long activityId = params.get("activityId");
        // 防越权：强制本人自助加入，忽略请求体里的 userId（管理员代加请用 /member/batch-join）
        Long userId = StpUtil.getLoginIdAsLong();
        studyService.memberJoin(activityId, userId);
        return Result.ok("加入成功");
    }

    @PostMapping("/member/batch-join")
    @OperationLog("批量加入学习活动")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> batchJoin(@RequestBody Map<String, Object> params) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        @SuppressWarnings("unchecked")
        List<?> rawIds = (List<?>) params.get("userIds");
        List<Long> userIds = rawIds.stream()
                .map(o -> Long.valueOf(o.toString()))
                .collect(java.util.stream.Collectors.toList());
        int count = studyService.batchMemberJoin(activityId, userIds);
        return Result.ok("成功添加 " + count + " 人");
    }

    @PutMapping("/member/assign")
    @OperationLog("分配成员负责人")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> reassignMember(@RequestBody Map<String, Long> params) {
        studyService.reassignMember(params.get("memberId"), params.get("leaderId"));
        return Result.ok("分配成功");
    }

    // ---- 周期 ----
    @PostMapping("/week/next")
    @OperationLog("开启下一周期")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> nextWeek(@RequestBody Map<String, Long> params) {
        studyService.nextWeek(params.get("activityId"));
        return Result.ok("进入下一周期");
    }

    // ---- 作业 ----
    @PostMapping("/homework/upload")
    @OperationLog("上传作业文件")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> uploadHomework(@RequestBody Map<String, Object> params) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        Integer week = Integer.valueOf(params.get("week").toString());
        String title = params.get("title") != null ? params.get("title").toString() : null;
        String fileUrl = params.get("fileUrl") != null ? params.get("fileUrl").toString() : null;
        String fileName = params.get("fileName") != null ? params.get("fileName").toString() : null;
        // 发布时间：null/空 表示立即发布；未来时间表示定时发布
        Object pt = params.get("publishTime");
        java.time.LocalDateTime publishTime = (pt != null && !pt.toString().isBlank())
                ? parseDateTime(pt.toString()) : null;
        studyService.uploadHomework(activityId, week, title, fileUrl, fileName, publishTime);
        return Result.ok("上传成功");
    }

    /** 作业排期列表（管理端查看当前+未来多周的作业题目与发布状态） */
    @GetMapping("/homework/schedule")
    @OperationLog("查询作业排期")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> homeworkSchedule(@RequestParam Long activityId) {
        return Result.ok(studyService.getHomeworkSchedule(activityId));
    }

    /** 兼容前端日期选择器：解析 "yyyy-MM-dd HH:mm[:ss]"（含 ISO 'T'） */
    private static java.time.LocalDateTime parseDateTime(String s) {
        String v = s.trim().replace('T', ' ');
        java.time.format.DateTimeFormatter[] fmts = {
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        };
        for (java.time.format.DateTimeFormatter f : fmts) {
            try {
                return java.time.LocalDateTime.parse(v, f);
            } catch (Exception ignore) {
                // 尝试下一种格式
            }
        }
        throw new com.sap.common.BusinessException("发布时间格式不正确");
    }

    @DeleteMapping("/homework")
    @OperationLog("删除作业文件")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> deleteHomework(@RequestParam Long activityId, @RequestParam Integer week) {
        studyService.deleteHomework(activityId, week);
        return Result.ok("删除成功");
    }

    @DeleteMapping("/homework/my")
    @OperationLog("删除我的作业提交")
    public Result<?> deleteMySubmission(@RequestParam Long activityId, @RequestParam Integer week) {
        Long userId = StpUtil.getLoginIdAsLong();
        studyService.deleteMySubmission(activityId, week, userId);
        return Result.ok("删除成功");
    }

    @PostMapping("/homework/submit")
    @OperationLog("学生提交作业")
    public Result<?> submitHomework(@RequestBody Map<String, Object> params) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long activityId = Long.valueOf(params.get("activityId").toString());
        Integer week = Integer.valueOf(params.get("week").toString());
        String title = params.get("title") != null ? params.get("title").toString() : null;
        String fileUrl = params.get("fileUrl") != null ? params.get("fileUrl").toString() : null;
        String fileName = params.get("fileName") != null ? params.get("fileName").toString() : null;
        studyService.submitStudentHomework(activityId, week, userId, title, fileUrl, fileName);
        return Result.ok("提交成功");
    }

    // ---- 评分 ----
    @PostMapping("/score")
    @OperationLog("提交评分")
    public Result<?> score(@RequestBody StudyScore studyScore) {
        studyScore.setLeaderUserId(StpUtil.getLoginIdAsLong());
        studyService.score(studyScore);
        return Result.ok("评分成功");
    }

    @GetMapping("/score/overview")
    @OperationLog("查询评分总览")
    public Result<?> scoreOverview(@RequestParam Long activityId,
                                    @RequestParam(required = false) Integer week) {
        return Result.ok(studyService.scoreOverview(activityId, week));
    }

    // ---- 用户端专用接口 ----

    @GetMapping("/my-status")
    @OperationLog("查询我的学习状态")
    public Result<?> myStatus() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(studyService.getMyStatus(userId));
    }

    @GetMapping("/my-scores")
    @OperationLog("查询我的成绩")
    public Result<?> myScores() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(studyService.getMyScores(userId));
    }

    @GetMapping("/ranking")
    @OperationLog("查询成绩排名")
    public Result<?> ranking(@RequestParam Long activityId,
                              @RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(studyService.getRanking(activityId, current, size));
    }
}
