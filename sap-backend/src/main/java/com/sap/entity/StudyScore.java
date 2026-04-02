package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习评分表实体
 * <p>记录负责人对成员每周期的评分和评语</p>
 */
@Data
@TableName("study_score")
@Entity
@Table(name = "study_score")
public class StudyScore {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学习活动ID */
    @jakarta.persistence.Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT COMMENT '学习活动ID'")
    private Long activityId;

    /** 周期 */
    @jakarta.persistence.Column(name = "week", nullable = false, columnDefinition = "INT COMMENT '周期'")
    private Integer week;

    /** 被评分成员ID */
    @jakarta.persistence.Column(name = "member_user_id", nullable = false, columnDefinition = "BIGINT COMMENT '被评分成员ID'")
    private Long memberUserId;

    /** 评分负责人ID */
    @jakarta.persistence.Column(name = "leader_user_id", nullable = false, columnDefinition = "BIGINT COMMENT '评分负责人ID'")
    private Long leaderUserId;

    /** 分数 1-10 */
    @jakarta.persistence.Column(name = "score", nullable = false, columnDefinition = "INT COMMENT '分数 1-10'")
    private Integer score;

    /** 评语 */
    @jakarta.persistence.Column(name = "comment", nullable = false, length = 1000, columnDefinition = "VARCHAR(1000) COMMENT '评语'")
    private String comment;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;
}
