package com.sap.config;

import cn.dev33.satoken.stp.StpInterface;
import com.sap.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * sa-token 权限认证实现
 */
@Component
public class SaTokenConfig implements StpInterface {

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        List<Integer> roleCodes = userRoleMapper.selectRoleCodesByUserId(userId);
        return roleCodes.stream().map(String::valueOf).collect(Collectors.toList());
    }
}
