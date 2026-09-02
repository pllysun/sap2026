package com.sap.jw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改当前会员名下某个教务账号的备注名。 */
@Data
public class JwRemarkDTO {

    @NotBlank(message = "教务账号不能为空")
    @Size(max = 64, message = "教务账号长度不能超过 64 个字符")
    private String account;

    /** null 或空白表示清除备注。 */
    @Size(max = 40, message = "备注不能超过 40 个字符")
    private String remark;
}
