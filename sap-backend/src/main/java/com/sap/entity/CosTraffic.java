package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

/**
 * COS 流量按 用户×方向×日 的滚动计数表（"大概统计"，按文件大小近似累计）。
 * <p>direction：UPLOAD / DOWNLOAD。唯一键 (stat_date,user_id,direction) 作并发原子自增锚点。</p>
 */
@Data
@TableName("stat_cos_traffic")
@Entity
@Table(name = "stat_cos_traffic", uniqueConstraints =
        @UniqueConstraint(name = "uk_cos_traffic", columnNames = {"stat_date", "user_id", "direction"}))
public class CosTraffic {
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

    @jakarta.persistence.Column(name = "direction", length = 10, columnDefinition = "VARCHAR(10) COMMENT 'UPLOAD/DOWNLOAD'")
    private String direction;

    @jakarta.persistence.Column(name = "bytes", columnDefinition = "BIGINT DEFAULT 0 COMMENT '当日累计字节'")
    private Long bytes;

    @jakarta.persistence.Column(name = "count", columnDefinition = "INT DEFAULT 0 COMMENT '当日笔数'")
    private Integer count;
}
