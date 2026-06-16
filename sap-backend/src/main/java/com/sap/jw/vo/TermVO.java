package com.sap.jw.vo;

import lombok.Data;

/** 学期选项 */
@Data
public class TermVO {
    /** 学期值，如 "2025-2026-2" */
    private String value;
    /** 显示文本 */
    private String label;
    /** 是否为当前选中学期 */
    private boolean current;
}
