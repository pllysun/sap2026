package com.sap.entity.judger;
import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
@Data @Entity @Table(name="oj_problem_set_audit",indexes=@Index(name="idx_oj_set_audit",columnList="problem_set_id,id")) @TableName("oj_problem_set_audit")
public class OjProblemSetAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(name="problem_set_id") private Long problemSetId;
    @Column(name="actor_id") private Long actorId;
    @Column(name="job_id") private Long jobId;
    @Column(length=40) private String action;
    @Column(length=500) private String reason;
    @Column(name="created_at") private Long createdAt;
}
