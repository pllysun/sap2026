package com.sap.config;

import com.sap.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * SaTokenConfig（StpInterface 实现）单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaTokenConfigTest {

    @Mock UserRoleMapper userRoleMapper;

    @InjectMocks SaTokenConfig saTokenConfig;

    @Test
    void getPermissionList_returnsEmptyList() {
        List<String> perms = saTokenConfig.getPermissionList("1", "login");
        assertNotNull(perms);
        assertTrue(perms.isEmpty());
    }

    @Test
    void getRoleList_mapsRoleCodesToStrings() {
        when(userRoleMapper.selectRoleCodesByUserId(5L)).thenReturn(List.of(0, 1));

        List<String> roles = saTokenConfig.getRoleList("5", "login");

        assertEquals(List.of("0", "1"), roles);
    }

    @Test
    void getRoleList_emptyWhenNoRoles() {
        when(userRoleMapper.selectRoleCodesByUserId(8L)).thenReturn(List.of());

        List<String> roles = saTokenConfig.getRoleList(8L, "login");

        assertNotNull(roles);
        assertTrue(roles.isEmpty());
    }
}
