package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
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

    /** /download 代理中转的单文件大小上限(30MB)；更大的文件走 /go 直链由 CDN 承载。 */
    private static final long PROXY_MAX_BYTES = 30L * 1024 * 1024;

    @Autowired
    private CosService cosService;

    @Autowired
    private com.sap.service.TrafficService trafficService;

    @Autowired
    private com.sap.service.AppAccessService appAccessService;
    @Autowired
    private com.sap.service.AppDownloadService appDownloads;

    /**
     * 对象存储是否已配置（供前端上传前预检，未配置时给出明确引导）
     */
    @GetMapping("/cos-status")
    public Result<?> cosStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("configured", cosService.isConfigured());
        return Result.ok(data);
    }

    // App 基础等级即可维护头像等个人资料；真实角色不发生变化，COS 每日配额继续兜底防滥用。
    @PostMapping("/upload")
    @OperationLog("上传文件")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        appAccessService.requireBasicAccess(StpUtil.getLoginIdAsLong());
        requireGuestImage(file);
        Map<String, String> result = cosService.upload(file);
        return Result.ok(result);
    }

    @PostMapping("/upload/batch")
    @OperationLog("批量上传文件")
    public Result<?> batchUpload(@RequestParam("files") MultipartFile[] files) {
        appAccessService.requireBasicAccess(StpUtil.getLoginIdAsLong());
        if (files != null && files.length > 10) {
            return Result.error("单次最多上传 10 个文件");
        }
        List<Map<String, String>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                requireGuestImage(file);
                results.add(cosService.upload(file));
            }
        }
        return Result.ok(results);
    }

    /** 游客仍可维护头像和反馈截图；文档等大文件仅允许正式成员上传。 */
    private void requireGuestImage(MultipartFile file) {
        if (StpUtil.getRoleList().stream().anyMatch(r -> java.util.Set.of("0", "1", "2", "3").contains(r))) return;
        String name = java.util.Objects.toString(file.getOriginalFilename(), "").toLowerCase(java.util.Locale.ROOT);
        if (file.getSize() > 2L * 1024 * 1024 || !name.matches(".*\\.(?:jpg|jpeg|png|gif|webp)$"))
            throw new com.sap.common.BusinessException(403, "游客仅可上传 2 MB 以内的头像或反馈图片");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(12);
            boolean valid = bytes.length >= 12 && (
                    ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255)
                    || (name.endsWith(".png") && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8), new byte[]{-119,80,78,71,13,10,26,10}))
                    || (name.endsWith(".gif") && new String(bytes,0,6,java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a"))
                    || (name.endsWith(".webp") && new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")));
            if (!valid) throw new com.sap.common.BusinessException(400, "图片内容与文件类型不一致");
        } catch (java.io.IOException e) { throw new com.sap.common.BusinessException(400, "图片读取失败"); }
    }

    /**
     * 代理下载 – 解决 COS 跨域下载文件名为 UUID 的问题
     */
    @GetMapping("/download")
    public void download(@RequestParam String url,
                         @RequestParam(required = false, defaultValue = "file") String name,
                         jakarta.servlet.http.HttpServletResponse response) {
        if (appDownloads.isLegacyDownload(url)) {
            try { appDownloads.legacy(url, StpUtil.getLoginIdAsLong(), ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest(), response); }
            catch (java.io.IOException e) { response.setStatus(502); }
            return;
        }
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
        boolean schemeOk = "https".equalsIgnoreCase(scheme) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        // 收紧白名单：仅本系统已配置的 COS/CDN 主机，而非泛 .myqcloud.com（防打到他人桶）
        boolean hostOk = host != null && cosService.isAllowedPublicHost(host);
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

            // 限制经服务器中转的文件大小，超过则拒绝（大文件应走 /go 直链由 CDN 承载，避免刷爆服务器带宽）
            long contentLength = conn.getContentLengthLong();
            if (contentLength > PROXY_MAX_BYTES) {
                response.setStatus(413); // Payload Too Large
                return;
            }

            String contentType = conn.getContentType();
            if (contentType == null) contentType = "application/octet-stream";

            String encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            response.setContentType(contentType);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);

            try (java.io.InputStream in = conn.getInputStream();
                 java.io.OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len; long transferred = 0;
                while ((len = in.read(buf)) != -1) {
                    transferred += len;
                    if (transferred > PROXY_MAX_BYTES) throw new java.io.IOException("代理下载超过 30 MB 限额");
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
        if (appDownloads.isLegacyDownload(url)) {
            try { appDownloads.legacy(url, StpUtil.getLoginIdAsLong(), ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest(), response); }
            catch (java.io.IOException e) { response.setStatus(502); }
            return;
        }
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
        boolean schemeOk = "https".equalsIgnoreCase(scheme) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        boolean hostOk = cosService.isAllowedPublicHost(host);
        if (!schemeOk || !hostOk) {
            response.setStatus(403);
            return;
        }

        // 计量：优先用上传登记的大小，未命中则 HEAD 兜底取 Content-Length（取不到只计次）
        long size = trafficService.sizeOf(url);
        if (size < 0) size = headContentLength(uri);
        trafficService.recordDownload(size < 0 ? 0 : size);

        // 直接 302 到公网直链。
        // 注意：不可追加 response-content-disposition 等 response-* 覆盖参数——COS 对「匿名 GET」
        // 会拒绝（InvalidRequest: "Request specific response headers cannot be used for anonymous
        // GET response."），自定义 CDN 域名也无法走预签名。文件名由客户端自行处理：App 升级按
        // versionCode 落地，浏览器下载用对象键名。（这是 CDN 域名下 APK/文件下载 400 的根因。）
        try {
            response.sendRedirect(url);
        } catch (Exception e) {
            response.setStatus(500);
        }
    }

    /** 浏览器使用请求头鉴权获取经校验的文件位置，随后由对象存储传输文件。 */
    @PostMapping("/download-link")
    public Result<?> downloadLink(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.length() > 4096 || appDownloads.isLegacyDownload(url))
            throw new com.sap.common.BusinessException(403, "该文件请通过专用下载入口获取");
        java.net.URI uri;
        try { uri = java.net.URI.create(url); }
        catch (IllegalArgumentException e) { throw new com.sap.common.BusinessException(400, "下载地址无效"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !cosService.isAllowedPublicHost(uri.getHost()))
            throw new com.sap.common.BusinessException(403, "下载地址不属于本站对象存储");
        long size = trafficService.sizeOf(url);
        if (size < 0) size = headContentLength(uri);
        trafficService.recordDownload(Math.max(0, size));
        return Result.ok(Map.of("url", url));
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
