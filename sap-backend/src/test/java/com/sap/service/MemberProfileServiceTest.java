package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.entity.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MemberProfileServiceTest {
    JdbcTemplate jdbc;
    MemberProfileService service;

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds); service = new MemberProfileService(jdbc);
        // 从真实实体字段生成轻量数据库，捕获关联查询拼错字段/错误外键。
        for (Class<?> entity : List.of(User.class, UserRole.class, Term.class, Position.class, JoinApplication.class,
                JoinManager.class, StudyActivity.class, StudyMember.class, StudyLeader.class, StudyScore.class,
                StudyMaterial.class, Note.class, Message.class, MessageReply.class, MessageLike.class,
                AppFeedbackIssue.class, AppFeedbackComment.class)) {
            var columns = new ArrayList<String>();
            for (Field f : entity.getDeclaredFields()) {
                var column = f.getAnnotation(jakarta.persistence.Column.class);
                String name = column == null || column.name().isEmpty() ? f.getName() : column.name();
                String type = f.getType() == Long.class ? "BIGINT" : f.getType() == Integer.class ? "INT" :
                    f.getType() == Boolean.class ? "BOOLEAN" : f.getType() == LocalDateTime.class ? "TIMESTAMP" : "VARCHAR(10000)";
                columns.add(name + " " + type + (name.equals("deleted") ? " DEFAULT 0" : ""));
            }
            jdbc.execute("CREATE TABLE " + entity.getAnnotation(jakarta.persistence.Table.class).name() + "(" + String.join(",", columns) + ")");
        }
        jdbc.update("INSERT INTO sys_user(id,student_id,name,qq,password,status,created_at,deleted) VALUES (1,'20260001','同名成员','100001','NEVER_RETURN_PASSWORD',1,'2026-09-01 12:00:00',0),(2,'20260002','同名成员','100002','PRIVATE',1,'2026-09-02 12:00:00',0),(3,'20260003','已删除','100003','PRIVATE',0,NULL,1)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_code) VALUES (1,2),(1,3)");
        jdbc.update("INSERT INTO sys_position(id,position_name) VALUES(1,'技术部长')");
        jdbc.update("INSERT INTO sys_term(id,user_id,position_id,grade,created_at) VALUES(1,1,1,'2026','2026-09-20 08:00:00'),(2,2,1,'2026','2026-09-21 08:00:00')");
        jdbc.update("INSERT INTO join_application(id,user_id,manager_id,approved_by,status,created_at,approved_at,payment_code) VALUES(1,1,2,2,2,'2026-09-03 08:00:00','2026-09-04 09:00:00','PRIVATE_PAYMENT')");
        jdbc.update("INSERT INTO join_manager(id,user_id,grade,created_at) VALUES(1,1,'2026','2026-09-20 08:00:00')");
        jdbc.update("INSERT INTO study_activity(id,title,grade,status) VALUES(1,'学习小队','2026',1)");
        jdbc.update("INSERT INTO study_leader(id,activity_id,user_id) VALUES(10,1,2),(20,1,1)");
        jdbc.update("INSERT INTO study_member(id,activity_id,user_id,leader_id,week) VALUES(1,1,1,10,1),(2,1,2,20,1)");
        jdbc.update("INSERT INTO study_score(id,activity_id,member_user_id,leader_user_id,week,score,comment) VALUES(1,1,1,2,1,9,'继续努力'),(2,1,2,1,1,8,'已完成')");
        jdbc.update("INSERT INTO study_material(id,activity_id,user_id,week,file_type,title,file_name) VALUES(1,1,1,1,2,'算法练习','答案.pdf')");
        jdbc.update("INSERT INTO sap_note(id,author_id,title,description) VALUES(1,1,'编程笔记','算法摘要'),(2,2,'其他人的笔记','不可串档')");
        jdbc.update("INSERT INTO msg_board(id,user_id,content) VALUES(1,1,'会员留言'),(2,2,'其他留言')");
        jdbc.update("INSERT INTO msg_reply(id,message_id,user_id,content) VALUES(1,2,1,'会员回复')");
        jdbc.update("INSERT INTO msg_like(id,user_id,target_type,target_id) VALUES(1,1,0,2)");
        jdbc.update("INSERT INTO app_feedback_issue(id,reporter_id,title,content,status) VALUES(1,1,'课程反馈','详情','OPEN')");
        jdbc.update("INSERT INTO app_feedback_comment(id,issue_id,author_id,content,admin_reply) VALUES(1,1,1,'补充回复',FALSE)");
    }

    @SuppressWarnings("unchecked")
    @Test void returnsExplicitProfileAndAllLinkedSectionsWithoutSecretsOrLogs() {
        var profile = service.profile(1L);
        assertEquals("20260001", ((Map<String,Object>) profile.get("user")).get("studentId"));
        assertEquals(List.of(2,3), profile.get("roles"));
        assertEquals("2026-09-04 09:00:00.0", profile.get("joinedAt").toString());
        assertEquals("2026-09-20 08:00:00.0", profile.get("firstArchivedAt").toString());
        var sections = (List<Map<String,Object>>) profile.get("sections");
        assertEquals(12, sections.size());
        for (var section : sections) {
            var data = service.relations(1L, (String) section.get("key"), 1, 12);
            assertEquals(section.get("total"), data.get("total"));
            assertFalse(((List<?>)data.get("records")).isEmpty(), section.get("key").toString());
            String serialized = com.alibaba.fastjson2.JSON.toJSONString(data);
            assertFalse(serialized.contains("PRIVATE"));
            assertFalse(serialized.contains("不可串档"));
        }
        String serialized = com.alibaba.fastjson2.JSON.toJSONString(profile);
        assertFalse(serialized.toLowerCase().contains("password"));
        assertFalse(serialized.contains("paymentCode"));
        assertFalse(serialized.contains("log"));
    }

    @SuppressWarnings("unchecked")
    @Test void studyResolvesLeaderRecordIdAndMemberScoreRatherThanSameName() {
        var row = ((List<Map<String,Object>>)service.relations(1L,"study",1,12).get("records")).getFirst();
        assertEquals("同名成员", row.get("leaderName"));
        assertEquals(9, row.get("score"));
        assertEquals("继续努力", row.get("comment"));
        assertEquals(1L, service.relations(1L,"study",1,12).get("total"));
    }

    @SuppressWarnings("unchecked")
    @Test void paginatesAndBoundsSizeWithDeterministicOrdering() {
        jdbc.update("INSERT INTO sys_term(id,user_id,position_id,grade,created_at) VALUES(3,1,1,'2025','2025-09-20 08:00:00')");
        var first = service.relations(1L,"terms",1,1);
        var second = service.relations(1L,"terms",2,1);
        assertEquals(2L, first.get("total"));
        assertEquals("2026", ((List<Map<String,Object>>) first.get("records")).getFirst().get("grade"));
        assertEquals("2025", ((List<Map<String,Object>>) second.get("records")).getFirst().get("grade"));
        assertEquals(50, service.relations(1L,"terms",-1,10000).get("size"));
        assertEquals(List.of(), service.relations(1L,"terms",Integer.MAX_VALUE,50).get("records"));
    }

    @Test void deletedOrUnknownMembersAndUnlistedRelationsCannotBeRead() {
        assertThrows(BusinessException.class, () -> service.profile(3L));
        assertThrows(BusinessException.class, () -> service.profile(999L));
        assertThrows(BusinessException.class, () -> service.relations(1L,"sys_log",1,10));
        assertThrows(BusinessException.class, () -> service.relations(1L,"terms; DROP TABLE sys_user",1,10));
        assertNull(service.profile(2L).get("joinedAt"), "不能把注册或建档日期冒充入会日期");
        jdbc.update("UPDATE sys_term SET deleted=1 WHERE user_id=1");
        assertEquals(0L, service.relations(1L,"terms",1,12).get("total"));
    }
}
