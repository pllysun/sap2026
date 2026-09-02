package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 软协课表三级云控及 App 能力判定；不会修改用户真实角色。 */
@Service
public class AppAccessService {

    public static final String GUEST_ACCESS_LEVEL_KEY = "guest_access_level";
    public static final int CLOSED = 0;
    public static final int BASIC = 1;
    public static final int FULL = 2;

    private final SettingService settingService;
    private final UserRoleMapper userRoleMapper;

    public AppAccessService(SettingService settingService, UserRoleMapper userRoleMapper) {
        this.settingService = settingService;
        this.userRoleMapper = userRoleMapper;
    }

    /** 全局游客等级，异常配置安全降级为 0。 */
    public int guestAccessLevel() {
        return normalize(settingService.getValue(GUEST_ACCESS_LEVEL_KEY));
    }

    /** 当前账号在 App 内的有效能力等级；真实会员始终为 2。 */
    public int effectiveLevel(long userId) {
        return isMember(userId) ? FULL : guestAccessLevel();
    }

    public boolean isMember(long userId) {
        List<Integer> roles = userRoleMapper.selectRoleCodesByUserId(userId);
        return roles != null && roles.stream().anyMatch(role -> role != null && role <= 3);
    }

    public boolean hasFullAccess(long userId) {
        return effectiveLevel(userId) >= FULL;
    }

    public boolean hasBasicAccess(long userId) {
        return effectiveLevel(userId) >= BASIC;
    }

    public void requireFullAccess(long userId) {
        if (!hasFullAccess(userId)) {
            throw new BusinessException(403, "当前功能暂不可用");
        }
    }

    public void requireBasicAccess(long userId) {
        if (!hasBasicAccess(userId)) {
            throw new BusinessException(403, "当前功能暂不可用");
        }
    }

    public void updateGuestAccessLevel(int level) {
        if (level < CLOSED || level > FULL) {
            throw new BusinessException(400, "游客权限等级只能为 0、1 或 2");
        }
        com.sap.entity.Setting setting = new com.sap.entity.Setting();
        setting.setSettingKey(GUEST_ACCESS_LEVEL_KEY);
        setting.setSettingValue(String.valueOf(level));
        setting.setDescription("软协课表游客权限等级：0关闭、1基础、2完整App能力");
        settingService.updateSetting(setting);
    }

    static int normalize(String value) {
        try {
            return Math.max(CLOSED, Math.min(FULL, Integer.parseInt(value.trim())));
        } catch (Exception ignored) {
            return CLOSED;
        }
    }
}
