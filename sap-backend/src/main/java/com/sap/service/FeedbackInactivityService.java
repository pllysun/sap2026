package com.sap.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;

/** 与所有人工回复/状态操作锁定同一 Issue 行，防止新回复与自动关闭交错。 */
@Service
@Slf4j
public class FeedbackInactivityService {
    public static final String MESSAGE = "该问题在维护者回复后已超过 7 天没有新的回复，系统已自动关闭。如仍有问题，可新建 Issue，或联系维护者重新打开该 Issue。";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public FeedbackInactivityService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void scheduledClose() {
        try { closeInactive(LocalDateTime.now()); }
        catch (Exception e) { log.error("自动关闭过期反馈失败，将在下轮重试", e); }
    }
    public int closeInactive(LocalDateTime now) {
        var cutoff = now.minusDays(7);
        var ids = jdbc.queryForList("""
            SELECT i.id FROM app_feedback_issue i WHERE i.deleted=0 AND i.status='OPEN'
            AND i.updated_at < ?
            AND EXISTS (SELECT 1 FROM app_feedback_comment c WHERE c.issue_id=i.id AND c.deleted=0 AND c.admin_reply=1)
            AND NOT EXISTS (SELECT 1 FROM app_feedback_comment c WHERE c.issue_id=i.id AND c.deleted=0 AND c.created_at >= ?)
            ORDER BY i.updated_at,i.id LIMIT 200
            """, Long.class, cutoff, cutoff);
        int count = 0;
        for (Long id : ids) {
            Boolean closed = tx.execute(status -> {
                var rows = jdbc.queryForList("SELECT status, updated_at FROM app_feedback_issue WHERE id=? AND deleted=0 FOR UPDATE", id);
                if (rows.isEmpty() || !"OPEN".equals(rows.get(0).get("status"))) return false;
                var changed = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM app_feedback_issue i WHERE i.id=? AND i.updated_at < ?
                    AND EXISTS (SELECT 1 FROM app_feedback_comment c WHERE c.issue_id=i.id AND c.deleted=0 AND c.admin_reply=1)
                    AND NOT EXISTS (SELECT 1 FROM app_feedback_comment c WHERE c.issue_id=i.id AND c.deleted=0 AND c.created_at >= ?)
                    """, Long.class, id, cutoff, cutoff);
                if (changed == null || changed == 0) return false;
                jdbc.update("INSERT INTO app_feedback_comment(issue_id,author_id,content,admin_reply,created_at,deleted) VALUES (?,0,?,0,?,0)", id, MESSAGE, now);
                jdbc.update("UPDATE app_feedback_issue SET status='CLOSED',closed_by=0,closed_at=?,updated_at=? WHERE id=?", now, now, id);
                return true;
            });
            if (Boolean.TRUE.equals(closed)) count++;
        }
        return count;
    }
}
