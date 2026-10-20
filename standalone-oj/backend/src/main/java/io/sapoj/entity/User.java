package io.sapoj.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Entity @Table(name="sys_user") @TableName("sys_user")
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @TableId(type=IdType.AUTO) private Long id;
    /** The API field studentId is the account identifier, not restricted to student numbers. */
    @Column(name="student_id",nullable=false,unique=true,length=32) private String studentId;
    @JsonIgnore @com.alibaba.fastjson2.annotation.JSONField(serialize=false)
    @Column(nullable=false,length=255) private String password;
    @Column(nullable=false,length=50) private String name;
    @Column(length=50) private String nickname;
    @Column(length=255) private String avatar;
    @Column(name="role_code",nullable=false) private Integer roleCode=3;
    @Column(nullable=false) private Integer status=1;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @TableLogic @Column(nullable=false) private Integer deleted=0;
}
