package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="oj_learning_profile") @TableName("oj_learning_profile")
public class OjLearningProfile {
    @Id @TableId(type=IdType.INPUT) @Column(name="user_id") private Long userId;
    @Column(nullable=false) private Long revision;
    @Lob @Column(name="state_json",columnDefinition="LONGTEXT",nullable=false) private String stateJson;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
