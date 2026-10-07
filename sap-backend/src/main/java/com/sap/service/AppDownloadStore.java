package com.sap.service;

import com.sap.common.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Persistent global gate: ticket consumption, thresholds and daily totals commit together. */
@Service
public class AppDownloadStore {
    public final JdbcTemplate db;
    private final TransactionTemplate tx;
    private Clock clock = Clock.systemUTC();
    public AppDownloadStore(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db = db;
        this.tx = new TransactionTemplate(manager);
    }
    @PostConstruct public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS sys_app_download_policy (id INT PRIMARY KEY, mode VARCHAR(16) NOT NULL, alert_email VARCHAR(254) NOT NULL, alert_limit INT NOT NULL, proxy_limit INT NOT NULL, revision VARCHAR(64) NOT NULL, epoch VARCHAR(64) NOT NULL, guard_date DATE NOT NULL, guard_count BIGINT NOT NULL DEFAULT 0, alerted BOOLEAN NOT NULL DEFAULT FALSE, auto_switched BOOLEAN NOT NULL DEFAULT FALSE)");
        db.execute("CREATE TABLE IF NOT EXISTS sys_app_download_day (download_date DATE PRIMARY KEY, total_count BIGINT NOT NULL DEFAULT 0, cos_count BIGINT NOT NULL DEFAULT 0, server_count BIGINT NOT NULL DEFAULT 0, failed_count BIGINT NOT NULL DEFAULT 0, estimated_bytes BIGINT NOT NULL DEFAULT 0)");
        db.execute("CREATE TABLE IF NOT EXISTS sys_app_download_ticket (token_hash VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, ip_hash VARCHAR(64) NOT NULL, version_code INT NOT NULL, sha256 VARCHAR(64) NOT NULL, epoch VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, used_at BIGINT, INDEX idx_app_ticket_expiry(expires_at), INDEX idx_app_ticket_user(user_id,created_at))");
        if (db.queryForObject("SELECT COUNT(*) FROM sys_app_download_policy WHERE id=1", Integer.class) == 0) {
            try {
                db.update("INSERT INTO sys_app_download_policy(id,mode,alert_email,alert_limit,proxy_limit,revision,epoch,guard_date) VALUES (1,'COS','1125887000@qq.com',20,50,?,?,?)", uuid(), uuid(), today());
            } catch (org.springframework.dao.DuplicateKeyException ignored) { /* Concurrent startup. */ }
        }
    }
    public <T> T transaction(Supplier<T> action) { return tx.execute(status -> action.get()); }
    public Map<String, Object> lock() { return db.queryForMap("SELECT * FROM sys_app_download_policy WHERE id=1 FOR UPDATE"); }
    public long now() { return clock.millis(); }
    public LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))); }
    static String uuid() { return UUID.randomUUID().toString(); }
    static long number(Object value) { return ((Number) value).longValue(); }
    static boolean flag(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0; }
    public Map<String, Object> view(int days) {
        var p = db.queryForMap("SELECT * FROM sys_app_download_policy WHERE id=1");
        long count = Objects.toString(p.get("guard_date")).equals(today().toString()) ? number(p.get("guard_count")) : 0;
        var records = db.queryForList("SELECT * FROM sys_app_download_day WHERE download_date>=? ORDER BY download_date DESC", today().minusDays(Math.max(1, Math.min(366, days)) - 1));
        var current = db.queryForList("SELECT * FROM sys_app_download_day WHERE download_date=?", today());
        return Map.of("mode", p.get("mode"), "alertEmail", p.get("alert_email"), "alertLimit", p.get("alert_limit"), "proxyLimit", p.get("proxy_limit"),
                "revision", p.get("revision"), "guardCount", count, "autoSwitched", flag(p.get("auto_switched")), "today", today().toString(),
                "todayTotal", current.isEmpty() ? 0L : current.getFirst().get("total_count"), "records", records);
    }
    public record Settings(String revision, String alertEmail, int alertLimit, int proxyLimit) {}
    public Map<String, Object> settings(Settings update) {
        if (update == null || update.alertEmail() == null || update.alertEmail().length() > 254 || !update.alertEmail().matches("^[^\\s@,;<>]+@[^\\s@,;<>]+\\.[^\\s@,;<>]+$") || update.alertLimit() < 1 || update.proxyLimit() <= update.alertLimit() || update.proxyLimit() > 100000)
            throw new BusinessException(400, "请填写有效邮箱，并设置 1 ≤ 告警阈值 < 服务器下载阈值 ≤ 100000");
        return transaction(() -> {
            var p = lock(); checkRevision(p, update.revision());
            String mode = p.get("mode").toString();
            if (Objects.toString(p.get("guard_date")).equals(today().toString()) && number(p.get("guard_count")) > update.proxyLimit()) mode = "SERVER";
            db.update("UPDATE sys_app_download_policy SET alert_email=?,alert_limit=?,proxy_limit=?,mode=?,auto_switched=?,revision=? WHERE id=1", update.alertEmail().trim(), update.alertLimit(), update.proxyLimit(), mode, "SERVER".equals(mode) && "COS".equals(p.get("mode")) || flag(p.get("auto_switched")), uuid());
            return view(30);
        });
    }
    public Map<String, Object> changeMode(String mode, String revision) {
        if (!Set.of("COS", "SERVER").contains(Objects.toString(mode, ""))) throw new BusinessException(400, "下载方式无效");
        return transaction(() -> {
            var p = lock(); checkRevision(p, revision);
            if (!mode.equals(p.get("mode"))) {
                db.update("UPDATE sys_app_download_policy SET mode=?,revision=?,epoch=?,guard_date=?,guard_count=0,alerted=FALSE,auto_switched=FALSE WHERE id=1", mode, uuid(), uuid(), today());
            }
            return view(30);
        });
    }
    private void checkRevision(Map<String, Object> p, String revision) {
        if (!Objects.equals(p.get("revision"), revision)) throw new BusinessException(409, "设置已发生变化，请刷新后重试");
    }
}
