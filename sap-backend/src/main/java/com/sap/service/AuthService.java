package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.dto.LoginDTO;
import com.sap.dto.RegisterDTO;
import com.sap.entity.Setting;
import com.sap.entity.Term;
import com.sap.entity.User;
import com.sap.entity.UserRole;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.TermMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import com.sap.util.PasswordUtil;
import com.sap.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
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
    @Autowired
    private TermMapper termMapper;

    /** 登录失败锁定：达到阈值后锁定一段时间，缓解在线暴力破解 */
    private static final int MAX_FAIL_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 10 * 60 * 1000L;
    // value: [failCount, lockUntilEpochMillis]
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> loginAttempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 登录（Web/PC，使用全局默认 token 时效）
     */
    public Map<String, Object> login(LoginDTO dto) {
        User user = authenticate(dto);
        StpUtil.login(user.getId());
        return loginResult(user);
    }

    /**
     * App 登录：签发永不过期的长效 token（device=app，timeout/activeTimeout=-1）。
     * <p>满足“本地有凭证即永久免密”——App 把 token 存本地，启动时调 /api/auth/info 校验即可免密。
     * 长效 token 是长期凭证，App 端须安全存储（Android Keystore / EncryptedSharedPreferences）。</p>
     */
    public Map<String, Object> appLogin(LoginDTO dto) {
        User user = authenticate(dto);
        // 非会员(仅游客角色 4)登录受管理端「非会员登录」开关控制；会员(角色≤3)不受限
        List<Integer> roles = userRoleMapper.selectRoleCodesByUserId(user.getId());
        boolean isMember = roles.stream().anyMatch(r -> r <= 3);
        if (!isMember && !guestLoginAllowed()) {
            throw new BusinessException(403, "当前暂未开放非会员登录，请先完成入会或联系软件协会");
        }
        StpUtil.login(user.getId(), new cn.dev33.satoken.stp.SaLoginModel()
                .setDevice("app")
                .setTimeout(-1)
                .setActiveTimeout(-1));
        Map<String, Object> result = loginResult(user);
        result.put("roles", roles); // 客户端据此判定会员/非会员并门控功能
        return result;
    }

    /** 管理端「非会员登录」开关（Setting: allow_guest_login）。 */
    private boolean guestLoginAllowed() {
        Setting s = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "allow_guest_login")
        );
        return s != null && "true".equalsIgnoreCase(s.getSettingValue());
    }

    /** 取客户端 IP（经 nginx 反代取 X-Forwarded-For 首段，否则 RemoteAddr）。 */
    private String clientIp() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attr =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attr == null) return "unknown";
            jakarta.servlet.http.HttpServletRequest req = attr.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
            String ip = req.getRemoteAddr();
            return ip != null ? ip : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 校验账号密码（含失败锁定/禁用判断），返回用户；失败抛业务异常。 */
    private User authenticate(LoginDTO dto) {
        // 锁定按 (学号 + 客户端IP) 维度：避免攻击者对某学号循环错误把受害者从其自身 IP 也锁死(定向DoS)
        String key = dto.getStudentId() + "|" + clientIp();
        long now = System.currentTimeMillis();
        long[] rec = key != null ? loginAttempts.get(key) : null;
        if (rec != null && rec[1] > now) {
            long mins = (rec[1] - now) / 60000 + 1;
            throw new BusinessException("登录失败次数过多，账号已临时锁定，请约 " + mins + " 分钟后再试");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getStudentId, dto.getStudentId())
        );
        // 统一“账号或密码错误”，避免通过提示区分账号是否存在（防枚举）
        if (user == null) {
            recordLoginFail(key, now);
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        if (!PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            recordLoginFail(key, now);
            throw new BusinessException("账号或密码错误");
        }
        if (key != null) loginAttempts.remove(key); // 登录成功，清除失败计数
        return user;
    }

    private Map<String, Object> loginResult(User user) {
        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("user", toVO(user));
        return result;
    }

    private void recordLoginFail(String key, long now) {
        if (key == null) return;
        long[] rec = loginAttempts.computeIfAbsent(key, k -> new long[]{0, 0});
        synchronized (rec) {
            rec[0]++;
            if (rec[0] >= MAX_FAIL_ATTEMPTS) {
                rec[1] = now + LOCK_DURATION_MS;
                rec[0] = 0; // 锁定后重置计数，锁定期满重新计
            }
        }
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
        return buildCurrentUser(true);
    }

    /**
     * 轻量版当前用户信息：不含头像 + 平台身份(届+职务) + 修改时间 updatedAt（毫秒）。
     * <p>App 频繁调用此接口判断是否要重新拉头像：本地缓存的 updatedAt &lt; 服务端 updatedAt 才去调
     * {@link #getCurrentUser()} 拿新头像 URL，否则直接用本地缓存头像——省 CDN 流量。</p>
     */
    public Map<String, Object> getCurrentUserLight() {
        return buildCurrentUser(false);
    }

    /** 组装当前用户信息：含/不含头像，附 roles + identities(届+身份) + updatedAt(毫秒)。 */
    private Map<String, Object> buildCurrentUser(boolean includeAvatar) {
        long userId = StpUtil.getLoginIdAsLong();
        User user = cacheService.getUserById(userId);
        if (user == null) {
            // 缓存未命中（如新建/初始化用户尚未进缓存）时回源数据库，避免误报“用户不存在”
            user = userMapper.selectById(userId);
            if (user != null) {
                cacheService.refreshUsers();
            }
        }
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserVO vo = toVO(user);
        if (!includeAvatar) {
            vo.setAvatar(null); // 轻量版不下发头像，App 用本地缓存
        }
        List<Integer> roles = userRoleMapper.selectRoleCodesByUserId(userId);
        // 平台身份：换届表(届 grade + 身份 position) 逐条，如 2025/宣传部部长、2026/会长；无记录=游客
        List<Map<String, Object>> identities = new ArrayList<>();
        for (Term t : termMapper.selectList(
                new LambdaQueryWrapper<Term>().eq(Term::getUserId, userId))) {
            Map<String, Object> idy = new HashMap<>();
            idy.put("grade", t.getGrade());
            idy.put("positionName", cacheService.getPositionName(t.getPositionId()));
            identities.add(idy);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("user", vo);
        result.put("roles", roles);
        result.put("identities", identities);
        result.put("updatedAt", user.getUpdatedAt() == null ? null
                : user.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
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
        // 显式刷新 updated_at：user 由 selectById 读出后 updatedAt 非空，MyBatis-Plus 的
        // strictUpdateFill 仅在字段为 null 时填充 → 不显式置则会把旧值写回，App 端据 updatedAt
        // 判断是否重拉头像的省流量逻辑就永远认为“没变过”，导致换头像后 App 不刷新。
        user.setUpdatedAt(java.time.LocalDateTime.now());
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
