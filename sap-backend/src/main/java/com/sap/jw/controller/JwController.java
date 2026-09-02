package com.sap.jw.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.BusinessException;
import com.sap.common.Result;
import com.sap.jw.client.CaptchaRequiredException;
import com.sap.jw.client.MfaRequiredException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.dto.EvalAutoDTO;
import com.sap.jw.dto.EvalSubmitDTO;
import com.sap.jw.dto.JwBindDTO;
import com.sap.jw.dto.JwCaptchaDTO;
import com.sap.jw.dto.JwRemarkDTO;
import com.sap.jw.service.JwCredentialService;
import com.sap.jw.service.JwEvaluationService;
import com.sap.jw.service.JwExamService;
import com.sap.jw.service.JwGradeService;
import com.sap.jw.service.JwQualitySessionManager;
import com.sap.jw.service.JwScheduleService;
import com.sap.jw.service.JwSessionManager;
import com.sap.jw.service.PendingLoginManager;
import com.sap.service.AppAccessService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教务相关接口。全部需会员登录（Sa-Token 拦截 /api/**）。
 * <p>一个会员可绑定多个教务学号，数据接口用 {@code account} 区分；
 * account 省略时取默认（最早绑定的）学号。</p>
 */
@RestController
@RequestMapping("/api/jw")
public class JwController {

    /** 单实例内按用户串行提交绑定，避免并发请求绕过单账号上限。 */
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> bindLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private JwCredentialService credentialService;
    @Autowired
    private JwSessionManager sessionManager;
    @Autowired
    private JwQualitySessionManager qualitySessionManager;
    @Autowired
    private JwScheduleService scheduleService;
    @Autowired
    private JwGradeService gradeService;
    @Autowired
    private JwExamService examService;
    @Autowired
    private JwEvaluationService evaluationService;
    @Autowired
    private JwAuthClient authClient;
    @Autowired
    private PendingLoginManager pendingManager;
    @Autowired
    private AppAccessService appAccessService;

    /**
     * 绑定一个学校教务账号（先校验账密能登录，再加密存库）。
     * 若需验证码且自动 OCR 用尽 → 返回 {@code {needCaptcha:true, challengeId, captchaImage(base64)}}，
     * 客户端展示图片让用户输入后调 {@code /bind/captcha}。成功为 {@code {needCaptcha:false}}。
     */
    @PostMapping("/bind")
    @OperationLog("绑定教务账号")
    public Result<?> bind(@Valid @RequestBody JwBindDTO dto) {
        long userId = fullAccessUserId();
        requireBindSlot(userId, dto.getAccount());
        try {
            sessionManager.loginAndCache(userId, dto.getAccount(), dto.getPassword()); // 校验
            saveBinding(userId, dto.getAccount(), dto.getPassword());
            qualitySessionManager.invalidate(userId, dto.getAccount());
            return Result.ok(Map.of("needCaptcha", false));
        } catch (CaptchaRequiredException e) {
            return captchaResult(pendingManager.put(userId, dto.getAccount(), dto.getPassword(), e.getPending()),
                    e.getCaptchaImage());
        } catch (MfaRequiredException e) {
            // 安全手机短信二次验证：短信已发出，返回挑战 + 掩码手机号，客户端输码后调 /bind/mfa
            String cid = pendingManager.put(userId, dto.getAccount(), dto.getPassword(), e.getPending());
            return mfaResult(cid, e.getPhone());
        }
    }

    /** 输入短信验证码后续登：成功保存绑定；错误则报错让用户重输。 */
    @PostMapping("/bind/mfa")
    @OperationLog("短信验证码续登绑定")
    public Result<?> bindMfa(@RequestBody JwCaptchaDTO dto) {
        long userId = fullAccessUserId();
        PendingLoginManager.Entry e = pendingManager.get(dto.getChallengeId());
        if (e == null || e.userId == null || e.userId != userId) {
            throw new BusinessException("二次验证会话已过期，请重新绑定");
        }
        JwHttpSession s = authClient.continueWithMfa(e.cas, dto.getCode());
        saveBinding(e.userId, e.account, e.rawPassword);
        sessionManager.cache(e.userId, e.account, s);
        qualitySessionManager.invalidate(e.userId, e.account);
        pendingManager.remove(dto.getChallengeId());
        return Result.ok(Map.of("needMfa", false));
    }

