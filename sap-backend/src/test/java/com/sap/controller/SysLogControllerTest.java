package com.sap.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.common.Result;
import com.sap.entity.SysLog;
import com.sap.service.SysLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysLogControllerTest extends BaseUnitTest {

    @Mock SysLogService sysLogService;
    @InjectMocks SysLogController controller;

    @Test
    void list_delegates() {
        Page<SysLog> page = new Page<>(1, 20);
        when(sysLogService.pageQuery(1, 20, "查询", "GET")).thenReturn(page);
        Result<?> r = controller.list(1, 20, "查询", "GET");
        assertEquals(200, r.getCode());
        assertSame(page, r.getData());
    }

    @Test
    void stats_delegates() {
        Map<String, Object> stats = Map.of("total", 5);
        when(sysLogService.getStats(7)).thenReturn(stats);
        Result<?> r = controller.stats(7);
        assertEquals(stats, r.getData());
    }

    @Test
    void calendar_delegates() {
        List<List<Object>> cal = List.of(List.of("2026-06-14", 3));
        when(sysLogService.getDailyCalendar(365)).thenReturn(cal);
        Result<?> r = controller.calendar(365);
        assertEquals(cal, r.getData());
    }

    @Test
    void publicCalendar_delegatesWith365() {
        List<List<Object>> cal = List.of(List.of("2026-06-14", 1));
        when(sysLogService.getDailyCalendar(365)).thenReturn(cal);
        Result<?> r = controller.publicCalendar();
        assertEquals(cal, r.getData());
    }
}
