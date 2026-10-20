package com.sap.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.*;
import com.sap.common.GlobalExceptionHandler;
import com.sap.service.UserDirectoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class UserDirectoryControllerTest {
    private final UserDirectoryService service=mock(UserDirectoryService.class);
    MockMvc mvc;SaTokenConfig config;SaTokenDao dao;SaTokenContext context;StpInterface roles;SaTokenDaoDefaultImpl testDao;

    @BeforeEach void setup() {
        config=SaManager.getConfig();dao=SaManager.getSaTokenDao();context=SaManager.getSaTokenContext();roles=SaManager.getStpInterface();
        var cfg=new SaTokenConfig();cfg.setTokenName("sap-token");cfg.setIsPrint(false);cfg.setIsReadCookie(false);cfg.setIsShare(false);
        SaManager.setConfig(cfg);testDao=new SaTokenDaoDefaultImpl();SaManager.setSaTokenDao(testDao);
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        mvc=MockMvcBuilders.standaloneSetup(new UserDirectoryController(service))
            .addInterceptors(new SaInterceptor(handler->StpUtil.checkLogin())).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.list(anyInt(),anyInt(),any())).thenReturn(Map.of("records",List.of(),"total",0));
        when(service.options()).thenReturn(Map.of("grades",List.of("2026"),"positions",List.of()));
    }

    @AfterEach void teardown() {
        RequestContextHolder.resetRequestAttributes();testDao.destroy();SaManager.setConfig(config);SaManager.setSaTokenDao(dao);
        SaManager.setSaTokenContext(context);SaManager.setStpInterface(roles);
    }

    String token(String role) {
        SaManager.setStpInterface(new StpInterface() {
            public List<String> getRoleList(Object id,String type){return List.of(role);}
            public List<String> getPermissionList(Object id,String type){return List.of();}
        });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(),new MockHttpServletResponse()));
        try{return StpUtil.stpLogic.createLoginSession(9001L,new SaLoginModel());}
        finally{RequestContextHolder.resetRequestAttributes();}
    }

    @ParameterizedTest @ValueSource(strings={"0","1","2"})
    void allAdminLevelsCanReadAndZeroValuedFiltersAreNotDropped(String role) throws Exception {
        String token=token(role);
        mvc.perform(get("/api/user/directory").header("sap-token",token).param("roleCode","0").param("status","0").param("grade","2026"))
            .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/user/directory/filters").header("sap-token",token))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.grades[0]").value("2026"));
        var filters=org.mockito.ArgumentCaptor.forClass(UserDirectoryService.Filters.class);
        verify(service).list(eq(1),eq(20),filters.capture());assertEquals(0,filters.getValue().roleCode());
        assertEquals(0,filters.getValue().status());assertEquals("highest",filters.getValue().roleMatch());verify(service).options();
    }

    @ParameterizedTest @ValueSource(strings={"3","4"})
    void membersAndGuestsCannotReadPersonalDataOrFilterOptions(String role) throws Exception {
        String token=token(role);
        for(String path:List.of("/api/user/directory","/api/user/directory/filters"))
            mvc.perform(get(path).header("sap-token",token)).andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
    }

    @Test void anonymousRequestsCannotReadDirectory() throws Exception {
        for(String path:List.of("/api/user/directory","/api/user/directory/filters"))
            mvc.perform(get(path)).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }
}
