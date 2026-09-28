package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.mapper.UserMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ClassScheduleProgressTest {
    JdbcTemplate jdbc;
    ClassScheduleService service;
    final String batch = UUID.randomUUID().toString();

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        service = new ClassScheduleService(jdbc, mock(JwSessionManager.class), mock(JwCredentialService.class),
                mock(UserMapper.class), mock(JwCalendarService.class), new DataSourceTransactionManager(ds));
        service.ensureSchema();
    }

    void event(String source, String status, int rows) {
        jdbc.update("INSERT INTO jw_class_schedule_pull_log(batch_id,term_value,source_type,trigger_type,actor_name,status,message,row_count) " +
                "VALUES (?,?,?,?,?,?,?,?)", batch, "2026-2027-1", source, "MANUAL", "测试维护者", status, "测试过程", rows);
    }

    @Test void completedBatchShowsOnlyLatestSourceResultsAndHistoryRemainsPageable() {
        event("all", "RUNNING", 0);
        event("class", "RUNNING", 100);
        event("class", "SUCCESS", 120);
        event("teacher", "RUNNING", 40);
        event("teacher", "SUCCESS", 40);
        event("all", "SUCCESS", 160);
        var progress = service.progress(batch);
        assertEquals("SUCCESS", progress.get("status"));
        assertEquals(160, progress.get("totalRows"));
        assertEquals(Map.of("2026-2027-1:class", 120, "2026-2027-1:teacher", 40), progress.get("counts"));
        var sources = (List<Map<String,Object>>) progress.get("sources");
        assertEquals(2, sources.size());
        assertTrue(sources.stream().allMatch(row -> row.get("status").equals("SUCCESS")));
        assertEquals(6L, service.history(batch, 1, 2).get("total"));
        assertEquals(2, ((List<?>) service.history(batch, 2, 2).get("records")).size());
        var page = service.batches(1, 10, "SUCCESS", "维护者");
        assertEquals(1L, page.get("total"));
        assertEquals(0L, service.batches(1, 10, "RUNNING", null).get("total"));
    }

    @Test void failureRetainsParsedPagesAndCountsAndDoesNotReplaceDatabaseRows() {
        event("all", "RUNNING", 0);
        event("class", "RUNNING", 500);
        jdbc.update("UPDATE jw_class_schedule_pull_log SET completed_pages=2,total_pages=5,fetched_rows=520,reported_rows=1300 WHERE source_type='class'");
        event("class", "FAILED", 0);
        event("all", "FAILED", 0);
        var progress = service.progress(batch);
        var source = ((List<Map<String,Object>>) progress.get("sources")).getFirst();
        assertEquals(500, progress.get("totalRows"));
        assertEquals(2, source.get("completedPages"));
        assertEquals(5, source.get("totalPages"));
        assertEquals("FAILED", source.get("status"));
    }

    @Test void latestOverallStateSupersedesEarlierSmsWaitAndRestartsMarkOrphansFailed() {
        event("all", "PENDING", 0);
        event("all", "RUNNING", 0);
        assertEquals("RUNNING", service.progress(batch).get("status"));
        service.recoverInterruptedPulls();
        assertEquals("FAILED", service.progress(batch).get("status"));
    }

    @Test void batchPaginationNeverTruncatesFinalStateByGlobalLogLimit() {
        for (int i = 0; i < 220; i++) event("class", "RUNNING", i);
        event("class", "SUCCESS", 219);
        event("all", "SUCCESS", 219);
        assertEquals(1L, service.batches(1, 10, "ALL", null).get("total"));
        assertEquals(222L, service.progress(batch).get("eventCount"));
        assertEquals(219, service.progress(batch).get("totalRows"));
    }

    @Test void rejectsMalformedAndReusedBatch() {
        assertThrows(BusinessException.class, () -> service.progress("../other"));
        service.queuePull(batch, null, 10L);
        assertThrows(BusinessException.class, () -> service.queuePull(batch, null, 10L));
    }

    @Test void historicalNonUuidBatchCanBeListedAndReadButCannotBeSubmittedAsNewTask() {
        String legacy = "mu93pn3g-5524e471";
        event("class", "RUNNING", 100);
        event("class", "SUCCESS", 120);
        event("all", "SUCCESS", 120);
        jdbc.update("UPDATE jw_class_schedule_pull_log SET batch_id=? WHERE batch_id=?", legacy, batch);
        var list = service.batches(1, 10, "SUCCESS", null);
        assertEquals(1L, list.get("total"));
        assertEquals("SUCCESS", service.progress(legacy).get("status"));
        assertEquals(3L, service.history(legacy, 1, 2).get("total"));
        assertEquals(1, ((List<?>) service.progress(legacy).get("sources")).size());
        assertThrows(BusinessException.class, () -> service.queuePull(legacy, null, 10L));
    }

    @Test void historicalInterruptedBatchWithoutOverallEventIsRecoveredOnce() {
        event(null, "RUNNING", 0);
        event("class", "RUNNING", 100);
        event("teacher", "RUNNING", 20);
        assertEquals("RUNNING", service.progress(batch).get("status"));
        service.recoverInterruptedPulls();
        assertEquals("FAILED", service.progress(batch).get("status"));
        assertEquals(120, service.progress(batch).get("totalRows"));
        assertEquals(1L, service.batches(1, 10, "FAILED", null).get("total"));
        service.recoverInterruptedPulls();
        assertEquals(4L, service.history(batch, 1, 10).get("total"), "重启恢复不应重复添加终态");
    }

    @Test void batchesOrderByStartDateEvenWhenOldTaskGetsNewRecoveryEvent() {
        event("all", "RUNNING", 0);
        jdbc.update("UPDATE jw_class_schedule_pull_log SET started_at='2026-08-01 09:00:00' WHERE batch_id=?", batch);
        String recent = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO jw_class_schedule_pull_log(batch_id,source_type,trigger_type,status,started_at) VALUES (?,'all','MANUAL','SUCCESS','2026-09-29 09:00:00')", recent);
        service.recordTaskState(batch, "FAILED", "旧任务恢复");
        var first = (List<Map<String,Object>>) service.batches(1, 1, "ALL", null).get("records");
        var second = (List<Map<String,Object>>) service.batches(2, 1, "ALL", null).get("records");
        assertEquals(recent, first.getFirst().get("batchId"));
        assertEquals(batch, second.getFirst().get("batchId"));
    }

    @Test void historyUsesEventDateWithIdAsTieBreaker() {
        event("class", "RUNNING", 10);
        jdbc.update("UPDATE jw_class_schedule_pull_log SET started_at='2026-09-29 10:00:00' WHERE batch_id=?", batch);
        event("class", "SUCCESS", 20);
        jdbc.update("UPDATE jw_class_schedule_pull_log SET started_at='2026-09-28 10:00:00' WHERE status='SUCCESS'");
        var rows = (List<Map<String,Object>>) service.history(batch, 1, 10).get("records");
        assertEquals("RUNNING", rows.getFirst().get("status"));
        assertEquals("SUCCESS", rows.getLast().get("status"));
    }
}
