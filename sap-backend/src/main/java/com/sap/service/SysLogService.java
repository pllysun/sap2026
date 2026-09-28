package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.entity.LogStats;
import com.sap.entity.SysLog;
import com.sap.mapper.LogStatsMapper;
import com.sap.mapper.SysLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysLogService {

    @Autowired
    private SysLogMapper sysLogMapper;
    @Autowired
    private LogStatsMapper logStatsMapper;

    /**
     * 分页查询日志
     */
    public Page<SysLog> pageQuery(int current, int size, String operationType, String httpMethod) {
        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(SysLog::getRequestTime, java.time.LocalDateTime.now().minusDays(90));
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(SysLog::getOperationType, operationType);
        }
        if (httpMethod != null && !httpMethod.isEmpty()) {
            wrapper.eq(SysLog::getHttpMethod, httpMethod);
        }
        wrapper.orderByDesc(SysLog::getRequestTime);
        return sysLogMapper.selectPage(new Page<>(Math.max(1,current), Math.max(1,Math.min(100,size))), wrapper);
    }

    /**
     * 获取统计数据
     */
    public Map<String, Object> getStats(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<LogStats> statsList = logStatsMapper.selectList(
                new LambdaQueryWrapper<LogStats>()
                        .ge(LogStats::getStatDate, startDate)
                        .le(LogStats::getStatDate, endDate)
        );

        // 按操作类型汇总
        Map<String, Integer> byOperationType = new HashMap<>();
        Map<String, Integer> byHttpMethod = new HashMap<>();
        Map<String, Integer> byDate = new TreeMap<>();

        // 初始化日期
        for (int i = 0; i < days; i++) {
            byDate.put(startDate.plusDays(i).toString(), 0);
        }

        for (LogStats s : statsList) {
            byOperationType.merge(s.getOperationType(), s.getCount(), Integer::sum);
            byHttpMethod.merge(s.getHttpMethod(), s.getCount(), Integer::sum);
            String dateKey = s.getStatDate().toString();
            byDate.merge(dateKey, s.getCount(), Integer::sum);
        }

        int total = statsList.stream().mapToInt(LogStats::getCount).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("byOperationType", byOperationType);
        result.put("byHttpMethod", byHttpMethod);
        result.put("dailyTrend", byDate);
        return result;
    }

    /**
     * 获取日历热力图数据：返回近 N 天的每日操作总数
     * 格式：List of [date_string, count]
     */
    public List<List<Object>> getDailyCalendar(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<LogStats> statsList = logStatsMapper.selectList(
                new LambdaQueryWrapper<LogStats>()
                        .ge(LogStats::getStatDate, startDate)
                        .le(LogStats::getStatDate, endDate)
        );

        // 按日期汇总
        Map<String, Integer> dateMap = new TreeMap<>();
        for (int i = 0; i < days; i++) {
            dateMap.put(startDate.plusDays(i).toString(), 0);
        }
        for (LogStats s : statsList) {
            String key = s.getStatDate().toString();
            dateMap.merge(key, s.getCount(), Integer::sum);
        }

        List<List<Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dateMap.entrySet()) {
            result.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public long totalCount() {
        return sysLogMapper.selectCount(null);
    }

    public List<List<Object>> getCalendarYear(int year) {
        if (year < 2000 || year > LocalDate.now().getYear()) throw new com.sap.common.BusinessException(400,"年份超出范围");
        LocalDate start=LocalDate.of(year,1,1), end=start.plusYears(1);
        var rows=logStatsMapper.selectList(new LambdaQueryWrapper<LogStats>()
            .ge(LogStats::getStatDate,start).lt(LogStats::getStatDate,end));
        Map<LocalDate,Long> counts=new TreeMap<>();
        rows.forEach(row -> counts.merge(row.getStatDate(),row.getCount().longValue(),Long::sum));
        List<List<Object>> result=new ArrayList<>();
        for (var date=start;date.isBefore(end);date=date.plusDays(1)) result.add(Arrays.asList(date.toString(),counts.getOrDefault(date,0L)));
        return result;
    }
}
