package com.sap.service.mail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.jw.util.AesUtil;
import com.sap.service.EmailService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** 数据库为事实来源；本地执行器只负责唤醒，不保存待发邮件。 */
@Service
public class MailQueue {
    public static final long LEASE = 600_000;
    public static final long NOTIFICATION_GAP = 65_000;
    private static final String CODE_EVENTS = "('PASSWORD_CODE','APP_REGISTRATION_CODE')";
    private static final String QUEUED_EVENTS = "('ACCOUNT_REGISTERED','MEMBER_JOINED')";
    private final MailStore store;
    private final EmailService email;
    private final ObjectMapper json;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "database-mail-worker"); t.setDaemon(true); return t;
    });
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();
    private boolean active;
    private boolean signalled;
    @Value("${jw.aes-key}") private String key;
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");
    public MailQueue(MailStore store, EmailService email, ObjectMapper json) {
        this.store = store; this.email = email; this.json = json;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        // 首次安装按代码推荐标识绑定，后续解绑不会被重新绑定。
        store.transaction(() -> {
            var legacy = store.lock();
            // 旧版全局租约只迁移给当时正在发送的一封，不能阻塞其他邮件。
            if (legacy.get("owner") != null) {
                store.db.update("UPDATE sys_mail_queue SET lease_until=? WHERE state='SENDING' AND owner=? AND lease_until=0",
                        number(legacy.get("lease_until")), legacy.get("owner"));
            }
            store.db.update("UPDATE sys_mail_worker SET owner=NULL,lease_until=0 WHERE id=1");
            for (MailHook hook : MailHook.values()) {
                if (store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_binding WHERE event_key=?", Long.class, hook.name()) == 0) {
                    var matches = store.db.queryForList("SELECT id FROM sys_email_template WHERE template_key=? AND deleted=0", hook.initialTemplateKey);
                    Long initialId = matches.isEmpty() ? null : number(matches.get(0).get("id"));
                    if (initialId != null) {
                        try { validateContract(hook, email.getTemplate(initialId)); }
                        catch (BusinessException invalidTemplate) {
                            // 相同标识也可能被管理员改成其他用途，不能把不匹配的模板带入业务流程。
                            store.audit(null, hook.name(), null, null, "SKIPPED", "推荐模板不符合代码参数约定，请在管理端重新绑定", null);
                            initialId = null;
                        }
                    }
                    store.db.update("INSERT INTO sys_mail_binding(event_key,template_id,defaults_json,updated_at) VALUES (?,?,?,?)", hook.name(),
                            initialId, hook == MailHook.ACCOUNT_REGISTERED
                                    ? writeJson(Map.of("associationIntro","软件协会是一群热爱技术、乐于分享的同学相聚的地方。我们希望在这里一起交流想法、动手实践，在探索中发现兴趣，在协作中收获成长。欢迎你来了解我们，找到属于自己的方向。")) : "{}", System.currentTimeMillis());
                }
            }
            return null;
        });
        kick();
    }
    // 持久化记录兜底恢复；正常邮件在业务提交后立即唤醒，不等待此扫描。
    @Scheduled(fixedDelay = 5000)
    public void recover() { kick(); }
    @PreDestroy public void stop() {
        executor.shutdown();
        senders.shutdown();
        try {
            if (!senders.awaitTermination(20, TimeUnit.SECONDS)) senders.shutdownNow();
        } catch (InterruptedException interrupted) {
            senders.shutdownNow(); Thread.currentThread().interrupt();
        }
    }
    public synchronized void kick() {
        signalled = true;
        if (active || executor.isShutdown()) return;
        active = true;
        executor.execute(this::work);
    }
    private void afterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { kick(); }
            });
        } else kick();
    }
    private void work() {
        synchronized (this) { signalled = false; }
        try {
            while (!senders.isShutdown() && email.sendingEnabled()) {
                var job = claim();
                if (job == null) break;
                try { senders.execute(() -> send(job)); }
                catch (RejectedExecutionException stopping) {
                    // 执行器拒绝时还没有发起 SMTP，可安全恢复为待处理。
                    store.db.update("UPDATE sys_mail_queue SET state='PENDING',owner=NULL,lease_until=0 WHERE id=? AND owner=?",
                            job.get("id"), job.get("owner"));
                    break;
                }
            }
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("邮件调度暂不可用：{}", error.getClass().getSimpleName());
        } finally {
            synchronized (this) {
                active = false;
                if (signalled && !executor.isShutdown()) kick();
            }
        }
    }
    private void send(Map<String,Object> job) {
        String error = null, result = "SUCCESS";
        try {
            long expires = number(job.get("expires_at"));
            if (expires > 0 && expires <= System.currentTimeMillis()) {
                result = "EXPIRED"; error = "邮件已过期，未进行 SMTP 发送；验证码请重新申请";
            } else {
                String html = AesUtil.decrypt(key, String.valueOf(job.get("payload")));
                email.deliver(String.valueOf(job.get("recipient")), String.valueOf(job.get("subject")), html);
            }
        } catch (Exception failure) {
            result = "FAILED";
            error = "SMTP 发送失败（" + failure.getClass().getSimpleName() + "），请检查配置和网络；超时可能已被服务器接收";
        }
        try { finish(job, result, error); }
        catch (Exception failure) {
            // 保留发送记录与租约；恢复时标记未知，不能因落库失败而重复发信。
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("邮件结果暂无法保存：{}", failure.getClass().getSimpleName());
        } finally { kick(); }
    }
    Map<String,Object> claim() {
        return store.transaction(() -> {
            var state = store.lock(); long now = System.currentTimeMillis();
            for (var expired : store.db.queryForList("SELECT * FROM sys_mail_queue WHERE state='SENDING' AND lease_until<=? FOR UPDATE", now)) {
                moveFailure(expired, "UNKNOWN", "发送中断，结果未知；请核实收件情况后决定是否重试", null);
                if (MailHook.isQueuedNotification(String.valueOf(expired.get("event_key")))) {
                    store.db.update("UPDATE sys_mail_worker SET next_at=? WHERE id=1", now + NOTIFICATION_GAP);
                    state.put("next_at",now + NOTIFICATION_GAP);
                }
            }
            // 验证码优先。欢迎通知仅在没有验证码等待/发送、没有另一封欢迎通知在发时领取。
            boolean codesActive = store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE event_key IN " + CODE_EVENTS + " AND state IN ('PENDING','SENDING')",Long.class)>0;
            boolean notificationActive = store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE event_key IN " + QUEUED_EVENTS + " AND state='SENDING'",Long.class)>0;
            boolean allowNotification = !codesActive && !notificationActive && number(state.get("next_at"))<=now;
            String eligible = allowNotification ? "" : " AND event_key NOT IN " + QUEUED_EVENTS;
            var rows = store.db.queryForList("SELECT * FROM sys_mail_queue WHERE state='PENDING'" + eligible
                    + " ORDER BY CASE WHEN event_key IN " + CODE_EVENTS + " THEN 0 WHEN event_key IN " + QUEUED_EVENTS + " THEN 2 ELSE 1 END,created_at,id LIMIT 1 FOR UPDATE");
            if (rows.isEmpty()) return null;
            var job = rows.get(0); String owner = UUID.randomUUID().toString();
            store.db.update("UPDATE sys_mail_queue SET state='SENDING',owner=?,lease_until=?,attempts=attempts+1 WHERE id=?", owner, now + LEASE, job.get("id"));
            job.put("owner", owner); job.put("lease_until", now + LEASE); job.put("attempts", number(job.get("attempts")) + 1);
            if (MailHook.isQueuedNotification(String.valueOf(job.get("event_key"))))
                store.db.update("UPDATE sys_mail_worker SET next_at=? WHERE id=1",now + NOTIFICATION_GAP);
            store.audit(String.valueOf(job.get("id")), String.valueOf(job.get("event_key")), String.valueOf(job.get("recipient")), String.valueOf(job.get("subject")), "SENDING", "开始发送", null);
            return job;
        });
    }
    void finish(Map<String,Object> job, String result, String error) {
        store.transaction(() -> {
            // 与领取保持相同锁顺序，普通通知完成时持久化节奏，不占用验证码发送通道。
            store.lock();
            var rows = store.db.queryForList("SELECT owner FROM sys_mail_queue WHERE id=? AND state='SENDING' FOR UPDATE", job.get("id"));
            if (rows.isEmpty() || !Objects.equals(rows.get(0).get("owner"), job.get("owner"))) return null;
            if (MailHook.isQueuedNotification(String.valueOf(job.get("event_key"))))
                store.db.update("UPDATE sys_mail_worker SET next_at=? WHERE id=1",System.currentTimeMillis() + NOTIFICATION_GAP);
            if (error != null) moveFailure(job, result, error, null);
            else {
                store.audit(String.valueOf(job.get("id")), String.valueOf(job.get("event_key")), String.valueOf(job.get("recipient")), String.valueOf(job.get("subject")), "SUCCESS", "SMTP 已接受（不代表最终投递或已读）", null);
                store.db.update("DELETE FROM sys_mail_queue WHERE id=?", job.get("id"));
            }
            return null;
        });
    }
    private void moveFailure(Map<String,Object> job, String status, String reason, Long actor) {
        store.db.update("INSERT INTO sys_mail_failed(id,event_key,recipient,user_id,subject,payload,expires_at,created_at,attempts,status,reason,failed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                job.get("id"), job.get("event_key"), job.get("recipient"), job.get("user_id"), job.get("subject"), job.get("payload"), job.get("expires_at"), job.get("created_at"), job.get("attempts"), status, reason, System.currentTimeMillis());
        store.audit(String.valueOf(job.get("id")), String.valueOf(job.get("event_key")), String.valueOf(job.get("recipient")), String.valueOf(job.get("subject")), status, reason, actor);
        store.db.update("DELETE FROM sys_mail_queue WHERE id=?", job.get("id"));
    }
    public String enqueue(MailHook hook, Long userId, String to, Map<String,Object> values, String eventId, Long expiresAt) {
        return enqueue(hook,userId,to,values,eventId,expiresAt,
                Map.of("eventTitle",hook.title,"recipient",store.identity(userId),"initiator",store.identity(userId),"reason",hook.title+"触发邮件通知"));
    }
    public String enqueue(MailHook hook, Long userId, String to, Map<String,Object> values, String eventId, Long expiresAt, Map<String,Object> context) {
        return store.transaction(() -> {
            store.lock();
            if (eventId == null || eventId.length() > 150) throw new BusinessException(400,"缺少有效业务幂等键");
            String unique = hook.name() + ":" + eventId;
            if (unique.length() > 180) throw new BusinessException(400,"业务幂等键过长");
            var duplicate = store.db.queryForList("SELECT message_id FROM sys_mail_dedup WHERE event_id=?", unique);
            if (!duplicate.isEmpty()) return String.valueOf(duplicate.get(0).get("message_id"));
            var bindings = store.db.queryForList("SELECT * FROM sys_mail_binding WHERE event_key=?", hook.name());
            if (bindings.isEmpty() || bindings.get(0).get("template_id") == null) {
                store.audit(null,hook.name(),to,null,"SKIPPED","代码事件未绑定模板",initiatorId(context),context); return null;
            }
            var template = email.getTemplate(number(bindings.get(0).get("template_id")));
            if (!Boolean.TRUE.equals(template.get("enabled"))) {
                store.audit(null,hook.name(),to,null,"SKIPPED","绑定模板已停用",initiatorId(context),context); return null;
            }
            try { validateContract(hook, template); }
            catch (BusinessException invalidBinding) {
                store.audit(null,hook.name(),to,null,"SKIPPED","绑定模板参数与代码事件不一致，请重新绑定",initiatorId(context),context);
                return null;
            }
            Map<String,Object> merged = new LinkedHashMap<>(readJson(bindings.get(0).get("defaults_json")));
            merged.putAll(values);
            for (String variable : variables(template)) {
                if (merged.get(variable) == null || String.valueOf(merged.get(variable)).isBlank())
                    throw new BusinessException(400, "邮件事件缺少参数：" + variable);
            }
            if (hook.isVerificationCode() && (expiresAt == null || expiresAt <= System.currentTimeMillis()))
                throw new BusinessException(400,"验证码邮件必须设置未来的过期时间");
            var rendered = email.render(String.valueOf(template.get("subject")), String.valueOf(template.get("htmlContent")), merged);
            String id = insert(hook.name(), userId, to, String.valueOf(rendered.get("subject")), String.valueOf(rendered.get("html")), expiresAt, initiatorId(context),context);
            store.db.update("INSERT INTO sys_mail_dedup(event_id,message_id,created_at) VALUES (?,?,?)", unique, id, System.currentTimeMillis());
            afterCommit(); return id;
        });
    }
    public String enqueueTest(String to, String subject, String html, Long actor) {
        return store.transaction(() -> { store.lock(); String id = insert("TEST", null, to, subject, html, null, actor); afterCommit(); return id; });
    }
    /** Fixed system alert: persistent dedup and the same audited/retryable SMTP queue as business mail. */
    public String enqueueDownloadAlert(String to, String eventId, String date, long count, int threshold) {
        return store.transaction(() -> {
            store.lock();
            String unique = "APP_DOWNLOAD_ALERT:" + eventId;
            var found = store.db.queryForList("SELECT message_id FROM sys_mail_dedup WHERE event_id=?", unique);
            if (!found.isEmpty()) return String.valueOf(found.getFirst().get("message_id"));
            String html = "<h2>软协 App 下载次数告警</h2><p>北京时间 " + date + "，本轮防护计数已达 " + count + " 次，超过告警阈值 " + threshold
                    + " 次。</p><p>请在管理端「软协课表 → 下载防护」检查当天统计与下载方式。超过服务器下载阈值后，后续下载自动由服务器转发。</p>";
            String id = insert("APP_DOWNLOAD_ALERT", null, to, "软协 App 异常下载告警", html, null, null,
                    Map.of("eventTitle", "App 下载次数告警", "reason", "全平台当日下载次数超过配置阈值", "details", Map.of("日期", date, "防护计数", count, "告警阈值", threshold)));
            store.db.update("INSERT INTO sys_mail_dedup(event_id,message_id,created_at) VALUES (?,?,?)", unique, id, System.currentTimeMillis());
            afterCommit();
            return id;
        });
    }
    private String insert(String hook, Long userId, String to, String subject, String html, Long expiry, Long actor) {
        return insert(hook,userId,to,subject,html,expiry,actor,null);
    }
    private String insert(String hook, Long userId, String to, String subject, String html, Long expiry, Long actor, Map<String,Object> context) {
        if (to == null || to.length()>254 || !to.matches("^[^\\s@,;<>]+@[^\\s@,;<>]+\\.[^\\s@,;<>]+$")) throw new BusinessException(400,"收件邮箱无效");
        if (subject == null || subject.isBlank() || subject.length()>255 || subject.contains("\r") || subject.contains("\n")) throw new BusinessException(400,"邮件主题无效或过长");
        if (html == null || html.length()>256000) throw new BusinessException(400,"邮件正文超过大小限制");
        String id=UUID.randomUUID().toString();
        store.db.update("INSERT INTO sys_mail_queue(id,event_key,recipient,user_id,subject,payload,expires_at,created_at) VALUES (?,?,?,?,?,?,?,?)", id, hook, to, userId, subject, AesUtil.encrypt(key,html), expiry, System.currentTimeMillis());
        store.audit(id,hook,to,subject,"QUEUED",MailHook.isQueuedNotification(hook)?"已加入普通通知队列，验证码优先":"已提交，立即发送",actor,context); return id;
    }
    static Long initiatorId(Map<String,Object> context) {
        return context.get("initiator") instanceof Map<?,?> person && person.get("id") instanceof Number id ? id.longValue() : null;
    }
    public List<Map<String,Object>> bindings() {
        var result=new ArrayList<Map<String,Object>>();
        for (MailHook hook:MailHook.values()) {
            Map<String,Object> row=new LinkedHashMap<>(); row.put("eventKey",hook.name()); row.put("title",hook.title); row.put("variables",hook.variables);
            var list=store.db.queryForList("SELECT * FROM sys_mail_binding WHERE event_key=?",hook.name());
            row.put("templateId",list.isEmpty()?null:list.get(0).get("template_id"));
            row.put("deliveryPolicy",MailHook.deliveryPolicy(hook.name()));
            boolean compatible=true;
            if(row.get("templateId")!=null) {
                try { validateContract(hook,email.getTemplate(number(row.get("templateId")))); }
                catch(BusinessException invalidBinding) { compatible=false; }
            }
            row.put("compatible",compatible);
            row.put("defaults",list.isEmpty()?Map.of():readJson(list.get(0).get("defaults_json"))); result.add(row);
        } return result;
    }
    public void bind(MailHook hook, Long templateId, Map<String,Object> defaults, Long actor) {
        if (defaults.keySet().stream().anyMatch(k -> hook != MailHook.ACCOUNT_REGISTERED || !k.equals("associationIntro"))) throw new BusinessException(400,"仅协会介绍可配置为固定参数");
        store.transaction(() -> {
            store.lock();
            if (templateId!=null) {
                var template=email.getTemplate(templateId);
                validateContract(hook,template);
                if(variables(template).contains("associationIntro") && String.valueOf(defaults.getOrDefault("associationIntro","")).isBlank())
                    throw new BusinessException(400,"请填写注册欢迎邮件的协会介绍");
            }
            store.db.update("DELETE FROM sys_mail_binding WHERE event_key=?",hook.name());
            store.db.update("INSERT INTO sys_mail_binding(event_key,template_id,defaults_json,updated_by,updated_at) VALUES (?,?,?,?,?)",hook.name(),templateId,writeJson(defaults),actor,System.currentTimeMillis());
            store.audit(null,hook.name(),null,null,templateId==null?"UNBOUND":"BOUND","模板 ID："+templateId,actor); return null;
        });
    }
    public void validateBoundTemplate(Long id, Map<String,Object> template) {
        for(var row:store.db.queryForList("SELECT event_key FROM sys_mail_binding WHERE template_id=?",id)) validateContract(MailHook.parse(String.valueOf(row.get("event_key"))),template);
    }
    public static void validateContract(MailHook hook, Map<String,Object> template) {
        if (hook.isVerificationCode()) {
            var secret = java.util.regex.Pattern.compile("\\{\\{\\s*code\\s*}}");
            var document = org.jsoup.Jsoup.parse(String.valueOf(template.get("htmlContent")));
            for (var element : document.getAllElements()) {
                for (var attribute : element.attributes()) {
                    if (secret.matcher(attribute.getValue()).find())
                        throw new BusinessException(400, "验证码只能出现在邮件正文文本中，不能用于链接或图片等属性");
                }
                if ((element.tagName().equals("style") || element.tagName().equals("script")) && secret.matcher(element.data()).find())
                    throw new BusinessException(400, "验证码不能出现在样式或脚本中");
            }
        }
        Set<String> actual = variables(template);
        Set<String> expected = new LinkedHashSet<>(hook.variables);
        if (!actual.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected); missing.removeAll(actual);
            Set<String> extra = new LinkedHashSet<>(actual); extra.removeAll(expected);
            throw new BusinessException(400, "模板参数必须与代码事件完全一致；缺少：" + missing + "；多出：" + extra);
        }
        if(hook.isVerificationCode() && (variables(Map.of("subject",template.get("subject"),"htmlContent","")).contains("code") || !variables(Map.of("subject","","htmlContent",template.get("htmlContent"))).containsAll(List.of("code","expiresInMinutes")))) throw new BusinessException(400,"验证码模板正文须包含 code 和 expiresInMinutes，主题不得包含验证码");
    }
    private static Set<String> variables(Map<String,Object> template) {
        var m=VARIABLE.matcher(template.get("subject")+"\n"+template.get("htmlContent")); Set<String> result=new LinkedHashSet<>(); while(m.find())result.add(m.group(1));return result;
    }
    public Map<String,Object> page(String kind, int page, int size) {
        if (kind.equals("messages")) return messagePage(page, size);
        String table=switch(kind){case "queue"->"sys_mail_queue";case "failed"->"sys_mail_failed";case "logs"->"sys_mail_log";default->throw new BusinessException(400,"未知列表");};
        int limit=Math.max(1,Math.min(100,size));int offset=(Math.max(1,Math.min(100000,page))-1)*limit;
        String columns=kind.equals("logs")?"*":"id,event_key,recipient,user_id,subject,expires_at,created_at,attempts,"+(kind.equals("queue")?"state":"status,reason,failed_at");
        if(!kind.equals("logs"))columns+=",(SELECT l.context_json FROM sys_mail_log l WHERE l.message_id="+table+".id AND l.context_json IS NOT NULL ORDER BY l.id DESC LIMIT 1) AS context_json";
        return Map.of("total",store.db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class),"records",store.enrich(store.db.queryForList("SELECT "+columns+" FROM "+table+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",limit,offset)));
    }
    /** 每封邮件仅展示最新事件；没有 message_id 的跳过/配置事件分别保留。 */
    private Map<String,Object> messagePage(int page, int size) {
        int limit=Math.max(1,Math.min(100,size));
        int offset=(Math.max(1,Math.min(100000,page))-1)*limit;
        String latest=" FROM sys_mail_log l WHERE l.message_id IS NULL OR NOT EXISTS "
                +"(SELECT 1 FROM sys_mail_log newer WHERE newer.message_id=l.message_id AND newer.id>l.id)";
        return Map.of("total",store.db.queryForObject("SELECT COUNT(*)"+latest,Long.class),
                "records",store.enrich(store.db.queryForList("SELECT l.*"+latest+" ORDER BY l.id DESC LIMIT ? OFFSET ?",limit,offset)));
    }
    public Map<String,Object> history(Long logId, int page, int size) {
        var rows=store.db.queryForList("SELECT * FROM sys_mail_log WHERE id=?",logId);
        if(rows.isEmpty())throw new BusinessException(404,"日志记录不存在");
        Object messageId=rows.get(0).get("message_id");
        if(messageId==null)return Map.of("total",1,"records",store.enrich(rows));
        int limit=Math.max(1,Math.min(100,size));
        int offset=(Math.max(1,Math.min(100000,page))-1)*limit;
        return Map.of("total",store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_log WHERE message_id=?",Long.class,messageId),
                "records",store.enrich(store.db.queryForList("SELECT * FROM sys_mail_log WHERE message_id=? ORDER BY id DESC LIMIT ? OFFSET ?",messageId,limit,offset)));
    }
    public Map<String,Object> failedDetail(String id) {
        var rows=store.db.queryForList("SELECT * FROM sys_mail_failed WHERE id=?",id);
        if(rows.isEmpty())throw new BusinessException(404,"失败记录不存在");
        var row=rows.get(0);String encrypted=String.valueOf(row.remove("payload"));
        row.put("html", MailHook.isVerificationCode(String.valueOf(row.get("event_key")))?"<p>验证码内容已隐藏，请重新申请验证码。</p>":AesUtil.decrypt(key,encrypted)); return row;
    }
    public void failedAction(List<String> ids, String action, Long actor) {
        if(ids==null||ids.isEmpty()||ids.size()>100)throw new BusinessException(400,"每次请选择 1–100 条记录");
        if(!List.of("retry","ignore","delete").contains(action))throw new BusinessException(400,"未知操作");
        store.transaction(()->{
            store.lock();
            for(String id:new LinkedHashSet<>(ids)) {
                var rows=store.db.queryForList("SELECT * FROM sys_mail_failed WHERE id=? FOR UPDATE",id);if(rows.isEmpty())continue;
                var row=rows.get(0);
                if(action.equals("retry")) {
                    if(MailHook.isVerificationCode(String.valueOf(row.get("event_key"))) || (number(row.get("expires_at"))>0 && number(row.get("expires_at"))<=System.currentTimeMillis())) throw new BusinessException(400,"选中项含验证码或过期邮件，请重新发起原业务请求");
                    store.db.update("INSERT INTO sys_mail_queue(id,event_key,recipient,user_id,subject,payload,expires_at,created_at,attempts) VALUES (?,?,?,?,?,?,?,?,?)", id,row.get("event_key"),row.get("recipient"),row.get("user_id"),row.get("subject"),row.get("payload"),row.get("expires_at"),System.currentTimeMillis(),row.get("attempts"));
                }
                if(action.equals("ignore"))store.db.update("UPDATE sys_mail_failed SET status='IGNORED',updated_by=? WHERE id=?",actor,id);
                else store.db.update("DELETE FROM sys_mail_failed WHERE id=?",id);
                store.audit(id,String.valueOf(row.get("event_key")),String.valueOf(row.get("recipient")),String.valueOf(row.get("subject")),action.toUpperCase(Locale.ROOT),"管理员处理失败邮件",actor);
            } if(action.equals("retry"))afterCommit();return null;
        });
    }
    public Map<String,Object> status() {
        var state=new LinkedHashMap<>(store.db.queryForMap("SELECT COALESCE(MAX(lease_until),0) AS lease_until FROM sys_mail_queue WHERE state='SENDING'"));
        state.put("next_at",0L);
        var verification = laneStatus(CODE_EVENTS);
        var notifications = new LinkedHashMap<>(laneStatus(QUEUED_EVENTS));
        notifications.put("nextAt",store.db.queryForObject("SELECT next_at FROM sys_mail_worker WHERE id=1",Long.class));
        notifications.put("intervalSeconds",NOTIFICATION_GAP/1000);
        return Map.of("queue",store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue",Long.class),"failed",store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_failed WHERE status<>'IGNORED'",Long.class),"state",state,"enabled",email.sendingEnabled(),
                "pending",store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE state='PENDING'",Long.class),
                "sending",store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE state='SENDING'",Long.class),"deliveryMode","VERIFICATION_FIRST",
                "verification",verification,"notifications",notifications);
    }
    private Map<String,Object> laneStatus(String events) {
        return Map.of("pending",store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE state='PENDING' AND event_key IN " + events,Long.class),
                "sending",store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue WHERE state='SENDING' AND event_key IN " + events,Long.class));
    }
    private Map<String,Object> readJson(Object raw) {try{return raw==null?Map.of():json.readValue(String.valueOf(raw),new TypeReference<>(){});}catch(Exception e){throw new BusinessException(400,"事件固定参数无效");}}
    private String writeJson(Object data){try{return json.writeValueAsString(data);}catch(Exception e){throw new BusinessException(400,"参数无法保存");}}
    static long number(Object n){return n==null?0:((Number)n).longValue();}
}
