package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("log_stats")
@Entity
@Table(name = "log_stats")
public class LogStats {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "stat_date", columnDefinition = "DATE COMMENT '统计日期'")
    private LocalDate statDate;

    @jakarta.persistence.Column(name = "operation_type", length = 10, columnDefinition = "VARCHAR(10) COMMENT '操作类型'")
    private String operationType;

    @jakarta.persistence.Column(name = "http_method", length = 10, columnDefinition = "VARCHAR(10) COMMENT 'HTTP方法'")
    private String httpMethod;

    @jakarta.persistence.Column(name = "count", columnDefinition = "INT DEFAULT 0 COMMENT '次数'")
    private Integer count;
}
