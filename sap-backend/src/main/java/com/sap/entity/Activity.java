package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 活动表实体
 * <p>记录社团组织的各类活动，按年级和次数编号</p>
 */
@Data
@TableName("act_activity")
@Entity
@Table(name = "act_activity")
public class Activity {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 活动年份 */
    @jakarta.persistence.Column(name = "grade", nullable = false, length = 10, columnDefinition = "VARCHAR(10) COMMENT '年份'")
    private String grade;

    /** 活动次数 */
    @jakarta.persistence.Column(name = "seq_num", nullable = false, columnDefinition = "INT COMMENT '活动次数'")
    private Integer seqNum;

    /** 活动名称 */
    @jakarta.persistence.Column(name = "title", nullable = false, length = 200, columnDefinition = "VARCHAR(200) COMMENT '活动名称'")
    private String title;

    /** 活动内容 */
    @jakarta.persistence.Column(name = "content", columnDefinition = "TEXT COMMENT '活动内容'")
    private String content;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @jakarta.persistence.Column(name = "updated_at", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updatedAt;

    /** 逻辑删除：0正常 1已删除 */
    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
