package com.sap.controller;

import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.CosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileController.class);

    @Autowired
    private CosService cosService;

    @Autowired
    private com.sap.service.TrafficService trafficService;

    /**
     * 对象存储是否已配置（供前端上传前预检，未配置时给出明确引导）
     */
    @GetMapping("/cos-status")
    public Result<?> cosStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("configured", cosService.isConfigured());
        return Result.ok(data);
    }

    @PostMapping("/upload")
    @OperationLog("上传文件")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        Map<String, String> result = cosService.upload(file);
        return Result.ok(result);
    }

    @PostMapping("/upload/batch")
    @OperationLog("批量上传文件")
    public Result<?> batchUpload(@RequestParam("files") MultipartFile[] files) {
        List<Map<String, String>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                results.add(cosService.upload(file));
            }
        }
        return Result.ok(results);
    }

    /**
     * 代理下载 – 解决 COS 跨域下载文件名为 UUID 的问题
     */
    @GetMapping("/download")
    public void download(@RequestParam String url,
                         @RequestParam(required = false, defaultValue = "file") String name,
                         jakarta.servlet.http.HttpServletResponse response) {
        // SSRF 防护：仅允许代理下载本系统对象存储(腾讯云 COS)域名下的文件，
        // 拒绝内网地址 / 云元数据 / 任意 http(s) 目标
        java.net.URI uri;
        try {
            uri = java.net.URI.create(url);
        } catch (Exception e) {
            response.setStatus(400);
            return;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean schemeOk = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        boolean hostOk = host != null && host.toLowerCase().endsWith(".myqcloud.com");
        if (!schemeOk || !hostOk) {
            response.setStatus(403);
            return;
        }
        try {
            java.net.URL fileUrl = uri.toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) fileUrl.openConnection();
            conn.setInstanceFollowRedirects(false); // 禁止跟随跳转，防止重定向绕过白名单
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            String contentType = conn.getContentType();
            if (contentType == null) contentType = "application/octet-stream";

            String encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            response.setContentType(contentType);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);

            try (java.io.InputStream in = conn.getInputStream();
                 java.io.OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
        } catch (Exception e) {
            log.error("代理下载文件失败: url={}", url, e);
            response.setStatus(500);
        }
    }

    /**
     * 下载计量重定向：记录本次下载流量（按当前用户，按文件大小近似），随后 302 跳转到 COS 直链。
     * <p>文件字节始终由 COS/CDN 传输，<b>不经过本服务器</b>，契合小带宽服务器。替代 /download 流式代理。</p>
     */
    @GetMapping("/go")
    public void go(@RequestParam String url,
                   @RequestParam(required = false, defaultValue = "file") String name,
                   jakarta.servlet.http.HttpServletResponse response) {
        java.net.URI uri;
        try {
            uri = java.net.URI.create(url);
        } catch (Exception e) {
            response.setStatus(400);
            return;
        }
        // SSRF 防护：仅允许跳转到本系统对象存储(腾讯云 COS 默认域名)或已配置的自定义下载域名(CDN)
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean schemeOk = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        boolean hostOk = cosService.isAllowedPublicHost(host);
        if (!schemeOk || !hostOk) {
            response.setStatus(403);
            return;
        }

        // 计量：优先用上传登记的大小，未命中则 HEAD 兜底取 Content-Length（取不到只计次）
        long size = trafficService.sizeOf(url);
        if (size < 0) size = headContentLength(uri);
        trafficService.recordDownload(size < 0 ? 0 : size);

        // 拼 response-content-disposition（公有桶 COS 支持该 query 覆盖）以保留原文件名，再 302
        try {
            String disp = "attachment;filename=\"" + name + "\"";
            String encodedDisp = java.net.URLEncoder.encode(disp, "UTF-8");
            String sep = url.contains("?") ? "&" : "?";
            response.sendRedirect(url + sep + "response-content-disposition=" + encodedDisp);
        } catch (Exception e) {
            try {
                response.sendRedirect(url);
            } catch (Exception ignore) {
                response.setStatus(500);
            }
        }
    }

    /** HEAD 请求取 COS 对象 Content-Length；失败返回 -1。仅在上传登记表未命中时兜底调用。 */
    private long headContentLength(java.net.URI uri) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            long len = conn.getContentLengthLong();
            conn.disconnect();
            return len;
        } catch (Exception e) {
            return -1;
        }
    }
}
