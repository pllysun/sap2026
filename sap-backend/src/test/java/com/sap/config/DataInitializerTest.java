package com.sap.config;

import com.sap.BaseUnitTest;
import com.sap.entity.Role;
import com.sap.entity.User;
import com.sap.entity.UserRole;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.RoleMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import com.sap.service.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DataInitializer 单元测试：覆盖角色 / 职位 / 超管账号的幂等初始化分支。
 * 超管逻辑仅在私有环境显式配置后生效；补齐角色不重置现有密码。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataInitializerTest extends BaseUnitTest {

    @Mock RoleMapper roleMapper;
    @Mock PositionMapper positionMapper;
    @Mock UserMapper userMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock SettingService settingService;

    @InjectMocks DataInitializer initializer;

    @BeforeEach
    void configureTestOnlyBootstrap() {
        ReflectionTestUtils.setField(initializer, "bootstrapAdminStudentId", "99000001");
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", "bootstrap-test-only-password");
    }

    @Test
    void ensureSuperAdmin_disabledByDefault() {
        ReflectionTestUtils.setField(initializer, "bootstrapAdminStudentId", "");
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", "");
        initializer.run();
        verifyNoInteractions(userMapper, userRoleMapper);
    }

    @Test
    void ensureSuperAdmin_requiresBothPrivateValues() {
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", " ");
        initializer.run();
        verifyNoInteractions(userMapper, userRoleMapper);
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", "test-only-password");
        ReflectionTestUtils.setField(initializer, "bootstrapAdminStudentId", " ");
        initializer.run();
        verifyNoInteractions(userMapper, userRoleMapper);
    }

    /** 让超管逻辑变为 no-op：账号已存在且已是超管 */
    private void superAdminAlreadyConfigured() {
        User existing = new User();
        existing.setId(1L);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
    }

    // ---------- 角色 / 职位 ----------

    @Test
    void run_insertsAllRolesAndPositions_whenNoneExist() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(positionMapper.selectCount(any())).thenReturn(0L);
        superAdminAlreadyConfigured();

        initializer.run();

        verify(roleMapper, times(5)).insert(any(Role.class));
        verify(positionMapper, times(8)).insert(any());
    }

    @Test
    void initRoles_skipsExistingRoles() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(5L);
        superAdminAlreadyConfigured();

        initializer.run();

        verify(roleMapper, never()).insert(any());
    }

    @Test
    void initRoles_nullCountTreatedAsZero_inserts() {
        when(roleMapper.selectCount(any())).thenReturn(null);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        superAdminAlreadyConfigured();

        initializer.run();

        verify(roleMapper, times(5)).insert(any(Role.class));
    }

    @Test
    void initPositions_skips_whenAlreadyHasPositions() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(3L);
        superAdminAlreadyConfigured();

        initializer.run();

        verify(positionMapper, never()).insert(any());
    }

    // ---------- 超管账号 ----------

    @Test
    void ensureSuperAdmin_createsAccount_whenMissing() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectOne(any())).thenReturn(null); // 账号不存在
        when(settingService.getCurrentGrade()).thenReturn("2030");
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).setId(100L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        initializer.run();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCap.capture());
        User admin = userCap.getValue();
        assertEquals("99000001", admin.getStudentId());
        assertEquals("超级管理员", admin.getName());
        assertEquals("2030", admin.getGrade());
        assertNotNull(admin.getPassword());
        assertNotEquals("bootstrap-test-only-password", admin.getPassword(), "密码应被 BCrypt 加密");
        assertTrue(admin.getPassword().startsWith("$2"), "应为 BCrypt 哈希");

        ArgumentCaptor<UserRole> urCap = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(urCap.capture());
        assertEquals(100L, urCap.getValue().getUserId());
        assertEquals(0, urCap.getValue().getRoleCode());
    }

    @Test
    void ensureSuperAdmin_defaultsGradeTo2026_whenSettingNull() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(settingService.getCurrentGrade()).thenReturn(null);

        initializer.run();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCap.capture());
        assertEquals("2026", userCap.getValue().getGrade());
    }

    @Test
    void ensureSuperAdmin_promotesWithoutResettingPassword_whenExplicitlyConfigured() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        User existing = new User();
        existing.setId(5L);
        existing.setStudentId("99000001");
        existing.setPassword("oldhash");
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(userRoleMapper.selectCount(any())).thenReturn(0L); // 尚无超管角色

        initializer.run();

        // 只补齐角色，不能覆盖已有密码。
        verify(userMapper, never()).updateById(any());
        assertEquals("oldhash", existing.getPassword());

        ArgumentCaptor<UserRole> urCap = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(urCap.capture());
        assertEquals(5L, urCap.getValue().getUserId());
        assertEquals(0, urCap.getValue().getRoleCode());

        verify(userMapper, never()).insert(any());
    }

    @Test
    void ensureSuperAdmin_noop_whenAlreadySuperAdmin() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        User existing = new User();
        existing.setId(5L);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(userRoleMapper.selectCount(any())).thenReturn(1L); // 已是超管

        initializer.run();

        verify(userMapper, never()).insert(any());
        verify(userMapper, never()).updateById(any());
        verify(userRoleMapper, never()).insert(any());
    }
}
