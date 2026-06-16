package com.sap.jw.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.BusinessException;
import com.sap.common.Result;
import com.sap.jw.client.CaptchaRequiredException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.dto.EvalAutoDTO;
import com.sap.jw.dto.JwBindDTO;
import com.sap.jw.dto.JwCaptchaDTO;
import com.sap.jw.service.JwCredentialService;
import com.sap.jw.service.JwEvaluationService;
import com.sap.jw.service.JwExamService;
import com.sap.jw.service.JwGradeService;
import com.sap.jw.service.JwScheduleService;
import com.sap.jw.service.JwSessionManager;
import com.sap.jw.service.PendingLoginManager;
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

    @Autowired
    private JwCredentialService credentialService;
    @Autowired
    private JwSessionManager sessionManager;
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

    /**
     * 绑定一个学校教务账号（先校验账密能登录，再加密存库）。
     * 若需验证码且自动 OCR 用尽 → 返回 {@code {needCaptcha:true, challengeId, captchaImage(base64)}}，
     * 客户端展示图片让用户输入后调 {@code /bind/captcha}。成功为 {@code {needCaptcha:false}}。
     */
    @PostMapping("/bind")
    @OperationLog("绑定教务账号")
    public Result<?> bind(@Valid @RequestBody JwBindDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        try {
            sessionManager.loginAndCache(userId, dto.getAccount(), dto.getPassword()); // 校验
            credentialService.save(userId, dto.getAccount(), dto.getPassword());
            return Result.ok(Map.of("needCaptcha", false));
        } catch (CaptchaRequiredException e) {
            return captchaResult(pendingManager.put(userId, dto.getAccount(), dto.getPassword(), e.getPending()),
                    e.getCaptchaImage());
        }
    }

    /** 人工输入验证码后续登：成功保存绑定；仍错则返回新验证码图。 */
    @PostMapping("/bind/captcha")
    @OperationLog("验证码续登绑定")
    public Result<?> bindCaptcha(@RequestBody JwCaptchaDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        PendingLoginManager.Entry e = pendingManager.get(dto.getChallengeId());
        if (e == null || e.userId == null || e.userId != userId) {
            throw new BusinessException("验证码会话已过期，请重新绑定");
        }
        try {
            JwHttpSession s = authClient.continueWithCaptcha(e.cas, dto.getCode());
            credentialService.save(e.userId, e.account, e.rawPassword);
            sessionManager.cache(e.userId, e.account, s);
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
        long userId = StpUtil.getLoginIdAsLong();
        credentialService.unbind(userId, account);
        sessionManager.invalidate(userId, account);
        return Result.ok("已解绑");
    }

    /** 已绑定的全部学号（含上次同步时间），按绑定先后；首个为默认。 */
    @GetMapping("/accounts")
    public Result<?> accounts() {
        long userId = StpUtil.getLoginIdAsLong();
        List<Map<String, Object>> list = new ArrayList<>();
        credentialService.listByUser(userId).forEach(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("account", c.getJwAccount());
            m.put("lastSyncAt", c.getLastSyncAt());
            list.add(m);
        });
        return Result.ok(list);
    }

    /** 绑定状态（兼容旧版：返回默认学号信息 + 数量） */
    @GetMapping("/status")
    public Result<?> status() {
        long userId = StpUtil.getLoginIdAsLong();
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
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(scheduleService.getSchedule(userId, resolve(userId, account), term));
    }

    /** 可选学期列表 */
    @GetMapping("/terms")
    public Result<?> terms(@RequestParam(required = false) String account) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(scheduleService.getSchedule(userId, resolve(userId, account), null).getTerms());
    }

    /** 全部课程成绩 */
    @GetMapping("/grades")
    public Result<?> grades(@RequestParam(required = false) String account) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(gradeService.getGrades(userId, resolve(userId, account)));
    }

    /** 考试安排（term 为空取默认学期） */
    @GetMapping("/exams")
    public Result<?> exams(@RequestParam(required = false) String account,
                           @RequestParam(required = false) String term) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(examService.getExams(userId, resolve(userId, account), term));
    }

    /** 评教总览（已评 + 未评）；term 为空取最新一轮评教。 */
    @GetMapping("/eval/list")
    public Result<?> evalList(@RequestParam(required = false) String account,
                             @RequestParam(required = false) String term) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(evaluationService.getOverview(userId, resolve(userId, account), term));
    }

    /** 一键自动评教：对未评任务自动满分(留1项次高)+固定评语提交。提交后不可撤销。 */
    @PostMapping("/eval/auto")
    @OperationLog("一键自动评教")
    public Result<?> evalAuto(@RequestBody(required = false) EvalAutoDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        EvalAutoDTO d = dto == null ? new EvalAutoDTO() : dto;
        return Result.ok(evaluationService.autoEvaluate(userId, resolve(userId, d.getAccount()), d.getTerm(), d.getComment()));
    }

    /** account 省略时回退到默认学号；无任何绑定则抛业务异常。 */
    private String resolve(long userId, String account) {
        if (account != null && !account.isBlank()) return account.trim();
        String def = credentialService.defaultAccount(userId);
        if (def == null) throw new BusinessException("尚未绑定教务账号");
        return def;
    }
}
