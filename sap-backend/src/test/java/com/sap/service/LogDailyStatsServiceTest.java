package com.sap.service;

import com.sap.entity.SysLog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogDailyStatsServiceTest {
    JdbcTemplate db;DataSourceTransactionManager manager;ApiLogExclusionService rules;LogAnalyticsService analytics;LogDailyStatsService daily;
    LocalDateTime old=LocalDateTime.now().minusDays(100),today=LocalDateTime.now().minusSeconds(1);
    @BeforeEach void setup(){
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);rules=new ApiLogExclusionService(db,manager);rules.initialize();
        db.execute("CREATE TABLE sys_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,request_time TIMESTAMP,path VARCHAR(255),endpoint VARCHAR(255),source VARCHAR(8),user_id BIGINT,user_name VARCHAR(50),http_method VARCHAR(10),ip VARCHAR(50),operation_type VARCHAR(10),description VARCHAR(200),duration BIGINT,result_code INT)");
        analytics=new LogAnalyticsService(db,manager);ReflectionTestUtils.setField(analytics,"exclusions",rules);analytics.ensureSchema();
        add(old,"/api/ping","GET");add(old,"/api/oj-nodes/{id}/heartbeat","POST");add(old,"/api/auth/info","GET");analytics.archiveBatch(LocalDateTime.now());
        add(today,"/api/ping","GET");add(today,"/api/oj-nodes/{id}/heartbeat","POST");add(today,"/api/auth/info","GET");
        daily=new LogDailyStatsService(db,manager,rules);daily.initialize();
    }
    void add(LocalDateTime date,String endpoint,String method){db.update("INSERT INTO sys_log(request_time,path,endpoint,source,http_method,operation_type,duration,result_code) VALUES(?,?,?,'WEB',?,'查询',1,200)",date,endpoint,endpoint,method);}
    @Test void migrationIncludesArchiveAndDetailOnceAndFiltersEveryRead(){
        assertEquals(2,daily.total());daily.initialize();assertEquals(2,daily.total());
        assertEquals(6L,db.queryForObject("SELECT SUM(call_count) FROM sys_log_daily_endpoint",Long.class));
        var recent=analytics.query("endpoint",false,null,null,null,null,null,null,1,20);assertEquals(1L,recent.get("total"));
        assertEquals(1L,analytics.query("endpoint",true,null,null,null,null,null,null,1,20).get("total"));
        var logs=new SysLogService(mock(com.sap.mapper.SysLogMapper.class),daily,rules);
        assertEquals(1L,logs.getStats(7).get("total"));
        rules.replace(List.of());assertEquals(6,daily.total());assertEquals(3L,logs.getStats(7).get("total"));
        assertEquals(3L,analytics.query("endpoint",false,null,null,null,null,null,null,1,20).get("total"));
    }
    @Test void recordingAndReenablingChangeOnlyFutureRequestsWithoutErasingHistory(){
        var log=new SysLog();log.setEndpoint("/api/ping");log.setHttpMethod("GET");log.setRequestTime(today);log.setOperationType("查询");
        daily.record(log);assertEquals(6L,db.queryForObject("SELECT SUM(call_count) FROM sys_log_daily_endpoint",Long.class));
        rules.replace(List.of());daily.record(log);assertEquals(7,daily.total());
        rules.replace(List.of(new ApiLogExclusionService.Rule("/api/ping","GET")));assertEquals(4,daily.total());
    }
}
