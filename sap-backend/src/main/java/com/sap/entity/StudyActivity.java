package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习小组活动表实体
 * <p>管理学习小组活动，周期通过currentWeek动态递增</p>
 */
@Data
@TableName("study_activity")
@Entity
@Table(name = "study_activity")
public class StudyActivity {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 年级 */
    @jakarta.persistence.Column(name = "grade", nullable = false, length = 10, columnDefinition = "VARCHAR(10) COMMENT '年级'")
    private String grade;

    /** 活动次数(该年级第N次学习活动) */
    @jakarta.persistence.Column(name = "seq_num", nullable = false, columnDefinition = "INT COMMENT '活动次数'")
    private Integer seqNum;

    /** 当前周期(动态递增，从1开始) */
    @jakarta.persistence.Column(name = "current_week", columnDefinition = "INT DEFAULT 1 COMMENT '当前周期'")
    private Integer currentWeek;

    /** 活动周期(用户端操作的周期，可由会长修改) */
    @jakarta.persistence.Column(name = "active_week", columnDefinition = "INT DEFAULT 1 COMMENT '活动周期'")
    private Integer activeWeek;

    /** 活动标题 */
    @jakarta.persistence.Column(name = "title", length = 200, columnDefinition = "VARCHAR(200) COMMENT '活动标题'")
    private String title;

    /** 状态：0关闭 1进行中 */
    @jakarta.persistence.Column(name = "status", columnDefinition = "TINYINT DEFAULT 1 COMMENT '0关闭 1进行中'")
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
