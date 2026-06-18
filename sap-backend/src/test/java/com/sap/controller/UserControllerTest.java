package com.sap.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.Result;
import com.sap.entity.User;
import com.sap.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerTest {

    @Mock UserService userService;

    @InjectMocks UserController controller;

    @Test
    void list_returnsPage() {
        Page<User> page = new Page<>(1, 10);
        when(userService.listUsers(1, 10, "kw")).thenReturn(page);

        Result<?> result = controller.list(1, 10, "kw");

        assertEquals(200, result.getCode());
        assertSame(page, result.getData());
        verify(userService).listUsers(1, 10, "kw");
    }

    @Test
    void getById_returnsUser() {
        User u = new User();
        u.setId(3L);
        when(userService.getUserById(3L)).thenReturn(u);

        Result<?> result = controller.getById(3L);

        assertSame(u, result.getData());
    }

    @Test
    void update_returnsMessage() {
        User u = new User();

        Result<?> result = controller.update(5L, u);

        assertEquals("更新成功", result.getData());
        verify(userService).updateUser(5L, u);
    }

    @Test
    void updateRoles_returnsMessage() {
        List<Integer> roles = List.of(2, 3);

        Result<?> result = controller.updateRoles(5L, roles);

        assertEquals("权限更新成功", result.getData());
        verify(userService).updateUserRole(5L, roles);
    }

    @Test
    void getRoles_returnsRoleList() {
        when(userService.getUserRoles(7L)).thenReturn(List.of(1, 2));

        Result<?> result = controller.getRoles(7L);

        assertEquals(List.of(1, 2), result.getData());
    }

    @Test
    void upgrade_returnsMessage() {
        Result<?> result = controller.upgrade(9L);

        assertEquals("升级成功", result.getData());
        verify(userService).upgradeToMember(9L);
    }

    @Test
    void batchUpgrade_returnsCountMessage() {
        when(userService.batchUpgradeToMember(List.of(1L, 2L, 3L))).thenReturn(2);

        Result<?> result = controller.batchUpgrade(List.of(1L, 2L, 3L));

        assertEquals("成功升级 2 人", result.getData());
    }

    @Test
    void listMembers_returnsUsers() {
        List<User> members = List.of(new User());
        when(userService.listMemberUsers()).thenReturn(members);

        Result<?> result = controller.listMembers();

        assertSame(members, result.getData());
    }

    @Test
    void resetPassword_delegatesAndReturnsMessage() {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("studentId", "20200001");
        body.put("newPassword", "newPass123");

        Result<?> result = controller.resetPassword(body);

        verify(userService).resetPassword("20200001", "newPass123");
        assertEquals("密码已重置", result.getData());
    }
}
