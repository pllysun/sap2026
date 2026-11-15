package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.BaseUnitTest;
import com.sap.common.Result;
import com.sap.service.HomeworkPreviewService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeworkPreviewControllerTest extends BaseUnitTest {

    @Mock HomeworkPreviewService homeworkPreviewService;

    @InjectMocks HomeworkPreviewController controller;

    @Test
    void preview_student_passesAdminFalse() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(50L);
            st.when(StpUtil::getRoleList).thenReturn(List.of("3"));
            when(homeworkPreviewService.preview(1L, 2, 50L, false)).thenReturn(Map.of("previewable", true));

            Result<?> r = controller.preview(1L, 2);

            assertEquals(200, r.getCode());
            assertEquals(Map.of("previewable", true), r.getData());
            st.verify(() -> StpUtil.checkRoleOr("0", "1", "2", "3"));
        }
    }

    @Test
    void preview_admin_passesAdminTrueAndSkipsRoleCheck() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(50L);
            st.when(StpUtil::getRoleList).thenReturn(List.of("1"));
            when(homeworkPreviewService.preview(1L, 2, 50L, true)).thenReturn(Map.of("previewable", true));

            Result<?> r = controller.preview(1L, 2);

            assertEquals(200, r.getCode());
            verify(homeworkPreviewService).preview(1L, 2, 50L, true);
        }
    }
}
