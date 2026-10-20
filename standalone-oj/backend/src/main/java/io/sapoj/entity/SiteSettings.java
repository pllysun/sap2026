package io.sapoj.entity;
import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

@Data @Entity @Table(name="oj_site_settings") @TableName("oj_site_settings")
public class SiteSettings {
    @Id @TableId(type=IdType.INPUT) private Long id=1L;
    @Column(name="site_name",length=60) private String siteName="SAP OJ";
    @Column(name="registration_enabled") private Boolean registrationEnabled=false;
    @Column(name="queue_capacity") private Integer queueCapacity=50;
    @Column(name="daily_submission_limit") private Integer dailySubmissionLimit=0;
    @Column(name="max_judge_group_ms") private Long maxJudgeGroupMs=600000L;
}
