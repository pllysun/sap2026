package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.client.MfaRequiredException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 管理端只提交任务，单工作线程串行采集；短信会话仅保存在内存，进度持久化到采集日志。 */
@Service
public class ClassScheduleTaskService {
    private final ClassScheduleService schedules;
    private final PendingClassScheduleManager pending;
    private final JwAuthClient auth;
    private final JwSessionManager sessions;
    private final Executor worker;
    private Task active;

    @Autowired
    public ClassScheduleTaskService(ClassScheduleService schedules, PendingClassScheduleManager pending,
                                    JwAuthClient auth, JwSessionManager sessions) {
        this(schedules, pending, auth, sessions, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "class-schedule-collector");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ClassScheduleTaskService(ClassScheduleService schedules, PendingClassScheduleManager pending,
                             JwAuthClient auth, JwSessionManager sessions, Executor worker) {
        this.schedules = schedules; this.pending = pending; this.auth = auth; this.sessions = sessions; this.worker = worker;
    }

    @PostConstruct
    public void recover() { schedules.recoverInterruptedPulls(); }

    public synchronized Map<String, Object> start(Long credentialOwner, Long actorId, String account,
                                                 String password, String term, String requestedBatch) {
        expirePending();
        if (active != null) {
            if (active.batchId.equals(requestedBatch) && active.actorId.equals(actorId)) return accepted(active);
            throw new BusinessException(409, "已有班级课表采集任务正在执行，请查看当前进度");
        }
        String batch = requestedBatch == null ? UUID.randomUUID().toString() : requestedBatch;
        schedules.queuePull(batch, term, actorId);
        Task task = new Task(batch, credentialOwner, actorId, account, term);
        active = task;
        try {
            worker.execute(() -> collect(task, password));
        } catch (RuntimeException error) {
            active = null;
            schedules.recordTaskState(batch, "FAILED", "采集任务未能启动，请稍后重试");
            throw new BusinessException("采集任务未能启动，请稍后重试");
        }
        return accepted(task);
    }

    private void collect(Task task, String password) {
        try {
            if (password == null) schedules.pullAs(task.credentialOwner, task.account, task.term,
                    "MANUAL", task.actorId, null, task.batchId);
            else schedules.pullWithCredentials(task.actorId, task.account, password, task.term,
                    "MANUAL", null, task.batchId);
            complete(task);
        } catch (MfaRequiredException error) {
            synchronized (this) {
                task.challengeId = pending.put(task.actorId, task.account, task.term, error.getPending(), error.getPhone(), task.batchId);
                task.waiting = true;
            }
        } catch (Exception error) {
            schedules.recordTaskState(task.batchId, "FAILED", message(error));
            complete(task);
        }
    }

    public synchronized Map<String, Object> resume(Long actorId, String challengeId, String code) {
        expirePending();
        PendingClassScheduleManager.Entry entry = pending.get(challengeId);
        if (entry == null || !actorId.equals(entry.userId) || active == null || !active.batchId.equals(entry.batchId)
                || !challengeId.equals(active.challengeId))
            throw new BusinessException("短信验证会话已过期，请重新开始采集");
        if (!active.waiting) throw new BusinessException(409, "正在验证，请勿重复提交");
        if (code == null || code.isBlank()) throw new BusinessException("请输入短信验证码");
        Task task = active;
        task.waiting = false;
        schedules.recordTaskState(task.batchId, "RUNNING", "正在验证短信验证码");
        try { worker.execute(() -> {
            try {
                var session = auth.continueWithMfa(entry.cas, code.trim());
                sessions.cache(task.credentialOwner, entry.account, session);
                pending.remove(challengeId);
                synchronized (this) { task.challengeId = null; }
                collect(task, null);
            } catch (Exception error) {
                synchronized (this) {
                    if (pending.get(challengeId) != null) {
                        task.waiting = true;
                        schedules.recordTaskState(task.batchId, "PENDING", message(error));
                    } else {
                        schedules.recordTaskState(task.batchId, "FAILED", message(error));
                        complete(task);
                    }
                }
            }
        }); } catch (RuntimeException error) {
            task.waiting = true;
            schedules.recordTaskState(task.batchId, "PENDING", "验证任务暂时无法启动，请稍后重试");
            throw new BusinessException("验证任务暂时无法启动，请稍后重试");
        }
        return accepted(task);
    }

    public synchronized Map<String, Object> resend(Long actorId, String challengeId) {
        expirePending();
        var entry = pending.get(challengeId);
        if (entry == null || !actorId.equals(entry.userId) || active == null || !active.waiting
                || !active.batchId.equals(entry.batchId) || !challengeId.equals(active.challengeId))
            throw new BusinessException("短信验证会话已过期或正在验证，请稍后重试");
        auth.sendSms(entry.cas);
        return Map.of("challengeId", challengeId, "phone", entry.phone == null ? "" : entry.phone);
    }

    public synchronized Map<String, Object> progress(String batchId, Long actorId) {
        expirePending();
        Map<String, Object> result = schedules.progress(batchId);
        if (active != null && active.batchId.equals(batchId) && active.waiting && active.actorId.equals(actorId)) {
            var entry = pending.get(active.challengeId);
            if (entry != null) {
                result.put("needMfa", true);
                result.put("challengeId", active.challengeId);
                result.put("phone", entry.phone);
                result.put("mfaExpiresAt", entry.expiresAt);
            }
        }
        return result;
    }

    public synchronized String activeBatchId() { expirePending(); return active == null ? null : active.batchId; }

    @Scheduled(fixedDelay = 30000)
    public synchronized void expirePending() {
        if (active != null && active.waiting && pending.get(active.challengeId) == null) {
            schedules.recordTaskState(active.batchId, "FAILED", "短信验证已超时，请重新发起采集");
            active = null;
        }
    }

    private synchronized void complete(Task task) { if (active == task) active = null; }
    private static Map<String, Object> accepted(Task task) { return Map.of("batchId", task.batchId, "accepted", true); }
    private static String message(Exception error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) return "采集任务异常，请稍后重试";
        return value.substring(0, Math.min(value.length(), 990));
    }
    @PreDestroy
    public void close() { if (worker instanceof ExecutorService executor) executor.shutdownNow(); }

    private static final class Task {
        final String batchId, account, term;
        final Long credentialOwner, actorId;
        String challengeId;
        boolean waiting;
        Task(String batchId, Long credentialOwner, Long actorId, String account, String term) {
            this.batchId = batchId; this.credentialOwner = credentialOwner; this.actorId = actorId;
            this.account = account; this.term = term;
        }
    }
}
