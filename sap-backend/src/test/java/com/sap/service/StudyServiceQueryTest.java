package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyLeader;
import com.sap.entity.StudyMaterial;
import com.sap.entity.StudyMember;
import com.sap.entity.StudyScore;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖查询/详情/统计：listActivities / getStudyActivityYears / listAllActivitiesWithStats /
 * getActivityDetail / getCycleDetail / count* / getMyStatus / getMyScores / getRanking。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyServiceQueryTest extends BaseUnitTest {

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

    private Setting grade(String value) {
        Setting s = new Setting();
        s.setSettingKey("current_grade");
        s.setSettingValue(value);
        return s;
    }

    private StudyActivity activity(long id, String grade, int seq, int currentWeek, int status) {
        StudyActivity a = new StudyActivity();
        a.setId(id);
        a.setGrade(grade);
        a.setSeqNum(seq);
        a.setCurrentWeek(currentWeek);
        a.setActiveWeek(currentWeek);
        a.setStatus(status);
        a.setTitle("活动" + id);
        return a;
    }

    private StudyScore score(long activityId, int week, long memberUserId, long leaderUserId, int sc) {
        StudyScore s = new StudyScore();
        s.setActivityId(activityId);
        s.setWeek(week);
        s.setMemberUserId(memberUserId);
        s.setLeaderUserId(leaderUserId);
        s.setScore(sc);
        s.setComment("评语内容十个字以上的");
        return s;
    }

    // ===================== listActivities =====================

    @Test
    void listActivities_withGrade_filters() {
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(activity(1, "2026", 1, 1, 1)));
        List<StudyActivity> result = service.listActivities("2026");
        assertEquals(1, result.size());
    }

    @Test
    void listActivities_nullGrade_noFilter() {
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.listActivities(null).isEmpty());
    }

    @Test
    void listActivities_emptyGrade_noFilter() {
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.listActivities("").isEmpty());
    }

    // ===================== getStudyActivityYears =====================

    @Test
    void getStudyActivityYears_returnsDistinctSortedDescending() {
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(
                activity(1, "2024", 1, 1, 1),
                activity(2, "2026", 1, 1, 1),
                activity(3, "2025", 1, 1, 1)
        ));
        List<String> years = service.getStudyActivityYears();
        assertEquals(List.of("2026", "2025", "2024"), years);
    }

    // ===================== listAllActivitiesWithStats =====================

    @Test
    void listAllActivitiesWithStats_empty_returnsEmptyList() {
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.listAllActivitiesWithStats().isEmpty());
    }

    @Test
    void listAllActivitiesWithStats_populatesMemberCounts() {
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(
                activity(1, "2026", 2, 3, 1),
                activity(2, "2026", 1, 1, 0)
        ));
        when(studyMemberMapper.selectCount(any())).thenReturn(5L).thenReturn(2L);

        List<Map<String, Object>> result = service.listAllActivitiesWithStats();

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).get("id"));
        assertEquals(5L, result.get(0).get("memberCount"));
        assertEquals(2L, result.get(1).get("memberCount"));
    }

    // ===================== getActivityDetail =====================

    @Test
    void getActivityDetail_notFound_throws() {
        when(studyActivityMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getActivityDetail(99L));
        assertEquals("活动不存在", ex.getMessage());
    }

    @Test
    void getActivityDetail_buildsWeekSummaries() {
        StudyActivity a = activity(1, "2026", 1, 2, 1); // currentWeek = 2 -> 两个周期汇总
        when(studyActivityMapper.selectById(1L)).thenReturn(a);
        // listLeaders 内部
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of());
        // 各 selectCount：totalMembers + 每周期 memberCount
        when(studyMemberMapper.selectCount(any())).thenReturn(4L, 4L, 3L);
        // 每周期评分
        when(studyScoreMapper.selectList(any()))
                .thenReturn(List.of(score(1, 1, 50, 9, 8), score(1, 1, 51, 9, 6))) // week1 avg 7.0
                .thenReturn(List.of()); // week2 无评分
        // 每周期作业题目
        StudyMaterial hw = new StudyMaterial();
        hw.setTitle("作业一");
        hw.setFileUrl("u");
        hw.setFileName("f");
        // 调用顺序：先 lastHw(取最大周, hw.week=null→按 currentWeek) → week1 → week2
        when(studyMaterialMapper.selectOne(any())).thenReturn(hw, hw, null);

        Map<String, Object> detail = service.getActivityDetail(1L);

        assertSame(a, detail.get("activity"));
        assertEquals(4L, detail.get("totalMembers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weeks = (List<Map<String, Object>>) detail.get("weekSummaries");
        assertEquals(2, weeks.size());
        assertEquals(2, weeks.get(0).get("scoredCount"));
        assertEquals(7.0, weeks.get(0).get("avgScore"));
        assertEquals("作业一", weeks.get(0).get("homeworkTitle"));
        assertEquals(0, weeks.get(1).get("scoredCount"));
        assertEquals(0.0, weeks.get(1).get("avgScore"));
        assertNull(weeks.get(1).get("homeworkTitle"));
    }

    // ===================== getCycleDetail =====================

    @Test
    void getCycleDetail_noMembers_returnsEmpty() {
        when(studyMemberMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.getCycleDetail(1L, 1).isEmpty());
    }

    @Test
    void getCycleDetail_assemblesMemberRowsWithLeaderScoreAndSubmissions() {
        StudyMember m1 = new StudyMember();
        m1.setId(100L);
        m1.setUserId(50L);
        m1.setLeaderId(10L);
        m1.setWeek(1);
        StudyMember m2 = new StudyMember();
        m2.setId(101L);
        m2.setUserId(51L);
        m2.setLeaderId(null); // 未分配
        m2.setWeek(1);
        when(studyMemberMapper.selectList(any())).thenReturn(List.of(m1, m2));

        StudyLeader leader = new StudyLeader();
        leader.setId(10L);
        leader.setUserId(9L);
        when(studyLeaderMapper.selectBatchIds(any())).thenReturn(List.of(leader));

        StudyScore sc = score(1, 1, 50, 9, 8);
        sc.setId(500L);
        when(studyScoreMapper.selectList(any())).thenReturn(List.of(sc));

        StudyMaterial sub = new StudyMaterial();
        sub.setUserId(50L);
        sub.setFileType(2);
        when(studyMaterialMapper.selectList(any())).thenReturn(List.of(sub));

        when(cacheService.getUserName(50L)).thenReturn("甲");
        when(cacheService.getStudentId(50L)).thenReturn("S50");
        when(cacheService.getUserName(51L)).thenReturn("乙");
        when(cacheService.getStudentId(51L)).thenReturn("S51");
        when(cacheService.getUserName(9L)).thenReturn("负责人");

        List<Map<String, Object>> rows = service.getCycleDetail(1L, 1);

        assertEquals(2, rows.size());
        Map<String, Object> r1 = rows.get(0);
        assertEquals("甲", r1.get("userName"));
        assertEquals("负责人", r1.get("leaderName"));
        assertEquals(8, r1.get("score"));
        assertEquals(500L, r1.get("scoreId"));
        assertEquals(true, r1.get("submitted"));

        Map<String, Object> r2 = rows.get(1);
        assertEquals("未分配", r2.get("leaderName"));
        assertNull(r2.get("score"));
        assertEquals(false, r2.get("submitted"));
    }

    @Test
    void getCycleDetail_leaderMissingInMap_showsUnassigned() {
        StudyMember m = new StudyMember();
        m.setId(100L);
        m.setUserId(50L);
        m.setLeaderId(99L); // leaderId 存在但批量查询查不到
        m.setWeek(1);
        when(studyMemberMapper.selectList(any())).thenReturn(List.of(m));
        when(studyLeaderMapper.selectBatchIds(any())).thenReturn(List.of()); // 查不到负责人
        when(studyScoreMapper.selectList(any())).thenReturn(List.of());
        when(studyMaterialMapper.selectList(any())).thenReturn(List.of());
        when(cacheService.getUserName(50L)).thenReturn("甲");
        when(cacheService.getStudentId(50L)).thenReturn("S50");

        List<Map<String, Object>> rows = service.getCycleDetail(1L, 1);
        assertEquals("未分配", rows.get(0).get("leaderName"));
    }

    // ===================== count =====================

    @Test
    void countStudyActivities_delegates() {
        when(studyActivityMapper.selectCount(null)).thenReturn(8L);
        assertEquals(8L, service.countStudyActivities());
    }

    @Test
    void countStudyActivitiesByGrade_delegates() {
        when(studyActivityMapper.selectCount(any())).thenReturn(3L);
        assertEquals(3L, service.countStudyActivitiesByGrade("2026"));
    }

    // ===================== getMyStatus =====================

    @Test
    void getMyStatus_noActivity_returnsHasActivityFalse() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        when(studyActivityMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = service.getMyStatus(50L);
        assertEquals(false, result.get("hasActivity"));
    }

    @Test
    void getMyStatus_joinedWithSubmissionAndScore() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        StudyActivity latest = activity(1, "2026", 1, 2, 1);
        latest.setActiveWeek(2);
        when(studyActivityMapper.selectOne(any())).thenReturn(latest);
        when(studyMemberMapper.selectCount(any())).thenReturn(1L); // 已加入

        StudyMaterial hw = new StudyMaterial();
        hw.setFileType(1);
        when(studyMaterialMapper.selectOne(any())).thenReturn(hw);

        StudyMaterial sub = new StudyMaterial();
        sub.setFileType(2);
        when(studyMaterialMapper.selectList(any())).thenReturn(List.of(sub));

        StudyScore weekScore = score(1, 2, 50, 9, 7);
        when(studyScoreMapper.selectOne(any())).thenReturn(weekScore);
        when(cacheService.getUserName(9L)).thenReturn("负责人");

        Map<String, Object> result = service.getMyStatus(50L);

        assertEquals(true, result.get("hasActivity"));
        assertEquals(true, result.get("joined"));
        assertEquals(true, result.get("submitted"));
        assertSame(hw, result.get("homework"));
        @SuppressWarnings("unchecked")
        Map<String, Object> scoreInfo = (Map<String, Object>) result.get("weekScore");
        assertNotNull(scoreInfo);
        assertEquals(7, scoreInfo.get("score"));
        assertEquals("负责人", scoreInfo.get("leaderName"));
    }

    @Test
    void getMyStatus_notJoined_noSubmissionInfo() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        StudyActivity latest = activity(1, "2026", 1, 1, 1);
        when(studyActivityMapper.selectOne(any())).thenReturn(latest);
        when(studyMemberMapper.selectCount(any())).thenReturn(0L); // 未加入
        when(studyMaterialMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = service.getMyStatus(50L);

        assertEquals(false, result.get("joined"));
        assertFalse(result.containsKey("submitted"));
        assertFalse(result.containsKey("weekScore"));
    }

    @Test
    void getMyStatus_joinedNoScore_omitsWeekScore() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        StudyActivity latest = activity(1, "2026", 1, 1, 1);
        when(studyActivityMapper.selectOne(any())).thenReturn(latest);
        when(studyMemberMapper.selectCount(any())).thenReturn(1L);
        when(studyMaterialMapper.selectOne(any())).thenReturn(null);
        when(studyMaterialMapper.selectList(any())).thenReturn(List.of());
        when(studyScoreMapper.selectOne(any())).thenReturn(null); // 无评分

        Map<String, Object> result = service.getMyStatus(50L);

        assertEquals(false, result.get("submitted"));
        assertFalse(result.containsKey("weekScore"));
    }

    // ===================== getMyScores =====================

    @Test
    void getMyScores_noGradeActivities_returnsEmpty() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.getMyScores(50L);
        @SuppressWarnings("unchecked")
        List<Object> activities = (List<Object>) result.get("activities");
        assertTrue(activities.isEmpty());
    }

    @Test
    void getMyScores_userNotJoinedAny_returnsEmpty() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(activity(1, "2026", 1, 1, 1)));
        when(studyMemberMapper.selectList(any())).thenReturn(List.of()); // 未参与任何活动

        Map<String, Object> result = service.getMyScores(50L);
        @SuppressWarnings("unchecked")
        List<Object> activities = (List<Object>) result.get("activities");
        assertTrue(activities.isEmpty());
    }

    @Test
    void getMyScores_computesTotalsAndRank() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        StudyActivity a = activity(1, "2026", 1, 2, 1);
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(a));

        StudyMember membership = new StudyMember();
        membership.setActivityId(1L);
        membership.setUserId(50L);
        when(studyMemberMapper.selectList(any())).thenReturn(List.of(membership));

        // 用户自己的成绩：两周共 15 分
        List<StudyScore> myScores = List.of(score(1, 1, 50, 9, 8), score(1, 2, 50, 9, 7));
        // 全活动成绩：用户50=15，用户51=20(排名更高)，用户52=10
        List<StudyScore> allActivityScores = new ArrayList<>(myScores);
        allActivityScores.add(score(1, 1, 51, 9, 10));
        allActivityScores.add(score(1, 2, 51, 9, 10));
        allActivityScores.add(score(1, 1, 52, 9, 10));

        when(studyScoreMapper.selectList(any()))
                .thenReturn(myScores)            // 用户在已加入活动中的成绩
                .thenReturn(allActivityScores);  // 计算排名时查该活动全部成绩
        when(cacheService.getUserName(9L)).thenReturn("负责人");

        Map<String, Object> result = service.getMyScores(50L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activities = (List<Map<String, Object>>) result.get("activities");
        assertEquals(1, activities.size());
        Map<String, Object> am = activities.get(0);
        assertEquals(15, am.get("totalScore"));
        // 仅 51(20) 高于 15 -> rank 2
        assertEquals(2L, am.get("rank"));
        assertEquals(3, am.get("totalParticipants"));
    }

    // ===================== getRanking =====================

    @Test
    void getRanking_sortsByTotalDescAndPaginates() {
        // 三个用户：50=15, 51=20, 52=10
        List<StudyScore> all = List.of(
                score(1, 1, 50, 9, 8), score(1, 2, 50, 9, 7),
                score(1, 1, 51, 9, 10), score(1, 2, 51, 9, 10),
                score(1, 1, 52, 9, 10)
        );
        when(studyScoreMapper.selectList(any())).thenReturn(all);
        when(cacheService.getUserName(anyLong())).thenReturn("U");
        when(cacheService.getStudentId(anyLong())).thenReturn("S");

        Map<String, Object> result = service.getRanking(1L, 1, 2); // 第一页两条
        assertEquals(3, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(2, records.size());
        assertEquals(1, records.get(0).get("rank"));
        assertEquals(51L, records.get(0).get("userId"));
        assertEquals(20, records.get(0).get("totalScore"));
        assertEquals(2, records.get(1).get("rank"));
        assertEquals(50L, records.get(1).get("userId"));
    }

    @Test
    void getRanking_secondPage_returnsRemainder() {
        List<StudyScore> all = List.of(
                score(1, 1, 50, 9, 8),
                score(1, 1, 51, 9, 10),
                score(1, 1, 52, 9, 5)
        );
        when(studyScoreMapper.selectList(any())).thenReturn(all);
        when(cacheService.getUserName(anyLong())).thenReturn("U");
        when(cacheService.getStudentId(anyLong())).thenReturn("S");

        Map<String, Object> result = service.getRanking(1L, 2, 2); // 第二页
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(1, records.size());
        assertEquals(3, records.get(0).get("rank"));
        assertEquals(52L, records.get(0).get("userId"));
    }

    @Test
    void getRanking_empty_returnsZeroTotal() {
        when(studyScoreMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> result = service.getRanking(1L, 1, 20);
        assertEquals(0, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertTrue(records.isEmpty());
    }
}
