package com.sap.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LogAnalyticsServiceTest {
    @Test void resultCodeMappingMatchesJdbcAndMybatisUnderStandardNaming() throws Exception {
        var field=com.sap.entity.SysLog.class.getDeclaredField("resultCode");
        assertEquals("result_code",field.getAnnotation(jakarta.persistence.Column.class).name());
    }
    @Test void supportsBothMysqlAndH2TimestampRepresentations() {
        var date=LocalDateTime.of(2026,6,1,8,15);
        assertEquals(date,LogAnalyticsService.sqlTime(date));
        assertEquals(date,LogAnalyticsService.sqlTime(java.sql.Timestamp.valueOf(date)));
        assertThrows(IllegalArgumentException.class,()->LogAnalyticsService.sqlTime(null));
    }
    JdbcTemplate jdbc; LogAnalyticsService service;
    LocalDateTime now=LocalDateTime.now().withNano(0), old=now.minusDays(100).withMinute(0).withSecond(0);
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);service=new LogAnalyticsService(jdbc,new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE sys_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,request_time TIMESTAMP,path VARCHAR(255),endpoint VARCHAR(255),source VARCHAR(8),user_id BIGINT,user_name VARCHAR(50),http_method VARCHAR(10),ip VARCHAR(50),operation_type VARCHAR(10),description VARCHAR(200),duration BIGINT,result_code INT)");
        jdbc.execute("CREATE TABLE log_stats(stat_date DATE,count BIGINT)");
        jdbc.update("INSERT INTO log_stats VALUES (?,123)",old.toLocalDate());
        service.ensureSchema();
    }
    void insert(LocalDateTime date,long user,int code,long duration) {
        jdbc.update("INSERT INTO sys_log(request_time,path,endpoint,source,user_id,user_name,http_method,ip,operation_type,description,duration,result_code) VALUES (?,'/api/app/feedback/1','/api/app/feedback/{id}','APP',?,'测试用户','GET','127.0.0.1','查询','查看反馈',?,?)",date,user,duration,code);
    }
    @Test void archiveIsIdempotentPreservesDimensionsAndHeatmap() {
        insert(old,1,200,10);insert(old.plusMinutes(1),1,200,30);insert(old,2,500,50);insert(now,1,200,10);
        assertEquals(3,service.archiveBatch(now));assertEquals(0,service.archiveBatch(now));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_log",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_log_archive",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT SUM(call_count) FROM sys_log_archive",Integer.class));
        assertEquals(123,jdbc.queryForObject("SELECT SUM(count) FROM log_stats",Integer.class));
        var groups=service.query("endpoint",true,"APP",null,null,null,null,null,1,20);
        var row=((List<Map<String,Object>>)groups.get("records")).getFirst();
        assertEquals(3,((Number)row.get("call_count")).intValue());
        assertEquals(2,((Number)row.get("user_count")).intValue());
        assertEquals(1,((Number)row.get("failure_count")).intValue());
        assertEquals(90,((Number)row.get("duration_sum")).intValue());
        assertEquals(1L,service.query("user",true,"APP",1L,null,null,null,null,1,20).get("total"));
        assertEquals(0L,service.query("detail",true,"WEB",null,null,null,null,null,1,20).get("total"));
    }
    @Test void detailAlwaysRestrictsToNinetyDaysAndPagesBothDimensions() {
        insert(old,1,200,10);insert(now,1,200,10);insert(now,2,200,10);
        var first=service.query("detail",false,null,null,null,null,old.toLocalDate(),null,1,1);
        assertEquals(2L,first.get("total"));assertEquals(1,((List<?>)first.get("records")).size());
        var next=service.query("detail",false,null,null,null,null,null,null,2,1);
        assertNotEquals(((List<?>)first.get("records")).getFirst(),((List<?>)next.get("records")).getFirst());
        assertEquals(2L,service.query("user",false,null,null,null,null,null,null,1,1).get("total"));
        assertEquals(1L,service.query("endpoint",false,null,null,null,null,null,null,1,1).get("total"));
    }
    @Test void failedArchiveRollsBackWithoutDeletingAnyDetail() {
        insert(old,1,200,10);
        jdbc.execute("ALTER TABLE sys_log_archive ADD CONSTRAINT reject_archive CHECK (call_count < 1)");
        assertThrows(Exception.class,()->service.archiveBatch(now));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_log",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_log_archive",Integer.class));
    }
    @Test void lateArrivingOldRowsMergeAndUnknownResultIsNotSuccess() {
        insert(old,1,200,10);service.archiveBatch(now);insert(old.plusMinutes(2),1,200,20);service.archiveBatch(now);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_log_archive",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT SUM(call_count) FROM sys_log_archive",Integer.class));
        jdbc.update("INSERT INTO sys_log(request_time,path,duration) VALUES (?,'/api/jw/schedule/123',5)",old);
        service.archiveBatch(now);
        assertEquals("/api/jw/schedule/{id}",jdbc.queryForObject("SELECT endpoint FROM sys_log_archive WHERE result_code=0",String.class));
    }
    @Test void classificationDoesNotTreatAdminMaintenanceAsApp() {
        assertEquals("WEB",LogAnalyticsService.classify("/api/app/feedback/admin/issues",null));
        assertEquals("APP",LogAnalyticsService.classify("/api/jw/schedule",null));
        assertEquals("APP",LogAnalyticsService.classify("/api/auth/login","app"));
        assertEquals("WEB",LogAnalyticsService.classify("/api/users",null));
        assertEquals("/api/app/feedback/{id}",LogAnalyticsService.normalizeEndpoint("/api/app/feedback/123?token=secret"));
    }
    @Test void concurrentWorkersDoNotDuplicateOrLoseRows() throws Exception {
        for(int i=0;i<1001;i++) insert(old,1,200,1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.archiveBatch(now));
            var b=pool.submit(()->service.archiveBatch(now));
            assertEquals(1001,a.get(30,java.util.concurrent.TimeUnit.SECONDS)+b.get(30,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1001,jdbc.queryForObject("SELECT SUM(call_count) FROM sys_log_archive",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_log",Integer.class));
    }
}
