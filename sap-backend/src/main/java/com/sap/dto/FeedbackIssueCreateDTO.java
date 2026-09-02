package com.sap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** App 创建意见反馈 Issue。 */
@Data
public class FeedbackIssueCreateDTO {
    @NotBlank(message = "标题不能为空")
    @Size(min = 4, max = 120, message = "标题须为 4～120 个字符")
    private String title;

    @NotBlank(message = "反馈内容不能为空")
    @Size(min = 10, max = 5000, message = "反馈内容须为 10～5000 个字符")
    private String content;

    private String category;

    @Size(max = 32, message = "版本名过长")
    private String appVersionName;
    private Integer appVersionCode;

    /** 旧客户端兼容字段；服务端会忽略且不再保存。 */
    @Size(max = 255, message = "设备信息过长")
    private String deviceInfo;

    /** 先经反馈图片上传接口取得的 COS URL，最多 4 张。 */
    @Size(max = 4, message = "反馈图片最多上传 4 张")
    private List<String> images;
}
