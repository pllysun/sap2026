package com.sap.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static com.sap.RegistrationPolicyFixture.*;

class CaptchaServiceTest {
    private static final String IP = "192.0.2.1";
    private CaptchaService service;
    private RegistrationProtectionSettingsService settings;
    private long now;

    @BeforeEach
    void setup() {
        settings = settings();
        service = new CaptchaService(settings);
        now = 1_000_000L;
        ReflectionTestUtils.setField(service, "clock", (LongSupplier) () -> now);
    }

    private String generate() {
        Map<String, Object> data = service.generate(IP);
        assertTrue(((String) data.get("image")).startsWith("data:image/png;base64,"));
        assertFalse(data.containsKey("answer"));
        return (String) data.get("captchaId");
    }

    private String answer(String id) {
        Map<?, ?> captchas = (Map<?, ?>) ReflectionTestUtils.getField(service, "captchas");
        return (String) ReflectionTestUtils.getField(captchas.get(id), "answer");
    }

    @Test
    void 首次注册及未知来源默认均要求验证码() {
        assertTrue(service.requiresCaptchaForAttempt(IP));
        assertTrue(service.requiresCaptchaForAttempt(null));
    }

    @Test
    void 关闭注册验证码开关可豁免() {
        change(settings, "captcha", "enabled", false);
        assertFalse(service.requiresCaptchaForAttempt(IP));
    }

    @Test
    void 可选豁免按请求预占且到期重置() {
        change(settings, "captcha", "freeLimit", 2);
        assertFalse(service.requiresCaptchaForAttempt(IP));
        assertFalse(service.requiresCaptchaForAttempt(IP));
        assertTrue(service.requiresCaptchaForAttempt(IP));
        now += 86_400_000L;
        service.sweep();
        assertFalse(service.requiresCaptchaForAttempt(IP));
    }

    @Test
    void 并发请求不能多领免验证码名额() throws Exception {
        change(settings, "captcha", "freeLimit", 5);
        assertEquals(5, parallelCount(() -> !service.requiresCaptchaForAttempt(IP)));
    }

    @Test
    void 六位验证码不区分大小写且一次性消费() {
        String id = generate(), answer = answer(id);
        assertEquals(6, answer.length());
        now += 1_000;
        assertTrue(service.verify(id, " " + answer.toLowerCase(java.util.Locale.ROOT) + " ", IP));
        assertFalse(service.verify(id, answer, IP));
    }

    @Test
    void 并发重放只能验证成功一次() throws Exception {
        String id = generate(), answer = answer(id);
        now += 1_000;
        assertEquals(1, parallelCount(() -> service.verify(id, answer, IP)));
    }

    @Test
    void 换IP或答错均消费验证码不能继续猜() {
        String id = generate(), answer = answer(id);
        now += 1_000;
        assertFalse(service.verify(id, answer, "192.0.2.2"));
        assertFalse(service.verify(id, answer, IP));
        id = generate();
        answer = answer(id);
        now += 1_000;
        assertFalse(service.verify(id, "wrong", IP));
        assertFalse(service.verify(id, answer, IP));
    }

    @Test
    void 过快提交和过期图片均拒绝() {
        String id = generate(), answer = answer(id);
        assertFalse(service.verify(id, answer, IP));
        now += 1_000;
        assertFalse(service.verify(id, answer, IP));
        id = generate();
        answer = answer(id);
        now += 180_000;
        assertFalse(service.verify(id, answer, IP));
    }

    @Test
    void 空答案也只能提交一次且清理过期图片() {
        String id = generate(), answer = answer(id);
        now += 1_000;
        assertFalse(service.verify(id, "", IP));
        assertFalse(service.verify(id, answer, IP));
        assertFalse(service.verify(null, null, IP));
        id = generate();
        assertFalse(service.verify(id, null, IP));
        generate();
        now += 180_000;
        service.sweep();
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(service, "captchas")).isEmpty());
    }

    private int parallelCount(java.util.concurrent.Callable<Boolean> attempt) throws Exception {
        try (var pool = Executors.newFixedThreadPool(24)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 24; i++) results.add(pool.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return attempt.call();
            }));
            start.countDown();
            int success = 0;
            for (Future<Boolean> result : results) if (result.get(5, TimeUnit.SECONDS)) success++;
            return success;
        }
    }
}
