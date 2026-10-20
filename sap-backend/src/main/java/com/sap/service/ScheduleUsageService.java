package com.sap.service;

import com.sap.common.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** 课表前台展示与成功同步分开计量，不用请求次数推断实际使用。 */
@Service
public class ScheduleUsageService {
    private static final List<String> MODES = List.of("JW", "WEB", "CLASS");
    private static final Map<String, String> LABELS = Map.of("JW", "教务课表", "WEB", "Web 课表", "CLASS", "班级课表");
    private final JdbcTemplate db;

    public ScheduleUsageService(JdbcTemplate db) { this.db = db; }

    @PostConstruct
    public void initialize() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS stat_schedule_usage (
                stat_date DATE NOT NULL, user_id BIGINT NOT NULL,
                mode VARCHAR(8) NOT NULL, event_type VARCHAR(8) NOT NULL,
                received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (stat_date,user_id,mode,event_type)
            )
            """);
    }

    public record Event(LocalDate date, String mode, String event) {}
    public record Report(List<Event> events) {}
    public record ModeUsage(String mode, String label, long activeUsers, long syncUsers) {}
    public record DayUsage(LocalDate date, long activeUsers, long syncUsers) {}
    public record Summary(int days, long activeUsers, long syncUsers, LocalDate trackingSince,
                          List<ModeUsage> modes, List<DayUsage> daily) {}

    /** 身份只取服务端登录态；Web 导入在端上发生，其余同步必须以服务端成功日志为准。 */
    @Transactional
    public void report(long userId, Report report) {
        if (userId <= 0) throw new BusinessException(401, "请先登录");
        if (report == null || report.events() == null || report.events().isEmpty() || report.events().size() > 120)
            throw new BusinessException(400, "每次上报 1 至 120 条课表使用记录");
        LocalDate today = LocalDate.now();
        for (Event event : report.events()) {
            if (event == null || event.date() == null || event.date().isAfter(today)
                    || event.date().isBefore(today.minusDays(89)) || event.mode() == null || !MODES.contains(event.mode())
                    || !("VIEW".equals(event.event()) || "SYNC".equals(event.event()) && "WEB".equals(event.mode())))
                throw new BusinessException(400, "课表使用记录无效");
        }
        for (Event event : new LinkedHashSet<>(report.events())) {
            // 主键保证多设备、并发重试和离线补报均不会重复增加人数。
            db.update("""
                INSERT INTO stat_schedule_usage(stat_date,user_id,mode,event_type) VALUES(?,?,?,?)
                ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)
                """, event.date(), userId, event.mode(), event.event());
        }
    }

    public long activeUsers(int days) {
        LocalDate today = LocalDate.now();
        Long count = db.queryForObject("""
            SELECT COUNT(DISTINCT user_id) FROM stat_schedule_usage
            WHERE stat_date>=? AND stat_date<=? AND user_id>0 AND event_type='VIEW'
            """, Long.class, today.minusDays(safeDays(days) - 1L), today);
        return count == null ? 0 : count;
    }

    public Summary summary(int days) {
        int n = safeDays(days);
        LocalDate today = LocalDate.now(), start = today.minusDays(n - 1L);
        // 已有两代 App 的成功同步均可核验；归档与热日志交界处用 UNION 去重。
        // 这里绝不把成功同步反推为前台展示，后台同步不等于用户实际打开课表。
        String successes = """
            SELECT CAST(request_time AS DATE) AS stat_date,user_id,
                CASE WHEN endpoint='/api/jw/schedule' THEN 'JW' ELSE 'CLASS' END AS mode,'SYNC' AS event_type
            FROM sys_log WHERE request_time>=? AND request_time<? AND user_id>0 AND result_code=200
                AND ((http_method='GET' AND endpoint IN ('/api/jw/schedule','/api/class-schedule/schedule'))
                     OR (http_method='POST' AND endpoint='/api/class-schedule/sync'))
            UNION
            SELECT CAST(bucket_time AS DATE) AS stat_date,user_id,
                CASE WHEN endpoint='/api/jw/schedule' THEN 'JW' ELSE 'CLASS' END AS mode,'SYNC' AS event_type
            FROM sys_log_archive WHERE bucket_time>=? AND bucket_time<? AND user_id>0 AND result_code=200
                AND ((http_method='GET' AND endpoint IN ('/api/jw/schedule','/api/class-schedule/schedule'))
                     OR (http_method='POST' AND endpoint='/api/class-schedule/sync'))
            """;
        Set<Long> active = new HashSet<>(), synced = new HashSet<>();
        Map<String, Set<Long>> modeActive = new HashMap<>(), modeSync = new HashMap<>();
        Map<LocalDate, Set<Long>> dayActive = new LinkedHashMap<>(), daySync = new LinkedHashMap<>();
        MODES.forEach(mode -> { modeActive.put(mode, new HashSet<>()); modeSync.put(mode, new HashSet<>()); });
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            dayActive.put(day, new HashSet<>());
            daySync.put(day, new HashSet<>());
        }
        db.query("""
            SELECT stat_date,user_id,mode,event_type FROM stat_schedule_usage
            WHERE stat_date>=? AND stat_date<=? AND user_id>0
            UNION
            """ + successes, row -> {
            LocalDate day = row.getDate("stat_date").toLocalDate();
            long user = row.getLong("user_id");
            String mode = row.getString("mode"), event = row.getString("event_type");
            if (!MODES.contains(mode) || !dayActive.containsKey(day)) return;
            if ("VIEW".equals(event)) {
                active.add(user); modeActive.get(mode).add(user); dayActive.get(day).add(user);
            } else if ("SYNC".equals(event)) {
                synced.add(user); modeSync.get(mode).add(user); daySync.get(day).add(user);
            }
        }, start, today, start.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                start.atStartOfDay(), today.plusDays(1).atStartOfDay());
        Date first = db.queryForObject("SELECT MIN(stat_date) FROM stat_schedule_usage WHERE event_type='VIEW'", Date.class);
        List<ModeUsage> modes = MODES.stream().map(mode -> new ModeUsage(mode, LABELS.get(mode),
                modeActive.get(mode).size(), modeSync.get(mode).size())).toList();
        List<DayUsage> daily = dayActive.keySet().stream().map(day -> new DayUsage(day,
                dayActive.get(day).size(), daySync.get(day).size())).toList();
        return new Summary(n, active.size(), synced.size(), first == null ? null : first.toLocalDate(), modes, daily);
    }

    private static int safeDays(int days) { return days == 30 || days == 90 ? days : 7; }
}
