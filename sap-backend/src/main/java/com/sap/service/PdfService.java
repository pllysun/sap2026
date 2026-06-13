package com.sap.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Markdown → PDF 转换服务
 * <p>使用 commonmark 解析 Markdown，openhtmltopdf 渲染 PDF</p>
 * <p>内嵌 Noto Sans SC 字体，跨平台支持中文</p>
 */
@Slf4j
@Service
public class PdfService {

    private final Parser parser;
    private final HtmlRenderer renderer;

    /** 中文字体临时文件（从 classpath 解压） */
    private File fontFile;

    private static final String FONT_FAMILY = "Noto Sans SC";
    private static final String CLASSPATH_FONT = "fonts/NotoSansSC-Regular.otf";

    public PdfService() {
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();

        // 从 classpath 提取字体到临时文件（openhtmltopdf 需要 File 对象）
        initFont();
    }

    private void initFont() {
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_FONT);
            if (resource.exists()) {
                Path tempFile = Files.createTempFile("noto-sans-sc-", ".otf");
                try (InputStream is = resource.getInputStream()) {
                    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                fontFile = tempFile.toFile();
                fontFile.deleteOnExit();
                log.info("[PdfService] 已加载内嵌中文字体: {}", CLASSPATH_FONT);
            } else {
                log.warn("[PdfService] 未找到内嵌字体 {}，尝试系统字体回退", CLASSPATH_FONT);
                trySystemFonts();
            }
        } catch (Exception e) {
            log.warn("[PdfService] 加载内嵌字体失败: {}，尝试系统字体回退", e.getMessage());
            trySystemFonts();
        }
    }

    /**
     * 回退：尝试加载系统中文字体
     */
    private void trySystemFonts() {
        String[] candidates = {
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simsun.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/System/Library/Fonts/PingFang.ttc",
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                fontFile = f;
                log.info("[PdfService] 使用系统字体: {}", path);
                return;
            }
        }
        log.error("[PdfService] 未找到任何中文字体，PDF 将无法正确显示中文！");
    }

    /**
     * 将 Markdown 文本转换为 PDF 字节流
     */
    public byte[] markdownToPdf(String title, String markdown) throws Exception {
        // 1. Markdown → HTML
        Node document = parser.parse(markdown);
        String bodyHtml = renderer.render(document);

        // 2. HTML5 → XHTML（修复自闭合标签）
        bodyHtml = sanitizeToXhtml(bodyHtml);

        // 3. 包装为完整 XHTML
        String xhtml = buildXhtml(title, bodyHtml);

        // 4. HTML → PDF
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            // 注册中文字体
            if (fontFile != null) {
                builder.useFont(fontFile, FONT_FAMILY, 400,
                        BaseRendererBuilder.FontStyle.NORMAL, true);
            }

            builder.withHtmlContent(xhtml, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    /**
     * 构建完整的 XHTML 文档
     */
    private String buildXhtml(String title, String bodyHtml) {
        String safeTitle = escapeXml(title != null ? title : "笔记");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <meta charset="UTF-8" />
                  <title>%s</title>
                  <style>
                    body {
                      font-family: 'Noto Sans SC', 'Microsoft YaHei', 'SimSun', sans-serif;
                      font-size: 12pt;
                      line-height: 1.8;
                      color: #333;
                      margin: 40px;
                    }
                    h1 { font-size: 20pt; margin: 16px 0 8px; border-bottom: 1px solid #ddd; padding-bottom: 6px; }
                    h2 { font-size: 16pt; margin: 14px 0 6px; border-bottom: 1px solid #eee; padding-bottom: 4px; }
                    h3 { font-size: 14pt; margin: 12px 0 6px; }
                    h4 { font-size: 12pt; margin: 10px 0 4px; font-weight: bold; }
                    p  { margin: 6px 0; }
                    code {
                      background: #f5f5f5;
                      padding: 1px 4px;
                      border-radius: 3px;
                      font-size: 10pt;
                    }
                    pre {
                      background: #f6f8fa;
                      color: #24292e;
                      padding: 12px;
                      border-radius: 6px;
                      overflow-x: auto;
                      font-size: 9pt;
                      line-height: 1.5;
                      border: 1px solid #e1e4e8;
                    }
                    pre code { background: none; color: inherit; padding: 0; }
                    blockquote {
                      border-left: 3px solid #8b7355;
                      padding: 6px 12px;
                      margin: 10px 0;
                      background: #faf9f7;
                      color: #666;
                    }
                    table { border-collapse: collapse; width: 100%%; margin: 10px 0; }
                    th, td { border: 1px solid #ddd; padding: 6px 10px; text-align: left; font-size: 10pt; }
                    th { background: #f5f3f0; font-weight: bold; }
                    ul, ol { padding-left: 20px; margin: 6px 0; }
                    li { margin: 3px 0; }
                    hr { border: none; border-top: 1px solid #ddd; margin: 14px 0; }
                    a { color: #8b7355; }
                    img { max-width: 100%%; }
                    .pdf-title {
                      font-size: 22pt;
                      font-weight: bold;
                      text-align: center;
                      margin-bottom: 24px;
                      padding-bottom: 12px;
                      border-bottom: 2px solid #8b7355;
                      color: #222;
                    }
                    .pdf-footer {
                      margin-top: 30px;
                      padding-top: 10px;
                      border-top: 1px solid #eee;
                      font-size: 9pt;
                      color: #999;
                      text-align: center;
                    }
                  </style>
                </head>
                <body>
                  <div class="pdf-title">%s</div>
                  %s
                  <div class="pdf-footer">软件协会 · 知识库</div>
                </body>
                </html>
                """.formatted(safeTitle, safeTitle, bodyHtml);
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 将 HTML5 转换为合法 XHTML
     */
    private String sanitizeToXhtml(String html) {
        html = html.replaceAll("<br\\s*/?>", "<br />");
        html = html.replaceAll("<hr\\s*/?>", "<hr />");
        html = html.replaceAll("<img([^>]*?)\\s*/?>", "<img$1 />");
        html = html.replaceAll("<input([^>]*?)\\s*/?>", "<input$1 />");
        html = html.replaceAll("<col([^>]*?)\\s*/?>", "<col$1 />");
        html = html.replaceAll("<area([^>]*?)\\s*/?>", "<area$1 />");
        html = html.replaceAll("<embed([^>]*?)\\s*/?>", "<embed$1 />");
        html = html.replaceAll("<source([^>]*?)\\s*/?>", "<source$1 />");
        return html;
    }
}
