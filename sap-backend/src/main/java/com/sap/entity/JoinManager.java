package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 入会负责人
 */
@Data
@TableName("join_manager")
@Entity
@Table(name = "join_manager", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_grade", columnNames = {"user_id", "grade"}))
public class JoinManager {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 负责人用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '负责人用户ID'")
    private Long userId;

    /** 负责年级 */
    @jakarta.persistence.Column(name = "grade", nullable = false, length = 10, columnDefinition = "VARCHAR(10) COMMENT '负责年级'")
    private String grade;

    /** 支付宝收款码URL */
    @jakarta.persistence.Column(name = "alipay_qr", length = 500, columnDefinition = "VARCHAR(500) COMMENT '支付宝收款码URL'")
    private String alipayQr;

    /** 微信收款码URL */
    @jakarta.persistence.Column(name = "wechat_qr", length = 500, columnDefinition = "VARCHAR(500) COMMENT '微信收款码URL'")
    private String wechatQr;

    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;
}
