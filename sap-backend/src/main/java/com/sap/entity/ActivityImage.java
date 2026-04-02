package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

/**
 * 活动图片表实体
 * <p>存储活动关联的图片URL</p>
 */
@Data
@TableName("act_activity_image")
@Entity
@Table(name = "act_activity_image")
public class ActivityImage {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 活动ID */
    @jakarta.persistence.Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT COMMENT '活动ID'")
    private Long activityId;

    /** 图片地址 */
    @jakarta.persistence.Column(name = "image_url", nullable = false, length = 500, columnDefinition = "VARCHAR(500) COMMENT '图片地址'")
    private String imageUrl;

    /** 排序序号 */
    @jakarta.persistence.Column(name = "sort_order", columnDefinition = "INT DEFAULT 0 COMMENT '排序'")
    private Integer sortOrder;
}
