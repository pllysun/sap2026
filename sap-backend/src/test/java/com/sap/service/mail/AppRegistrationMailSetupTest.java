package com.sap.service.mail;

import com.sap.dto.EmailTemplateDTO;
import com.sap.entity.EmailTemplate;
import com.sap.mapper.EmailTemplateMapper;
import com.sap.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppRegistrationMailSetupTest {
    @Test void installsPackagedTemplateWithCompleteContract() throws Exception {
        var email=mock(EmailService.class);var mapper=mock(EmailTemplateMapper.class);
        new AppRegistrationMailSetup(email,mapper).initialize();
        var captor=ArgumentCaptor.forClass(EmailTemplateDTO.class);verify(email).createTemplate(captor.capture());
        var template=captor.getValue();assertEquals("app.registration-code",template.getTemplateKey());
        assertTrue(template.getEnabled());assertEquals("APP_REGISTRATION_CODE",template.getEventKey());
        assertEquals(Files.readString(Path.of("../templates/email/app.registration-code.html")),template.getHtmlContent());
        assertDoesNotThrow(()->MailQueue.validateContract(MailHook.APP_REGISTRATION_CODE,
                Map.of("subject",template.getSubject(),"htmlContent",template.getHtmlContent())));
    }
    @Test void preservesExistingTemplateIncludingDeletedOrDisabled() throws Exception {
        var email=mock(EmailService.class);var mapper=mock(EmailTemplateMapper.class);
        var existing=new EmailTemplate();existing.setEnabled(false);existing.setDeleted(1);
        when(mapper.selectAnyByKey("app.registration-code")).thenReturn(existing);
        new AppRegistrationMailSetup(email,mapper).initialize();verifyNoInteractions(email);
    }
}
