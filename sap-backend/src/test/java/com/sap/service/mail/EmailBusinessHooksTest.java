package com.sap.service.mail;

import com.sap.entity.*;
import com.sap.mapper.UserMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailBusinessHooksTest {
    MailQueue queue;UserMapper users;EmailBusinessHooks hooks;JdbcTemplate db;User user;
    @BeforeEach void setup(){
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);var store=new MailStore(db,new DataSourceTransactionManager(ds));store.init();
        queue=mock(MailQueue.class);users=mock(UserMapper.class);hooks=new EmailBusinessHooks(queue,store,users);
        user=new User();user.setId(10L);user.setName("测试用户");user.setStudentId("20260001");user.setQq("123456789");when(users.selectById(10L)).thenReturn(user);
    }
    @Test void registerAndMembershipUseTypedHooks(){hooks.registered(user);hooks.memberJoined(10L);
        verify(queue).enqueue(eq(MailHook.ACCOUNT_REGISTERED),eq(10L),eq("123456789@qq.com"),eq(Map.of("name","测试用户")),eq("user:10"),isNull(),anyMap());
        verify(queue).enqueue(eq(MailHook.MEMBER_JOINED),eq(10L),eq("123456789@qq.com"),eq(Map.of("name","测试用户")),eq("user:10"),isNull(),anyMap());}
    @Test void missingEmailStillRecordsWhoRegisteredAndWhy() {
        user.setQq(null);hooks.registered(user);verifyNoInteractions(queue);
        String context=db.queryForObject("SELECT context_json FROM sys_mail_log",String.class);
        assertTrue(context.contains("20260001"));assertTrue(context.contains("测试用户"));assertTrue(context.contains("账号注册成功"));
        assertEquals("SKIPPED",db.queryForObject("SELECT status FROM sys_mail_log",String.class));
    }
    @Test void issueExcerptRedactsLabelledSecretsAndLimitsSize() {
        assertFalse(MailAuditContext.excerpt("密码：unsafe-value 验证码:123456 token=private-token").contains("123456"));
        assertFalse(MailAuditContext.excerpt("密码：unsafe-value 验证码:123456 token=private-token").contains("unsafe-value"));
        assertTrue(MailAuditContext.excerpt("长内容".repeat(1000)).endsWith("（摘要已截断）"));
    }
    @Test void passwordHasExpiryAndMustBeSixDigits(){
        assertThrows(com.sap.common.BusinessException.class,()->hooks.passwordCode(user,"abc",Instant.now().plusSeconds(60),"request1"));
        hooks.passwordCode(user,"123456",Instant.now().plusSeconds(600),"request1");verify(queue).enqueue(eq(MailHook.PASSWORD_CODE),eq(10L),eq("123456789@qq.com"),argThat(v->"123456".equals(v.get("code"))),eq("request1"),anyLong(),argThat(context->!context.toString().contains("123456")));}
    @Test void nonAdminOrSelfRepliesDoNotNotify(){
        var issue=new AppFeedbackIssue();issue.setReporterId(10L);var comment=new AppFeedbackComment();comment.setAdminReply(false);hooks.issueReplied(issue,comment,user);verifyNoInteractions(queue);
        comment.setAdminReply(true);comment.setAuthorId(10L);hooks.issueReplied(issue,comment,user);verifyNoInteractions(queue);
    }
    @Test void appRegistrationTargetsPendingUsersQqAndKeepsCodeOutOfAudit() {
        Instant expiry=Instant.now().plusSeconds(900);
        assertThrows(com.sap.common.BusinessException.class,()->hooks.appRegistrationCode("20260001","测试用户","123456789","abc",expiry,"app:1"));
        assertThrows(com.sap.common.BusinessException.class,()->hooks.appRegistrationCode("20260001","测试用户","123456789","654321",Instant.now().minusSeconds(1),"app:1"));
        hooks.appRegistrationCode("20260001","测试用户","123456789","654321",expiry,"app:1");
        verify(queue).enqueue(eq(MailHook.APP_REGISTRATION_CODE),isNull(),eq("123456789@qq.com"),
                argThat(v->"654321".equals(v.get("code")) && "123456789@qq.com".equals(v.get("email")) && "20260001".equals(v.get("account"))),
                eq("app:1"),eq(expiry.toEpochMilli()),argThat(context->!context.toString().contains("654321")));
        verifyNoInteractions(users);
    }
    @Test void adminReplyNotifiesOnlyIssueOwner(){
        var issue=new AppFeedbackIssue();issue.setId(8L);issue.setTitle("问题");issue.setStatus("CLOSED");issue.setReporterId(10L);
        var comment=new AppFeedbackComment();comment.setId(9L);comment.setAdminReply(true);comment.setAuthorId(20L);comment.setContent("已解决");
        hooks.issueReplied(issue,comment,null);verify(queue).enqueue(eq(MailHook.ISSUE_REPLIED),eq(10L),eq("123456789@qq.com"),argThat(v->"已关闭".equals(v.get("issueStatus"))),eq("comment:9"),isNull(),argThat(context->Long.valueOf(20).equals(((Map<?,?>)context.get("initiator")).get("id"))&&context.toString().contains("已解决")));
    }
    @Test void studyQueriesMatchActualSchemaAndRemindAssignedReviewer(){
        db.execute("CREATE TABLE study_member(activity_id BIGINT,week INT,user_id BIGINT,leader_id BIGINT,deleted INT)");
        db.execute("CREATE TABLE study_leader(id BIGINT,user_id BIGINT,deleted INT)");
        db.execute("CREATE TABLE study_material(activity_id BIGINT,week INT,user_id BIGINT,file_type INT)");
        db.execute("CREATE TABLE study_score(activity_id BIGINT,week INT,member_user_id BIGINT)");
        db.execute("CREATE TABLE study_activity(id BIGINT,title VARCHAR(100),grade VARCHAR(10),seq_num INT)");
        db.update("INSERT INTO study_member VALUES(1,2,3,4,0)");db.update("INSERT INTO study_leader VALUES(4,10,0)");db.update("INSERT INTO study_material VALUES(1,2,3,2)");db.update("INSERT INTO study_activity VALUES(1,'学习任务','2026',1)");
        hooks.studySubmission(1L,2,3L);verify(queue).enqueue(eq(MailHook.STUDY_REVIEW_REQUESTED),eq(10L),eq("123456789@qq.com"),argThat(v->Long.valueOf(1).equals(v.get("pendingCount"))),startsWith("1:2:10:"),isNull(),argThat(context->Long.valueOf(3).equals(((Map<?,?>)context.get("initiator")).get("id"))));
        clearInvocations(queue);db.update("INSERT INTO study_score VALUES(1,2,3)");hooks.studySubmission(1L,2,3L);verifyNoInteractions(queue);
    }
}
