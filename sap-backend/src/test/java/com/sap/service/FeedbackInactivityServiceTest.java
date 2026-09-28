package com.sap.service;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackInactivityServiceTest {
    JdbcTemplate jdbc;FeedbackInactivityService service;
    LocalDateTime now=LocalDateTime.of(2026,9,11,12,0);
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);service=new FeedbackInactivityService(jdbc,new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE app_feedback_issue(id BIGINT PRIMARY KEY,status VARCHAR(16),updated_at TIMESTAMP,deleted INT,closed_by BIGINT,closed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE app_feedback_comment(id BIGINT AUTO_INCREMENT PRIMARY KEY,issue_id BIGINT,author_id BIGINT,content VARCHAR(2000),admin_reply INT,created_at TIMESTAMP,deleted INT)");
    }
    void issue(int id,LocalDateTime changed,boolean maintainer) {
        jdbc.update("INSERT INTO app_feedback_issue(id,status,updated_at,deleted) VALUES (?,'OPEN',?,0)",id,changed);
        if (maintainer) reply(id,now.minusDays(9),1);
    }
    void reply(int id,LocalDateTime date,int admin) {
        jdbc.update("INSERT INTO app_feedback_comment(issue_id,author_id,content,admin_reply,created_at,deleted) VALUES (?,1,'回复',?,?,0)",id,admin,date);
    }
    @Test void onlyInactiveMaintainerHandledIssuesCloseOnce() {
        issue(1,now.minusDays(8),true);issue(2,now.minusDays(20),false);
        issue(3,now.minusDays(8),true);reply(3,now.minusDays(1),0);
        issue(4,now.minusDays(1),true); // 近期重新打开
        issue(5,now.minusDays(7),true); // 边界，不是超过七天
        issue(6,now.minusDays(8),true);jdbc.update("UPDATE app_feedback_issue SET status='CLOSED' WHERE id=6");
        assertEquals(1,service.closeInactive(now));assertEquals(0,service.closeInactive(now));
        assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM app_feedback_issue WHERE id=1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM app_feedback_comment WHERE author_id=0",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT content FROM app_feedback_comment WHERE author_id=0",String.class).contains("7 天"));
    }
    @Test void commentFailureRollsBackClosure() {
        issue(1,now.minusDays(8),true);
        jdbc.execute("ALTER TABLE app_feedback_comment ADD CONSTRAINT no_system CHECK (author_id<>0)");
        assertThrows(Exception.class,()->service.closeInactive(now));
        assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM app_feedback_issue WHERE id=1",String.class));
    }
}
