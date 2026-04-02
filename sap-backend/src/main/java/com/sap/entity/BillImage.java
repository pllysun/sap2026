package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

/**
 * 财务图片表实体
 * <p>存储账单关联的凭证图片</p>
 */
@Data
@TableName("fin_bill_image")
@Entity
@Table(name = "fin_bill_image")
public class BillImage {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 账单ID */
    @jakarta.persistence.Column(name = "bill_id", nullable = false, columnDefinition = "BIGINT COMMENT '账单ID'")
    private Long billId;

    /** 图片地址 */
    @jakarta.persistence.Column(name = "image_url", nullable = false, length = 500, columnDefinition = "VARCHAR(500) COMMENT '图片地址'")
    private String imageUrl;
}
