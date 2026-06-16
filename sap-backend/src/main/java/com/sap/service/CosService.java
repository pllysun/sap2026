package com.sap.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 腾讯云 COS 对象存储服务
 */
@Service
public class CosService {

    @Autowired
    private SettingService settingService;

    @Autowired
    private TrafficService trafficService;

    /** 允许上传的文件扩展名（逗号分隔），来自 application.yml: file.upload.allowed-types */
    @org.springframework.beans.factory.annotation.Value("${file.upload.allowed-types:jpg,jpeg,png,gif,webp,md,pdf,doc,docx,xls,xlsx}")
    private String allowedTypes;

    /** 单文件最大字节数，来自 application.yml: file.upload.max-size */
    @org.springframework.beans.factory.annotation.Value("${file.upload.max-size:52428800}")
    private long maxSize;

    private static final String KEY_BUCKET = "cos_bucket_name";
    private static final String KEY_SECRET_ID = "cos_secret_id";
    private static final String KEY_SECRET_KEY = "cos_secret_key";
    private static final String KEY_REGION = "cos_region";
    /** 可选：自定义下载域名(CDN)。APK 禁止走 COS 默认域名下载，配置它后下载走自定义域名。运行时设置，改了无需重启。 */
    private static final String KEY_CDN = "cos_cdn_domain";

    /**
     * 从数据库读取配置并构建 COSClient
     */
    private COSClient buildClient() {
        String secretId = settingService.getValue(KEY_SECRET_ID);
        String secretKey = settingService.getValue(KEY_SECRET_KEY);
        String region = settingService.getValue(KEY_REGION);

        // 配置项初始化为空字符串，需同时判空串，否则会带着空密钥构建客户端抛出晦涩错误
        if (isBlank(secretId) || isBlank(secretKey) || isBlank(region)) {
            throw new BusinessException("对象存储(COS)尚未配置，请先到「系统设置 → 对象存储」中填写并保存 SecretId / SecretKey / 地域 / Bucket");
        }

        // region 存的是类似 "cos.ap-nanjing"，需去掉 "cos." 前缀
        String regionName = region.startsWith("cos.") ? region.substring(4) : region;

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig config = new ClientConfig(new Region(regionName));
        return new COSClient(cred, config);
    }

