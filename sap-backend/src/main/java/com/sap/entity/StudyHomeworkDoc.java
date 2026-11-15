package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业在线预览正文。
 * <p>只保存从 COS 上 Markdown 原文提取出来的文本，图片仍由 Markdown 中的外链指向 COS；
 * 原文件继续保留在 COS 供下载，两张表互不影响。</p>
 */
@Data
@TableName("study_homework_doc")
@Entity
@Table(name = "study_homework_doc",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_homework_doc_material", columnNames = "material_id"),
        indexes = @Index(name = "idx_study_homework_doc_week", columnList = "activity_id,week"))
public class StudyHomeworkDoc {

    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 对应的 study_material 行ID。
     * <p>缓存必须绑定不可变的 materialId，而不是只按 activity+week 查找：
     * 否则并发“旧请求还在抓取、管理员已替换作业”时，旧请求可能把旧正文写回当前周期，
     * 或者命中另一份未发布作业的正文。换作业会生成新的 material 行，天然换版本。</p>
     */
    @jakarta.persistence.Column(name = "material_id", nullable = false, columnDefinition = "BIGINT COMMENT '作业题目materialId'")
    private Long materialId;

    /** 学习活动ID */
    @jakarta.persistence.Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT COMMENT '学习活动ID'")
    private Long activityId;

    /** 周期 */
    @jakarta.persistence.Column(name = "week", nullable = false, columnDefinition = "INT COMMENT '周期'")
    private Integer week;

    /** 正文格式：目前只有 markdown */
    @jakarta.persistence.Column(name = "content_format", nullable = false, length = 16,
            columnDefinition = "VARCHAR(16) DEFAULT 'markdown' COMMENT '正文格式'")
    private String contentFormat;

    /** Markdown 正文 */
    @jakarta.persistence.Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT COMMENT '作业正文'")
    private String content;

    /** 从对象存储读取到的原始字节数 */
    @jakarta.persistence.Column(name = "source_bytes", columnDefinition = "INT COMMENT '原始字节数'")
    private Integer sourceBytes;

    /** 最近一次从对象存储抓取的时间 */
    @jakarta.persistence.Column(name = "fetched_at", columnDefinition = "DATETIME COMMENT '从COS抓取时间'")
    private LocalDateTime fetchedAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @jakarta.persistence.Column(name = "updated_at", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updatedAt;
}
