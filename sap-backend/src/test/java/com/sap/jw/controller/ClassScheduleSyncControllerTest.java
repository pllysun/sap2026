package com.sap.jw.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.BusinessException;
import com.sap.jw.service.ClassScheduleSyncService;
import com.sap.service.AppAccessService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassScheduleSyncControllerTest {
    @Test void requiresAuthenticatedBasicAccessBeforeReading() {
        var service = mock(ClassScheduleSyncService.class);
        var access = mock(AppAccessService.class);
        var controller = new ClassScheduleSyncController(service, access);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(12L);
            doThrow(new BusinessException(403, "拒绝访问")).when(access).requireBasicAccess(12L);
            assertThrows(BusinessException.class, () -> controller.sync(new ClassScheduleSyncService.Request(List.of(), false)));
            verifyNoInteractions(service);
        }
    }
}
