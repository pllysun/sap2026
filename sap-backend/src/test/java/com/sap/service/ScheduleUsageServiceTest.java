package com.sap.service;

import com.sap.common.BusinessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleUsageServiceTest {
    JdbcTemplate db;
    ScheduleUsageService service;
    LocalDate today = LocalDate.now();

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(ds);
        service = new ScheduleUsageService(db);
        service.initialize();
        db.execute("CREATE TABLE sys_log(request_time DATETIME,user_id BIGINT,endpoint VARCHAR(255),http_method VARCHAR(10),result_code INT)");
        db.execute("CREATE TABLE sys_log_archive(bucket_time DATETIME,user_id BIGINT,endpoint VARCHAR(255),http_method VARCHAR(10),result_code INT)");
    }

    void report(long user, String mode, String event, LocalDate day) {
        service.report(user, new ScheduleUsageService.Report(List.of(new ScheduleUsageService.Event(day, mode, event))));
    }

    void log(String table, LocalDate day, long user, String endpoint, String method, int code) {
        db.update("INSERT INTO " + table + " VALUES(?,?,?,?,?)", day.atTime(12, 0), user, endpoint, method, code);
    }

    @Test void deduplicatesRetriesDevicesDaysAndModesWithoutAddingDailyTotals() {
        report(1, "JW", "VIEW", today);
        report(1, "JW", "VIEW", today);
        report(1, "CLASS", "VIEW", today);
        report(1, "WEB", "VIEW", today.minusDays(1));
        report(2, "WEB", "VIEW", today);
        var result = service.summary(7);
        assertEquals(2, result.activeUsers());
        assertEquals(1, result.daily().get(5).activeUsers());
        assertEquals(2, result.daily().get(6).activeUsers());
        assertEquals(2, result.modes().get(1).activeUsers());
        assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM stat_schedule_usage", Integer.class));
        assertEquals(2, service.activeUsers(7));
    }

    @Test void countsOnlyVerifiedSuccessAndKeepsBackgroundSyncSeparateFromViews() {
        log("sys_log", today, 1, "/api/jw/schedule", "GET", 500);
        log("sys_log", today, 2, "/api/jw/schedule", "GET", 200);
        log("sys_log", today, 2, "/api/class-schedule/schedule", "GET", 200);
        log("sys_log", today, 3, "/api/class-schedule/sync", "POST", 200);
        log("sys_log", today, 0, "/api/jw/schedule", "GET", 200);
        log("sys_log", today, 4, "/api/jw/terms", "GET", 200);
        log("sys_log", today, 5, "/api/jw/schedule", "POST", 200);
        report(6, "WEB", "SYNC", today);
        var result = service.summary(7);
        assertEquals(0, result.activeUsers());
        assertEquals(3, result.syncUsers());
        assertEquals(1, result.modes().get(0).syncUsers());
        assertEquals(1, result.modes().get(1).syncUsers());
        assertEquals(2, result.modes().get(2).syncUsers());
        assertNull(result.trackingSince());
    }

    @Test void includesArchivedSuccessAndDeduplicatesHotArchiveOverlap() {
        log("sys_log", today.minusDays(6), 1, "/api/jw/schedule", "GET", 200);
        log("sys_log_archive", today.minusDays(6), 1, "/api/jw/schedule", "GET", 200);
        log("sys_log_archive", today.minusDays(89), 2, "/api/class-schedule/schedule", "GET", 200);
        assertEquals(1, service.summary(7).syncUsers());
        assertEquals(2, service.summary(90).syncUsers());
        assertEquals(1, service.summary(7).daily().getFirst().syncUsers());
    }

    @Test void boundsNaturalDayWindowAndFillsEmptyDates() {
        report(1, "WEB", "VIEW", today.minusDays(6));
        report(2, "WEB", "VIEW", today.minusDays(7));
        log("sys_log", today.plusDays(1), 3, "/api/jw/schedule", "GET", 200);
        var result = service.summary(7);
        assertEquals(1, result.activeUsers());
        assertEquals(0, result.syncUsers());
        assertEquals(7, result.daily().size());
        assertEquals(today.minusDays(6), result.daily().getFirst().date());
        assertEquals(0, result.daily().get(1).activeUsers());
        assertEquals(2, service.summary(30).activeUsers());
    }

    @Test void validatesWholeBatchBeforeWritingAndRejectsForgedServerSync() {
        var valid = new ScheduleUsageService.Event(today, "WEB", "VIEW");
        for (var bad : Arrays.asList(null,
                new ScheduleUsageService.Event(today, null, "VIEW"),
                new ScheduleUsageService.Event(today, "CLASS", "SYNC"),
                new ScheduleUsageService.Event(today, "JW", "SYNC"),
                new ScheduleUsageService.Event(today.plusDays(1), "WEB", "VIEW"),
                new ScheduleUsageService.Event(today.minusDays(90), "WEB", "VIEW"))) {
            assertThrows(BusinessException.class, () -> service.report(1,
                    new ScheduleUsageService.Report(Arrays.asList(valid, bad))));
        }
        assertThrows(BusinessException.class, () -> service.report(0, new ScheduleUsageService.Report(List.of(valid))));
        assertThrows(BusinessException.class, () -> service.report(1, new ScheduleUsageService.Report(Collections.nCopies(121, valid))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM stat_schedule_usage", Integer.class));
    }
}
