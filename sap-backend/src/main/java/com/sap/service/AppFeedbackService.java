package com.sap.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.BusinessException;
import com.sap.common.PageResult;
import com.sap.dto.FeedbackCommentCreateDTO;
import com.sap.dto.FeedbackIssueCreateDTO;
import com.sap.entity.AppFeedbackComment;
import com.sap.entity.AppFeedbackIssue;
import com.sap.entity.User;
import com.sap.mapper.AppFeedbackCommentMapper;
import com.sap.mapper.AppFeedbackIssueMapper;
import com.sap.mapper.UserMapper;
import com.sap.vo.AppFeedbackSummaryVO;
import com.sap.vo.FeedbackCommentVO;
import com.sap.vo.FeedbackIssueVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 软协课表 Issue 中心：账号反馈、共享跟进、管理端回复、关闭与删除。 */
@Service
public class AppFeedbackService {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    static final String MAINTAINER_STUDENT_ID = "20202753";
    static final int NON_MEMBER_UNPROCESSED_LIMIT = 3;
    static final int MEMBER_UNPROCESSED_LIMIT = 10;
    private static final int MAX_IMAGES = 4;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final List<String> CATEGORIES = List.of("BUG", "FEATURE", "EXPERIENCE", "OTHER");
    private static final Map<String, String> CATEGORY_TEXT = Map.of(
            "BUG", "问题反馈",
            "FEATURE", "功能建议",
            "EXPERIENCE", "体验优化",
            "OTHER", "其他"
    );

    private final AppFeedbackIssueMapper issueMapper;
    private final AppFeedbackCommentMapper commentMapper;
    private final UserMapper userMapper;
    private final StatsService statsService;
    private final AppVersionService appVersionService;
    private final CosService cosService;

    public AppFeedbackService(AppFeedbackIssueMapper issueMapper,
                              AppFeedbackCommentMapper commentMapper,
                              UserMapper userMapper,
                              StatsService statsService,
                              AppVersionService appVersionService,
                              CosService cosService) {
        this.issueMapper = issueMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.statsService = statsService;
        this.appVersionService = appVersionService;
        this.cosService = cosService;
    }

    public PageResult<FeedbackIssueVO> list(long viewerId, boolean admin, int current, int size,
                                            String status, String category, String keyword, boolean mine) {
        int safeCurrent = Math.max(1, current);
        int safeSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<AppFeedbackIssue> query = new LambdaQueryWrapper<>();
        String normalizedStatus = normalizeStatus(status, true);
        if (normalizedStatus != null) query.eq(AppFeedbackIssue::getStatus, normalizedStatus);
        String normalizedCategory = normalizeCategory(category, true);
        if (normalizedCategory != null) query.eq(AppFeedbackIssue::getCategory, normalizedCategory);
        if (mine) query.eq(AppFeedbackIssue::getReporterId, viewerId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(q -> q.like(AppFeedbackIssue::getTitle, value)
                    .or().like(AppFeedbackIssue::getContent, value));
        }
        query.orderByDesc(AppFeedbackIssue::getUpdatedAt).orderByDesc(AppFeedbackIssue::getId);

        Page<AppFeedbackIssue> page = issueMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        Map<Long, User> users = usersForIssues(page.getRecords());
        Map<Long, Integer> commentCounts = commentCounts(page.getRecords().stream()
                .map(AppFeedbackIssue::getId).toList());
        List<FeedbackIssueVO> records = page.getRecords().stream()
                .map(issue -> toIssueVO(issue, users, viewerId, admin,
                        commentCounts.getOrDefault(issue.getId(), 0), List.of()))
                .toList();
        return PageResult.of(records, page.getTotal(), safeCurrent, safeSize);
    }

