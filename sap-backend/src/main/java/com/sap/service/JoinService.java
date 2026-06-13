package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.*;
import com.sap.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JoinService {

    @Autowired private JoinManagerMapper joinManagerMapper;
    @Autowired private JoinApplicationMapper joinApplicationMapper;
    @Autowired private UserRoleMapper userRoleMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private TermMapper termMapper;
    @Autowired private PositionMapper positionMapper;
    @Autowired private SettingService settingService;
    @Autowired private BillService billService;
    @Autowired private CacheService cacheService;

    // ============ 开关 ============

    public boolean isJoinEnabled() {
        String val = settingService.getValue("join_enabled");
        return "true".equalsIgnoreCase(val);
    }

    public void toggleJoin(boolean enabled) {
        Setting s = new Setting();
        s.setSettingKey("join_enabled");
        s.setSettingValue(String.valueOf(enabled));
        s.setDescription("入会通道开关");
        settingService.updateSetting(s);
    }

    // ============ 负责人管理 ============

    public List<Map<String, Object>> listManagers() {
        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());

        List<JoinManager> managers = joinManagerMapper.selectList(
                new LambdaQueryWrapper<JoinManager>().eq(JoinManager::getGrade, grade)
        );

        return managers.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("userId", m.getUserId());
            map.put("alipayQr", m.getAlipayQr());
            map.put("wechatQr", m.getWechatQr());
            map.put("createdAt", m.getCreatedAt());
            // 查用户信息
            User user = userMapper.selectById(m.getUserId());
            if (user != null) {
                map.put("name", user.getName());
                map.put("qq", user.getQq());
                map.put("studentId", user.getStudentId());
            }
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 换届时自动添加当前年级的管理层为负责人
     * 逻辑与学习小组 autoAddLeadersFromTerm 一致：
     * 从换届表中筛选当前年级 + 管理层身份(roleCode<=2) 的人
     */
    public void initManagers() {
        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());

        // 1. 从缓存获取管理层身份（roleCode <= 2 的 Position）
        List<Position> adminPositions = cacheService.getAdminPositions();
        if (adminPositions.isEmpty()) return;
        List<Integer> adminPosIds = adminPositions.stream()
                .map(Position::getId).collect(Collectors.toList());

        // 2. 从换届表中筛选当前年级 + 管理层身份的人
        List<Term> terms = termMapper.selectList(
                new LambdaQueryWrapper<Term>()
                        .eq(Term::getGrade, grade)
                        .in(Term::getPositionId, adminPosIds)
        );

        // 3. 去重后添加为负责人
        Set<Long> addedUsers = new HashSet<>();
        for (Term term : terms) {
            if (addedUsers.contains(term.getUserId())) continue;
            // 检查是否已是负责人
            JoinManager existing = joinManagerMapper.selectOne(
                    new LambdaQueryWrapper<JoinManager>()
                            .eq(JoinManager::getUserId, term.getUserId())
                            .eq(JoinManager::getGrade, grade)
            );
            if (existing == null) {
                JoinManager jm = new JoinManager();
                jm.setUserId(term.getUserId());
                jm.setGrade(grade);
                joinManagerMapper.insert(jm);
            }
            addedUsers.add(term.getUserId());
        }
    }

    public void addManager(Long userId) {
        // 不允许添加超管
        UserRole superAdmin = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleCode, 0)
        );
        if (superAdmin != null) throw new BusinessException("超级管理员不可设为负责人");

        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());

        JoinManager existing = joinManagerMapper.selectOne(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getUserId, userId)
                        .eq(JoinManager::getGrade, grade)
        );
        if (existing != null) throw new BusinessException("该用户已是负责人");

        JoinManager jm = new JoinManager();
        jm.setUserId(userId);
        jm.setGrade(grade);
        joinManagerMapper.insert(jm);
    }

    public void removeManager(Long managerId) {
        joinManagerMapper.deleteById(managerId);
    }

    /**
     * 负责人上传收款码
     */
    public void uploadQrCode(Long userId, String alipayQr, String wechatQr) {
        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());

        JoinManager jm = joinManagerMapper.selectOne(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getUserId, userId)
                        .eq(JoinManager::getGrade, grade)
        );
        if (jm == null) throw new BusinessException("您不是当前年度负责人");

        if (alipayQr != null) jm.setAlipayQr(alipayQr);
        if (wechatQr != null) jm.setWechatQr(wechatQr);
        joinManagerMapper.updateById(jm);
    }

    // ============ 申请流程 ============

    /**
     * 游客申请入会 → 随机分配负责人
     */
    @Transactional
    public Map<String, Object> apply(Long userId) {
        // 检查是否已是成员
        UserRole memberRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleCode, 3)
        );
        if (memberRole != null) throw new BusinessException("您已是正式成员");

        // 检查是否已有申请
        JoinApplication existing = joinApplicationMapper.selectOne(
                new LambdaQueryWrapper<JoinApplication>()
                        .eq(JoinApplication::getUserId, userId)
                        .ne(JoinApplication::getStatus, 2)  // 排除已通过的
        );
        if (existing != null) {
            // 返回已有的申请信息
            return getApplicationDetail(existing);
        }

        if (!isJoinEnabled()) throw new BusinessException("入会通道未开启");

        // 查找已上传收款码的负责人
        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());

        List<JoinManager> managers = joinManagerMapper.selectList(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getGrade, grade)
                        .and(w -> w.isNotNull(JoinManager::getAlipayQr).ne(JoinManager::getAlipayQr, "")
                                .or()
                                .isNotNull(JoinManager::getWechatQr).ne(JoinManager::getWechatQr, ""))
        );
        if (managers.isEmpty()) throw new BusinessException("暂无可用负责人，请稍后再试");

        // 统计每个负责人被分配的次数，选最少的
        Map<Long, Long> assignCounts = new HashMap<>();
        for (JoinManager m : managers) {
            Long count = joinApplicationMapper.selectCount(
                    new LambdaQueryWrapper<JoinApplication>()
                            .eq(JoinApplication::getManagerId, m.getUserId())
                            .ne(JoinApplication::getStatus, 2)
            );
            assignCounts.put(m.getUserId(), count);
        }
        // 找到分配最少的
        Long minCount = Collections.min(assignCounts.values());
        List<JoinManager> candidates = managers.stream()
                .filter(m -> assignCounts.get(m.getUserId()).equals(minCount))
                .collect(Collectors.toList());
        // 随机选一个
        JoinManager selected = candidates.get(new Random().nextInt(candidates.size()));

        JoinApplication app = new JoinApplication();
        app.setUserId(userId);
        app.setManagerId(selected.getUserId());
        app.setStatus(0);
        app.setAssignedAt(LocalDateTime.now());
        joinApplicationMapper.insert(app);

        return getApplicationDetail(app);
    }

    /**
     * 超过24h后刷新负责人
     */
    @Transactional
    public Map<String, Object> refreshManager(Long userId) {
        JoinApplication app = joinApplicationMapper.selectOne(
                new LambdaQueryWrapper<JoinApplication>()
                        .eq(JoinApplication::getUserId, userId)
                        .eq(JoinApplication::getStatus, 0)
        );
        if (app == null) throw new BusinessException("无待处理的申请");

        if (app.getAssignedAt() != null && app.getAssignedAt().plusHours(24).isAfter(LocalDateTime.now())) {
            throw new BusinessException("分配未超过24小时，暂不可刷新");
        }

        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());

        // 排除当前负责人, 查找其它有收款码的负责人
        List<JoinManager> managers = joinManagerMapper.selectList(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getGrade, grade)
                        .ne(JoinManager::getUserId, app.getManagerId())
                        .and(w -> w.isNotNull(JoinManager::getAlipayQr).ne(JoinManager::getAlipayQr, "")
                                .or()
                                .isNotNull(JoinManager::getWechatQr).ne(JoinManager::getWechatQr, ""))
        );
        if (managers.isEmpty()) throw new BusinessException("暂无其他可用负责人");

        JoinManager selected = managers.get(new Random().nextInt(managers.size()));
        app.setManagerId(selected.getUserId());
        app.setAssignedAt(LocalDateTime.now());
        joinApplicationMapper.updateById(app);

        return getApplicationDetail(app);
    }

    /**
     * 提交支付编码
     */
    public void submitPaymentCode(Long userId, String paymentCode) {
        JoinApplication app = joinApplicationMapper.selectOne(
                new LambdaQueryWrapper<JoinApplication>()
                        .eq(JoinApplication::getUserId, userId)
                        .eq(JoinApplication::getStatus, 0)
        );
        if (app == null) throw new BusinessException("无待处理的申请");

        app.setPaymentCode(paymentCode);
        app.setStatus(1);
        app.setSubmittedAt(LocalDateTime.now());
        joinApplicationMapper.updateById(app);
    }

    /**
     * 查看我的申请
     */
    public Map<String, Object> getMyApplication(Long userId) {
        JoinApplication app = joinApplicationMapper.selectOne(
                new LambdaQueryWrapper<JoinApplication>()
                        .eq(JoinApplication::getUserId, userId)
                        .orderByDesc(JoinApplication::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (app == null) return null;
        return getApplicationDetail(app);
    }

    // ============ 审核 ============

    /**
     * 审核列表
     * @param status 0=待提交 1=已提交 2=已通过, null=全部
     * @param onlyMine 只看自己分配的
     */
    public List<Map<String, Object>> listApplications(Integer status, boolean onlyMine, Long currentUserId) {
        LambdaQueryWrapper<JoinApplication> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(JoinApplication::getStatus, status);
        }
        if (onlyMine) {
            wrapper.eq(JoinApplication::getManagerId, currentUserId);
        }
        wrapper.orderByDesc(JoinApplication::getCreatedAt);

        List<JoinApplication> list = joinApplicationMapper.selectList(wrapper);
        return list.stream().map(this::getApplicationDetailWithUser).collect(Collectors.toList());
    }

    /**
     * 审核通过
     */
    @Transactional
    public void approve(Long applicationId, Long approvedByUserId) {
        JoinApplication app = joinApplicationMapper.selectById(applicationId);
        if (app == null) throw new BusinessException("申请不存在");
        if (app.getStatus() == 2) throw new BusinessException("已审核通过");

        // 检查权限：负责人只能审核自己的
        List<Integer> roles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, approvedByUserId)
        ).stream().map(UserRole::getRoleCode).collect(Collectors.toList());

        boolean isLeaderOrSuper = roles.contains(0) || roles.contains(1);
        if (!isLeaderOrSuper && !app.getManagerId().equals(approvedByUserId)) {
            throw new BusinessException("无权审核此申请");
        }

        app.setStatus(2);
        app.setApprovedAt(LocalDateTime.now());
        app.setApprovedBy(approvedByUserId);
        joinApplicationMapper.updateById(app);

        // 升级用户角色：游客(4) → 成员(3)
        upgradeToMember(app.getUserId());

        // 创建财务收入记录
        createMembershipBill(app.getUserId());
    }

    /**
     * 直接升级会员
     */
    @Transactional
    public void directUpgrade(String studentId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getStudentId, studentId)
        );
        if (user == null) throw new BusinessException("用户不存在");

        // 检查是否已是成员
        UserRole memberRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId())
                        .eq(UserRole::getRoleCode, 3)
        );
        if (memberRole != null) throw new BusinessException("该用户已是正式成员");

        upgradeToMember(user.getId());
        createMembershipBill(user.getId());
    }

    // ============ 内部方法 ============

    private void upgradeToMember(Long userId) {
        // 删除游客角色
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleCode, 4)
        );
        // 添加成员角色（如果不存在）
        UserRole exists = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleCode, 3)
        );
        if (exists == null) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleCode(3);
            userRoleMapper.insert(ur);
        }
        // 与 UserService.upgradeToMember 保持一致：写入当前年级的"成员"换届记录，
        // 避免经入会通道升级的成员在历届/年级统计中缺失
        ensureMemberTerm(userId);
    }

    /** 确保用户在当前年级有"成员"身份的换届记录（缺则补，幂等） */
    private void ensureMemberTerm(Long userId) {
        String currentGrade = settingService.getValue("current_grade");
        if (currentGrade == null) currentGrade = String.valueOf(java.time.LocalDate.now().getYear());
        Position memberPosition = positionMapper.selectOne(
                new LambdaQueryWrapper<Position>().eq(Position::getPositionName, "成员")
        );
        if (memberPosition == null) return; // 无成员身份配置则跳过，不阻断升级
        Long cnt = termMapper.selectCount(
                new LambdaQueryWrapper<Term>()
                        .eq(Term::getUserId, userId)
                        .eq(Term::getGrade, currentGrade)
                        .eq(Term::getPositionId, memberPosition.getId())
        );
        if (cnt == 0) {
            Term term = new Term();
            term.setUserId(userId);
            term.setGrade(currentGrade);
            term.setPositionId(memberPosition.getId());
            termMapper.insert(term);
        }
    }

    private void createMembershipBill(Long userId) {
        User user = userMapper.selectById(userId);
        String feeStr = settingService.getValue("membership_fee");
        BigDecimal fee = new BigDecimal(feeStr != null ? feeStr : "30");

        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());

        Bill bill = new Bill();
        bill.setBillType(1); // 收入
        bill.setContent("会费 - " + (user != null ? user.getName() : "未知") + "(" + (user != null ? user.getStudentId() : "") + ")");
        bill.setAmount(fee);
        bill.setBillTime(LocalDateTime.now());
        bill.setRemark("入会审核通过自动记录");
        bill.setGrade(grade);
        billService.addBill(bill, null);
    }

    private Map<String, Object> getApplicationDetail(JoinApplication app) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", app.getId());
        map.put("status", app.getStatus());
        map.put("paymentCode", app.getPaymentCode());
        map.put("assignedAt", app.getAssignedAt());
        map.put("submittedAt", app.getSubmittedAt());
        map.put("approvedAt", app.getApprovedAt());
        map.put("createdAt", app.getCreatedAt());

        // 负责人信息
        if (app.getManagerId() != null) {
            User manager = userMapper.selectById(app.getManagerId());
            String grade = settingService.getValue("current_grade");
            if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());
            JoinManager jm = joinManagerMapper.selectOne(
                    new LambdaQueryWrapper<JoinManager>()
                            .eq(JoinManager::getUserId, app.getManagerId())
                            .eq(JoinManager::getGrade, grade)
                            .last("LIMIT 1")
            );
            if (manager != null) {
                map.put("managerName", manager.getName());
                map.put("managerQq", manager.getQq());
            }
            if (jm != null) {
                map.put("alipayQr", jm.getAlipayQr());
                map.put("wechatQr", jm.getWechatQr());
            }
        }

        // 是否可刷新（超过24h且状态为0）
        boolean canRefresh = app.getStatus() == 0 && app.getAssignedAt() != null
                && app.getAssignedAt().plusHours(24).isBefore(LocalDateTime.now());
        map.put("canRefresh", canRefresh);

        return map;
    }

    private Map<String, Object> getApplicationDetailWithUser(JoinApplication app) {
        Map<String, Object> map = getApplicationDetail(app);
        // 申请人信息
        User applicant = userMapper.selectById(app.getUserId());
        if (applicant != null) {
            map.put("userName", applicant.getName());
            map.put("studentId", applicant.getStudentId());
            map.put("userQq", applicant.getQq());
        }
        return map;
    }

    /**
     * 检查用户是否是当前年度负责人
     */
    public boolean isCurrentManager(Long userId) {
        String grade = settingService.getValue("current_grade");
        if (grade == null) grade = String.valueOf(java.time.LocalDate.now().getYear());
        return joinManagerMapper.selectCount(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getUserId, userId)
                        .eq(JoinManager::getGrade, grade)
        ) > 0;
    }
}
