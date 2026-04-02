package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

/**
 * 权限表实体
 * <p>定义系统权限码：0超级管理员 1会长 2管理员 3成员 4游客</p>
 */
@Data
@TableName("sys_role")
@Entity
@Table(name = "sys_role")
public class Role {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 权限码 */
    @jakarta.persistence.Column(name = "role_code", nullable = false, unique = true, columnDefinition = "INT COMMENT '权限码'")
    private Integer roleCode;

    /** 权限名称 */
    @jakarta.persistence.Column(name = "role_name", nullable = false, length = 50, columnDefinition = "VARCHAR(50) COMMENT '权限名称'")
    private String roleName;
}
