package com.sap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.sap.service.mail.MailQueue;

import java.time.LocalDate;
import java.util.*;

/** 首页全平台概览；与按届展示的任职、财务统计分开。只返回摘要，不返回凭据或邮件正文。 */
@Service
@RequiredArgsConstructor
public class DashboardInsightsService {
    private final JdbcTemplate db;
    private final ApiLogExclusionService exclusions;
    private final StatsService stats;
    private final ScheduleUsageService schedules;
    private final MailQueue mail;
    private final JoinService joins;

    public Map<String,Object> overview(boolean leader) {
        LocalDate today=LocalDate.now(),start=today.minusDays(13);
        var result=new LinkedHashMap<String,Object>();
        result.put("generatedAt",java.time.LocalDateTime.now());
        result.put("users",db.queryForMap("""
            SELECT COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN gender=1 THEN 1 ELSE 0 END),0) AS male,
                COALESCE(SUM(CASE WHEN gender=0 THEN 1 ELSE 0 END),0) AS female,
                COALESCE(SUM(CASE WHEN gender IS NULL OR gender NOT IN (0,1) THEN 1 ELSE 0 END),0) AS `unknown`,
                COALESCE(SUM(CASE WHEN status=0 THEN 1 ELSE 0 END),0) AS disabled,
                COALESCE(SUM(CASE WHEN created_at>=? THEN 1 ELSE 0 END),0) AS today,
                COALESCE(SUM(CASE WHEN created_at>=? THEN 1 ELSE 0 END),0) AS recent
            FROM sys_user WHERE deleted=0
            """,today.atStartOfDay(),today.minusDays(6).atStartOfDay()));
        result.put("memberAccounts",db.queryForObject("SELECT COUNT(*) FROM sys_user u WHERE u.deleted=0 AND EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id=u.id AND r.role_code<=3)",Long.class));
        result.put("recentUsers",db.queryForList("SELECT id,name,student_id AS studentId,qq,gender,created_at AS createdAt FROM sys_user WHERE deleted=0 ORDER BY created_at DESC,id DESC LIMIT 8"));
        var registrations=db.queryForList("SELECT CAST(created_at AS DATE) AS stat_date,COUNT(*) AS total FROM sys_user WHERE deleted=0 AND created_at>=? GROUP BY CAST(created_at AS DATE) ORDER BY stat_date",start.atStartOfDay());
        Map<String,Long> counts=new HashMap<>();
        registrations.forEach(row->counts.put(String.valueOf(row.get("stat_date")),((Number)row.get("total")).longValue()));
        List<Map<String,Object>> trend=new ArrayList<>();
        for(int i=0;i<14;i++){String date=start.plusDays(i).toString();trend.add(Map.of("date",date,"count",counts.getOrDefault(date,0L)));}
        result.put("registrationTrend",trend);
        result.put("join",Map.of("enabled",joins.isJoinEnabled(),"states",db.queryForList("SELECT status,COUNT(*) AS total FROM join_application GROUP BY status")));
        var filter=exclusions.sql("COALESCE(endpoint,path)","http_method");
        var args=new ArrayList<Object>(List.of(today.atStartOfDay(),today.plusDays(1).atStartOfDay()));args.addAll(filter.args());
        result.put("logs",db.queryForMap("SELECT COUNT(*) AS requests,COALESCE(SUM(CASE WHEN result_code>=400 THEN 1 ELSE 0 END),0) AS failures,COUNT(DISTINCT CASE WHEN user_id>0 THEN user_id ELSE NULL END) AS users FROM sys_log WHERE request_time>=? AND request_time<?"+filter.text(),args.toArray()));
        var schedule=schedules.summary(7);
        result.put("schedule",Map.of("days",7,"activeUsers",schedule.activeUsers(),"syncUsers",schedule.syncUsers(),"modes",schedule.modes(),
                "openFeedback",db.queryForObject("SELECT COUNT(*) FROM app_feedback_issue WHERE deleted=0 AND status<>'CLOSED'",Long.class)));
        // 保持邮件、流量模块原有的 0/1 权限，不通过首页绕过模块权限。
        if(leader){result.put("mail",mail.status());result.put("traffic",stats.overview(7));}
        result.put("leader",leader);
        return result;
    }
}
