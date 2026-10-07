package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="oj_solution", uniqueConstraints=@UniqueConstraint(name="uk_oj_solution_revision",columnNames={"problem_id","problem_revision"}))
@TableName("oj_solution")
public class OjSolution {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(name="problem_id",nullable=false) private Long problemId;
    @Column(name="problem_revision",nullable=false) private Long problemRevision;
    @Lob @Column(name="document_json",columnDefinition="LONGTEXT",nullable=false) private String documentJson;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
