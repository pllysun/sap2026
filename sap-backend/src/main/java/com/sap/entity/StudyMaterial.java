package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习资料/作业表实体
 * <p>记录学习活动中上传的作业题目和成员提交的作业文件</p>
 */
@Data
@TableName("study_material")
@Entity
@Table(name = "study_material")
public class StudyMaterial {
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

    /** 上传者ID(会长上传作业题目时可为空) */
    @jakarta.persistence.Column(name = "user_id", columnDefinition = "BIGINT COMMENT '上传者ID'")
    private Long userId;

    /** 文件类型：0学习资料 1作业题目 2成员提交作业 */
    @jakarta.persistence.Column(name = "file_type", nullable = false, columnDefinition = "TINYINT COMMENT '0学习资料 1作业题目 2成员提交'")
    private Integer fileType;

    /** 作业标题 */
    @jakarta.persistence.Column(name = "title", length = 200, columnDefinition = "VARCHAR(200) COMMENT '作业标题'")
    private String title;

    /** 文件名 */
    @jakarta.persistence.Column(name = "file_name", length = 255, columnDefinition = "VARCHAR(255) COMMENT '文件名'")
    private String fileName;

    /** 文件地址 */
    @jakarta.persistence.Column(name = "file_url", length = 500, columnDefinition = "VARCHAR(500) COMMENT '文件地址'")
    private String fileUrl;

    /**
     * 发布时间（仅作业题目 fileType=1 使用）。
     * null 或 ≤ 当前时间 = 已发布(学生可见)；> 当前时间 = 定时草稿(仅管理员可见)。
     * 到达发布时间时由定时任务自动推进周期并匹配负责人。
     */
    @jakarta.persistence.Column(name = "publish_time", columnDefinition = "DATETIME COMMENT '作业发布时间(定时)'")
    private LocalDateTime publishTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;
}