    public FeedbackIssueVO detail(long viewerId, boolean admin, Long issueId) {
        AppFeedbackIssue issue = requireIssue(issueId);
        List<AppFeedbackComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<AppFeedbackComment>()
                        .eq(AppFeedbackComment::getIssueId, issueId)
                        .orderByAsc(AppFeedbackComment::getCreatedAt)
                        .orderByAsc(AppFeedbackComment::getId));
        Set<Long> userIds = comments.stream().map(AppFeedbackComment::getAuthorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        userIds.add(issue.getReporterId());
        if (issue.getClosedBy() != null) userIds.add(issue.getClosedBy());
        Map<Long, User> users = users(userIds);
        List<FeedbackCommentVO> timeline = comments.stream()
                .map(comment -> toCommentVO(
                        comment,
                        users.get(comment.getAuthorId()),
                        issue.getReporterId()))
                .toList();
        return toIssueVO(issue, users, viewerId, admin, comments.size(), timeline);
    }

    @Transactional
    public FeedbackIssueVO create(long reporterId, boolean member, FeedbackIssueCreateDTO dto) {
        String title = cleanTitle(dto.getTitle());
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (title.length() < 4 || title.length() > 120) {
            throw new BusinessException(400, "标题须为 4～120 个字符");
        }
        if (content.length() < 10 || content.length() > 5000) {
            throw new BusinessException(400, "反馈内容须为 10～5000 个字符");
        }
        List<String> images = validateImageUrls(dto.getImages());
        String category = normalizeCategory(dto.getCategory(), false);

        // 按账号加行锁，使同一账号的并发提交严格串行，避免 count+insert 竞态突破额度。
        if (userMapper.lockActiveUserById(reporterId) == null) {
            throw new BusinessException(404, "提交账号不存在或已停用");
        }
        ensureQuotaAvailable(reporterId, member);

        AppFeedbackIssue issue = new AppFeedbackIssue();
        issue.setReporterId(reporterId);
        issue.setTitle(title);
        issue.setContent(content);
        issue.setImageUrls(images.isEmpty() ? null : JSON.toJSONString(images));
        issue.setCategory(category);
        issue.setStatus(OPEN);
        issue.setAppVersionName(clean(dto.getAppVersionName(), 32));
        issue.setAppVersionCode(dto.getAppVersionCode());
        // 兼容旧版请求字段但不再保存运行环境，避免旧客户端继续写入设备信息。
        issue.setDeviceInfo(null);
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        issue.setDeleted(0);
        issueMapper.insert(issue);
        return detail(reporterId, false, issue.getId());
    }

