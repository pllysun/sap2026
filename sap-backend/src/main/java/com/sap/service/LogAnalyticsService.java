package com.sap.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 90 天热明细 + 永久小时聚合。归档、校验、删除处于同一事务，跨实例互斥。 */
@Service
@Slf4j
public class LogAnalyticsService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public LogAnalyticsService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
    }
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_log_archive_lock (id INT PRIMARY KEY)");
        try { jdbc.update("INSERT INTO sys_log_archive_lock(id) VALUES (1)"); }
        catch (org.springframework.dao.DuplicateKeyException ignored) { }
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sys_log_archive (
                group_key VARCHAR(64) PRIMARY KEY, bucket_time DATETIME NOT NULL,
                source VARCHAR(8) NOT NULL, endpoint VARCHAR(255) NOT NULL,
                http_method VARCHAR(10) NOT NULL, user_id BIGINT NOT NULL,
                user_name VARCHAR(50), ip VARCHAR(50), operation_type VARCHAR(10), description VARCHAR(200),
                result_code INT NOT NULL, call_count BIGINT NOT NULL, duration_sum BIGINT NOT NULL,
                duration_min BIGINT NOT NULL, duration_max BIGINT NOT NULL,
                first_time DATETIME NOT NULL, last_time DATETIME NOT NULL
            )
            """);
        // 独立新表，仅持久保存汇总；旧 log_stats（热力图）始终保留。
        ensureIndex("idx_log_archive_bucket", "bucket_time");
        ensureIndex("idx_log_archive_user", "user_id,bucket_time");
        ensureIndex("idx_log_archive_endpoint", "endpoint,bucket_time");
    }
    private void ensureIndex(String name, String columns) {
        try { jdbc.execute("CREATE INDEX " + name + " ON sys_log_archive(" + columns + ")"); }
        catch (org.springframework.dao.DataAccessException e) {
            // 索引存在可忽略；表读写失败仍由主流程显式报错，不跳过归档校验。
            log.debug("归档索引检查 {}: {}", name, e.getMessage());
        }
    }
    public static String classify(String path, String client) {
        if (path == null) return "WEB";
        if (path.contains("/admin") || path.startsWith("/api/class-schedule/collect") || path.startsWith("/api/class-schedule/log")) return "WEB";
        return "app".equalsIgnoreCase(client) || path.startsWith("/api/app") || path.startsWith("/api/jw")
            || path.startsWith("/api/class-schedule") || path.startsWith("/api/academic-calendar")
            || path.startsWith("/api/auth/app") ? "APP" : "WEB";
    }
    public static String normalizeEndpoint(String path) {
        if (path == null) return "未知接口";
        return path.split("\\?",2)[0].replaceAll("/[0-9]+(?=/|$)", "/{id}")
            .replaceAll("/[a-fA-F0-9]{8}-[a-fA-F0-9-]{27,}(?=/|$)", "/{id}");
    }
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 180_000)
    public void archiveScheduled() {
        try {
            for (int i=0;i<20;i++) if (archiveBatch(LocalDateTime.now()) < 1000) break;
        } catch (Exception e) { log.error("日志归档未完成，事务已回滚，保留原始明细", e); }
    }
    public int archiveBatch(LocalDateTime now) {
        Integer result = tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM sys_log_archive_lock WHERE id=1 FOR UPDATE", Integer.class);
            var rows = jdbc.queryForList("SELECT * FROM sys_log WHERE request_time < ? ORDER BY request_time,id LIMIT 1000 FOR UPDATE", now.minusDays(90));
            if (rows.isEmpty()) return 0;
            Map<String, Aggregate> groups = new LinkedHashMap<>();
            for (var row: rows) {
                var a = new Aggregate(row);
                groups.compute(a.key, (k, previous) -> previous == null ? a : previous.add(a));
            }
            long before = totalArchived(groups.keySet());
            for (var a: groups.values()) {
                int updated = jdbc.update("""
                    UPDATE sys_log_archive SET call_count=call_count+?,duration_sum=duration_sum+?,
                    duration_min=LEAST(duration_min,?),duration_max=GREATEST(duration_max,?),
                    first_time=LEAST(first_time,?),last_time=GREATEST(last_time,?) WHERE group_key=?
                    """, a.count,a.sum,a.min,a.max,a.first,a.last,a.key);
                if (updated == 0) jdbc.update("""
                    INSERT INTO sys_log_archive(group_key,bucket_time,source,endpoint,http_method,user_id,user_name,ip,
                    operation_type,description,result_code,call_count,duration_sum,duration_min,duration_max,first_time,last_time)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, a.key,a.bucket,a.source,a.endpoint,a.method,a.userId,a.name,a.ip,a.operation,a.description,
                    a.code,a.count,a.sum,a.min,a.max,a.first,a.last);
            }
            if (totalArchived(groups.keySet())-before != rows.size()) throw new IllegalStateException("归档数量校验失败");
            var ids = rows.stream().map(r -> r.get("id")).toArray();
            int deleted = jdbc.update("DELETE FROM sys_log WHERE id IN (" + String.join(",", Collections.nCopies(ids.length,"?")) + ")",ids);
            if (deleted != rows.size()) throw new IllegalStateException("明细删除数量校验失败");
            return deleted;
        });
        return result == null ? 0 : result;
    }
    private long totalArchived(Set<String> keys) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(SUM(call_count),0) FROM sys_log_archive WHERE group_key IN ("+
            String.join(",",Collections.nCopies(keys.size(),"?"))+")", Long.class,keys.toArray()));
    }
    private static String str(Map<String,Object> row,String key,String fallback) {
        Object value=row.get(key); return value == null ? fallback : value.toString();
    }
    private static long number(Map<String,Object> row,String key,long fallback) {
        Object value=row.get(key);return value instanceof Number n ? n.longValue() : fallback;
    }
    static LocalDateTime sqlTime(Object value) {
        // Connector/J 的 DATETIME getObject 返回 LocalDateTime；H2 返回 Timestamp。
        if (value instanceof LocalDateTime date) return date;
        if (value instanceof Timestamp date) return date.toLocalDateTime();
        throw new IllegalArgumentException("Unsupported SQL timestamp type");
    }
    private static class Aggregate {
        String key,source,endpoint,method,name,ip,operation,description;
        long userId,count=1,sum,min,max; int code;
        LocalDateTime bucket,first,last;
        Aggregate(Map<String,Object> row) {
            first=sqlTime(row.get("request_time"));last=first;
            bucket=first.withMinute(0).withSecond(0).withNano(0);
            source=str(row,"source",classify(str(row,"path",""),null));
            endpoint=str(row,"endpoint",normalizeEndpoint(str(row,"path","")));
            method=str(row,"http_method","");userId=number(row,"user_id",-1);
            name=str(row,"user_name","匿名");ip=str(row,"ip","");operation=str(row,"operation_type","");description=str(row,"description","");
            code=(int)number(row,"result_code",0);sum=Math.max(0,number(row,"duration",0));min=sum;max=sum;
            try {
                // 长度编码防止分隔符出现在描述/接口中造成碰撞。
                var values=List.of(bucket.toString(),source,endpoint,method,""+userId,name,ip,operation,description,""+code);
                var raw=new StringBuilder();values.forEach(v -> raw.append(v.length()).append(':').append(v));
                key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        Aggregate add(Aggregate other) {
            count+=other.count;sum+=other.sum;min=Math.min(min,other.min);max=Math.max(max,other.max);
            if (other.first.isBefore(first)) first=other.first;
            if (other.last.isAfter(last)) last=other.last;
            return this;
        }
    }

    /** dimension: detail / endpoint / user；archive=true 查询独立历史汇总，不伪造逐条明细。 */
    public Map<String,Object> query(String dimension, boolean archive, String source, Long userId, String endpoint,
                                    String method, LocalDate start, LocalDate end, int page, int size) {
        if (!List.of("detail","endpoint","user").contains(dimension)) throw new com.sap.common.BusinessException(400,"不支持的日志维度");
        if (source != null && !source.isBlank() && !List.of("APP","WEB").contains(source)) throw new com.sap.common.BusinessException(400,"不支持的日志分类");
        size=Math.max(1,Math.min(100,size));page=Math.max(1,Math.min(1000000,page));
        var cutoff=LocalDateTime.now().minusDays(90);
        LocalDateTime from=start == null ? (archive ? LocalDateTime.of(1970,1,1,0,0) : cutoff) : start.atStartOfDay();
        if (!archive && from.isBefore(cutoff)) from=cutoff;
        LocalDateTime to=end == null ? LocalDateTime.now().plusSeconds(1) : end.plusDays(1).atStartOfDay();
        String category="COALESCE(source, CASE WHEN path LIKE '%/admin%' THEN 'WEB' WHEN path LIKE '/api/app%' OR path LIKE '/api/jw%' OR path LIKE '/api/class-schedule%' OR path LIKE '/api/academic-calendar%' OR path LIKE '/api/auth/app%' THEN 'APP' ELSE 'WEB' END)";
        String base=archive ? "SELECT * FROM sys_log_archive" : "SELECT id,request_time AS first_time,request_time AS last_time,"+category+" AS source,COALESCE(endpoint,path) AS endpoint,http_method,COALESCE(user_id,-1) AS user_id,user_name,ip,operation_type,description,COALESCE(result_code,0) AS result_code,1 AS call_count,COALESCE(duration,0) AS duration_sum,COALESCE(duration,0) AS duration_min,COALESCE(duration,0) AS duration_max,path FROM sys_log";
        String time=archive ? "bucket_time" : "first_time";
        StringBuilder where=new StringBuilder(" WHERE "+time+">=? AND "+time+"<?");
        List<Object> args=new ArrayList<>(List.of(from,to));
        if (source!=null && !source.isBlank()) {where.append(" AND source=?");args.add(source);}
        if (userId!=null) {where.append(" AND user_id=?");args.add(userId);}
        if (endpoint!=null && !endpoint.isBlank()) {where.append(" AND endpoint=?");args.add(endpoint);}
        if (method!=null && !method.isBlank()) {where.append(" AND http_method=?");args.add(method);}
        String filtered=" FROM ("+base+") logs"+where;
        String query;
        if (dimension.equals("detail")) query="SELECT *"+filtered;
        else {
            String group=dimension.equals("endpoint") ? "source,endpoint,http_method" : "user_id";
            query="SELECT "+group+",MAX(user_name) AS user_name,SUM(call_count) AS call_count,COUNT(DISTINCT NULLIF(user_id,-1)) AS user_count,COUNT(DISTINCT CONCAT(http_method,' ',endpoint)) AS endpoint_count,SUM(CASE WHEN result_code>=400 THEN call_count ELSE 0 END) AS failure_count,SUM(CASE WHEN result_code=0 THEN call_count ELSE 0 END) AS unknown_count,SUM(duration_sum) AS duration_sum,MAX(duration_max) AS duration_max,MIN(first_time) AS first_time,MAX(last_time) AS last_time"+filtered+" GROUP BY "+group;
        }
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+query+") totals",Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add((page-1)*size);
        String order=dimension.equals("detail") ? (archive ? "bucket_time DESC,group_key" : "first_time DESC,id DESC") : "call_count DESC,"+(dimension.equals("user") ? "user_id" : "source,endpoint,http_method");
        var records=jdbc.queryForList(query+" ORDER BY "+order+" LIMIT ? OFFSET ?",paged.toArray());
        return Map.of("records",records,"total",total==null?0:total,"current",page,"size",size,"retentionDays",90,"archived",archive);
    }
}
