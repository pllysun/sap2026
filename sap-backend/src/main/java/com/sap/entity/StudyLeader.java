package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习小组负责人表实体
 * <p>记录每次学习活动中的小组负责人</p>
 */
@Data
@TableName("study_leader")
@Entity
@Table(name = "study_leader")
public class StudyLeader {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学习活动ID */
    @jakarta.persistence.Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT COMMENT '学习活动ID'")
    private Long activityId;

    /** 负责人用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '负责人用户ID'")
    private Long userId;

    /** 负责人学号 */
    @jakarta.persistence.Column(name = "student_id", nullable = false, length = 20, columnDefinition = "VARCHAR(20) COMMENT '负责人学号'")
    private String studentId;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;
}
