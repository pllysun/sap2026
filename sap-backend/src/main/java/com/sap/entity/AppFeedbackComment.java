package com.sap.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** Issue 时间线中的一条用户跟进或维护者回复。 */
@Data
@Entity
@TableName("app_feedback_comment")
@Table(name = "app_feedback_comment", indexes = {
        @Index(name = "idx_feedback_comment_issue", columnList = "issue_id,created_at"),
        @Index(name = "idx_feedback_comment_parent", columnList = "issue_id,parent_id,created_at"),
        @Index(name = "idx_feedback_comment_handled", columnList = "issue_id,admin_reply,deleted")
})
public class AppFeedbackComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @jakarta.persistence.Column(name = "issue_id", nullable = false)
    private Long issueId;

    @jakarta.persistence.Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * 一级回复的 ID。null 表示时间线根回复；对子回复继续回复时仍指向根回复，
     * 因此数据库结构永远只有一层嵌套。
     */
    @jakarta.persistence.Column(name = "parent_id")
    private Long parentId;

    @jakarta.persistence.Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 兼容列；新回复仅账号 20202753 写入 1，响应展示时还会按当前账号重新判定。 */
    @jakarta.persistence.Column(name = "admin_reply", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Boolean adminReply;

    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at")
    private LocalDateTime createdAt;

    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0")
    private Integer deleted;
}
