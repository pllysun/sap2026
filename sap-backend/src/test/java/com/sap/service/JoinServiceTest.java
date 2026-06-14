package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.*;
import com.sap.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JoinServiceTest extends BaseUnitTest {

    @Mock JoinManagerMapper joinManagerMapper;
    @Mock JoinApplicationMapper joinApplicationMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock UserMapper userMapper;
    @Mock TermMapper termMapper;
    @Mock PositionMapper positionMapper;
    @Mock SettingService settingService;
    @Mock BillService billService;
    @Mock CacheService cacheService;

    @InjectMocks JoinService service;

    private JoinManager manager(long id, long userId, String grade) {
        JoinManager m = new JoinManager();
        m.setId(id);
        m.setUserId(userId);
        m.setGrade(grade);
        m.setAlipayQr("alipay-url");
        m.setWechatQr("wechat-url");
        return m;
    }

    private UserRole role(long userId, int code) {
        UserRole r = new UserRole();
        r.setUserId(userId);
        r.setRoleCode(code);
        return r;
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setName("用户" + id);
        u.setStudentId("S" + id);
        u.setQq("qq" + id);
        return u;
    }

    // ============ 开关 ============

    @Test
    void isJoinEnabled_trueWhenSettingTrue() {
        when(settingService.getValue("join_enabled")).thenReturn("true");
        assertTrue(service.isJoinEnabled());
    }

    @Test
    void isJoinEnabled_falseOtherwise() {
        when(settingService.getValue("join_enabled")).thenReturn("false");
        assertFalse(service.isJoinEnabled());
        when(settingService.getValue("join_enabled")).thenReturn(null);
        assertFalse(service.isJoinEnabled());
    }

    @Test
    void toggleJoin_updatesSetting() {
        service.toggleJoin(true);
        ArgumentCaptor<Setting> cap = ArgumentCaptor.forClass(Setting.class);
        verify(settingService).updateSetting(cap.capture());
        assertEquals("join_enabled", cap.getValue().getSettingKey());
        assertEquals("true", cap.getValue().getSettingValue());
    }

    // ============ listManagers ============

    @Test
    void listManagers_returnsWithUserInfo() {
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectList(any())).thenReturn(List.of(manager(1, 10, "2025")));
        when(userMapper.selectById(10L)).thenReturn(user(10));

        List<Map<String, Object>> result = service.listManagers();
        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).get("userId"));
        assertEquals("用户10", result.get(0).get("name"));
        assertEquals("S10", result.get(0).get("studentId"));
    }

    @Test
    void listManagers_nullGradeFallsBackToCurrentYear_userMissing() {
        when(settingService.getValue("current_grade")).thenReturn(null);
        when(joinManagerMapper.selectList(any())).thenReturn(List.of(manager(1, 10, "x")));
        when(userMapper.selectById(10L)).thenReturn(null);

        List<Map<String, Object>> result = service.listManagers();
        assertEquals(1, result.size());
        assertFalse(result.get(0).containsKey("name"));
    }

    // ============ addManager ============

    @Test
    void addManager_superAdmin_throws() {
        when(userRoleMapper.selectOne(any())).thenReturn(role(5, 0));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addManager(5L));
        assertEquals("超级管理员不可设为负责人", ex.getMessage());
    }

    @Test
    void addManager_alreadyManager_throws() {
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectOne(any())).thenReturn(manager(1, 5, "2025"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addManager(5L));
        assertEquals("该用户已是负责人", ex.getMessage());
    }

    @Test
    void addManager_inserts() {
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectOne(any())).thenReturn(null);

        service.addManager(5L);
        ArgumentCaptor<JoinManager> cap = ArgumentCaptor.forClass(JoinManager.class);
        verify(joinManagerMapper).insert(cap.capture());
        assertEquals(5L, cap.getValue().getUserId());
        assertEquals("2025", cap.getValue().getGrade());
    }

    @Test
    void removeManager_delegatesDelete() {
        service.removeManager(9L);
        verify(joinManagerMapper).deleteById(9L);
    }

    // ============ uploadQrCode ============

    @Test
    void uploadQrCode_notManager_throws() {
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadQrCode(1L, "a", "w"));
        assertEquals("您不是当前年度负责人", ex.getMessage());
    }

    @Test
    void uploadQrCode_updates() {
        JoinManager jm = manager(1, 10, "2025");
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectOne(any())).thenReturn(jm);

        service.uploadQrCode(10L, "newAlipay", null);
        assertEquals("newAlipay", jm.getAlipayQr());
        assertEquals("wechat-url", jm.getWechatQr()); // unchanged (null arg)
        verify(joinManagerMapper).updateById(jm);
    }

    // ============ initManagers ============

    @Test
    void initManagers_noAdminPositions_returnsEarly() {
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(cacheService.getAdminPositions()).thenReturn(List.of());
        service.initManagers();
        verify(termMapper, never()).selectList(any());
    }

    @Test
    void initManagers_addsNewManagersDedup() {
        when(settingService.getValue("current_grade")).thenReturn("2025");
        Position p = new Position();
        p.setId(1);
        when(cacheService.getAdminPositions()).thenReturn(List.of(p));

        Term t1 = new Term();
        t1.setUserId(100L);
        Term t2 = new Term();
        t2.setUserId(100L); // duplicate
        Term t3 = new Term();
        t3.setUserId(200L);
        when(termMapper.selectList(any())).thenReturn(List.of(t1, t2, t3));
        when(joinManagerMapper.selectOne(any())).thenReturn(null);

        service.initManagers();
        // 100 (once, dedup) + 200 = 2 inserts
        verify(joinManagerMapper, times(2)).insert(any(JoinManager.class));
    }

    @Test
    void initManagers_existingManagerNotReinserted() {
        when(settingService.getValue("current_grade")).thenReturn("2025");
        Position p = new Position();
        p.setId(1);
        when(cacheService.getAdminPositions()).thenReturn(List.of(p));
        Term t = new Term();
        t.setUserId(100L);
        when(termMapper.selectList(any())).thenReturn(List.of(t));
        when(joinManagerMapper.selectOne(any())).thenReturn(manager(1, 100, "2025"));

        service.initManagers();
        verify(joinManagerMapper, never()).insert(any());
    }

    // ============ apply ============

    @Test
    void apply_alreadyMember_throws() {
        when(userRoleMapper.selectOne(any())).thenReturn(role(1, 3));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(1L));
        assertEquals("您已是正式成员", ex.getMessage());
    }

    @Test
    void apply_existingApplication_returnsDetail() {
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        JoinApplication existing = new JoinApplication();
        existing.setId(7L);
        existing.setStatus(0);
        existing.setManagerId(null);
        when(joinApplicationMapper.selectOne(any())).thenReturn(existing);

        Map<String, Object> result = service.apply(1L);
        assertEquals(7L, result.get("id"));
        verify(joinApplicationMapper, never()).insert(any());
    }

    @Test
    void apply_joinDisabled_throws() {
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        when(joinApplicationMapper.selectOne(any())).thenReturn(null);
        when(settingService.getValue("join_enabled")).thenReturn("false");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(1L));
        assertEquals("入会通道未开启", ex.getMessage());
    }

    @Test
    void apply_noManagers_throws() {
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        when(joinApplicationMapper.selectOne(any())).thenReturn(null);
        when(settingService.getValue("join_enabled")).thenReturn("true");
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(1L));
        assertEquals("暂无可用负责人，请稍后再试", ex.getMessage());
    }

    @Test
    void apply_assignsLeastLoadedManager_andInserts() {
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        when(joinApplicationMapper.selectOne(any())).thenReturn(null);
        when(settingService.getValue("join_enabled")).thenReturn("true");
        when(settingService.getValue("current_grade")).thenReturn("2025");
        JoinManager m1 = manager(1, 10, "2025");
        when(joinManagerMapper.selectList(any())).thenReturn(List.of(m1));
        when(joinApplicationMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectById(10L)).thenReturn(user(10));
        when(joinManagerMapper.selectOne(any())).thenReturn(null).thenReturn(m1);

        Map<String, Object> result = service.apply(99L);

        ArgumentCaptor<JoinApplication> cap = ArgumentCaptor.forClass(JoinApplication.class);
        verify(joinApplicationMapper).insert(cap.capture());
        assertEquals(99L, cap.getValue().getUserId());
        assertEquals(10L, cap.getValue().getManagerId());
        assertEquals(0, cap.getValue().getStatus());
        assertNotNull(result);
    }

    // ============ refreshManager ============

    @Test
    void refreshManager_noApplication_throws() {
        when(joinApplicationMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.refreshManager(1L));
        assertEquals("无待处理的申请", ex.getMessage());
    }

    @Test
    void refreshManager_within24h_throws() {
        JoinApplication app = new JoinApplication();
        app.setStatus(0);
        app.setAssignedAt(LocalDateTime.now().minusHours(2));
        when(joinApplicationMapper.selectOne(any())).thenReturn(app);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.refreshManager(1L));
        assertEquals("分配未超过24小时，暂不可刷新", ex.getMessage());
    }

    @Test
    void refreshManager_noOtherManagers_throws() {
        JoinApplication app = new JoinApplication();
        app.setStatus(0);
        app.setManagerId(10L);
        app.setAssignedAt(LocalDateTime.now().minusHours(25));
        when(joinApplicationMapper.selectOne(any())).thenReturn(app);
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.refreshManager(1L));
        assertEquals("暂无其他可用负责人", ex.getMessage());
    }

    @Test
    void refreshManager_reassigns() {
        JoinApplication app = new JoinApplication();
        app.setStatus(0);
        app.setManagerId(10L);
        app.setAssignedAt(LocalDateTime.now().minusHours(25));
        when(joinApplicationMapper.selectOne(any())).thenReturn(app).thenReturn(null);
        when(settingService.getValue("current_grade")).thenReturn("2025");
        JoinManager other = manager(2, 20, "2025");
        when(joinManagerMapper.selectList(any())).thenReturn(List.of(other));
        when(userMapper.selectById(20L)).thenReturn(user(20));

        service.refreshManager(1L);
        assertEquals(20L, app.getManagerId());
        verify(joinApplicationMapper).updateById(app);
    }

    @Test
    void refreshManager_nullAssignedAt_passesThroughToReassign() {
        JoinApplication app = new JoinApplication();
        app.setStatus(0);
        app.setManagerId(10L);
        app.setAssignedAt(null); // skip 24h check
        when(joinApplicationMapper.selectOne(any())).thenReturn(app);
        when(settingService.getValue("current_grade")).thenReturn("2025");
        JoinManager other = manager(2, 20, "2025");
        when(joinManagerMapper.selectList(any())).thenReturn(List.of(other));
        when(userMapper.selectById(20L)).thenReturn(user(20));

        service.refreshManager(1L);
        assertEquals(20L, app.getManagerId());
    }

    // ============ submitPaymentCode ============

    @Test
    void submitPaymentCode_noApplication_throws() {
        when(joinApplicationMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitPaymentCode(1L, "code"));
        assertEquals("无待处理的申请", ex.getMessage());
    }

    @Test
    void submitPaymentCode_updates() {
        JoinApplication app = new JoinApplication();
        app.setStatus(0);
        when(joinApplicationMapper.selectOne(any())).thenReturn(app);

        service.submitPaymentCode(1L, "PAY123");
        assertEquals("PAY123", app.getPaymentCode());
        assertEquals(1, app.getStatus());
        verify(joinApplicationMapper).updateById(app);
    }

    // ============ getMyApplication ============

    @Test
    void getMyApplication_null_returnsNull() {
        when(joinApplicationMapper.selectOne(any())).thenReturn(null);
        assertNull(service.getMyApplication(1L));
    }

    @Test
    void getMyApplication_returnsDetail() {
        JoinApplication app = new JoinApplication();
        app.setId(3L);
        app.setStatus(1);
        when(joinApplicationMapper.selectOne(any())).thenReturn(app);
        Map<String, Object> result = service.getMyApplication(1L);
        assertEquals(3L, result.get("id"));
    }

    // ============ listApplications ============

    @Test
    void listApplications_withStatusAndOnlyMine() {
        JoinApplication app = new JoinApplication();
        app.setId(1L);
        app.setUserId(50L);
        when(joinApplicationMapper.selectList(any())).thenReturn(List.of(app));
        when(userMapper.selectById(50L)).thenReturn(user(50));

        List<Map<String, Object>> result = service.listApplications(1, true, 99L);
        assertEquals(1, result.size());
        assertEquals("用户50", result.get(0).get("userName"));
        assertEquals("S50", result.get(0).get("studentId"));
    }

    @Test
    void listApplications_nullStatusNotMine() {
        when(joinApplicationMapper.selectList(any())).thenReturn(List.of());
        List<Map<String, Object>> result = service.listApplications(null, false, 99L);
        assertTrue(result.isEmpty());
    }

    // ============ approve ============

    @Test
    void approve_notFound_throws() {
        when(joinApplicationMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, 9L));
        assertEquals("申请不存在", ex.getMessage());
    }

    @Test
    void approve_alreadyApproved_throws() {
        JoinApplication app = new JoinApplication();
        app.setStatus(2);
        when(joinApplicationMapper.selectById(1L)).thenReturn(app);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, 9L));
        assertEquals("已审核通过", ex.getMessage());
    }

    @Test
    void approve_noPermission_throws() {
        JoinApplication app = new JoinApplication();
        app.setStatus(1);
        app.setManagerId(10L);
        when(joinApplicationMapper.selectById(1L)).thenReturn(app);
        // approver has only role 3, not leader/super, and not the assigned manager
        when(userRoleMapper.selectList(any())).thenReturn(List.of(role(9, 3)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, 9L));
        assertEquals("无权审核此申请", ex.getMessage());
    }

    @Test
    void approve_byLeader_succeeds_upgradesAndBills() {
        JoinApplication app = new JoinApplication();
        app.setStatus(1);
        app.setManagerId(10L);
        app.setUserId(50L);
        when(joinApplicationMapper.selectById(1L)).thenReturn(app);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(role(9, 1))); // leader

        // upgradeToMember path
        when(userRoleMapper.selectOne(any())).thenReturn(null); // member role not exists
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(positionMapper.selectOne(any())).thenReturn(null); // skip term (no member position)
        when(userMapper.selectById(50L)).thenReturn(user(50));
        when(settingService.getValue("membership_fee")).thenReturn("50");

        service.approve(1L, 9L);

        assertEquals(2, app.getStatus());
        assertEquals(9L, app.getApprovedBy());
        verify(joinApplicationMapper).updateById(app);
        verify(userRoleMapper).insert(any(UserRole.class)); // member role added
        verify(billService).addBill(any(Bill.class), isNull());
    }

    @Test
    void approve_byAssignedManager_succeeds() {
        JoinApplication app = new JoinApplication();
        app.setStatus(1);
        app.setManagerId(9L); // approver is the manager
        app.setUserId(50L);
        when(joinApplicationMapper.selectById(1L)).thenReturn(app);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(role(9, 2))); // not 0/1
        when(userRoleMapper.selectOne(any())).thenReturn(role(50, 3)); // already member -> no insert
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(positionMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectById(50L)).thenReturn(user(50));
        when(settingService.getValue("membership_fee")).thenReturn(null); // default 30

        service.approve(1L, 9L);
        assertEquals(2, app.getStatus());
        verify(userRoleMapper, never()).insert(any());
        ArgumentCaptor<Bill> cap = ArgumentCaptor.forClass(Bill.class);
        verify(billService).addBill(cap.capture(), isNull());
        assertEquals(new BigDecimal("30"), cap.getValue().getAmount());
        assertEquals(1, cap.getValue().getBillType());
    }

    // ============ directUpgrade ============

    @Test
    void directUpgrade_userNotFound_throws() {
        when(userMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.directUpgrade("S1"));
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void directUpgrade_alreadyMember_throws() {
        User u = user(50);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(userRoleMapper.selectOne(any())).thenReturn(role(50, 3));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.directUpgrade("S50"));
        assertEquals("该用户已是正式成员", ex.getMessage());
    }

    @Test
    void directUpgrade_succeeds_withMemberTermCreated() {
        User u = user(50);
        when(userMapper.selectOne(any())).thenReturn(u);
        // memberRole check -> null; inside upgradeToMember selectOne for role 3 -> null
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        when(settingService.getValue("current_grade")).thenReturn("2025");
        Position memberPos = new Position();
        memberPos.setId(8);
        memberPos.setPositionName("成员");
        when(positionMapper.selectOne(any())).thenReturn(memberPos);
        when(termMapper.selectCount(any())).thenReturn(0L); // no existing term -> insert
        when(userMapper.selectById(50L)).thenReturn(u);
        when(settingService.getValue("membership_fee")).thenReturn("30");

        service.directUpgrade("S50");

        verify(userRoleMapper).insert(any(UserRole.class));
        verify(termMapper).insert(any(Term.class));
        verify(billService).addBill(any(Bill.class), isNull());
    }

    @Test
    void directUpgrade_memberTermAlreadyExists_noTermInsert() {
        User u = user(50);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(userRoleMapper.selectOne(any())).thenReturn(null);
        when(settingService.getValue("current_grade")).thenReturn("2025");
        Position memberPos = new Position();
        memberPos.setId(8);
        when(positionMapper.selectOne(any())).thenReturn(memberPos);
        when(termMapper.selectCount(any())).thenReturn(2L); // existing -> no insert
        when(userMapper.selectById(50L)).thenReturn(u);
        when(settingService.getValue("membership_fee")).thenReturn("40");

        service.directUpgrade("S50");
        verify(termMapper, never()).insert(any());
    }

    // ============ isCurrentManager ============

    @Test
    void isCurrentManager_trueWhenCountPositive() {
        when(settingService.getValue("current_grade")).thenReturn("2025");
        when(joinManagerMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.isCurrentManager(10L));
    }

    @Test
    void isCurrentManager_falseWhenZero_nullGrade() {
        when(settingService.getValue("current_grade")).thenReturn(null);
        when(joinManagerMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.isCurrentManager(10L));
    }
}
