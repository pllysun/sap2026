package com.sap.service.mail;

import com.sap.common.BusinessException;
import com.sap.dto.EmailTemplateDTO;
import com.sap.mapper.EmailTemplateMapper;
import com.sap.service.EmailService;
import com.sap.service.SettingService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailHookDefinitionTest {
    @Test void everyCodeParameterHasDocumentationAndScaffoldMatchesContract() {
        for (MailHook hook : MailHook.values()) {
            var definition = hook.definition();
            var parameters = (List<MailHook.Parameter>) definition.get("parameters");
            assertEquals(hook.variables, parameters.stream().map(MailHook.Parameter::name).toList());
            assertFalse(((String) definition.get("guidance")).isBlank());
            for (var parameter : parameters) {
                assertFalse(parameter.label().isBlank());
                assertFalse(parameter.description().isBlank());
                assertFalse(parameter.example().isBlank());
            }
            var html = parameters.stream().map(p -> "<p>{{" + p.name() + "}}</p>").reduce("", String::concat);
            assertDoesNotThrow(() -> MailQueue.validateContract(hook, Map.of("subject",hook.title,"htmlContent",html)));
        }
        assertEquals(4, ((List<?>) MailHook.PASSWORD_CODE.definition().get("parameters")).size());
    }

    private EmailTemplateDTO draft(String event, String subject, String html) {
        var dto = new EmailTemplateDTO();
        dto.setTemplateKey("test.template"); dto.setTemplateName("测试模板");
        dto.setEventKey(event); dto.setSubject(subject); dto.setHtmlContent(html); dto.setEnabled(false);
        return dto;
    }

    @Test void createRejectsIncompleteAndUnknownHookBeforeWriting() {
        var mapper = mock(EmailTemplateMapper.class);
        var service = new EmailService(mock(SettingService.class),mapper);
        assertThrows(BusinessException.class, () -> service.createTemplate(draft("PASSWORD_CODE","修改密码","{{name}} {{account}} {{code}}")));
        assertThrows(BusinessException.class, () -> service.createTemplate(draft("PASSWORD_CODE","{{code}}","{{name}} {{account}} {{expiresInMinutes}}")));
        assertThrows(BusinessException.class, () -> service.createTemplate(draft("UNKNOWN","欢迎","{{name}}")));
        assertThrows(BusinessException.class, () -> service.createTemplate(draft("MEMBER_JOINED","欢迎","{{name}} {{extra}}")));
        verifyNoInteractions(mapper);
    }

    @Test void validHookAndIndependentTemplateCanBeSavedWithoutBinding() {
        var mapper = mock(EmailTemplateMapper.class);
        var service = new EmailService(mock(SettingService.class),mapper);
        assertDoesNotThrow(() -> service.createTemplate(draft("PASSWORD_CODE","修改密码","{{name}} {{account}} {{code}} {{expiresInMinutes}}")));
        assertDoesNotThrow(() -> service.createTemplate(draft(null,"独立模板","{{customVariable}}")));
        verify(mapper,times(2)).insert(any(com.sap.entity.EmailTemplate.class));
    }
}
