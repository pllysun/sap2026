package com.sap.jw.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教务账号绑定表
 * <p>存储会员绑定的学校教务(CAS统一身份)账号，密码经 AES 加密后落库，
 * 用于后台免密拉取课表/成绩/考试。</p>
 */
@Data
@TableName("jw_credential")
@Entity
@Table(name = "jw_credential",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_account", columnNames = {"user_id", "jw_account"}))
public class JwCredential {

    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的会员用户 id（sys_user.id，一个会员可绑多个教务账号） */
    @jakarta.persistence.Column(name = "user_id", nullable = false,
            columnDefinition = "BIGINT COMMENT '会员用户id'")
    private Long userId;

    /** 学校教务账号（学号） */
    @jakarta.persistence.Column(name = "jw_account", nullable = false, length = 64,
            columnDefinition = "VARCHAR(64) COMMENT '学校教务账号'")
    private String jwAccount;

    /** 学校密码（AES-256-GCM 加密，base64） */
    @jakarta.persistence.Column(name = "jw_password_enc", nullable = false, length = 512,
            columnDefinition = "VARCHAR(512) COMMENT '学校密码(AES加密)'")
    private String jwPasswordEnc;

    /** 状态：0禁用 1正常 */
    @jakarta.persistence.Column(name = "status",
            columnDefinition = "TINYINT DEFAULT 1 COMMENT '状态 0禁用 1正常'")
    private Integer status;

    /** 最近一次成功拉取时间 */
    @jakarta.persistence.Column(name = "last_sync_at",
            columnDefinition = "DATETIME COMMENT '最近成功同步时间'")
    private LocalDateTime lastSyncAt;

    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @jakarta.persistence.Column(name = "updated_at", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updatedAt;

    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
