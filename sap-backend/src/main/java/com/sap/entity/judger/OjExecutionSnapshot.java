package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/** Immutable, content-addressed judge inputs; user code remains in its submission. */
@Data @Entity @Table(name="oj_execution_snapshot") @TableName("oj_execution_snapshot")
public class OjExecutionSnapshot {
    @Id @Column(length=64) @TableId(type=IdType.INPUT) private String id;
    @Lob @Column(name="payload_json",columnDefinition="LONGTEXT") private String payloadJson;
    @Column(name="created_at") private LocalDateTime createdAt;
}
