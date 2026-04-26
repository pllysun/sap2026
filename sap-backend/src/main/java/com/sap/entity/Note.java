package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 软协笔记实体
 * <p>存储 Markdown 格式的笔记内容，支持浏览/下载统计</p>
 */
@Data
@TableName("sap_note")
@Entity
@Table(name = "sap_note")
public class Note {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 笔记标题 */
    @jakarta.persistence.Column(name = "title", nullable = false, length = 200, columnDefinition = "VARCHAR(200) COMMENT '标题'")
    private String title;

    /** Markdown 原文 */
    @jakarta.persistence.Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT COMMENT 'Markdown内容'")
    private String content;

    /** 笔记简介（可选，用于列表卡片展示） */
    @jakarta.persistence.Column(name = "description", columnDefinition = "VARCHAR(500) COMMENT '笔记简介'")
    private String description;

    /** 上传者用户ID */
    @jakarta.persistence.Column(name = "author_id", columnDefinition = "BIGINT COMMENT '上传者ID'")
    private Long authorId;

    /** 总浏览人次 */
    @jakarta.persistence.Column(name = "view_count", columnDefinition = "INT DEFAULT 0 COMMENT '浏览人次'")
    private Integer viewCount;

    /** 总下载人次 */
    @jakarta.persistence.Column(name = "download_count", columnDefinition = "INT DEFAULT 0 COMMENT '下载人次'")
    private Integer downloadCount;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @jakarta.persistence.Column(name = "updated_at", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updatedAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
