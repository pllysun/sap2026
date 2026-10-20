package com.sap.service.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.service.EmailService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailPriorityDeliveryTest {
    MailStore store;JdbcTemplate db;EmailService smtp;MailQueue mail;
    long order=System.currentTimeMillis();
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        db=new JdbcTemplate(ds);store=new MailStore(db,new DataSourceTransactionManager(ds));store.init();
        db.execute("CREATE TABLE sys_email_template(id BIGINT PRIMARY KEY,template_key VARCHAR(80),deleted INT)");
        smtp=mock(EmailService.class);mail=worker();
    }
    MailQueue worker() {
        var q=new MailQueue(store,smtp,new ObjectMapper());ReflectionTestUtils.setField(q,"key","0123456789abcdef0123456789abcdef");return q;
    }
    @AfterEach void stop(){mail.stop();}
    // Fixture classification is committed atomically before the real dispatcher is awakened.
    String put(String event,String subject) {
        return store.transaction(()->{
            var id=mail.enqueueTest("user@example.test",subject,"<p>test</p>",null);
            db.update("UPDATE sys_mail_queue SET event_key=?,expires_at=?,created_at=? WHERE id=?",event,
                    MailHook.isVerificationCode(event)?System.currentTimeMillis()+900000:null,++order,id);
            return id;
        });
    }
    @Test void laterCodeIsClaimedBeforeEarlierWelcomeAndNotificationWaitsUntilCodeCompletes() {
        String welcome=put("ACCOUNT_REGISTERED","Welcome");String code=put("APP_REGISTRATION_CODE","Code");
        var first=mail.claim();assertEquals(code,first.get("id"));assertNull(mail.claim());
        mail.finish(first,"SUCCESS",null);assertEquals(welcome,mail.claim().get("id"));
    }
    @Test void inFlightWelcomeDoesNotBlockCodeAndSecondWelcomeRemainsQueued() throws Exception {
        var welcomeStarted=new CountDownLatch(1);var releaseWelcome=new CountDownLatch(1);var codeStarted=new CountDownLatch(1);
        doAnswer(call->{
            if("Welcome one".equals(call.getArgument(1))) {welcomeStarted.countDown();assertTrue(releaseWelcome.await(5,TimeUnit.SECONDS));}
            if("Code".equals(call.getArgument(1))) codeStarted.countDown();
            return null;
        }).when(smtp).deliver(anyString(),anyString(),anyString());
        put("ACCOUNT_REGISTERED","Welcome one");String queued=put("MEMBER_JOINED","Welcome two");
        when(smtp.sendingEnabled()).thenReturn(true);
        try {
            mail.kick();assertTrue(welcomeStarted.await(5,TimeUnit.SECONDS));
            put("PASSWORD_CODE","Code");assertTrue(codeStarted.await(5,TimeUnit.SECONDS),"Code must not wait for the welcome SMTP call");
            assertEquals("PENDING",db.queryForObject("SELECT state FROM sys_mail_queue WHERE id=?",String.class,queued));
            verify(smtp,never()).deliver(anyString(),eq("Welcome two"),anyString());
        } finally {releaseWelcome.countDown();}
    }
    @Test void notificationCooldownSurvivesRestartButDoesNotDelayCodesOrOtherImmediateMail() {
        put("MEMBER_JOINED","Welcome");mail.finish(mail.claim(),"SUCCESS",null);
        long next=db.queryForObject("SELECT next_at FROM sys_mail_worker",Long.class);assertTrue(next>=System.currentTimeMillis()+64000);
        String welcome=put("ACCOUNT_REGISTERED","Next welcome");put("PASSWORD_CODE","Code");put("ISSUE_REPLIED","Reply");
        var restarted=worker();
        try {
            restarted.start();assertEquals(next,db.queryForObject("SELECT next_at FROM sys_mail_worker",Long.class));
            var code=restarted.claim();assertEquals("PASSWORD_CODE",code.get("event_key"));restarted.finish(code,"SUCCESS",null);
            var reply=restarted.claim();assertEquals("ISSUE_REPLIED",reply.get("event_key"));restarted.finish(reply,"SUCCESS",null);
            assertNull(restarted.claim());db.update("UPDATE sys_mail_worker SET next_at=0");assertEquals(welcome,restarted.claim().get("id"));
        } finally {restarted.stop();}
    }
    @Test void twoWorkersAllowOnlyOneQueuedNotificationInFlight() throws Exception {
        put("ACCOUNT_REGISTERED","First");put("MEMBER_JOINED","Second");var other=worker();var pool=Executors.newFixedThreadPool(2);
        try {
            var results=pool.invokeAll(List.<Callable<Map<String,Object>>>of(mail::claim,other::claim));
            int claimed=0;for(var result:results)if(result.get()!=null)claimed++;assertEquals(1,claimed);
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE state='SENDING'",Integer.class));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE state='PENDING'",Integer.class));
        } finally {pool.shutdownNow();other.stop();}
    }
    @Test void normalBacklogIsNotDrainedWhileVerificationBurstIsSending() throws Exception {
        var allCodesStarted=new CountDownLatch(3);var releaseCodes=new CountDownLatch(1);
        doAnswer(call->{if(call.getArgument(1).toString().startsWith("Code")){allCodesStarted.countDown();assertTrue(releaseCodes.await(5,TimeUnit.SECONDS));}return null;})
                .when(smtp).deliver(anyString(),anyString(),anyString());
        for(int i=0;i<12;i++)put(i%2==0?"ACCOUNT_REGISTERED":"MEMBER_JOINED","Welcome "+i);
        for(int i=0;i<3;i++)put(i%2==0?"APP_REGISTRATION_CODE":"PASSWORD_CODE","Code "+i);
        when(smtp.sendingEnabled()).thenReturn(true);
        try {
            mail.kick();assertTrue(allCodesStarted.await(5,TimeUnit.SECONDS));
            assertEquals(12,db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE event_key IN ('ACCOUNT_REGISTERED','MEMBER_JOINED') AND state='PENDING'",Integer.class));
            verify(smtp,never()).deliver(anyString(),startsWith("Welcome"),anyString());
        } finally {releaseCodes.countDown();}
    }
    @Test void statusAndAuditExposeCorrectDeliveryPolicies() {
        put("ACCOUNT_REGISTERED","Welcome");put("APP_REGISTRATION_CODE","Code");
        var status=mail.status();assertEquals("VERIFICATION_FIRST",status.get("deliveryMode"));
        assertEquals(1L,((Number)((Map<?,?>)status.get("verification")).get("pending")).longValue());
        assertEquals(1L,((Number)((Map<?,?>)status.get("notifications")).get("pending")).longValue());
        var rows=(List<Map<String,Object>>)mail.page("queue",1,20).get("records");
        assertTrue(rows.stream().anyMatch(r->r.get("event_key").equals("ACCOUNT_REGISTERED")&&r.get("deliveryPolicy").equals("NOTIFICATION_QUEUE")));
        assertEquals("VERIFICATION",MailHook.PASSWORD_CODE.definition().get("deliveryPolicy"));
        assertEquals("NOTIFICATION_QUEUE",MailHook.ACCOUNT_REGISTERED.definition().get("deliveryPolicy"));
        assertEquals("IMMEDIATE",MailHook.ISSUE_REPLIED.definition().get("deliveryPolicy"));
    }
}
