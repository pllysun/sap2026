package com.sap.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.dto.FeedbackCommentCreateDTO;
import com.sap.dto.FeedbackIssueCreateDTO;
import com.sap.dto.FeedbackStatusDTO;
import com.sap.service.AppFeedbackService;
import com.sap.service.AppAccessService;
import com.sap.vo.FeedbackIssueVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppFeedbackControllerTest {

    @Mock private AppFeedbackService feedbackService;
    @Mock private AppAccessService accessService;

    private AppFeedbackController controller() {
        return new AppFeedbackController(feedbackService, accessService);
    }

    @Test
    void memberCanReplyButIsNotTreatedAsMaintainer() {
        AppFeedbackController controller = controller();
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("我可以补充复现步骤");
        when(feedbackService.comment(eq(11L), eq(false), eq(5L), any())).thenReturn(new FeedbackIssueVO());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            stp.when(StpUtil::getRoleList).thenReturn(List.of("3"));

            controller.comment(5L, dto);
        }

        verify(feedbackService).comment(11L, false, 5L, dto);
    }

    @Test
    void managementAccountCanUseReplyEndpointWithoutControllerAssigningTags() {
        AppFeedbackController controller = controller();
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("已收到，正在处理中");
        when(feedbackService.comment(eq(22L), eq(true), eq(5L), any())).thenReturn(new FeedbackIssueVO());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(22L);
            stp.when(StpUtil::getRoleList).thenReturn(List.of("2"));

            controller.comment(5L, dto);
        }

        verify(feedbackService).comment(22L, true, 5L, dto);
    }

    @Test
    void nonMemberCanCreateAndReceivesNonMemberQuotaTier() {
        AppFeedbackController controller = controller();
        FeedbackIssueCreateDTO dto = new FeedbackIssueCreateDTO();
        when(feedbackService.create(33L, false, dto)).thenReturn(new FeedbackIssueVO());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(33L);
            stp.when(StpUtil::getRoleList).thenReturn(List.of("4"));

            controller.create(dto);
        }

        verify(feedbackService).create(33L, false, dto);
    }

    @Test
    void fullAppGuestReceivesExpandedQuotaWithoutBecomingMaintainer() {
        AppFeedbackController controller = controller();
        FeedbackIssueCreateDTO dto = new FeedbackIssueCreateDTO();
        when(accessService.hasFullAccess(34L)).thenReturn(true);
        when(feedbackService.create(34L, true, dto)).thenReturn(new FeedbackIssueVO());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(34L);
            stp.when(StpUtil::getRoleList).thenReturn(List.of("4"));
            controller.create(dto);
        }

        verify(feedbackService).create(34L, true, dto);
    }

    @Test
    void imageUploadIsAvailableToMembersAndDelegatesValidatedFiles() {
        AppFeedbackController controller = controller();
        MockMultipartFile image = new MockMultipartFile(
                "files", "feedback.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
        MultipartFile[] files = new MultipartFile[]{image};
        when(feedbackService.uploadImages(11L, true, files))
                .thenReturn(List.of("https://example.test/feedback.png"));
        when(accessService.hasFullAccess(11L)).thenReturn(true);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            stp.when(StpUtil::getRoleList).thenReturn(List.of("3"));

            controller.uploadImages(files);
        }

        verify(feedbackService).uploadImages(11L, true, files);
    }

    @Test
    void onlyManagementEndpointCanChangeIssueStatus() throws Exception {
        Method method = AppFeedbackController.class.getMethod(
                "changeStatus", Long.class, FeedbackStatusDTO.class);
        SaCheckRole role = method.getAnnotation(SaCheckRole.class);

        assertArrayEquals(new String[]{"0", "1", "2"}, role.value());
        assertEquals(SaMode.OR, role.mode());
    }

    @Test
    void managementStatusChangeUsesCurrentOperator() {
        AppFeedbackController controller = controller();
        FeedbackStatusDTO dto = new FeedbackStatusDTO();
        dto.setStatus("CLOSED");
        when(feedbackService.changeStatus(22L, 5L, "CLOSED")).thenReturn(new FeedbackIssueVO());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(22L);

            controller.changeStatus(5L, dto);
        }

        verify(feedbackService).changeStatus(22L, 5L, "CLOSED");
    }

    @Test
    void reporterCloseEndpointUsesCurrentAccountAndOnlyRequestsClose() {
        AppFeedbackController controller = controller();
        when(feedbackService.closeOwnIssue(11L, false, 5L)).thenReturn(new FeedbackIssueVO());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            stp.when(StpUtil::getRoleList).thenReturn(List.of("3"));

            controller.closeOwnIssue(5L);
        }

        verify(feedbackService).closeOwnIssue(11L, false, 5L);
    }

    @Test
    void onlyManagementCanPermanentlyDeleteIssue() throws Exception {
        Method method = AppFeedbackController.class.getMethod("deleteIssue", Long.class);
        SaCheckRole role = method.getAnnotation(SaCheckRole.class);

        assertArrayEquals(new String[]{"0", "1", "2"}, role.value());
        assertEquals(SaMode.OR, role.mode());

        AppFeedbackController controller = controller();
        controller.deleteIssue(5L);
        verify(feedbackService).deleteIssue(5L);
    }
}
