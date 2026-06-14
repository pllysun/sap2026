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
import org.junit.jupiter.api.Test;
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

    @Test
    void run_insertsAllRoles_whenNoneExist() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(positionMapper.selectCount(any())).thenReturn(0L);
        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(settingService.getCurrentGrade()).thenReturn("2027");

        initializer.run();

        // 5 个内置角色全部插入
        verify(roleMapper, times(5)).insert(any(Role.class));
        // 8 条内置职位全部插入
        verify(positionMapper, times(8)).insert(any());
    }

    @Test
    void initRoles_skipsExistingRoles() {
        // 角色已存在 -> 不插入
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(5L);
        when(userRoleMapper.selectCount(any())).thenReturn(1L); // 已有超管 -> 跳过建号

        initializer.run();

        verify(roleMapper, never()).insert(any());
    }

    @Test
    void initRoles_nullCountTreatedAsZero_inserts() {
        when(roleMapper.selectCount(any())).thenReturn(null);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        initializer.run();

        verify(roleMapper, times(5)).insert(any(Role.class));
    }

    @Test
    void initPositions_skips_whenAlreadyHasPositions() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(3L); // 已有职位 -> 跳过
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        initializer.run();

        verify(positionMapper, never()).insert(any());
    }

    @Test
    void initAdmin_skips_whenSuperAdminExists() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        when(userRoleMapper.selectCount(any())).thenReturn(2L); // 已有超管

        initializer.run();

        verify(userMapper, never()).insert(any());
        verify(userMapper, never()).selectCount(any()); // 提前 return，不查同名学号
    }

    @Test
    void initAdmin_createsAdmin_whenNoSuperAdminAndNoSameStudentId() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        when(userRoleMapper.selectCount(any())).thenReturn(0L); // 无超管
        when(userMapper.selectCount(any())).thenReturn(0L);     // 无同名学号
        when(settingService.getCurrentGrade()).thenReturn("2030");
        // 模拟插入后回填主键
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).setId(100L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        initializer.run();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCap.capture());
        User admin = userCap.getValue();
        assertEquals("admin", admin.getStudentId());
        assertEquals("系统管理员", admin.getName());
        assertEquals("2030", admin.getGrade());
        assertNotNull(admin.getPassword());
        assertNotEquals("admin123", admin.getPassword(), "密码应被加密");

        ArgumentCaptor<UserRole> urCap = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(urCap.capture());
        assertEquals(100L, urCap.getValue().getUserId());
        assertEquals(0, urCap.getValue().getRoleCode());
    }

    @Test
    void initAdmin_defaultsGradeTo2026_whenSettingNull() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(settingService.getCurrentGrade()).thenReturn(null);

        initializer.run();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCap.capture());
        assertEquals("2026", userCap.getValue().getGrade());
    }

    @Test
    void initAdmin_skipsAndWarns_whenSameStudentIdExists() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(positionMapper.selectCount(any())).thenReturn(1L);
        when(userRoleMapper.selectCount(any())).thenReturn(0L); // 无超管
        when(userMapper.selectCount(any())).thenReturn(1L);     // 已存在 admin 学号

        initializer.run();

        verify(userMapper, never()).insert(any());
        verify(userRoleMapper, never()).insert(any());
    }
}
