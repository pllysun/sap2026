package com.sap.controller;

import com.sap.BaseUnitTest;
import com.sap.common.Result;
import com.sap.entity.Activity;
import com.sap.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityControllerTest extends BaseUnitTest {

    @Mock ActivityService activityService;
    @InjectMocks ActivityController controller;

    @Test
    void list_delegates() {
        List<Map<String, Object>> list = List.of(Map.of("grade", "2025"));
        when(activityService.listByGrade("2025")).thenReturn(list);
        Result<?> r = controller.list("2025");
        assertEquals(200, r.getCode());
        assertEquals(list, r.getData());
    }

    @Test
    void page_delegates() {
        Map<String, Object> page = Map.of("total", 1L);
        when(activityService.pageAll(1, 5)).thenReturn(page);
        Result<?> r = controller.page(1, 5);
        assertEquals(page, r.getData());
    }

    @Test
    void add_buildsActivityAndPassesImages() {
        Map<String, Object> params = new HashMap<>();
        params.put("grade", "2025");
        params.put("title", "活动标题");
        params.put("content", "活动内容");
        params.put("imageUrls", List.of("u1", "u2"));

        Result<?> r = controller.add(params);

        ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
        verify(activityService).addActivity(cap.capture(), eq(List.of("u1", "u2")));
        assertEquals("2025", cap.getValue().getGrade());
        assertEquals("活动标题", cap.getValue().getTitle());
        assertEquals("活动内容", cap.getValue().getContent());
        assertEquals("添加成功", r.getData());
    }

    @Test
    void update_buildsActivityAndPassesImages() {
        Map<String, Object> params = new HashMap<>();
        params.put("title", "新标题");
        params.put("content", "新内容");
        params.put("imageUrls", null);

        Result<?> r = controller.update(9L, params);

        ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
        verify(activityService).updateActivity(eq(9L), cap.capture(), any());
        assertEquals("新标题", cap.getValue().getTitle());
        assertEquals("修改成功", r.getData());
    }

    @Test
    void delete_delegates() {
        Result<?> r = controller.delete(3L);
        verify(activityService).deleteActivity(3L);
        assertEquals("删除成功", r.getData());
    }

    @Test
    void years_delegates() {
        when(activityService.getActivityYears()).thenReturn(List.of("2025", "2024"));
        Result<?> r = controller.years();
        assertEquals(List.of("2025", "2024"), r.getData());
    }

    @Test
    void count_delegates() {
        when(activityService.getCountByGrade("2025")).thenReturn(4L);
        Result<?> r = controller.count("2025");
        assertEquals(4L, r.getData());
    }
}
