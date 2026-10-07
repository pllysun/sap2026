package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.entity.User;
import com.sap.mapper.UserMapper;
import com.sap.service.mail.MailQueue;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real transactions exercise races at quota boundaries, reset/history separation and ticket replay. */
class AppDownloadServiceTest {
    AppDownloadStore store;
    AppDownloadService service;
    MailQueue mail;
    UserMapper users;
    final AppDownloadService.Package pack = new AppDownloadService.Package(118, "a".repeat(64), 123, "apk/sap-123.apk", Path.of("/unused"));
    @BeforeEach void setup() {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        store = new AppDownloadStore(new JdbcTemplate(source), new DataSourceTransactionManager(source));
        ReflectionTestUtils.setField(store, "clock", Clock.fixed(Instant.parse("2026-10-04T03:00:00Z"), ZoneOffset.UTC));
        store.initialize();
        var versions = mock(AppVersionService.class); var version = new com.sap.vo.AppVersionVO(); version.setVersionCode(pack.version()); version.setSha256(pack.sha()); when(versions.getPublished()).thenReturn(version);
        mail = mock(MailQueue.class); users = mock(UserMapper.class);
        when(users.selectById(anyLong())).thenAnswer(call -> { var u = new User(); u.setId(call.getArgument(0)); u.setStatus(1); return u; });
        service = new AppDownloadService(store, versions, mock(CosService.class), users, mail, mock(TrafficService.class), "/tmp/sap-download-test");
        ReflectionTestUtils.setField(service, "ready", pack);
    }
    @AfterEach void cleanup() { service.stop(); store.db.execute("SHUTDOWN"); }
    MockHttpServletRequest request() { var r = new MockHttpServletRequest("GET", "/api/app/download/file"); r.setRemoteAddr("127.0.0.1"); return r; }
    String token(long user) { String url = service.issue(user, request()).downloadUrl(); return url.substring(url.indexOf("ticket=") + 7); }
    @Test void countOnlyRedeemedTokensAndRejectReplayExpiryIpAndDisabledAccounts() {
        String t = token(1); assertEquals(0L, store.view(30).get("todayTotal"));
        assertEquals("COS", service.redeem(t, request(), pack).mode());
        assertEquals(410, assertThrows(BusinessException.class, () -> service.redeem(t, request(), pack)).getCode());
        String badIp = token(2); var other = request(); other.setRemoteAddr("192.0.2.2");
        assertEquals(403, assertThrows(BusinessException.class, () -> service.redeem(badIp, other, pack)).getCode());
        String disabled = token(3); var user = new User(); user.setStatus(0); when(users.selectById(3L)).thenReturn(user);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.redeem(disabled, request(), pack)).getCode());
        String expired = token(4); store.db.update("UPDATE sys_app_download_ticket SET expires_at=? WHERE token_hash=?", store.now(), AppDownloadService.hash(expired));
        assertEquals(410, assertThrows(BusinessException.class, () -> service.redeem(expired, request(), pack)).getCode());
        assertEquals(1L, store.view(30).get("todayTotal")); verifyNoInteractions(mail);
    }
    @Test void thresholdsAreStrictAndGlobalAndMailSentOnce() {
        for (long i = 1; i <= 55; i++) {
            var result = service.redeem(token(i), request(), pack);
            assertEquals(i <= 50 ? "COS" : "SERVER", result.mode());
        }
        verify(mail, times(1)).enqueueDownloadAlert(eq("1125887000@qq.com"), anyString(), eq("2026-10-04"), eq(21L), eq(20));
        var day = store.db.queryForMap("SELECT * FROM sys_app_download_day");
        assertEquals(55L, day.get("total_count")); assertEquals(50L, day.get("cos_count")); assertEquals(5L, day.get("server_count"));
        assertEquals("SERVER", store.view(30).get("mode")); assertEquals(true, store.view(30).get("autoSwitched"));
    }
    @Test void concurrentDownloadsCannotOvershootCosThresholdOrSpendOneTicketTwice() throws Exception {
        store.db.update("UPDATE sys_app_download_policy SET guard_count=49,alerted=TRUE WHERE id=1");
        var tokens = new ArrayList<String>(); for (long i = 1; i <= 12; i++) tokens.add(token(i));
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(12);
        try {
            var futures = tokens.stream().map(t -> pool.submit(() -> { start.await(); return service.redeem(t, request(), pack).mode(); })).toList(); start.countDown();
            int cos = 0; for (var f : futures) if ("COS".equals(f.get(10, TimeUnit.SECONDS))) cos++;
            assertEquals(1, cos); assertEquals(61L, store.view(30).get("guardCount"));
            assertEquals(12L, store.view(30).get("todayTotal"));
            String shared = token(99);
            var sameTicket = java.util.stream.IntStream.range(0, 8).mapToObj(i -> pool.submit(() -> {
                try { service.redeem(shared, request(), pack); return 200; } catch (BusinessException e) { return e.getCode(); }
            })).toList();
            int successes = 0;
            for (var f : sameTicket) { int code = f.get(10, TimeUnit.SECONDS); if (code == 200) successes++; else assertEquals(410, code); }
            assertEquals(1, successes); assertEquals(13L, store.view(30).get("todayTotal"));
        } finally { pool.shutdownNow(); }
    }
    @Test void manualSwitchResetsGuardInvalidatesTicketsButPreservesDailyHistoryAndSurvivesRestart() {
        service.redeem(token(1), request(), pack); String stale = token(2);
        var first = store.view(30); store.changeMode("SERVER", first.get("revision").toString());
        assertEquals(0L, store.view(30).get("guardCount")); assertEquals(1L, store.view(30).get("todayTotal"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.redeem(stale, request(), pack)).getCode());
        store.initialize(); assertEquals("SERVER", store.view(30).get("mode"));
        ReflectionTestUtils.setField(store, "clock", Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneOffset.UTC));
        assertEquals("SERVER", service.redeem(token(3), request(), pack).mode());
        assertEquals(2L, store.db.queryForObject("SELECT SUM(total_count) FROM sys_app_download_day", Long.class));
        var current = store.view(30); store.changeMode("COS", current.get("revision").toString()); assertEquals(0L, store.view(30).get("guardCount"));
        assertEquals("COS", service.redeem(token(4), request(), pack).mode());
    }
    @Test void settingsValidateAndStaleSavesCannotUndoAutomaticSwitch() {
        var p = store.view(30); var revision = p.get("revision").toString();
        assertThrows(BusinessException.class, () -> store.settings(new AppDownloadStore.Settings(revision, "bad", 20, 50)));
        assertThrows(BusinessException.class, () -> store.settings(new AppDownloadStore.Settings(revision, "ok@example.com", 50, 20)));
        service.redeem(token(1), request(), pack);
        assertEquals(409, assertThrows(BusinessException.class, () -> store.changeMode("SERVER", revision)).getCode());
        for (int i = 0; i < 5; i++) token(2);
        assertEquals(429, assertThrows(BusinessException.class, () -> token(2)).getCode());
    }
    @Test void wrongVersionAndHeadDoNotSpendTicketOrCount() {
        String t = token(1);
        var changed = new AppDownloadService.Package(119, "b".repeat(64), 123, "apk/sap-456.apk", Path.of("/unused"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.redeem(t, request(), changed)).getCode());
        var head = request(); head.setMethod("HEAD"); assertEquals(405, assertThrows(BusinessException.class, () -> service.download(t, head, new MockHttpServletResponse())).getCode());
        assertEquals(0L, store.view(30).get("todayTotal"));
        assertEquals("COS", service.redeem(t, request(), pack).mode());
    }
}
