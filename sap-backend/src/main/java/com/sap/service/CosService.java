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

    /**
     * 从数据库读取配置并构建 COSClient
     */
    private COSClient buildClient() {
        String secretId = settingService.getValue(KEY_SECRET_ID);
        String secretKey = settingService.getValue(KEY_SECRET_KEY);
        String region = settingService.getValue(KEY_REGION);

        if (secretId == null || secretKey == null || region == null) {
            throw new BusinessException("COS 配置不完整，请到系统设置中配置对象存储");
        }

        // region 存的是类似 "cos.ap-nanjing"，需去掉 "cos." 前缀
        String regionName = region.startsWith("cos.") ? region.substring(4) : region;

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig config = new ClientConfig(new Region(regionName));
        return new COSClient(cred, config);
    }

    private String getBucketName() {
        String bucket = settingService.getValue(KEY_BUCKET);
        if (bucket == null || bucket.isEmpty()) {
            throw new BusinessException("COS Bucket 未配置");
        }
        return bucket;
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
            String url = "https://" + bucketName + ".cos." + regionName + ".myqcloud.com/" + cosKey;

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
    public Map<String, String> getMaskedConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("bucketName", settingService.getValue(KEY_BUCKET));
        config.put("region", settingService.getValue(KEY_REGION));
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
