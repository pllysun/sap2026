package com.sap.jw.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.BusinessException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.service.ClassScheduleService;
import com.sap.jw.service.ClassScheduleTaskService;
import com.sap.jw.service.JwSessionManager;
import com.sap.jw.service.PendingClassScheduleManager;
import com.sap.service.AppAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassScheduleControllerTest {
    @Mock ClassScheduleService scheduleService;
    @Mock ClassScheduleTaskService tasks;
    @Mock AppAccessService accessService;
    @Mock JwAuthClient authClient;
    @Mock JwSessionManager sessionManager;
    @Mock PendingClassScheduleManager pendingManager;
    @InjectMocks ClassScheduleController controller;

    @ParameterizedTest
    @ValueSource(strings = {"0", "1"})
    void leaderOrSuper_canSelectAnotherCredentialOwner(String role) {
        when(scheduleService.scheduleConfig()).thenReturn(Map.of("ownerUserId", 20L));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            st.when(StpUtil::getRoleList).thenReturn(List.of(role));

            assertEquals(200, controller.pull(Map.of("ownerUserId", 30L)).getCode());

            verify(tasks).start(30L, 10L, null, null, null, null);
        }
    }

    @Test
    void admin_cannotSelectAnUnrelatedCredentialOwner() {
        when(scheduleService.scheduleConfig()).thenReturn(Map.of("ownerUserId", 20L));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            st.when(StpUtil::getRoleList).thenReturn(List.of("2"));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> controller.pull(Map.of("ownerUserId", 30L)));

            assertEquals(403, error.getCode());
            verifyNoInteractions(tasks);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {10L, 20L})
    void admin_canUseOwnOrConfiguredCredentialOwner(long owner) {
        when(scheduleService.scheduleConfig()).thenReturn(Map.of("ownerUserId", 20L));
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            st.when(StpUtil::getRoleList).thenReturn(List.of("2"));

            assertEquals(200, controller.pull(Map.of("ownerUserId", owner)).getCode());

            verify(tasks).start(owner, 10L, null, null, null, null);
        }
    }

    @Test
    void rejectsMalformedProgressBatchBeforeStartingCollection() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            assertThrows(BusinessException.class, () -> controller.pull(Map.of("batchId", "../other")));
            verifyNoInteractions(scheduleService);
        }
    }
}
