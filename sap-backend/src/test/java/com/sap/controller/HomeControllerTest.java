package com.sap.controller;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sap.mapper.HomeOverviewMapper;
import com.sap.mapper.NoteMapper;
import com.sap.service.ActivityService;
import com.sap.service.StudyService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeControllerTest {
    @Test void overviewCountsDistinctMembersNotRegistrationsOrTermRows() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY, grade VARCHAR(10), status INT, deleted INT)");
        db.execute("CREATE TABLE sys_user_role(user_id BIGINT, role_code INT)");
        db.execute("CREATE TABLE sys_term(user_id BIGINT, grade VARCHAR(10), deleted INT)");
        db.execute("INSERT INTO sys_term VALUES(1,'2025',0),(1,'2025',0),(3,'2025',0),(1,'2026',0),(6,'2026',1)");
        db.execute("INSERT INTO sys_user VALUES(1,'2025',1,0),(2,'2025',1,0),(3,'2026',1,0),(4,'2025',0,0),(5,'2025',1,1),(6,NULL,1,0)");
        db.execute("INSERT INTO sys_user_role VALUES(1,3),(1,2),(1,4),(2,4),(3,1),(4,3),(5,3),(6,3)");
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
        var sessions = factory.getObject(); sessions.getConfiguration().addMapper(HomeOverviewMapper.class);
        var mapper = new SqlSessionTemplate(sessions).getMapper(HomeOverviewMapper.class);
        assertEquals(5, mapper.registeredCount());
        assertEquals(3, mapper.memberCount());
        var grades = mapper.membersByGrade();
        assertEquals(2, grades.size());
        assertEquals("2025", grades.get(0).get("grade"));
        assertEquals(1L, ((Number) grades.get(0).get("count")).longValue());
        var archive = mapper.memberArchiveByTerm();
        assertEquals(2, archive.size());
        assertEquals(2L, ((Number) archive.get(0).get("count")).longValue());
        assertEquals(1L, ((Number) archive.get(1).get("count")).longValue());
        var activities = mock(ActivityService.class); var study = mock(StudyService.class); var notes = mock(NoteMapper.class);
        when(activities.countActivities()).thenReturn(8L); when(study.countStudyActivities()).thenReturn(4L); when(notes.selectCount(null)).thenReturn(2L);
        var result = new HomeController(mapper, activities, study, notes).overview();
        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(3L, data.get("memberCount")); assertEquals(5L, data.get("registeredCount"));
        assertFalse(data.containsKey("financeStats")); assertFalse(data.containsKey("users"));
        // 展示接口不再继承管理端的角色限制，仍受全局登录鉴权保护。
        assertNull(HomeController.class.getAnnotation(cn.dev33.satoken.annotation.SaCheckRole.class));
        db.execute("SHUTDOWN");
    }
}
