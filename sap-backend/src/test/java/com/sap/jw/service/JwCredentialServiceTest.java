package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.config.JwProperties;
import com.sap.jw.entity.JwCredential;
import com.sap.jw.mapper.JwCredentialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwCredentialServiceTest {

    @Mock private JwCredentialMapper mapper;
    @Mock private JwProperties properties;

    private JwCredentialService service;

    @BeforeEach
    void setUp() {
        service = new JwCredentialService(mapper, properties);
    }

    @Test
    void updateRemarkPersistsTrimmedValueOnOwnedCredential() {
        JwCredential credential = credential();
        when(mapper.selectOne(any())).thenReturn(credential);

        String saved = service.updateRemark(11L, " 20250001 ", "  主账号  ");

        assertEquals("主账号", saved);
        assertEquals("主账号", credential.getRemark());
        verify(mapper).updateById(credential);
    }

    @Test
    void blankRemarkClearsPersistedValue() {
        JwCredential credential = credential();
        credential.setRemark("旧备注");
        when(mapper.selectOne(any())).thenReturn(credential);

        assertNull(service.updateRemark(11L, "20250001", "   "));
        assertNull(credential.getRemark());
        verify(mapper).updateById(credential);
    }

    @Test
    void updateRemarkRejectsAccountNotBoundByCurrentUser() {
        when(mapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateRemark(11L, "20259999", "其他账号"));

        assertEquals("尚未绑定该教务账号", error.getMessage());
        verify(mapper, never()).updateById(any());
    }

    @Test
    void updateRemarkRejectsOverFortyCharacters() {
        when(mapper.selectOne(any())).thenReturn(credential());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateRemark(11L, "20250001", "a".repeat(41)));

        assertEquals(400, error.getCode());
        verify(mapper, never()).updateById(any());
    }

    private JwCredential credential() {
        JwCredential credential = new JwCredential();
        credential.setId(1L);
        credential.setUserId(11L);
        credential.setJwAccount("20250001");
        return credential;
    }
}
