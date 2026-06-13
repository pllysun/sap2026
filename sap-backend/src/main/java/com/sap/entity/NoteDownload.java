package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 笔记下载记录实体
 * <p>记录每个用户对每篇笔记的下载次数和最后下载时间</p>
 */
@Data
@TableName("sap_note_download")
@Entity
@Table(name = "sap_note_download", uniqueConstraints =
        @UniqueConstraint(name = "uk_note_download", columnNames = {"note_id", "user_id"}))
public class NoteDownload {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 笔记ID */
    @jakarta.persistence.Column(name = "note_id", nullable = false, columnDefinition = "BIGINT COMMENT '笔记ID'")
    private Long noteId;

    /** 下载用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '用户ID'")
    private Long userId;

    /** 该用户下载此笔记的次数 */
    @jakarta.persistence.Column(name = "download_count", columnDefinition = "INT DEFAULT 1 COMMENT '下载次数'")
    private Integer downloadCount;

    /** 最后下载时间 */
    @jakarta.persistence.Column(name = "last_download_at", columnDefinition = "DATETIME COMMENT '最后下载时间'")
    private LocalDateTime lastDownloadAt;
}
