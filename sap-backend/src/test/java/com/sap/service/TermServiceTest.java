package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Position;
import com.sap.entity.Setting;
import com.sap.entity.StudyActivity;
import com.sap.entity.Term;
import com.sap.entity.UserRole;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.StudyActivityMapper;
import com.sap.mapper.TermMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TermServiceTest extends BaseUnitTest {

    @Mock TermMapper termMapper;
    @Mock UserMapper userMapper;
    @Mock PositionMapper positionMapper;
    @Mock SettingMapper settingMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock CacheService cacheService;
    @Mock StudyActivityMapper studyActivityMapper;
    @Mock JoinService joinService;

    @InjectMocks TermService service;

    private Term term(long id, long userId, int posId, String grade) {
        Term t = new Term();
        t.setId(id);
        t.setUserId(userId);
        t.setPositionId(posId);
        t.setGrade(grade);
        return t;
    }

    private Position pos(int id, String name, int sortOrder, Integer isSystem,
                         int maxCount, Integer roleCode) {
        Position p = new Position();
        p.setId(id);
        p.setPositionName(name);
        p.setSortOrder(sortOrder);
        p.setIsSystem(isSystem);
        p.setMaxCount(maxCount);
        p.setRoleCode(roleCode);
        return p;
    }

    private Map<String, Object> assignment(Object positionId, List<?> userIds) {
        Map<String, Object> m = new HashMap<>();
        m.put("positionId", positionId);
        m.put("userIds", userIds);
        return m;
    }

    private Setting grade(String value) {
        Setting s = new Setting();
        s.setSettingKey("current_grade");
        s.setSettingValue(value);
        return s;
    }

    // ===================== listByGradePaged =====================
    @Test
    void listByGradePaged_empty_returnsEmptyPage() {
        when(termMapper.selectList(any())).thenReturn(new ArrayList<>());
        Page<Map<String, Object>> page = service.listByGradePaged("2025", 1, 10);
        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords() == null || page.getRecords().isEmpty());
    }

    @Test
    void listByGradePaged_sortsBySortOrderAndPaginates() {
        List<Term> terms = new ArrayList<>(List.of(
                term(1, 100, 10, "2025"),  // sortOrder 5
                term(2, 200, 20, "2025")   // sortOrder 1
        ));
        when(termMapper.selectList(any())).thenReturn(terms);
        when(positionMapper.selectById(10)).thenReturn(pos(10, "成员", 5, 0, 1, 3));
        when(positionMapper.selectById(20)).thenReturn(pos(20, "会长", 1, 1, 1, 1));
        when(cacheService.getUserName(any())).thenReturn("name");
        when(cacheService.getStudentId(any())).thenReturn("sid");
        when(cacheService.getPositionName(any())).thenReturn("posName");

        Page<Map<String, Object>> page = service.listByGradePaged("2025", 1, 10);

        assertEquals(2, page.getTotal());
        // sortOrder 1 (posId 20) should be first
        assertEquals(20, page.getRecords().get(0).get("positionId"));
        assertEquals(10, page.getRecords().get(1).get("positionId"));
    }

    @Test
    void listByGradePaged_positionNullOrSortOrderNull_uses999() {
        List<Term> terms = new ArrayList<>(List.of(term(1, 100, 10, "2025")));
        when(termMapper.selectList(any())).thenReturn(terms);
        when(positionMapper.selectById(10)).thenReturn(null); // -> 999
        when(cacheService.getUserName(any())).thenReturn("n");
        when(cacheService.getStudentId(any())).thenReturn("s");
        when(cacheService.getPositionName(any())).thenReturn("p");

        Page<Map<String, Object>> page = service.listByGradePaged("2025", 1, 10);
        assertEquals(1, page.getTotal());
    }

    @Test
    void listByGradePaged_secondPageBeyondData_emptyRecords() {
        List<Term> terms = new ArrayList<>(List.of(term(1, 100, 10, "2025")));
        when(termMapper.selectList(any())).thenReturn(terms);
        when(positionMapper.selectById(10)).thenReturn(pos(10, "成员", 5, 0, 1, 3));

        Page<Map<String, Object>> page = service.listByGradePaged("2025", 5, 10);
        assertEquals(1, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    // ===================== listByGrade =====================
    @Test
    void listByGrade_empty_returnsEmptyList() {
        when(termMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertTrue(service.listByGrade("2025").isEmpty());
    }

    @Test
    void listByGrade_mapsRecords() {
        when(termMapper.selectList(any())).thenReturn(List.of(term(1, 100, 10, "2025")));
        when(cacheService.getUserName(100L)).thenReturn("张三");
        when(cacheService.getStudentId(100L)).thenReturn("2025001");
        when(cacheService.getPositionName(10)).thenReturn("会长");

        List<Map<String, Object>> result = service.listByGrade("2025");
        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).get("userName"));
        assertEquals("2025001", result.get(0).get("studentId"));
        assertEquals("会长", result.get(0).get("positionName"));
    }

    // ===================== listGrades =====================
    @Test
    void listGrades_returnsDistinctGrades() {
        when(termMapper.selectList(any())).thenReturn(List.of(
                term(1, 1, 1, "2025"), term(2, 2, 1, "2024")));
        assertEquals(List.of("2025", "2024"), service.listGrades());
    }

    // ===================== addTerm =====================
    @Test
    void addTerm_withGrade_noDuplicate_inserts() {
        Term t = term(0, 1, 10, "2025");
        when(termMapper.selectCount(any())).thenReturn(0L);
        service.addTerm(t);
        verify(termMapper).insert(t);
    }

    @Test
    void addTerm_blankGrade_usesSetting() {
        Term t = term(0, 1, 10, "");
        when(settingMapper.selectOne(any())).thenReturn(grade("2030"));
        when(termMapper.selectCount(any())).thenReturn(0L);
        service.addTerm(t);
        assertEquals("2030", t.getGrade());
        verify(termMapper).insert(t);
    }

    @Test
    void addTerm_nullGradeNoSetting_defaults2025() {
        Term t = term(0, 1, 10, null);
        when(settingMapper.selectOne(any())).thenReturn(null);
        when(termMapper.selectCount(any())).thenReturn(0L);
        service.addTerm(t);
        assertEquals("2025", t.getGrade());
    }

    @Test
    void addTerm_duplicate_throws() {
        Term t = term(0, 1, 10, "2025");
        when(termMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addTerm(t));
        assertEquals("该用户在该年级已存在相同身份", ex.getMessage());
        verify(termMapper, never()).insert(any());
    }

    // ===================== deleteTerm =====================
    @Test
    void deleteTerm_deletesById() {
        service.deleteTerm(7L);
        verify(termMapper).deleteById(7L);
    }

    // ===================== changeover =====================
    @Test
    void changeover_settingNull_throws() {
        when(settingMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeover(List.of()));
        assertEquals("未找到年级设置", ex.getMessage());
    }

    @Test
    void changeover_gradeNotNumeric_throwsBusinessException() {
        when(settingMapper.selectOne(any())).thenReturn(grade("abc"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeover(List.of()));
        assertTrue(ex.getMessage().contains("当前年级配置非法"));
    }

    @Test
    void changeover_systemPositionNotAssigned_throws() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        Position president = pos(1, "会长", 1, 1, 1, 1); // isSystem=1 required
        when(positionMapper.selectList(any())).thenReturn(List.of(president));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeover(new ArrayList<>())); // empty assignments
        assertEquals("必须选择 会长", ex.getMessage());
    }

    @Test
    void changeover_nullAssignments_systemPositionMissing_throws() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        Position president = pos(1, "会长", 1, 1, 1, 1);
        when(positionMapper.selectList(any())).thenReturn(List.of(president));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeover(null));
        assertEquals("必须选择 会长", ex.getMessage());
    }

    @Test
    void changeover_duplicateUserAcrossPositions_throws() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        // no system positions to satisfy
        Position p1 = pos(1, "副会长", 2, 0, 2, 2);
        Position p2 = pos(2, "部长", 3, 0, 2, 2);
        when(positionMapper.selectList(any())).thenReturn(List.of(p1, p2));
        when(cacheService.getUserName(100L)).thenReturn("张三");

        List<Map<String, Object>> assignments = List.of(
                assignment(1, List.of(100)),
                assignment(2, List.of(100)) // same user
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeover(assignments));
        assertTrue(ex.getMessage().contains("张三"));
        assertTrue(ex.getMessage().contains("一人只能担任一个职位"));
    }

    @Test
    void changeover_userIdsNullSkippedInDuplicateCheck() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        when(positionMapper.selectList(any())).thenReturn(new ArrayList<>());
        // assignment with null userIds is skipped everywhere
        List<Map<String, Object>> assignments = new ArrayList<>();
        Map<String, Object> a = new HashMap<>();
        a.put("positionId", 1);
        a.put("userIds", null);
        assignments.add(a);

        service.changeover(assignments);

        verify(termMapper, never()).insert(any());
        verify(settingMapper).updateById(any());
    }

    @Test
    void changeover_positionIdNotInMap_throws() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        when(positionMapper.selectList(any())).thenReturn(new ArrayList<>()); // empty map
        List<Map<String, Object>> assignments = List.of(assignment(99, List.of(100)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeover(assignments));
        assertEquals("身份ID不存在: 99", ex.getMessage());
    }

    @Test
    void changeover_exceedMaxCount_throws() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        Position p1 = pos(1, "副会长", 2, 0, 1, 2); // maxCount=1
        when(positionMapper.selectList(any())).thenReturn(List.of(p1));

        List<Map<String, Object>> assignments = List.of(assignment(1, List.of(100, 200)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeover(assignments));
        assertTrue(ex.getMessage().contains("最多选 1 人"));
    }

    @Test
    void changeover_emptyUserIds_skipped() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        Position p1 = pos(1, "副会长", 2, 0, 2, 2);
        when(positionMapper.selectList(any())).thenReturn(List.of(p1));

        List<Map<String, Object>> assignments = List.of(assignment(1, List.of()));
        service.changeover(assignments);

        verify(termMapper, never()).insert(any());
        verify(settingMapper).updateById(any());
    }

    @Test
    void changeover_success_insertsTermsAndRoles_archivesAndInitsManagers() {
        Setting current = grade("2025");
        when(settingMapper.selectOne(any())).thenReturn(current);
        Position president = pos(1, "会长", 1, 1, 1, 1); // system, roleCode=1
        Position member = pos(2, "副会长", 2, 0, 2, null); // roleCode null -> skip role
        when(positionMapper.selectList(any())).thenReturn(List.of(president, member));
        when(userRoleMapper.selectCount(any())).thenReturn(0L); // no existing role

        List<Map<String, Object>> assignments = List.of(
                assignment(1, List.of(100)),
                assignment(2, List.of(200))
        );

        service.changeover(assignments);

        // 2 terms inserted
        verify(termMapper, times(2)).insert(any(Term.class));
        // only president (roleCode=1) inserts a UserRole; member has null roleCode
        verify(userRoleMapper, times(1)).insert(any(UserRole.class));
        // new grade is 2026 (currentGradeNum + 1)
        assertEquals("2026", current.getSettingValue());
        verify(settingMapper).updateById(any(Setting.class));
        // archive update of in-progress study activities
        verify(studyActivityMapper).update(any(StudyActivity.class), any());
        verify(joinService).initManagers();
    }

    @Test
    void changeover_roleAlreadyExists_skipsRoleInsert() {
        when(settingMapper.selectOne(any())).thenReturn(grade("2025"));
        Position president = pos(1, "会长", 1, 1, 1, 1);
        when(positionMapper.selectList(any())).thenReturn(List.of(president));
        when(userRoleMapper.selectCount(any())).thenReturn(2L); // already has role

        List<Map<String, Object>> assignments = List.of(assignment(1, List.of(100)));
        service.changeover(assignments);

        verify(termMapper, times(1)).insert(any(Term.class));
        verify(userRoleMapper, never()).insert(any());
        verify(settingMapper).updateById(any(Setting.class));
    }
}
