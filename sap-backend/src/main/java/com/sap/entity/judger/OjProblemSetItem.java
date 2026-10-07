package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

@Data @Entity @Table(name="oj_problem_set_item",uniqueConstraints=@UniqueConstraint(name="uk_oj_set_problem",columnNames={"problem_set_id","problem_id"})) @TableName("oj_problem_set_item")
public class OjProblemSetItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(name="problem_set_id",nullable=false) private Long problemSetId;
    @Column(name="problem_id",nullable=false) private Long problemId;
    @Column(name="sort_order") private Integer sortOrder;
    @Column(length=200) private String title;
    @Column(length=16) private String difficulty;
    @Column(name="source_platform",length=100) private String sourcePlatform;
    @Column(name="tags_json",length=2000) private String tagsJson;
    @Column(name="modes_json",length=100) private String modesJson;
    private Long revision;
    @Column(name="validation_signature",length=64) private String validationSignature;
    private Boolean active;
    @Column(name="cancel_reason",length=500) private String cancelReason;
    @Lob @Column(name="snapshot_json",columnDefinition="LONGTEXT") private String snapshotJson;
}
