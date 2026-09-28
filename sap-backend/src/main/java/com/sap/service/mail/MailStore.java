package com.sap.service.mail;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.util.function.Supplier;

@Component
public class MailStore {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sap.mapper.UserMapper users;
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
    public final JdbcTemplate db;
    private final TransactionTemplate tx;
    public MailStore(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db = db; this.tx = new TransactionTemplate(manager);
    }
    @PostConstruct
    public void init() {
        db.execute("CREATE TABLE IF NOT EXISTS sys_mail_worker (id INT PRIMARY KEY, owner VARCHAR(64), lease_until BIGINT NOT NULL DEFAULT 0, next_at BIGINT NOT NULL DEFAULT 0)");
        try {
            db.update("INSERT INTO sys_mail_worker(id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM sys_mail_worker WHERE id=1)");
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // 多实例首次启动竞争创建单例行；另一个实例已成功创建即可继续。
        }
        db.execute("CREATE TABLE IF NOT EXISTS sys_mail_binding (event_key VARCHAR(64) PRIMARY KEY, template_id BIGINT, defaults_json TEXT, updated_by BIGINT, updated_at BIGINT NOT NULL)");
        String payload = "id VARCHAR(64) PRIMARY KEY, event_key VARCHAR(64) NOT NULL, recipient VARCHAR(254) NOT NULL, user_id BIGINT, subject VARCHAR(255) NOT NULL, payload MEDIUMTEXT NOT NULL, expires_at BIGINT, created_at BIGINT NOT NULL, attempts INT NOT NULL DEFAULT 0";
        db.execute("CREATE TABLE IF NOT EXISTS sys_mail_queue (" + payload + ", state VARCHAR(20) NOT NULL DEFAULT 'PENDING', owner VARCHAR(64), INDEX idx_mail_queue_order(state,created_at))");
        db.execute("CREATE TABLE IF NOT EXISTS sys_mail_failed (" + payload + ", status VARCHAR(20) NOT NULL, reason VARCHAR(500), failed_at BIGINT NOT NULL, updated_by BIGINT, INDEX idx_mail_failed_time(failed_at))");
        db.execute("CREATE TABLE IF NOT EXISTS sys_mail_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, message_id VARCHAR(64), event_key VARCHAR(64), recipient VARCHAR(254), subject VARCHAR(255), status VARCHAR(32) NOT NULL, detail VARCHAR(500), actor_id BIGINT, created_at BIGINT NOT NULL, INDEX idx_mail_log_time(created_at))");
        db.execute("CREATE TABLE IF NOT EXISTS sys_mail_dedup (event_id VARCHAR(180) PRIMARY KEY, message_id VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL)");
        db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            boolean contextColumn=false;
            try(var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,"sys_mail_log",null)) {
                while(columns.next()) if("context_json".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) contextColumn=true;
            }
            if(!contextColumn) {
                try(var statement=connection.createStatement()) { statement.execute("ALTER TABLE sys_mail_log ADD COLUMN context_json MEDIUMTEXT"); }
                catch(java.sql.SQLException e) { if(e.getErrorCode()!=1060)throw e; }
            }
            boolean exists=false;
            try(var indexes=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,"sys_mail_log",false,false)) {
                while(indexes.next()) if("idx_mail_log_message".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) exists=true;
            }
            if(!exists) {
                try(var statement=connection.createStatement()) {
                    statement.execute("CREATE INDEX idx_mail_log_message ON sys_mail_log(message_id,id)");
                } catch(java.sql.SQLException e) {
                    if(e.getErrorCode()!=1061)throw e; // MySQL 多实例启动时重复索引名，可安全忽略。
                }
            }
            return null;
        });
    }
    public <T> T transaction(Supplier<T> action) { return tx.execute(s -> action.get()); }
    public Map<String,Object> lock() { return db.queryForMap("SELECT * FROM sys_mail_worker WHERE id=1 FOR UPDATE"); }
    public void audit(String id, String hook, String to, String subject, String status, String detail, Long actor) {
        audit(id,hook,to,subject,status,detail,actor,null);
    }
    public void audit(String id, String hook, String to, String subject, String status, String detail, Long actor, Map<String,Object> supplied) {
        Map<String,Object> context = supplied == null ? new LinkedHashMap<>() : new LinkedHashMap<>(supplied);
        if(supplied==null && id!=null) {
            var previous=db.queryForList("SELECT context_json FROM sys_mail_log WHERE message_id=? AND context_json IS NOT NULL ORDER BY id DESC LIMIT 1",id);
            if(!previous.isEmpty())context.putAll(decode(previous.get(0).get("context_json")));
            else if(db.queryForObject("SELECT COUNT(*) FROM sys_mail_log WHERE message_id=?",Long.class,id)>0)context.put("source","legacy");
        }
        context.putIfAbsent("eventTitle",eventTitle(hook));
        context.putIfAbsent("reason",switch(status) {
            case "BOUND" -> "管理员配置代码事件的邮件模板";
            case "UNBOUND" -> "管理员解除代码事件与模板的绑定";
            default -> "TEST".equals(hook) ? "管理员主动发送测试邮件" : "系统处理邮件业务事件";
        });
        context.putIfAbsent("source","snapshot");
        var operator=actor==null ? Map.<String,Object>of("name","系统工作器","account","系统")
                : supplied!=null && supplied.get("initiator") instanceof Map<?,?> initiator && Objects.equals(initiator.get("id"),actor)
                ? new LinkedHashMap<>(initiator) : identity(actor);
        context.put("operator",operator);
        if(actor!=null)context.putIfAbsent("initiator",operator);
        try {
            db.update("INSERT INTO sys_mail_log(message_id,event_key,recipient,subject,status,detail,actor_id,created_at,context_json) VALUES (?,?,?,?,?,?,?,?,?)",
                    id, hook, to, subject, status, detail, actor, System.currentTimeMillis(),json.writeValueAsString(context));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalArgumentException("邮件业务日志无法序列化",e);}
    }
    public Map<String,Object> identity(Long id) { return MailAuditContext.person(id,users==null||id==null ? null : users.selectById(id)); }
    private Map<String,Object> decode(Object value) {
        if(value==null)return new LinkedHashMap<>();
        try {return json.readValue(String.valueOf(value),new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});}
        catch(Exception ignored) {return new LinkedHashMap<>();}
    }
    private String eventTitle(String key) {
        try {return MailHook.valueOf(key).title;} catch(Exception ignored) {return "TEST".equals(key)?"管理员测试邮件":"邮件配置 / 系统事件";}
    }
    /** 旧记录只补可确定的当前资料，不伪造历史业务摘要。 */
    public List<Map<String,Object>> enrich(List<Map<String,Object>> rows) {
        Map<Long,Map<String,Object>> people=new HashMap<>();
        for(var row:rows) {
            var context=decode(row.remove("context_json"));
            if(context.isEmpty()) {
                context.put("source","legacy");context.put("eventTitle",eventTitle(String.valueOf(row.get("event_key"))));
                context.put("reason",switch(String.valueOf(row.get("status"))) {
                    case "BOUND" -> "管理员配置代码事件的邮件模板";
                    case "UNBOUND" -> "管理员解除代码事件与模板的绑定";
                    default -> "历史记录未保存业务快照，事件详情未记录";
                });
                Long actor=row.get("actor_id") instanceof Number n?n.longValue():null;
                if(actor!=null) {
                    var person=people.computeIfAbsent(actor,this::identity);context.put("operator",person);
                    if("ACCOUNT_REGISTERED".equals(row.get("event_key"))) {
                        context.put("recipient",person); context.put("initiator",person);
                        context.put("reason","账号注册成功，触发欢迎了解软件协会邮件");
                    }
                }
            }
            row.put("context",context);
        }
        return rows;
    }
    public void requireUnbound(Long id) {
        if (db.queryForObject("SELECT COUNT(*) FROM sys_mail_binding WHERE template_id=?", Long.class, id) > 0)
            throw new com.sap.common.BusinessException(400, "此模板已绑定代码事件，请先解绑后删除");
    }
}
