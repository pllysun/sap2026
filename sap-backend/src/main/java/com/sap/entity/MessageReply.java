package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 留言回复表实体
 * <p>一级回复，不支持嵌套回复</p>
 */
@Data
@TableName("msg_reply")
@Entity
@Table(name = "msg_reply")
public class MessageReply {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 留言ID */
    @jakarta.persistence.Column(name = "message_id", nullable = false, columnDefinition = "BIGINT COMMENT '留言ID'")
    private Long messageId;

    /** 回复者用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '回复者用户ID'")
    private Long userId;

    /** 回复内容 */
    @jakarta.persistence.Column(name = "content", nullable = false, columnDefinition = "TEXT COMMENT '回复内容'")
    private String content;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
