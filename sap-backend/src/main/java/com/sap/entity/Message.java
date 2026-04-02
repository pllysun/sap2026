package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 留言板实体
 * <p>存储用户留言，支持匿名留言</p>
 */
@Data
@TableName("msg_board")
@Entity
@Table(name = "msg_board")
public class Message {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID（可匿名为null） */
    @jakarta.persistence.Column(name = "user_id", columnDefinition = "BIGINT COMMENT '用户ID(可匿名)'")
    private Long userId;

    /** 留言内容 */
    @jakarta.persistence.Column(name = "content", nullable = false, columnDefinition = "TEXT COMMENT '留言内容'")
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
