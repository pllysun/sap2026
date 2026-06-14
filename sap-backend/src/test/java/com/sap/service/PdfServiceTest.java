package com.sap.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PdfService 是纯计算（commonmark + openhtmltopdf），不依赖外部 mapper，直接真实调用。
 */
class PdfServiceTest {

    private final PdfService service = new PdfService();

    @Test
    void markdownToPdf_returnsNonEmptyBytes() throws Exception {
        byte[] bytes = service.markdownToPdf("我的笔记标题",
                "# 一级标题\n\n这是正文段落，包含**加粗**和`代码`。\n\n- 列表项1\n- 列表项2");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // PDF 文件头魔数 %PDF
        assertEquals('%', bytes[0]);
        assertEquals('P', bytes[1]);
        assertEquals('D', bytes[2]);
        assertEquals('F', bytes[3]);
    }

    @Test
    void markdownToPdf_withTable_renders() throws Exception {
        String md = "## 表格\n\n| 列1 | 列2 |\n| --- | --- |\n| a | b |\n";
        byte[] bytes = service.markdownToPdf("表格标题", md);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void markdownToPdf_emptyContent_stillProducesPdf() throws Exception {
        byte[] bytes = service.markdownToPdf("空内容", "");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void markdownToPdf_nullTitle_usesDefault() throws Exception {
        byte[] bytes = service.markdownToPdf(null, "正文");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void markdownToPdf_titleWithXmlSpecialChars_escaped() throws Exception {
        byte[] bytes = service.markdownToPdf("<标题> & \"引号\"", "内容");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void markdownToPdf_withImageAndBreaks_sanitizedXhtml() throws Exception {
        String md = "正文\n\n![alt](http://example.com/a.png)\n\n---\n\n换行后内容";
        byte[] bytes = service.markdownToPdf("图片", md);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }
}
