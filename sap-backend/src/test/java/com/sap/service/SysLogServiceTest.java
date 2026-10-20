package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.mapper.SysLogMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SysLogServiceTest extends BaseUnitTest {
    SysLogMapper mapper=mock(SysLogMapper.class);
    LogDailyStatsService daily=mock(LogDailyStatsService.class);
    ApiLogExclusionService rules=mock(ApiLogExclusionService.class);
    SysLogService service=new SysLogService(mapper,daily,rules);
    Map<String,Object> row(LocalDate day,String op,String method,long count){return Map.of("stat_date",day,"operation_type",op,"http_method",method,"call_count",count);}
    @Test void groupsFilteredEndpointCountsWithoutLosingLargeTotals(){
        var today=LocalDate.now();when(daily.counts(any(),any())).thenReturn(List.of(row(today,"查询","GET",3_000_000_000L),row(today,"新增","POST",2L),row(today.minusDays(1),"查询","GET",5L)));
        var stats=service.getStats(7);assertEquals(3_000_000_007L,stats.get("total"));
        assertEquals(Map.of("查询",3_000_000_005L,"新增",2L),stats.get("byOperationType"));
        assertEquals(Map.of("GET",3_000_000_005L,"POST",2L),stats.get("byHttpMethod"));
        assertEquals(7,((Map<?,?>)stats.get("dailyTrend")).size());
        assertEquals(3_000_000_002L,((Map<?,?>)stats.get("dailyTrend")).get(today.toString()));
    }
    @Test void emptyCalendarIncludesZeroDaysAndBoundsUntrustedWindow(){
        when(daily.counts(any(),any())).thenReturn(List.of());
        assertEquals(0L,service.getStats(0).get("total"));
        var rows=service.getDailyCalendar(10000);assertEquals(366,rows.size());assertTrue(rows.stream().allMatch(row->row.get(1).equals(0L)));
    }
    @Test void calendarYearIsCompleteIncludingLeapDays(){when(daily.counts(any(),any())).thenReturn(List.of());assertEquals(366,service.getCalendarYear(2024).size());assertThrows(com.sap.common.BusinessException.class,()->service.getCalendarYear(1999));}
    @Test void allTimeTotalUsesPersistentFilteredAggregate(){when(daily.total()).thenReturn(89L);assertEquals(89L,service.totalCount());verifyNoInteractions(mapper);}
    @Test void legacyPaginationAlsoAppliesMemoryRules(){
        when(rules.sql(anyString(),anyString())).thenReturn(new ApiLogExclusionService.Sql(" AND NOT (REGEXP_LIKE(COALESCE(endpoint,path,''),?))",List.of("^/api/ping$")));
        when(mapper.selectPage(any(),any())).thenAnswer(call->call.getArgument(0));
        var page=service.pageQuery(0,1000,null,null);assertEquals(1,page.getCurrent());assertEquals(100,page.getSize());
    }
}
