package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Position;
import com.sap.entity.Setting;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyLeader;
import com.sap.entity.StudyMember;
import com.sap.entity.Term;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖 StudyService 的活动生命周期：createActivity / closeActivity / setActiveWeek / nextWeek
 * 及自动添加负责人 autoAddLeadersFromTerm 的各分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyServiceCreateTest extends BaseUnitTest {

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

    private StudyActivity activity(long id, String grade, Integer seq, int currentWeek, int status) {
        StudyActivity a = new StudyActivity();
        a.setId(id);
        a.setGrade(grade);
        a.setSeqNum(seq);
        a.setCurrentWeek(currentWeek);
        a.setActiveWeek(currentWeek);
        a.setStatus(status);
        return a;
    }

    private Position pos(int id) {
        Position p = new Position();
        p.setId(id);
        return p;
    }

    private User user(long id, String studentId) {
        User u = new User();
        u.setId(id);
        u.setStudentId(studentId);
        return u;
    }

    // ===================== createActivity =====================

    @Test
    void createActivity_usesSettingGrade_seqMaxPlusOne_archivesActives() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        // 该年级最大序号活动
        when(studyActivityMapper.selectOne(any())).thenReturn(activity(9, "2026", 5, 3, 1));
        // 同年级进行中活动需归档
        StudyActivity activeOne = activity(7, "2026", 4, 2, 1);
        when(studyActivityMapper.selectList(any())).thenReturn(List.of(activeOne));
        // 自动添加负责人：无管理层身份则直接 return
        when(cacheService.getAdminPositions()).thenReturn(List.of());

        StudyActivity toCreate = new StudyActivity();
        toCreate.setTitle("新活动");

        service.createActivity(toCreate);

        assertEquals("2026", toCreate.getGrade());
        assertEquals(6, toCreate.getSeqNum(), "应为 max(5)+1");
        assertEquals(1, toCreate.getCurrentWeek());
        assertEquals(1, toCreate.getActiveWeek());
        assertEquals(1, toCreate.getStatus());

        // 已存在的进行中活动被归档为 0
        assertEquals(0, activeOne.getStatus());
        verify(studyActivityMapper).updateById(activeOne);
        verify(studyActivityMapper).insert(toCreate);
    }

    @Test
    void createActivity_firstOfGrade_seqIsOne_noSettingDefaults2025() {
        when(settingMapper.selectOne(any())).thenReturn(null); // 无 setting -> 默认 2025
        when(studyActivityMapper.selectOne(any())).thenReturn(null); // 该年级无活动
        when(studyActivityMapper.selectList(any())).thenReturn(List.of()); // 无进行中活动
        when(cacheService.getAdminPositions()).thenReturn(List.of());

        StudyActivity toCreate = new StudyActivity();
        service.createActivity(toCreate);

        assertEquals("2025", toCreate.getGrade());
        assertEquals(1, toCreate.getSeqNum());
        verify(studyActivityMapper).insert(toCreate);
        verify(studyActivityMapper, never()).updateById(any(com.sap.entity.StudyActivity.class));
    }

    @Test
    void createActivity_lastActivitySeqNull_fallsBackToSeqOne() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        // 存在活动但 seqNum 为 null
        when(studyActivityMapper.selectOne(any())).thenReturn(activity(3, "2026", null, 1, 0));
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());
        when(cacheService.getAdminPositions()).thenReturn(List.of());

        StudyActivity toCreate = new StudyActivity();
        service.createActivity(toCreate);

        assertEquals(1, toCreate.getSeqNum());
    }

    @Test
    void createActivity_autoAddsLeadersFromTerm_dedupesUsers() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        when(studyActivityMapper.selectOne(any())).thenReturn(null);
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());
        when(cacheService.getAdminPositions()).thenReturn(List.of(pos(1), pos(2)));

        Term t1 = new Term();
        t1.setUserId(100L);
        t1.setPositionId(1);
        Term t2 = new Term();
        t2.setUserId(100L); // 重复 userId，应跳过
        t2.setPositionId(2);
        Term t3 = new Term();
        t3.setUserId(200L);
        t3.setPositionId(2);
        when(termMapper.selectList(any())).thenReturn(List.of(t1, t2, t3));

        when(cacheService.getUserById(100L)).thenReturn(user(100L, "S100"));
        when(cacheService.getUserById(200L)).thenReturn(user(200L, "S200"));

        StudyActivity toCreate = new StudyActivity();
        service.createActivity(toCreate);

        // 去重后仅插入 2 个负责人
        verify(studyLeaderMapper, times(2)).insert(any(StudyLeader.class));
    }

    @Test
    void createActivity_autoAddLeaders_skipsWhenUserMissingInCache() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2026"));
        when(studyActivityMapper.selectOne(any())).thenReturn(null);
        when(studyActivityMapper.selectList(any())).thenReturn(List.of());
        when(cacheService.getAdminPositions()).thenReturn(List.of(pos(1)));

        Term t1 = new Term();
        t1.setUserId(300L);
        t1.setPositionId(1);
        when(termMapper.selectList(any())).thenReturn(List.of(t1));
        when(cacheService.getUserById(300L)).thenReturn(null); // 缓存无此用户

        service.createActivity(new StudyActivity());

        verify(studyLeaderMapper, never()).insert(any(StudyLeader.class));
    }

    // ===================== closeActivity =====================

    @Test
    void closeActivity_setsStatusZero() {
        StudyActivity a = activity(5, "2026", 1, 2, 1);
        when(studyActivityMapper.selectById(5L)).thenReturn(a);

        service.closeActivity(5L);

        assertEquals(0, a.getStatus());
        verify(studyActivityMapper).updateById(a);
    }

    @Test
    void closeActivity_notFound_throws() {
        when(studyActivityMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.closeActivity(99L));
        assertEquals("活动不存在", ex.getMessage());
    }

    // ===================== setActiveWeek =====================

    @Test
    void setActiveWeek_validWeek_updates() {
        StudyActivity a = activity(5, "2026", 1, 4, 1);
        when(studyActivityMapper.selectById(5L)).thenReturn(a);

        service.setActiveWeek(5L, 3);

        assertEquals(3, a.getActiveWeek());
        verify(studyActivityMapper).updateById(a);
    }

    @Test
    void setActiveWeek_notFound_throws() {
        when(studyActivityMapper.selectById(5L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setActiveWeek(5L, 1));
        assertEquals("活动不存在", ex.getMessage());
    }

    @Test
    void setActiveWeek_belowOne_throws() {
        StudyActivity a = activity(5, "2026", 1, 4, 1);
        when(studyActivityMapper.selectById(5L)).thenReturn(a);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setActiveWeek(5L, 0));
        assertEquals("周期范围无效，当前最大周期为 4", ex.getMessage());
        verify(studyActivityMapper, never()).updateById(any(com.sap.entity.StudyActivity.class));
    }

    @Test
    void setActiveWeek_aboveMax_throws() {
        StudyActivity a = activity(5, "2026", 1, 4, 1);
        when(studyActivityMapper.selectById(5L)).thenReturn(a);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setActiveWeek(5L, 5));
        assertEquals("周期范围无效，当前最大周期为 4", ex.getMessage());
    }

    // ===================== nextWeek =====================

    @Test
    void nextWeek_notFound_throws() {
        when(studyActivityMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.nextWeek(1L));
        assertEquals("活动不存在", ex.getMessage());
    }

    @Test
    void nextWeek_distributesPrevMembersAcrossLeadersRoundRobin() {
        StudyActivity a = activity(1, "2026", 1, 2, 1);
        when(studyActivityMapper.selectById(1L)).thenReturn(a);

        // 上一周期 3 个去重成员
        StudyMember mem1 = member(10L);
        StudyMember mem2 = member(11L);
        StudyMember mem3 = member(12L);
        when(studyMemberMapper.selectList(any())).thenReturn(List.of(mem1, mem2, mem3));

        StudyLeader l1 = leader(1L);
        StudyLeader l2 = leader(2L);
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of(l1, l2));

        service.nextWeek(1L);

        // currentWeek/activeWeek 推进为 3
        assertEquals(3, a.getCurrentWeek());
        assertEquals(3, a.getActiveWeek());
        verify(studyActivityMapper).updateById(a);

        // 3 个成员被插入新周期
        ArgumentCaptor<StudyMember> captor = ArgumentCaptor.forClass(StudyMember.class);
        verify(studyMemberMapper, times(3)).insert(captor.capture());
        for (StudyMember m : captor.getAllValues()) {
            assertEquals(3, m.getWeek());
            assertEquals(1L, m.getActivityId());
            assertNotNull(m.getLeaderId());
        }
    }

    @Test
    void nextWeek_noLeaders_advancesWeekButInsertsNoMembers() {
        StudyActivity a = activity(1, "2026", 1, 1, 1);
        when(studyActivityMapper.selectById(1L)).thenReturn(a);
        when(studyMemberMapper.selectList(any())).thenReturn(List.of(member(10L)));
        when(studyLeaderMapper.selectList(any())).thenReturn(List.of()); // 无负责人

        service.nextWeek(1L);

        assertEquals(2, a.getCurrentWeek());
        verify(studyMemberMapper, never()).insert(any(com.sap.entity.StudyMember.class));
    }

    private StudyMember member(long userId) {
        StudyMember m = new StudyMember();
        m.setUserId(userId);
        m.setActivityId(1L);
        return m;
    }

    private StudyLeader leader(long id) {
        StudyLeader l = new StudyLeader();
        l.setId(id);
        l.setActivityId(1L);
        return l;
    }
}
