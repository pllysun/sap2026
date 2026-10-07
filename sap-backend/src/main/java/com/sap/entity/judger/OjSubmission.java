package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="oj_submission",indexes={
    @Index(name="idx_oj_queue",columnList="status,kind,id"),
    @Index(name="idx_oj_user_history",columnList="user_id,problem_id,id"),
    @Index(name="idx_oj_set_score",columnList="problem_set_id,kind,accepted_at,id"),
    @Index(name="idx_oj_snapshot",columnList="snapshot_key")},
    uniqueConstraints=@UniqueConstraint(name="uk_oj_request",columnNames={"user_id","request_key"})) @TableName("oj_submission")
public class OjSubmission {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO)
    private Long id;
    @Column(name="problem_id")
    private Long problemId;
    @Column(name="user_id")
    private Long userId;
    @Column(name="problem_set_id") private Long problemSetId;
    @Column(name="problem_set_item_id") private Long problemSetItemId;
    @Column(name="accepted_at") private Long acceptedAt;
    @Column(name="request_key",length=64) private String requestKey;
    @Column(name="result_version") private Integer resultVersion;
    @Column(name="validation_signature",length=64) private String validationSignature;
    @Column(name="revision")
    private Long revision;
    @Column(name="kind")
    private String kind;
    @Column(name="language")
    private String language;
    @Column(name="mode")
    private String mode;
    @Column(name="status")
    private String status;
    @Column(name="ever_accepted")
    private Boolean everAccepted;
    @Column(name="node_id") private Long nodeId;
    @Column(name="node_name",length=80) private String nodeName;
    @Column(name="attempt") private Integer attempt;
    @Lob @Column(name="code",columnDefinition="LONGTEXT")
    private String code;
    @Lob @Column(name="snapshot_json",columnDefinition="LONGTEXT")
    private String snapshotJson;
    @Column(name="snapshot_key",length=64) private String snapshotKey;
    @Lob @Column(name="result_json",columnDefinition="LONGTEXT")
    private String resultJson;
    @Column(name="passed_cases")
    private Integer passedCases;
    @Column(name="total_cases")
    private Integer totalCases;
    @Column(name="created_at")
    private LocalDateTime createdAt;
    @Column(name="started_at")
    private LocalDateTime startedAt;
    @Column(name="finished_at")
    private LocalDateTime finishedAt;
}
