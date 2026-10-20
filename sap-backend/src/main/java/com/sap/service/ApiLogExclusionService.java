package com.sap.service;

import com.sap.common.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerMapping;

import java.util.*;
import java.util.regex.Pattern;

/** 小型持久化规则集；请求路径仅匹配内存快照，不访问数据库。 */
@Service
public class ApiLogExclusionService {
    public record Rule(String path, String method) {}
    private record Compiled(Rule rule, String expression, Pattern regex) {}
    public record Sql(String text, List<Object> args) {}
    private static final Set<String> METHODS = Set.of("*","GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS");
    private static final List<Compiled> DEFAULTS = List.of(compile(new Rule("/api/ping","*")),
            compile(new Rule("/api/oj-nodes/{id}/heartbeat","*")));
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private volatile List<Compiled> snapshot = DEFAULTS;

    public ApiLogExclusionService(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db=db; this.tx=new TransactionTemplate(manager);
    }

    @PostConstruct public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS sys_api_log_filter_state (id INT PRIMARY KEY)");
        db.execute("CREATE TABLE IF NOT EXISTS sys_api_log_filter (path VARCHAR(191) NOT NULL,http_method VARCHAR(10) NOT NULL,PRIMARY KEY(path,http_method))");
        // 状态行区分首次部署与管理员主动清空，重启不能重新加回已移除的规则。
        tx.executeWithoutResult(status -> {
            if(db.queryForObject("SELECT COUNT(*) FROM sys_api_log_filter_state WHERE id=1",Long.class)==0) {
                db.update("INSERT INTO sys_api_log_filter_state(id) VALUES(1)");
                for(var item:DEFAULTS) db.update("INSERT INTO sys_api_log_filter(path,http_method) VALUES(?,?)",item.rule.path,item.rule.method);
            }
        });
        reload();
    }

    private void reload() {
        snapshot=List.copyOf(db.query("SELECT path,http_method FROM sys_api_log_filter ORDER BY path,http_method",
                (row,n)->compile(new Rule(row.getString(1),row.getString(2)))));
    }
    public List<Rule> rules() {return snapshot.stream().map(Compiled::rule).toList();}
    public synchronized List<Rule> replace(List<Rule> rules) {
        if(rules==null||rules.size()>50)throw new BusinessException(400,"最多配置 50 条过滤规则");
        var compiled=rules.stream().map(ApiLogExclusionService::compile).toList();
        var distinct=compiled.stream().map(Compiled::rule).distinct().toList();
        if(distinct.size()!=compiled.size())throw new BusinessException(400,"过滤规则重复");
        tx.executeWithoutResult(status -> {
            db.queryForObject("SELECT id FROM sys_api_log_filter_state WHERE id=1 FOR UPDATE",Integer.class);
            db.update("DELETE FROM sys_api_log_filter");
            distinct.forEach(rule->db.update("INSERT INTO sys_api_log_filter(path,http_method) VALUES(?,?)",rule.path,rule.method));
        });
        // 提交成功后一次性发布，失败时继续使用原快照。
        snapshot=List.copyOf(compiled);
        return rules();
    }
    private static Compiled compile(Rule rule) {
        if(rule==null||rule.path==null||rule.method==null)throw new BusinessException(400,"请填写接口路径与方法");
        String path=rule.path.trim(),method=rule.method.trim().toUpperCase(Locale.ROOT);
        if(path.length()>191||!path.matches("/api/(?:[A-Za-z0-9_.-]+|\\{[A-Za-z][A-Za-z0-9_]*})(?:/(?:[A-Za-z0-9_.-]+|\\{[A-Za-z][A-Za-z0-9_]*}))*")
                ||path.contains("..")||!METHODS.contains(method))
            throw new BusinessException(400,"请填写完整的 /api/ 接口路径，动态段使用 {id}；不支持通配符和正则表达式");
        StringBuilder regex=new StringBuilder("(?-i)^");
        for(String part:path.substring(1).split("/"))regex.append('/').append(part.startsWith("{")?"[^/]+":part.replace(".","\\."));
        regex.append('$');
        return new Compiled(new Rule(path,method),regex.toString(),Pattern.compile(regex.toString()));
    }
    public boolean excludes(HttpServletRequest request) {
        String path=request.getRequestURI(),context=request.getContextPath();
        if(context!=null&&!context.isEmpty()&&path!=null&&path.startsWith(context+"/"))path=path.substring(context.length());
        Object matched=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return excludes(path,request.getMethod())||(matched!=null&&excludes(matched.toString(),request.getMethod()));
    }
    public boolean excludes(String endpoint,String method) {
        if(endpoint==null)return false;
        for(var item:snapshot)if((item.rule.method.equals("*")||item.rule.method.equalsIgnoreCase(method))&&item.regex.matcher(endpoint).matches())return true;
        return false;
    }
    /** 同一规则用于历史数据读取；保留原记录，取消过滤后也可重新查看已有历史。列名均为服务端常量。 */
    public Sql sql(String endpointColumn,String methodColumn) {
        StringBuilder where=new StringBuilder();List<Object> args=new ArrayList<>();
        for(var item:snapshot) {
            where.append(" AND NOT (REGEXP_LIKE(COALESCE(").append(endpointColumn).append(",''),?)");
            args.add(item.expression);
            if(!item.rule.method.equals("*")){where.append(" AND ").append(methodColumn).append("=?");args.add(item.rule.method);}
            where.append(')');
        }
        return new Sql(where.toString(),List.copyOf(args));
    }
}
