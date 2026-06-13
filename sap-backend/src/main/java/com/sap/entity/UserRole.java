package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

/**
 * 用户权限关联表实体
 * <p>记录用户与权限码的多对多关系</p>
 */
@Data
@TableName("sys_user_role")
@Entity
@Table(name = "sys_user_role", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_role", columnNames = {"user_id", "role_code"}))
public class UserRole {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '用户ID'")
    private Long userId;

    /** 权限码 */
    @jakarta.persistence.Column(name = "role_code", nullable = false, columnDefinition = "INT COMMENT '权限码'")
    private Integer roleCode;
}
