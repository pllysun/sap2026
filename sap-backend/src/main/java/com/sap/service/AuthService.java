package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.dto.LoginDTO;
import com.sap.dto.RegisterDTO;
import com.sap.entity.Setting;
import com.sap.entity.User;
import com.sap.entity.UserRole;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import com.sap.util.PasswordUtil;
import com.sap.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private SettingMapper settingMapper;
    @Autowired
    private CacheService cacheService;

    /**
     * 登录
     */
    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getStudentId, dto.getStudentId())
        );
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        if (!PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        StpUtil.login(user.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("user", toVO(user));
        return result;
    }

    /**
     * 管理端登录 - 仅允许权限0,1,2
     */
    public Map<String, Object> adminLogin(LoginDTO dto) {
        Map<String, Object> result = login(dto);
        UserVO userVO = (UserVO) result.get("user");
        List<Integer> roles = userRoleMapper.selectRoleCodesByUserId(userVO.getId());
        boolean isAdmin = roles.stream().anyMatch(r -> r <= 2);
        if (!isAdmin) {
            StpUtil.logout();
            throw new BusinessException(403, "无管理端登录权限");
        }
        result.put("roles", roles);
        return result;
    }

    /**
     * 注册 (用户端)
     */
    @Transactional
    public void register(RegisterDTO dto) {
        // 检查学号是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStudentId, dto.getStudentId())
        );
        if (count > 0) {
            throw new BusinessException("该学号已注册");
        }
        // 获取当前年级
        Setting gradeSetting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
        );
        String currentGrade = gradeSetting != null ? gradeSetting.getSettingValue() : "2025";

        User user = new User();
        user.setStudentId(dto.getStudentId());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getName());
        user.setGender(dto.getGender());
        user.setQq(dto.getQq());
        user.setGrade(currentGrade);
        user.setAvatar("/default-avatar.png");
        user.setStatus(1);
        userMapper.insert(user);

        // 默认赋予游客权限
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleCode(4);
        userRoleMapper.insert(userRole);

        // 刷新缓存
        cacheService.addUser(user);
    }

    /**
     * 获取当前登录用户信息
     */
    public Map<String, Object> getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = cacheService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        List<Integer> roles = userRoleMapper.selectRoleCodesByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("user", toVO(user));
        result.put("roles", roles);
        return result;
    }

    /**
     * 修改个人信息（仅允许修改非核心字段：头像、网名、性别）
     */
    public void updateProfile(Map<String, Object> params) {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        if (params.containsKey("avatar")) {
            user.setAvatar((String) params.get("avatar"));
        }
        if (params.containsKey("nickname")) {
            user.setNickname((String) params.get("nickname"));
        }
        if (params.containsKey("gender")) {
            user.setGender(Integer.valueOf(params.get("gender").toString()));
        }
        userMapper.updateById(user);
        cacheService.refreshUsers();
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setStudentId(user.getStudentId());
        vo.setName(user.getName());
        vo.setNickname(user.getNickname());
        vo.setGender(user.getGender());
        vo.setQq(user.getQq());
        vo.setGrade(user.getGrade());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        return vo;
    }
}
