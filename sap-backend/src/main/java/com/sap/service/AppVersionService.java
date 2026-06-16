package com.sap.service;

import com.sap.entity.Setting;
import com.sap.vo.AppVersionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * App 版本元数据：存数据库(Setting 表)，管理端发布、客户端读取。
 * 发布时把 APK 传腾讯云 COS（复用平台 COS 配置），自动算 sha256/size。
 */
@Service
public class AppVersionService {

    static final String K_CODE = "app_version_code";
    static final String K_NAME = "app_version_name";
    static final String K_CHANGELOG = "app_changelog";
    static final String K_FORCE = "app_force_update";
    static final String K_MIN = "app_min_version_code";
    static final String K_SHA = "app_apk_sha256";
    static final String K_SIZE = "app_apk_size";
    static final String K_URL = "app_download_url";

    @Autowired
    private SettingService settingService;

    @Autowired
    private CosService cosService;

    /** 读取当前已发布版本（未发布时 versionCode=0，客户端视为无更新）。 */
    public AppVersionVO getLatest() {
        AppVersionVO vo = new AppVersionVO();
        vo.setVersionCode(parseInt(settingService.getValue(K_CODE), 0));
        vo.setVersionName(nullToEmpty(settingService.getValue(K_NAME)));
        vo.setChangelog(nullToEmpty(settingService.getValue(K_CHANGELOG)));
        vo.setForceUpdate(Boolean.parseBoolean(settingService.getValue(K_FORCE)));
        vo.setMinSupportedVersionCode(parseInt(settingService.getValue(K_MIN), 1));
        vo.setSha256(nullToEmpty(settingService.getValue(K_SHA)));
        vo.setSize(parseLong(settingService.getValue(K_SIZE), 0L));
        // 下发时把存量默认域名地址重写到自定义下载域名(若已配置)；APK 禁止走 COS 默认域名下载。
        // 这样后续配好自定义域名即生效，连已发布版本都无需重新发版。
        vo.setDownloadUrl(cosService.toCdnUrl(nullToEmpty(settingService.getValue(K_URL))));
        return vo;
    }

    /** 发布新版本：先把 APK 传 COS，再把元数据落库；返回落库后的最新版本。 */
    public AppVersionVO publish(MultipartFile apk, int versionCode, String versionName,
                                String changelog, boolean forceUpdate, int minSupportedVersionCode) {
        Map<String, Object> uploaded = cosService.uploadApk(apk);

        save(K_CODE, String.valueOf(versionCode), "App 最新 versionCode");
        save(K_NAME, nullToEmpty(versionName), "App 版本名");
        save(K_CHANGELOG, nullToEmpty(changelog), "App 更新说明");
        save(K_FORCE, String.valueOf(forceUpdate), "App 是否强制更新");
        save(K_MIN, String.valueOf(minSupportedVersionCode), "App 最低支持 versionCode");
        save(K_SHA, String.valueOf(uploaded.get("sha256")), "APK SHA-256");
        save(K_SIZE, String.valueOf(uploaded.get("size")), "APK 字节数");
        save(K_URL, String.valueOf(uploaded.get("url")), "APK 下载地址(COS/CDN)");
        return getLatest();
    }

    private void save(String key, String value, String desc) {
        Setting s = new Setting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setDescription(desc);
        settingService.updateSetting(s);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int parseInt(String s, int def) {
        try {
            return (s == null || s.isBlank()) ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseLong(String s, long def) {
        try {
            return (s == null || s.isBlank()) ? def : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
