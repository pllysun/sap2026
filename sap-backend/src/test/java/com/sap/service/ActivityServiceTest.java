package com.sap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Activity;
import com.sap.entity.ActivityImage;
import com.sap.entity.Setting;
import com.sap.mapper.ActivityImageMapper;
import com.sap.mapper.ActivityMapper;
import com.sap.mapper.SettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest extends BaseUnitTest {

    @Mock ActivityMapper activityMapper;
    @Mock ActivityImageMapper activityImageMapper;
    @Mock SettingMapper settingMapper;

    @InjectMocks ActivityService service;

    private Activity act(long id, String grade, int seq) {
        Activity a = new Activity();
        a.setId(id);
        a.setGrade(grade);
        a.setSeqNum(seq);
        a.setTitle("t" + id);
        return a;
    }

    @Test
    void listByGrade_returnsActivitiesWithImages() {
        when(activityMapper.selectList(any())).thenReturn(List.of(act(1, "2025", 1)));
        ActivityImage img = new ActivityImage();
        img.setImageUrl("u");
        when(activityImageMapper.selectList(any())).thenReturn(List.of(img));

        List<Map<String, Object>> result = service.listByGrade("2025");

        assertEquals(1, result.size());
        assertEquals("2025", result.get(0).get("grade"));
        assertEquals(List.of(img), result.get(0).get("images"));
    }

    @Test
    void pageAll_populatesRecordsAndImages() {
        when(activityMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Activity> p = inv.getArgument(0);
            p.setRecords(List.of(act(2, "2024", 3)));
            p.setTotal(1);
            return p;
        });
        when(activityImageMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.pageAll(1, 5);

        assertEquals(1L, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(1, records.size());
        assertEquals(3, records.get(0).get("seqNum"));
    }

    @Test
    void addActivity_usesProvidedGradeAndMaxSeqPlusOne() {
        Activity a = act(0, "2025", 0);
        when(activityMapper.selectOne(any())).thenReturn(act(9, "2025", 7)); // last seq = 7

        service.addActivity(a, List.of("url1", "url2"));

        assertEquals(8, a.getSeqNum(), "应为 max+1");
        verify(activityMapper).insert(a);
        verify(activityImageMapper, times(2)).insert(any(ActivityImage.class));
    }

    @Test
    void addActivity_firstActivityGetsSeqOne_andNullImagesSkipped() {
        Activity a = act(0, "2025", 0);
        when(activityMapper.selectOne(any())).thenReturn(null); // no existing activity

        service.addActivity(a, null);

        assertEquals(1, a.getSeqNum());
        verify(activityImageMapper, never()).insert(any());
    }

    @Test
    void addActivity_fallsBackToCurrentGradeSettingWhenGradeBlank() {
        Activity a = act(0, null, 0);
        Setting s = new Setting();
        s.setSettingValue("2030");
        when(settingMapper.selectOne(any())).thenReturn(s);
        when(activityMapper.selectOne(any())).thenReturn(null);

        service.addActivity(a, List.of());

        assertEquals("2030", a.getGrade());
        assertEquals(1, a.getSeqNum());
    }

    @Test
    void addActivity_defaultsGradeTo2025WhenSettingMissing() {
        Activity a = act(0, "", 0);
        when(settingMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.selectOne(any())).thenReturn(null);

        service.addActivity(a, null);

        assertEquals("2025", a.getGrade());
    }

    @Test
    void updateActivity_updatesAndReplacesImages() {
        when(activityMapper.selectById(5L)).thenReturn(act(5, "2025", 1));
        Activity changed = new Activity();
        changed.setTitle("new");
        changed.setContent("c");

        service.updateActivity(5L, changed, List.of("u"));

        verify(activityMapper).updateById(any(Activity.class));
        verify(activityImageMapper).delete(any());
        verify(activityImageMapper).insert(any(ActivityImage.class));
    }

    @Test
    void updateActivity_nullImageUrlsDoesNotTouchImages() {
        when(activityMapper.selectById(5L)).thenReturn(act(5, "2025", 1));
        service.updateActivity(5L, new Activity(), null);
        verify(activityImageMapper, never()).delete(any());
    }

    @Test
    void updateActivity_throwsWhenNotFound() {
        when(activityMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateActivity(99L, new Activity(), null));
        assertEquals("活动不存在", ex.getMessage());
    }

    @Test
    void deleteActivity_cascadesImages() {
        service.deleteActivity(3L);
        verify(activityMapper).deleteById(3L);
        verify(activityImageMapper).delete(any());
    }

    @Test
    void countMethods_delegate() {
        when(activityMapper.selectCount(any())).thenReturn(4L);
        assertEquals(4L, service.countActivities());
        assertEquals(4L, service.countActivitiesByGrade("2025"));
        assertEquals(4L, service.getCountByGrade("2025"));
    }

    @Test
    void getActivityYears_returnsDistinctSortedDescending() {
        when(activityMapper.selectList(any()))
                .thenReturn(List.of(act(1, "2023", 1), act(2, "2025", 1), act(3, "2024", 1)));
        List<String> years = service.getActivityYears();
        assertEquals(List.of("2025", "2024", "2023"), years);
    }
}
