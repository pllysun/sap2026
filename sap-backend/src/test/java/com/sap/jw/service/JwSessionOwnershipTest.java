package com.sap.jw.service;
import com.sap.common.BusinessException;
import com.sap.jw.client.*;
import com.sap.jw.config.JwProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class JwSessionOwnershipTest {
 @Test void cachedSessionStillRequiresOwnedBinding() {
  var credentials=mock(JwCredentialService.class);var auth=mock(JwAuthClient.class);
  var sessions=new JwSessionManager(auth,credentials,new JwProperties(),new PendingLoginManager());
  sessions.cache(1L,"school-account",mock(JwHttpSession.class));
  when(credentials.getDecryptedPassword(1L,"school-account")).thenThrow(new BusinessException("未绑定"));
  assertThrows(BusinessException.class,()->sessions.getSession(1L,"school-account"));
  verifyNoInteractions(auth);
 }
}
