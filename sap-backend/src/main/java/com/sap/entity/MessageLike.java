package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 点赞表实体
 * <p>支持对留言和回复点赞</p>
 */
@Data
@TableName("msg_like")
@Entity
@Table(name = "msg_like")
public class MessageLike {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 目标类型：0留言 1回复 */
    @jakarta.persistence.Column(name = "target_type", nullable = false, columnDefinition = "TINYINT COMMENT '0留言 1回复'")
    private Integer targetType;

    /** 目标ID（留言ID或回复ID） */
    @jakarta.persistence.Column(name = "target_id", nullable = false, columnDefinition = "BIGINT COMMENT '目标ID'")
    private Long targetId;

    /** 点赞用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '点赞用户ID'")
    private Long userId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;
}
