package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户表实体
 * <p>存储社团所有注册用户的基本信息，包括学号、密码、姓名、QQ等</p>
 */
@Data
@TableName("sys_user")
@Entity
@Table(name = "sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学号（唯一） */
    @jakarta.persistence.Column(name = "student_id", nullable = false, unique = true, length = 20, columnDefinition = "VARCHAR(20) COMMENT '学号'")
    private String studentId;

    /** 密码（BCrypt加密）。绝不随响应序列化外泄（本项目 HTTP 用 FastJson2，故用 @JSONField；并加 Jackson @JsonIgnore 双保险）。 */
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.persistence.Column(name = "password", nullable = false, columnDefinition = "VARCHAR(255) COMMENT '密码(BCrypt)'")
    private String password;

    /** 姓名 */
    @jakarta.persistence.Column(name = "name", nullable = false, length = 50, columnDefinition = "VARCHAR(50) COMMENT '姓名'")
    private String name;

    /** 网名（默认同姓名） */
    @jakarta.persistence.Column(name = "nickname", length = 50, columnDefinition = "VARCHAR(50) COMMENT '网名(默认姓名)'")
    private String nickname;

    /** 性别：0女 1男 */
    @jakarta.persistence.Column(name = "gender", columnDefinition = "TINYINT COMMENT '性别 0女 1男'")
    private Integer gender;

    /** QQ号 */
    @jakarta.persistence.Column(name = "qq", nullable = false, length = 20, columnDefinition = "VARCHAR(20) COMMENT 'QQ号'")
    private String qq;

    /** 年级 */
    @jakarta.persistence.Column(name = "grade", length = 10, columnDefinition = "VARCHAR(10) COMMENT '年级'")
    private String grade;

    /** 头像URL */
    @jakarta.persistence.Column(name = "avatar", columnDefinition = "VARCHAR(255) DEFAULT '/default-avatar.png' COMMENT '头像'")
    private String avatar;

    /** 状态：0禁用 1正常 */
    @jakarta.persistence.Column(name = "status", columnDefinition = "TINYINT DEFAULT 1 COMMENT '状态 0禁用 1正常'")
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @jakarta.persistence.Column(name = "updated_at", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updatedAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
