package com.sap.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.entity.Position;
import com.sap.entity.Role;
import com.sap.entity.User;
import com.sap.entity.UserRole;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.RoleMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import com.sap.service.SettingService;
import com.sap.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器。
 * <p>应用启动时（表由 Hibernate 自动创建后）幂等地补齐基础数据：
 * 权限角色、内置身份、初始超级管理员账号。仅当数据缺失时才插入，
 * 因此对已有数据的库不会产生任何副作用。设置项由
 * {@link SettingService#initDefaultSettings()} 负责，本类不重复处理。</p>
 */
@Component
@Order(20)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** 内置角色：role_code -> 名称 */
    private static final Object[][] ROLES = {
            {0, "超级管理员"}, {1, "会长"}, {2, "管理员"}, {3, "成员"}, {4, "游客"}
    };

    /** 内置身份：name, isSystem, sortOrder, maxCount, roleCode */
    private static final Object[][] POSITIONS = {
            {"会长", 1, 1, 1, 1},
            {"团支书", 1, 2, 1, 2},
            {"副会长", 0, 3, 2, 2},
            {"学术部部长", 0, 4, 1, 2},
            {"学术部副部长", 0, 5, 2, 2},
            {"宣传部部长", 0, 6, 1, 2},
            {"宣传部副部长", 0, 7, 2, 2},
            {"成员", 0, 99, 999, 3}
    };

    private static final String DEFAULT_ADMIN_STUDENT_ID = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final RoleMapper roleMapper;
    private final PositionMapper positionMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final SettingService settingService;

    public DataInitializer(RoleMapper roleMapper, PositionMapper positionMapper,
                           UserMapper userMapper, UserRoleMapper userRoleMapper,
                           SettingService settingService) {
        this.roleMapper = roleMapper;
        this.positionMapper = positionMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.settingService = settingService;
    }

    @Override
    public void run(String... args) {
        initRoles();
        initPositions();
        initAdmin();
    }

    private void initRoles() {
        for (Object[] r : ROLES) {
            Integer code = (Integer) r[0];
            Long exists = roleMapper.selectCount(
                    new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, code));
            if (exists == null || exists == 0) {
                Role role = new Role();
                role.setRoleCode(code);
                role.setRoleName((String) r[1]);
                roleMapper.insert(role);
                log.info("[DataInitializer] 初始化角色: {} ({})", r[1], code);
            }
        }
    }

    private void initPositions() {
        Long count = positionMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return; // 已有身份数据，跳过
        }
        for (Object[] p : POSITIONS) {
            Position pos = new Position();
            pos.setPositionName((String) p[0]);
            pos.setIsSystem((Integer) p[1]);
            pos.setSortOrder((Integer) p[2]);
            pos.setMaxCount((Integer) p[3]);
            pos.setRoleCode((Integer) p[4]);
            positionMapper.insert(pos);
        }
        log.info("[DataInitializer] 初始化内置身份 {} 条", POSITIONS.length);
    }

    private void initAdmin() {
        // 已存在任意超级管理员（role_code=0）则跳过
        Long superAdmins = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleCode, 0));
        if (superAdmins != null && superAdmins > 0) {
            return;
        }
        // 避免与同学号账号冲突
        Long sameStudent = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStudentId, DEFAULT_ADMIN_STUDENT_ID));
        if (sameStudent != null && sameStudent > 0) {
            log.warn("[DataInitializer] 无超级管理员但已存在学号 {} 的账号，跳过自动建号，请手动授予角色 0",
                    DEFAULT_ADMIN_STUDENT_ID);
            return;
        }

        User admin = new User();
        admin.setStudentId(DEFAULT_ADMIN_STUDENT_ID);
        admin.setPassword(PasswordUtil.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setName("系统管理员");
        admin.setNickname("系统管理员");
        admin.setQq("10000");
        admin.setGender(1);
        admin.setStatus(1);
        admin.setAvatar("/default-avatar.png");
        String grade = settingService.getCurrentGrade();
        admin.setGrade(grade != null ? grade : "2026");
        userMapper.insert(admin);

        UserRole ur = new UserRole();
        ur.setUserId(admin.getId());
        ur.setRoleCode(0);
        userRoleMapper.insert(ur);

        log.warn("========================================================");
        log.warn(" 已创建默认超级管理员账号: 学号={} 密码={}",
                DEFAULT_ADMIN_STUDENT_ID, DEFAULT_ADMIN_PASSWORD);
        log.warn(" 出于安全考虑，请首次登录后立即修改该密码！");
        log.warn("========================================================");
    }
}
