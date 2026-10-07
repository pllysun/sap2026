package com.sap.service.judger;

import com.sap.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Only explicitly selected public metadata is returned; source, snapshots and test outputs stay private. */
@Service @RequiredArgsConstructor
public class OjInsightsService {
    private final JdbcTemplate db;
    private final OjService oj;
    private static final String NAME="COALESCE(NULLIF(u.nickname,''),CONCAT('用户 ',u.id))";
    private static final String TIME="CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.result_json,'$.timeMs')),'0') AS UNSIGNED)";
    private static final String MEMORY="CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.result_json,'$.memoryBytes')),'0') AS UNSIGNED)";
    private static final String ELIGIBLE=" s.kind='SUBMIT' AND s.status='AC' AND s.revision=p.revision AND p.status='PUBLISHED' AND p.validation_signature=? AND JSON_UNQUOTE(JSON_EXTRACT(s.snapshot_json,'$.signature'))=p.validation_signature ";
    private String eligible(){return ELIGIBLE+" AND s.problem_set_id IS NULL ";}

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Map<String,Object> leaderboard() {
        String signature=oj.currentSignature();
        Map<String,Object> totals=db.queryForMap("SELECT COUNT(*) AS total,COUNT(CASE WHEN difficulty='EASY' THEN 1 END) AS easy,COUNT(CASE WHEN difficulty='MEDIUM' THEN 1 END) AS medium,COUNT(CASE WHEN difficulty='HARD' THEN 1 END) AS hard FROM oj_problem WHERE status='PUBLISHED' AND validation_signature=?",signature);
        List<Map<String,Object>> rows=db.queryForList("SELECT u.name AS name,u.student_id AS studentId,"+NAME+" AS nickname,COUNT(DISTINCT p.id) AS acCount,COUNT(DISTINCT CASE WHEN p.difficulty='EASY' THEN p.id END) AS easyAc,COUNT(DISTINCT CASE WHEN p.difficulty='MEDIUM' THEN p.id END) AS mediumAc,COUNT(DISTINCT CASE WHEN p.difficulty='HARD' THEN p.id END) AS hardAc FROM oj_submission s JOIN sys_user u ON u.id=s.user_id AND u.deleted=0 AND u.status=1 LEFT JOIN oj_problem p ON p.id=s.problem_id AND "+eligible()+" WHERE s.kind='SUBMIT' AND s.problem_set_id IS NULL GROUP BY u.id,u.name,u.nickname,u.student_id ORDER BY acCount DESC,MIN(s.created_at),u.id LIMIT 100",signature);
        return Map.of("records",rows,"totals",totals);
    }
    private List<Map<String,Object>> accepted(Long problem, String language, String mode) {
        oj.detail(problem,false);
        return db.queryForList("SELECT s.id,s.node_id AS nodeId,s.user_id AS userId,"+NAME+" AS nickname,u.name AS name,u.student_id AS studentId,"+TIME+" AS timeMs,"+MEMORY+" AS memoryBytes FROM oj_submission s JOIN oj_problem p ON p.id=s.problem_id JOIN sys_user u ON u.id=s.user_id AND u.deleted=0 AND u.status=1 WHERE "+eligible()+" AND s.problem_id=? AND s.language=? AND s.mode=? ORDER BY timeMs,memoryBytes,s.id",oj.currentSignature(),problem,language,mode);
    }
    public Map<String,Object> ranking(Long problem,String language,String mode) {
        List<Map<String,Object>> all=accepted(problem,language,mode);
        Set<Object> users=new HashSet<>();List<Map<String,Object>> rows=new ArrayList<>();
        for (Map<String,Object> item:all) if(users.add(item.get("userId"))) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("nickname",item.get("nickname"));row.put("name",item.get("name"));row.put("studentId",item.get("studentId"));row.put("timeMs",item.get("timeMs"));row.put("memoryBytes",item.get("memoryBytes"));rows.add(row);
            if(rows.size()==100) break;
        }
        return Map.of("records",rows,"participants",all.stream().map(r->r.get("userId")).distinct().count());
    }
    public Map<String,Object> performance(Long id,Long user) {
        Map<String,Object> job=oj.job(id,user,false); // Ownership check must precede every metric query.
        if(!"AC".equals(job.get("status"))) throw new BusinessException(400,"通过后可查看性能统计");
        Map<?,?> result=(Map<?,?>)job.get("result");
        long time=number(result.get("timeMs")),memory=number(result.get("memoryBytes"));
        if(job.get("problemSetId")!=null)return Map.of("comparable",false,"sampleCount",0,"time",distribution(List.of(time),time),"memory",distribution(List.of(memory),memory),"note","本次题单提交的运行指标；不使用执行耗时或内存作为题单排名依据。");
        List<Map<String,Object>> all=accepted(((Number)job.get("problemId")).longValue(),(String)job.get("language"),(String)job.get("mode")).stream().filter(r->Objects.equals(r.get("nodeId"),job.get("nodeId"))).toList();
        boolean comparable="SUBMIT".equals(job.get("kind")) && all.stream().anyMatch(r->number(r.get("id"))==id);
        Map<String,Object> view=new LinkedHashMap<>();view.put("comparable",comparable);
        view.put("sampleCount",comparable?all.size():0);view.put("time",distribution(comparable?all.stream().map(r->number(r.get("timeMs"))).toList():List.of(time),time));
        view.put("memory",distribution(comparable?all.stream().map(r->number(r.get("memoryBytes"))).toList():List.of(memory),memory));
        view.put("note",comparable?"相同题目版本、语言、模式、运行配置与判题节点的正式 AC 提交；耗时为单用例峰值。":"本次运行指标；样例运行或旧版本记录不参与正式提交比较。");
        return view;
    }
    static long number(Object value) {return value instanceof Number n?n.longValue():0;}
    public static Map<String,Object> distribution(List<Long> values,long current) {
        long min=values.stream().mapToLong(Long::longValue).min().orElse(current),max=values.stream().mapToLong(Long::longValue).max().orElse(current);
        double width=Math.max(1,(max-min+1d)/8);long[] counts=new long[8];
        for(long v:values) counts[Math.min(7,(int)((v-min)/width))]++;
        List<Map<String,Object>> bins=new ArrayList<>();for(int i=0;i<8;i++)bins.add(Map.of("from",min+i*width,"to",min+(i+1)*width,"count",counts[i],"current",Math.min(7,(int)((current-min)/width))==i));
        double percentile=values.isEmpty()?0:100d*values.stream().filter(v->v>current).count()/values.size();
        return Map.of("value",current,"beatsPercent",Math.round(percentile*10)/10d,"bins",bins);
    }
    private static final String TASK_COLUMNS="SELECT s.id,s.node_name AS nodeName,s.attempt,p.title AS problemTitle,u.name AS name,u.nickname AS nickname,u.student_id AS studentId,s.kind,s.language,s.mode,s.status,s.passed_cases AS passedCases,s.total_cases AS totalCases,s.created_at AS createdAt,s.started_at AS startedAt,s.finished_at AS finishedAt,TIMESTAMPDIFF(SECOND,s.created_at,COALESCE(s.started_at,NOW())) AS waitSeconds,TIMESTAMPDIFF(SECOND,s.started_at,COALESCE(s.finished_at,NOW())) AS durationSeconds,"+TIME+" AS timeMs,"+MEMORY+" AS memoryBytes FROM oj_submission s LEFT JOIN oj_problem p ON p.id=s.problem_id LEFT JOIN sys_user u ON u.id=s.user_id ";
    public Map<String,Object> monitor(int page) {
        page=Math.max(1,Math.min(100000,page));
        List<Map<String,Object>> queue=db.queryForList(TASK_COLUMNS+"WHERE s.status IN ('QUEUED','RUNNING') ORDER BY CASE WHEN s.status='RUNNING' THEN 0 ELSE 1 END,s.kind,s.id LIMIT 50");
        List<Map<String,Object>> logs=db.queryForList(TASK_COLUMNS+"ORDER BY s.id DESC LIMIT 20 OFFSET ?",(page-1)*20);
        return Map.of("health",oj.health(),"queue",queue,"records",logs,"total",db.queryForObject("SELECT COUNT(*) FROM oj_submission",Long.class),"page",page,"capacity",oj.queueLimit(),"workers",oj.health().getOrDefault("capacity",0));
    }
    public Map<String,Object> monitorJob(Long id) {
        List<Map<String,Object>> rows=db.queryForList(TASK_COLUMNS+"WHERE s.id=?",id);
        if(rows.isEmpty()) throw new BusinessException(404,"运行记录不存在");
        Map<String,Object> task=rows.getFirst();
        List<Map<String,Object>> events=db.queryForList("SELECT id,event,level,status,node_name AS nodeName,created_at AS createdAt FROM oj_runtime_log WHERE job_id=? ORDER BY created_at,id",id);
        if(events.isEmpty()) {
            events=new ArrayList<>();
            events.add(legacyEvent("QUEUED",task.get("createdAt"),"QUEUED"));
            if(task.get("startedAt")!=null) events.add(legacyEvent("STARTED",task.get("startedAt"),"RUNNING"));
            if(task.get("finishedAt")!=null) events.add(legacyEvent("FINISHED",task.get("finishedAt"),(String)task.get("status")));
        }
        return Map.of("task",task,"timeline",events);
    }
    private Map<String,Object> legacyEvent(String event,Object date,String status) {
        Map<String,Object> item=new LinkedHashMap<>();item.put("id",event);item.put("event",event);item.put("createdAt",date);item.put("status",status);item.put("level","SYSTEM_ERROR".equals(status)?"ERROR":"INFO");item.put("historical",true);return item;
    }
}
