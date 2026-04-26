package com.sap.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 笔记浏览记录实体
 * <p>记录每个用户对每篇笔记的浏览次数和最后浏览时间</p>
 */
@Data
@TableName("sap_note_view")
@Entity
@Table(name = "sap_note_view")
public class NoteView {
    @TableId(type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 笔记ID */
    @jakarta.persistence.Column(name = "note_id", nullable = false, columnDefinition = "BIGINT COMMENT '笔记ID'")
    private Long noteId;

    /** 浏览用户ID */
    @jakarta.persistence.Column(name = "user_id", nullable = false, columnDefinition = "BIGINT COMMENT '用户ID'")
    private Long userId;

    /** 该用户浏览此笔记的次数 */
    @jakarta.persistence.Column(name = "view_count", columnDefinition = "INT DEFAULT 1 COMMENT '浏览次数'")
    private Integer viewCount;

    /** 最后浏览时间 */
    @jakarta.persistence.Column(name = "last_view_at", columnDefinition = "DATETIME COMMENT '最后浏览时间'")
    private LocalDateTime lastViewAt;
}
