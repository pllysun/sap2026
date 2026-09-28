package com.sap.service.mail;

import com.sap.dto.EmailTemplateDTO;
import com.sap.mapper.EmailTemplateMapper;
import com.sap.service.EmailService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 新模板仅初始化一次，管理员后续编辑、停用或删除均保留；绑定由 MailQueue 初始化。 */
@Component
public class AppRegistrationMailSetup {
    private final EmailService email;
    private final EmailTemplateMapper templates;
    public AppRegistrationMailSetup(EmailService email, EmailTemplateMapper templates) { this.email=email; this.templates=templates; }
    @Order(-100)
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() throws IOException {
        if(templates.selectAnyByKey(MailHook.APP_REGISTRATION_CODE.initialTemplateKey)!=null) return;
        var dto=new EmailTemplateDTO();
        dto.setTemplateKey(MailHook.APP_REGISTRATION_CODE.initialTemplateKey);
        dto.setEventKey(MailHook.APP_REGISTRATION_CODE.name());
        dto.setTemplateName("App 注册 · QQ 邮箱验证码");
        dto.setSubject("软件协会｜App 注册邮箱验证码");
        dto.setDescription("手机端注册验证 QQ 邮箱归属，验证码由注册业务生成，15 分钟内有效，Web 注册不使用此流程。");
        dto.setEnabled(true);
        try(var input=new ClassPathResource("mail/app.registration-code.html").getInputStream()) {
            dto.setHtmlContent(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        email.createTemplate(dto);
    }
}
