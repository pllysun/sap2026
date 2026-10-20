package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.entity.SysLog;
import com.sap.mapper.SysLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SysLogService {
    private final SysLogMapper sysLogMapper;
    private final LogDailyStatsService dailyStats;
    private final ApiLogExclusionService exclusions;

    public Page<SysLog> pageQuery(int current,int size,String operationType,String httpMethod) {
        var wrapper=new LambdaQueryWrapper<SysLog>();
        wrapper.ge(SysLog::getRequestTime,java.time.LocalDateTime.now().minusDays(90));
        if(operationType!=null&&!operationType.isBlank())wrapper.eq(SysLog::getOperationType,operationType);
        if(httpMethod!=null&&!httpMethod.isBlank())wrapper.eq(SysLog::getHttpMethod,httpMethod);
        var filter=exclusions.sql("COALESCE(endpoint,path)","http_method");
        if(!filter.text().isEmpty()) {
            String sql=filter.text().substring(5);
            for(int i=0;i<filter.args().size();i++)sql=sql.replaceFirst("\\?","{"+i+"}");
            wrapper.apply(sql,filter.args().toArray());
        }
        wrapper.orderByDesc(SysLog::getRequestTime).orderByDesc(SysLog::getId);
        return sysLogMapper.selectPage(new Page<>(Math.max(1,current),Math.max(1,Math.min(100,size))),wrapper);
    }
    private int safeDays(int days){return Math.max(1,Math.min(366,days));}
    public Map<String,Object> getStats(int days) {
        int n=safeDays(days);LocalDate end=LocalDate.now(),start=end.minusDays(n-1L);
        Map<String,Long> operations=new TreeMap<>(),methods=new TreeMap<>(),dates=new TreeMap<>();
        for(int i=0;i<n;i++)dates.put(start.plusDays(i).toString(),0L);
        long total=0;
        for(var row:dailyStats.counts(start,end.plusDays(1))) {
            long count=((Number)row.get("call_count")).longValue();total+=count;
            operations.merge(String.valueOf(row.get("operation_type")),count,Long::sum);
            methods.merge(String.valueOf(row.get("http_method")),count,Long::sum);
            dates.merge(String.valueOf(row.get("stat_date")),count,Long::sum);
        }
        return Map.of("total",total,"byOperationType",operations,"byHttpMethod",methods,"dailyTrend",dates);
    }
    public List<List<Object>> getDailyCalendar(int days) {
        int n=safeDays(days);LocalDate end=LocalDate.now();return calendar(end.minusDays(n-1L),end.plusDays(1));
    }
    public List<List<Object>> getCalendarYear(int year) {
        if(year<2000||year>LocalDate.now().getYear())throw new com.sap.common.BusinessException(400,"年份超出范围");
        LocalDate start=LocalDate.of(year,1,1);return calendar(start,start.plusYears(1));
    }
    private List<List<Object>> calendar(LocalDate start,LocalDate end) {
        Map<String,Long> counts=new TreeMap<>();
        for(var row:dailyStats.counts(start,end))counts.merge(String.valueOf(row.get("stat_date")),((Number)row.get("call_count")).longValue(),Long::sum);
        List<List<Object>> result=new ArrayList<>();
        for(var date=start;date.isBefore(end);date=date.plusDays(1))result.add(List.of(date.toString(),counts.getOrDefault(date.toString(),0L)));
        return result;
    }
    public long totalCount(){return dailyStats.total();}
}
