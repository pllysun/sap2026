package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.BusinessException;
import com.sap.entity.*;
import com.sap.mapper.*;
import com.sap.util.PasswordUtil;
import com.sap.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private SettingMapper settingMapper;
    @Autowired
    private TermMapper termMapper;
    @Autowired
    private PositionMapper positionMapper;
    @Autowired
    private CacheService cacheService;

    public Page<User> listUsers(int current, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getName, keyword)
                    .or().like(User::getStudentId, keyword)
                    .or().like(User::getNickname, keyword));
        }
        wrapper.orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    public void updateUser(Long id, User user) {
        User existing = userMapper.selectById(id);
        if (existing == null) throw new BusinessException("用户不存在");
        existing.setNickname(user.getNickname());
        existing.setGender(user.getGender());
        existing.setQq(user.getQq());
        existing.setAvatar(user.getAvatar());
        existing.setStatus(user.getStatus());
        userMapper.updateById(existing);
        cacheService.updateUser(existing);
    }

    @Transactional
    public void updateUserRole(Long userId, List<Integer> roleCodes) {
        if (roleCodes == null) roleCodes = List.of();
        // 去重，避免触发 uk_user_role 唯一约束
        List<Integer> codes = roleCodes.stream().distinct().collect(Collectors.toList());

        // 防垂直越权：只有超级管理员(0)可以授予/变更 超管(0)、会长(1) 等高权限角色
        List<String> callerRoles = StpUtil.getRoleList();
        boolean callerIsSuperAdmin = callerRoles.contains("0");
        if (!callerIsSuperAdmin) {
            int grantMin = codes.stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
            List<Integer> targetCurrent = userRoleMapper.selectRoleCodesByUserId(userId);
            int targetMin = targetCurrent.stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
            if (grantMin <= 1 || targetMin <= 1) {
                throw new BusinessException("无权授予或修改高于自身权限的角色");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Integer code : codes) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleCode(code);
            userRoleMapper.insert(ur);
        }
    }

    public List<Integer> getUserRoles(Long userId) {
        return userRoleMapper.selectRoleCodesByUserId(userId);
    }

    public long countUsers() {
        return userMapper.selectCount(null);
    }

    /**
     * 游客升级为成员
     * <p>1. 将权限从游客(4)改为成员(3)</p>
     * <p>2. 在换届表中记录: 当前年级 + "成员"身份</p>
     *
     * @param userId 用户ID
     */
    @Transactional
    public void upgradeToMember(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        // 检查是否已是成员或更高权限
        List<Integer> roles = userRoleMapper.selectRoleCodesByUserId(userId);
        if (roles.stream().anyMatch(r -> r <= 3)) {
            throw new BusinessException("该用户已是成员或更高权限，无需升级");
        }

        // 1. 移除游客权限，赋予成员权限
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleCode, 4));
        UserRole memberRole = new UserRole();
        memberRole.setUserId(userId);
        memberRole.setRoleCode(3);
        userRoleMapper.insert(memberRole);

        // 2. 获取当前年级
        Setting gradeSetting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
        );
        String currentGrade = gradeSetting != null ? gradeSetting.getSettingValue() : "2025";

        // 3. 获取"成员"身份的Position ID
        Position memberPosition = positionMapper.selectOne(
                new LambdaQueryWrapper<Position>().eq(Position::getPositionName, "成员")
        );
        if (memberPosition == null) throw new BusinessException("未找到成员身份配置");

        // 4. 写入换届表记录
        Long existCount = termMapper.selectCount(
                new LambdaQueryWrapper<Term>()
                        .eq(Term::getUserId, userId)
                        .eq(Term::getGrade, currentGrade)
                        .eq(Term::getPositionId, memberPosition.getId())
        );
        if (existCount == 0) {
            Term term = new Term();
            term.setUserId(userId);
            term.setGrade(currentGrade);
            term.setPositionId(memberPosition.getId());
            termMapper.insert(term);
        }
    }

    /**
     * 批量升级游客为成员
     *
     * @param userIds 用户ID列表
     * @return 成功升级的数量
     */
    @Transactional
    public int batchUpgradeToMember(List<Long> userIds) {
        int success = 0;
        for (Long userId : userIds) {
            try {
                upgradeToMember(userId);
                success++;
            } catch (BusinessException e) {
                // 已是成员，跳过
            }
        }
        return success;
    }

    /**
     * 获取所有成员用户（roleCode <= 3，排除纯游客）
     * 用于换届选人时的候选名单
     */
    public List<User> listMemberUsers() {
        // 查找所有 roleCode <= 3 的 userId
        List<UserRole> memberRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().le(UserRole::getRoleCode, 3)
        );
        List<Long> memberUserIds = memberRoles.stream()
                .map(UserRole::getUserId)
                .distinct()
                .collect(Collectors.toList());
        if (memberUserIds.isEmpty()) return List.of();
        return userMapper.selectBatchIds(memberUserIds);
    }
}
