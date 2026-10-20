package com.sap.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sap.common.BusinessException;
import com.sap.entity.*;
import com.sap.mapper.*;
import com.sap.service.mail.EmailBusinessHooks;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真正的 SQL、行锁和事务；所有账户、会费及邮件均为隔离测试数据。 */
class JoinWorkflowPersistenceTest {
    private JdbcTemplate db;
    private JoinService service;
    private BillService bills;
    private EmailBusinessHooks mail;

    @BeforeEach void prepare() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        db = new JdbcTemplate(ds);
        for (Class<?> type : List.of(User.class, UserRole.class, JoinManager.class, JoinApplication.class,
                Setting.class, Term.class, Position.class, Bill.class, BillImage.class)) {
            List<String> columns = new ArrayList<>();
            for (var field : type.getDeclaredFields()) {
                if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && !tableField.exist()) continue;
                Column column = field.getAnnotation(Column.class);
                String name = column == null || column.name().isBlank() ? field.getName() : column.name();
                String sqlType = field.isAnnotationPresent(Id.class) ? "BIGINT AUTO_INCREMENT PRIMARY KEY" :
                        field.getType() == Long.class ? "BIGINT" : field.getType() == Integer.class ? "INT" :
                        field.getType() == BigDecimal.class ? "DECIMAL(10,2)" :
                        field.getType() == LocalDateTime.class ? "TIMESTAMP" : "VARCHAR(1024)";
                columns.add("`" + name + "` " + sqlType + (name.equals("deleted") ? " DEFAULT 0" : ""));
            }
            db.execute("CREATE TABLE " + type.getAnnotation(TableName.class).value() + " (" + String.join(",", columns) + ")");
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        var sessions = factory.getObject();
        for (Class<?> mapper : List.of(UserMapper.class, UserRoleMapper.class, JoinManagerMapper.class,
                JoinApplicationMapper.class, SettingMapper.class, TermMapper.class, PositionMapper.class,
                BillMapper.class, BillImageMapper.class)) sessions.getConfiguration().addMapper(mapper);
        var sql = new SqlSessionTemplate(sessions);
        var settings = new SettingService();
        ReflectionTestUtils.setField(settings, "settingMapper", sql.getMapper(SettingMapper.class));
        bills = spy(new BillService());
        ReflectionTestUtils.setField(bills, "billMapper", sql.getMapper(BillMapper.class));
        ReflectionTestUtils.setField(bills, "billImageMapper", sql.getMapper(BillImageMapper.class));
        ReflectionTestUtils.setField(bills, "settingMapper", sql.getMapper(SettingMapper.class));
        var target = new JoinService();
        ReflectionTestUtils.setField(target, "userMapper", sql.getMapper(UserMapper.class));
        ReflectionTestUtils.setField(target, "userRoleMapper", sql.getMapper(UserRoleMapper.class));
        ReflectionTestUtils.setField(target, "joinManagerMapper", sql.getMapper(JoinManagerMapper.class));
        ReflectionTestUtils.setField(target, "joinApplicationMapper", sql.getMapper(JoinApplicationMapper.class));
        ReflectionTestUtils.setField(target, "termMapper", sql.getMapper(TermMapper.class));
        ReflectionTestUtils.setField(target, "positionMapper", sql.getMapper(PositionMapper.class));
        ReflectionTestUtils.setField(target, "settingService", settings);
        ReflectionTestUtils.setField(target, "billService", bills);
        var cache = mock(CacheService.class);
        when(cache.getAdminPositions()).thenReturn(List.of());
        ReflectionTestUtils.setField(target, "cacheService", cache);
        mail = mock(EmailBusinessHooks.class);
        ReflectionTestUtils.setField(target, "emailHooks", mail);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (JoinService) proxy.getProxy();
        for (long id : List.of(1L, 9L, 10L, 11L)) {
            db.update("INSERT INTO sys_user(id,student_id,name,password,qq,status,deleted) VALUES(?,?,?,?,?,1,0)", id,"fixture-"+id,"测试"+id,"unused-fixture","123450"+id);
            db.update("INSERT INTO sys_user_role(user_id,role_code) VALUES(?,?)", id, id==1 ? 4 : id==9 ? 1 : 2);
        }
        db.update("INSERT INTO sys_setting(setting_key,setting_value) VALUES('current_grade','2026'),('membership_fee','30.00'),('join_enabled','false')");
        db.update("INSERT INTO join_manager(user_id,grade,alipay_qr) VALUES(10,'2026','https://example.invalid/qr.svg')");
        db.update("INSERT INTO sys_position(id,position_name,role_code) VALUES(3,'成员',3)");
    }

    @AfterEach void close() { db.execute("SHUTDOWN"); }
    private long start() { service.toggleJoin(true); return ((Number)service.apply(1L).get("id")).longValue(); }
    private int count(String sql) { return db.queryForObject(sql, Integer.class); }

    @Test void openApplySubmitApproveClosesMembershipAndFinance() {
        long id = start();
        service.submitPaymentCode(1L, " fixture-payment ");
        assertEquals(1, service.getMyApplication(1L).get("status"));
        service.approve(id, 10L);
        assertEquals(2, service.getMyApplication(1L).get("status"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id=1 AND role_code=3"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id=1 AND role_code=4"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_term WHERE user_id=1 AND grade='2026'"));
        assertEquals(1, count("SELECT COUNT(*) FROM fin_bill"));
        assertEquals("fixture-payment", service.getMyApplication(1L).get("paymentCode"));
        verify(mail).memberJoined(1L);
    }

    @Test void manualApprovalMaySkipPaymentSubmission() {
        long id = start();
        assertEquals(0, service.getMyApplication(1L).get("status"));
        service.approve(id, 9L);
        service.approve(id, 9L);
        assertEquals(2, service.getMyApplication(1L).get("status"));
        assertEquals(1, count("SELECT COUNT(*) FROM fin_bill"));
        verify(mail, times(1)).memberJoined(1L);
    }

    @Test void concurrentApplyAndApproveDoNotDuplicateApplicationsRolesOrBills() throws Exception {
        service.toggleJoin(true);
        var applications = together(() -> service.apply(1L));
        assertEquals(applications.get(0).get("id"), applications.get(1).get("id"));
        assertEquals(1, count("SELECT COUNT(*) FROM join_application"));
        long id = ((Number)applications.get(0).get("id")).longValue();
        together(() -> { service.approve(id, 9L); return true; });
        assertEquals(1, count("SELECT COUNT(*) FROM fin_bill"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id=1 AND role_code=3"));
    }

    @Test void directUpgradeClosesPendingApplicationAndNeverBillsAgain() {
        long id = start();
        service.directUpgrade("fixture-1", 9L);
        service.approve(id, 10L);
        assertEquals(2, service.getMyApplication(1L).get("status"));
        assertEquals(1, count("SELECT COUNT(*) FROM fin_bill"));
        verify(mail, times(1)).memberJoined(1L);
    }

    @Test void returnedInformationCanBeCorrectedWithoutChangingPayeeOrPayingAgain() {
        long id = start();
        service.submitPaymentCode(1L, "old-reference");
        service.returnApplication(id, 10L, "请核对交易单号");
        var app = service.getMyApplication(1L);
        assertEquals(3, app.get("status"));
        assertEquals("请核对交易单号", app.get("reviewNote"));
        assertEquals(false, app.get("canRefresh"));
        service.submitPaymentCode(1L, "correct-reference");
        service.submitPaymentCode(1L, "correct-reference");
        service.approve(id, 10L);
        assertEquals(1, count("SELECT COUNT(*) FROM join_application"));
        assertEquals(1, count("SELECT COUNT(*) FROM fin_bill"));
    }

    @Test void closingOnlyStopsNewApplicationsAndExistingFlowStillCompletes() {
        long id = start();
        service.toggleJoin(false);
        assertEquals(id, ((Number)service.apply(1L).get("id")).longValue());
        service.submitPaymentCode(1L, "after-close");
        service.approve(id, 9L);
        assertEquals(2, service.getMyApplication(1L).get("status"));
    }

    @Test void feeAndGradeAreFrozenAtApplicationTime() {
        long id = start();
        db.update("UPDATE sys_setting SET setting_value='2027' WHERE setting_key='current_grade'");
        db.update("UPDATE sys_setting SET setting_value='70.00' WHERE setting_key='membership_fee'");
        service.approve(id, 9L);
        assertEquals(0, new BigDecimal("30.00").compareTo(db.queryForObject("SELECT amount FROM fin_bill", BigDecimal.class)));
        assertEquals("2026", db.queryForObject("SELECT grade FROM fin_bill", String.class));
        assertEquals("2026", service.getMyApplication(1L).get("grade"));
    }

    @Test void financeFailureRollsBackRoleApplicationAndMemberArchive() {
        long id = start();
        doThrow(new IllegalStateException("fixture finance unavailable")).when(bills).addBill(any(), any());
        assertThrows(IllegalStateException.class, () -> service.approve(id, 9L));
        assertEquals(0, service.getMyApplication(1L).get("status"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id=1 AND role_code=3"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id=1 AND role_code=4"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_term WHERE user_id=1"));
    }

    @Test void unavailablePayeePreventsOpeningAndOtherManagersCannotReview() {
        db.update("UPDATE join_manager SET alipay_qr=' '");
        assertFalse((Boolean)service.getReadiness().get("ready"));
        assertThrows(BusinessException.class, () -> service.toggleJoin(true));
        assertFalse(service.isJoinEnabled());
        db.update("UPDATE join_manager SET alipay_qr='https://example.invalid/qr.svg'");
        long id = start();
        assertThrows(BusinessException.class, () -> service.approve(id, 11L));
        assertThrows(BusinessException.class, () -> service.returnApplication(id, 11L, "不允许越权"));
        assertEquals(0, count("SELECT COUNT(*) FROM fin_bill"));
    }

    @Test void correctionLengthAndManagerRemovalAreGuarded() {
        long id = start();
        assertThrows(BusinessException.class, () -> service.submitPaymentCode(1L, "x".repeat(101)));
        assertThrows(BusinessException.class, () -> service.returnApplication(id, 9L, " "));
        Long managerId = db.queryForObject("SELECT id FROM join_manager", Long.class);
        assertThrows(BusinessException.class, () -> service.removeManager(managerId));
        service.approve(id, 9L);
        service.removeManager(managerId);
        assertEquals(0, count("SELECT COUNT(*) FROM join_manager"));
    }

    private <T> List<T> together(Supplier<T> action) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<T> call = () -> { if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException(); return action.get(); };
            var a = pool.submit(call); var b = pool.submit(call); start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
