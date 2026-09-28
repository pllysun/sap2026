package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.entity.User;
import com.sap.mapper.UserMapper;
import com.sap.service.mail.*;
import com.sap.util.PasswordUtil;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceTest {
    JdbcTemplate db; MailStore store; UserMapper users; EmailBusinessHooks hooks; EmailService email;
    CacheService cache; CaptchaService captcha; AuthService auth; User user; Recovery service; String sentCode;
    class Recovery extends PasswordRecoveryService {
        long clock=System.currentTimeMillis(); AtomicInteger logouts=new AtomicInteger();
        Recovery() { super(store,users,hooks,email,cache,captcha,auth); }
        @Override long now() { return clock; }
        @Override void invalidateSessions(Long id) { logouts.incrementAndGet(); }
    }
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        db=new JdbcTemplate(ds);store=new MailStore(db,new DataSourceTransactionManager(ds));store.init();
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,student_id VARCHAR(20),password VARCHAR(255),qq VARCHAR(20),status INT,deleted INT,updated_at TIMESTAMP)");
        db.update("INSERT INTO sys_mail_binding(event_key,template_id,updated_at) VALUES('PASSWORD_CODE',3,0)");
        users=mock(UserMapper.class);hooks=mock(EmailBusinessHooks.class);email=mock(EmailService.class);
        cache=mock(CacheService.class);captcha=mock(CaptchaService.class);auth=mock(AuthService.class);
        when(captcha.verify(anyString(),anyString(),anyString())).thenReturn(true);
        when(email.sendingEnabled()).thenReturn(true);
        when(email.getTemplate(3L)).thenReturn(Map.of("enabled",true,"subject","找回密码","htmlContent","{{name}} {{account}} {{code}} {{expiresInMinutes}}"));
        user=new User();user.setId(7L);user.setStudentId("20260007");user.setName("测试同学");user.setQq("123456789");user.setStatus(1);user.setPassword(PasswordUtil.encode("old-password"));
        when(users.selectOne(any())).thenReturn(user);when(users.selectById(7L)).thenReturn(user);
        when(hooks.passwordCode(any(),anyString(),any(),anyString())).thenAnswer(i->{sentCode=i.getArgument(1);return UUID.randomUUID().toString();});
        db.update("INSERT INTO sys_user(id,student_id,password,qq,status,deleted) VALUES(?,?,?,?,1,0)",7,user.getStudentId(),user.getPassword(),user.getQq());
        service=new Recovery();ReflectionTestUtils.setField(service,"key","0123456789abcdef0123456789abcdef");service.init();
    }
    Map<String,Object> request() {return service.request(user.getStudentId(),"image-id","ABCD","127.0.0.1");}
    long count(){return db.queryForObject("SELECT COUNT(*) FROM sys_password_recovery",Long.class);}
    String id(){return (String)request().get("requestId");}
    @Test void usesStoredQqAndNeverStoresPlaintextCode() {
        String id=id();assertTrue(sentCode.matches("[0-9]{6}"));
        verify(hooks).passwordCode(eq(user),eq(sentCode),any(),eq("recovery:"+id));
        String hash=db.queryForObject("SELECT code_hash FROM sys_password_recovery WHERE id=?",String.class,id);
        assertEquals(64,hash.length());assertNotEquals(sentCode,hash);
        assertEquals(180,requestCooldown());
    }
    int requestCooldown(){return PasswordRecoveryService.COOLDOWN_SECONDS;}
    @Test void captchaFailureDoesNotSendOrCreateChallenge() {
        when(captcha.verify(any(),any(),any())).thenReturn(false);
        assertThrows(BusinessException.class,this::request);assertEquals(0,count());verifyNoInteractions(hooks);
    }
    @Test void nonexistentOrInvalidQqHasSameResponseButNoMail() {
        when(users.selectOne(any())).thenReturn(null);
        var response=request();assertNotNull(response.get("requestId"));assertEquals(PasswordRecoveryService.REQUEST_NOTICE,response.get("notice"));
        verifyNoInteractions(hooks);assertEquals(1,count());
        assertThrows(BusinessException.class,()->service.reset((String)response.get("requestId"),"123456","new-password"));
    }
    @Test void disabledUserCannotRecover() {
        user.setStatus(0);id();verifyNoInteractions(hooks);
    }
    @Test void correctCodeUpdatesPasswordConsumesAllChallengesAndLogsOut() {
        String id=id();service.reset(id,sentCode,"new-password");
        assertTrue(PasswordUtil.matches("new-password",db.queryForObject("SELECT password FROM sys_user",String.class)));
        assertEquals(1,service.logouts.get());verify(cache).updateUser(user);
        verify(auth).clearPasswordRecoveryLock(user.getStudentId());
        assertNull(db.queryForObject("SELECT code_hash FROM sys_password_recovery",String.class));
        assertThrows(BusinessException.class,()->service.reset(id,sentCode,"another-password"));
        var log=db.queryForMap("SELECT * FROM sys_mail_log WHERE status='PASSWORD_RESET'");
        assertTrue(log.get("context_json").toString().contains("20260007"));
        assertFalse(log.toString().contains(sentCode));assertFalse(log.toString().contains("new-password"));
    }
    @Test void wrongAttemptsAreCommittedAndFiveFailuresLockCode() {
        String id=id(),correct=sentCode,wrong=correct.equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++)assertThrows(BusinessException.class,()->service.reset(id,wrong,"new-password"));
        assertEquals(5,db.queryForObject("SELECT attempts FROM sys_password_recovery",Integer.class));
        assertThrows(BusinessException.class,()->service.reset(id,correct,"new-password"));assertEquals(0,service.logouts.get());
    }
    @Test void expiredCodeCannotReset() {
        String id=id();service.clock+=PasswordRecoveryService.TTL;
        assertThrows(BusinessException.class,()->service.reset(id,sentCode,"new-password"));
    }
    @Test void cooldownAndDailyLimitSurviveNewServiceInstance() {
        id();assertEquals(429,assertThrows(BusinessException.class,this::request).getCode());
        long time=service.clock; service=new Recovery();service.clock=time;
        ReflectionTestUtils.setField(service,"key","0123456789abcdef0123456789abcdef");service.init();
        assertThrows(BusinessException.class,this::request);
        service.clock+=180_000;id();service.clock+=180_000;id();service.clock+=180_000;
        assertThrows(BusinessException.class,this::request);assertEquals(3,count());
    }
    @Test void sameQqAcrossAccountsSharesQuota() {
        id();user.setStudentId("20260008");assertThrows(BusinessException.class,this::request);assertEquals(1,count());
    }
    @Test void resendInvalidatesPreviousCode() {
        String old=id(),code=sentCode;service.clock+=180_000;String latest=id();
        assertThrows(BusinessException.class,()->service.reset(old,code,"new-password"));
        service.reset(latest,sentCode,"new-password");
    }
    @Test void changedQqOrPasswordInvalidatesCode() {
        String id=id();user.setQq("987654321");
        assertThrows(BusinessException.class,()->service.reset(id,sentCode,"new-password"));
        user.setQq("123456789");user.setPassword(PasswordUtil.encode("admin-changed"));
        assertThrows(BusinessException.class,()->service.reset(id,sentCode,"new-password"));
    }
    @Test void mailUnavailableOrEnqueueFailureCannotCreateUsableCode() {
        when(email.sendingEnabled()).thenReturn(false);assertEquals(503,assertThrows(BusinessException.class,this::request).getCode());assertEquals(0,count());
        when(email.sendingEnabled()).thenReturn(true);when(hooks.passwordCode(any(),anyString(),any(),anyString())).thenReturn(null);
        assertThrows(BusinessException.class,this::request);assertEquals(0,count());
    }
    @Test void simultaneousRequestsOnlyEnqueueOnce() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            Callable<Boolean> call=()->{gate.await();try{request();return true;}catch(BusinessException e){return false;}};
            var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            assertNotEquals(a.get(),b.get());assertEquals(1,count());verify(hooks,times(1)).passwordCode(any(),anyString(),any(),anyString());
        } finally{pool.shutdownNow();}
    }
    @Test void simultaneousCodeRedemptionOnlySucceedsOnce() throws Exception {
        String id=id(),code=sentCode;var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> call=()->{try{service.reset(id,code,"new-password");return true;}catch(BusinessException e){return false;}};
            var a=pool.submit(call);var b=pool.submit(call);assertNotEquals(a.get(),b.get());assertEquals(1,service.logouts.get());
        } finally{pool.shutdownNow();}
    }
    @Test void globalAndIpLimitsApplyEvenToUnknownAccounts() {
        when(users.selectOne(any())).thenReturn(null);
        for(int i=0;i<5;i++)service.request("account"+i,"id","text","same-ip");
        assertEquals(429,assertThrows(BusinessException.class,()->service.request("another","id","text","same-ip")).getCode());
        for(int i=5;i<8;i++)service.request("account"+i,"id","text","ip"+i);
        assertEquals(429,assertThrows(BusinessException.class,()->service.request("another","id","text","different-ip")).getCode());
    }
    @Test void queueBacklogRejectsNewCodeWithoutBypassingMailPacing() {
        for(int i=0;i<5;i++)db.update("INSERT INTO sys_mail_queue(id,event_key,recipient,subject,payload,created_at) VALUES(?,'TEST','test@example.com','test','encrypted',0)","queued"+i);
        assertEquals(429,assertThrows(BusinessException.class,this::request).getCode());assertEquals(0,count());verifyNoInteractions(hooks);
    }
    @Test void realHookAndQueueProduceEncryptedMailAndUsableCodeInOneTransaction() {
        var queue=new MailQueue(store,email,new com.fasterxml.jackson.databind.ObjectMapper()) {@Override public synchronized void kick(){}};
        ReflectionTestUtils.setField(queue,"key","0123456789abcdef0123456789abcdef");
        try {
            hooks=new EmailBusinessHooks(queue,store,users);
            service=new Recovery();ReflectionTestUtils.setField(service,"key","0123456789abcdef0123456789abcdef");
            when(email.render(anyString(),anyString(),anyMap())).thenAnswer(i->{
                Map<String,Object> params=i.getArgument(2);sentCode=params.get("code").toString();
                assertEquals("20260007",params.get("account"));
                return Map.of("subject","找回密码","html","<p>验证码："+sentCode+"</p>");
            });
            String id=id();var row=db.queryForMap("SELECT * FROM sys_mail_queue");
            assertEquals("123456789@qq.com",row.get("recipient"));
            assertEquals("PASSWORD_CODE",row.get("event_key"));
            assertTrue(com.sap.jw.util.AesUtil.decrypt("0123456789abcdef0123456789abcdef",row.get("payload").toString()).contains(sentCode));
            assertFalse(db.queryForList("SELECT * FROM sys_mail_log").toString().contains(sentCode));
            service.reset(id,sentCode,"new-password");
            verify(email,never()).deliver(anyString(),anyString(),anyString());
        } finally {queue.stop();}
    }
}
