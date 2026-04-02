package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设置表实体
 * <p>存储系统配置的键值对，如当前年级等</p>
 */
@Data
@TableName("sys_setting")
@Entity
@Table(name = "sys_setting")
public class Setting {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 设置键（唯一） */
    @jakarta.persistence.Column(name = "setting_key", nullable = false, unique = true, length = 100, columnDefinition = "VARCHAR(100) COMMENT '设置键'")
    private String settingKey;

    /** 设置值 */
    @jakarta.persistence.Column(name = "setting_value", nullable = false, length = 500, columnDefinition = "VARCHAR(500) COMMENT '设置值'")
    private String settingValue;

    /** 描述 */
    @jakarta.persistence.Column(name = "description", columnDefinition = "VARCHAR(255) COMMENT '描述'")
    private String description;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @jakarta.persistence.Column(name = "updated_at", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updatedAt;
}
