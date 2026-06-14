package com.sap.controller;

import com.sap.BaseUnitTest;
import com.sap.common.Result;
import com.sap.entity.Setting;
import com.sap.entity.Term;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.TermMapper;
import com.sap.service.ActivityService;
import com.sap.service.BillService;
import com.sap.service.StudyService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardControllerTest extends BaseUnitTest {

    @Mock ActivityService activityService;
    @Mock BillService billService;
    @Mock StudyService studyService;
    @Mock SettingMapper settingMapper;
    @Mock TermMapper termMapper;

    @InjectMocks DashboardController controller;

    private Term gradeTerm(String grade) {
        Term t = new Term();
        t.setGrade(grade);
        return t;
    }

    @Test
    @SuppressWarnings("unchecked")
    void stats_withGrade_usesGradeScopedQueries() {
        when(termMapper.selectCount(any())).thenReturn(12L);
        when(activityService.countActivitiesByGrade("2025")).thenReturn(3L);
        when(studyService.countStudyActivitiesByGrade("2025")).thenReturn(4L);
        when(billService.getStats("2025")).thenReturn(Map.of("income", 100));

        Result<?> r = controller.stats("2025");
        assertEquals(200, r.getCode());
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals("2025", data.get("currentGrade"));
        assertEquals(12L, data.get("userCount"));
        assertEquals(3L, data.get("activityCount"));
        assertEquals(4L, data.get("studyActivityCount"));
        assertEquals(Map.of("income", 100), data.get("financeStats"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stats_nullGrade_resolvesFromSetting() {
        Setting s = new Setting();
        s.setSettingValue("2024");
        when(settingMapper.selectOne(any())).thenReturn(s);
        when(termMapper.selectCount(any())).thenReturn(7L);
        when(activityService.countActivitiesByGrade("2024")).thenReturn(1L);
        when(studyService.countStudyActivitiesByGrade("2024")).thenReturn(2L);
        when(billService.getStats("2024")).thenReturn(Map.of());

        Result<?> r = controller.stats(null);
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals("2024", data.get("currentGrade"));
        assertEquals(7L, data.get("userCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stats_nullGradeNoSetting_defaults2025() {
        when(settingMapper.selectOne(any())).thenReturn(null);
        when(termMapper.selectCount(any())).thenReturn(0L);

        Result<?> r = controller.stats("");
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals("2025", data.get("currentGrade"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stats_all_usesGlobalQueries() {
        when(termMapper.selectCount(isNull())).thenReturn(100L);
        when(activityService.countActivities()).thenReturn(20L);
        when(studyService.countStudyActivities()).thenReturn(30L);
        when(billService.getStats(isNull())).thenReturn(Map.of("total", 999));

        Result<?> r = controller.stats("all");
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals("all", data.get("currentGrade"));
        assertEquals(100L, data.get("userCount"));
        assertEquals(20L, data.get("activityCount"));
        assertEquals(30L, data.get("studyActivityCount"));
        assertEquals(Map.of("total", 999), data.get("financeStats"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradeStats_returnsPerGradeCounts() {
        when(termMapper.selectList(any())).thenReturn(List.of(gradeTerm("2024"), gradeTerm("2025")));
        when(termMapper.selectCount(any())).thenReturn(5L, 8L);

        Result<?> r = controller.gradeStats();
        List<Map<String, Object>> data = (List<Map<String, Object>>) r.getData();
        assertEquals(2, data.size());
        assertEquals("2024", data.get(0).get("grade"));
        assertEquals(5L, data.get(0).get("count"));
        assertEquals("2025", data.get(1).get("grade"));
        assertEquals(8L, data.get(1).get("count"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradeStats_empty() {
        when(termMapper.selectList(any())).thenReturn(List.of());
        Result<?> r = controller.gradeStats();
        List<Map<String, Object>> data = (List<Map<String, Object>>) r.getData();
        assertTrue(data.isEmpty());
    }
}
