package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * COS 文件对象登记表
 * <p>每次上传成功登记一条，供下载重定向计量端点按 url 反查文件大小（避免后端代理字节流）。</p>
 */
@Data
@TableName("stat_file_object")
@Entity
@Table(name = "stat_file_object", uniqueConstraints =
        @UniqueConstraint(name = "uk_file_object_url", columnNames = {"url"}))
public class FileObject {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "cos_key", length = 512, columnDefinition = "VARCHAR(512) COMMENT 'COS对象Key'")
    private String cosKey;

    @jakarta.persistence.Column(name = "url", length = 768, columnDefinition = "VARCHAR(768) COMMENT '公网直链(唯一)'")
    private String url;

    @jakarta.persistence.Column(name = "file_name", length = 255, columnDefinition = "VARCHAR(255) COMMENT '原始文件名'")
    private String fileName;

    @jakarta.persistence.Column(name = "size_bytes", columnDefinition = "BIGINT DEFAULT 0 COMMENT '文件字节数'")
    private Long sizeBytes;

    @jakarta.persistence.Column(name = "uploader_id", columnDefinition = "BIGINT COMMENT '上传者ID'")
    private Long uploaderId;

    @jakarta.persistence.Column(name = "uploader_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '上传者名'")
    private String uploaderName;

    @jakarta.persistence.Column(name = "create_time", columnDefinition = "DATETIME COMMENT '登记时间'")
    private LocalDateTime createTime;
}
