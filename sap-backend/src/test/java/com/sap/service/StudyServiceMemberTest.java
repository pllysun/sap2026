package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyLeader;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖成员管理：autoJoinLatest / memberJoin / batchMemberJoin / reassignMember
 * 以及私有 assignToLeastLoadedLeader 的均分分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyServiceMemberTest extends BaseUnitTest {

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

    private StudyActivity activity(long id, int currentWeek) {
        StudyActivity a = new StudyActivity();
        a.setId(id);
        a.setGrade("2026");
        a.setCurrentWeek(currentWeek);
        a.setStatus(1);
        return a;
    }

    private StudyLeader leader(long id) {
        StudyLeader l = new StudyLeader();
        l.setId(id);
        l.setActivityId(1L);
        return l;
    }

    // ===================== memberJoin =====================

    @Test
    void memberJoin_activityNotFound_throws() {
        when(studyActivityMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.memberJoin(1L, 50L));
        assertEquals("学习活动不存在", ex.getMessage());
    }

    @Test
    void memberJoin_alreadyJoined_throws() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1, 2));
        // 当前周期已加入
        when(studyMemberMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.memberJoin(1L, 50L));
        assertEquals("已加入该活动当前周期", ex.getMessage());
        verify(studyMemberMapper, never()).insert(any(com.sap.entity.StudyMember.class));
    }

    @Test
    void memberJoin_success_insertsAndAssignsLeastLoadedLeader() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1, 2));
        // 第一次 selectCount = 重复检查(0)；后续为各负责人负载查询
        when(studyMemberMapper.selectCount(any()))
                .thenReturn(0L)   // 重复检查
                .thenReturn(3L)   // leader 1 负载
                .thenReturn(1L);  // leader 2 负载（最少）
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of(leader(1L), leader(2L)));

        service.memberJoin(1L, 50L);

        ArgumentCaptor<StudyMember> insertCaptor = ArgumentCaptor.forClass(StudyMember.class);
        verify(studyMemberMapper).insert(insertCaptor.capture());
        StudyMember inserted = insertCaptor.getValue();
        assertEquals(1L, inserted.getActivityId());
        assertEquals(50L, inserted.getUserId());
        assertEquals(2, inserted.getWeek());

        // 分配给负载最少的负责人(id=2)，通过 updateById 落库
        ArgumentCaptor<StudyMember> updateCaptor = ArgumentCaptor.forClass(StudyMember.class);
        verify(studyMemberMapper).updateById(updateCaptor.capture());
        assertEquals(2L, updateCaptor.getValue().getLeaderId());
    }

    @Test
    void memberJoin_noLeaders_insertsButDoesNotAssign() {
        when(studyActivityMapper.selectById(1L)).thenReturn(activity(1, 1));
        when(studyMemberMapper.selectCount(any())).thenReturn(0L);
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of()); // 无负责人

        service.memberJoin(1L, 50L);

        verify(studyMemberMapper).insert(any(StudyMember.class));
        verify(studyMemberMapper, never()).updateById(any(com.sap.entity.StudyMember.class));
    }

    // ===================== autoJoinLatest =====================

    @Test
    void autoJoinLatest_noActiveActivity_throws() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        when(studyActivityMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.autoJoinLatest(50L));
        assertEquals("当前年级没有进行中的学习活动", ex.getMessage());
    }

    @Test
    void autoJoinLatest_delegatesToMemberJoin() {
        when(settingMapper.selectOne(any())).thenReturn(null); // 默认 2025
        StudyActivity latest = activity(7, 1);
        when(studyActivityMapper.selectOne(any())).thenReturn(latest);
        // memberJoin 内部
        when(studyActivityMapper.selectById(7L)).thenReturn(latest);
        when(studyMemberMapper.selectCount(any())).thenReturn(0L);
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of());

        service.autoJoinLatest(50L);

        verify(studyMemberMapper).insert(any(StudyMember.class));
    }

    // ===================== batchMemberJoin =====================

    @Test
    void batchMemberJoin_countsSuccess_swallowsBusinessException() {
        StudyActivity a = activity(1, 1);
        when(studyActivityMapper.selectById(1L)).thenReturn(a);
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of());
        // user 60 重复(selectCount=1 抛被吞)，user 61/62 正常(0)
        when(studyMemberMapper.selectCount(any()))
                .thenReturn(1L)   // uid 60 重复 -> 抛 -> 吞
                .thenReturn(0L)   // uid 61 正常
                .thenReturn(0L);  // uid 62 正常

        int success = service.batchMemberJoin(1L, List.of(60L, 61L, 62L));

        assertEquals(2, success);
        verify(studyMemberMapper, times(2)).insert(any(StudyMember.class));
    }

    @Test
    void batchMemberJoin_emptyList_returnsZero() {
        int success = service.batchMemberJoin(1L, List.of());
        assertEquals(0, success);
        verify(studyMemberMapper, never()).insert(any(com.sap.entity.StudyMember.class));
    }

    // ===================== reassignMember =====================

    @Test
    void reassignMember_success_updatesLeaderId() {
        StudyMember m = new StudyMember();
        m.setId(5L);
        m.setLeaderId(1L);
        when(studyMemberMapper.selectById(5L)).thenReturn(m);

        service.reassignMember(5L, 9L);

        assertEquals(9L, m.getLeaderId());
        verify(studyMemberMapper).updateById(m);
    }

    @Test
    void reassignMember_notFound_throws() {
        when(studyMemberMapper.selectById(5L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reassignMember(5L, 9L));
        assertEquals("成员不存在", ex.getMessage());
    }
}
