package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.EmailTemplateDTO;
import com.sap.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 管理端邮件 SMTP 配置、HTML 模板与测试发送。仅管理员及以上角色可访问。 */
@RestController
@RequestMapping("/api/email")
@SaCheckRole(value = {"0", "1", "2"}, mode = SaMode.OR)
public class EmailController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.sap.service.mail.MailQueue mailQueue;

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/config")
    public Result<?> config() {
        return Result.ok(emailService.getConfig());
    }

    @PutMapping("/config")
    @OperationLog("保存邮件 SMTP 配置")
    public Result<?> saveConfig(@RequestBody Map<String, String> request) {
        emailService.saveConfig(request);
        return Result.ok("邮件配置已保存", emailService.getConfig());
    }

    @PostMapping("/config/test")
    @OperationLog("测试邮件 SMTP 配置")
    public Result<?> testConfig() {
        emailService.testConnection();
        return Result.ok("SMTP 连通性测试通过");
    }

    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> templates() {
        return Result.ok(emailService.listTemplates());
    }

    @GetMapping("/templates/{id}")
    public Result<?> template(@PathVariable Long id) {
        return Result.ok(emailService.getTemplate(id));
    }

    @PostMapping("/templates")
    @OperationLog("新增邮件模板")
    public Result<?> createTemplate(@Valid @RequestBody EmailTemplateDTO dto) {
        return Result.ok("邮件模板已创建", emailService.createTemplate(dto));
    }

    @PutMapping("/templates/{id}")
    @OperationLog("修改邮件模板")
    public Result<?> updateTemplate(@PathVariable Long id, @Valid @RequestBody EmailTemplateDTO dto) {
        return Result.ok("邮件模板已更新", emailService.updateTemplate(id, dto));
    }

    @DeleteMapping("/templates/{id}")
    @OperationLog("删除邮件模板")
    public Result<?> deleteTemplate(@PathVariable Long id) {
        emailService.deleteTemplate(id);
        return Result.ok("邮件模板已删除");
    }

    @PostMapping("/templates/{id}/preview")
    public Result<?> preview(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> variables) {
        return Result.ok(emailService.preview(id, variables));
    }

    @PostMapping("/test-send")
    @OperationLog("发送测试邮件")
    public Result<?> sendTest(@RequestBody Map<String, Object> request) {
        emailService.sendTest(request);
        return Result.ok("测试邮件已加入队列，请在发送日志查看结果");
    }

    @GetMapping("/bindings")
    public Result<?> bindings() { return Result.ok(mailQueue.bindings()); }

    @GetMapping("/hooks")
    public Result<?> hooks() {
        return Result.ok(java.util.Arrays.stream(com.sap.service.mail.MailHook.values())
                .map(com.sap.service.mail.MailHook::definition).toList());
    }

    public record BindingRequest(Long templateId, Map<String,Object> defaults) {}
    @PutMapping("/bindings/{eventKey}")
    @OperationLog("修改邮件代码事件绑定")
    public Result<?> bind(@org.springframework.web.bind.annotation.PathVariable String eventKey, @RequestBody BindingRequest request) {
        mailQueue.bind(com.sap.service.mail.MailHook.parse(eventKey),request.templateId(),request.defaults()==null?Map.of():request.defaults(),cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
        return Result.ok();
    }
    @GetMapping("/delivery/status")
    public Result<?> status(){return Result.ok(mailQueue.status());}
    @PostMapping("/delivery/activate")
    @OperationLog("手动唤醒邮件队列")
    public Result<?> activate(){mailQueue.kick();return Result.ok("已唤醒邮件队列");}
    @GetMapping("/delivery/{kind}")
    public Result<?> delivery(@org.springframework.web.bind.annotation.PathVariable String kind,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size) {
        return Result.ok(mailQueue.page(kind,page,size));
    }
    @GetMapping("/failed/{id}")
    public Result<?> failed(@org.springframework.web.bind.annotation.PathVariable String id){return Result.ok(mailQueue.failedDetail(id));}
    @GetMapping("/delivery/logs/{id}/history")
    public Result<?> history(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size) {
        return Result.ok(mailQueue.history(id,page,size));
    }
    public record FailureAction(List<String> ids) {}
    @PostMapping("/failed/actions/{action}")
    @OperationLog("处理失败邮件")
    public Result<?> failedAction(@org.springframework.web.bind.annotation.PathVariable String action,@RequestBody FailureAction request){
        mailQueue.failedAction(request.ids(),action,cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());return Result.ok();
    }
}
