package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="oj_language") @TableName("oj_language")
public class OjLanguage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO)
    private Long id;
    @Column(name="language_key",nullable=false,unique=true,length=16)
    private String languageKey;
    @Column(name="label")
    private String label;
    @Column(name="version")
    private String version;
    @Column(name="enabled")
    private Boolean enabled;
    @Column(name="time_limit_ms")
    private Integer timeLimitMs;
    @Column(name="memory_limit_mb")
    private Integer memoryLimitMb;
    @Column(name="updated_at")
    private LocalDateTime updatedAt;
}
