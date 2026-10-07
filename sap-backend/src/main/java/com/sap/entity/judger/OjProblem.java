package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="oj_problem") @TableName("oj_problem")
public class OjProblem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO)
    private Long id;
    @Column(name="slug",nullable=false,unique=true,length=100)
    private String slug;
    @Column(name="title",length=200)
    private String title;
    @Column(name="difficulty")
    private String difficulty;
    @Column(name="status")
    private String status;
    @Column(name="sort_order")
    private Long sortOrder;
    @Column(name="revision")
    private Long revision;
    @Lob @Column(name="pack_json",columnDefinition="LONGTEXT")
    private String packJson;
    @Column(name="validation_signature")
    private String validationSignature;
    @Column(name="validation_job_id")
    private Long validationJobId;
    @Column(name="created_at")
    private LocalDateTime createdAt;
    @Column(name="updated_at")
    private LocalDateTime updatedAt;
}