    /** 校验额度后上传反馈图片；只接收常见图片格式，每个 Issue 最多 4 张、单张最多 8MB。 */
    public List<String> uploadImages(long reporterId, boolean member, MultipartFile[] files) {
        // App 先传附件再创建 Issue；在这里预检可避免已满额账号制造无主 COS 文件。
        ensureQuotaAvailable(reporterId, member);
        if (files == null || files.length == 0) throw new BusinessException(400, "请选择要上传的图片");
        if (files.length > MAX_IMAGES) throw new BusinessException(400, "反馈图片最多上传 4 张");
        for (MultipartFile file : files) validateImageFile(file);

        List<String> urls = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            Map<String, String> uploaded = cosService.upload(file);
            String url = uploaded == null ? null : uploaded.get("url");
            if (url == null || url.isBlank()) throw new BusinessException("图片上传失败：未返回访问地址");
            urls.add(url);
        }
        return validateImageUrls(urls);
    }

    @Transactional
    public FeedbackIssueVO comment(long viewerId, boolean admin, Long issueId,
                                   FeedbackCommentCreateDTO dto) {
        AppFeedbackIssue issue = requireIssue(issueId);
        if (CLOSED.equals(issue.getStatus())) {
            throw new BusinessException(409, "该反馈已关闭，如问题仍存在请新建反馈");
        }
        return insertComment(viewerId, admin, issue, dto);
    }

    /**
     * 管理端专用回复入口。管理端可以在 Issue 关闭后补充处理说明，App 端仍只能调用
     * {@link #comment(long, boolean, Long, FeedbackCommentCreateDTO)}，因此不会获得该能力。
     */
    @Transactional
    public FeedbackIssueVO adminComment(long adminId, Long issueId, FeedbackCommentCreateDTO dto) {
        AppFeedbackIssue issue = requireIssue(issueId);
        return insertComment(adminId, true, issue, dto);
    }

    private FeedbackIssueVO insertComment(long viewerId, boolean admin, AppFeedbackIssue issue,
                                          FeedbackCommentCreateDTO dto) {
        Long issueId = issue.getId();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isBlank() || content.length() > 2000) {
            throw new BusinessException(400, "回复内容须为 1～2000 个字符");
        }
        AppFeedbackComment comment = new AppFeedbackComment();
        comment.setIssueId(issueId);
        comment.setAuthorId(viewerId);
        comment.setParentId(normalizeParentId(issueId, dto.getParentId()));
        comment.setContent(content);
        comment.setAdminReply(isMaintainer(userMapper.selectById(viewerId)));
        comment.setCreatedAt(LocalDateTime.now());
        comment.setDeleted(0);
        commentMapper.insert(comment);
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        return detail(viewerId, admin, issueId);
    }

    /**
     * 发起人关闭自己的 Issue。这里不接受目标状态，因此用户无法借此重新打开或删除；
     * 管理端完整状态流仍由 {@link #changeStatus(long, Long, String)} 负责。
     */
    @Transactional
    public FeedbackIssueVO closeOwnIssue(long viewerId, boolean admin, Long issueId) {
        AppFeedbackIssue issue = requireIssue(issueId);
        if (!Objects.equals(issue.getReporterId(), viewerId)) {
            throw new BusinessException(403, "只能关闭自己发起的 Issue");
        }
        if (CLOSED.equals(issue.getStatus())) return detail(viewerId, admin, issueId);
        issue.setStatus(CLOSED);
        issue.setClosedBy(viewerId);
        issue.setClosedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        return detail(viewerId, admin, issueId);
    }

    @Transactional
    public FeedbackIssueVO changeStatus(long adminId, Long issueId, String requestedStatus) {
        AppFeedbackIssue issue = requireIssue(issueId);
        String status = normalizeStatus(requestedStatus, false);
        if (status.equals(issue.getStatus())) return detail(adminId, true, issueId);
        issue.setStatus(status);
        issue.setUpdatedAt(LocalDateTime.now());
        if (CLOSED.equals(status)) {
            issue.setClosedBy(adminId);
            issue.setClosedAt(LocalDateTime.now());
        } else {
            issue.setClosedBy(null);
            issue.setClosedAt(null);
        }
        issueMapper.updateById(issue);
        return detail(adminId, true, issueId);
    }

    /** 管理端永久删除 Issue 及其全部回复，明确区别于关闭。 */
    @Transactional
    public void deleteIssue(Long issueId) {
        requireIssue(issueId);
        commentMapper.hardDeleteByIssueId(issueId);
        if (issueMapper.hardDeleteById(issueId) != 1) {
            throw new BusinessException(404, "反馈不存在或已删除");
        }
    }

    public AppFeedbackSummaryVO summary(int days) {
        int safeDays = days == 30 || days == 90 ? days : 7;
        AppFeedbackSummaryVO vo = new AppFeedbackSummaryVO();
        vo.setDays(safeDays);
        vo.setScheduleUsers(statsService.scheduleUsers(safeDays));
        long open = countByStatus(OPEN);
        long closed = countByStatus(CLOSED);
        vo.setOpenIssues(open);
        vo.setClosedIssues(closed);
        vo.setTotalIssues(open + closed);
        Map<String, Long> categories = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            Long count = issueMapper.selectCount(new LambdaQueryWrapper<AppFeedbackIssue>()
                    .eq(AppFeedbackIssue::getCategory, category));
            categories.put(category, count == null ? 0L : count);
        }
        vo.setCategoryCounts(categories);
        vo.setLatestVersion(appVersionService.getLatest());
        return vo;
    }

    private long countByStatus(String status) {
        Long count = issueMapper.selectCount(new LambdaQueryWrapper<AppFeedbackIssue>()
                .eq(AppFeedbackIssue::getStatus, status));
        return count == null ? 0L : count;
    }

    private AppFeedbackIssue requireIssue(Long issueId) {
        AppFeedbackIssue issue = issueId == null ? null : issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(404, "反馈不存在或已删除");
        return issue;
    }

    private FeedbackIssueVO toIssueVO(AppFeedbackIssue issue, Map<Long, User> users,
                                      long viewerId, boolean admin, int commentCount,
                                      List<FeedbackCommentVO> comments) {
        FeedbackIssueVO vo = new FeedbackIssueVO();
        vo.setId(issue.getId());
        vo.setTitle(issue.getTitle());
        vo.setContent(issue.getContent());
        vo.setImages(readImageUrls(issue.getImageUrls()));
        vo.setCategory(issue.getCategory());
        vo.setCategoryText(CATEGORY_TEXT.getOrDefault(issue.getCategory(), "其他"));
        vo.setStatus(issue.getStatus());
        vo.setReporterId(issue.getReporterId());
        User reporter = users.get(issue.getReporterId());
        vo.setReporterName(displayName(reporter, issue.getReporterId()));
        vo.setReporterAvatar(reporter == null ? null : reporter.getAvatar());
        vo.setCommentCount(commentCount);
        boolean mine = Objects.equals(issue.getReporterId(), viewerId);
        vo.setMine(mine);
        // App 与管理端的共同展示字段只描述会员端可否继续留言；管理端回复按钮由管理端页面的角色权限控制。
        vo.setCanComment(OPEN.equals(issue.getStatus()));
        // App 端关闭接口只面向发起人；管理端使用独立状态接口。
        vo.setCanClose(OPEN.equals(issue.getStatus()) && mine);
        vo.setAppVersionName(issue.getAppVersionName());
        vo.setAppVersionCode(issue.getAppVersionCode());
        // deviceInfo 是旧版兼容字段；后端不再保存或下发运行环境。
        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        vo.setClosedAt(issue.getClosedAt());
        vo.setClosedBy(issue.getClosedBy());
        vo.setClosedByName(issue.getClosedBy() == null ? null
                : displayName(users.get(issue.getClosedBy()), issue.getClosedBy()));
        vo.setComments(comments);
        return vo;
    }

    private FeedbackCommentVO toCommentVO(
            AppFeedbackComment comment,
            User author,
            Long reporterId) {
        FeedbackCommentVO vo = new FeedbackCommentVO();
        vo.setId(comment.getId());
        vo.setAuthorId(comment.getAuthorId());
        vo.setAuthorName(displayName(author, comment.getAuthorId()));
        vo.setAuthorAvatar(author == null ? null : author.getAvatar());
        vo.setParentId(comment.getParentId());
        vo.setAdminReply(isMaintainer(author));
        vo.setQuestioner(Objects.equals(comment.getAuthorId(), reporterId));
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }

    /** 校验回复目标，并把对子回复的继续回复固定归并到根回复，禁止产生第三层。 */
    private Long normalizeParentId(Long issueId, Long requestedParentId) {
        if (requestedParentId == null) return null;
        AppFeedbackComment target = commentMapper.selectById(requestedParentId);
        if (target == null || !Objects.equals(target.getIssueId(), issueId)) {
            throw new BusinessException(400, "回复目标不存在或不属于当前 Issue");
        }
        return target.getParentId() == null ? target.getId() : target.getParentId();
    }

    private Map<Long, User> usersForIssues(List<AppFeedbackIssue> issues) {
        Set<Long> ids = issues.stream().map(AppFeedbackIssue::getReporterId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        issues.stream().map(AppFeedbackIssue::getClosedBy).filter(Objects::nonNull).forEach(ids::add);
        return users(ids);
    }

    private Map<Long, User> users(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Integer> commentCounts(List<Long> issueIds) {
        if (issueIds.isEmpty()) return Map.of();
        List<AppFeedbackComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<AppFeedbackComment>().in(AppFeedbackComment::getIssueId, issueIds));
        Map<Long, Integer> counts = new HashMap<>();
        for (AppFeedbackComment comment : comments) counts.merge(comment.getIssueId(), 1, Integer::sum);
        return counts;
    }

    private void ensureQuotaAvailable(long reporterId, boolean member) {
        int limit = member ? MEMBER_UNPROCESSED_LIMIT : NON_MEMBER_UNPROCESSED_LIMIT;
        long unprocessed = issueMapper.countUnprocessedByReporter(reporterId);
        if (unprocessed >= limit) {
            throw new BusinessException(429,
                    "未处理反馈已达到上限（" + limit + " 条），请等待管理员回复或处理后再提交");
        }
    }

    private String normalizeCategory(String value, boolean allowAll) {
        if (value == null || value.isBlank() || allowAll && "ALL".equalsIgnoreCase(value)) {
            return allowAll ? null : "OTHER";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalized)) throw new BusinessException(400, "不支持的反馈分类");
        return normalized;
    }

    private String normalizeStatus(String value, boolean allowAll) {
        if (value == null || value.isBlank()) {
            if (allowAll) return null;
            throw new BusinessException(400, "反馈状态不能为空");
        }
        if (allowAll && "ALL".equalsIgnoreCase(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!OPEN.equals(normalized) && !CLOSED.equals(normalized)) {
            throw new BusinessException(400, "不支持的反馈状态");
        }
        return normalized;
    }

    private String cleanTitle(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ");
    }

    private String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private List<String> validateImageUrls(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > MAX_IMAGES) throw new BusinessException(400, "反馈图片最多上传 4 张");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > 1024) {
                throw new BusinessException(400, "反馈图片地址无效");
            }
            try {
                URI uri = URI.create(value.trim());
                boolean safe = "https".equalsIgnoreCase(uri.getScheme())
                        && uri.getUserInfo() == null && uri.getFragment() == null
                        && cosService.isOwnedPublicHost(uri.getHost());
                if (!safe) throw new BusinessException(400, "反馈图片必须来自本系统对象存储");
                unique.add(uri.toString());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(400, "反馈图片地址无效");
            }
        }
        return List.copyOf(unique);
    }

    private List<String> readImageUrls(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> parsed = JSON.parseArray(json, String.class);
            return parsed == null ? List.of() : parsed.stream().filter(Objects::nonNull)
                    .limit(MAX_IMAGES).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "图片不能为空");
        if (file.getSize() > MAX_IMAGE_BYTES) throw new BusinessException(400, "单张图片不能超过 8MB");
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!Set.of("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp").contains(mime)) {
            throw new BusinessException(400, "仅支持 JPG、PNG、GIF 或 WebP 图片");
        }

        String detected;
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            detected = detectImageType(header);
        } catch (IOException e) {
            throw new BusinessException(400, "图片读取失败");
        }
        if (detected == null) throw new BusinessException(400, "图片内容或格式无效");
        boolean mimeOk = "jpeg".equals(detected)
                ? "image/jpeg".equals(mime) || "image/jpg".equals(mime)
                : ("image/" + detected).equals(mime);
        if (!mimeOk) throw new BusinessException(400, "图片类型与实际内容不一致");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean extensionOk = "jpeg".equals(detected)
                ? name.endsWith(".jpg") || name.endsWith(".jpeg")
                : name.endsWith("." + detected);
        if (!extensionOk) throw new BusinessException(400, "图片扩展名与实际格式不一致");
    }

    private String detectImageType(byte[] h) {
        if (h.length >= 3 && u(h[0]) == 0xff && u(h[1]) == 0xd8 && u(h[2]) == 0xff) return "jpeg";
        if (h.length >= 8 && u(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && u(h[4]) == 0x0d && u(h[5]) == 0x0a && u(h[6]) == 0x1a && u(h[7]) == 0x0a) return "png";
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F'
                && h[3] == '8' && (h[4] == '7' || h[4] == '9') && h[5] == 'a') return "gif";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }

    private int u(byte value) {
        return value & 0xff;
    }

    private String displayName(User user, Long fallbackId) {
        if (user != null && user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        if (user != null && user.getName() != null && !user.getName().isBlank()) return user.getName();
        if (user != null && user.getStudentId() != null && !user.getStudentId().isBlank()) return user.getStudentId();
        return "用户 #" + fallbackId;
    }

    private boolean isMaintainer(User user) {
        return user != null && MAINTAINER_STUDENT_ID.equals(user.getStudentId());
    }
}
