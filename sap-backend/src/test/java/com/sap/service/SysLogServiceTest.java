package com.sap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.entity.LogStats;
import com.sap.entity.SysLog;
import com.sap.mapper.LogStatsMapper;
import com.sap.mapper.SysLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysLogServiceTest extends BaseUnitTest {

    @Mock SysLogMapper sysLogMapper;
    @Mock LogStatsMapper logStatsMapper;

    @InjectMocks SysLogService service;

    private LogStats stat(LocalDate date, String op, String method, int count) {
        LogStats s = new LogStats();
        s.setStatDate(date);
        s.setOperationType(op);
        s.setHttpMethod(method);
        s.setCount(count);
        return s;
    }

    // ============ pageQuery ============

    @Test
    void pageQuery_withFilters_returnsPage() {
        SysLog log = new SysLog();
        log.setId(1L);
        when(sysLogMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<SysLog> p = inv.getArgument(0);
            p.setRecords(List.of(log));
            p.setTotal(1);
            return p;
        });

        Page<SysLog> result = service.pageQuery(1, 20, "查询", "GET");
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
    }

    @Test
    void pageQuery_noFilters_returnsPage() {
        when(sysLogMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<SysLog> p = inv.getArgument(0);
            p.setTotal(0);
            return p;
        });

        Page<SysLog> result = service.pageQuery(1, 20, "", null);
        assertEquals(0L, result.getTotal());
    }

    // ============ getStats ============

    @Test
    void getStats_aggregatesByTypeMethodAndDate() {
        LocalDate today = LocalDate.now();
        List<LogStats> stats = List.of(
                stat(today, "查询", "GET", 3),
                stat(today, "新增", "POST", 2),
                stat(today.minusDays(1), "查询", "GET", 5)
        );
        when(logStatsMapper.selectList(any())).thenReturn(stats);

        Map<String, Object> result = service.getStats(7);

        assertEquals(10, result.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> byOp = (Map<String, Integer>) result.get("byOperationType");
        assertEquals(8, byOp.get("查询"));
        assertEquals(2, byOp.get("新增"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> byMethod = (Map<String, Integer>) result.get("byHttpMethod");
        assertEquals(8, byMethod.get("GET"));
        assertEquals(2, byMethod.get("POST"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> dailyTrend = (Map<String, Integer>) result.get("dailyTrend");
        // 7 days initialized
        assertEquals(7, dailyTrend.size());
        assertEquals(5, dailyTrend.get(today.toString()));
        assertEquals(5, dailyTrend.get(today.minusDays(1).toString()));
    }

    @Test
    void getStats_emptyStats_zeroTotalsWithInitializedDates() {
        when(logStatsMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> result = service.getStats(3);
        assertEquals(0, result.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> dailyTrend = (Map<String, Integer>) result.get("dailyTrend");
        assertEquals(3, dailyTrend.size());
        dailyTrend.values().forEach(v -> assertEquals(0, v));
    }

    // ============ getDailyCalendar ============

    @Test
    void getDailyCalendar_returnsDatePairsSorted() {
        LocalDate today = LocalDate.now();
        when(logStatsMapper.selectList(any())).thenReturn(List.of(
                stat(today, "查询", "GET", 4)
        ));

        List<List<Object>> result = service.getDailyCalendar(3);
        assertEquals(3, result.size());
        // each entry: [dateString, count]
        List<Object> last = result.get(result.size() - 1);
        assertEquals(today.toString(), last.get(0));
        assertEquals(4, last.get(1));
        // earliest day count 0
        assertEquals(0, result.get(0).get(1));
    }

    @Test
    void getDailyCalendar_emptyStats_allZero() {
        when(logStatsMapper.selectList(any())).thenReturn(List.of());
        List<List<Object>> result = service.getDailyCalendar(2);
        assertEquals(2, result.size());
        result.forEach(pair -> assertEquals(0, pair.get(1)));
    }

    // ============ totalCount ============

    @Test
    void totalCount_delegates() {
        when(sysLogMapper.selectCount(isNull())).thenReturn(42L);
        assertEquals(42L, service.totalCount());
    }
}
