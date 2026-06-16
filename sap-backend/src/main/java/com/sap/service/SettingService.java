package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.entity.Setting;
import com.sap.mapper.SettingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SettingService {

    @Autowired
    private SettingMapper settingMapper;

    /**
     * 应用启动时，自动初始化默认设置项（仅 key 不存在时才插入）
     */
    @jakarta.annotation.PostConstruct
    public void initDefaultSettings() {
        insertIfAbsent("footer_address", "中南林业科技大学 学生活动中心1701", "页脚-地址");
        insertIfAbsent("footer_qq", "1576316531", "页脚-官方QQ");
        insertIfAbsent("footer_email", "sap@csuft.edu.cn", "页脚-联系邮箱");
        insertIfAbsent("footer_copyright", "中南林业科技大学软件协会", "页脚-版权主体名称");
        insertIfAbsent("qr_qq_group_url", "", "QQ群二维码图片URL");
        insertIfAbsent("qr_qq_group_name", "", "QQ群二维码名称");
        insertIfAbsent("qr_qq_account_url", "", "QQ号二维码图片URL");
        insertIfAbsent("qr_qq_account_name", "", "QQ号二维码名称");
        insertIfAbsent("join_enabled", "false", "入会通道开关");
        insertIfAbsent("membership_fee", "30", "会费金额(元)");
        
        insertIfAbsent("current_grade", "2026", "当前年级参数(核心依赖)");
        insertIfAbsent("cos_bucket_name", "", "对象存储 Bucket 名称");
        insertIfAbsent("cos_region", "", "对象存储 地域 (Region)");
        insertIfAbsent("cos_secret_id", "", "对象存储 SecretId");
        insertIfAbsent("cos_secret_key", "", "对象存储 SecretKey");
        insertIfAbsent("join_qq_group_url", "", "入会-新生群二维码");
        insertIfAbsent("join_qq_group_name", "", "入会-新生群名称");
        insertIfAbsent("join_group_link", "", "入会-一键加群链接");
        insertIfAbsent("allow_guest_login", "false", "是否允许非会员(游客)登录App");

        // 安卓 App 在线升级（由管理端「App 版本发布」页维护，APK 走 COS）
        insertIfAbsent("app_version_code", "0", "App 最新 versionCode（0=未发布）");
        insertIfAbsent("app_version_name", "", "App 版本名");
        insertIfAbsent("app_changelog", "", "App 更新说明");
        insertIfAbsent("app_force_update", "false", "App 是否强制更新");
        insertIfAbsent("app_min_version_code", "1", "App 最低支持 versionCode");
        insertIfAbsent("app_apk_sha256", "", "APK SHA-256");
        insertIfAbsent("app_apk_size", "0", "APK 字节数");
        insertIfAbsent("app_download_url", "", "APK 下载地址(COS/CDN)");
    }

    private void insertIfAbsent(String key, String value, String description) {
        Setting existing = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, key)
        );
        if (existing == null) {
            Setting s = new Setting();
            s.setSettingKey(key);
            s.setSettingValue(value);
            s.setDescription(description);
            settingMapper.insert(s);
        } else if (existing.getDescription() == null || existing.getDescription().trim().isEmpty()) {
            existing.setDescription(description);
            settingMapper.updateById(existing);
        }
    }

    public String getValue(String key) {
        Setting setting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, key)
        );
        return setting != null ? setting.getSettingValue() : null;
    }

    public void updateSetting(Setting setting) {
        Setting existing = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, setting.getSettingKey())
        );
        if (existing != null) {
            existing.setSettingValue(setting.getSettingValue());
            if (setting.getDescription() != null) {
                existing.setDescription(setting.getDescription());
            }
            settingMapper.updateById(existing);
        } else {
            settingMapper.insert(setting);
        }
    }

    public String getCurrentGrade() {
        return getValue("current_grade");
    }

    /**
     * 获取公开设置项（footer_ 和 qr_ 前缀），返回 key→value 映射
     */
    public java.util.Map<String, String> getPublicSettings() {
        List<Setting> list = settingMapper.selectList(
                new LambdaQueryWrapper<Setting>()
                        .likeRight(Setting::getSettingKey, "footer_")
                        .or()
                        .likeRight(Setting::getSettingKey, "qr_")
                        .or()
                        .likeRight(Setting::getSettingKey, "join_qq_")
                        .or()
                        .eq(Setting::getSettingKey, "join_group_link")
        );
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (Setting s : list) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return map;
    }
}
