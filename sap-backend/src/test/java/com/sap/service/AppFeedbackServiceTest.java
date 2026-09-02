package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.BusinessException;
import com.sap.dto.FeedbackCommentCreateDTO;
import com.sap.dto.FeedbackIssueCreateDTO;
import com.sap.entity.AppFeedbackComment;
import com.sap.entity.AppFeedbackIssue;
import com.sap.entity.User;
import com.sap.mapper.AppFeedbackCommentMapper;
import com.sap.mapper.AppFeedbackIssueMapper;
import com.sap.mapper.UserMapper;
import com.sap.vo.AppFeedbackSummaryVO;
import com.sap.vo.AppVersionVO;
import com.sap.vo.FeedbackIssueVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppFeedbackServiceTest {

    @Mock private AppFeedbackIssueMapper issueMapper;
    @Mock private AppFeedbackCommentMapper commentMapper;
    @Mock private UserMapper userMapper;
    @Mock private StatsService statsService;
    @Mock private AppVersionService appVersionService;
    @Mock private CosService cosService;

    private AppFeedbackService service;
    private User reporter;

    @BeforeEach
    void setUp() {
        service = new AppFeedbackService(issueMapper, commentMapper, userMapper, statsService,
                appVersionService, cosService);
        reporter = new User();
        reporter.setId(11L);
        reporter.setStudentId("20250011");
        reporter.setName("测试会员");
        reporter.setNickname("课表用户");
        reporter.setAvatar("https://cdn.example.test/avatar.png");
    }

    @Test
    void createStoresIssueMetadataAndReturnsDetail() {
        AtomicReference<AppFeedbackIssue> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            AppFeedbackIssue issue = invocation.getArgument(0);
            issue.setId(7L);
            saved.set(issue);
            return 1;
        }).when(issueMapper).insert(any(AppFeedbackIssue.class));
        when(issueMapper.selectById(7L)).thenAnswer(ignored -> saved.get());
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));
        when(userMapper.lockActiveUserById(11L)).thenReturn(11L);
        when(issueMapper.countUnprocessedByReporter(11L)).thenReturn(0L);

        FeedbackIssueCreateDTO dto = new FeedbackIssueCreateDTO();
        dto.setTitle("  希望增加周视图小组件  ");
        dto.setContent("希望可以增加一个更紧凑的周视图桌面小组件，方便快速查看课程。");
        dto.setCategory("feature");
        dto.setAppVersionName("1.27");
        dto.setAppVersionCode(36);
        dto.setDeviceInfo("Example Phone · Android 15");
        String image = "https://sap-test.cos.ap-guangzhou.myqcloud.com/uploads/feedback.png";
        dto.setImages(List.of(image));
        when(cosService.isOwnedPublicHost("sap-test.cos.ap-guangzhou.myqcloud.com")).thenReturn(true);

        FeedbackIssueVO result = service.create(11L, true, dto);

        assertEquals(7L, result.getId());
        assertEquals("FEATURE", saved.get().getCategory());
        assertEquals("OPEN", saved.get().getStatus());
        assertEquals("希望增加周视图小组件", saved.get().getTitle());
        assertNull(saved.get().getDeviceInfo());
        assertNull(result.getDeviceInfo());
        assertEquals(List.of(image), result.getImages());
        assertTrue(result.isMine());
        assertTrue(result.isCanComment());
        assertTrue(result.isCanClose());
    }

    @Test
    void uploadImagesChecksRealImageBytesAndOwnedUrl() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("files", "feedback.png", "image/png", png);
        String url = "https://sap-test.cos.ap-guangzhou.myqcloud.com/uploads/feedback.png";
        when(cosService.upload(file)).thenReturn(Map.of("url", url));
        when(cosService.isOwnedPublicHost("sap-test.cos.ap-guangzhou.myqcloud.com")).thenReturn(true);
        when(issueMapper.countUnprocessedByReporter(11L)).thenReturn(0L);

        assertEquals(List.of(url), service.uploadImages(11L, true, new MockMultipartFile[]{file}));
        verify(cosService).upload(file);
    }

    @Test
    void uploadImagesRejectsExtensionSpoofBeforeCosUpload() {
        MockMultipartFile fake = new MockMultipartFile(
                "files", "feedback.png", "image/png", "not an image".getBytes());
        when(issueMapper.countUnprocessedByReporter(11L)).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.uploadImages(11L, true, new MockMultipartFile[]{fake}));

        assertEquals(400, error.getCode());
        verify(cosService, never()).upload(any());
    }

    @Test
    void createRejectsExternalImageUrl() {
        FeedbackIssueCreateDTO dto = new FeedbackIssueCreateDTO();
        dto.setTitle("希望增加图片反馈");
        dto.setContent("这里是一段符合长度要求的反馈详细内容。");
        dto.setCategory("BUG");
        dto.setImages(List.of("https://example.com/not-owned.png"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(11L, true, dto));

        assertEquals(400, error.getCode());
        verify(issueMapper, never()).insert(any());
    }

    @Test
    void legacyDeviceEnvironmentIsNeverReturned() {
        AppFeedbackIssue issue = issue("OPEN");
        when(issueMapper.selectById(5L)).thenReturn(issue);
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));

        assertNull(service.detail(99L, false, 5L).getDeviceInfo());
        assertNull(service.detail(11L, false, 5L).getDeviceInfo());
        assertNull(service.detail(99L, true, 5L).getDeviceInfo());
    }

    @Test
    void nonMemberCannotExceedThreeUnprocessedIssues() {
        when(userMapper.lockActiveUserById(11L)).thenReturn(11L);
        when(issueMapper.countUnprocessedByReporter(11L)).thenReturn(3L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(11L, false, validCreateDto()));

        assertEquals(429, error.getCode());
        assertTrue(error.getMessage().contains("3 条"));
        verify(issueMapper, never()).insert(any());
    }

    @Test
    void memberCannotExceedTenUnprocessedIssues() {
        when(userMapper.lockActiveUserById(11L)).thenReturn(11L);
        when(issueMapper.countUnprocessedByReporter(11L)).thenReturn(10L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(11L, true, validCreateDto()));

        assertEquals(429, error.getCode());
        assertTrue(error.getMessage().contains("10 条"));
        verify(issueMapper, never()).insert(any());
    }

    @Test
    void fullQuotaRejectsImageUploadBeforeCosWrite() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile file = new MockMultipartFile("files", "feedback.png", "image/png", png);
        when(issueMapper.countUnprocessedByReporter(11L)).thenReturn(10L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.uploadImages(11L, true, new MockMultipartFile[]{file}));

        assertEquals(429, error.getCode());
        verify(cosService, never()).upload(any());
    }

    @Test
    void memberCannotCommentClosedIssue() {
        when(issueMapper.selectById(5L)).thenReturn(issue("CLOSED"));
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("问题仍然存在");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.comment(11L, false, 5L, dto));

        assertEquals(409, error.getCode());
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void managementCannotCommentClosedIssueEither() {
        when(issueMapper.selectById(5L)).thenReturn(issue("CLOSED"));
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("关闭后的管理端回复");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.comment(22L, true, 5L, dto));

        assertEquals(409, error.getCode());
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void closedIssueIsNeverMarkedCommentableForManagement() {
        AppFeedbackIssue issue = issue("CLOSED");
        when(issueMapper.selectById(5L)).thenReturn(issue);
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));

        assertFalse(service.detail(22L, true, 5L).isCanComment());
    }

    @Test
    void onlyConfiguredMaintainerAccountGetsMaintainerLabel() {
        AppFeedbackIssue issue = issue("OPEN");
        when(issueMapper.selectById(5L)).thenReturn(issue);
        reporter.setStudentId("20202753");
        when(userMapper.selectById(11L)).thenReturn(reporter);
        AtomicReference<AppFeedbackComment> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            AppFeedbackComment comment = invocation.getArgument(0);
            comment.setId(9L);
            saved.set(comment);
            return 1;
        }).when(commentMapper).insert(any(AppFeedbackComment.class));
        when(commentMapper.selectList(any())).thenAnswer(ignored ->
                saved.get() == null ? List.of() : List.of(saved.get()));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("已在下个版本修复");

        FeedbackIssueVO result = service.comment(11L, true, 5L, dto);

        assertTrue(Boolean.TRUE.equals(saved.get().getAdminReply()));
        assertEquals(1, result.getComments().size());
        assertTrue(result.getComments().get(0).isAdminReply());
        assertTrue(result.getComments().get(0).isQuestioner());
        assertEquals(reporter.getAvatar(), result.getComments().get(0).getAuthorAvatar());
        verify(issueMapper).updateById(issue);
    }

    @Test
    void otherManagementAccountIsNotShownAsMaintainer() {
        AppFeedbackIssue issue = issue("OPEN");
        User manager = new User();
        manager.setId(22L);
        manager.setStudentId("20250022");
        manager.setName("普通管理员");
        when(issueMapper.selectById(5L)).thenReturn(issue);
        when(userMapper.selectById(22L)).thenReturn(manager);
        AtomicReference<AppFeedbackComment> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            AppFeedbackComment comment = invocation.getArgument(0);
            comment.setId(9L);
            saved.set(comment);
            return 1;
        }).when(commentMapper).insert(any(AppFeedbackComment.class));
        when(commentMapper.selectList(any())).thenAnswer(ignored -> List.of(saved.get()));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter, manager));
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("管理端普通回复");

        FeedbackIssueVO result = service.comment(22L, true, 5L, dto);

        assertFalse(Boolean.TRUE.equals(saved.get().getAdminReply()));
        assertFalse(result.getComments().get(0).isAdminReply());
        assertFalse(result.getComments().get(0).isQuestioner());
    }

    @Test
    void repliesToNestedCommentsAreFlattenedUnderTheRootComment() {
        AppFeedbackIssue issue = issue("OPEN");
        AppFeedbackComment root = comment(8L, null, 22L, "根回复");
        AppFeedbackComment child = comment(9L, 8L, 11L, "二级回复");
        when(issueMapper.selectById(5L)).thenReturn(issue);
        when(commentMapper.selectById(9L)).thenReturn(child);
        when(userMapper.selectById(11L)).thenReturn(reporter);
        AtomicReference<AppFeedbackComment> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            AppFeedbackComment comment = invocation.getArgument(0);
            comment.setId(10L);
            saved.set(comment);
            return 1;
        }).when(commentMapper).insert(any(AppFeedbackComment.class));
        when(commentMapper.selectList(any())).thenAnswer(ignored -> List.of(root, child, saved.get()));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("继续讨论");
        dto.setParentId(9L);

        FeedbackIssueVO result = service.comment(11L, false, 5L, dto);

        assertEquals(8L, saved.get().getParentId());
        assertEquals(8L, result.getComments().get(2).getParentId());
    }

    @Test
    void replyTargetMustBelongToTheSameIssue() {
        when(issueMapper.selectById(5L)).thenReturn(issue("OPEN"));
        AppFeedbackComment otherIssue = comment(9L, null, 22L, "其他 Issue");
        otherIssue.setIssueId(99L);
        when(commentMapper.selectById(9L)).thenReturn(otherIssue);
        FeedbackCommentCreateDTO dto = new FeedbackCommentCreateDTO();
        dto.setContent("错误回复目标");
        dto.setParentId(9L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.comment(11L, false, 5L, dto));

        assertEquals(400, error.getCode());
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void reporterCanCloseOwnIssueButCannotDeleteOrReopenIt() {
        AppFeedbackIssue issue = issue("OPEN");
        when(issueMapper.selectById(5L)).thenReturn(issue);
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));

        FeedbackIssueVO closed = service.closeOwnIssue(11L, false, 5L);

        assertEquals("CLOSED", closed.getStatus());
        assertEquals(11L, issue.getClosedBy());
        assertFalse(closed.isCanClose());
        verify(issueMapper).updateById(issue);
    }

    @Test
    void userCannotCloseAnotherReportersIssue() {
        when(issueMapper.selectById(5L)).thenReturn(issue("OPEN"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.closeOwnIssue(99L, false, 5L));

        assertEquals(403, error.getCode());
        verify(issueMapper, never()).updateById(any());
    }

    @Test
    void statusCanBeClosedAndReopenedByManagementFlow() {
        AppFeedbackIssue issue = issue("OPEN");
        when(issueMapper.selectById(5L)).thenReturn(issue);
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));

        FeedbackIssueVO closed = service.changeStatus(22L, 5L, "CLOSED");
        assertEquals("CLOSED", closed.getStatus());
        assertEquals(22L, issue.getClosedBy());
        assertTrue(issue.getClosedAt() != null);

        FeedbackIssueVO reopened = service.changeStatus(22L, 5L, "OPEN");
        assertEquals("OPEN", reopened.getStatus());
        assertNull(issue.getClosedBy());
        assertNull(issue.getClosedAt());
    }

    @Test
    void deletePermanentlyRemovesCommentsBeforeIssue() {
        when(issueMapper.selectById(5L)).thenReturn(issue("OPEN"));
        when(issueMapper.hardDeleteById(5L)).thenReturn(1);

        service.deleteIssue(5L);

        var ordered = inOrder(commentMapper, issueMapper);
        ordered.verify(commentMapper).hardDeleteByIssueId(5L);
        ordered.verify(issueMapper).hardDeleteById(5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsIssueStyleMetadataAndCommentCount() {
        AppFeedbackIssue issue = issue("OPEN");
        Page<AppFeedbackIssue> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(issue));
        when(issueMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));
        AppFeedbackComment first = new AppFeedbackComment();
        first.setIssueId(5L);
        AppFeedbackComment second = new AppFeedbackComment();
        second.setIssueId(5L);
        when(commentMapper.selectList(any())).thenReturn(List.of(first, second));

        var result = service.list(11L, false, 1, 20, "OPEN", "ALL", "周视图", false);

        assertEquals(1, result.getRecords().size());
        assertEquals(2, result.getRecords().get(0).getCommentCount());
        assertEquals("课表用户", result.getRecords().get(0).getReporterName());
        assertTrue(result.getRecords().get(0).isCanClose());
    }

    @Test
    void summaryCombinesUsageIssuesAndPublishedVersion() {
        when(statsService.scheduleUsers(30)).thenReturn(42L);
        when(issueMapper.selectCount(any())).thenReturn(3L, 2L, 1L, 1L, 2L, 1L);
        AppVersionVO version = new AppVersionVO();
        version.setVersionCode(36);
        version.setVersionName("1.27");
        when(appVersionService.getLatest()).thenReturn(version);

        AppFeedbackSummaryVO result = service.summary(30);

        assertEquals(42L, result.getScheduleUsers());
        assertEquals(3L, result.getOpenIssues());
        assertEquals(2L, result.getClosedIssues());
        assertEquals(5L, result.getTotalIssues());
        assertEquals("1.27", result.getLatestVersion().getVersionName());
    }

    private AppFeedbackIssue issue(String status) {
        AppFeedbackIssue issue = new AppFeedbackIssue();
        issue.setId(5L);
        issue.setReporterId(11L);
        issue.setTitle("希望增加周视图小组件");
        issue.setContent("希望可以增加一个更紧凑的周视图桌面小组件。");
        issue.setCategory("FEATURE");
        issue.setStatus(status);
        issue.setAppVersionName("1.26");
        issue.setAppVersionCode(35);
        issue.setDeviceInfo("Example Phone");
        issue.setCreatedAt(LocalDateTime.now().minusDays(1));
        issue.setUpdatedAt(LocalDateTime.now());
        issue.setDeleted(0);
        return issue;
    }

    private FeedbackIssueCreateDTO validCreateDto() {
        FeedbackIssueCreateDTO dto = new FeedbackIssueCreateDTO();
        dto.setTitle("希望改进课表显示");
        dto.setContent("这里是一段符合长度要求的反馈详细内容。");
        dto.setCategory("FEATURE");
        dto.setImages(List.of());
        return dto;
    }

    private AppFeedbackComment comment(Long id, Long parentId, Long authorId, String content) {
        AppFeedbackComment comment = new AppFeedbackComment();
        comment.setId(id);
        comment.setIssueId(5L);
        comment.setParentId(parentId);
        comment.setAuthorId(authorId);
        comment.setContent(content);
        comment.setAdminReply(false);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setDeleted(0);
        return comment;
    }
}
