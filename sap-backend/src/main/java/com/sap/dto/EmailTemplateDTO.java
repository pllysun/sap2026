package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理端邮件模板编辑请求。HTML 内容不在此处做富文本转义，预览使用 sandbox iframe 隔离。 */
@Data
public class EmailTemplateDTO {

    @NotBlank(message = "模板标识不能为空")
    @Pattern(regexp = "[A-Za-z0-9._-]{2,80}", message = "模板标识只能包含字母、数字、点、下划线和短横线")
    private String templateKey;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 120, message = "模板名称不能超过 120 个字符")
    private String templateName;

    @NotBlank(message = "邮件主题不能为空")
    @Size(max = 255, message = "邮件主题不能超过 255 个字符")
    private String subject;

    @NotBlank(message = "HTML 内容不能为空")
    private String htmlContent;

    @Size(max = 500, message = "模板说明不能超过 500 个字符")
    private String description;

    private Boolean enabled = true;

    /** 可选的编辑器目标事件，用于保存前校验，不改变事件绑定。空值表示独立模板。 */
    @Size(max = 64)
    private String eventKey;
}
