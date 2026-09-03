package com.sap.jw.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.common.BusinessException;
import com.sap.jw.client.MfaRequiredException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.dto.JwCaptchaDTO;
import com.sap.jw.service.ClassScheduleService;
import com.sap.jw.service.PendingClassScheduleManager;
import com.sap.jw.service.JwSessionManager;
import com.sap.service.AppAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 班级课表查询、采集与定时任务管理接口。 */
@RestController
@RequestMapping("/api/class-schedule")
public class ClassScheduleController {

    private final ClassScheduleService scheduleService;
    private final AppAccessService accessService;
    private final JwAuthClient authClient;
    private final JwSessionManager sessionManager;
    private final PendingClassScheduleManager pendingManager;

    public ClassScheduleController(ClassScheduleService scheduleService,
                                   AppAccessService accessService,
                                   JwAuthClient authClient,
                                   JwSessionManager sessionManager,
                                   PendingClassScheduleManager pendingManager) {
        this.scheduleService = scheduleService;
        this.accessService = accessService;
        this.authClient = authClient;
        this.sessionManager = sessionManager;
        this.pendingManager = pendingManager;
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
        return Result.ok(Map.of(
                "terms", scheduleService.terms(),
                "logs", scheduleService.logs(100)));
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
        // 当前项目没有启用 sa-token-jwt extra 扩展；操作者姓名由采集服务按 actor_id 回查，
        // 不能调用 StpUtil.getExtra，否则会在真正登录教务前直接抛 ApiDisabledException(500)。
        String actorName = null;
        // 不使用固定“管理端”占位名；采集服务会按 actor_id 回查真实用户姓名，
        // 这样手动触发与定时任务的审计日志都能指向具体操作者。
        Long requestedOwner = number(request.get("ownerUserId"));
        boolean superAdmin = StpUtil.getRoleList().contains("0");
        Long configuredOwner = number(scheduleService.scheduleConfig().get("ownerUserId"));
        if (!superAdmin && requestedOwner != null
                && !requestedOwner.equals(userId)
                && !requestedOwner.equals(configuredOwner)) {
            throw new com.sap.common.BusinessException(403, "无权使用该负责人教务账号采集");
        }
        // 管理员未指定负责人时，优先使用超级管理员在定时配置中设置的采集负责人；
        // 没有集中负责人则使用当前管理员自己的绑定账号。密码始终从后端加密凭据读取。
        Long credentialOwner = requestedOwner != null
                ? requestedOwner
                : configuredOwner != null ? configuredOwner : userId;
        try {
            return Result.ok("班级课表采集任务已完成",
                    password == null
                            ? scheduleService.pullAs(credentialOwner, account, term, "MANUAL", userId, actorName)
                            : scheduleService.pullWithCredentials(userId, account, password, term, "MANUAL", actorName));
        } catch (MfaRequiredException e) {
            // 一次性密码登录触发安全手机验证时保留 CAS 会话，前端输入短信后可继续同一次采集，
            // 不要求管理员先把账号绑定到自己的 App 账号。
            String cleanAccount = account == null ? "" : account.trim();
            String challengeId = pendingManager.put(userId, cleanAccount, term, e.getPending(), e.getPhone());
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("needMfa", true);
            data.put("challengeId", challengeId);
            data.put("phone", e.getPhone());
            return Result.ok("需要短信二次验证", data);
        }
    }

    /** 输入短信验证码后继续本次班级课表采集。 */
    @PostMapping("/admin/pull/mfa")
    @OperationLog("短信验证码续登采集班级课表")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> pullMfa(@RequestBody JwCaptchaDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        PendingClassScheduleManager.Entry entry = pendingManager.get(dto == null ? null : dto.getChallengeId());
        if (entry == null || entry.userId == null || !entry.userId.equals(userId)) {
            throw new BusinessException("短信验证会话已过期，请重新开始采集");
        }
        JwHttpSession session = authClient.continueWithMfa(entry.cas, dto == null ? null : dto.getCode());
        sessionManager.cache(entry.userId, entry.account, session);
        pendingManager.remove(dto.getChallengeId());
        return Result.ok("班级课表采集任务已完成",
                scheduleService.pullAs(entry.userId, entry.account, entry.term, "MANUAL", userId, null));
    }

    /** 重新发送班级课表采集所需的短信验证码。 */
    @PostMapping("/admin/pull/mfa/resend")
    @OperationLog("重发班级课表采集短信验证码")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> resendPullMfa(@RequestBody JwCaptchaDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        PendingClassScheduleManager.Entry entry = pendingManager.get(dto == null ? null : dto.getChallengeId());
        if (entry == null || entry.userId == null || !entry.userId.equals(userId)) {
            throw new BusinessException("短信验证会话已过期，请重新开始采集");
        }
        authClient.sendSms(entry.cas);
        return Result.ok(Map.of("needMfa", true, "challengeId", dto.getChallengeId(), "phone", entry.phone));
    }

    @GetMapping("/admin/logs")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> logs(@RequestParam(defaultValue = "100") int limit) {
        return Result.ok(scheduleService.logs(limit));
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
