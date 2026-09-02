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

/** 软协课表意见反馈 Issue。 */
@Data
@Entity
@TableName("app_feedback_issue")
@Table(name = "app_feedback_issue", indexes = {
        @Index(name = "idx_feedback_status_updated", columnList = "status,updated_at"),
        @Index(name = "idx_feedback_reporter", columnList = "reporter_id"),
        @Index(name = "idx_feedback_reporter_pending", columnList = "reporter_id,status,deleted")
})
public class AppFeedbackIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @jakarta.persistence.Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @jakarta.persistence.Column(name = "title", nullable = false, length = 120)
    private String title;

    @jakarta.persistence.Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** 最多 4 张反馈图片的 COS URL，JSON 数组。 */
    @jakarta.persistence.Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    /** BUG / FEATURE / EXPERIENCE / OTHER。 */
    @jakarta.persistence.Column(name = "category", nullable = false, length = 24)
    private String category;

    /** OPEN / CLOSED。 */
    @jakarta.persistence.Column(name = "status", nullable = false, length = 16)
    private String status;

    @jakarta.persistence.Column(name = "app_version_name", length = 32)
    private String appVersionName;

    @jakarta.persistence.Column(name = "app_version_code")
    private Integer appVersionCode;

    /** 旧版兼容列；新反馈不再保存或下发运行环境。 */
    @jakarta.persistence.Column(name = "device_info", length = 255)
    private String deviceInfo;

    @jakarta.persistence.Column(name = "closed_by")
    private Long closedBy;

    @jakarta.persistence.Column(name = "closed_at")
    private LocalDateTime closedAt;

    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @jakarta.persistence.Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0")
    private Integer deleted;
}
