package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.dto.LoginDTO;
import com.sap.dto.RegisterDTO;
import com.sap.entity.Setting;
import com.sap.entity.User;
import com.sap.entity.UserRole;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import com.sap.util.PasswordUtil;
import com.sap.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest extends BaseUnitTest {

    @Mock UserMapper userMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock SettingMapper settingMapper;
    @Mock CacheService cacheService;

    @InjectMocks AuthService service;

    private User user(long id, String studentId, String rawPw, Integer status) {
        User u = new User();
        u.setId(id);
        u.setStudentId(studentId);
        u.setPassword(PasswordUtil.encode(rawPw));
        u.setName("name" + id);
        u.setStatus(status);
        return u;
    }

    private LoginDTO loginDto(String studentId, String pw) {
        LoginDTO dto = new LoginDTO();
        dto.setStudentId(studentId);
        dto.setPassword(pw);
        return dto;
    }

    // ===================== login =====================

    @Test
    void login_success_returnsTokenAndUserVO() {
        User u = user(1L, "S-ok", "pw", 1);
        when(userMapper.selectOne(any())).thenReturn(u);

        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getTokenValue).thenReturn("token-abc");

            Map<String, Object> result = service.login(loginDto("S-ok", "pw"));

            assertEquals("token-abc", result.get("token"));
            UserVO vo = (UserVO) result.get("user");
            assertEquals(1L, vo.getId());
            assertEquals("S-ok", vo.getStudentId());
            st.verify(() -> StpUtil.login(1L));
        }
    }

    @Test
    void login_userNotFound_throwsAndRecordsFail() {
        when(userMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.login(loginDto("S-missing", "pw")));
            assertEquals("账号或密码错误", ex.getMessage());
        }
    }

    @Test
    void login_disabledAccount_throws() {
        User u = user(2L, "S-disabled", "pw", 0);
        when(userMapper.selectOne(any())).thenReturn(u);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.login(loginDto("S-disabled", "pw")));
            assertEquals("账号已被禁用", ex.getMessage());
        }
    }

    @Test
    void login_nullStatus_doesNotBlock() {
        User u = user(3L, "S-nullstatus", "pw", null);
        when(userMapper.selectOne(any())).thenReturn(u);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getTokenValue).thenReturn("t");
            Map<String, Object> result = service.login(loginDto("S-nullstatus", "pw"));
            assertNotNull(result.get("user"));
        }
    }

    @Test
    void login_wrongPassword_throws() {
        User u = user(4L, "S-wrongpw", "correct", 1);
        when(userMapper.selectOne(any())).thenReturn(u);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.login(loginDto("S-wrongpw", "wrong")));
            assertEquals("账号或密码错误", ex.getMessage());
        }
    }

    @Test
    void login_nullStudentId_noLockRecorded_butThrowsWhenNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            // studentId null -> recordLoginFail returns early, still throws
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.login(loginDto(null, "pw")));
            assertEquals("账号或密码错误", ex.getMessage());
        }
    }

    @Test
    void login_lockedAfterFiveFailures_sixthAttemptThrowsLockMessage() {
        // user exists but password always wrong -> recordLoginFail
        User u = user(5L, "S-lock", "right", 1);
        when(userMapper.selectOne(any())).thenReturn(u);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            // 5 wrong attempts -> after the 5th the account becomes locked
            for (int i = 0; i < 5; i++) {
                BusinessException ex = assertThrows(BusinessException.class,
                        () -> service.login(loginDto("S-lock", "wrong")));
                assertEquals("账号或密码错误", ex.getMessage());
            }
            // 6th attempt should hit the lock branch
            BusinessException locked = assertThrows(BusinessException.class,
                    () -> service.login(loginDto("S-lock", "right")));
            assertTrue(locked.getMessage().contains("账号已临时锁定"),
                    "应进入锁定分支，实际: " + locked.getMessage());
        }
    }

    @Test
    void login_successClearsFailureCount() {
        User u = user(6L, "S-clear", "pw", 1);
        when(userMapper.selectOne(any())).thenReturn(u);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getTokenValue).thenReturn("t");
            // one wrong attempt
            assertThrows(BusinessException.class, () -> service.login(loginDto("S-clear", "bad")));
            // then success
            Map<String, Object> ok = service.login(loginDto("S-clear", "pw"));
            assertNotNull(ok.get("token"));
        }
    }

    // ===================== adminLogin =====================

    @Test
    void adminLogin_withAdminRole_returnsRoles() {
        User u = user(10L, "S-admin", "pw", 1);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(userRoleMapper.selectRoleCodesByUserId(10L)).thenReturn(List.of(1, 3));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getTokenValue).thenReturn("t");
            Map<String, Object> result = service.adminLogin(loginDto("S-admin", "pw"));
            assertEquals(List.of(1, 3), result.get("roles"));
        }
    }

    @Test
    void adminLogin_withoutAdminRole_logsOutAndThrows403() {
        User u = user(11L, "S-noadmin", "pw", 1);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(userRoleMapper.selectRoleCodesByUserId(11L)).thenReturn(List.of(3, 4));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getTokenValue).thenReturn("t");
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.adminLogin(loginDto("S-noadmin", "pw")));
            assertEquals("无管理端登录权限", ex.getMessage());
            assertEquals(403, ex.getCode());
            st.verify(StpUtil::logout);
        }
    }

    // ===================== register =====================

    @Test
    void register_duplicateStudentId_throws() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        RegisterDTO dto = new RegisterDTO();
        dto.setStudentId("dup");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto));
        assertEquals("该学号已注册", ex.getMessage());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void register_success_usesCurrentGradeAndNickname() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        Setting grade = new Setting();
        grade.setSettingValue("2030");
        when(settingMapper.selectOne(any())).thenReturn(grade);

        RegisterDTO dto = new RegisterDTO();
        dto.setStudentId("new1");
        dto.setPassword("secret");
        dto.setName("张三");
        dto.setNickname("nick");
        dto.setGender(1);
        dto.setQq("123");

        service.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals("new1", saved.getStudentId());
        assertEquals("nick", saved.getNickname());
        assertEquals("2030", saved.getGrade());
        assertEquals("/default-avatar.png", saved.getAvatar());
        assertEquals(1, saved.getStatus());
        assertTrue(PasswordUtil.matches("secret", saved.getPassword()));

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertEquals(4, roleCaptor.getValue().getRoleCode());
        verify(cacheService).addUser(saved);
    }

    @Test
    void register_nullNickname_fallsBackToName_andDefaultGrade() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(settingMapper.selectOne(any())).thenReturn(null); // no grade setting -> default 2025

        RegisterDTO dto = new RegisterDTO();
        dto.setStudentId("new2");
        dto.setPassword("secret");
        dto.setName("李四");
        dto.setNickname(null);
        dto.setQq("321");

        service.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("李四", captor.getValue().getNickname());
        assertEquals("2025", captor.getValue().getGrade());
    }

    // ===================== getCurrentUser =====================

    @Test
    void getCurrentUser_success() {
        User u = user(20L, "S-cur", "pw", 1);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(20L);
            when(cacheService.getUserById(20L)).thenReturn(u);
            when(userRoleMapper.selectRoleCodesByUserId(20L)).thenReturn(List.of(2));

            Map<String, Object> result = service.getCurrentUser();
            assertEquals(List.of(2), result.get("roles"));
            assertEquals(20L, ((UserVO) result.get("user")).getId());
        }
    }

    @Test
    void getCurrentUser_userMissing_throws() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(99L);
            when(cacheService.getUserById(99L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentUser());
            assertEquals("用户不存在", ex.getMessage());
        }
    }

    // ===================== updateProfile =====================

    @Test
    void updateProfile_updatesAllProvidedFields() {
        User u = user(30L, "S-prof", "pw", 1);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(30L);
            when(userMapper.selectById(30L)).thenReturn(u);

            Map<String, Object> params = new HashMap<>();
            params.put("avatar", "/new.png");
            params.put("nickname", "newNick");
            params.put("gender", "1");

            service.updateProfile(params);

            assertEquals("/new.png", u.getAvatar());
            assertEquals("newNick", u.getNickname());
            assertEquals(1, u.getGender());
            verify(userMapper).updateById(u);
            verify(cacheService).refreshUsers();
        }
    }

    @Test
    void updateProfile_emptyParams_onlyPersists() {
        User u = user(31L, "S-prof2", "pw", 1);
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(31L);
            when(userMapper.selectById(31L)).thenReturn(u);

            service.updateProfile(new HashMap<>());

            verify(userMapper).updateById(u);
            verify(cacheService).refreshUsers();
        }
    }

    @Test
    void updateProfile_userMissing_throws() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(40L);
            when(userMapper.selectById(40L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateProfile(new HashMap<>()));
            assertEquals("用户不存在", ex.getMessage());
        }
    }
}
