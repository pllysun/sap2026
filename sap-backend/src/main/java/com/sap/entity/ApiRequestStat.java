package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

/**
 * 接口请求按 用户×接口×方法×日 的滚动计数表。
 * <p>endpoint 为规范化路由模式（如 /api/user/{id}），由全局拦截器写入，覆盖全部 /api/**。</p>
 * <p>唯一键 (stat_date,user_id,endpoint,http_method) 作并发原子自增锚点。endpoint 限 191 以保 MySQL 索引长度安全。</p>
 */
@Data
@TableName("stat_api_request")
@Entity
@Table(name = "stat_api_request", uniqueConstraints =
        @UniqueConstraint(name = "uk_api_request", columnNames = {"stat_date", "user_id", "endpoint", "http_method"}))
public class ApiRequestStat {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "stat_date", columnDefinition = "DATE COMMENT '统计日期'")
    private LocalDate statDate;

    @jakarta.persistence.Column(name = "user_id", columnDefinition = "BIGINT DEFAULT 0 COMMENT '用户ID(0=匿名)'")
    private Long userId;

    @jakarta.persistence.Column(name = "user_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '用户名(冗余)'")
    private String userName;

    @jakarta.persistence.Column(name = "endpoint", length = 191, columnDefinition = "VARCHAR(191) COMMENT '规范化接口路径'")
    private String endpoint;

    @jakarta.persistence.Column(name = "http_method", length = 10, columnDefinition = "VARCHAR(10) COMMENT 'HTTP方法'")
    private String httpMethod;

    @jakarta.persistence.Column(name = "count", columnDefinition = "INT DEFAULT 0 COMMENT '当日次数'")
    private Integer count;
}
