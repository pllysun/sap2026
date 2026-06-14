package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.BaseUnitTest;
import com.sap.common.Result;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyLeader;
import com.sap.entity.StudyScore;
import com.sap.service.StudyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 覆盖 StudyController 全部端点：参数解析、StpUtil 登录 ID 注入、Service 委派与返回包装。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyControllerTest extends BaseUnitTest {

    @Mock StudyService studyService;

    @InjectMocks StudyController controller;

    // ===================== 查询端点 =====================

    @Test
    void studyActivityYears_returnsServiceResult() {
        when(studyService.getStudyActivityYears()).thenReturn(List.of("2026", "2025"));
        Result<?> r = controller.studyActivityYears();
        assertEquals(200, r.getCode());
        assertEquals(List.of("2026", "2025"), r.getData());
    }

    @Test
    void allActivitiesWithStats_delegates() {
        when(studyService.listAllActivitiesWithStats()).thenReturn(List.of());
        Result<?> r = controller.allActivitiesWithStats();
        assertEquals(200, r.getCode());
        assertEquals(List.of(), r.getData());
    }

    @Test
    void listActivities_passesGrade() {
        when(studyService.listActivities("2026")).thenReturn(List.of());
        controller.listActivities("2026");
        verify(studyService).listActivities("2026");
    }

    @Test
    void getActivityDetail_delegates() {
        Map<String, Object> detail = Map.of("k", "v");
        when(studyService.getActivityDetail(5L)).thenReturn(detail);
        Result<?> r = controller.getActivityDetail(5L);
        assertEquals(detail, r.getData());
    }

    @Test
    void getCycleDetail_delegates() {
        when(studyService.getCycleDetail(1L, 2)).thenReturn(List.of());
        controller.getCycleDetail(1L, 2);
        verify(studyService).getCycleDetail(1L, 2);
    }

    @Test
    void listMembers_delegatesToCycleDetail() {
        when(studyService.getCycleDetail(1L, 3)).thenReturn(List.of());
        controller.listMembers(1L, 3);
        verify(studyService).getCycleDetail(1L, 3);
    }

    @Test
    void listLeaders_delegates() {
        when(studyService.listLeaders(1L)).thenReturn(List.of());
        controller.listLeaders(1L);
        verify(studyService).listLeaders(1L);
    }

    @Test
    void scoreOverview_delegates() {
        when(studyService.scoreOverview(1L, 2)).thenReturn(List.of());
        controller.scoreOverview(1L, 2);
        verify(studyService).scoreOverview(1L, 2);
    }

    @Test
    void ranking_delegates() {
        when(studyService.getRanking(1L, 1, 20)).thenReturn(Map.of());
        controller.ranking(1L, 1, 20);
        verify(studyService).getRanking(1L, 1, 20);
    }

    // ===================== 写端点 =====================

    @Test
    void createActivity_invokesServiceAndReturnsOk() {
        StudyActivity a = new StudyActivity();
        Result<?> r = controller.createActivity(a);
        verify(studyService).createActivity(a);
        assertEquals("创建成功", r.getData());
    }

    @Test
    void closeActivity_invokesService() {
        Result<?> r = controller.closeActivity(5L);
        verify(studyService).closeActivity(5L);
        assertEquals("已关闭", r.getData());
    }

    @Test
    void setActiveWeek_extractsWeekParam() {
        Map<String, Integer> params = new HashMap<>();
        params.put("week", 3);
        Result<?> r = controller.setActiveWeek(5L, params);
        verify(studyService).setActiveWeek(5L, 3);
        assertEquals("活动周期已更新", r.getData());
    }

    @Test
    void addLeader_invokesService() {
        StudyLeader l = new StudyLeader();
        Result<?> r = controller.addLeader(l);
        verify(studyService).addLeader(l);
        assertEquals("添加成功", r.getData());
    }

    @Test
    void deleteLeader_invokesService() {
        Result<?> r = controller.deleteLeader(7L);
        verify(studyService).deleteLeader(7L);
        assertEquals("删除成功", r.getData());
    }

    @Test
    void restoreLeader_invokesService() {
        Result<?> r = controller.restoreLeader(7L);
        verify(studyService).restoreLeader(7L);
        assertEquals("恢复成功", r.getData());
    }

    @Test
    void reassignMember_extractsParams() {
        Map<String, Long> params = new HashMap<>();
        params.put("memberId", 100L);
        params.put("leaderId", 9L);
        controller.reassignMember(params);
        verify(studyService).reassignMember(100L, 9L);
    }

    @Test
    void nextWeek_extractsActivityId() {
        Map<String, Long> params = new HashMap<>();
        params.put("activityId", 1L);
        Result<?> r = controller.nextWeek(params);
        verify(studyService).nextWeek(1L);
        assertEquals("进入下一周期", r.getData());
    }

    // ===================== 成员加入（含 StpUtil） =====================

    @Test
    void autoJoin_usesParamUserIdWhenProvided() {
        Map<String, Long> params = new HashMap<>();
        params.put("userId", 88L);
        controller.autoJoin(params);
        verify(studyService).autoJoinLatest(88L);
    }

    @Test
    void autoJoin_nullParams_fallsBackToLoginId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            controller.autoJoin(null);
            verify(studyService).autoJoinLatest(1L);
        }
    }

    @Test
    void autoJoin_paramsWithoutUserId_fallsBackToLoginId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            controller.autoJoin(new HashMap<>());
            verify(studyService).autoJoinLatest(1L);
        }
    }

    @Test
    void memberJoin_usesProvidedUserId() {
        Map<String, Long> params = new HashMap<>();
        params.put("activityId", 1L);
        params.put("userId", 50L);
        controller.memberJoin(params);
        verify(studyService).memberJoin(1L, 50L);
    }

    @Test
    void memberJoin_nullUserId_fallsBackToLoginId() {
        Map<String, Long> params = new HashMap<>();
        params.put("activityId", 1L);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            controller.memberJoin(params);
            verify(studyService).memberJoin(1L, 1L);
        }
    }

    @Test
    void batchJoin_parsesActivityIdAndUserIds() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("userIds", List.of("60", "61", 62));
        when(studyService.batchMemberJoin(eq(1L), anyList())).thenReturn(3);

        Result<?> r = controller.batchJoin(params);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(studyService).batchMemberJoin(eq(1L), captor.capture());
        assertEquals(List.of(60L, 61L, 62L), captor.getValue());
        assertEquals("成功添加 3 人", r.getData());
    }

    // ===================== 作业（含 StpUtil） =====================

    @Test
    void uploadHomework_parsesAllParams_immediatePublish() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("week", "2");
        params.put("title", "作业");
        params.put("fileUrl", "url");
        params.put("fileName", "f.pdf");

        Result<?> r = controller.uploadHomework(params);
        // 无 publishTime → 传 null（立即发布）
        verify(studyService).uploadHomework(1L, 2, "作业", "url", "f.pdf", null);
        assertEquals("上传成功", r.getData());
    }

    @Test
    void uploadHomework_nullOptionalFields_passedAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("week", "2");
        controller.uploadHomework(params);
        verify(studyService).uploadHomework(1L, 2, null, null, null, null);
    }

    @Test
    void uploadHomework_parsesPublishTime() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("week", "5");
        params.put("title", "定时作业");
        params.put("fileUrl", "url");
        params.put("fileName", "f.pdf");
        params.put("publishTime", "2026-08-01 18:30:00");

        controller.uploadHomework(params);
        verify(studyService).uploadHomework(1L, 5, "定时作业", "url", "f.pdf",
                java.time.LocalDateTime.of(2026, 8, 1, 18, 30, 0));
    }

    @Test
    void uploadHomework_parsesPublishTime_isoAndNoSeconds() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("week", "3");
        params.put("publishTime", "2026-08-01T09:05");
        controller.uploadHomework(params);
        verify(studyService).uploadHomework(1L, 3, null, null, null,
                java.time.LocalDateTime.of(2026, 8, 1, 9, 5, 0));
    }

    @Test
    void uploadHomework_invalidPublishTime_throws() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("week", "3");
        params.put("publishTime", "not-a-date");
        assertThrows(com.sap.common.BusinessException.class, () -> controller.uploadHomework(params));
    }

    @Test
    void homeworkSchedule_delegates() {
        when(studyService.getHomeworkSchedule(1L)).thenReturn(java.util.List.of());
        Result<?> r = controller.homeworkSchedule(1L);
        assertEquals(200, r.getCode());
        verify(studyService).getHomeworkSchedule(1L);
    }

    @Test
    void deleteHomework_delegates() {
        Result<?> r = controller.deleteHomework(1L, 2);
        verify(studyService).deleteHomework(1L, 2);
        assertEquals("删除成功", r.getData());
    }

    @Test
    void deleteMySubmission_usesLoginId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            Result<?> r = controller.deleteMySubmission(1L, 2);
            verify(studyService).deleteMySubmission(1L, 2, 1L);
            assertEquals("删除成功", r.getData());
        }
    }

    @Test
    void submitHomework_usesLoginIdAndParsesParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("week", "2");
        params.put("title", "我的作业");
        params.put("fileUrl", "url");
        params.put("fileName", "my.pdf");

        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(50L);
            Result<?> r = controller.submitHomework(params);
            verify(studyService).submitStudentHomework(1L, 2, 50L, "我的作业", "url", "my.pdf");
            assertEquals("提交成功", r.getData());
        }
    }

    @Test
    void submitHomework_nullOptionalFields_passedAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", "1");
        params.put("week", "2");
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(50L);
            controller.submitHomework(params);
            verify(studyService).submitStudentHomework(1L, 2, 50L, null, null, null);
        }
    }

    // ===================== 评分（含 StpUtil） =====================

    @Test
    void score_setsLeaderUserIdFromLoginId_thenDelegates() {
        StudyScore s = new StudyScore();
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
            Result<?> r = controller.score(s);
            assertEquals(9L, s.getLeaderUserId());
            verify(studyService).score(s);
            assertEquals("评分成功", r.getData());
        }
    }

    // ===================== 我的状态/成绩（含 StpUtil） =====================

    @Test
    void myStatus_usesLoginId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(50L);
            when(studyService.getMyStatus(50L)).thenReturn(Map.of("hasActivity", false));
            Result<?> r = controller.myStatus();
            verify(studyService).getMyStatus(50L);
            assertNotNull(r.getData());
        }
    }

    @Test
    void myScores_usesLoginId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(50L);
            when(studyService.getMyScores(50L)).thenReturn(Map.of());
            controller.myScores();
            verify(studyService).getMyScores(50L);
        }
    }
}
