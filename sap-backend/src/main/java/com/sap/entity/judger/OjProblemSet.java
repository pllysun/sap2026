package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

@Data @Entity @Table(name="oj_problem_set") @TableName("oj_problem_set")
public class OjProblemSet {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(length=120,nullable=false) private String name;
    @Column(length=3000) private String description;
    @Column(length=16) private String mode;
    @Column(length=16) private String status;
    @Column(name="access_type",length=16) private String accessType;
    @Lob @Column(name="students_json",columnDefinition="LONGTEXT") private String studentsJson;
    @Column(name="languages_json",length=256) private String languagesJson;
    @Column(name="starts_at") private Long startsAt;
    @Column(name="ends_at") private Long endsAt;
    @Column(name="public_code") private Boolean publicCode;
    @Column(name="revision") private Long revision;
    @Column(name="created_at") private Long createdAt;
    @Column(name="updated_at") private Long updatedAt;
}
