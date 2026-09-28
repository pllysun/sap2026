package com.sap.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端维护的 HTML 邮件模板。模板只面向受信任的管理员，发送前会填充 {{variable}} 占位符。 */
@Data
@Entity
@TableName("sys_email_template")
@Table(name = "sys_email_template", indexes = {
        @Index(name = "uk_email_template_key", columnList = "template_key", unique = true),
        @Index(name = "idx_email_template_enabled", columnList = "enabled,updated_at")
})
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "template_key", nullable = false, unique = true, length = 80)
    private String templateKey;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "html_content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String htmlContent;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Boolean enabled = true;

    @TableField(fill = FieldFill.INSERT)
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Integer deleted = 0;
}
