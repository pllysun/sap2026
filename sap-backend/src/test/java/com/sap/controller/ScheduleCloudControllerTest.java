package com.sap.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.GlobalExceptionHandler;
import com.sap.dto.AppAnnouncementDTO;
import com.sap.service.AppAccessService;
import com.sap.service.AppAnnouncementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 经真实 Sa-Token 拦截器验证云控读写权限，所有业务依赖均为本地 mock。 */
class ScheduleCloudControllerTest {
    private static final String BASE = "/api/app/cloud/admin";
    private static final String ANNOUNCEMENT = "{\"title\":\"测试公告\",\"content\":\"测试内容\",\"published\":false}";
    private final AppAccessService accessService = mock(AppAccessService.class);
    private final AppAnnouncementService announcementService = mock(AppAnnouncementService.class);
    private MockMvc mvc;
    private SaTokenConfig previousConfig;
    private SaTokenDao previousDao;
    private SaTokenContext previousContext;
    private StpInterface previousRoles;
    private SaTokenDaoDefaultImpl testDao;

    @BeforeEach
    void setUp() {
        previousConfig = SaManager.getConfig();
        previousDao = SaManager.getSaTokenDao();
        previousContext = SaManager.getSaTokenContext();
        previousRoles = SaManager.getStpInterface();
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("sap-token");
        config.setIsPrint(false);
        config.setIsReadCookie(false);
        config.setIsShare(false);
        SaManager.setConfig(config);
        testDao = new SaTokenDaoDefaultImpl();
        SaManager.setSaTokenDao(testDao);
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        mvc = MockMvcBuilders.standaloneSetup(new ScheduleCloudController(accessService, announcementService))
                .addInterceptors(new SaInterceptor(handler -> StpUtil.checkLogin()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        testDao.destroy();
        SaManager.setConfig(previousConfig);
        SaManager.setSaTokenDao(previousDao);
        SaManager.setSaTokenContext(previousContext);
        SaManager.setStpInterface(previousRoles);
    }

    private String token(String role) {
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object id, String type) { return List.of(); }
            @Override
            public List<String> getRoleList(Object id, String type) { return List.of(role); }
        });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest(), new MockHttpServletResponse()));
        try {
            return StpUtil.stpLogic.createLoginSession(9001L, new SaLoginModel());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1"})
    void leaderOrSuper_canChangeGuestLevelAndManageAnnouncements(String role) throws Exception {
        String token = token(role);
        mvc.perform(put(BASE + "/guest-access-level").header("sap-token", token)
                        .contentType("application/json").content("{\"level\":2}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.guestAccessLevel").value(2));
        mvc.perform(post(BASE + "/announcements").header("sap-token", token)
                        .contentType("application/json").content(ANNOUNCEMENT))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(put(BASE + "/announcements/7").header("sap-token", token)
                        .contentType("application/json").content(ANNOUNCEMENT))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(delete(BASE + "/announcements/7").header("sap-token", token))
                .andExpect(jsonPath("$.code").value(200));

        verify(accessService).updateGuestAccessLevel(2);
        verify(announcementService).create(eq(9001L), any(AppAnnouncementDTO.class));
        verify(announcementService).update(eq(7L), any(AppAnnouncementDTO.class));
        verify(announcementService).delete(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "3", "4"})
    void lowerRoles_cannotModifyGuestLevelOrAnnouncements(String role) throws Exception {
        String token = token(role);
        mvc.perform(put(BASE + "/guest-access-level").header("sap-token", token)
                        .contentType("application/json").content("{\"level\":2}"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post(BASE + "/announcements").header("sap-token", token)
                        .contentType("application/json").content(ANNOUNCEMENT))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(put(BASE + "/announcements/7").header("sap-token", token)
                        .contentType("application/json").content(ANNOUNCEMENT))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete(BASE + "/announcements/7").header("sap-token", token))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(accessService, announcementService);
    }

    @Test
    void admin_canStillReadCloudOverview() throws Exception {
        when(accessService.guestAccessLevel()).thenReturn(1);
        when(announcementService.adminList()).thenReturn(List.of());
        mvc.perform(get(BASE).header("sap-token", token("2")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.guestAccessLevel").value(1));
    }

    @Test
    void anonymous_cannotModifyCloudSettings() throws Exception {
        mvc.perform(put(BASE + "/guest-access-level")
                        .contentType("application/json").content("{\"level\":2}"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(accessService, announcementService);
    }
}
