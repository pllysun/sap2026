package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Issue 跟进回复。 */
@Data
public class FeedbackCommentCreateDTO {
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 2000, message = "回复内容不能超过 2000 个字符")
    private String content;

    /** 回复目标；后端会把对二级回复的继续回复归并到同一一级回复下。 */
    private Long parentId;
}
