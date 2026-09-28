package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.dto.AppRegisterDTO;
import com.sap.service.mail.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppRegistrationServiceTest {
    JdbcTemplate db; MailStore store; EmailBusinessHooks hooks; EmailService email;
    CaptchaService captcha; AuthService auth; RegistrationProtectionService protection;
    Registration service; String sentCode;
    class Registration extends AppRegistrationService {
        long clock=System.currentTimeMillis();
        Registration() { super(store,hooks,email,captcha,auth,protection); }
        @Override long now() { return clock; }
    }
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        db=new JdbcTemplate(ds);store=new MailStore(db,new DataSourceTransactionManager(ds));store.init();
        hooks=mock(EmailBusinessHooks.class);email=mock(EmailService.class);captcha=mock(CaptchaService.class);
        auth=mock(AuthService.class);protection=mock(RegistrationProtectionService.class);
        when(protection.acquire(any(),any())).thenReturn(mock(RegistrationProtectionService.Permit.class));
        when(captcha.verify(any(),any(),any())).thenReturn(true);
        when(email.sendingEnabled()).thenReturn(true);
        db.update("INSERT INTO sys_mail_binding(event_key,template_id,updated_at) VALUES('APP_REGISTRATION_CODE',7,0)");
        when(email.getTemplate(7L)).thenReturn(Map.of("enabled",true,"subject","App 注册验证","htmlContent","{{name}} {{account}} {{email}} {{code}} {{expiresInMinutes}}"));
        when(hooks.appRegistrationCode(any(),any(),any(),any(),any(),any())).thenAnswer(i->{sentCode=i.getArgument(3);return UUID.randomUUID().toString();});
        service=new Registration();ReflectionTestUtils.setField(service,"key","0123456789abcdef0123456789abcdef");service.init();
    }
    Map<String,Object> request() { return service.request("20260001","测试同学","123456789","image-id","ABCD","127.0.0.1"); }
    long count() {return db.queryForObject("SELECT COUNT(*) FROM sys_app_registration_code",Long.class);}
    AppRegisterDTO dto(String id,String code) {
        var dto=new AppRegisterDTO();dto.setStudentId("20260001");dto.setPassword("test-password");dto.setName("测试同学");dto.setGender(0);dto.setQq("123456789");dto.setEmailRequestId(id);dto.setEmailCode(code);return dto;
    }
    @Test void requiresCaptchaBeforeSendingAndRejectsWrongImageWithoutMail() {
        assertEquals(Map.of("captchaRequired",true),service.request("20260001","测试同学","123456789",null,null,"ip"));
        verifyNoInteractions(hooks);assertEquals(0,count());
        when(captcha.verify(any(),any(),any())).thenReturn(false);
        assertThrows(BusinessException.class,this::request);verifyNoInteractions(hooks);assertEquals(0,count());
    }
    @Test void storesOnlyDigestAndBindsEmailToRegistrationFields() {
        var result=request();String id=(String)result.get("requestId");
        assertEquals("123456789@qq.com",result.get("email"));assertEquals(180,result.get("cooldownSeconds"));assertEquals(900L,result.get("expiresInSeconds"));
        assertFalse(result.toString().contains(sentCode));
        assertEquals(64,db.queryForObject("SELECT code_hash FROM sys_app_registration_code",String.class).length());
        assertFalse(db.queryForList("SELECT * FROM sys_app_registration_code").toString().contains(sentCode));
        var wrong=dto(id,sentCode);wrong.setQq("987654321");
        assertThrows(BusinessException.class,()->service.register(wrong,"ip"));
        wrong.setQq("123456789");wrong.setStudentId("20260002");
        assertThrows(BusinessException.class,()->service.register(wrong,"ip"));verifyNoInteractions(auth);
    }
    @Test void requiresEmailCodeEvenWhenAllOtherFieldsAreValid() {
        assertThrows(BusinessException.class,()->service.register(dto(null,null),"ip"));verifyNoInteractions(auth);
        assertThrows(BusinessException.class,()->service.register(dto(UUID.randomUUID().toString(),"123456"),"ip"));verifyNoInteractions(auth);
    }
    @Test void successCreatesAccountOnceAndConsumesCode() {
        String id=(String)request().get("requestId");var dto=dto(id,sentCode);
        service.register(dto,"ip");verify(auth).register(dto);
        assertNull(db.queryForObject("SELECT code_hash FROM sys_app_registration_code",String.class));
        assertEquals(1,db.queryForObject("SELECT consumed FROM sys_app_registration_code",Integer.class));
        assertThrows(BusinessException.class,()->service.register(dto,"ip"));verify(auth,times(1)).register(any());
    }
    @Test void fiveWrongAttemptsPersistAndBlockCorrectCode() {
        String id=(String)request().get("requestId"),correct=sentCode;
        String wrong=correct.equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++)assertThrows(BusinessException.class,()->service.register(dto(id,wrong),"ip"));
        assertEquals(5,db.queryForObject("SELECT attempts FROM sys_app_registration_code",Integer.class));
        assertThrows(BusinessException.class,()->service.register(dto(id,correct),"ip"));verifyNoInteractions(auth);
    }
    @Test void expiredCodeCannotCreateAccount() {
        String id=(String)request().get("requestId");service.clock+=AppRegistrationService.TTL;
        assertThrows(BusinessException.class,()->service.register(dto(id,sentCode),"ip"));verifyNoInteractions(auth);
    }
    @Test void failedAccountCreationRollsBackConsumption() {
        String id=(String)request().get("requestId");
        doThrow(new BusinessException("该学号已注册")).when(auth).register(any());
        assertThrows(BusinessException.class,()->service.register(dto(id,sentCode),"ip"));
        assertEquals(0,db.queryForObject("SELECT consumed FROM sys_app_registration_code",Integer.class));
        assertNotNull(db.queryForObject("SELECT code_hash FROM sys_app_registration_code",String.class));
    }
    @Test void resendInvalidatesOldCodeAndRateLimitsSurviveRestart() {
        String old=(String)request().get("requestId"),oldCode=sentCode;
        assertEquals(429,assertThrows(BusinessException.class,this::request).getCode());
        long clock=service.clock;service=new Registration();service.clock=clock;
        ReflectionTestUtils.setField(service,"key","0123456789abcdef0123456789abcdef");service.init();
        assertThrows(BusinessException.class,this::request);
        service.clock+=180_000;String latest=(String)request().get("requestId");
        assertThrows(BusinessException.class,()->service.register(dto(old,oldCode),"ip"));
        service.register(dto(latest,sentCode),"ip");
    }
    @Test void quotaAppliesAcrossAccountsSharingQqAndAcrossQqSharingAccount() {
        request();
        assertThrows(BusinessException.class,()->service.request("20260002","同学","123456789","id","ABCD","other-ip"));
        assertThrows(BusinessException.class,()->service.request("20260001","同学","987654321","id","ABCD","other-ip"));
        for(int i=1;i<5;i++) {service.clock+=180_000;request();}
        service.clock+=180_000;assertThrows(BusinessException.class,this::request);assertEquals(5,count());
    }
    @Test void unavailableMailOrFailedEnqueueRollsBackChallenge() {
        when(email.sendingEnabled()).thenReturn(false);assertEquals(503,assertThrows(BusinessException.class,this::request).getCode());assertEquals(0,count());
        when(email.sendingEnabled()).thenReturn(true);
        when(hooks.appRegistrationCode(any(),any(),any(),any(),any(),any())).thenReturn(null);
        assertThrows(BusinessException.class,this::request);assertEquals(0,count());
    }
    @Test void simultaneousRequestsAndRedemptionsOnlySucceedOnce() throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Map<String,Object>> call=()->{try{return request();}catch(BusinessException e){return null;}};
            var a=pool.submit(call);var b=pool.submit(call);var first=a.get();var second=b.get();
            assertNotEquals(first==null,second==null);assertEquals(1,count());
            String id=(String)(first==null?second:first).get("requestId"),code=sentCode;
            Callable<Boolean> redeem=()->{try{service.register(dto(id,code),"ip");return true;}catch(BusinessException e){return false;}};
            var x=pool.submit(redeem);var y=pool.submit(redeem);assertNotEquals(x.get(),y.get());verify(auth,times(1)).register(any());
        } finally {pool.shutdownNow();}
    }
    @Test void backlogRejectsNewMailAndCleanupKeepsRecentQuota() {
        request();service.clock+=180_000;
        for(int i=0;i<5;i++)db.update("INSERT INTO sys_mail_queue(id,event_key,recipient,subject,payload,created_at) VALUES(?,'TEST','test@example.com','test','encrypted',0)","q"+i);
        assertEquals(429,assertThrows(BusinessException.class,this::request).getCode());assertEquals(1,count());
        service.cleanup();assertEquals(1,count());service.clock+=3*86_400_000L;service.cleanup();assertEquals(0,count());
    }
}
