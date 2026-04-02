package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务账单表实体
 * <p>记录社团的收入和支出明细</p>
 */
@Data
@TableName("fin_bill")
@Entity
@Table(name = "fin_bill")
public class Bill {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 账单类型：0支出 1收入 */
    @jakarta.persistence.Column(name = "bill_type", nullable = false, columnDefinition = "TINYINT COMMENT '0支出 1收入'")
    private Integer billType;

    /** 账单内容 */
    @jakarta.persistence.Column(name = "content", nullable = false, length = 500, columnDefinition = "VARCHAR(500) COMMENT '账单内容'")
    private String content;

    /** 金额 */
    @jakarta.persistence.Column(name = "amount", nullable = false, columnDefinition = "DECIMAL(10,2) COMMENT '金额'")
    private BigDecimal amount;

    /** 消费/收入时间 */
    @jakarta.persistence.Column(name = "bill_time", nullable = false, columnDefinition = "DATETIME COMMENT '消费/收入时间'")
    private LocalDateTime billTime;

    /** 备注 */
    @jakarta.persistence.Column(name = "remark", length = 500, columnDefinition = "VARCHAR(500) COMMENT '备注'")
    private String remark;

    /** 活动年级 */
    @jakarta.persistence.Column(name = "grade", length = 10, columnDefinition = "VARCHAR(10) COMMENT '活动年级'")
    private String grade;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
