package com.sap.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeedbackCommentVO {
    private Long id;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private Long parentId;
    private boolean adminReply;
    private boolean questioner;
    private String content;
    private LocalDateTime createdAt;
}
