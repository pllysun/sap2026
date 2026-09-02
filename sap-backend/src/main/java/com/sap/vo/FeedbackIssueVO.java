package com.sap.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** App 与管理端共用的 Issue 视图。 */
@Data
public class FeedbackIssueVO {
    private Long id;
    private String title;
    private String content;
    private List<String> images;
    private String category;
    private String categoryText;
    private String status;
    private Long reporterId;
    private String reporterName;
    private String reporterAvatar;
    private int commentCount;
    private boolean mine;
    private boolean canComment;
    private boolean canClose;
    private String appVersionName;
    private Integer appVersionCode;
    private String deviceInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    private Long closedBy;
    private String closedByName;
    private List<FeedbackCommentVO> comments;
}
