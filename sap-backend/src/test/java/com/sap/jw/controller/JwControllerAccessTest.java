package com.sap.jw.controller;

import com.sap.common.BusinessException;
import com.sap.jw.entity.JwCredential;
import com.sap.jw.service.JwCredentialService;
import com.sap.service.AppAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwControllerAccessTest {

    @Mock JwCredentialService credentialService;
    @Mock AppAccessService appAccessService;

    private JwController controller;

    @BeforeEach
    void setUp() {
        controller = new JwController();
        ReflectionTestUtils.setField(controller, "credentialService", credentialService);
        ReflectionTestUtils.setField(controller, "appAccessService", appAccessService);
    }

    @Test
    void realMemberCanSaveAdditionalAccount() {
        when(appAccessService.isMember(1L)).thenReturn(true);

        ReflectionTestUtils.invokeMethod(controller, "saveBinding", 1L, "20260002", "pw");

        verify(credentialService).save(1L, "20260002", "pw");
    }

    @Test
    void fullAccessGuestCannotSaveSecondDifferentAccount() {
        JwCredential existing = new JwCredential();
        existing.setJwAccount("20260001");
        when(appAccessService.isMember(2L)).thenReturn(false);
        when(credentialService.get(2L, "20260002")).thenReturn(null);
        when(credentialService.listByUser(2L)).thenReturn(List.of(existing));

        assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(controller, "saveBinding", 2L, "20260002", "pw"));
        verify(credentialService, never()).save(2L, "20260002", "pw");
    }

    @Test
    void fullAccessGuestCanRefreshExistingBinding() {
        JwCredential existing = new JwCredential();
        existing.setJwAccount("20260001");
        when(appAccessService.isMember(2L)).thenReturn(false);
        when(credentialService.get(2L, "20260001")).thenReturn(existing);

        ReflectionTestUtils.invokeMethod(controller, "saveBinding", 2L, "20260001", "new-pw");

        verify(credentialService).save(2L, "20260001", "new-pw");
    }
}
