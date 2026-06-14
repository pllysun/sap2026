package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyLeader;
import com.sap.entity.StudyMember;
import com.sap.entity.User;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖负责人管理：listLeaders / addLeader / deleteLeader / restoreLeader。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyServiceLeaderTest extends BaseUnitTest {

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

    private StudyActivity activity(long id, int currentWeek) {
        StudyActivity a = new StudyActivity();
        a.setId(id);
        a.setCurrentWeek(currentWeek);
        return a;
    }

    private StudyLeader leader(long id, long userId, String studentId) {
        StudyLeader l = new StudyLeader();
        l.setId(id);
        l.setActivityId(1L);
        l.setUserId(userId);
        l.setStudentId(studentId);
        l.setDeleted(0);
        return l;
    }

    private StudyMember member(long id, Long leaderId) {
        StudyMember m = new StudyMember();
        m.setId(id);
        m.setActivityId(1L);
        m.setLeaderId(leaderId);
        m.setWeek(1);
        return m;
    }

    // ===================== listLeaders =====================

    @Test
    void listLeaders_emptyLeaders_returnsEmptyList() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1, 1));
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> result = service.listLeaders(1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void listLeaders_aggregatesMemberCountsPerLeader() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1, 1));
        StudyLeader l1 = leader(10L, 100L, "S100");
        StudyLeader l2 = leader(20L, 200L, "S200");
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of(l1, l2));

        // l1 有 2 个成员，l2 有 0 个
        when(studyMemberMapper.selectList(any())).thenReturn(List.of(member(1, 10L), member(2, 10L)));
        when(cacheService.getUserName(100L)).thenReturn("张三");
        when(cacheService.getUserName(200L)).thenReturn("李四");

        List<Map<String, Object>> result = service.listLeaders(1L);

        assertEquals(2, result.size());
        Map<String, Object> first = result.get(0);
        assertEquals(10L, first.get("id"));
        assertEquals("张三", first.get("userName"));
        assertEquals(2L, first.get("memberCount"));
        assertEquals(0L, result.get(1).get("memberCount"));
    }

    @Test
    void listLeaders_activityNullDefaultsWeekOne() {
        when(studyActivityMapper.selectById(1L)).thenReturn(null);
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of(leader(10L, 100L, "S100")));
        when(studyMemberMapper.selectList(any())).thenReturn(List.of());
        when(cacheService.getUserName(100L)).thenReturn("张三");

        List<Map<String, Object>> result = service.listLeaders(1L);
        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).get("memberCount"));
    }

    // ===================== addLeader =====================

    @Test
    void addLeader_userNotFound_throws() {
        StudyLeader leader = new StudyLeader();
        leader.setStudentId("S999");
        leader.setActivityId(1L);
        when(cacheService.getUserByStudentId("S999")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addLeader(leader));
        assertEquals("该学号用户不存在", ex.getMessage());
    }

    @Test
    void addLeader_alreadyLeader_throws() {
        StudyLeader leader = new StudyLeader();
        leader.setStudentId("S100");
        leader.setActivityId(1L);
        User u = new User();
        u.setId(100L);
        when(cacheService.getUserByStudentId("S100")).thenReturn(u);
        when(studyLeaderMapper.selectCount(any())).thenReturn(1L); // 已存在

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addLeader(leader));
        assertEquals("该用户已是负责人", ex.getMessage());
        verify(studyLeaderMapper, never()).insert(any(com.sap.entity.StudyLeader.class));
    }

    @Test
    void addLeader_success_setsUserIdAndInserts() {
        StudyLeader leader = new StudyLeader();
        leader.setStudentId("S100");
        leader.setActivityId(1L);
        User u = new User();
        u.setId(100L);
        when(cacheService.getUserByStudentId("S100")).thenReturn(u);
        when(studyLeaderMapper.selectCount(any())).thenReturn(0L);

        service.addLeader(leader);

        assertEquals(100L, leader.getUserId());
        verify(studyLeaderMapper).insert(leader);
    }

    // ===================== deleteLeader =====================

    @Test
    void deleteLeader_notFound_throws() {
        when(studyLeaderMapper.selectById(5L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteLeader(5L));
        assertEquals("负责人不存在", ex.getMessage());
    }

    @Test
    void deleteLeader_reassignsOrphanedMembersToRemainingLeaders() {
        StudyLeader removed = leader(5L, 500L, "S500");
        when(studyLeaderMapper.selectById(5L)).thenReturn(removed);
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1, 1));

        // 该负责人当前周期有 1 个孤儿成员
        StudyMember orphan = member(99L, 5L);
        when(studyMemberMapper.selectList(any()))
                .thenReturn(List.of(orphan))   // 孤儿成员列表
                .thenReturn(List.of());        // assignToLeastLoadedLeader 中查剩余负责人(此处返回 leader 列表用 selectList 另一桩)
        // assignToLeastLoadedLeader 查负责人
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of(leader(8L, 800L, "S800")));
        when(studyMemberMapper.selectCount(any())).thenReturn(0L);

        service.deleteLeader(5L);

        verify(studyLeaderMapper).deleteById(5L);
        // 孤儿成员先被置空 leaderId 再重新分配，至少 update 两次
        verify(studyMemberMapper, atLeast(1)).updateById(orphan);
        // 重新分配到剩余负责人 id=8
        assertEquals(8L, orphan.getLeaderId());
    }

    @Test
    void deleteLeader_activityNull_onlyDeletesNoReassign() {
        StudyLeader removed = leader(5L, 500L, "S500");
        when(studyLeaderMapper.selectById(5L)).thenReturn(removed);
        when(studyActivityMapper.selectById(1L)).thenReturn(null);

        service.deleteLeader(5L);

        verify(studyLeaderMapper).deleteById(5L);
        verify(studyMemberMapper, never()).updateById(any(com.sap.entity.StudyMember.class));
    }

    // ===================== restoreLeader =====================

    @Test
    void restoreLeader_updatesDeletedFlagToZero() {
        service.restoreLeader(7L);

        ArgumentCaptor<StudyLeader> captor = ArgumentCaptor.forClass(StudyLeader.class);
        verify(studyLeaderMapper).updateById(captor.capture());
        StudyLeader updated = captor.getValue();
        assertEquals(7L, updated.getId());
        assertEquals(0, updated.getDeleted());
    }
}
