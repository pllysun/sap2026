package com.sap.service;

import com.sap.service.mail.MailQueue;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardInsightsServiceTest {
    JdbcTemplate db;DashboardInsightsService service;StatsService stats=mock(StatsService.class);MailQueue mail=mock(MailQueue.class);
    @BeforeEach void setup(){
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");db=new JdbcTemplate(ds);
        db.execute("CREATE TABLE sys_user(id BIGINT,name VARCHAR(50),student_id VARCHAR(20),qq VARCHAR(20),gender INT,status INT,created_at TIMESTAMP,deleted INT)");
        db.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_code INT)");db.execute("CREATE TABLE join_application(status INT)");
        db.execute("CREATE TABLE app_feedback_issue(status VARCHAR(20),deleted INT)");
        db.execute("CREATE TABLE sys_log(request_time TIMESTAMP,result_code INT,user_id BIGINT,endpoint VARCHAR(255),path VARCHAR(255),http_method VARCHAR(10))");
        for(int id=1;id<=4;id++)db.update("INSERT INTO sys_user VALUES(?,?,?,?,?,?,?,?)",id,"测试"+id,"2026000"+id,"12345678"+id,id==3?null:id%2,id==2?0:1,LocalDateTime.now(),id==4?1:0);
        db.update("INSERT INTO sys_user_role VALUES(1,0),(1,3),(2,3),(3,4)");
        db.update("INSERT INTO join_application VALUES(0),(1),(1),(2),(3)");
        db.update("INSERT INTO app_feedback_issue VALUES('OPEN',0),('CLOSED',0),('OPEN',1)");
        db.update("INSERT INTO sys_log VALUES(?,200,1,'/api/ping','/api/ping','GET'),(?,500,2,'/api/auth/info','/api/auth/info','GET')",LocalDateTime.now(),LocalDateTime.now());
        var rules=new ApiLogExclusionService(db,new DataSourceTransactionManager(ds));rules.initialize();
        var schedules=mock(ScheduleUsageService.class);when(schedules.summary(7)).thenReturn(new ScheduleUsageService.Summary(7,2,3,LocalDate.now(),List.of(),List.of()));
        var joins=mock(JoinService.class);when(joins.isJoinEnabled()).thenReturn(true);
        when(mail.status()).thenReturn(Map.of("queue",1));when(stats.overview(7)).thenReturn(Map.of("totalRequests",1));
        service=new DashboardInsightsService(db,rules,stats,schedules,mail,joins);
    }
    @Test void countsRegisteredAccountsGenderAndMembersWithoutRoleDuplicationOrDeletedUsers(){
        var result=service.overview(true);var users=(Map<?,?>)result.get("users");
        assertEquals(3,((Number)users.get("total")).intValue());assertEquals(1,((Number)users.get("male")).intValue());assertEquals(1,((Number)users.get("female")).intValue());assertEquals(1,((Number)users.get("unknown")).intValue());
        assertEquals(2L,result.get("memberAccounts"));assertEquals(14,((List<?>)result.get("registrationTrend")).size());
        var recent=(List<Map<String,Object>>)result.get("recentUsers");assertEquals(3,recent.size());assertTrue(recent.stream().allMatch(row->row.containsKey("qq")&&!row.containsKey("password")));
        var log=(Map<?,?>)result.get("logs");assertEquals(1,((Number)log.get("requests")).intValue());assertEquals(1,((Number)log.get("failures")).intValue());
        assertEquals(1L,((Map<?,?>)result.get("schedule")).get("openFeedback"));assertTrue(result.containsKey("mail"));assertTrue(result.containsKey("traffic"));
    }
    @Test void ordinaryAdminOverviewNeverQueriesLeaderOnlyMailAndTraffic(){var result=service.overview(false);assertFalse(result.containsKey("mail"));assertFalse(result.containsKey("traffic"));verifyNoInteractions(mail,stats);}
}
