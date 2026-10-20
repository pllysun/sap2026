package com.sap.service;

import com.sap.entity.SysLog;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;

/** 按日、接口、方法汇总，支持任意过滤规则下的精确热力图，避免每次扫描几十万条明细。 */
@Service
@DependsOn("logAnalyticsService")
public class LogDailyStatsService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ApiLogExclusionService exclusions;
    public LogDailyStatsService(JdbcTemplate db,PlatformTransactionManager manager,ApiLogExclusionService exclusions) {
        this.db=db;this.tx=new TransactionTemplate(manager);this.exclusions=exclusions;
    }
    @PostConstruct public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS sys_log_daily_state (id INT PRIMARY KEY,ready INT NOT NULL DEFAULT 0)");
        try {db.update("INSERT INTO sys_log_daily_state(id,ready) VALUES(1,0)");}
        catch(org.springframework.dao.DuplicateKeyException ignored) {}
        db.execute("CREATE TABLE IF NOT EXISTS sys_log_daily_endpoint (stat_date DATE NOT NULL,endpoint VARCHAR(255) NOT NULL,http_method VARCHAR(10) NOT NULL,operation_type VARCHAR(10) NOT NULL,call_count BIGINT NOT NULL,PRIMARY KEY(stat_date,endpoint,http_method,operation_type))");
        tx.executeWithoutResult(status -> {
            int ready=db.queryForObject("SELECT ready FROM sys_log_daily_state WHERE id=1 FOR UPDATE",Integer.class);
            if(ready==1)return;
            // 归档与热明细的交接也使用此锁，迁移时不会重复或遗漏交界批次。
            db.queryForObject("SELECT id FROM sys_log_archive_lock WHERE id=1 FOR UPDATE",Integer.class);
            db.update("""
                INSERT INTO sys_log_daily_endpoint(stat_date,endpoint,http_method,operation_type,call_count)
                SELECT stat_date,endpoint,http_method,operation_type,SUM(call_count) FROM (
                    SELECT CAST(request_time AS DATE) AS stat_date,COALESCE(endpoint,path,'未知接口') AS endpoint,
                        COALESCE(http_method,'') AS http_method,COALESCE(operation_type,'查询') AS operation_type,1 AS call_count FROM sys_log
                    UNION ALL
                    SELECT CAST(bucket_time AS DATE),endpoint,http_method,COALESCE(operation_type,'查询'),call_count FROM sys_log_archive
                ) counts GROUP BY stat_date,endpoint,http_method,operation_type
                """);
            db.update("UPDATE sys_log_daily_state SET ready=1 WHERE id=1");
        });
    }
    public void record(SysLog log) {
        if(exclusions.excludes(log.getEndpoint(),log.getHttpMethod()))return;
        db.update("""
            INSERT INTO sys_log_daily_endpoint(stat_date,endpoint,http_method,operation_type,call_count) VALUES(?,?,?,?,1)
            ON DUPLICATE KEY UPDATE call_count=call_count+1
            """, log.getRequestTime().toLocalDate(),log.getEndpoint(),log.getHttpMethod(),log.getOperationType());
    }
    public List<Map<String,Object>> counts(LocalDate start,LocalDate end) {
        var filter=exclusions.sql("endpoint","http_method");
        var args=new ArrayList<Object>(List.of(start,end));args.addAll(filter.args());
        return db.queryForList("SELECT stat_date,operation_type,http_method,SUM(call_count) AS call_count FROM sys_log_daily_endpoint WHERE stat_date>=? AND stat_date<?"+
                filter.text()+" GROUP BY stat_date,operation_type,http_method ORDER BY stat_date",args.toArray());
    }
    public long total() {
        var filter=exclusions.sql("endpoint","http_method");
        Long count=db.queryForObject("SELECT COALESCE(SUM(call_count),0) FROM sys_log_daily_endpoint WHERE 1=1"+filter.text(),Long.class,filter.args().toArray());
        return count==null?0:count;
    }
}
