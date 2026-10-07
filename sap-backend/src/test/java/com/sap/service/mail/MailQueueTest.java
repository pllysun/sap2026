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

class MailQueueTest {
    MailStore store; MailQueue queue; EmailService smtp; JdbcTemplate db;
    @BeforeEach void setup() {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);store=new MailStore(db,new DataSourceTransactionManager(ds));store.init();
        db.execute("CREATE TABLE sys_email_template(id BIGINT PRIMARY KEY,template_key VARCHAR(80),deleted INT)");
        smtp=mock(EmailService.class);when(smtp.sendingEnabled()).thenReturn(true);
        queue=new MailQueue(store,smtp,new ObjectMapper()) {@Override public synchronized void kick(){}};
        ReflectionTestUtils.setField(queue,"key","0123456789abcdef0123456789abcdef");queue.start();
    }
    @AfterEach void stop(){queue.stop();}
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    String enqueue(){return queue.enqueueTest("user@example.com","Test","<p>private content</p>",1L);}
    @Test void downloadAlertIsDeduplicatedPersistentAndUsesAuditedQueue() {
        String first = queue.enqueueDownloadAlert("operator@example.com", "2026-10-04:epoch", "2026-10-04", 21, 20);
        assertEquals(first, queue.enqueueDownloadAlert("operator@example.com", "2026-10-04:epoch", "2026-10-04", 22, 20));
        assertEquals(1, count("sys_mail_queue"));
        var message = db.queryForMap("SELECT * FROM sys_mail_queue");
        assertEquals("APP_DOWNLOAD_ALERT", message.get("event_key"));
        assertEquals("operator@example.com", message.get("recipient"));
        String payload = message.get("payload").toString();
        assertFalse(payload.contains("<h2>"));
        assertTrue(com.sap.jw.util.AesUtil.decrypt("0123456789abcdef0123456789abcdef", payload).contains("防护计数已达 21 次"));
        queue.enqueueDownloadAlert("operator@example.com", "2026-10-05:epoch", "2026-10-05", 21, 20);
        assertEquals(2, count("sys_mail_queue"));
        assertEquals(2, count("sys_mail_log"));
    }
    void clearGap(){db.update("UPDATE sys_mail_worker SET next_at=0 WHERE id=1");}
    Map<String,Object> businessContext() {
        var user=new com.sap.entity.User();user.setId(10L);user.setName("触发时姓名");user.setStudentId("20260001");
        var admin=new com.sap.entity.User();admin.setId(20L);admin.setName("回复管理员");admin.setStudentId("20200020");
        return MailAuditContext.event(MailHook.ISSUE_REPLIED,user,MailAuditContext.person(admin),"管理员回复问题，通知提交人",Map.of("问题编号",8L,"问题标题","课表显示","回复摘要","问题已修复"));
    }
    @Test void skippedMailStillHasBusinessSnapshotAndCorrectActor() {
        var context=businessContext();
        assertNull(queue.enqueue(MailHook.ISSUE_REPLIED,10L,"user@example.com",Map.of(),"comment:100",null,context));
        var rows=(List<Map<String,Object>>)queue.page("messages",1,20).get("records");
        assertEquals(1,rows.size());assertEquals(20L,((Number)rows.get(0).get("actor_id")).longValue());
        var saved=(Map<?,?>)rows.get(0).get("context");
        assertEquals("20260001",((Map<?,?>)saved.get("recipient")).get("account"));
        assertEquals("回复管理员",((Map<?,?>)saved.get("operator")).get("name"));
        assertEquals("问题已修复",((Map<?,?>)saved.get("details")).get("回复摘要"));
        assertEquals("SKIPPED",rows.get(0).get("status"));
        when(smtp.getTemplate(3L)).thenReturn(Map.of("enabled",false));
        db.update("UPDATE sys_mail_binding SET template_id=3 WHERE event_key='ISSUE_REPLIED'");
        assertNull(queue.enqueue(MailHook.ISSUE_REPLIED,10L,"user@example.com",Map.of(),"comment:101",null,context));
        assertEquals(2L,queue.page("messages",1,20).get("total"));
    }
    @Test void businessSnapshotSurvivesQueueFailureRetryAndSuccess() {
        when(smtp.getTemplate(3L)).thenReturn(Map.of("enabled",true,"subject","问题回复","htmlContent","{{name}} {{issueId}} {{issueTitle}} {{issueStatus}} {{adminName}} {{replyContent}} {{repliedAt}}"));
        db.update("UPDATE sys_mail_binding SET template_id=3 WHERE event_key='ISSUE_REPLIED'");
        when(smtp.render(anyString(),anyString(),anyMap())).thenReturn(Map.of("subject","问题回复","html","<p>邮件正文</p>"));
        String id=queue.enqueue(MailHook.ISSUE_REPLIED,10L,"user@example.com",Map.of("name","触发时姓名","issueId",8,"issueTitle","课表显示","issueStatus","处理中","adminName","回复管理员","replyContent","问题已修复","repliedAt","2026-09-06"),"comment:103",null,businessContext());
        var job=queue.claim();queue.finish(job,"FAILED","网络失败");
        var failed=(List<Map<String,Object>>)queue.page("failed",1,20).get("records");
        assertEquals("20260001",((Map<?,?>)((Map<?,?>)failed.get(0).get("context")).get("recipient")).get("account"));
        queue.failedAction(List.of(id),"retry",30L);clearGap();queue.finish(queue.claim(),"SUCCESS",null);
        var rows=(List<Map<String,Object>>)queue.page("messages",1,20).get("records");
        var saved=(Map<?,?>)rows.get(0).get("context");
        assertEquals("触发时姓名",((Map<?,?>)saved.get("recipient")).get("name"));
        assertEquals(20,((Number)((Map<?,?>)saved.get("initiator")).get("id")).intValue());
        assertEquals("系统工作器",((Map<?,?>)saved.get("operator")).get("name"));
        var history=(List<Map<String,Object>>)queue.history(((Number)rows.get(0).get("id")).longValue(),1,100).get("records");
        var retry=history.stream().filter(r->"RETRY".equals(r.get("status"))).findFirst().orElseThrow();
        assertEquals(30,((Number)((Map<?,?>)((Map<?,?>)retry.get("context")).get("operator")).get("id")).intValue());
        assertEquals(0,count("sys_mail_queue"));assertEquals(0,count("sys_mail_failed"));
    }
    @Test void legacyRegistrationUsesExplicitlyLabelledCurrentIdentityWithoutRewritingLogs() {
        var users=mock(com.sap.mapper.UserMapper.class);var user=new com.sap.entity.User();
        user.setId(10L);user.setName("当前姓名");user.setStudentId("20260001");when(users.selectById(10L)).thenReturn(user);
        ReflectionTestUtils.setField(store,"users",users);
        db.update("INSERT INTO sys_mail_log(event_key,status,actor_id,created_at) VALUES('ACCOUNT_REGISTERED','SKIPPED',10,1)");
        var row=((List<Map<String,Object>>)queue.page("messages",1,20).get("records")).get(0);
        var context=(Map<?,?>)row.get("context");assertEquals("legacy",context.get("source"));
        assertEquals("当前姓名",((Map<?,?>)context.get("recipient")).get("name"));
        assertNull(db.queryForObject("SELECT context_json FROM sys_mail_log",String.class));
        store.init(); // 重复启动迁移不丢失原数据。
        assertEquals(1,count("sys_mail_log"));
    }
    @Test void subsetTemplateCannotBindToIssueEvent() {
        var member=Map.<String,Object>of("subject","welcome","htmlContent","{{name}}");
        when(smtp.getTemplate(3L)).thenReturn(member);
        assertThrows(com.sap.common.BusinessException.class,()->queue.bind(MailHook.ISSUE_REPLIED,3L,Map.of(),1L));
        assertThrows(com.sap.common.BusinessException.class,()->MailQueue.validateContract(MailHook.MEMBER_JOINED,Map.of("subject","hi","htmlContent","")));
        var matching=Map.<String,Object>of("subject","{{issueTitle}}","htmlContent","{{ name }} {{issueId}} {{issueStatus}} {{adminName}} {{replyContent}} {{repliedAt}} {{name}}");
        assertDoesNotThrow(()->MailQueue.validateContract(MailHook.ISSUE_REPLIED,matching));
    }
    @Test void editingBoundTemplateCannotRemoveRequiredParameters() {
        var template=Map.<String,Object>of("subject","{{issueTitle}}","htmlContent","{{name}} {{issueId}} {{issueStatus}} {{adminName}} {{replyContent}} {{repliedAt}}");
        when(smtp.getTemplate(3L)).thenReturn(template);
        queue.bind(MailHook.ISSUE_REPLIED,3L,Map.of(),1L);
        assertThrows(com.sap.common.BusinessException.class,()->queue.validateBoundTemplate(3L,Map.of("subject","hi","htmlContent","{{name}}")));
    }
    @Test void messageListUsesLatestEventAndKeepsIndependentSystemEvents() {
        String first=enqueue();queue.finish(queue.claim(),"SUCCESS",null);
        String second=enqueue();clearGap();queue.finish(queue.claim(),"FAILED","failure");
        store.audit(null,"ACCOUNT_REGISTERED",null,null,"SKIPPED","disabled",1L);
        store.audit(null,"ACCOUNT_REGISTERED",null,null,"SKIPPED","disabled",2L);
        var page=queue.page("messages",1,20);
        assertEquals(4L,page.get("total"));
        var rows=(List<Map<String,Object>>)page.get("records");
        assertEquals("SUCCESS",rows.stream().filter(r->first.equals(r.get("message_id"))).findFirst().orElseThrow().get("status"));
        var secondRow=rows.stream().filter(r->second.equals(r.get("message_id"))).findFirst().orElseThrow();
        assertEquals("FAILED",secondRow.get("status"));
        assertEquals(3L,queue.history(((Number)secondRow.get("id")).longValue(),1,20).get("total"));
        queue.failedAction(List.of(second),"retry",1L);
        var newest=((List<Map<String,Object>>)queue.page("messages",1,1).get("records")).get(0);
        assertEquals("RETRY",newest.get("status"));
        assertEquals(4L,queue.history(((Number)newest.get("id")).longValue(),1,20).get("total"));
        assertEquals(1,((List<?>)queue.history(((Number)newest.get("id")).longValue(),2,3).get("records")).size());
    }
    @Test void oldMismatchedBindingIsSkippedWithoutBreakingBusiness() {
        db.update("UPDATE sys_mail_binding SET template_id=3 WHERE event_key='ISSUE_REPLIED'");
        when(smtp.getTemplate(3L)).thenReturn(Map.of("subject","hi","htmlContent","{{name}}","enabled",true));
        assertNull(queue.enqueue(MailHook.ISSUE_REPLIED,1L,"u@example.com",Map.of(),"old-binding",null));
        assertEquals(0,count("sys_mail_queue"));
        assertEquals("SKIPPED",db.queryForObject("SELECT status FROM sys_mail_log",String.class));
        assertEquals(false,queue.bindings().stream().filter(b->b.get("eventKey").equals("ISSUE_REPLIED")).findFirst().orElseThrow().get("compatible"));
    }
    @Test void firstStartupBindsAllTemplatesWithoutEnablingOrSending() throws Exception {
        db.update("DELETE FROM sys_mail_binding");
        var templates = new ObjectMapper().readValue(java.nio.file.Path.of("../templates/email/templates.json").toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() {});
        long id = 2;
        for (var template : templates) {
            db.update("INSERT INTO sys_email_template VALUES (?,?,0)", id, template.get("templateKey"));
            when(smtp.getTemplate(id++)).thenReturn(template);
        }
        queue.start();
        assertEquals(MailHook.values().length, queue.bindings().stream().filter(b -> b.get("templateId") != null).count());
        assertEquals(0, count("sys_mail_queue"));
        verify(smtp, never()).deliver(anyString(), anyString(), anyString());
        queue.bind(MailHook.MEMBER_JOINED, null, Map.of(), 1L);
        queue.start();
        assertEquals(MailHook.values().length - 1, queue.bindings().stream().filter(b -> b.get("templateId") != null).count());
    }
    @Test void startupDoesNotBindIncompatibleTemplate() {
        db.update("DELETE FROM sys_mail_binding WHERE event_key='MEMBER_JOINED'");
        db.update("INSERT INTO sys_email_template VALUES (3,'member.upgraded',0)");
        when(smtp.getTemplate(3L)).thenReturn(Map.of("subject","hello","htmlContent","{{unknown}}"));
        queue.start();
        assertNull(db.queryForMap("SELECT template_id FROM sys_mail_binding WHERE event_key='MEMBER_JOINED'").get("template_id"));
        assertEquals(1, count("sys_mail_log"));
    }
    @Test void successDeletesQueueAndPersistsCooldown() {
        enqueue();enqueue();var job=queue.claim();assertNotNull(job);assertNull(queue.claim());queue.finish(job,"SUCCESS",null);
        assertEquals(1,count("sys_mail_queue"));assertEquals(0,count("sys_mail_failed"));assertNull(queue.claim());
        assertTrue(db.queryForObject("SELECT next_at FROM sys_mail_worker",Long.class)>=System.currentTimeMillis()+64000);
        clearGap();assertNotNull(queue.claim());
    }
    @Test void failureRetryIgnoreDeleteAreAtomicAndAudited() {
        String id=enqueue();queue.finish(queue.claim(),"FAILED","simulated failure");
        assertEquals(0,count("sys_mail_queue"));assertEquals(1,count("sys_mail_failed"));
        assertTrue(String.valueOf(queue.failedDetail(id).get("html")).contains("private content"));
        queue.failedAction(List.of(id),"ignore",4L);assertEquals("IGNORED",db.queryForObject("SELECT status FROM sys_mail_failed",String.class));
        queue.failedAction(List.of(id),"retry",4L);queue.failedAction(List.of(id),"retry",4L);
        assertEquals(1,count("sys_mail_queue"));assertEquals(0,count("sys_mail_failed"));
        clearGap();queue.finish(queue.claim(),"FAILED","again");assertEquals(2,db.queryForObject("SELECT attempts FROM sys_mail_failed",Integer.class));
        queue.failedAction(List.of(id),"delete",4L);assertEquals(0,count("sys_mail_failed"));assertTrue(count("sys_mail_log")>=7);
    }
    @Test void expiredLeaseIsUnknownNotAutomaticallyResent() {
        enqueue();queue.claim();db.update("UPDATE sys_mail_worker SET lease_until=0");assertNull(queue.claim());
        assertEquals(0,count("sys_mail_queue"));assertEquals("UNKNOWN",db.queryForObject("SELECT status FROM sys_mail_failed",String.class));
        verify(smtp,never()).deliver(anyString(),anyString(),anyString());
    }
    @Test void concurrentWorkersClaimExactlyOne() throws Exception {
        enqueue();enqueue();var pool=Executors.newFixedThreadPool(2);
        try {var futures=pool.invokeAll(List.<Callable<Map<String,Object>>>of(()->queue.claim(),()->queue.claim()));
            int claimed=0;for(var f:futures)if(f.get()!=null)claimed++;assertEquals(1,claimed);
        } finally {pool.shutdownNow();}
    }
    @Test void payloadEncryptedAndListsContainNoHtml() {
        enqueue();String payload=db.queryForObject("SELECT payload FROM sys_mail_queue",String.class);
        assertFalse(payload.contains("private content"));assertFalse(queue.page("queue",1,20).toString().contains("private content"));
    }
    @Test void rollingBackBusinessAlsoRollsBackQueue() {
        assertThrows(RuntimeException.class,()->store.transaction(()->{enqueue();throw new RuntimeException("rollback");}));
        assertEquals(0,count("sys_mail_queue"));assertEquals(0,count("sys_mail_log"));
    }
    @Test void boundTemplatesCannotBeDeletedAndUnbindIsDurable() {
        when(smtp.getTemplate(3L)).thenReturn(Map.of("subject","hello","htmlContent","{{name}}"));
        queue.bind(MailHook.MEMBER_JOINED,3L,Map.of(),1L);
        assertThrows(com.sap.common.BusinessException.class,()->store.requireUnbound(3L));
        queue.bind(MailHook.MEMBER_JOINED,null,Map.of(),1L);queue.start();assertDoesNotThrow(()->store.requireUnbound(3L));
    }
    @Test void hookDedupSurvivesSuccessfulSend() {
        var template=Map.<String,Object>of("subject","hello","htmlContent","{{name}}","enabled",true);
        when(smtp.getTemplate(3L)).thenReturn(template);when(smtp.render(anyString(),anyString(),anyMap())).thenReturn(Map.of("subject","hello","html","<p>A</p>"));
        queue.bind(MailHook.MEMBER_JOINED,3L,Map.of(),1L);
        String id=queue.enqueue(MailHook.MEMBER_JOINED,1L,"u@example.com",Map.of("name","A"),"user:1",null);
        queue.finish(queue.claim(),"SUCCESS",null);
        assertEquals(id,queue.enqueue(MailHook.MEMBER_JOINED,1L,"u@example.com",Map.of("name","A"),"user:1",null));assertEquals(0,count("sys_mail_queue"));
    }
    @Test void failedPasswordIsRedactedAndNotRetryable() {
        String id=enqueue();db.update("UPDATE sys_mail_queue SET event_key='PASSWORD_CODE',expires_at=?",System.currentTimeMillis()+60000);
        queue.finish(queue.claim(),"FAILED","test");assertFalse(queue.failedDetail(id).get("html").toString().contains("private content"));
        assertThrows(com.sap.common.BusinessException.class,()->queue.failedAction(List.of(id),"retry",1L));assertEquals(1,count("sys_mail_failed"));
    }
    @Test void expiredMailNeverReachesSmtp() {
        enqueue();db.update("UPDATE sys_mail_queue SET expires_at=1");queue.stop();ReflectionTestUtils.invokeMethod(queue,"work");
        verify(smtp,never()).deliver(anyString(),anyString(),anyString());assertEquals("EXPIRED",db.queryForObject("SELECT status FROM sys_mail_failed",String.class));
    }
    @Test void appRegistrationQueueEncryptsCodeAndCannotExposeOrRetryFailedCode() {
        var template=Map.<String,Object>of("subject","注册邮箱验证","htmlContent","{{name}} {{account}} {{email}} {{code}} {{expiresInMinutes}}","enabled",true);
        when(smtp.getTemplate(3L)).thenReturn(template);
        when(smtp.render(anyString(),anyString(),anyMap())).thenReturn(Map.of("subject","注册邮箱验证","html","<p>654321</p>"));
        queue.bind(MailHook.APP_REGISTRATION_CODE,3L,Map.of(),1L);
        var vars=Map.<String,Object>of("name","测试用户","account","20260001","email","123456789@qq.com","code","654321","expiresInMinutes",15);
        assertThrows(com.sap.common.BusinessException.class,()->queue.enqueue(MailHook.APP_REGISTRATION_CODE,null,"123456789@qq.com",vars,"app:no-expiry",null));
        String id=queue.enqueue(MailHook.APP_REGISTRATION_CODE,null,"123456789@qq.com",vars,"app:1",System.currentTimeMillis()+900000);
        assertNotNull(id);
        assertFalse(db.queryForObject("SELECT payload FROM sys_mail_queue",String.class).contains("654321"));
        assertFalse(queue.page("queue",1,20).toString().contains("654321"));
        queue.finish(queue.claim(),"FAILED","test");
        assertFalse(queue.failedDetail(id).toString().contains("654321"));
        assertFalse(queue.page("logs",1,100).toString().contains("654321"));
        assertThrows(com.sap.common.BusinessException.class,()->queue.failedAction(List.of(id),"retry",1L));
        assertThrows(com.sap.common.BusinessException.class,()->MailQueue.validateContract(MailHook.APP_REGISTRATION_CODE,
                Map.of("subject","{{code}}","htmlContent",template.get("htmlContent"))));
    }
    @Test void workerDecryptsAndDoesNotSendDuringCooldown() {
        enqueue();enqueue();queue.stop();ReflectionTestUtils.invokeMethod(queue,"work");ReflectionTestUtils.invokeMethod(queue,"work");
        verify(smtp,times(1)).deliver("user@example.com","Test","<p>private content</p>");assertEquals(1,count("sys_mail_queue"));
    }
    @Test void workerTransfersSmtpFailureWithoutSensitiveExceptionText() {
        enqueue();doThrow(new RuntimeException("password=secret code=123456")).when(smtp).deliver(anyString(),anyString(),anyString());
        queue.stop();ReflectionTestUtils.invokeMethod(queue,"work");assertEquals(0,count("sys_mail_queue"));assertEquals(1,count("sys_mail_failed"));
        assertFalse(queue.page("logs",1,100).toString().contains("secret"));assertFalse(queue.page("failed",1,100).toString().contains("123456"));
    }
    @Test void contractsRejectUnknownVariablesAndCodesInSubject() {
        assertThrows(com.sap.common.BusinessException.class,()->MailQueue.validateContract(MailHook.MEMBER_JOINED,Map.of("subject","hi","htmlContent","{{unknown}}")));
        assertThrows(com.sap.common.BusinessException.class,()->MailQueue.validateContract(MailHook.PASSWORD_CODE,Map.of("subject","{{ code }}","htmlContent","{{name}} {{account}} {{code}} {{expiresInMinutes}}")));
        assertDoesNotThrow(()->MailQueue.validateContract(MailHook.PASSWORD_CODE,Map.of("subject","安全验证","htmlContent","{{name}} {{account}} {{code}} {{expiresInMinutes}}")));
    }
}
