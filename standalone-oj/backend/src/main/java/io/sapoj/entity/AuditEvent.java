package io.sapoj.entity;
import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="oj_audit_event") @TableName("oj_audit_event")
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(name="user_id") private Long userId;
    @Column(length=200) private String action;
    @Column(length=20) private String method;
    @Column(length=300) private String path;
    private Boolean success;
    @Column(name="created_at") private LocalDateTime createdAt;
}
