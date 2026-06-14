package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyLeader;
import com.sap.entity.StudyMaterial;
import com.sap.entity.StudyMember;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.StudyActivityMapper;
import com.sap.mapper.StudyLeaderMapper;
import com.sap.mapper.StudyMaterialMapper;
import com.sap.mapper.StudyMemberMapper;
import com.sap.mapper.StudyScoreMapper;
import com.sap.mapper.TermMapper;
import com.sap.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖作业管理：uploadHomework(含定时排期) / deleteHomework / deleteMySubmission /
 * submitStudentHomework / processScheduledPublish(到点自动推进) / getHomeworkSchedule。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyServiceHomeworkTest extends BaseUnitTest {

    @Mock StudyActivityMapper studyActivityMapper;
    @Mock StudyLeaderMapper studyLeaderMapper;
    @Mock StudyMemberMapper studyMemberMapper;
    @Mock StudyScoreMapper studyScoreMapper;
    @Mock StudyMaterialMapper studyMaterialMapper;
    @Mock TermMapper termMapper;
    @Mock UserMapper userMapper;
    @Mock SettingMapper settingMapper;
    @Mock PositionMapper positionMapper;
    @Mock CacheService cacheService;

    @InjectMocks StudyService service;

    private StudyActivity activity(int currentWeek) {
        StudyActivity a = new StudyActivity();
        a.setId(1L);
        a.setCurrentWeek(currentWeek);
        a.setActiveWeek(currentWeek);
        a.setStatus(1);
        return a;
    }

    // ===================== uploadHomework =====================

    @Test
    void uploadHomework_deletesOldThenInserts_usesTitle_immediatePublish() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(2));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginId).thenReturn(7L);

            service.uploadHomework(1L, 2, "第二周作业", "url", "hw.pdf", null);

            verify(studyMaterialMapper).delete(any());
            ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
            verify(studyMaterialMapper).insert(captor.capture());
            StudyMaterial m = captor.getValue();
            assertEquals(1L, m.getActivityId());
            assertEquals(2, m.getWeek());
            assertEquals(1, m.getFileType());
            assertEquals("第二周作业", m.getTitle());
            assertEquals("url", m.getFileUrl());
            assertEquals("hw.pdf", m.getFileName());
            assertEquals(7L, m.getUserId());
            assertNull(m.getPublishTime());
        }
    }

    @Test
    void uploadHomework_nullTitle_fallsBackToFileName() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginId).thenReturn(7L);

            service.uploadHomework(1L, 2, null, "url", "fallback.pdf", null);

            ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
            verify(studyMaterialMapper).insert(captor.capture());
            assertEquals("fallback.pdf", captor.getValue().getTitle());
        }
    }

    @Test
    void uploadHomework_futureWeekWithPublishTime_scheduled_doesNotAdvanceWeek() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1));
        LocalDateTime publish = LocalDateTime.now().plusDays(3);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginId).thenReturn(7L);

            // 给未来第 5 周排期，带发布时间
            service.uploadHomework(1L, 5, "第五周作业", "url", "w5.pdf", publish);

            ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
            verify(studyMaterialMapper).insert(captor.capture());
            assertEquals(5, captor.getValue().getWeek());
            assertEquals(publish, captor.getValue().getPublishTime());
            // 不推进周期、不创建成员
            verify(studyActivityMapper, never()).updateById(any());
            verify(studyMemberMapper, never()).insert(any(StudyMember.class));
        }
    }

    @Test
    void uploadHomework_invalidWeek_throws() {
        assertThrows(BusinessException.class,
                () -> service.uploadHomework(1L, 0, "t", "u", "f", null));
    }

    @Test
    void uploadHomework_activityNotFound_throws() {
        when(studyActivityMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.uploadHomework(9L, 1, "t", "u", "f", null));
    }

    // ===================== 到点自动推进 =====================

    @Test
    void processScheduledPublish_advancesWhenHomeworkDue_andRematches() {
        StudyActivity a = activity(1);
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(a));
        // 第2周作业已到发布时间(1L) → 推进；第3周未到(0L) → 停止
        when(studyMaterialMapper.selectCount(any())).thenReturn(1L, 0L);
        // 上一周成员 + 负责人，用于重新匹配
        StudyMember m = new StudyMember();
        m.setUserId(100L);
        when(studyMemberMapper.selectList(any())).thenReturn(List.of(m));
        StudyLeader l = new StudyLeader();
        l.setId(10L);
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of(l));

        int advanced = service.processScheduledPublish();

        assertEquals(1, advanced);
        assertEquals(2, a.getCurrentWeek());
        assertEquals(2, a.getActiveWeek());
        verify(studyActivityMapper).updateById(any(StudyActivity.class));
        verify(studyMemberMapper).insert(any(StudyMember.class)); // 重新匹配插入新成员
    }

    @Test
    void processScheduledPublish_noDueHomework_doesNothing() {
        StudyActivity a = activity(1);
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(a));
        when(studyMaterialMapper.selectCount(any())).thenReturn(0L);

        int advanced = service.processScheduledPublish();

        assertEquals(0, advanced);
        assertEquals(1, a.getCurrentWeek());
        verify(studyActivityMapper, never()).updateById(any());
    }

    @Test
    void processScheduledPublish_multipleWeeksDue_advancesSequentially() {
        StudyActivity a = activity(1);
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(a));
        // 第2、3周都到点(1L,1L)，第4周未到(0L)
        when(studyMaterialMapper.selectCount(any())).thenReturn(1L, 1L, 0L);
        when(studyMemberMapper.selectList(any())).thenReturn(List.of());
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of());

        int advanced = service.processScheduledPublish();

        assertEquals(2, advanced);
        assertEquals(3, a.getCurrentWeek());
    }

    @Test
    void processScheduledPublish_noRunningActivities_returnsZero() {
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, service.processScheduledPublish());
    }

    // ===================== getHomeworkSchedule =====================

    @Test
    void getHomeworkSchedule_marksPublishedAndScheduled() {
        StudyMaterial published = new StudyMaterial();
        published.setWeek(1);
        published.setTitle("已发布");
        published.setPublishTime(LocalDateTime.now().minusDays(1));
        StudyMaterial immediate = new StudyMaterial();
        immediate.setWeek(2);
        immediate.setTitle("立即");
        immediate.setPublishTime(null);
        StudyMaterial scheduled = new StudyMaterial();
        scheduled.setWeek(3);
        scheduled.setTitle("定时");
        scheduled.setPublishTime(LocalDateTime.now().plusDays(2));
        when(studyMaterialMapper.selectList(any()))
                .thenReturn(List.of(published, immediate, scheduled));

        List<Map<String, Object>> list = service.getHomeworkSchedule(1L);

        assertEquals(3, list.size());
        assertEquals(true, list.get(0).get("published"));   // 过去时间
        assertEquals(true, list.get(1).get("published"));   // null = 立即
        assertEquals(false, list.get(2).get("published"));  // 未来时间
        assertEquals(3, list.get(2).get("week"));
    }

    // ===================== deleteHomework / 提交 =====================

    @Test
    void deleteHomework_deletesFileType1() {
        service.deleteHomework(1L, 3);
        verify(studyMaterialMapper).delete(any());
    }

    @Test
    void deleteMySubmission_deletesFileType2ForUser() {
        service.deleteMySubmission(1L, 3, 50L);
        verify(studyMaterialMapper).delete(any());
    }

    @Test
    void submitStudentHomework_deletesOldThenInserts_usesTitle() {
        service.submitStudentHomework(1L, 2, 50L, "我的作业", "url", "my.pdf");

        verify(studyMaterialMapper).delete(any());
        ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(studyMaterialMapper).insert(captor.capture());
        StudyMaterial m = captor.getValue();
        assertEquals(2, m.getFileType());
        assertEquals(50L, m.getUserId());
        assertEquals("我的作业", m.getTitle());
        assertEquals("my.pdf", m.getFileName());
    }

    @Test
    void submitStudentHomework_nullTitle_fallsBackToFileName() {
        service.submitStudentHomework(1L, 2, 50L, null, "url", "auto.pdf");

        ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(studyMaterialMapper).insert(captor.capture());
        assertEquals("auto.pdf", captor.getValue().getTitle());
    }
}
