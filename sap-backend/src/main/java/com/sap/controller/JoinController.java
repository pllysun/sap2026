package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.BusinessException;
import com.sap.common.Result;
import com.sap.service.JoinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/join")
public class JoinController {

    @Autowired
    private JoinService joinService;

    /** 入会开关状态（公开） */
    @GetMapping("/status")
    public Result<?> status() {
        return Result.ok(joinService.isJoinEnabled());
    }

    /** 开关入会通道 */
    @PostMapping("/toggle")
    @OperationLog("切换入会通道")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> toggle(@RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        joinService.toggleJoin(enabled);
        // 开启时自动初始化负责人
        if (enabled) joinService.initManagers();
        return Result.ok("操作成功");
    }

    /** 负责人列表 */
    @GetMapping("/managers")
    @OperationLog("查询负责人列表")
    public Result<?> managers() {
        return Result.ok(joinService.listManagers());
    }

    /** 添加负责人 */
    @PostMapping("/manager")
    @OperationLog("添加负责人")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> addManager(@RequestBody Map<String, Object> body) {
        Long userId = Long.parseLong(String.valueOf(body.get("userId")));
        joinService.addManager(userId);
        return Result.ok("添加成功");
    }

    /** 移除负责人 */
    @DeleteMapping("/manager/{id}")
    @OperationLog("移除负责人")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> removeManager(@PathVariable Long id) {
        joinService.removeManager(id);
        return Result.ok("移除成功");
    }

    /** 负责人上传收款码 */
    @PostMapping("/manager/qr")
    @OperationLog("上传收款码")
    public Result<?> uploadQr(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String alipayQr = (String) body.get("alipayQr");
        String wechatQr = (String) body.get("wechatQr");
        joinService.uploadQrCode(userId, alipayQr, wechatQr);
        return Result.ok("上传成功");
    }

    /** 游客申请入会 */
    @PostMapping("/apply")
    @OperationLog("申请入会")
    public Result<?> apply() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(joinService.apply(userId));
    }

    /** 查看我的申请 */
    @GetMapping("/my-application")
    public Result<?> myApplication() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(joinService.getMyApplication(userId));
    }

    /** 刷新负责人（超过24h） */
    @PostMapping("/refresh-manager")
    @OperationLog("刷新负责人")
    public Result<?> refreshManager() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(joinService.refreshManager(userId));
    }

    /** 提交支付编码 */
    @PostMapping("/submit-payment")
    @OperationLog("提交支付编码")
    public Result<?> submitPayment(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String paymentCode = (String) body.get("paymentCode");
        joinService.submitPaymentCode(userId, paymentCode != null ? paymentCode : "");
        return Result.ok("提交成功");
    }

    /** 审核列表 */
    @GetMapping("/applications")
    @OperationLog("查询入会审核列表")
    public Result<?> applications(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") boolean onlyMine
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 防越权：仅管理员(0/1/2)可查看全部申请；其他人(含入会负责人)强制只看分配给自己的，
        // 避免任意登录用户批量拉取全部申请人实名/学号/QQ/支付码等 PII。
        List<String> roles = StpUtil.getRoleList();
        boolean isAdmin = roles.contains("0") || roles.contains("1") || roles.contains("2");
        boolean effectiveOnlyMine = isAdmin ? onlyMine : true;
        return Result.ok(joinService.listApplications(status, effectiveOnlyMine, userId));
    }

    /** 审核通过 */
    @PostMapping("/approve/{id}")
    @OperationLog("审核通过入会申请")
    public Result<?> approve(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        joinService.approve(id, userId);
        return Result.ok("审核通过");
    }

    /** 直接升级会员 */
    @PostMapping("/direct-upgrade")
    @OperationLog("直接升级会员")
    @SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
    public Result<?> directUpgrade(@RequestBody Map<String, Object> body) {
        String studentId = (String) body.get("studentId");
        if (studentId == null || studentId.isEmpty()) throw new BusinessException("学号不能为空");
        joinService.directUpgrade(studentId);
        return Result.ok("升级成功");
    }
}
