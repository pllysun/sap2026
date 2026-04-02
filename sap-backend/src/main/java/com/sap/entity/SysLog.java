package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_log")
@Entity
@Table(name = "sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "user_id", columnDefinition = "BIGINT COMMENT '操作用户ID'")
    private Long userId;

    @jakarta.persistence.Column(name = "user_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '操作用户名'")
    private String userName;

    @jakarta.persistence.Column(name = "ip", length = 50, columnDefinition = "VARCHAR(50) COMMENT 'IP地址'")
    private String ip;

    /** HTTP方法：GET/POST/PUT/DELETE */
    @jakarta.persistence.Column(name = "http_method", length = 10, columnDefinition = "VARCHAR(10) COMMENT 'HTTP方法'")
    private String httpMethod;

    @jakarta.persistence.Column(name = "path", length = 255, columnDefinition = "VARCHAR(255) COMMENT '请求路径'")
    private String path;

    /** 操作类型：查询/新增/修改/删除 */
    @jakarta.persistence.Column(name = "operation_type", length = 10, columnDefinition = "VARCHAR(10) COMMENT '操作类型'")
    private String operationType;

    @jakarta.persistence.Column(name = "description", length = 200, columnDefinition = "VARCHAR(200) COMMENT '操作描述'")
    private String description;

    @jakarta.persistence.Column(name = "duration", columnDefinition = "BIGINT COMMENT '耗时(ms)'")
    private Long duration;

    @jakarta.persistence.Column(name = "request_time", columnDefinition = "DATETIME COMMENT '请求时间'")
    private LocalDateTime requestTime;
}
