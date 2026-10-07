package com.sap.entity.judger;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;

@Data @Entity @Table(name="oj_problem_set_participant",uniqueConstraints=@UniqueConstraint(name="uk_oj_set_user",columnNames={"problem_set_id","user_id"})) @TableName("oj_problem_set_participant")
public class OjProblemSetParticipant {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    @Column(name="problem_set_id",nullable=false) private Long problemSetId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="joined_at") private Long joinedAt;
    private Boolean disqualified;
    @Column(length=500) private String reason;
}
