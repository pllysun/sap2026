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

/** 软协课表公告；App 只读取已发布记录，最新更新的公告排在最前。 */
@Data
@Entity
@TableName("app_announcement")
@Table(name = "app_announcement", indexes = {
        @Index(name = "idx_app_announcement_published", columnList = "published,updated_at")
})
public class AppAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @jakarta.persistence.Column(name = "title", nullable = false, length = 120)
    private String title;

    @jakarta.persistence.Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @jakarta.persistence.Column(name = "published", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Boolean published;

    @jakarta.persistence.Column(name = "created_by", nullable = false)
    private Long createdBy;

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
