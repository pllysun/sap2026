package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

/**
 * 身份表实体
 * <p>定义社团内的各种身份（会长、团支书、副会长、各部部长等），
 * 包含身份人数上限和对应权限码的配置</p>
 */
@Data
@TableName("sys_position")
@Entity
@Table(name = "sys_position")
public class Position {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 身份名称 */
    @jakarta.persistence.Column(name = "position_name", nullable = false, length = 50, columnDefinition = "VARCHAR(50) COMMENT '身份名称'")
    private String positionName;

    /** 是否系统内置：1内置不可删改 0自定义 */
    @jakarta.persistence.Column(name = "is_system", columnDefinition = "TINYINT DEFAULT 0 COMMENT '系统内置(不可删改)'")
    private Integer isSystem;

    /** 排序序号 */
    @jakarta.persistence.Column(name = "sort_order", columnDefinition = "INT DEFAULT 0 COMMENT '排序'")
    private Integer sortOrder;

    /** 最大人数上限 */
    @jakarta.persistence.Column(name = "max_count", columnDefinition = "INT DEFAULT 1 COMMENT '最大人数'")
    private Integer maxCount;

    /** 对应权限码：1会长 2管理员 3成员 */
    @jakarta.persistence.Column(name = "role_code", columnDefinition = "INT DEFAULT 3 COMMENT '对应权限码'")
    private Integer roleCode;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
