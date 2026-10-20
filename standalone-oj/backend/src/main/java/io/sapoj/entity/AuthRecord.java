package io.sapoj.entity;
import jakarta.persistence.*;
import lombok.Data;

/** Private authentication state, independent of Redis and separate from audit logs. */
@Data @Entity @Table(name="auth_state",indexes=@Index(name="idx_auth_expiry",columnList="expires_at"))
public class AuthRecord {
    @Id @Column(name="key_hash",length=64) private String keyHash;
    @Column(name="lookup_key",length=1024,nullable=false) private String lookupKey;
    @Column(name="value_type",length=16,nullable=false) private String valueType;
    @Lob @Column(name="payload",columnDefinition="LONGTEXT",nullable=false) private String payload;
    @Column(name="expires_at",nullable=false) private Long expiresAt;
}
