package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入会申请
 */
@Data
@TableName("join_application")
@Entity
@Table(name = "join_application")
public class JoinApplication {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请人用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '申请人ID'")
    private Long userId;

    /** 分配的负责人用户ID */
    @jakarta.persistence.Column(name = "manager_id", columnDefinition = "BIGINT COMMENT '分配的负责人user_id'")
    private Long managerId;

    /** 支付编码 */
    @jakarta.persistence.Column(name = "payment_code", length = 100, columnDefinition = "VARCHAR(100) COMMENT '支付编码'")
    private String paymentCode;

    /** 申请时的入会届别、会费快照；旧申请为空时兼容当前配置。 */
    @jakarta.persistence.Column(name = "grade", length = 10)
    private String grade;

    @jakarta.persistence.Column(name = "membership_fee", precision = 10, scale = 2)
    private BigDecimal membershipFee;

    /** 退回原因，保留至申请人修正并重新提交。 */
    @jakarta.persistence.Column(name = "review_note", length = 500)
    private String reviewNote;

    /** 状态：0待提交 1已提交 2已通过 3待补充 */
    @jakarta.persistence.Column(name = "status", columnDefinition = "TINYINT DEFAULT 0 COMMENT '0待提交 1已提交 2已通过 3待补充'")
    private Integer status;

    /** 分配负责人时间 */
    @jakarta.persistence.Column(name = "assigned_at", columnDefinition = "DATETIME COMMENT '分配负责人时间'")
    private LocalDateTime assignedAt;

    /** 提交支付码时间 */
    @jakarta.persistence.Column(name = "submitted_at", columnDefinition = "DATETIME COMMENT '提交支付码时间'")
    private LocalDateTime submittedAt;

    /** 审核通过时间 */
    @jakarta.persistence.Column(name = "approved_at", columnDefinition = "DATETIME COMMENT '审核通过时间'")
    private LocalDateTime approvedAt;

    /** 审核人ID */
    @jakarta.persistence.Column(name = "approved_by", columnDefinition = "BIGINT COMMENT '审核人ID'")
    private Long approvedBy;

    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;
}
