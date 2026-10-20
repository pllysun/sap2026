package com.sap.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.sap.mapper.ApiRequestStatMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiStatisticsFilteringTest {
    JdbcTemplate db;SqlSession session;ApiRequestStatMapper mapper;ApiLogExclusionService rules;LocalDate date=LocalDate.now();
    @BeforeEach void setup(){
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");db=new JdbcTemplate(ds);
        db.execute("CREATE TABLE stat_api_request(id BIGINT AUTO_INCREMENT PRIMARY KEY,stat_date DATE,user_id BIGINT,user_name VARCHAR(50),endpoint VARCHAR(191),http_method VARCHAR(10),count INT,UNIQUE(stat_date,user_id,endpoint,http_method))");
        rules=new ApiLogExclusionService(db,new DataSourceTransactionManager(ds));rules.initialize();
        db.update("INSERT INTO stat_api_request(stat_date,user_id,user_name,endpoint,http_method,count) VALUES(?,1,'A','/api/auth/info','GET',10)",date);
        db.update("INSERT INTO stat_api_request(stat_date,user_id,user_name,endpoint,http_method,count) VALUES(?,2,'B','/api/oj-nodes/{id}/heartbeat','POST',300)",date);
        db.update("INSERT INTO stat_api_request(stat_date,user_id,user_name,endpoint,http_method,count) VALUES(?,0,'匿名','/api/ping','GET',200)",date);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("filter",new JdbcTransactionFactory(),ds));config.addMapper(ApiRequestStatMapper.class);config.addMapper(com.sap.mapper.TermMapper.class);
        session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);mapper=session.getMapper(ApiRequestStatMapper.class);
    }
    ApiLogExclusionService.Sql filter(){return rules.sql("endpoint","http_method");}
    @AfterEach void close(){if(session!=null)session.close();}
    @Test void allTrafficViewsExcludeHistoricalProbeCountsAndProbeOnlyUsers(){
        assertEquals(10,mapper.totalRequests(date,filter()));assertEquals(1,mapper.activeUsers(date,filter()));
        assertEquals(1,mapper.topEndpoints(date,20,filter()).size());assertEquals(1,mapper.trend(date,filter()).size());
        assertTrue(mapper.byUser(date,2L,filter()).isEmpty());assertTrue(mapper.byEndpoint(date,"/api/ping",filter()).isEmpty());
        assertEquals(1,mapper.detail(date,100,filter()).size());assertEquals(1,mapper.distinctUsers(filter()).size());
        rules.replace(List.of());assertEquals(510,mapper.totalRequests(date,filter()));assertEquals(2,mapper.activeUsers(date,filter()));assertEquals(3,mapper.topEndpoints(date,20,filter()).size());
    }
    @Test void membershipCountsDeduplicateMultiplePositionsAndSkipDeletedUsers() {
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,deleted INT)");db.execute("CREATE TABLE sys_term(id BIGINT,user_id BIGINT,grade VARCHAR(10),deleted INT)");
        db.update("INSERT INTO sys_user VALUES(1,0),(2,0),(3,1)");
        db.update("INSERT INTO sys_term VALUES(1,1,'2025',0),(2,1,'2025',0),(3,1,'2026',0),(4,2,'2026',0),(5,3,'2026',0),(6,2,'2025',1)");
        var terms=session.getMapper(com.sap.mapper.TermMapper.class);
        assertEquals(2,terms.countMembers(null));assertEquals(1,terms.countMembers("2025"));assertEquals(2,terms.countMembers("2026"));
        assertEquals(2,terms.countsByGrade().size());
    }
    @Test void methodSpecificRuleBindsValuesAndOnlySuppressesSelectedMethod(){
        rules.replace(List.of(new ApiLogExclusionService.Rule("/api/auth/info","POST")));assertEquals(510,mapper.totalRequests(date,filter()));
        rules.replace(List.of(new ApiLogExclusionService.Rule("/api/auth/info","GET")));assertEquals(500,mapper.totalRequests(date,filter()));
    }
}
