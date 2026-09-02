package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppAnnouncementDTO {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 120, message = "公告标题不能超过 120 个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 10000, message = "公告内容不能超过 10000 个字符")
    private String content;

    private Boolean published = true;
}