    /** 重新发送短信验证码。 */
    @PostMapping("/bind/mfa/resend")
    @OperationLog("重发短信验证码")
    public Result<?> bindMfaResend(@RequestBody JwCaptchaDTO dto) {
        long userId = fullAccessUserId();
        PendingLoginManager.Entry e = pendingManager.get(dto.getChallengeId());
        if (e == null || e.userId == null || e.userId != userId) {
            throw new BusinessException("二次验证会话已过期，请重新绑定");
        }
        authClient.sendSms(e.cas);
        return Result.ok(Map.of("needMfa", true));
    }

    private Result<?> mfaResult(String challengeId, String phone) {
        Map<String, Object> data = new HashMap<>();
        data.put("needMfa", true);
        data.put("challengeId", challengeId);
        data.put("phone", phone);
        return Result.ok(data);
    }

    /** 人工输入验证码后续登：成功保存绑定；仍错则返回新验证码图。 */
    @PostMapping("/bind/captcha")
    @OperationLog("验证码续登绑定")
    public Result<?> bindCaptcha(@RequestBody JwCaptchaDTO dto) {
        long userId = fullAccessUserId();
        PendingLoginManager.Entry e = pendingManager.get(dto.getChallengeId());
        if (e == null || e.userId == null || e.userId != userId) {
            throw new BusinessException("验证码会话已过期，请重新绑定");
        }
        try {
            JwHttpSession s = authClient.continueWithCaptcha(e.cas, dto.getCode());
            saveBinding(e.userId, e.account, e.rawPassword);
            sessionManager.cache(e.userId, e.account, s);
            qualitySessionManager.invalidate(e.userId, e.account);
            pendingManager.remove(dto.getChallengeId());
            return Result.ok(Map.of("needCaptcha", false));
        } catch (CaptchaRequiredException ce) {
            return captchaResult(dto.getChallengeId(), ce.getCaptchaImage());
        }
    }

    private Result<?> captchaResult(String challengeId, byte[] image) {
        Map<String, Object> data = new HashMap<>();
        data.put("needCaptcha", true);
        data.put("challengeId", challengeId);
        data.put("captchaImage", Base64.getEncoder().encodeToString(image));
        return Result.ok(data);
    }

    /** 解绑指定学号 */
    @DeleteMapping("/unbind")
    @OperationLog("解绑教务账号")
    public Result<?> unbind(@RequestParam String account) {
        long userId = fullAccessUserId();
        credentialService.unbind(userId, account);
        sessionManager.invalidate(userId, account);
        qualitySessionManager.invalidate(userId, account);
        return Result.ok("已解绑");
    }

