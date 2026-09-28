package com.sap.jw.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.common.BusinessException;
import com.sap.jw.dto.JwCaptchaDTO;
import com.sap.jw.service.ClassScheduleService;
import com.sap.jw.service.ClassScheduleTaskService;
import com.sap.service.AppAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 班级课表查询与手动异步采集接口。 */
@RestController
@RequestMapping("/api/class-schedule")
public class ClassScheduleController {

    private final ClassScheduleService scheduleService;
    private final AppAccessService accessService;
    private final ClassScheduleTaskService tasks;

    public ClassScheduleController(ClassScheduleService scheduleService,
                                   AppAccessService accessService,
                                   ClassScheduleTaskService tasks) {
        this.scheduleService = scheduleService;
        this.accessService = accessService;
        this.tasks = tasks;
    }

    /** App 班级模式选择学期。游客在云控基础等级以上即可使用。 */
    @GetMapping("/terms")
    @OperationLog("查询班级课表学期")
    public Result<?> terms() {
        requireBasic();
        return Result.ok(scheduleService.terms());
    }

    /** App 班级模式按学院、专业筛选班级。 */
    @GetMapping("/classes")
    @OperationLog("查询班级课表班级")
    public Result<?> classes(@RequestParam String term,
                             @RequestParam(required = false) String college,
                             @RequestParam(required = false) String major,
                             @RequestParam(required = false) String grade) {
        requireBasic();
        return Result.ok(scheduleService.classes(term, college, major, grade));
    }

    /** App 班级模式读取某个班级的合并课表。 */
    @GetMapping("/schedule")
    @OperationLog("下载并切换班级课表")
    public Result<?> schedule(@RequestParam String term,
                              @RequestParam String college,
                              @RequestParam String major,
                              @RequestParam(required = false) String grade,
                              @RequestParam String className) {
        requireBasic();
        return Result.ok(scheduleService.schedule(term, college, major, grade, className));
    }

    /** 管理端概览；管理员可读，采集日志持久化。 */
    @GetMapping("/admin")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> admin() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("terms", scheduleService.terms());
        data.put("activeBatchId", tasks.activeBatchId());
        return Result.ok(data);
    }

    /** 管理端手动采集；管理员可以发起，执行人会写入持久化日志。 */
    @PostMapping("/admin/pull")
    @OperationLog("采集班级课表")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> pull(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        Long userId = StpUtil.getLoginIdAsLong();
        String account = stringValue(request.get("account"));
        String password = stringValue(request.get("password"));
        String term = stringValue(request.get("term"));
        String batchId = stringValue(request.get("batchId"));
        if (batchId != null && !batchId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new BusinessException(400, "无效的采集批次");
        }
        // 当前项目没有启用 sa-token-jwt extra 扩展；操作者姓名由采集服务按 actor_id 回查，
        // 不能调用 StpUtil.getExtra，否则会在真正登录教务前直接抛 ApiDisabledException(500)。
        // 不使用固定“管理端”占位名；采集服务会按 actor_id 回查真实用户姓名，
        // 这样手动触发与定时任务的审计日志都能指向具体操作者。
        Long requestedOwner = number(request.get("ownerUserId"));
        var roles = StpUtil.getRoleList();
        boolean leaderOrSuper = roles.contains("0") || roles.contains("1");
        Long configuredOwner = number(scheduleService.scheduleConfig().get("ownerUserId"));
        if (!leaderOrSuper && requestedOwner != null
                && !requestedOwner.equals(userId)
                && !requestedOwner.equals(configuredOwner)) {
            throw new com.sap.common.BusinessException(403, "无权使用该负责人教务账号采集");
        }
        // 管理员未指定负责人时，优先使用定时配置中设置的采集负责人；
        // 没有集中负责人则使用当前管理员自己的绑定账号。密码始终从后端加密凭据读取。
        Long credentialOwner = requestedOwner != null
                ? requestedOwner
                : configuredOwner != null ? configuredOwner : userId;
        if (password != null && account == null) throw new BusinessException(400, "请输入教务账号");
        return Result.ok("班级课表采集任务已启动", tasks.start(password == null ? credentialOwner : userId,
                userId, account, password, term, batchId));
    }

    /** 输入短信验证码后继续本次班级课表采集。 */
    @PostMapping("/admin/pull/mfa")
    @OperationLog("短信验证码续登采集班级课表")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> pullMfa(@RequestBody JwCaptchaDTO dto) {
        return Result.ok("已提交短信验证", tasks.resume(StpUtil.getLoginIdAsLong(),
                dto == null ? null : dto.getChallengeId(), dto == null ? null : dto.getCode()));
    }

    /** 重新发送班级课表采集所需的短信验证码。 */
    @PostMapping("/admin/pull/mfa/resend")
    @OperationLog("重发班级课表采集短信验证码")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> resendPullMfa(@RequestBody JwCaptchaDTO dto) {
        return Result.ok(tasks.resend(StpUtil.getLoginIdAsLong(), dto == null ? null : dto.getChallengeId()));
    }

    @GetMapping("/admin/logs")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> logs(@RequestParam(defaultValue = "100") int limit) {
        return Result.ok(scheduleService.logs(limit));
    }

    @GetMapping("/admin/pull/progress")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> pullProgress(@RequestParam String batchId) {
        return Result.ok(tasks.progress(batchId, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/admin/batches")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> batches(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size,
                             @RequestParam(required = false) String status, @RequestParam(required = false) String search) {
        return Result.ok(scheduleService.batches(page, size, status, search));
    }

    @GetMapping("/admin/pull/history")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> history(@RequestParam String batchId, @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(scheduleService.history(batchId, page, size));
    }

    private void requireBasic() {
        accessService.requireBasicAccess(StpUtil.getLoginIdAsLong());
    }

    private static String stringValue(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static Long number(Object value) {
        try {
            return value == null || String.valueOf(value).isBlank()
                    ? null : Long.valueOf(String.valueOf(value));
        } catch (Exception ignored) { return null; }
    }
}
