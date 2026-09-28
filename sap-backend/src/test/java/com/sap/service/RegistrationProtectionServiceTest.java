package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static com.sap.RegistrationPolicyFixture.*;

class RegistrationProtectionServiceTest {
    private RegistrationProtectionSettingsService settings;
    private RegistrationProtectionService service;
    private long now;

    @BeforeEach
    void setup() {
        RateLimiterService limiter = new RateLimiterService();
        RateLimitProperties rate = new RateLimitProperties();
        rate.setUseRedis(false);
        ReflectionTestUtils.setField(limiter, "props", rate);
        now = 1_000_000L;
        ReflectionTestUtils.setField(limiter, "clock", (LongSupplier) () -> now);
        settings = settings();
        service = new RegistrationProtectionService(limiter, settings);
    }

    private void register(String ip, String qq) {
        try (var permit = service.acquire(ip, qq)) { /* 模拟完成数据库调用，包括失败后的 finally */ }
    }

    private void limited(String ip, String qq) {
        assertEquals(429, assertThrows(BusinessException.class, () -> register(ip, qq)).getCode());
    }

    @Test
    void 同IP冷却期不会因释放并发名额而失效() {
        register("ip1", "12345");
        limited("ip1", "23456");
        now += 10_000;
        register("ip1", "23456");
    }

    @Test
    void 同IP小时与日额度均受限且可以到期恢复() {
        change(settings, "quotas", "ipHourlyLimit", 2);
        change(settings, "quotas", "ipDailyLimit", 3);
        register("ip1", "10001");
        now += 10_000;
        register("ip1", "10002");
        now += 10_000;
        limited("ip1", "10003");
        now += 3_600_000;
        register("ip1", "10003");
        now += 10_000;
        limited("ip1", "10004");
        now += 86_400_000;
        register("ip1", "10004");
    }

    @Test
    void 更换IP仍受QQ和全局额度限制() {
        change(settings, "quotas", "qqDailyLimit", 1);
        change(settings, "quotas", "globalMinuteLimit", 2);
        register("ip1", "10001");
        limited("ip2", "10001");
        register("ip3", "10002");
        limited("ip4", "10003");
        now += 60_000;
        register("ip5", "10004");
        limited("ip4", "10003"); // 全局拒绝前已预占的QQ日额度不退还
    }

    @Test
    void 超限不会泄漏并发名额且关闭许可是幂等的() {
        change(settings, "quotas", "maxConcurrent", 1);
        var first = service.acquire("ip1", "10001");
        limited("ip2", "10002");
        first.close();
        limited("ip1", "10001"); // 冷却异常也必须释放其刚预占的并发名额
        var second = service.acquire("ip2", "10002");
        first.close();
        limited("ip3", "10003");
        second.close();
        register("ip3", "10003");
    }

    @Test
    void 同IP并发仅一个请求进入数据库阶段() throws Exception {
        assertEquals(1, concurrentAdmissions(true));
    }

    @Test
    void 不同IP并发也不能突破实例上限() throws Exception {
        assertEquals(settings.current().quotas().maxConcurrent(), concurrentAdmissions(false));
    }

    private int concurrentAdmissions(boolean sameIp) throws Exception {
        List<RegistrationProtectionService.Permit> permits = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(24)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<RegistrationProtectionService.Permit>> futures = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                int id = i;
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    try {
                        return service.acquire(sameIp ? "shared" : "ip" + id, "1000" + id);
                    } catch (BusinessException e) {
                        assertEquals(429, e.getCode());
                        return null;
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                var permit = future.get(5, TimeUnit.SECONDS);
                if (permit != null) permits.add(permit);
            }
            return permits.size();
        } finally {
            permits.forEach(RegistrationProtectionService.Permit::close);
        }
    }
}
