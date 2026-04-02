package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 换届表实体
 * <p>记录每次换届中用户的身份分配，关联用户、年级和身份</p>
 */
@Data
@TableName("sys_term")
@Entity
@Table(name = "sys_term")
public class Term {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '用户ID'")
    private Long userId;

    /** 年级 */
    @jakarta.persistence.Column(name = "grade", nullable = false, length = 10, columnDefinition = "VARCHAR(10) COMMENT '年级'")
    private String grade;

    /** 身份ID（关联sys_position） */
    @jakarta.persistence.Column(name = "position_id", nullable = false, columnDefinition = "INT COMMENT '身份ID'")
    private Integer positionId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