    private String getBucketName() {
        String bucket = settingService.getValue(KEY_BUCKET);
        if (isBlank(bucket)) {
            throw new BusinessException("对象存储(COS)尚未配置，请先到「系统设置 → 对象存储」中填写并保存 SecretId / SecretKey / 地域 / Bucket");
        }
        return bucket;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 对象存储是否已完整配置（供前端/接口预检，避免上传后才发现未配置）
     */
    public boolean isConfigured() {
        return !isBlank(settingService.getValue(KEY_SECRET_ID))
                && !isBlank(settingService.getValue(KEY_SECRET_KEY))
                && !isBlank(settingService.getValue(KEY_REGION))
                && !isBlank(settingService.getValue(KEY_BUCKET));
    }

    /**
     * 上传文件到 COS
     * @return COS 公网访问 URL
     */
    public Map<String, String> upload(MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException("文件不能为空");

        // 大小校验
        if (maxSize > 0 && file.getSize() > maxSize) {
            throw new BusinessException("文件大小超过限制 " + (maxSize / 1024 / 1024) + "MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf(".")) : "";

        // 扩展名白名单校验
        String extNoDot = ext.startsWith(".") ? ext.substring(1).toLowerCase() : ext.toLowerCase();
        java.util.Set<String> allowed = new java.util.HashSet<>();
        for (String t : allowedTypes.split(",")) {
            if (!t.trim().isEmpty()) allowed.add(t.trim().toLowerCase());
        }
        if (extNoDot.isEmpty() || !allowed.contains(extNoDot)) {
            throw new BusinessException("不支持的文件类型：" + (extNoDot.isEmpty() ? "(无扩展名)" : extNoDot)
                    + "，仅允许 " + allowedTypes);
        }

        String dateDir = LocalDate.now().toString();
        String cosKey = "uploads/" + dateDir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        COSClient cosClient = buildClient();
        String bucketName = getBucketName();
        String region = settingService.getValue(KEY_REGION);
        String regionName = region.startsWith("cos.") ? region.substring(4) : region;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest putReq = new PutObjectRequest(bucketName, cosKey, file.getInputStream(), metadata);
            cosClient.putObject(putReq);

            // 构造公网 URL
            String url = publicUrl(cosKey, bucketName, regionName);

            // 上传计量：登记文件大小并按当前用户累计（埋点内部吞异常，不影响上传）
            trafficService.recordUpload(file.getSize(), cosKey, url, originalName);

            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("name", originalName);
            return result;
        } catch (Exception e) {
            throw new BusinessException("上传到 COS 失败: " + e.getMessage());
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 上传 APK（应用内升级专用）。不受通用图片/文档白名单限制；上传同时计算 SHA-256。
     * @return url（COS 公网直链）, sha256（小写十六进制）, size（字节）, name（原文件名）
     */
    public Map<String, Object> uploadApk(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("APK 文件不能为空");
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".apk")) {
            throw new BusinessException("请上传 .apk 安装包");
        }
        if (maxSize > 0 && file.getSize() > maxSize) {
            throw new BusinessException("APK 超过大小限制 " + (maxSize / 1024 / 1024) + "MB（可在 application.yml 调整 file.upload.max-size 与 multipart 限制）");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new BusinessException("读取 APK 失败: " + e.getMessage());
        }

        String sha256;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            sha256 = sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new BusinessException("计算 SHA-256 失败: " + e.getMessage());
        }

        String cosKey = "apk/sap-" + System.currentTimeMillis() + ".apk";
        COSClient cosClient = buildClient();
        String bucketName = getBucketName();
        String region = settingService.getValue(KEY_REGION);
        String regionName = region.startsWith("cos.") ? region.substring(4) : region;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType("application/vnd.android.package-archive");
            cosClient.putObject(new PutObjectRequest(bucketName, cosKey, new ByteArrayInputStream(bytes), metadata));

            String url = publicUrl(cosKey, bucketName, regionName);
            // APK 上传计量：登记到文件对象表（供下载重定向反查大小）并按发布者累计
            trafficService.recordUpload(bytes.length, cosKey, url, originalName);

            Map<String, Object> result = new HashMap<>();
            result.put("url", url);
            result.put("sha256", sha256);
            result.put("size", (long) bytes.length);
            result.put("name", originalName);
            return result;
        } catch (Exception e) {
            throw new BusinessException("上传 APK 到 COS 失败: " + e.getMessage());
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 检测 COS 连通性：上传 1KB 测试文件，成功后删除
     */
    public void testConnection() {
        COSClient cosClient = buildClient();
        String bucketName = getBucketName();
        String testKey = "_test/connectivity-check-" + System.currentTimeMillis() + ".txt";

        try {
            // 上传 1KB 测试文件
            byte[] testData = new byte[1024];
            java.util.Arrays.fill(testData, (byte) 'A');
            InputStream inputStream = new ByteArrayInputStream(testData);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(1024);
            metadata.setContentType("text/plain");

            PutObjectRequest putReq = new PutObjectRequest(bucketName, testKey, inputStream, metadata);
            cosClient.putObject(putReq);

            // 上传成功，立即删除
            cosClient.deleteObject(bucketName, testKey);
        } catch (Exception e) {
            throw new BusinessException("COS 连通性检测失败: " + e.getMessage());
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 获取 COS 配置（密钥脱敏）
     */
    /** 自定义下载域名(host)，未配置返回 null。接受 "dl.x.com" 或 "https://dl.x.com/" 形式。 */
    private String cdnHost() {
        String v = settingService.getValue(KEY_CDN);
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;
        v = v.replaceFirst("^https?://", "").replaceAll("/+$", "");
        int slash = v.indexOf('/');
        if (slash >= 0) v = v.substring(0, slash);
        return v.isEmpty() ? null : v;
    }

    /** 构造对象公网 URL：配了自定义域名(cos_cdn_domain)用它，否则用 COS 默认域名。 */
    private String publicUrl(String cosKey, String bucketName, String regionName) {
        String cdn = cdnHost();
        if (cdn != null) return "https://" + cdn + "/" + cosKey;
        return "https://" + bucketName + ".cos." + regionName + ".myqcloud.com/" + cosKey;
    }

    /** 把存量(默认域名)下载 URL 重写到自定义域名；未配置或非默认域名则原样返回。供下发下载地址用，改设置即生效、无需重新发版。 */
    public String toCdnUrl(String url) {
        String cdn = cdnHost();
        if (cdn == null || url == null || url.isBlank()) return url;
        try {
            java.net.URI u = java.net.URI.create(url.trim());
            String host = u.getHost();
            if (host != null && host.toLowerCase().endsWith(".myqcloud.com")) {
                String path = u.getRawPath() == null ? "" : u.getRawPath();
                return "https://" + cdn + path;
            }
        } catch (Exception ignore) {}
        return url;
    }

    /** 下载重定向端点 SSRF 白名单：COS 默认域名 + 已配置的自定义域名。 */
    public boolean isAllowedPublicHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        if (h.endsWith(".myqcloud.com")) return true;
        String cdn = cdnHost();
        return cdn != null && h.equals(cdn.toLowerCase());
    }

    public Map<String, String> getMaskedConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("bucketName", settingService.getValue(KEY_BUCKET));
        config.put("region", settingService.getValue(KEY_REGION));
        config.put("cdnDomain", settingService.getValue(KEY_CDN));
        config.put("secretId", maskSecret(settingService.getValue(KEY_SECRET_ID)));
        config.put("secretKey", maskSecret(settingService.getValue(KEY_SECRET_KEY)));
        return config;
    }

    /**
     * 保存 COS 配置
     * 密钥字段：为空或含 * 则跳过（不更新）
     */
    public void saveConfig(Map<String, String> config) {
        if (config.get("bucketName") != null) {
            saveSetting(KEY_BUCKET, config.get("bucketName"), "COS Bucket名称");
        }
        if (config.get("region") != null) {
            saveSetting(KEY_REGION, config.get("region"), "COS Region");
        }
        String secretId = config.get("secretId");
        if (secretId != null && !secretId.isEmpty() && !secretId.contains("*")) {
            saveSetting(KEY_SECRET_ID, secretId, "COS SecretId");
        }
        String secretKey = config.get("secretKey");
        if (secretKey != null && !secretKey.isEmpty() && !secretKey.contains("*")) {
            saveSetting(KEY_SECRET_KEY, secretKey, "COS SecretKey");
        }
        // 自定义下载域名：非密钥，允许填空清除
        if (config.get("cdnDomain") != null) {
            saveSetting(KEY_CDN, config.get("cdnDomain").trim(), "COS 自定义下载域名(APK/下载用CDN)");
        }
    }

    private void saveSetting(String key, String value, String description) {
        Setting s = new Setting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setDescription(description);
        settingService.updateSetting(s);
    }

    /**
     * 密钥脱敏：前4位 + **** + 后4位
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.length() <= 8) return secret;
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
