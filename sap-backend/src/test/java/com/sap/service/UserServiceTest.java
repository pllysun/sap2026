package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Position;
import com.sap.entity.Setting;
import com.sap.entity.Term;
import com.sap.entity.User;
import com.sap.entity.UserRole;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.TermMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest extends BaseUnitTest {

    @Mock UserMapper userMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock SettingMapper settingMapper;
    @Mock TermMapper termMapper;
    @Mock PositionMapper positionMapper;
    @Mock CacheService cacheService;

    @InjectMocks UserService service;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setStudentId("S" + id);
        u.setName("n" + id);
        return u;
    }

    private UserRole role(long userId, int code) {
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleCode(code);
        return ur;
    }

    // ===================== listUsers =====================

    @Test
    void listUsers_withKeyword_buildsFilterAndReturnsPage() {
        when(userMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<User> p = inv.getArgument(0);
            p.setRecords(List.of(user(1)));
            p.setTotal(1);
            return p;
        });

        Page<User> page = service.listUsers(1, 10, "abc");

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
    }

    @Test
    void listUsers_nullKeyword_noFilter() {
        when(userMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<User> p = inv.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        Page<User> page = service.listUsers(2, 5, null);
        assertEquals(0, page.getTotal());
    }

    @Test
    void listUsers_emptyKeyword_noFilter() {
        when(userMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<User> p = inv.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        Page<User> page = service.listUsers(1, 10, "");
        assertNotNull(page);
    }

    // ===================== getUserById =====================

    @Test
    void getUserById_delegatesToMapper() {
        User u = user(7);
        when(userMapper.selectById(7L)).thenReturn(u);
        assertSame(u, service.getUserById(7L));
    }

    // ===================== updateUser =====================

    @Test
    void updateUser_success_copiesEditableFields() {
        User existing = user(3);
        when(userMapper.selectById(3L)).thenReturn(existing);

        User incoming = new User();
        incoming.setNickname("nk");
        incoming.setGender(2);
        incoming.setQq("999");
        incoming.setAvatar("/a.png");
        incoming.setStatus(0);

        service.updateUser(3L, incoming);

        assertEquals("nk", existing.getNickname());
        assertEquals(2, existing.getGender());
        assertEquals("999", existing.getQq());
        assertEquals("/a.png", existing.getAvatar());
        assertEquals(0, existing.getStatus());
        verify(userMapper).updateById(existing);
        verify(cacheService).updateUser(existing);
    }

    @Test
    void updateUser_notFound_throws() {
        when(userMapper.selectById(8L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateUser(8L, new User()));
        assertEquals("用户不存在", ex.getMessage());
    }

    // ===================== updateUserRole =====================

    @Test
    void updateUserRole_superAdmin_canAssignAnyRole() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getRoleList).thenReturn(List.of("0"));

            service.updateUserRole(5L, List.of(1, 1, 3)); // dup 1 -> deduped

            verify(userRoleMapper).delete(any());
            ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
            verify(userRoleMapper, times(2)).insert(captor.capture());
            List<Integer> inserted = captor.getAllValues().stream().map(UserRole::getRoleCode).toList();
            assertEquals(List.of(1, 3), inserted);
        }
    }

    @Test
    void updateUserRole_nonSuperAdmin_grantingHighRole_throws() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getRoleList).thenReturn(List.of("1"));
            when(userRoleMapper.selectRoleCodesByUserId(5L)).thenReturn(List.of(3));

            // grantMin = 1 -> blocked
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateUserRole(5L, List.of(1, 3)));
            assertEquals("无权授予或修改高于自身权限的角色", ex.getMessage());
            verify(userRoleMapper, never()).insert(any());
        }
    }

    @Test
    void updateUserRole_nonSuperAdmin_targetIsHighRole_throws() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getRoleList).thenReturn(List.of("2"));
            // grant only low roles, but target currently has role 1 -> blocked
            when(userRoleMapper.selectRoleCodesByUserId(5L)).thenReturn(List.of(1));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateUserRole(5L, List.of(3, 4)));
            assertEquals("无权授予或修改高于自身权限的角色", ex.getMessage());
        }
    }

    @Test
    void updateUserRole_nonSuperAdmin_lowRolesOnly_allowed() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getRoleList).thenReturn(List.of("2"));
            when(userRoleMapper.selectRoleCodesByUserId(5L)).thenReturn(List.of(3, 4));

            service.updateUserRole(5L, List.of(3));

            verify(userRoleMapper).delete(any());
            verify(userRoleMapper, times(1)).insert(any());
        }
    }

    @Test
    void updateUserRole_nullRoleCodes_treatedAsEmpty() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getRoleList).thenReturn(List.of("0"));

            service.updateUserRole(5L, null);

            verify(userRoleMapper).delete(any());
            verify(userRoleMapper, never()).insert(any());
        }
    }

    @Test
    void updateUserRole_nonSuperAdmin_emptyCodesAndNoTargetRoles_allowed() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getRoleList).thenReturn(List.of("2"));
            when(userRoleMapper.selectRoleCodesByUserId(5L)).thenReturn(List.of());

            // grantMin = MAX_VALUE, targetMin = MAX_VALUE -> allowed
            service.updateUserRole(5L, List.of());

            verify(userRoleMapper).delete(any());
            verify(userRoleMapper, never()).insert(any());
        }
    }

    // ===================== getUserRoles =====================

    @Test
    void getUserRoles_delegates() {
        when(userRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of(3, 4));
        assertEquals(List.of(3, 4), service.getUserRoles(2L));
    }

    // ===================== countUsers =====================

    @Test
    void countUsers_delegates() {
        when(userMapper.selectCount(null)).thenReturn(12L);
        assertEquals(12L, service.countUsers());
    }

    // ===================== upgradeToMember =====================

    @Test
    void upgradeToMember_success_insertsRoleAndTerm() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        when(userRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of(4));

        Setting grade = new Setting();
        grade.setSettingValue("2026");
        when(settingMapper.selectOne(any())).thenReturn(grade);

        Position pos = new Position();
        pos.setId(9);
        pos.setPositionName("成员");
        when(positionMapper.selectOne(any())).thenReturn(pos);

        when(termMapper.selectCount(any())).thenReturn(0L);

        service.upgradeToMember(1L);

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertEquals(3, roleCaptor.getValue().getRoleCode());

        ArgumentCaptor<Term> termCaptor = ArgumentCaptor.forClass(Term.class);
        verify(termMapper).insert(termCaptor.capture());
        assertEquals(1L, termCaptor.getValue().getUserId());
        assertEquals("2026", termCaptor.getValue().getGrade());
        assertEquals(9, termCaptor.getValue().getPositionId());
    }

    @Test
    void upgradeToMember_termAlreadyExists_skipsTermInsert() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        when(userRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of(4));
        when(settingMapper.selectOne(any())).thenReturn(null); // default grade 2025

        Position pos = new Position();
        pos.setId(9);
        pos.setPositionName("成员");
        when(positionMapper.selectOne(any())).thenReturn(pos);

        when(termMapper.selectCount(any())).thenReturn(1L); // already exists

        service.upgradeToMember(1L);

        verify(userRoleMapper).insert(any(UserRole.class));
        verify(termMapper, never()).insert(any());
    }

    @Test
    void upgradeToMember_userNotFound_throws() {
        when(userMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upgradeToMember(1L));
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void upgradeToMember_alreadyMember_throws() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        when(userRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of(3));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upgradeToMember(1L));
        assertEquals("该用户已是成员或更高权限，无需升级", ex.getMessage());
        verify(termMapper, never()).insert(any());
    }

    @Test
    void upgradeToMember_positionMissing_throws() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        when(userRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of(4));
        when(settingMapper.selectOne(any())).thenReturn(null);
        when(positionMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upgradeToMember(1L));
        assertEquals("未找到成员身份配置", ex.getMessage());
    }

    // ===================== batchUpgradeToMember =====================

    @Test
    void batchUpgradeToMember_countsOnlySuccesses() {
        // user 1: success ; user 2: already member -> skipped
        when(userMapper.selectById(1L)).thenReturn(user(1));
        when(userMapper.selectById(2L)).thenReturn(user(2));
        when(userRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of(4));
        when(userRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of(3));

        Setting grade = new Setting();
        grade.setSettingValue("2026");
        when(settingMapper.selectOne(any())).thenReturn(grade);
        Position pos = new Position();
        pos.setId(9);
        pos.setPositionName("成员");
        when(positionMapper.selectOne(any())).thenReturn(pos);
        when(termMapper.selectCount(any())).thenReturn(0L);

        int success = service.batchUpgradeToMember(List.of(1L, 2L));
        assertEquals(1, success);
    }

    @Test
    void batchUpgradeToMember_emptyList_returnsZero() {
        assertEquals(0, service.batchUpgradeToMember(List.of()));
    }

    // ===================== listMemberUsers =====================

    @Test
    void listMemberUsers_returnsUsersForDistinctIds() {
        when(userRoleMapper.selectList(any()))
                .thenReturn(List.of(role(1L, 2), role(1L, 3), role(2L, 1)));
        List<User> users = List.of(user(1), user(2));
        when(userMapper.selectBatchIds(any())).thenReturn(users);

        List<User> result = service.listMemberUsers();
        assertEquals(2, result.size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idCaptor = ArgumentCaptor.forClass(List.class);
        verify(userMapper).selectBatchIds(idCaptor.capture());
        assertEquals(List.of(1L, 2L), idCaptor.getValue());
    }

    @Test
    void listMemberUsers_noMembers_returnsEmptyWithoutQuery() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        List<User> result = service.listMemberUsers();
        assertTrue(result.isEmpty());
        verify(userMapper, never()).selectBatchIds(any());
    }
}
