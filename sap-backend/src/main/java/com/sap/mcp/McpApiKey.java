package com.sap.mcp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@TableName("mcp_api_key")
@Table(name = "mcp_api_key",
        indexes = @Index(name = "idx_mcp_key_user", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_mcp_key_hash", columnNames = "key_hash"))
public class McpApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "jw_credential_id", nullable = false)
    private Long jwCredentialId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "key_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(nullable = false, length = 96)
    private String scopes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
