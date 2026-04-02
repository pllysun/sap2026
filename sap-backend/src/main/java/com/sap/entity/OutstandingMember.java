package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("outstanding_member")
@Entity
@Table(name = "outstanding_member")
public class OutstandingMember {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "name", nullable = false, length = 50, columnDefinition = "VARCHAR(50) COMMENT '姓名'")
    private String name;

    @jakarta.persistence.Column(name = "gender", length = 4, columnDefinition = "VARCHAR(4) DEFAULT '男' COMMENT '性别'")
    private String gender;

    @jakarta.persistence.Column(name = "grade", nullable = false, length = 10, columnDefinition = "VARCHAR(10) COMMENT '年级'")
    private String grade;

    @jakarta.persistence.Column(name = "major", length = 100, columnDefinition = "VARCHAR(100) COMMENT '专业'")
    private String major;

    /** 去向：考研/保研/就业/出国/创业/其他 */
    @jakarta.persistence.Column(name = "destination", length = 20, columnDefinition = "VARCHAR(20) COMMENT '去向'")
    private String destination;

    /** 去向内容：具体学校或公司 */
    @jakarta.persistence.Column(name = "destination_detail", length = 200, columnDefinition = "VARCHAR(200) COMMENT '去向内容'")
    private String destinationDetail;

    @jakarta.persistence.Column(name = "bio", columnDefinition = "TEXT COMMENT '个人简介'")
    private String bio;

    @TableField(fill = FieldFill.INSERT)
    @jakarta.persistence.Column(name = "created_at", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createdAt;

    @TableLogic
    @jakarta.persistence.Column(name = "deleted", columnDefinition = "TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    private Integer deleted;
}
