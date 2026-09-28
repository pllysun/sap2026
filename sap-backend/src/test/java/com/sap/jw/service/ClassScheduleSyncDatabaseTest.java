package com.sap.jw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.jw.client.JwHttpSession;
import com.sap.mapper.UserMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassScheduleSyncDatabaseTest {
    @Test void actualFourTableQueryDetectsChangesInRelatedTeacherAndRoom() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        var calendar = mock(JwCalendarService.class);
        when(calendar.storedDates()).thenReturn(Map.of("2026-2027-1", "2026-09-07"));
        when(calendar.getSemesterStart((JwHttpSession) null, "2026-2027-1")).thenReturn("2026-09-07");
        var schedules = new ClassScheduleService(jdbc, mock(JwSessionManager.class), mock(JwCredentialService.class),
            mock(UserMapper.class), calendar, new DataSourceTransactionManager(ds));
        schedules.ensureSchema();
        jdbc.update("INSERT INTO jw_class_schedule_term(term_value,term_label) VALUES ('2026-2027-1','2026-2027-1')");
        for (String table : List.of("class", "teacher", "room", "course")) {
            jdbc.update("INSERT INTO jw_class_schedule_" + table + " (term_value,source_key,external_id,weekday,section,week_range,college,grade,major,class_name) " +
                "VALUES ('2026-2027-1', 'key', 'lesson-1','星期一','1-2','1-16','学院','2025','专业','一班')");
        }
        jdbc.update("UPDATE jw_class_schedule_course SET course_name='软件工程'");
        jdbc.update("UPDATE jw_class_schedule_teacher SET teacher_name='旧教师'");
        jdbc.update("UPDATE jw_class_schedule_room SET room_name='旧教室'");
        var sync = new ClassScheduleSyncService(schedules, new ObjectMapper());
        var request = new ClassScheduleSyncService.Selection("key", "2026-2027-1", "学院", "2025", "专业", "一班", null);
        var first = sync.sync(new ClassScheduleSyncService.Request(List.of(request), false)).items().getFirst();
        @SuppressWarnings("unchecked") var course = ((List<Map<String,Object>>) first.data().get("courses")).getFirst();
        assertEquals("软件工程", course.get("name"));
        assertEquals("旧教师", course.get("teacher"));
        assertEquals("旧教室", course.get("room"));
        assertEquals(1, course.get("day"));
        var known = new ClassScheduleSyncService.Selection("key", "2026-2027-1", "学院", "2025", "专业", "一班", first.revision());
        assertFalse(sync.sync(new ClassScheduleSyncService.Request(List.of(known), false)).changed());
        jdbc.update("UPDATE jw_class_schedule_teacher SET teacher_name='新教师'");
        jdbc.update("UPDATE jw_class_schedule_room SET room_name='新教室'");
        assertTrue(sync.sync(new ClassScheduleSyncService.Request(List.of(known), false)).changed());
    }
}
