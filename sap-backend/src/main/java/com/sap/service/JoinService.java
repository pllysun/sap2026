package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.*;
import com.sap.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JoinService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.sap.service.mail.EmailBusinessHooks emailHooks;

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

    @Transactional
    public void toggleJoin(boolean enabled) {
        if (enabled) {
            initManagers();
            Map<String, Object> readiness = getReadiness();
            if (!Boolean.TRUE.equals(readiness.get("ready"))) {
                throw new BusinessException("暂不能开启入会通道：" + ((List<?>) readiness.get("issues")).stream().map(String::valueOf).collect(Collectors.joining("；")));
            }
        }
        Setting s = new Setting();
        s.setSettingKey("join_enabled");
        s.setSettingValue(String.valueOf(enabled));
        s.setDescription("入会通道开关");
        settingService.updateSetting(s);
    }

    /** 开放前检查；不包含负责人个人信息，用户端仅使用开放状态和会费。 */
    public Map<String, Object> getReadiness() {
        String grade = currentGrade();
        List<JoinManager> managers = joinManagerMapper.selectList(
                new LambdaQueryWrapper<JoinManager>().eq(JoinManager::getGrade, grade));
        long available = managers.stream().filter(this::isAvailableManager).count();
        List<String> issues = new ArrayList<>();
        if (available == 0) issues.add("请先设置有效负责人，并上传至少一种收款码、完善联系 QQ");
        BigDecimal fee = null;
        try { fee = membershipFee(); } catch (BusinessException error) { issues.add(error.getMessage()); }
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", isJoinEnabled());
        info.put("ready", issues.isEmpty());
        info.put("grade", grade);
        info.put("membershipFee", fee);
        info.put("managerCount", managers.size());
        info.put("readyManagerCount", available);
        info.put("issues", issues);
        return info;
    }

    public Map<String, Object> getJoinInfo() {
        Map<String, Object> info = getReadiness();
        return Map.of("enabled", info.get("enabled"), "ready", info.get("ready"),
                "grade", info.get("grade"), "membershipFee", info.get("membershipFee") == null ? "" : info.get("membershipFee"));
    }

    // ============ 负责人管理 ============

    public List<Map<String, Object>> listManagers() {
        String grade = currentGrade();

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
        String grade = currentGrade();

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
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || !isManagementUser(userId)) {
            throw new BusinessException("请选择启用中的会长或管理员作为负责人");
        }

        String grade = currentGrade();

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
        JoinManager manager = joinManagerMapper.selectById(managerId);
        if (manager != null && joinApplicationMapper.selectCount(new LambdaQueryWrapper<JoinApplication>()
                .eq(JoinApplication::getManagerId, manager.getUserId()).ne(JoinApplication::getStatus, 2)) > 0) {
            throw new BusinessException("该负责人仍有未完成申请，请先处理后再移除");
        }
        joinManagerMapper.deleteById(managerId);
    }

    /**
     * 负责人上传收款码
     */
    public void uploadQrCode(Long userId, String alipayQr, String wechatQr) {
        String grade = currentGrade();

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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> apply(Long userId) {
        lockApplicant(userId);
        // 检查是否已是成员
        UserRole memberRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .in(UserRole::getRoleCode, 0, 1, 2, 3).last("LIMIT 1")
        );
        if (memberRole != null) throw new BusinessException("您已是正式成员");

        // 检查是否已有申请
        JoinApplication existing = joinApplicationMapper.selectOne(
                new LambdaQueryWrapper<JoinApplication>()
                        .eq(JoinApplication::getUserId, userId)
                        .ne(JoinApplication::getStatus, 2)  // 排除已通过的
                        .orderByDesc(JoinApplication::getId).last("LIMIT 1")
        );
        if (existing != null) {
            // 返回已有的申请信息
            return getApplicationDetail(existing);
        }

        if (!isJoinEnabled()) throw new BusinessException("入会通道未开启");

        // 查找已上传收款码的负责人
        String grade = currentGrade();

        List<JoinManager> managers = joinManagerMapper.selectList(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getGrade, grade)
                        .and(w -> w.isNotNull(JoinManager::getAlipayQr).ne(JoinManager::getAlipayQr, "")
                                .or()
                                .isNotNull(JoinManager::getWechatQr).ne(JoinManager::getWechatQr, ""))
        );
        managers = managers.stream().filter(this::isAvailableManager).collect(Collectors.toList());
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
        app.setGrade(grade);
        app.setMembershipFee(membershipFee());
        app.setStatus(0);
        app.setAssignedAt(LocalDateTime.now());
        joinApplicationMapper.insert(app);

        return getApplicationDetail(app);
    }

    /**
     * 超过24h后刷新负责人
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> refreshManager(Long userId) {
        lockApplicant(userId);
        JoinApplication app = joinApplicationMapper.selectOne(
                new LambdaQueryWrapper<JoinApplication>()
                        .eq(JoinApplication::getUserId, userId)
                        .eq(JoinApplication::getStatus, 0)
                        .orderByDesc(JoinApplication::getId).last("LIMIT 1")
        );
        if (app == null) throw new BusinessException("无待处理的申请");
        // 已经提交过付款资料的申请不更换收款人，避免重复付款。
        if (app.getSubmittedAt() != null) throw new BusinessException("已提交过付款信息，请联系原负责人核对");

        if (app.getAssignedAt() != null && app.getAssignedAt().plusHours(24).isAfter(LocalDateTime.now())) {
            throw new BusinessException("分配未超过24小时，暂不可刷新");
        }

        String grade = applicationGrade(app);

        // 排除当前负责人, 查找其它有收款码的负责人
        List<JoinManager> managers = joinManagerMapper.selectList(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getGrade, grade)
                        .ne(JoinManager::getUserId, app.getManagerId())
                        .and(w -> w.isNotNull(JoinManager::getAlipayQr).ne(JoinManager::getAlipayQr, "")
                                .or()
                                .isNotNull(JoinManager::getWechatQr).ne(JoinManager::getWechatQr, ""))
        );
        managers = managers.stream().filter(this::isAvailableManager).collect(Collectors.toList());
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void submitPaymentCode(Long userId, String paymentCode) {
        lockApplicant(userId);
        String code = paymentCode == null ? "" : paymentCode.trim();
        if (code.length() > 100) throw new BusinessException("交易单号最多 100 个字符");
        JoinApplication app = joinApplicationMapper.selectOne(
                new LambdaQueryWrapper<JoinApplication>()
                        .eq(JoinApplication::getUserId, userId)
                        .in(JoinApplication::getStatus, 0, 1, 3)
                        .orderByDesc(JoinApplication::getId).last("LIMIT 1")
        );
        if (app == null) throw new BusinessException("无待处理的申请");

        // 重试保持幂等；待审核时也允许更正单号，不需要再次付款。
        if (Integer.valueOf(1).equals(app.getStatus()) && Objects.equals(app.getPaymentCode(), code)) return;
        app.setPaymentCode(code);
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
                        .orderByDesc(JoinApplication::getId)
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void approve(Long applicationId, Long approvedByUserId) {
        JoinApplication app = joinApplicationMapper.selectById(applicationId);
        if (app == null) throw new BusinessException("申请不存在");
        lockApplicant(app.getUserId());
        app = joinApplicationMapper.selectById(applicationId);
        checkReviewPermission(app, approvedByUserId);
        if (Integer.valueOf(2).equals(app.getStatus())) return;
        // 保留人工核验直接通过待提交申请的业务能力；不强制经过付款提交阶段。
        if (!Set.of(0, 1, 3).contains(app.getStatus())) throw new BusinessException("申请状态无效");

        boolean alreadyMember = hasMemberRole(app.getUserId());
        if (!alreadyMember) {
            upgradeToMember(app.getUserId(), applicationGrade(app));
            createMembershipBill(app.getUserId(), applicationFee(app), applicationGrade(app));
        }
        completeApplications(app.getUserId(), approvedByUserId);
    }

    private void checkReviewPermission(JoinApplication app, Long approvedByUserId) {

        // 检查权限：负责人只能审核自己的
        List<Integer> roles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, approvedByUserId)
        ).stream().map(UserRole::getRoleCode).collect(Collectors.toList());

        boolean isLeaderOrSuper = roles.contains(0) || roles.contains(1);
        if (!isLeaderOrSuper && (!Objects.equals(app.getManagerId(), approvedByUserId)
                || !roles.contains(2) || !isCurrentManager(approvedByUserId))) {
            throw new BusinessException("无权审核此申请");
        }
    }

    /** 支付信息不完整时退回补充，保留收款人与单号，不引导重复缴费。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void returnApplication(Long applicationId, Long reviewedBy, String reason) {
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty() || note.length() > 500) throw new BusinessException("请填写 1 至 500 字的补充说明");
        JoinApplication app = joinApplicationMapper.selectById(applicationId);
        if (app == null) throw new BusinessException("申请不存在");
        lockApplicant(app.getUserId());
        app = joinApplicationMapper.selectById(applicationId);
        checkReviewPermission(app, reviewedBy);
        if (Integer.valueOf(2).equals(app.getStatus())) throw new BusinessException("已通过的申请不能退回");
        app.setStatus(3);
        app.setReviewNote(note);
        joinApplicationMapper.updateById(app);
    }

    /**
     * 直接升级会员
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void directUpgrade(String studentId) {
        directUpgrade(studentId, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void directUpgrade(String studentId, Long approvedByUserId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getStudentId, studentId)
        );
        if (user == null) throw new BusinessException("用户不存在");
        lockApplicant(user.getId());

        // 检查是否已是成员
        UserRole memberRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId())
                        .in(UserRole::getRoleCode, 0, 1, 2, 3).last("LIMIT 1")
        );
        if (memberRole != null) throw new BusinessException("该用户已是正式成员");

        JoinApplication pending = joinApplicationMapper.selectOne(new LambdaQueryWrapper<JoinApplication>()
                .eq(JoinApplication::getUserId, user.getId()).ne(JoinApplication::getStatus, 2)
                .orderByDesc(JoinApplication::getId).last("LIMIT 1"));
        String grade = pending == null ? currentGrade() : applicationGrade(pending);
        BigDecimal fee = pending == null ? membershipFee() : applicationFee(pending);
        upgradeToMember(user.getId(), grade);
        createMembershipBill(user.getId(), fee, grade);
        completeApplications(user.getId(), approvedByUserId);
    }

    // ============ 内部方法 ============

    private void upgradeToMember(Long userId, String grade) {
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
        ensureMemberTerm(userId, grade);
        if (exists == null) emailHooks.memberJoined(userId);
    }

    /** 确保用户在当前年级有"成员"身份的换届记录（缺则补，幂等） */
    private void ensureMemberTerm(Long userId, String currentGrade) {
        Position memberPosition = positionMapper.selectOne(
                new LambdaQueryWrapper<Position>().eq(Position::getRoleCode, 3)
                        .orderByAsc(Position::getId).last("LIMIT 1")
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

    private void createMembershipBill(Long userId, BigDecimal fee, String grade) {
        User user = userMapper.selectById(userId);

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
        map.put("grade", applicationGrade(app));
        map.put("membershipFee", applicationFee(app));
        map.put("reviewNote", app.getReviewNote());
        map.put("assignedAt", app.getAssignedAt());
        map.put("submittedAt", app.getSubmittedAt());
        map.put("approvedAt", app.getApprovedAt());
        map.put("createdAt", app.getCreatedAt());

        // 负责人信息
        if (app.getManagerId() != null) {
            User manager = userMapper.selectById(app.getManagerId());
            String grade = applicationGrade(app);
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
        boolean canRefresh = app.getStatus() != null && app.getStatus() == 0 && app.getSubmittedAt() == null && app.getAssignedAt() != null
                && app.getAssignedAt().plusHours(24).isBefore(LocalDateTime.now());
        map.put("canRefresh", canRefresh);
        map.put("managerId", app.getManagerId());

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
        String grade = currentGrade();
        return joinManagerMapper.selectCount(
                new LambdaQueryWrapper<JoinManager>()
                        .eq(JoinManager::getUserId, userId)
                        .eq(JoinManager::getGrade, grade)
        ) > 0;
    }

    private void lockApplicant(Long userId) {
        if (userId == null || userMapper.lockActiveUserById(userId) == null) throw new BusinessException("用户不存在或已停用");
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }

    private boolean hasMemberRole(Long userId) {
        return userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
                .in(UserRole::getRoleCode, 0, 1, 2, 3).last("LIMIT 1")) != null;
    }

    private boolean isManagementUser(Long userId) {
        return userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
                .in(UserRole::getRoleCode, 1, 2)) > 0;
    }

    private boolean isAvailableManager(JoinManager manager) {
        if (!hasText(manager.getAlipayQr()) && !hasText(manager.getWechatQr())) return false;
        User user = userMapper.selectById(manager.getUserId());
        return user != null && Integer.valueOf(1).equals(user.getStatus()) && hasText(user.getQq())
                && user.getQq().matches("[1-9][0-9]{4,14}") && isManagementUser(manager.getUserId());
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String currentGrade() {
        String grade = settingService.getValue("current_grade");
        return hasText(grade) ? grade.trim() : String.valueOf(java.time.LocalDate.now().getYear());
    }
    private String applicationGrade(JoinApplication app) { return hasText(app.getGrade()) ? app.getGrade() : currentGrade(); }
    private BigDecimal applicationFee(JoinApplication app) { return app.getMembershipFee() != null ? app.getMembershipFee() : membershipFee(); }
    private BigDecimal membershipFee() {
        String value = settingService.getValue("membership_fee");
        try {
            BigDecimal amount = new BigDecimal(value == null ? "30" : value.trim());
            if (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException error) { throw new BusinessException("请在系统设置中配置有效会费金额（非负数，最多两位小数）"); }
    }

    private void completeApplications(Long userId, Long approvedBy) {
        // 同账号遗留的多条申请一起收口；只有角色首次升级时记一笔会费。
        List<JoinApplication> applications = joinApplicationMapper.selectList(new LambdaQueryWrapper<JoinApplication>()
                .eq(JoinApplication::getUserId, userId).ne(JoinApplication::getStatus, 2));
        for (JoinApplication application : applications) {
            application.setStatus(2);
            application.setApprovedAt(LocalDateTime.now());
            application.setApprovedBy(approvedBy);
            joinApplicationMapper.updateById(application);
        }
    }
}
