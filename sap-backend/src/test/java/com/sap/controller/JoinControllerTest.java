package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.common.Result;
import com.sap.service.JoinService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JoinControllerTest extends BaseUnitTest {

    @Mock JoinService joinService;
    @InjectMocks JoinController controller;

    @Test
    void status_returnsEnabledFlag() {
        when(joinService.isJoinEnabled()).thenReturn(true);
        Result<?> r = controller.status();
        assertEquals(200, r.getCode());
        assertEquals(true, r.getData());
    }

    @Test
    void toggle_enabled_initsManagers() {
        Result<?> r = controller.toggle(Map.of("enabled", "true"));
        verify(joinService).toggleJoin(true);
        verify(joinService).initManagers();
        assertEquals("操作成功", r.getData());
    }

    @Test
    void toggle_disabled_doesNotInit() {
        controller.toggle(Map.of("enabled", "false"));
        verify(joinService).toggleJoin(false);
        verify(joinService, never()).initManagers();
    }

    @Test
    void managers_returnsList() {
        when(joinService.listManagers()).thenReturn(List.of(Map.of("id", 1L)));
        Result<?> r = controller.managers();
        assertEquals(200, r.getCode());
        assertEquals(List.of(Map.of("id", 1L)), r.getData());
    }

    @Test
    void addManager_parsesUserId() {
        Result<?> r = controller.addManager(Map.of("userId", "55"));
        verify(joinService).addManager(55L);
        assertEquals("添加成功", r.getData());
    }

    @Test
    void removeManager_delegates() {
        Result<?> r = controller.removeManager(9L);
        verify(joinService).removeManager(9L);
        assertEquals("移除成功", r.getData());
    }

    @Test
    void uploadQr_usesLoginIdAndBodyValues() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Result<?> r = controller.uploadQr(Map.of("alipayQr", "a", "wechatQr", "w"));
            verify(joinService).uploadQrCode(7L, "a", "w");
            assertEquals("上传成功", r.getData());
        }
    }

    @Test
    void apply_delegatesWithLoginId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(joinService.apply(7L)).thenReturn(Map.of("id", 1L));
            Result<?> r = controller.apply();
            assertEquals(Map.of("id", 1L), r.getData());
        }
    }

    @Test
    void myApplication_delegates() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(joinService.getMyApplication(7L)).thenReturn(Map.of("id", 2L));
            Result<?> r = controller.myApplication();
            assertEquals(Map.of("id", 2L), r.getData());
        }
    }

    @Test
    void refreshManager_delegates() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(joinService.refreshManager(7L)).thenReturn(Map.of("id", 3L));
            Result<?> r = controller.refreshManager();
            assertEquals(Map.of("id", 3L), r.getData());
        }
    }

    @Test
    void submitPayment_passesCode() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Result<?> r = controller.submitPayment(Map.of("paymentCode", "PAY1"));
            verify(joinService).submitPaymentCode(7L, "PAY1");
            assertEquals("提交成功", r.getData());
        }
    }

    @Test
    void submitPayment_nullCode_defaultsEmpty() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            controller.submitPayment(new java.util.HashMap<>());
            verify(joinService).submitPaymentCode(7L, "");
        }
    }

    @Test
    void applications_delegatesWithUserId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(joinService.listApplications(1, true, 7L)).thenReturn(List.of());
            Result<?> r = controller.applications(1, true);
            assertEquals(200, r.getCode());
            verify(joinService).listApplications(1, true, 7L);
        }
    }

    @Test
    void approve_delegates() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Result<?> r = controller.approve(3L);
            verify(joinService).approve(3L, 7L);
            assertEquals("审核通过", r.getData());
        }
    }

    @Test
    void directUpgrade_blankStudentId_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.directUpgrade(Map.of("studentId", "")));
        assertEquals("学号不能为空", ex.getMessage());
        assertThrows(BusinessException.class,
                () -> controller.directUpgrade(new java.util.HashMap<>()));
    }

    @Test
    void directUpgrade_delegates() {
        Result<?> r = controller.directUpgrade(Map.of("studentId", "S001"));
        verify(joinService).directUpgrade("S001");
        assertEquals("升级成功", r.getData());
    }
}
