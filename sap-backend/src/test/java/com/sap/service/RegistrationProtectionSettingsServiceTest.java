package com.sap.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sap.common.BusinessException;
import com.sap.config.RateLimitProperties;
import com.sap.dto.RegistrationProtectionConfig;
import com.sap.mapper.SettingMapper;
import com.sap.util.IpUtil;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 H2、MyBatis 和事务验证持久化、回滚、并发保存与动态生效。 */
class RegistrationProtectionSettingsServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate db;
    private SettingMapper mapper;
    private TransactionTemplate tx;
    private ValidatorFactory validators;
    private RegistrationProtectionSettingsService service;
    private long now = 1_000_000;

    @BeforeEach
    void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE sys_setting(id INT AUTO_INCREMENT PRIMARY KEY, setting_key VARCHAR(100) NOT NULL UNIQUE, setting_value VARCHAR(500) NOT NULL, description VARCHAR(255), updated_at TIMESTAMP)");
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        var sessions = factory.getObject();
        sessions.getConfiguration().addMapper(SettingMapper.class);
        mapper = new SqlSessionTemplate(sessions).getMapper(SettingMapper.class);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        validators = Validation.buildDefaultValidatorFactory();
        service = instance();
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        IpUtil.configureTrustedProxies(List.of("127.0.0.1", "::1"));
        validators.close();
        db.execute("SHUTDOWN");
    }

    private RegistrationProtectionSettingsService instance() {
        var settings = new RegistrationProtectionSettingsService(mapper, json, validators.getValidator());
        ReflectionTestUtils.setField(settings, "clock", (LongSupplier) () -> now);
        settings.initialize();
        return settings;
    }

    private RegistrationProtectionConfig change(String group, String field, Object value) {
        ObjectNode document = json.valueToTree(service.reload().config());
        ((ObjectNode) document.get(group)).set(field, json.valueToTree(value));
        return json.convertValue(document, RegistrationProtectionConfig.class);
    }

    private RegistrationProtectionSettingsService.View save(RegistrationProtectionConfig config) {
        var update = new RegistrationProtectionSettingsService.Update(service.reload().revision(), config);
        return tx.execute(status -> service.save(update));
    }

    @Test
    void defaultsAreStoredOnceAndRestartPreservesSavedPolicy() {
        assertEquals(RegistrationProtectionConfig.defaults(), service.current());
        assertEquals(5, db.queryForObject("SELECT COUNT(*) FROM sys_setting", Integer.class));
        var changed = change("quotas", "ipHourlyLimit", 7);
        var saved = save(changed);
        assertEquals(changed, service.current());
        var restarted = instance();
        assertEquals(changed, restarted.current());
        assertEquals(saved.revision(), restarted.reload().revision());
        assertTrue(db.queryForObject("SELECT MAX(LENGTH(setting_value)) FROM sys_setting", Integer.class) <= 500);
    }

    @Test
    void invalidConfigCannotPartiallyUpdateTheDatabase() {
        var original = service.reload();
        var invalid = change("quotas", "maxConcurrentPerIp", 5);
        BusinessException error = assertThrows(BusinessException.class, () -> save(invalid));
        assertEquals(400, error.getCode());
        assertEquals(original.revision(), service.reload().revision());
        assertEquals(original.config(), service.current());

        var invalidProxy = new RegistrationProtectionConfig(original.config().captcha(), original.config().quotas(),
                original.config().requests(), "example.com");
        assertEquals(400, assertThrows(BusinessException.class, () -> save(invalidProxy)).getCode());
        assertEquals(original.config(), service.reload().config());
    }

    @Test
    void transactionRollbackPreservesStoredAndActivePolicy() {
        var original = service.reload();
        var changed = change("captcha", "length", 4);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            service.save(new RegistrationProtectionSettingsService.Update(original.revision(), changed));
            throw new IllegalStateException("模拟提交失败");
        }));
        assertEquals(original.config(), service.current());
        assertEquals(original, service.reload());
    }

    @Test
    void concurrentAdministratorsCannotOverwriteEachOther() throws Exception {
        String revision = service.reload().revision();
        var changed = change("requests", "registerCapacity", 3);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    tx.execute(status -> service.save(new RegistrationProtectionSettingsService.Update(revision, changed)));
                    return true;
                } catch (BusinessException e) {
                    assertEquals(409, e.getCode());
                    return false;
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(changed, service.reload().config());
    }

    @Test
    void otherInstancesReloadWithinFiveSeconds() {
        var other = instance();
        var original = other.current();
        var changed = change("captcha", "freeLimit", 2);
        save(changed);
        assertEquals(changed, service.current());
        assertEquals(original, other.current());
        now += 5_000;
        assertEquals(changed, other.current());
    }

    @Test
    void databaseFailureOrMalformedPolicyKeepsLastValidSnapshot() {
        var original = service.current();
        db.update("UPDATE sys_setting SET setting_value='invalid-json' WHERE setting_key=?",
                RegistrationProtectionSettingsService.PREFIX + "captcha");
        now += 5_000;
        assertEquals(original, service.current());
        assertThrows(IllegalStateException.class, service::reload);
        db.execute("DROP TABLE sys_setting");
        now += 5_000;
        assertEquals(original, service.current());
    }

    @Test
    void requestUsesOneConsistentSnapshotWhileLaterRequestsUseNewPolicy() {
        var original = service.current();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertEquals(original, service.current());
        var changed = change("requests", "registerPerMinute", 8);
        save(changed);
        assertEquals(original, service.current());
        RequestContextHolder.resetRequestAttributes();
        assertEquals(changed, service.current());
    }

    @Test
    void savingTrustedProxiesImmediatelyChangesHeaderHandling() {
        IpUtil.useTrustedProxySource(service::trustedProxyAddresses);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.9");
        request.addHeader("X-Real-IP", "198.51.100.1");
        assertEquals("192.0.2.9", IpUtil.clientIp(request));
        var config = service.current();
        save(new RegistrationProtectionConfig(config.captcha(), config.quotas(), config.requests(), "192.0.2.9\n::1"));
        assertEquals("198.51.100.1", IpUtil.clientIp(request));
    }

    private RateLimiterService limiter() {
        var limiter = new RateLimiterService();
        var legacy = new RateLimitProperties();
        legacy.setUseRedis(false);
        legacy.setEnabled(false); // 旧全局开关不能覆盖数据库中的注册保护开关。
        ReflectionTestUtils.setField(limiter, "props", legacy);
        ReflectionTestUtils.setField(limiter, "clock", (LongSupplier) () -> now);
        return limiter;
    }

    @Test
    void savedQuotaIsEnforcedWithoutRestartAndCountersAreNotReset() {
        var guard = new RegistrationProtectionService(limiter(), service);
        guard.acquire("ip", "qq1").close();
        save(change("quotas", "ipHourlyLimit", 1));
        now += 10_000;
        assertEquals(429, assertThrows(BusinessException.class, () -> guard.acquire("ip", "qq2")).getCode());
        save(change("quotas", "ipHourlyLimit", 2));
        now += 10_000;
        assertDoesNotThrow(() -> guard.acquire("ip", "qq2").close());
    }

    @Test
    void dynamicConcurrencyAndSwitchesPreserveExistingPermits() {
        var guard = new RegistrationProtectionService(limiter(), service);
        var first = guard.acquire("ip", "qq1");
        save(change("quotas", "maxConcurrentPerIp", 2));
        now += 10_000;
        var second = guard.acquire("ip", "qq2");
        save(change("quotas", "enabled", false));
        guard.acquire("ip", "qq3").close(); // 未计数的许可不得释放已有请求的额度。
        save(change("quotas", "enabled", true));
        save(change("quotas", "maxConcurrentPerIp", 1));
        first.close();
        assertThrows(BusinessException.class, () -> guard.acquire("ip", "qq3"));
        second.close();
        now += 10_000;
        assertDoesNotThrow(() -> guard.acquire("ip", "qq3").close());
    }

    @Test
    void captchaChangesApplyToNewImagesAndPreserveAlreadyIssuedImages() {
        var captcha = new CaptchaService(service);
        ReflectionTestUtils.setField(captcha, "clock", (LongSupplier) () -> now);
        var oldId = (String) captcha.generate("ip").get("captchaId");
        var stored = (java.util.Map<?, ?>) ReflectionTestUtils.getField(captcha, "captchas");
        var oldAnswer = (String) ReflectionTestUtils.getField(stored.get(oldId), "answer");
        save(change("captcha", "length", 4));
        save(change("captcha", "ttlSeconds", 30));
        var newId = (String) captcha.generate("ip").get("captchaId");
        var newAnswer = (String) ReflectionTestUtils.getField(stored.get(newId), "answer");
        assertEquals(6, oldAnswer.length());
        assertEquals(4, newAnswer.length());
        now += 31_000;
        assertTrue(captcha.verify(oldId, oldAnswer, "ip"));
        assertFalse(captcha.verify(newId, newAnswer, "ip"));
    }
}