    /** 已绑定的全部学号（含备注、上次同步时间），按绑定先后；首个为默认。 */
    @GetMapping("/accounts")
    public Result<?> accounts() {
        long userId = fullAccessUserId();
        List<Map<String, Object>> list = new ArrayList<>();
        credentialService.listByUser(userId).forEach(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("account", c.getJwAccount());
            m.put("remark", c.getRemark());
            m.put("lastSyncAt", c.getLastSyncAt());
            list.add(m);
        });
        return Result.ok(list);
    }

    /** 保存指定教务账号备注；备注跟随当前会员账号持久化，空白表示清除。 */
    @PutMapping("/accounts/remark")
    @OperationLog("修改教务账号备注")
    public Result<?> updateAccountRemark(@Valid @RequestBody JwRemarkDTO dto) {
        long userId = fullAccessUserId();
        credentialService.updateRemark(userId, dto.getAccount(), dto.getRemark());
        return Result.ok("备注已保存");
    }

    /** 绑定状态（兼容旧版：返回默认学号信息 + 数量） */
    @GetMapping("/status")
    public Result<?> status() {
        long userId = fullAccessUserId();
        String def = credentialService.defaultAccount(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("bound", def != null);
        data.put("account", def);
        data.put("count", credentialService.listByUser(userId).size());
        return Result.ok(data);
    }

    /** 课表（term 为空取当前学期；account 为空取默认学号） */
    @GetMapping("/schedule")
    public Result<?> schedule(@RequestParam(required = false) String account,
                              @RequestParam(required = false) String term) {
        long userId = fullAccessUserId();
        return Result.ok(scheduleService.getSchedule(userId, resolve(userId, account), term));
    }

    /** 可选学期列表 */
    @GetMapping("/terms")
    public Result<?> terms(@RequestParam(required = false) String account) {
        long userId = fullAccessUserId();
        return Result.ok(scheduleService.getSchedule(userId, resolve(userId, account), null).getTerms());
    }

    /** 全部课程成绩 */
    @GetMapping("/grades")
    public Result<?> grades(@RequestParam(required = false) String account) {
        long userId = fullAccessUserId();
        return Result.ok(gradeService.getGrades(userId, resolve(userId, account)));
    }

    /** 考试安排（term 为空取默认学期） */
    @GetMapping("/exams")
    public Result<?> exams(@RequestParam(required = false) String account,
                           @RequestParam(required = false) String term) {
        long userId = fullAccessUserId();
        return Result.ok(examService.getExams(userId, resolve(userId, account), term));
    }

    /** 新教学质量保障系统评教总览（已评 + 未评）；term 为空优先取进行中的任务。 */
    @GetMapping("/eval/list")
    public Result<?> evalList(@RequestParam(required = false) String account,
                             @RequestParam(required = false) String term) {
        long userId = fullAccessUserId();
        return Result.ok(evaluationService.getOverview(userId, resolve(userId, account), term));
    }

    /** 获取一门课程的评价量表，客户端可逐题自定义填写。 */
    @GetMapping("/eval/form")
    public Result<?> evalForm(@RequestParam(required = false) String account,
                              @RequestParam Long taskId,
                              @RequestParam Long courseId) {
        long userId = fullAccessUserId();
        return Result.ok(evaluationService.getForm(userId, resolve(userId, account), taskId, courseId));
    }

    /** 提交一门课程的自定义评价。 */
    @PostMapping("/eval/submit")
    @OperationLog("提交自定义教学评价")
    public Result<?> evalSubmit(@RequestBody EvalSubmitDTO dto) {
        long userId = fullAccessUserId();
        return Result.ok(evaluationService.submit(userId, resolve(userId, dto.getAccount()), dto));
    }

    /** 一键最高合法评分：若平台禁止全最高，只在扣分粒度最小的一题自动减分。提交后不可撤销。 */
    @PostMapping("/eval/auto")
    @OperationLog("一键最高合法教学评价")
    public Result<?> evalAuto(@RequestBody(required = false) EvalAutoDTO dto) {
        long userId = fullAccessUserId();
        EvalAutoDTO d = dto == null ? new EvalAutoDTO() : dto;
        return Result.ok(evaluationService.autoEvaluate(userId, resolve(userId, d.getAccount()),
                d.getTaskId(), d.getTerm(), d.getComment()));
    }

    private long fullAccessUserId() {
        long userId = StpUtil.getLoginIdAsLong();
        appAccessService.requireFullAccess(userId);
        return userId;
    }

    /** 游客完整能力等级最多绑定一个教务账号；真实会员不设上限。 */
    private void requireBindSlot(long userId, String account) {
        if (appAccessService.isMember(userId)) return;
        String clean = account == null ? "" : account.trim();
        boolean alreadyBound = credentialService.get(userId, clean) != null;
        if (!alreadyBound && !credentialService.listByUser(userId).isEmpty()) {
            throw new BusinessException(409, "当前教务账号数量已达上限，可先移除现有账号再添加");
        }
    }

    private void saveBinding(long userId, String account, String rawPassword) {
        Object lock = bindLocks.computeIfAbsent(userId, ignored -> new Object());
        synchronized (lock) {
            requireBindSlot(userId, account);
            credentialService.save(userId, account, rawPassword);
        }
    }

    /** account 省略时回退到默认学号；无任何绑定则抛业务异常。 */
    private String resolve(long userId, String account) {
        if (account != null && !account.isBlank()) return account.trim();
        String def = credentialService.defaultAccount(userId);
        if (def == null) throw new BusinessException("尚未绑定教务账号");
        return def;
    }
}
