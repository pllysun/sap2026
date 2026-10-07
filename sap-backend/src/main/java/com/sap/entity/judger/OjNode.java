package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="oj_node") @TableName("oj_node")
public class OjNode {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(nullable=false,length=80) private String name;
    @Column(nullable=false,length=500) private String endpoint;
    @JsonIgnore @Column(name="token_cipher",length=1000) private String tokenCipher;
    @Column(name="builtin") private Boolean builtin;
    @Column(name="enabled") private Boolean enabled;
    @Column(name="max_concurrency") private Integer maxConcurrency;
    @Column(name="created_at") private LocalDateTime createdAt;
}
