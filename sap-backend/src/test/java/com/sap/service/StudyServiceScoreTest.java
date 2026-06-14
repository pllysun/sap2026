package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.StudyLeader;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 重点覆盖 score() 的全部校验分支，以及 scoreOverview()。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyServiceScoreTest extends BaseUnitTest {

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

    /** 构造一个通过所有前置校验的合法评分对象 */
    private StudyScore validScore() {
        StudyScore s = new StudyScore();
        s.setActivityId(1L);
        s.setWeek(2);
        s.setMemberUserId(50L);
        s.setLeaderUserId(9L);
        s.setScore(8);
        s.setComment("这是一段不少于十个字的评语内容");
        return s;
    }

    private StudyLeader leaderEntity(long id) {
        StudyLeader l = new StudyLeader();
        l.setId(id);
        l.setActivityId(1L);
        l.setUserId(9L);
        return l;
    }

    private StudyMember memberEntity(Long leaderId) {
        StudyMember m = new StudyMember();
        m.setActivityId(1L);
        m.setUserId(50L);
        m.setWeek(2);
        m.setLeaderId(leaderId);
        return m;
    }

    // ===================== score 校验分支 =====================

    @Test
    void score_scoreBelowOne_throws() {
        StudyScore s = validScore();
        s.setScore(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("分数范围为1-10", ex.getMessage());
    }

    @Test
    void score_scoreAboveTen_throws() {
        StudyScore s = validScore();
        s.setScore(11);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("分数范围为1-10", ex.getMessage());
    }

    @Test
    void score_commentNull_throws() {
        StudyScore s = validScore();
        s.setComment(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("评语不得少于10个字", ex.getMessage());
    }

    @Test
    void score_commentTooShort_throws() {
        StudyScore s = validScore();
        s.setComment("太短了"); // < 10 字
        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("评语不得少于10个字", ex.getMessage());
    }

    @Test
    void score_notLeader_throws() {
        StudyScore s = validScore();
        when(studyLeaderMapper.selectOne(any())).thenReturn(null); // 评分人不是负责人
        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("无权评分：您不是该学习活动的负责人", ex.getMessage());
    }

    @Test
    void score_memberNotInList_throws() {
        StudyScore s = validScore();
        when(studyLeaderMapper.selectOne(any())).thenReturn(leaderEntity(7L));
        when(studyMemberMapper.selectOne(any())).thenReturn(null); // 成员不在名单
        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("该成员本周期不在学习名单中", ex.getMessage());
    }

    @Test
    void score_memberAssignedToAnotherLeader_throws() {
        StudyScore s = validScore();
        when(studyLeaderMapper.selectOne(any())).thenReturn(leaderEntity(7L));
        // 成员分配给负责人 99，与当前评分人 7 不符
        when(studyMemberMapper.selectOne(any())).thenReturn(memberEntity(99L));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("无权评分：该成员未分配给您", ex.getMessage());
    }

    @Test
    void score_alreadyScored_throws() {
        StudyScore s = validScore();
        when(studyLeaderMapper.selectOne(any())).thenReturn(leaderEntity(7L));
        when(studyMemberMapper.selectOne(any())).thenReturn(memberEntity(7L));
        when(studyScoreMapper.selectCount(any())).thenReturn(1L); // 已评分

        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("该成员本周期已评分", ex.getMessage());
        verify(studyScoreMapper, never()).insert(any(com.sap.entity.StudyScore.class));
    }

    @Test
    void score_duplicateKeyOnInsert_throwsBusinessException() {
        StudyScore s = validScore();
        when(studyLeaderMapper.selectOne(any())).thenReturn(leaderEntity(7L));
        when(studyMemberMapper.selectOne(any())).thenReturn(memberEntity(7L));
        when(studyScoreMapper.selectCount(any())).thenReturn(0L);
        when(studyScoreMapper.insert(any(com.sap.entity.StudyScore.class))).thenThrow(new DuplicateKeyException("uk_activity_week_member"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.score(s));
        assertEquals("该成员本周期已评分", ex.getMessage());
    }

    @Test
    void score_success_insertsScore() {
        StudyScore s = validScore();
        when(studyLeaderMapper.selectOne(any())).thenReturn(leaderEntity(7L));
        when(studyMemberMapper.selectOne(any())).thenReturn(memberEntity(7L));
        when(studyScoreMapper.selectCount(any())).thenReturn(0L);
        when(studyScoreMapper.insert(any(com.sap.entity.StudyScore.class))).thenReturn(1);

        service.score(s);

        verify(studyScoreMapper).insert(s);
    }

    @Test
    void score_memberLeaderIdNull_passesPermissionCheck() {
        // member.getLeaderId() == null 时不校验归属，应放行
        StudyScore s = validScore();
        when(studyLeaderMapper.selectOne(any())).thenReturn(leaderEntity(7L));
        when(studyMemberMapper.selectOne(any())).thenReturn(memberEntity(null));
        when(studyScoreMapper.selectCount(any())).thenReturn(0L);
        when(studyScoreMapper.insert(any(com.sap.entity.StudyScore.class))).thenReturn(1);

        service.score(s);

        verify(studyScoreMapper).insert(s);
    }

    @Test
    void score_boundaryScoreOne_andCommentExactlyTen_succeeds() {
        StudyScore s = validScore();
        s.setScore(1);
        s.setComment("0123456789"); // 恰好 10 字
        when(studyLeaderMapper.selectOne(any())).thenReturn(leaderEntity(7L));
        when(studyMemberMapper.selectOne(any())).thenReturn(memberEntity(7L));
        when(studyScoreMapper.selectCount(any())).thenReturn(0L);
        when(studyScoreMapper.insert(any(com.sap.entity.StudyScore.class))).thenReturn(1);

        service.score(s);
        verify(studyScoreMapper).insert(s);
    }

    // ===================== scoreOverview =====================

    @Test
    void scoreOverview_mapsScoresWithCacheNames() {
        StudyScore sc = new StudyScore();
        sc.setId(1L);
        sc.setWeek(2);
        sc.setScore(9);
        sc.setComment("评语内容十个字以上的");
        sc.setMemberUserId(50L);
        sc.setLeaderUserId(9L);
        when(studyScoreMapper.selectList(any())).thenReturn(List.of(sc));
        when(cacheService.getUserName(50L)).thenReturn("成员甲");
        when(cacheService.getStudentId(50L)).thenReturn("S50");
        when(cacheService.getUserName(9L)).thenReturn("负责人乙");

        List<Map<String, Object>> result = service.scoreOverview(1L, 2);

        assertEquals(1, result.size());
        Map<String, Object> m = result.get(0);
        assertEquals(9, m.get("score"));
        assertEquals("成员甲", m.get("memberName"));
        assertEquals("S50", m.get("memberStudentId"));
        assertEquals("负责人乙", m.get("leaderName"));
    }

    @Test
    void scoreOverview_nullWeek_doesNotFilterByWeek() {
        when(studyScoreMapper.selectList(any())).thenReturn(List.of());
        List<Map<String, Object>> result = service.scoreOverview(1L, null);
        assertTrue(result.isEmpty());
        verify(studyScoreMapper).selectList(any());
    }
}
