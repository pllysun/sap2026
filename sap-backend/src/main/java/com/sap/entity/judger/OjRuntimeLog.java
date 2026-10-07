package com.sap.entity.judger;
import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity @Table(name="oj_runtime_log",indexes=@Index(name="idx_oj_log_created",columnList="created_at,id")) @TableName("oj_runtime_log")
public class OjRuntimeLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(name="job_id") private Long jobId;
    @Column(name="event",length=40) private String event;
    @Column(name="level",length=10) private String level;
    @Column(name="status",length=40) private String status;
    @Column(name="node_name",length=80) private String nodeName;
    @Column(name="created_at") private LocalDateTime createdAt;
}
