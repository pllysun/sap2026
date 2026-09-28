package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_log")
@Entity
@Table(name = "sys_log", indexes = {
    @Index(name="idx_sys_log_time_id", columnList="request_time,id"),
    @Index(name="idx_sys_log_user_time", columnList="user_id,request_time"),
    @Index(name="idx_sys_log_endpoint_time", columnList="endpoint,request_time"),
    @Index(name="idx_sys_log_source_time", columnList="source,request_time")
})
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

    @Column(length=8)
    private String source;
    @Column(length=255)
    private String endpoint;
    /** HTTP 或业务结果码；旧数据 null 表示未记录，而非成功。 */
    @Column(name = "result_code")
    private Integer resultCode;
}
