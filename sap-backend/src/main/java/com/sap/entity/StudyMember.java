package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习成员表实体
 * <p>记录学习小组中的成员分配信息</p>
 */
@Data
@TableName("study_member")
@Entity
@Table(name = "study_member")
public class StudyMember {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学习活动ID */
    @jakarta.persistence.Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT COMMENT '学习活动ID'")
    private Long activityId;

    /** 成员用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '成员用户ID'")
    private Long userId;

    /** 分配的负责人ID */
    @jakarta.persistence.Column(name = "leader_id", columnDefinition = "BIGINT COMMENT '分配的负责人ID'")
    private Long leaderId;

    /** 所属周期 */
    @jakarta.persistence.Column(name = "week", nullable = false, columnDefinition = "INT COMMENT '所属周期'")
    private Integer week;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
