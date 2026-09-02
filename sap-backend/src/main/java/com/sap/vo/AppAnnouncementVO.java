package com.sap.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppAnnouncementVO {
    private Long id;
    private String title;
    private String content;
    private Boolean published;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
